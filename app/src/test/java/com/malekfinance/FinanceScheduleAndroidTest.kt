package com.malekfinance

import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real Room/SQLite under Robolectric; no Android emulator or external speech service. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinanceScheduleAndroidTest {
    private lateinit var db: FinanceDb
    private var walletId: Long = 0
    private val due = 1_700_000_000_000L

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FinanceDb::class.java)
            .allowMainThreadQueries().build()
        walletId = db.walletDao().insert(FinanceWallet(name = "اختبار", openingBalanceMinor = 1_000_000))
    }

    @After fun tearDown() { if (::db.isInitialized) db.close() }

    private fun bill(frequency: String = "MONTHLY") = FinanceBill(
        title = "فاتورة", categoryName = "عام", amountMinor = 100, currencyCode = "YER", walletId = walletId,
        frequency = frequency, anchorDay = 15, anchorMonth = 0, nextDueAt = due
    )

    @Test fun statusOnlyUpdateCannotOverwriteAnAdvancedBill() = runBlocking {
        val id = db.billDao().insert(bill())
        val stale = db.billDao().findById(id)!!
        val next = due + 86_400_000L
        db.billDao().update(stale.copy(nextDueAt = next, title = "updated", lastReminderOccurrenceKey = "delivered"))
        assertEquals(1, db.billDao().setActive(id, !stale.isActive))
        val actual = db.billDao().findById(id)!!
        assertEquals(next, actual.nextDueAt)
        assertEquals("updated", actual.title)
        assertEquals("delivered", actual.lastReminderOccurrenceKey)
        assertFalse(actual.isActive)
    }

    @Test fun recurringStatusUpdatesKeepScheduleAndMetadata() = runBlocking {
        val incomeId = db.recurringIncomeRuleDao().insert(FinanceRecurringIncomeRule(
            title = "راتب", incomeType = "SALARY", amountMinor = 100, currencyCode = "YER", walletId = walletId,
            frequency = "MONTHLY", anchorDay = 15, nextDueAt = due))
        val expenseId = db.recurringExpenseRuleDao().insert(FinanceRecurringExpenseRule(
            title = "إيجار", categoryName = "عام", amountMinor = 100, currencyCode = "YER", walletId = walletId,
            frequency = "MONTHLY", anchorDay = 15, nextDueAt = due))
        db.withTransaction {
            db.recurringIncomeRuleDao().update(db.recurringIncomeRuleDao().findById(incomeId)!!.copy(nextDueAt = due + 1000, amountMinor = 200))
            db.recurringExpenseRuleDao().update(db.recurringExpenseRuleDao().findById(expenseId)!!.copy(nextDueAt = due + 2000, amountMinor = 300))
        }
        assertFalse(FinanceScheduleOperations.toggleIncome(db, incomeId))
        assertFalse(FinanceScheduleOperations.toggleExpense(db, expenseId))
        assertEquals(due + 1000, db.recurringIncomeRuleDao().findById(incomeId)!!.nextDueAt)
        assertEquals(200L, db.recurringIncomeRuleDao().findById(incomeId)!!.amountMinor)
        assertEquals(due + 2000, db.recurringExpenseRuleDao().findById(expenseId)!!.nextDueAt)
        assertEquals(300L, db.recurringExpenseRuleDao().findById(expenseId)!!.amountMinor)
        assertTrue(FinanceScheduleOperations.toggleIncome(db, incomeId))
        assertTrue(FinanceScheduleOperations.toggleExpense(db, expenseId))
    }

    @Test fun paidOneTimeBillCannotBeReactivated() = runBlocking {
        val id = db.billDao().insert(bill("ONCE"))
        FinanceBillOperations.pay(db, id, due)
        assertTrue(runCatching { FinanceScheduleOperations.toggleBill(db, id) }.isFailure)
        assertFalse(db.billDao().findById(id)!!.isActive)
        assertEquals(1, db.billPaymentDao().forBill(id).size)
    }

    @Test fun simultaneousBillPaymentAndToggleNeverLoseScheduleProgress() = runBlocking {
        repeat(8) {
            val id = db.billDao().insert(bill())
            coroutineScope {
                listOf(
                    async(Dispatchers.Default) { runCatching { FinanceBillOperations.pay(db, id, due) } },
                    async(Dispatchers.Default) { runCatching { FinanceScheduleOperations.toggleBill(db, id) } }
                ).awaitAll()
            }
            val payments = db.billPaymentDao().forBill(id)
            val actual = db.billDao().findById(id)!!
            assertFalse(actual.isActive)
            assertTrue(payments.size in 0..1)
            val expectedDue = if (payments.isEmpty()) due else FinanceBillPolicy.nextDueAt(due, "MONTHLY", 15, 0)!!
            assertEquals(expectedDue, actual.nextDueAt)
            assertEquals(payments.size, db.dao().forWallet(walletId).count { it.billId == id })
        }
    }
}
