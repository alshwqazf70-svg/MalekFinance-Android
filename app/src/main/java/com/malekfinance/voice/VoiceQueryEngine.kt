package com.malekfinance.voice

import com.malekfinance.FinanceTransaction
import java.math.BigInteger
import java.util.Calendar
import java.util.Locale

data class VoiceQueryResult(
    val amountMinor: BigInteger,
    val currencyCode: String,
    val periodLabel: String
)

object VoiceQueryEngine {
    fun calculate(
        transactions: List<FinanceTransaction>,
        kind: String,
        walletId: Long,
        currencyCode: String,
        period: String?,
        nowMillis: Long = System.currentTimeMillis(),
        category: String? = null
    ): VoiceQueryResult {
        require(kind == "INCOME" || kind == "EXPENSE")
        val start = periodStart(period ?: "MONTH", nowMillis)
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            when (period ?: "MONTH") {
                "TODAY", "YESTERDAY" -> add(Calendar.DAY_OF_YEAR, 1)
                "WEEK" -> add(Calendar.WEEK_OF_YEAR, 1)
                "YEAR" -> add(Calendar.YEAR, 1)
                else -> add(Calendar.MONTH, 1)
            }
        }.timeInMillis
        val total = transactions.asSequence()
            .filter { it.walletId == walletId && it.currencyCode == currencyCode }
            .filter { it.kind == kind && it.amountMinor > 0L }
            .filter { category == null || it.category.equals(category, ignoreCase = true) }
            .filter { it.createdAt >= start.timeInMillis && it.createdAt < end }
            .fold(BigInteger.ZERO) { sum, item -> sum.add(BigInteger.valueOf(item.amountMinor)) }
        return VoiceQueryResult(total, currencyCode, period ?: "MONTH")
    }

    fun formatAmount(result: VoiceQueryResult): String {
        val major = result.amountMinor.divide(BigInteger.valueOf(100))
        val minor = result.amountMinor.remainder(BigInteger.valueOf(100)).abs()
        return String.format(Locale.ROOT, "%s.%02d %s", major.toString(), minor.toInt(), result.currencyCode)
    }

    private fun periodStart(period: String, nowMillis: Long): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            when (period) {
                "TODAY" -> Unit
                "YESTERDAY" -> add(Calendar.DAY_OF_YEAR, -1)
                "WEEK" -> {
                    val daysFromMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
                    add(Calendar.DAY_OF_YEAR, -daysFromMonday)
                }
                "YEAR" -> {
                    set(Calendar.DAY_OF_YEAR, 1)
                }
                else -> set(Calendar.DAY_OF_MONTH, 1)
            }
        }
}
