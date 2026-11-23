package com.malekfinance

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

/**
 * OWED_BY_ME: the user owes this amount and repayments are expenses.
 * OWED_TO_ME: another party owes the user and collections are income.
 */
@Entity(tableName = "debts", indices = [Index(value = ["walletId"])])
data class FinanceDebt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val description: String,
    val debtType: String,
    val principalMinor: Long,
    val currencyCode: String,
    val walletId: Long,
    @ColumnInfo(defaultValue = "1") val cashMovementRecorded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "debt_payments",
    indices = [Index(value = ["debtId"]), Index(value = ["transactionId"], unique = true)]
)
data class FinanceDebtPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val transactionId: Long,
    val amountMinor: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface FinanceDebtDao {
    @Query("SELECT * FROM debts ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<FinanceDebt>>

    @Query("SELECT * FROM debts WHERE id = :debtId LIMIT 1")
    suspend fun findById(debtId: Long): FinanceDebt?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(debt: FinanceDebt): Long
}

@Dao
interface FinanceDebtPaymentDao {
    @Query("SELECT * FROM debt_payments ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<FinanceDebtPayment>>

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY createdAt ASC, id ASC")
    suspend fun forDebt(debtId: Long): List<FinanceDebtPayment>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: FinanceDebtPayment): Long
}

object FinanceDebtPolicy {
    fun paidAmount(payments: List<FinanceDebtPayment>): BigInteger =
        payments.fold(BigInteger.ZERO) { total, payment ->
            if (payment.amountMinor <= 0L) total
            else total.add(BigInteger.valueOf(payment.amountMinor))
        }

    fun outstanding(principalMinor: Long, payments: List<FinanceDebtPayment>): BigInteger =
        BigInteger.valueOf(principalMinor.coerceAtLeast(0L)).subtract(paidAmount(payments)).max(BigInteger.ZERO)

    fun canApplyPayment(principalMinor: Long, payments: List<FinanceDebtPayment>, amountMinor: Long): Boolean =
        amountMinor > 0L &&
            BigInteger.valueOf(amountMinor) <= outstanding(principalMinor, payments)
}
