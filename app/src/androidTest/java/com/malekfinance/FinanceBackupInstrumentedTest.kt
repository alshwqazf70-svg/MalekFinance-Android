package com.malekfinance

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FinanceBackupInstrumentedTest {
    private lateinit var db: FinanceDb

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDb::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun encryptedBackupRestoresAllLedgerRows() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 5_000)
        )
        db.dao().insert(
            FinanceTransaction(amountMinor = 1_250, kind = "EXPENSE", note = "عملية أصلية", walletId = walletId, currencyCode = "YER")
        )
        val backup = ByteArrayOutputStream()
        FinanceBackupManager.export(db, backup, "strong-pass-123".toCharArray())
        assertTrue(backup.toByteArray().size > 40)
        assertTrue(!String(backup.toByteArray(), Charsets.UTF_8).contains("عملية أصلية"))

        db.dao().insert(
            FinanceTransaction(amountMinor = 9_999, kind = "EXPENSE", note = "عملية لاحقة", walletId = walletId, currencyCode = "YER")
        )
        FinanceBackupManager.restore(db, ByteArrayInputStream(backup.toByteArray()), "strong-pass-123".toCharArray())

        val restored = db.dao().forWallet(walletId)
        assertEquals(1, restored.size)
        assertEquals("عملية أصلية", restored.single().note)
        assertEquals(walletId, db.walletDao().findById(walletId)?.id)
    }

    @Test
    fun restoreFailureDuringRowInsertRollsBackTheExistingDatabase() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "النقد", currencyCode = "YER"))
        db.dao().insert(
            FinanceTransaction(amountMinor = 300, kind = "EXPENSE", note = "داخل النسخة", walletId = walletId)
        )
        val backup = ByteArrayOutputStream()
        FinanceBackupManager.export(db, backup, "rollback-pass".toCharArray())

        db.dao().insert(
            FinanceTransaction(amountMinor = 900, kind = "EXPENSE", note = "البيانات الحالية", walletId = walletId)
        )
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_restore_transaction BEFORE INSERT ON transactions " +
                "BEGIN SELECT RAISE(ABORT, 'injected restore failure'); END"
        )

        var failed = false
        try {
            FinanceBackupManager.restore(db, ByteArrayInputStream(backup.toByteArray()), "rollback-pass".toCharArray())
        } catch (_: Exception) {
            failed = true
        }

        assertTrue(failed)
        val remaining = db.dao().forWallet(walletId)
        assertEquals(1, remaining.size)
        assertEquals("البيانات الحالية", remaining.single().note)
    }

    @Test
    fun tooShortPasswordsAreClearedForBothBackupDirections() = runBlocking {
        val exportPassword = "short".toCharArray()
        var exportFailed = false
        try {
            FinanceBackupManager.export(db, ByteArrayOutputStream(), exportPassword)
        } catch (_: Exception) {
            exportFailed = true
        }
        assertTrue(exportFailed)
        assertTrue(exportPassword.all { it == '\u0000' })

        val restorePassword = "short".toCharArray()
        var restoreFailed = false
        try {
            FinanceBackupManager.restore(db, ByteArrayInputStream(byteArrayOf()), restorePassword)
        } catch (_: Exception) {
            restoreFailed = true
        }
        assertTrue(restoreFailed)
        assertTrue(restorePassword.all { it == '\u0000' })
    }

    @Test
    fun invalidBackupHeaderStillClearsTheSuppliedPasswordBuffer() = runBlocking {
        val backup = ByteArrayOutputStream()
        FinanceBackupManager.export(db, backup, "header-pass".toCharArray())
        val corrupted = backup.toByteArray().also { it[0] = 0 }
        val password = "header-pass".toCharArray()

        var failed = false
        try {
            FinanceBackupManager.restore(db, ByteArrayInputStream(corrupted), password)
        } catch (_: Exception) {
            failed = true
        }

        assertTrue(failed)
        assertTrue(password.all { it == '\u0000' })
    }

    @Test
    fun wrongPasswordDoesNotChangeExistingData() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "النقد", currencyCode = "YER"))
        db.dao().insert(FinanceTransaction(amountMinor = 500, kind = "EXPENSE", note = "محفوظ", walletId = walletId))
        val backup = ByteArrayOutputStream()
        FinanceBackupManager.export(db, backup, "correct-pass".toCharArray())

        var failed = false
        try {
            FinanceBackupManager.restore(db, ByteArrayInputStream(backup.toByteArray()), "wrong-pass".toCharArray())
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
        assertEquals("محفوظ", db.dao().forWallet(walletId).single().note)
    }

    @Test
    fun modifiedCiphertextIsRejectedWithoutChangingExistingData() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "النقد", currencyCode = "YER"))
        db.dao().insert(FinanceTransaction(amountMinor = 400, kind = "EXPENSE", note = "داخل النسخة", walletId = walletId))
        val backup = ByteArrayOutputStream()
        FinanceBackupManager.export(db, backup, "integrity-pass".toCharArray())

        db.dao().insert(FinanceTransaction(amountMinor = 900, kind = "EXPENSE", note = "الحالي", walletId = walletId))
        val tampered = backup.toByteArray().also { it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte() }
        var failed = false
        try {
            FinanceBackupManager.restore(db, ByteArrayInputStream(tampered), "integrity-pass".toCharArray())
        } catch (_: Exception) {
            failed = true
        }

        assertTrue(failed)
        val rows = db.dao().forWallet(walletId)
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.note == "داخل النسخة" })
        assertTrue(rows.any { it.note == "الحالي" })
    }

    @Test
    fun truncatedBackupIsRejectedWithoutChangingExistingData() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "النقد", currencyCode = "YER"))
        db.dao().insert(FinanceTransaction(amountMinor = 700, kind = "EXPENSE", note = "البيانات الحالية", walletId = walletId))
        val truncated = ByteArrayOutputStream().also {
            FinanceBackupManager.export(db, it, "truncate-pass".toCharArray())
        }.toByteArray().copyOf(24)

        var failed = false
        try {
            FinanceBackupManager.restore(db, ByteArrayInputStream(truncated), "truncate-pass".toCharArray())
        } catch (_: Exception) {
            failed = true
        }

        assertTrue(failed)
        assertEquals("البيانات الحالية", db.dao().forWallet(walletId).single().note)
    }


    @Test fun authenticatedButOrphanedBackupIsRejectedAndCurrentDataSurvives() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "نقد"))
        // Authentication proves the file was not modified, not that its contents are valid.
        val invalid = FinanceTransaction(amountMinor = 100, kind = "EXPENSE", note = "يتيم", walletId = walletId, debtId = 999L)
        val invalidId = db.dao().insert(invalid)
        val backup = ByteArrayOutputStream()
        FinanceBackupManager.export(db, backup, "semantic-pass".toCharArray())
        db.dao().delete(invalid.copy(id = invalidId))
        db.dao().insert(FinanceTransaction(amountMinor = 300, kind = "INCOME", note = "بيانات سليمة حالية", walletId = walletId))
        var rejected = false
        try { FinanceBackupManager.restore(db, backup.toByteArray().inputStream(), "semantic-pass".toCharArray()) }
        catch (_: Exception) { rejected = true }
        assertTrue(rejected)
        assertEquals("بيانات سليمة حالية", db.dao().forWallet(walletId).single().note)
    }
}
