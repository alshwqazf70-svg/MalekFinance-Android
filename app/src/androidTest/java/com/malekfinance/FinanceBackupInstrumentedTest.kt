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
}
