package com.malekfinance.voice

import com.malekfinance.FinanceTransaction
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceQueryEngineTest {
    private fun date(day: Int, month: Int = Calendar.JUNE, year: Int = 2026): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis

    @Test
    fun spendingQueryIsScopedToWalletCurrencyAndPeriod() {
        val items = listOf(
            FinanceTransaction(amountMinor = 1_000, kind = "EXPENSE", note = "a", walletId = 1, currencyCode = "YER", createdAt = date(4)),
            FinanceTransaction(amountMinor = 2_000, kind = "EXPENSE", note = "b", walletId = 1, currencyCode = "YER", createdAt = date(12)),
            FinanceTransaction(amountMinor = 9_000, kind = "EXPENSE", note = "other wallet", walletId = 2, currencyCode = "YER", createdAt = date(12)),
            FinanceTransaction(amountMinor = 8_000, kind = "EXPENSE", note = "other currency", walletId = 1, currencyCode = "USD", createdAt = date(12)),
            FinanceTransaction(amountMinor = 7_000, kind = "EXPENSE", note = "previous month", walletId = 1, currencyCode = "YER", createdAt = date(31, Calendar.MAY))
        )
        val result = VoiceQueryEngine.calculate(items, "EXPENSE", 1, "YER", "MONTH", date(20))
        assertEquals("3000", result.amountMinor.toString())
    }

    @Test
    fun incomeAndExpenseTotalsRemainSeparate() {
        val items = listOf(
            FinanceTransaction(amountMinor = 50_000, kind = "INCOME", note = "salary", walletId = 1, currencyCode = "YER", createdAt = date(5)),
            FinanceTransaction(amountMinor = 12_500, kind = "EXPENSE", note = "rent", walletId = 1, currencyCode = "YER", createdAt = date(5))
        )
        assertEquals("50000", VoiceQueryEngine.calculate(items, "INCOME", 1, "YER", "MONTH", date(20)).amountMinor.toString())
        assertEquals("12500", VoiceQueryEngine.calculate(items, "EXPENSE", 1, "YER", "MONTH", date(20)).amountMinor.toString())
    }

    @Test
    fun formatsMinorUnitsWithoutFloatingPoint() {
        val result = VoiceQueryEngine.calculate(
            listOf(FinanceTransaction(amountMinor = 12_345, kind = "EXPENSE", note = "x", walletId = 1, currencyCode = "YER", createdAt = date(8))),
            "EXPENSE", 1, "YER", "MONTH", date(20)
        )
        assertEquals("123.45 YER", VoiceQueryEngine.formatAmount(result))
    }
}
