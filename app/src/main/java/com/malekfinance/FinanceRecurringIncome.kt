package com.malekfinance

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

@Entity(tableName = "recurring_income_rules", indices = [Index(value = ["walletId"])])
data class FinanceRecurringIncomeRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val incomeType: String,
    val amountMinor: Long,
    val currencyCode: String,
    val walletId: Long,
    val frequency: String,
    val anchorDay: Int,
    val nextDueAt: Long,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recurring_income_entries",
    indices = [
        Index(value = ["ruleId"]),
        Index(value = ["ruleId", "occurrenceKey"], unique = true),
        Index(value = ["transactionId"], unique = true)
    ]
)
data class FinanceRecurringIncomeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val occurrenceKey: String,
    val transactionId: Long,
    val amountMinor: Long,
    val recordedAt: Long = System.currentTimeMillis()
)

@Dao
interface FinanceRecurringIncomeRuleDao {
    @Query("SELECT * FROM recurring_income_rules ORDER BY isActive DESC, nextDueAt ASC, id DESC")
    fun observeAll(): Flow<List<FinanceRecurringIncomeRule>>

    @Query("SELECT * FROM recurring_income_rules WHERE id = :ruleId LIMIT 1")
    suspend fun findById(ruleId: Long): FinanceRecurringIncomeRule?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: FinanceRecurringIncomeRule): Long

    @Query("UPDATE recurring_income_rules SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean): Int

    @Update
    suspend fun update(rule: FinanceRecurringIncomeRule)
}

@Dao
interface FinanceRecurringIncomeEntryDao {
    @Query("SELECT * FROM recurring_income_entries ORDER BY recordedAt DESC, id DESC")
    fun observeAll(): Flow<List<FinanceRecurringIncomeEntry>>

    @Query("SELECT * FROM recurring_income_entries WHERE ruleId = :ruleId ORDER BY recordedAt DESC, id DESC")
    suspend fun forRule(ruleId: Long): List<FinanceRecurringIncomeEntry>

    @Query("SELECT EXISTS(SELECT 1 FROM recurring_income_entries WHERE ruleId = :ruleId AND occurrenceKey = :occurrenceKey)")
    suspend fun exists(ruleId: Long, occurrenceKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: FinanceRecurringIncomeEntry): Long
}

object FinanceRecurringIncomePolicy {
    fun firstDueAt(now: Long, frequency: String, anchorDay: Int): Long? {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        when (frequency) {
            "WEEKLY" -> return now
            "MONTHLY" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val currentDay = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_MONTH)
                if (currentDay > anchorDay) calendar.add(Calendar.MONTH, 1)
                val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                calendar.set(Calendar.DAY_OF_MONTH, anchorDay.coerceIn(1, lastDay))
                return calendar.timeInMillis
            }
            else -> return null
        }
    }

    fun occurrenceKey(dueAt: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
        return "%04d-%02d-%02d".format(
            java.util.Locale.US,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun nextDueAt(dueAt: Long, frequency: String, anchorDay: Int): Long? {
        val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
        when (frequency) {
            "WEEKLY" -> calendar.add(Calendar.DAY_OF_MONTH, 7)
            "MONTHLY" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.MONTH, 1)
                val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                calendar.set(Calendar.DAY_OF_MONTH, anchorDay.coerceIn(1, lastDay))
            }
            else -> return null
        }
        return calendar.timeInMillis
    }

    fun isDue(nextDueAt: Long, now: Long): Boolean = now >= nextDueAt
}
