package com.malekfinance

import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import androidx.room.withTransaction
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable password-encrypted backup. The password is never stored and all work
 * happens locally. Only schema v12 backups are accepted for restore in this release.
 */
object FinanceBackupManager {
    private const val MAGIC = "MALBK12!"
    private const val ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    private const val MAX_ROWS = 100_000
    private val tables = FinanceBackupIntegrity.tableNames

    suspend fun export(db: FinanceDb, output: OutputStream, password: CharArray) {
        if (password.size < 8) {
            password.fill('\u0000')
            throw IllegalArgumentException("Backup password must contain at least 8 characters")
        }
        val payload = try {
            db.withTransaction {
                val database = db.openHelper.readableDatabase
                var encodedBytes = 0L
                var rowCount = 0
                JSONObject()
                    .put("format", MAGIC)
                    .put("schemaVersion", 12)
                    .put("createdAt", System.currentTimeMillis())
                    .put("tables", JSONObject().apply {
                        tables.forEach { table ->
                            val rows = JSONArray()
                            database.query("SELECT * FROM $table").use { cursor ->
                                while (cursor.moveToNext()) {
                                    require(++rowCount <= MAX_ROWS) { "Too many backup rows" }
                                    val encoded = encodeRow(cursor)
                                    encodedBytes += encoded.toString().toByteArray(StandardCharsets.UTF_8).size
                                    require(encodedBytes <= MAX_BACKUP_BYTES - 1024L) { "Backup exceeds size limit" }
                                    rows.put(encoded)
                                }
                            }
                            put(table, rows)
                        }
                    })
                    .toString()
                    .toByteArray(StandardCharsets.UTF_8)
            }
        } catch (failure: Throwable) {
            password.fill('\u0000')
            throw failure
        }

        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        var encrypted: ByteArray? = null
        try {
            require(payload.size + 40 <= MAX_BACKUP_BYTES) { "Backup exceeds the supported 64 MiB limit" }
            val key = try { deriveKey(password, salt) } finally { password.fill('\u0000') }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
            cipher.updateAAD(MAGIC.toByteArray(StandardCharsets.US_ASCII))
            val ciphertext = cipher.doFinal(payload)
            encrypted = ciphertext
            require(ciphertext.size + 40 <= MAX_BACKUP_BYTES) { "Backup exceeds the supported 64 MiB limit" }
            output.write(MAGIC.toByteArray(StandardCharsets.US_ASCII))
            output.write(salt)
            output.write(nonce)
            output.write(ByteBuffer.allocate(4).putInt(ciphertext.size).array())
            output.write(ciphertext)
            output.flush()
        } finally {
            // Also clear the password when validation fails before key derivation
            // (for example, when the backup exceeds the supported size).
            password.fill('\u0000')
            payload.fill(0)
            salt.fill(0)
            nonce.fill(0)
            encrypted?.fill(0)
        }
    }

