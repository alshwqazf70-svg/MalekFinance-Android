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
