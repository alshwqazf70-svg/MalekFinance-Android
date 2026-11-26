package com.malekfinance

import androidx.room.withTransaction

/** Serialize status changes with payments; never write a stale schedule snapshot. */
object FinanceScheduleOperations {
    suspend fun toggleBill(db: FinanceDb, id: Long): Boolean = db.withTransaction {
        val current = db.billDao().findById(id) ?: error("Bill not found")
        check(current.frequency != "ONCE" || db.billPaymentDao().forBill(id).isEmpty()) {
            "A paid one-time bill cannot be reactivated"
        }
        val active = !current.isActive
        check(db.billDao().setActive(id, active) == 1)
        active
    }

    suspend fun toggleIncome(db: FinanceDb, id: Long): Boolean = db.withTransaction {
        val current = db.recurringIncomeRuleDao().findById(id) ?: error("Rule not found")
        val active = !current.isActive
        check(db.recurringIncomeRuleDao().setActive(id, active) == 1)
        active
    }

    suspend fun toggleExpense(db: FinanceDb, id: Long): Boolean = db.withTransaction {
        val current = db.recurringExpenseRuleDao().findById(id) ?: error("Rule not found")
        val active = !current.isActive
        check(db.recurringExpenseRuleDao().setActive(id, active) == 1)
        active
    }
}
