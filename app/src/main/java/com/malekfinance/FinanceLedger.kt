package com.malekfinance

import java.math.BigInteger

/**
 * Deterministic, currency-isolated ledger calculations.
 * Transfers are represented separately from income/expense transactions.
 *
 * Invalid persisted rows are ignored rather than allowing a negative amount,
 * self-transfer, or currency mismatch to reverse or fabricate wallet balance.
 */
class InsufficientWalletFundsException : IllegalStateException("Insufficient wallet funds")

object FinanceLedger {
    fun calculateBalance(
        wallet: FinanceWallet,
        transactions: List<FinanceTransaction>,
        transfers: List<FinanceTransfer>
    ): BigInteger {
        var balance = BigInteger.valueOf(wallet.openingBalanceMinor)

        transactions.asSequence()
            .filter {
                it.walletId == wallet.id &&
                    it.currencyCode == wallet.currencyCode &&
                    it.amountMinor > 0L
            }
            .forEach { transaction ->
                val amount = BigInteger.valueOf(transaction.amountMinor)
                when (transaction.kind) {
                    "INCOME" -> balance = balance.add(amount)
                    "EXPENSE", "DEBT_ISSUED", "SAVINGS_CONTRIBUTION" -> balance = balance.subtract(amount)
                    "DEBT_RECEIVED", "SAVINGS_WITHDRAWAL" -> balance = balance.add(amount)
                }
            }

        transfers.asSequence()
            .filter {
                it.amountMinor > 0L &&
                    it.fromWalletId != it.toWalletId &&
                    it.currencyCode == wallet.currencyCode &&
                    (it.fromWalletId == wallet.id || it.toWalletId == wallet.id)
            }
            .forEach { transfer ->
                val amount = BigInteger.valueOf(transfer.amountMinor)
                if (wallet.id == transfer.fromWalletId) {
                    balance = balance.subtract(amount)
                } else {
                    balance = balance.add(amount)
                }
            }

        return balance
    }

    fun hasSufficientFunds(
        wallet: FinanceWallet,
        amountMinor: Long,
        transactions: List<FinanceTransaction>,
        transfers: List<FinanceTransfer>
    ): Boolean {
        if (amountMinor <= 0L) return false
        return calculateBalance(wallet, transactions, transfers) >= BigInteger.valueOf(amountMinor)
    }
}
