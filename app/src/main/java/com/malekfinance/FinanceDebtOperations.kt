package com.malekfinance

import androidx.room.withTransaction

/** Atomic debt creation shared by manual and voice-assisted flows. */
object FinanceDebtOperations {
    suspend fun create(
        db: FinanceDb,
        personName: String,
        description: String,
        debtType: String,
        principalMinor: Long,
        walletId: Long,
        recordCashMovement: Boolean = true
    ): Long = db.withTransaction {
        val cleanName = personName.trim()
        require(cleanName.isNotBlank()) { "Person name is required" }
        require(debtType == "OWED_BY_ME" || debtType == "OWED_TO_ME") { "Invalid debt direction" }
        require(principalMinor > 0L) { "Debt amount must be positive" }
        val wallet = db.walletDao().findById(walletId) ?: error("Wallet not found")

        if (recordCashMovement && debtType == "OWED_TO_ME") {
            val transactions = db.dao().forWallet(wallet.id)
            val transfers = db.transferDao().forWallet(wallet.id)
            if (!FinanceLedger.hasSufficientFunds(wallet, principalMinor, transactions, transfers)) {
                throw InsufficientWalletFundsException()
            }
        }

        val debtId = db.debtDao().insert(
            FinanceDebt(
                personName = cleanName,
                description = description.trim(),
                debtType = debtType,
                principalMinor = principalMinor,
                currencyCode = wallet.currencyCode,
                walletId = wallet.id,
                cashMovementRecorded = recordCashMovement
            )
        )
        if (recordCashMovement) {
            db.dao().insert(
                FinanceTransaction(
                    amountMinor = principalMinor,
                    kind = if (debtType == "OWED_BY_ME") "DEBT_RECEIVED" else "DEBT_ISSUED",
                    note = (if (debtType == "OWED_BY_ME") "استلام قرض من: " else "إقراض سلفة إلى: ") + cleanName,
                    category = if (debtType == "OWED_BY_ME") "قرض مستلم" else "سلفة ممنوحة",
                    walletId = wallet.id,
                    currencyCode = wallet.currencyCode,
                    debtId = debtId
                )
            )
        }
        debtId
    }
}


/** Applies a partial or final debt payment and its wallet movement atomically. */
object FinanceDebtPaymentOperations {
    suspend fun applyPayment(db: FinanceDb, debtId: Long, amountMinor: Long): Long = db.withTransaction {
        require(amountMinor > 0L) { "Debt payment must be positive" }
        val debt = db.debtDao().findById(debtId) ?: error("Debt not found")
        require(debt.debtType == "OWED_BY_ME" || debt.debtType == "OWED_TO_ME") {
            "Invalid debt direction"
        }
        val payments = db.debtPaymentDao().forDebt(debt.id)
        check(FinanceDebtPolicy.canApplyPayment(debt.principalMinor, payments, amountMinor)) {
            "Payment exceeds the outstanding debt"
        }
        val wallet = db.walletDao().findById(debt.walletId) ?: error("Wallet not found")
        check(wallet.currencyCode == debt.currencyCode) { "Debt wallet currency mismatch" }

        if (debt.debtType == "OWED_BY_ME") {
            val transactions = db.dao().forWallet(wallet.id)
            val transfers = db.transferDao().forWallet(wallet.id)
            if (!FinanceLedger.hasSufficientFunds(wallet, amountMinor, transactions, transfers)) {
                throw InsufficientWalletFundsException()
            }
        }

        val transactionId = db.dao().insert(
            FinanceTransaction(
                amountMinor = amountMinor,
                kind = if (debt.debtType == "OWED_BY_ME") "EXPENSE" else "INCOME",
                note = (if (debt.debtType == "OWED_BY_ME") "سداد دين: " else "تحصيل دين: ") + debt.personName,
                category = if (debt.debtType == "OWED_BY_ME") "سداد دين" else "تحصيل دين",
                walletId = wallet.id,
                currencyCode = wallet.currencyCode,
                debtId = debt.id
            )
        )
        db.debtPaymentDao().insert(
            FinanceDebtPayment(
                debtId = debt.id,
                transactionId = transactionId,
                amountMinor = amountMinor
            )
        )
        transactionId
    }
}
