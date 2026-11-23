package com.malekfinance

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceReportAnalyticsTest {
    private fun time(day: Int, month: Int = Calendar.JUNE, year: Int = 2026): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, 12, 0, 0) }.timeInMillis

    @Test
    fun monthlyTimelineSeparatesIncomeExpenseAndCurrency() {
        val rows = listOf(
            FinanceTransaction(amountMinor = 10_000, kind = "INCOME", note = "salary", walletId = 1, currencyCode = "YER", createdAt = time(10)),
            FinanceTransaction(amountMinor = 2_500, kind = "EXPENSE", note = "food", walletId = 1, currencyCode = "YER", createdAt = time(11)),
            FinanceTransaction(amountMinor = 99_000, kind = "EXPENSE", note = "other wallet", walletId = 2, currencyCode = "YER", createdAt = time(11)),
            FinanceTransaction(amountMinor = 88_000, kind = "INCOME", note = "other currency", walletId = 1, currencyCode = "USD", createdAt = time(11))
        )
        val timeline = FinanceReportAnalytics.timeline(rows, 1, "YER", FinanceReportRange.MONTHLY, time(20))
        val currentMonth = timeline.last()
        assertEquals("10000", currentMonth.incomeMinor.toString())
        assertEquals("2500", currentMonth.expenseMinor.toString())
        assertEquals(12, timeline.size)
    }

    @Test
    fun categoryBreakdownGroupsOnlyPositiveExpensesForCurrentRange() {
        val rows = listOf(
            FinanceTransaction(amountMinor = 1_000, kind = "EXPENSE", note = "a", category = "طعام", walletId = 1, currencyCode = "YER", createdAt = time(4)),
            FinanceTransaction(amountMinor = 2_000, kind = "EXPENSE", note = "b", category = "طعام", walletId = 1, currencyCode = "YER", createdAt = time(5)),
            FinanceTransaction(amountMinor = 3_000, kind = "EXPENSE", note = "c", category = "نقل", walletId = 1, currencyCode = "YER", createdAt = time(6)),
            FinanceTransaction(amountMinor = 9_000, kind = "INCOME", note = "not expense", category = "راتب", walletId = 1, currencyCode = "YER", createdAt = time(6))
        )
        val result = FinanceReportAnalytics.categoryBreakdown(rows, 1, "YER", FinanceReportRange.MONTHLY, time(20))
        assertEquals("طعام", result.first().category)
        assertEquals("3000", result.first().amountMinor.toString())
        assertEquals(2, result.size)
    }

    @Test
    fun yearlyTimelineContainsFiveYearsAndDoesNotMixWallets() {
        val rows = listOf(
            FinanceTransaction(amountMinor = 1_000, kind = "INCOME", note = "this year", walletId = 1, currencyCode = "YER", createdAt = time(5)),
            FinanceTransaction(amountMinor = 9_000, kind = "INCOME", note = "other wallet", walletId = 3, currencyCode = "YER", createdAt = time(5))
        )
        val timeline = FinanceReportAnalytics.timeline(rows, 1, "YER", FinanceReportRange.YEARLY, time(20))
        assertEquals(5, timeline.size)
        assertEquals("1000", timeline.last().incomeMinor.toString())
    }
}
