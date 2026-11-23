package com.malekfinance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinanceRecurringExpensePolicyTest {
    private fun date(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, 30, 0) }.timeInMillis

    private fun parts(timestamp: Long): List<Int> =
        Calendar.getInstance().apply { timeInMillis = timestamp }.let {
            listOf(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.HOUR_OF_DAY))
        }

    @Test
    fun monthlyScheduleClampsFebruaryAndRestoresAnchorDay() {
        val february = FinanceRecurringExpensePolicy.nextDueAt(date(2025, 1, 31), "MONTHLY", 31)!!
        val march = FinanceRecurringExpensePolicy.nextDueAt(february, "MONTHLY", 31)!!
        assertEquals(listOf(2025, 2, 28, 9), parts(february))
        assertEquals(listOf(2025, 3, 31, 9), parts(march))
    }

    @Test
    fun monthlyScheduleSupportsLeapYear() {
        val february = FinanceRecurringExpensePolicy.nextDueAt(date(2024, 1, 31), "MONTHLY", 31)!!
        assertEquals(listOf(2024, 2, 29, 9), parts(february))
    }

    @Test
    fun weeklyScheduleAdvancesSevenCalendarDays() {
        assertEquals(listOf(2025, 4, 14, 9), parts(FinanceRecurringExpensePolicy.nextDueAt(date(2025, 4, 7), "WEEKLY", 7)!!))
    }

    @Test
    fun firstMonthlyDueChoosesCurrentOrNextMonthCorrectly() {
        assertEquals(listOf(2025, 4, 25, 12), parts(FinanceRecurringExpensePolicy.firstDueAt(date(2025, 4, 10, 12), "MONTHLY", 25)!!))
        assertEquals(listOf(2025, 5, 25, 12), parts(FinanceRecurringExpensePolicy.firstDueAt(date(2025, 4, 26, 12), "MONTHLY", 25)!!))
    }

    @Test
    fun occurrenceKeyUsesScheduledDateAndDueCheckBlocksEarlyPosting() {
        val due = date(2025, 8, 19)
        assertEquals("2025-08-19", FinanceRecurringExpensePolicy.occurrenceKey(due))
        assertFalse(FinanceRecurringExpensePolicy.isDue(due, due - 1))
        assertTrue(FinanceRecurringExpensePolicy.isDue(due, due))
    }

    @Test
    fun confirmedRecurringExpenseReducesWalletBalanceAndIsLinkedToRule() {
        val wallet = FinanceWallet(id = 4, name = "المحفظة", currencyCode = "YER", openingBalanceMinor = 100_000)
        val expense = FinanceTransaction(
            amountMinor = 25_000,
            kind = "EXPENSE",
            note = "مصروف دوري: الإنترنت",
            category = "فواتير",
            walletId = wallet.id,
            currencyCode = wallet.currencyCode,
            recurringExpenseRuleId = 12
        )
        assertEquals(java.math.BigInteger.valueOf(75_000), FinanceLedger.calculateBalance(wallet, listOf(expense), emptyList()))
        assertEquals(12L, expense.recurringExpenseRuleId)
    }

    @Test
    fun unsupportedFrequencyIsRejected() {
        assertNull(FinanceRecurringExpensePolicy.nextDueAt(date(2025, 1, 1), "DAILY", 1))
        assertNull(FinanceRecurringExpensePolicy.firstDueAt(date(2025, 1, 1), "DAILY", 1))
    }
}
