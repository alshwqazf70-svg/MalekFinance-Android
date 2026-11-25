package com.malekfinance

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

/** Guard ordinary edits/deletes at the database boundary, not only by hiding UI buttons. */
object FinanceTransactionOperations {
    fun isOrdinary(row: FinanceTransaction) = row.kind in setOf("INCOME", "EXPENSE") &&
        row.debtId == null && row.savingsGoalId == null && row.recurringIncomeRuleId == null &&
        row.recurringExpenseRuleId == null && row.billId == null

    suspend fun delete(db: FinanceDb, expected: FinanceTransaction) = db.withTransaction {
        val current = db.dao().findById(expected.id) ?: error("Transaction no longer exists")
        require(current == expected && isOrdinary(current)) { "Linked or changed transaction cannot be deleted here" }
        db.dao().delete(current)
    }

    suspend fun update(db: FinanceDb, expected: FinanceTransaction, amountMinor: Long, kind: String, note: String, category: String) = db.withTransaction {
        val current = db.dao().findById(expected.id) ?: error("Transaction no longer exists")
        require(current == expected && isOrdinary(current)) { "Linked or changed transaction cannot be edited here" }
        require(amountMinor > 0 && kind in setOf("INCOME", "EXPENSE") && note.isNotBlank())
        require(db.categoryDao().observeAll().first().any { it.isActive && it.name == category && FinanceCategoryPolicy.isCompatible(it.kind, kind) })
        db.dao().update(current.copy(amountMinor = amountMinor, kind = kind, note = note.trim(), category = category))
    }
}
