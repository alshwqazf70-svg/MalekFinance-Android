package com.malekfinance

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

@Entity(tableName = "savings_goals", indices = [Index(value = ["walletId"])])
data class FinanceSavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val currencyCode: String,
    val walletId: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "savings_entries",
    indices = [Index(value = ["goalId"]), Index(value = ["transactionId"], unique = true)]
)
data class FinanceSavingsEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val transactionId: Long,
    val entryType: String,
    val amountMinor: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface FinanceSavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<FinanceSavingsGoal>>

    @Query("SELECT * FROM savings_goals WHERE id = :goalId LIMIT 1")
    suspend fun findById(goalId: Long): FinanceSavingsGoal?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: FinanceSavingsGoal): Long
}

@Dao
interface FinanceSavingsEntryDao {
    @Query("SELECT * FROM savings_entries ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<FinanceSavingsEntry>>

    @Query("SELECT * FROM savings_entries WHERE goalId = :goalId ORDER BY createdAt ASC, id ASC")
    suspend fun forGoal(goalId: Long): List<FinanceSavingsEntry>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: FinanceSavingsEntry): Long
}

object FinanceSavingsPolicy {
    fun balance(entries: List<FinanceSavingsEntry>): BigInteger =
        entries.fold(BigInteger.ZERO) { total, entry ->
            if (entry.amountMinor <= 0L) return@fold total
            val amount = BigInteger.valueOf(entry.amountMinor)
            when (entry.entryType) {
                "CONTRIBUTION" -> total.add(amount)
                "WITHDRAWAL" -> total.subtract(amount)
                else -> total
            }
        }

    fun canContribute(targetMinor: Long, entries: List<FinanceSavingsEntry>, amountMinor: Long): Boolean =
        targetMinor > 0L && amountMinor > 0L &&
            BigInteger.valueOf(amountMinor) <= BigInteger.valueOf(targetMinor).subtract(balance(entries)).max(BigInteger.ZERO)

    fun canWithdraw(entries: List<FinanceSavingsEntry>, amountMinor: Long): Boolean =
        amountMinor > 0L && BigInteger.valueOf(amountMinor) <= balance(entries).max(BigInteger.ZERO)

    fun progressPercent(balanceMinor: BigInteger, targetMinor: Long): Int {
        if (targetMinor <= 0L) return 0
        return java.math.BigDecimal(balanceMinor.max(BigInteger.ZERO))
            .multiply(java.math.BigDecimal("100"))
            .divide(java.math.BigDecimal.valueOf(targetMinor), 0, java.math.RoundingMode.HALF_UP)
            .min(java.math.BigDecimal.valueOf(Int.MAX_VALUE.toLong()))
            .toInt()
    }
}
