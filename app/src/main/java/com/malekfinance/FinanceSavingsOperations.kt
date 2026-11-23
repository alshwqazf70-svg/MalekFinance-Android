package com.malekfinance

import androidx.room.withTransaction

/** Atomic operations that keep savings goals, entries, and wallet ledger in sync. */
object FinanceSavingsOperations {
    suspend fun contribute(db: FinanceDb, goalId: Long, amountMinor: Long): Long = db.withTransaction {
        require(amountMinor > 0L) { "Savings contribution must be positive" }
        val goal = db.savingsGoalDao().findById(goalId) ?: error("Savings goal not found")
        val entries = db.savingsEntryDao().forGoal(goal.id)
        require(FinanceSavingsPolicy.canContribute(goal.targetMinor, entries, amountMinor)) {
            "Contribution exceeds remaining goal amount"
        }
        val wallet = db.walletDao().findById(goal.walletId) ?: error("Wallet not found")
        check(wallet.currencyCode == goal.currencyCode)
        val transactions = db.dao().forWallet(wallet.id)
        val transfers = db.transferDao().forWallet(wallet.id)
        if (!FinanceLedger.hasSufficientFunds(wallet, amountMinor, transactions, transfers)) {
            throw InsufficientWalletFundsException()
        }
        val transactionId = db.dao().insert(
            FinanceTransaction(
                amountMinor = amountMinor,
                kind = "SAVINGS_CONTRIBUTION",
                note = "مساهمة ادخار: " + goal.name,
                category = "ادخار",
                walletId = wallet.id,
                currencyCode = wallet.currencyCode,
                savingsGoalId = goal.id
            )
        )
        db.savingsEntryDao().insert(
            FinanceSavingsEntry(
                goalId = goal.id,
                transactionId = transactionId,
                entryType = "CONTRIBUTION",
                amountMinor = amountMinor
            )
        )
    }

    suspend fun withdraw(db: FinanceDb, goalId: Long, amountMinor: Long): Long = db.withTransaction {
        require(amountMinor > 0L) { "Savings withdrawal must be positive" }
        val goal = db.savingsGoalDao().findById(goalId) ?: error("Savings goal not found")
        val entries = db.savingsEntryDao().forGoal(goal.id)
        require(FinanceSavingsPolicy.canWithdraw(entries, amountMinor)) {
            "Withdrawal exceeds saved balance"
        }
        val wallet = db.walletDao().findById(goal.walletId) ?: error("Wallet not found")
        check(wallet.currencyCode == goal.currencyCode)
        val transactionId = db.dao().insert(
            FinanceTransaction(
                amountMinor = amountMinor,
                kind = "SAVINGS_WITHDRAWAL",
                note = "سحب من الادخار: " + goal.name,
                category = "ادخار",
                walletId = wallet.id,
                currencyCode = wallet.currencyCode,
                savingsGoalId = goal.id
            )
        )
        db.savingsEntryDao().insert(
            FinanceSavingsEntry(
                goalId = goal.id,
                transactionId = transactionId,
                entryType = "WITHDRAWAL",
                amountMinor = amountMinor
            )
        )
    }
}
