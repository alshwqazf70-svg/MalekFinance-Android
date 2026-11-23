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
import java.util.Locale

@Entity(
    tableName = "bills",
    indices = [Index(value = ["walletId"]), Index(value = ["nextDueAt"])]
)
data class FinanceBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryName: String,
    val amountMinor: Long,
    val currencyCode: String,
    val walletId: Long,
    /** ONCE, MONTHLY, or YEARLY. */
    val frequency: String,
    val anchorDay: Int,
    /** Zero-based month used by yearly bills. */
    val anchorMonth: Int,
    val nextDueAt: Long,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "NULL") val lastReminderOccurrenceKey: String? = null
)

@Entity(
    tableName = "bill_payments",
    indices = [
        Index(value = ["billId"]),
        Index(value = ["billId", "occurrenceKey"], unique = true),
        Index(value = ["transactionId"], unique = true)
    ]
)
data class FinanceBillPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val occurrenceKey: String,
    val transactionId: Long,
    val amountMinor: Long,
    val paidAt: Long = System.currentTimeMillis()
)

@Dao
interface FinanceBillDao {
    @Query("SELECT * FROM bills ORDER BY isActive DESC, nextDueAt ASC, id DESC")
    fun observeAll(): Flow<List<FinanceBill>>

    @Query("SELECT * FROM bills WHERE id = :billId LIMIT 1")
    suspend fun findById(billId: Long): FinanceBill?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bill: FinanceBill): Long

    @Update
    suspend fun update(bill: FinanceBill)
}

@Dao
interface FinanceBillPaymentDao {
    @Query("SELECT * FROM bill_payments ORDER BY paidAt DESC, id DESC")
    fun observeAll(): Flow<List<FinanceBillPayment>>

    @Query("SELECT * FROM bill_payments WHERE billId = :billId ORDER BY paidAt DESC, id DESC")
    suspend fun forBill(billId: Long): List<FinanceBillPayment>

    @Query("SELECT EXISTS(SELECT 1 FROM bill_payments WHERE billId = :billId AND occurrenceKey = :occurrenceKey)")
    suspend fun exists(billId: Long, occurrenceKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: FinanceBillPayment): Long
}

object FinanceBillPolicy {
    private val datePattern = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    /** Parse a strict local date (yyyy-MM-dd) at 09:00 to avoid midnight/DST edge cases. */
    fun parseLocalDate(value: String): Long? {
        val normalized = value.trim().map { ch ->
            when (ch) {
                in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                else -> ch
            }
        }.joinToString("")
        val match = datePattern.matchEntire(normalized) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        if (year !in 1970..9999 || month !in 1..12 || day !in 1..31) return null
        return runCatching {
            Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 9)
                isLenient = false
            }.timeInMillis
        }.getOrNull()
    }

    fun formatLocalDate(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "%04d-%02d-%02d".format(
            Locale.US,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun occurrenceKey(dueAt: Long): String = formatLocalDate(dueAt)

    fun nextDueAt(dueAt: Long, frequency: String, anchorDay: Int, anchorMonth: Int): Long? {
        val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
        when (frequency) {
            "MONTHLY" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.MONTH, 1)
                calendar.set(Calendar.DAY_OF_MONTH, anchorDay.coerceIn(1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            "YEARLY" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.add(Calendar.YEAR, 1)
                calendar.set(Calendar.MONTH, anchorMonth.coerceIn(0, 11))
                calendar.set(Calendar.DAY_OF_MONTH, anchorDay.coerceIn(1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            else -> return null
        }
        return calendar.timeInMillis
    }

    fun reminderAt(dueAt: Long, now: Long): Long = (dueAt - 86_400_000L).coerceAtLeast(now)

    fun isDue(nextDueAt: Long, now: Long): Boolean = now >= nextDueAt
}
