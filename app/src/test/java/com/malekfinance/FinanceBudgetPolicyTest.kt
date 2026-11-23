package com.malekfinance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.Calendar
import java.util.TimeZone

class FinanceBudgetPolicyTest {
    @Test
    fun monthWindowUsesCalendarMonthBoundaries() {
        val utc = TimeZone.getTimeZone("UTC")
        val at = Calendar.getInstance(utc).apply {
            set(2024, Calendar.FEBRUARY, 15, 12, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val window = FinanceBudgetPolicy.monthWindow(at, utc)
        val start = Calendar.getInstance(utc).apply { timeInMillis = window.startMillis }
        val end = Calendar.getInstance(utc).apply { timeInMillis = window.endExclusiveMillis }
        assertEquals(1, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.FEBRUARY, start.get(Calendar.MONTH))
        assertEquals(1, end.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MARCH, end.get(Calendar.MONTH))
    }

    @Test
    fun spendingIncludesOnlyMatchingExpenseCategoryCurrencyAndMonth() {
        val budget = FinanceBudget(
            categoryName = "مطاعم",
            currencyCode = "YER",
            periodKey = "1970-01",
            limitMinor = 10_000L
        )
        val rows = listOf(
            FinanceTransaction(amountMinor = 2_500, kind = "EXPENSE", note = "غداء", category = "مطاعم", createdAt = 2_000, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 1_000, kind = "INCOME", note = "دخل", category = "مطاعم", createdAt = 3_000, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 9_000, kind = "EXPENSE", note = "غداء", category = "مطاعم", createdAt = 4_000, currencyCode = "USD"),
            FinanceTransaction(amountMinor = 7_000, kind = "EXPENSE", note = "وقود", category = "بنزين", createdAt = 5_000, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 4_000, kind = "EXPENSE", note = "غداء", category = "مطاعم", createdAt = 10_000, currencyCode = "YER")
        )
        assertEquals(BigInteger.valueOf(2_500), FinanceBudgetPolicy.calculateSpent(budget, rows, 10_000))
    }

    @Test
    fun budgetAlertsAtEightyAndOneHundredPercent() {
        assertEquals("NORMAL", FinanceBudgetPolicy.alertLevel(BigInteger.valueOf(7_999), 10_000))
        assertEquals("NEAR_LIMIT", FinanceBudgetPolicy.alertLevel(BigInteger.valueOf(8_000), 10_000))
        assertEquals("OVER_LIMIT", FinanceBudgetPolicy.alertLevel(BigInteger.valueOf(10_000), 10_000))
        assertTrue(FinanceBudgetPolicy.usagePercent(BigInteger.valueOf(12_500), 10_000) >= 100)
    }
}
