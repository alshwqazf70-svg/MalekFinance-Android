package com.malekfinance

import androidx.room.withTransaction

/**
 * Executes internal wallet transfers against the latest persisted ledger state.
 * All validation and insertion happen in one Room transaction so concurrent
 * requests cannot both spend the same available balance.
 */
object FinanceTransferOperations {
    suspend fun transfer(
        db: FinanceDb,
        fromWalletId: Long,
        toWalletId: Long,
        amountMinor: Long,
        note: String = ""
    ): Long = db.withTransaction {
        require(amountMinor > 0L) { "Transfer amount must be positive" }
        require(fromWalletId != toWalletId) { "Source and destination wallets must differ" }

        val source = db.walletDao().findById(fromWalletId)
            ?: throw IllegalArgumentException("Source wallet not found")
        val destination = db.walletDao().findById(toWalletId)
            ?: throw IllegalArgumentException("Destination wallet not found")
        require(source.currencyCode == destination.currencyCode) {
            "Transfers between different currencies are not supported"
        }

        val latestTransactions = db.dao().forWallet(source.id)
        val latestTransfers = db.transferDao().forWallet(source.id)
        if (!FinanceLedger.hasSufficientFunds(
                source, amountMinor, latestTransactions, latestTransfers
            )
        ) {
            throw InsufficientWalletFundsException()
        }

        db.transferDao().insert(
            FinanceTransfer(
                fromWalletId = source.id,
                toWalletId = destination.id,
                amountMinor = amountMinor,
                currencyCode = source.currencyCode,
                note = note.trim()
            )
        )
    }
}
