package com.malekfinance

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Calendar
import java.util.TimeZone

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryName", "currencyCode", "periodStartMillis"], unique = true)]
)
data class FinanceBudget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val currencyCode: String,
    val periodStartMillis: Long,
    val limitMinor: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface FinanceBudgetDao {
    @Query("SELECT * FROM budgets ORDER BY periodStartMillis DESC, categoryName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FinanceBudget>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: FinanceBudget): Long

    @Query("DELETE FROM budgets WHERE id = :budgetId")
    suspend fun delete(budgetId: Long): Int
}

/** Month-scoped, currency-isolated budget calculations. */
object FinanceBudgetPolicy {
    data class MonthWindow(val startMillis: Long, val endExclusiveMillis: Long)

    fun monthWindow(
        atMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): MonthWindow {
        val start = Calendar.getInstance(timeZone).apply {
            timeInMillis = atMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return MonthWindow(start.timeInMillis, end.timeInMillis)
    }

    fun calculateSpent(
        budget: FinanceBudget,
        transactions: List<FinanceTransaction>,
        monthEndExclusiveMillis: Long
    ): BigInteger = transactions.asSequence()
        .filter {
            it.kind == "EXPENSE" &&
                it.category == budget.categoryName &&
                it.currencyCode == budget.currencyCode &&
                it.createdAt >= budget.periodStartMillis &&
                it.createdAt < monthEndExclusiveMillis
        }
        .fold(BigInteger.ZERO) { total, transaction ->
            total.add(BigInteger.valueOf(transaction.amountMinor))
        }

    fun usagePercent(spentMinor: BigInteger, limitMinor: Long): Int {
        if (limitMinor <= 0L) return 0
        return BigDecimal(spentMinor)
            .multiply(BigDecimal("100"))
            .divide(BigDecimal.valueOf(limitMinor), 0, RoundingMode.HALF_UP)
            .min(BigDecimal.valueOf(Int.MAX_VALUE.toLong()))
            .toInt()
    }

    fun alertLevel(spentMinor: BigInteger, limitMinor: Long): String {
        if (limitMinor <= 0L) return "INVALID"
        val spent = spentMinor
        val limit = BigInteger.valueOf(limitMinor)
        return when {
            spent >= limit -> "OVER_LIMIT"
            spent.multiply(BigInteger.valueOf(100)) >= limit.multiply(BigInteger.valueOf(80)) -> "NEAR_LIMIT"
            else -> "NORMAL"
        }
    }
}
