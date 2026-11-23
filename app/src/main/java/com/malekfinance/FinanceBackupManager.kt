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
    private val tables = listOf(
        "wallets", "categories", "transactions", "transfers", "budgets",
        "debts", "debt_payments", "savings_goals", "savings_entries",
        "recurring_income_rules", "recurring_income_entries",
        "recurring_expense_rules", "recurring_expense_entries",
        "bills", "bill_payments"
    )

    suspend fun export(db: FinanceDb, output: OutputStream, password: CharArray) {
        if (password.size < 8) {
            password.fill('\u0000')
            throw IllegalArgumentException("Backup password must contain at least 8 characters")
        }
        val payload = try {
            db.withTransaction {
                val database = db.openHelper.readableDatabase
                JSONObject()
                    .put("format", MAGIC)
                    .put("schemaVersion", 12)
                    .put("createdAt", System.currentTimeMillis())
                    .put("tables", JSONObject().apply {
                        tables.forEach { table ->
                            val rows = JSONArray()
                            database.query("SELECT * FROM $table").use { cursor ->
                                while (cursor.moveToNext()) rows.put(encodeRow(cursor))
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
            password.fill('\\u0000')
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
                tables.forEach { require(tableData.has(it)) { "Backup is missing table: $it" } }

                db.withTransaction {
                    val database = db.openHelper.writableDatabase
                    tables.asReversed().forEach { database.execSQL("DELETE FROM $it") }
                    tables.forEach { table ->
                        val expectedColumns = tableData.getJSONArray(table)
                        val actualColumns = database.query("PRAGMA table_info($table)").use { cursor ->
                            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                        }
                        for (index in 0 until expectedColumns.length()) {
                            val row = expectedColumns.getJSONObject(index)
                            val columns = row.getJSONArray("columns")
                            val values = row.getJSONArray("values")
                            require(columns.length() == values.length())
                            val rowColumnNames = buildSet {
                                for (columnIndex in 0 until columns.length()) add(columns.getString(columnIndex))
                            }
                            require(
                                rowColumnNames.size == columns.length() && rowColumnNames == actualColumns
                            ) { "Backup row does not match the current $table schema" }
                            val content = ContentValues()
                            for (columnIndex in 0 until columns.length()) {
                                val column = columns.getString(columnIndex)
                                require(column in actualColumns) { "Unknown column $column in $table" }
                                val cell = values.getJSONObject(columnIndex)
                                when (cell.getInt("type")) {
                                    Cursor.FIELD_TYPE_NULL -> content.putNull(column)
                                    Cursor.FIELD_TYPE_INTEGER -> content.put(column, cell.getLong("value"))
                                    Cursor.FIELD_TYPE_FLOAT -> content.put(column, cell.getDouble("value"))
                                    Cursor.FIELD_TYPE_STRING -> content.put(column, cell.getString("value"))
                                    Cursor.FIELD_TYPE_BLOB -> content.put(column, Base64.decode(cell.getString("value"), Base64.NO_WRAP))
                                    else -> error("Unsupported cell type")
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