    suspend fun restore(db: FinanceDb, input: InputStream, password: CharArray) {
        if (password.size < 8) {
            password.fill('\u0000')
            throw IllegalArgumentException("Backup password must contain at least 8 characters")
        }
        val bytes = try {
            input.use { readBounded(it, MAX_BACKUP_BYTES) }
        } catch (failure: Throwable) {
            password.fill('\u0000')
            throw failure
        }
        try {
            require(bytes.size in 40..MAX_BACKUP_BYTES) { "Backup file size is invalid" }
            val magicBytes = MAGIC.toByteArray(StandardCharsets.US_ASCII)
            require(bytes.copyOfRange(0, magicBytes.size).contentEquals(magicBytes)) { "Unsupported backup format" }
            val salt = bytes.copyOfRange(8, 24)
            val nonce = bytes.copyOfRange(24, 36)
            try {
                val encryptedLength = ByteBuffer.wrap(bytes, 36, 4).int
                require(encryptedLength > 16 && encryptedLength == bytes.size - 40) { "Backup file is incomplete" }
                val key = deriveKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
                cipher.updateAAD(magicBytes)
                val encryptedPayload = bytes.copyOfRange(40, bytes.size)
                val plaintext = try {
                    cipher.doFinal(encryptedPayload)
                } finally {
                    encryptedPayload.fill(0)
                }
                val json = try {
                    JSONObject(String(plaintext, StandardCharsets.UTF_8))
                } finally {
                    plaintext.fill(0)
                }
                require(json.optString("format") == MAGIC && json.optInt("schemaVersion") == 12) {
                    "Backup database version is not supported by this app version"
                }
                val tableData = json.getJSONObject("tables")
                require(tableData.keys().asSequence().toSet() == tables.toSet()) { "Backup tables do not match schema v12" }

                db.withTransaction {
                    val database = db.openHelper.writableDatabase
                    require(tables.sumOf { tableData.getJSONArray(it).length().toLong() } <= MAX_ROWS) { "Too many backup rows" }
                    val decoded = mutableMapOf<String, List<Map<String, Any?>>>()
                    tables.asReversed().forEach { database.execSQL("DELETE FROM $it") }
                    tables.forEach { table ->
                        val expectedColumns = tableData.getJSONArray(table)
                        val actualColumns = database.query("PRAGMA table_info($table)").use { cursor ->
                            buildMap {
                                while (cursor.moveToNext()) put(cursor.getString(cursor.getColumnIndexOrThrow("name")),
                                    cursor.getString(cursor.getColumnIndexOrThrow("type")) to (cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 1))
                            }
                        }
                        val decodedRows = mutableListOf<Map<String, Any?>>()
                        decoded[table] = decodedRows
                        for (index in 0 until expectedColumns.length()) {
                            val row = expectedColumns.getJSONObject(index)
                            val columns = row.getJSONArray("columns")
                            val values = row.getJSONArray("values")
                            require(columns.length() == values.length())
                            val rowColumnNames = buildSet {
                                for (columnIndex in 0 until columns.length()) add(columns.getString(columnIndex))
                            }
                            require(
                                rowColumnNames.size == columns.length() && rowColumnNames == actualColumns.keys
                            ) { "Backup row does not match the current $table schema" }
                            val content = ContentValues()
                            val decodedRow = mutableMapOf<String, Any?>()
                            decodedRows.add(decodedRow)
                            for (columnIndex in 0 until columns.length()) {
                                val column = columns.getString(columnIndex)
                                require(column in actualColumns) { "Unknown column $column in $table" }
                                val cell = values.getJSONObject(columnIndex)
                                val (sqlType, required) = actualColumns.getValue(column)
                                when (cell.getInt("type")) {
                                    Cursor.FIELD_TYPE_NULL -> {
                                        require(!required) { "Required column is null" }
                                        content.putNull(column); decodedRow[column] = null
                                    }
                                    Cursor.FIELD_TYPE_INTEGER -> {
                                        require(sqlType == "INTEGER") { "Backup column type mismatch" }
                                        val value = cell.get("value")
                                        require(value is Int || value is Long) { "Integer cannot be a string or floating point" }
                                        val number = (value as Number).toLong()
                                        content.put(column, number); decodedRow[column] = number
                                    }
                                    Cursor.FIELD_TYPE_STRING -> {
                                        require(sqlType == "TEXT") { "Backup column type mismatch" }
                                        val value = cell.get("value")
                                        require(value is String) { "Text column contains a non-string" }
                                        content.put(column, value); decodedRow[column] = value
                                    }
                                    else -> error("Unsupported backup cell type")
                                }
                            }
                            val insertedRowId = database.insert(
                                table,
                                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                                content
                            )
                            check(insertedRowId != -1L) { "Could not restore a row in table $table" }
                        }
                    }
                    // Still inside the same transaction: any semantic failure restores
                    // every original table, not just the last invalid row.
                    FinanceBackupIntegrity.validate(decoded)
                }
            } finally {
                salt.fill(0)
                nonce.fill(0)
            }
        } finally {
            bytes.fill(0)
            password.fill('\u0000')
        }
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maximum) { "Backup file is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun encodeRow(cursor: Cursor): JSONObject {
        val columns = JSONArray()
        val values = JSONArray()
        for (index in 0 until cursor.columnCount) {
            columns.put(cursor.getColumnName(index))
            val cell = JSONObject().put("type", cursor.getType(index))
            when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> cell.put("value", JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> cell.put("value", cursor.getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> cell.put("value", cursor.getDouble(index))
                Cursor.FIELD_TYPE_STRING -> cell.put("value", cursor.getString(index))
                Cursor.FIELD_TYPE_BLOB -> cell.put("value", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
                else -> error("Unsupported SQLite value type")
            }
            values.put(cell)
        }
        return JSONObject().put("columns", columns).put("values", values)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }
}
