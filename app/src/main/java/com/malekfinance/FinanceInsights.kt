package com.malekfinance

import java.math.BigInteger
import java.util.Calendar

/** Local, explainable summaries, never predictions of guaranteed future income. */
data class FinanceInsightSnapshot(
    val income: BigInteger,
    val expense: BigInteger,
    val previousExpense: BigInteger,
    val topCategory: String?,
    val topCategoryAmount: BigInteger,
    val dailyExpenses: List<BigInteger>,
    val elapsedDays: Int
) {
    val net: BigInteger get() = income.subtract(expense)
    fun monthlySavingScenario(reductionPercent: Int): BigInteger {
        require(reductionPercent in 0..100)
        // A 30-day illustration based on this month's elapsed calendar days.
        return expense.multiply(BigInteger.valueOf(30L * reductionPercent))
            .divide(BigInteger.valueOf(elapsedDays.coerceAtLeast(1).toLong() * 100L))
    }
}

object FinanceInsights {
    fun snapshot(rows: List<FinanceTransaction>, currency: String, now: Long): FinanceInsightSnapshot {
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val month = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val previous = (month.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val valid = rows.filter { it.currencyCode == currency && it.amountMinor > 0 && it.createdAt <= now }
        val current = valid.filter { it.createdAt >= month.timeInMillis }
        fun total(items: List<FinanceTransaction>, kind: String) = items.filter { it.kind == kind }
            .fold(BigInteger.ZERO) { sum, row -> sum.add(BigInteger.valueOf(row.amountMinor)) }
        val categories = current.filter { it.kind == "EXPENSE" }.groupBy { it.category.ifBlank { "عام" } }
            .mapValues { (_, items) -> total(items, "EXPENSE") }
        val top = categories.entries.sortedWith(compareByDescending<Map.Entry<String, BigInteger>> { it.value }.thenBy { it.key }).firstOrNull()
        val daily = (6 downTo 0).map { offset ->
            val start = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            total(valid.filter { it.createdAt >= start.timeInMillis && it.createdAt < end.timeInMillis }, "EXPENSE")
        }
        return FinanceInsightSnapshot(
            total(current, "INCOME"), total(current, "EXPENSE"),
            total(valid.filter { it.createdAt >= previous.timeInMillis && it.createdAt < month.timeInMillis }, "EXPENSE"),
            top?.key, top?.value ?: BigInteger.ZERO, daily, today.get(Calendar.DAY_OF_MONTH)
        )
    }
}
