package com.malekfinance

import java.math.BigInteger
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinanceInsightsTest {
    private fun date(month: Int = Calendar.NOVEMBER, day: Int = 25, hour: Int = 12): Long = Calendar.getInstance().apply {
        clear(); set(2026, month, day, hour, 0, 0)
    }.timeInMillis
    private fun row(amount: Long, kind: String = "EXPENSE", currency: String = "YER", at: Long = date(), category: String = "عام") =
        FinanceTransaction(amountMinor = amount, kind = kind, note = "test", currencyCode = currency, createdAt = at, category = category)

    @Test fun isolatesCurrencyAndExcludesInvalidFutureAndNonIncomeExpenseKinds() {
        val snapshot = FinanceInsights.snapshot(listOf(
            row(1000), row(5000, "INCOME"), row(99_000, currency = "USD"), row(-500),
            row(30_000, "DEBT_RECEIVED"), row(20_000, "SAVINGS_CONTRIBUTION"), row(100_000, at = date(day = 26))
        ), "YER", date())
        assertEquals(BigInteger.valueOf(1000), snapshot.expense)
        assertEquals(BigInteger.valueOf(5000), snapshot.income)
        assertEquals(BigInteger.valueOf(4000), snapshot.net)
    }

    @Test fun separatesCurrentAndPreviousCalendarMonths() {
        val snapshot = FinanceInsights.snapshot(listOf(
            row(100, at = date(day = 1, hour = 0)), row(200, at = date(Calendar.OCTOBER, 31)),
            row(400, at = date(Calendar.SEPTEMBER, 30))
        ), "YER", date())
        assertEquals(BigInteger.valueOf(100), snapshot.expense)
        assertEquals(BigInteger.valueOf(200), snapshot.previousExpense)
    }

    @Test fun weeklyPulseIncludesZeroDaysInChronologicalOrder() {
        val snapshot = FinanceInsights.snapshot(listOf(row(500, at = date(day = 19)), row(900), row(800, at = date(day = 18))), "YER", date())
        assertEquals(listOf(500L, 0L, 0L, 0L, 0L, 0L, 900L).map(BigInteger::valueOf), snapshot.dailyExpenses)
    }

    @Test fun totalsCannotOverflowLong() {
        val snapshot = FinanceInsights.snapshot(listOf(row(Long.MAX_VALUE), row(Long.MAX_VALUE)), "YER", date())
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), snapshot.expense)
    }

    @Test fun topCategoryUsesOnlyCurrentMonthExpenses() {
        val snapshot = FinanceInsights.snapshot(listOf(row(500, category = "طعام"), row(200, category = "طعام"), row(600, category = "نقل"), row(5000, "INCOME", category = "راتب")), "YER", date())
        assertEquals("طعام", snapshot.topCategory)
        assertEquals(BigInteger.valueOf(700), snapshot.topCategoryAmount)
    }

    @Test fun scenarioUsesElapsedDaysAndDoesNotChangeRecords() {
        val rows = listOf(row(10_000))
        val snapshot = FinanceInsights.snapshot(rows, "YER", date())
        assertEquals(BigInteger.valueOf(2400), snapshot.monthlySavingScenario(20))
        assertEquals(BigInteger.ZERO, snapshot.monthlySavingScenario(0))
        assertEquals(10_000L, rows.single().amountMinor)
    }

    @Test fun emptyStateDoesNotInventFinancialData() {
        val snapshot = FinanceInsights.snapshot(emptyList(), "YER", date())
        assertNull(snapshot.topCategory)
        assertEquals(BigInteger.ZERO, snapshot.expense)
        assertEquals(BigInteger.ZERO, snapshot.monthlySavingScenario(50))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scenarioRejectsInvalidPercentage() {
        FinanceInsights.snapshot(emptyList(), "YER", date()).monthlySavingScenario(101)
    }
}
