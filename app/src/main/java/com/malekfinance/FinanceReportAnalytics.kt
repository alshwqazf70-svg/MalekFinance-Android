package com.malekfinance

import java.math.BigInteger
import java.util.Calendar
import java.util.Locale

enum class FinanceReportRange { DAILY, WEEKLY, MONTHLY, YEARLY }

data class FinanceReportBucket(
    val label: String,
    val incomeMinor: BigInteger,
    val expenseMinor: BigInteger
)

data class FinanceCategoryTotal(val category: String, val amountMinor: BigInteger)

object FinanceReportAnalytics {
    fun customRange(
        transactions: List<FinanceTransaction>,
        walletId: Long,
        currencyCode: String,
        startInclusive: Long,
        endExclusive: Long
    ): FinanceReportBucket {
        require(endExclusive > startInclusive)
        val matching = transactions.filter {
            it.walletId == walletId && it.currencyCode == currencyCode &&
                it.createdAt >= startInclusive && it.createdAt < endExclusive
        }
        return FinanceReportBucket(
            label = "مخصص",
            incomeMinor = matching.filter { it.kind == "INCOME" && it.amountMinor > 0 }
                .fold(BigInteger.ZERO) { sum, row -> sum.add(BigInteger.valueOf(row.amountMinor)) },
            expenseMinor = matching.filter { it.kind == "EXPENSE" && it.amountMinor > 0 }
                .fold(BigInteger.ZERO) { sum, row -> sum.add(BigInteger.valueOf(row.amountMinor)) }
        )
    }

    fun customCategoryBreakdown(
        transactions: List<FinanceTransaction>,
        walletId: Long,
        currencyCode: String,
        startInclusive: Long,
        endExclusive: Long
    ): List<FinanceCategoryTotal> {
        require(endExclusive > startInclusive)
        return transactions.asSequence()
            .filter { it.walletId == walletId && it.currencyCode == currencyCode }
            .filter { it.kind == "EXPENSE" && it.amountMinor > 0 && it.createdAt >= startInclusive && it.createdAt < endExclusive }
            .groupBy { it.category.ifBlank { "عام" } }
            .map { (category, rows) ->
                FinanceCategoryTotal(category, rows.fold(BigInteger.ZERO) { sum, row -> sum.add(BigInteger.valueOf(row.amountMinor)) })
            }
            .sortedByDescending { it.amountMinor }
    }

    fun timeline(
        transactions: List<FinanceTransaction>,
        walletId: Long,
        currencyCode: String,
        range: FinanceReportRange,
        nowMillis: Long = System.currentTimeMillis()
    ): List<FinanceReportBucket> {
        val count = when (range) {
            FinanceReportRange.DAILY -> 7
            FinanceReportRange.WEEKLY -> 8
            FinanceReportRange.MONTHLY -> 12
            FinanceReportRange.YEARLY -> 5
        }
        val buckets = (count - 1 downTo 0).map { offset ->
            val start = boundary(nowMillis, range, -offset)
            val end = boundary(nowMillis, range, -offset + 1)
            val label = label(start, range)
            val matching = transactions.asSequence()
                .filter { it.walletId == walletId && it.currencyCode == currencyCode }
                .filter { it.createdAt >= start && it.createdAt < end }
                .toList()
            FinanceReportBucket(
                label = label,
                incomeMinor = matching.filter { it.kind == "INCOME" && it.amountMinor > 0 }.fold(BigInteger.ZERO) { a, t -> a.add(BigInteger.valueOf(t.amountMinor)) },
                expenseMinor = matching.filter { it.kind == "EXPENSE" && tAmountValid(it.amountMinor) }.fold(BigInteger.ZERO) { a, t -> a.add(BigInteger.valueOf(t.amountMinor)) }
            )
        }
        return buckets
    }

    fun categoryBreakdown(
        transactions: List<FinanceTransaction>,
        walletId: Long,
        currencyCode: String,
        range: FinanceReportRange,
        nowMillis: Long = System.currentTimeMillis()
    ): List<FinanceCategoryTotal> {
        val start = boundary(nowMillis, range, 0)
        val totals = transactions.asSequence()
            .filter { it.walletId == walletId && it.currencyCode == currencyCode }
            .filter { it.kind == "EXPENSE" && it.amountMinor > 0 && it.createdAt >= start && it.createdAt <= nowMillis }
            .groupBy { it.category.ifBlank { "عام" } }
            .map { (category, rows) ->
                FinanceCategoryTotal(category, rows.fold(BigInteger.ZERO) { sum, row -> sum.add(BigInteger.valueOf(row.amountMinor)) })
            }
        return totals.sortedByDescending { it.amountMinor }
    }

    private fun tAmountValid(value: Long) = value > 0L

    private fun boundary(nowMillis: Long, range: FinanceReportRange, offset: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        when (range) {
            FinanceReportRange.DAILY -> {
                cal.add(Calendar.DAY_OF_YEAR, offset)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            }
            FinanceReportRange.WEEKLY -> {
                val daysFromMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday + offset * 7)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            }
            FinanceReportRange.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.MONTH, offset)
            }
            FinanceReportRange.YEARLY -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.YEAR, offset)
            }
        }
        return cal.timeInMillis
    }

    private fun label(timestamp: Long, range: FinanceReportRange): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when (range) {
            FinanceReportRange.DAILY -> String.format(Locale.ROOT, "%02d/%02d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
            FinanceReportRange.WEEKLY -> "أسبوع " + String.format(Locale.ROOT, "%02d/%04d", cal.get(Calendar.WEEK_OF_YEAR), cal.get(Calendar.YEAR))
            FinanceReportRange.MONTHLY -> String.format(Locale.ROOT, "%02d/%04d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
            FinanceReportRange.YEARLY -> cal.get(Calendar.YEAR).toString()
        }
    }
}
