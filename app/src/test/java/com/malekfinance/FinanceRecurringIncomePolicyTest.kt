package com.malekfinance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinanceRecurringIncomePolicyTest {
    private fun date(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 30, 0)
        }.timeInMillis

    private fun parts(timestamp: Long): List<Int> =
        Calendar.getInstance().apply { timeInMillis = timestamp }.let {
            listOf(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.HOUR_OF_DAY))
        }

    @Test
    fun monthlyScheduleClampsFebruaryAndRestoresOriginalAnchorDay() {
        val january31 = date(2025, 1, 31)
        val february = FinanceRecurringIncomePolicy.nextDueAt(january31, "MONTHLY", 31)!!
        val march = FinanceRecurringIncomePolicy.nextDueAt(february, "MONTHLY", 31)!!
        assertEquals(listOf(2025, 2, 28, 9), parts(february))
        assertEquals(listOf(2025, 3, 31, 9), parts(march))
    }

    @Test
    fun monthlyScheduleHandlesLeapFebruary() {
        val january31 = date(2024, 1, 31)
        val february = FinanceRecurringIncomePolicy.nextDueAt(january31, "MONTHLY", 31)!!
        assertEquals(listOf(2024, 2, 29, 9), parts(february))
    }

    @Test
    fun weeklyScheduleAdvancesExactlySevenCalendarDays() {
        val monday = date(2025, 4, 7)
        val next = FinanceRecurringIncomePolicy.nextDueAt(monday, "WEEKLY", 7)!!
        assertEquals(listOf(2025, 4, 14, 9), parts(next))
    }

    @Test
    fun firstMonthlyDueUsesTheSelectedDayInTheCurrentMonthWhenStillAhead() {
        val now = date(2025, 4, 10, 12)
        val due = FinanceRecurringIncomePolicy.firstDueAt(now, "MONTHLY", 25)!!
        assertEquals(listOf(2025, 4, 25, 12), parts(due))
    }

    @Test
    fun firstMonthlyDueMovesToNextMonthWhenSelectedDayHasPassed() {
        val now = date(2025, 4, 26, 12)
        val due = FinanceRecurringIncomePolicy.firstDueAt(now, "MONTHLY", 25)!!
        assertEquals(listOf(2025, 5, 25, 12), parts(due))
    }

    @Test
    fun firstMonthlyDueClampsToMonthLengthWithoutLosingAnchorDay() {
        val now = date(2025, 2, 1, 12)
        val due = FinanceRecurringIncomePolicy.firstDueAt(now, "MONTHLY", 31)!!
        assertEquals(listOf(2025, 2, 28, 12), parts(due))
        val following = FinanceRecurringIncomePolicy.nextDueAt(due, "MONTHLY", 31)!!
        assertEquals(listOf(2025, 3, 31, 12), parts(following))
    }

    @Test
    fun firstWeeklyDueIsImmediatelyReviewable() {
        val now = date(2025, 4, 10, 12)
        assertEquals(now, FinanceRecurringIncomePolicy.firstDueAt(now, "WEEKLY", 10))
    }

    @Test
    fun occurrenceKeyIsStableForTheScheduledLocalDate() {
        val first = date(2025, 8, 19, 8)
        val sameDayLater = date(2025, 8, 19, 22)
        assertEquals("2025-08-19", FinanceRecurringIncomePolicy.occurrenceKey(first))
        assertEquals(FinanceRecurringIncomePolicy.occurrenceKey(first), FinanceRecurringIncomePolicy.occurrenceKey(sameDayLater))
    }

    @Test
    fun dueCheckDoesNotAllowPrematurePosting() {
        val dueAt = date(2025, 8, 19)
        assertFalse(FinanceRecurringIncomePolicy.isDue(dueAt, dueAt - 1))
        assertTrue(FinanceRecurringIncomePolicy.isDue(dueAt, dueAt))
        assertTrue(FinanceRecurringIncomePolicy.isDue(dueAt, dueAt + 1))
    }

    @Test
    fun confirmedRecurringSalaryIsPostedAsNormalIncomeToItsWallet() {
        val wallet = FinanceWallet(id = 4, name = "الحساب البنكي", currencyCode = "YER", openingBalanceMinor = 2_000)
        val salary = FinanceTransaction(
            amountMinor = 75_000,
            kind = "INCOME",
            note = "راتب: الراتب الشهري",
            category = "راتب",
            walletId = wallet.id,
            currencyCode = wallet.currencyCode,
            recurringIncomeRuleId = 9
        )
        assertEquals(java.math.BigInteger.valueOf(77_000), FinanceLedger.calculateBalance(wallet, listOf(salary), emptyList()))
        assertEquals(9L, salary.recurringIncomeRuleId)
    }

    @Test
    fun unsupportedFrequencyIsRejected() {
        assertNull(FinanceRecurringIncomePolicy.nextDueAt(date(2025, 1, 1), "DAILY", 1))
    }
}
