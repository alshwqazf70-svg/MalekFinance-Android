package com.malekfinance

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceRoomIntegrityTest {
    private lateinit var db: FinanceDb

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDb::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun transferRejectsOverspendingAndConcurrentTransfersCannotSpendSameFundsTwice() = runBlocking {
        val sourceId = db.walletDao().insert(
            FinanceWallet(name = "المصدر", currencyCode = "YER", openingBalanceMinor = 1_000)
        )
        val firstDestinationId = db.walletDao().insert(
            FinanceWallet(name = "الوجهة الأولى", currencyCode = "YER")
        )
        val secondDestinationId = db.walletDao().insert(
            FinanceWallet(name = "الوجهة الثانية", currencyCode = "YER")
        )

        expectFailure {
            FinanceTransferOperations.transfer(db, sourceId, firstDestinationId, 1_001)
        }
        assertEquals(0, rowCount("transfers"))

        val outcomes = coroutineScope {
            listOf(
                async { runCatching { FinanceTransferOperations.transfer(db, sourceId, firstDestinationId, 700) } },
                async { runCatching { FinanceTransferOperations.transfer(db, sourceId, secondDestinationId, 700) } }
            ).awaitAll()
        }

        assertEquals("Only one concurrent withdrawal may consume the available balance", 1, outcomes.count { it.isSuccess })
        assertEquals(1, rowCount("transfers"))
        val source = db.walletDao().findById(sourceId)!!
        val sourceBalance = FinanceLedger.calculateBalance(
            source,
            db.dao().forWallet(sourceId),
            db.transferDao().forWallet(sourceId)
        )
        assertEquals(java.math.BigInteger.valueOf(300), sourceBalance)
    }

    @Test
    fun failedMultiStepRoomTransactionRollsBackEveryInsertedRow() = runBlocking {
        expectFailure {
            db.withTransaction {
                db.dao().insert(
                    FinanceTransaction(
                        amountMinor = 250,
                        kind = "EXPENSE",
                        note = "must roll back",
                        walletId = 1,
                        currencyCode = "YER"
                    )
                )
                error("Simulated failure after the first database write")
            }
        }

        assertEquals(0, rowCount("transactions"))
    }

    @Test
    fun recurringIncomeExpenseAndBillPaymentsRejectDuplicateOccurrencesAtomically() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "المحفظة", currencyCode = "YER"))
        val incomeRuleId = db.recurringIncomeRuleDao().insert(
            FinanceRecurringIncomeRule(
                title = "راتب",
                incomeType = "SALARY",
                amountMinor = 10_000,
                currencyCode = "YER",
                walletId = walletId,
                frequency = "MONTHLY",
                anchorDay = 1,
                nextDueAt = 1_800_000_000_000
            )
        )
        val expenseRuleId = db.recurringExpenseRuleDao().insert(
            FinanceRecurringExpenseRule(
                title = "إيجار",
                categoryName = "سكن",
                amountMinor = 5_000,
                currencyCode = "YER",
                walletId = walletId,
                frequency = "MONTHLY",
                anchorDay = 1,
                nextDueAt = 1_800_000_000_000
            )
        )
        val billId = db.billDao().insert(
            FinanceBill(
                title = "فاتورة",
                categoryName = "فواتير",
                amountMinor = 2_000,
                currencyCode = "YER",
                walletId = walletId,
                frequency = "MONTHLY",
                anchorDay = 1,
                anchorMonth = 0,
                nextDueAt = 1_800_000_000_000
            )
        )

        db.recurringIncomeEntryDao().insert(
            FinanceRecurringIncomeEntry(incomeRuleId, "2030-01-01", 1001, 10_000)
        )
        db.recurringExpenseEntryDao().insert(
            FinanceRecurringExpenseEntry(expenseRuleId, "2030-01-01", 2001, 5_000)
        )
        db.billPaymentDao().insert(
            FinanceBillPayment(billId, "2030-01-01", 3001, 2_000)
        )
        val before = rowCount("transactions")

        expectFailure {
            db.withTransaction {
                val transactionId = db.dao().insert(
                    FinanceTransaction(100, "INCOME", "duplicate salary", walletId = walletId)
                )
                db.recurringIncomeEntryDao().insert(
                    FinanceRecurringIncomeEntry(incomeRuleId, "2030-01-01", transactionId, 100)
                )
            }
        }
        assertEquals(before, rowCount("transactions"))

        expectFailure {
            db.withTransaction {
                val transactionId = db.dao().insert(
                    FinanceTransaction(100, "EXPENSE", "duplicate rent", walletId = walletId)
                )
                db.recurringExpenseEntryDao().insert(
                    FinanceRecurringExpenseEntry(expenseRuleId, "2030-01-01", transactionId, 100)
                )
            }
        }
        assertEquals(before, rowCount("transactions"))

        expectFailure {
            db.withTransaction {
                val transactionId = db.dao().insert(
                    FinanceTransaction(100, "EXPENSE", "duplicate bill", walletId = walletId)
                )
                db.billPaymentDao().insert(
                    FinanceBillPayment(billId, "2030-01-01", transactionId, 100)
                )
            }
        }
        assertEquals(before, rowCount("transactions"))
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Expected the database operation to fail", failed)
    }

    private fun rowCount(table: String): Int {
        require(table in setOf("transactions", "transfers"))
        return db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }
}
