package com.malekfinance

import java.math.BigInteger

/**
 * Deterministic, currency-isolated ledger calculations.
 * Transfers are represented separately from income/expense transactions.
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
            .filter { it.walletId == wallet.id && it.currencyCode == wallet.currencyCode }
            .forEach { transaction ->
                val amount = BigInteger.valueOf(transaction.amountMinor)
                when (transaction.kind) {
                    "INCOME" -> balance = balance.add(amount)
                    "EXPENSE", "DEBT_ISSUED" -> balance = balance.subtract(amount)
                    "DEBT_RECEIVED" -> balance = balance.add(amount)
                }
            }

        transfers.asSequence()
            .filter { it.currencyCode == wallet.currencyCode }
            .forEach { transfer ->
                val amount = BigInteger.valueOf(transfer.amountMinor)
                when (wallet.id) {
                    transfer.fromWalletId -> balance = balance.subtract(amount)
                    transfer.toWalletId -> balance = balance.add(amount)
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
