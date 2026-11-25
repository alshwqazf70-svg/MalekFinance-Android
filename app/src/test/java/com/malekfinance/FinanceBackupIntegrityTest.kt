package com.malekfinance

import org.junit.Test

class FinanceBackupIntegrityTest {
    private fun row(vararg values: Pair<String, Any?>): Map<String, Any?> = mapOf("id" to 1L, "createdAt" to 100L) + values
    private fun wallet(id: Long = 1L, currency: String = "YER") = row("id" to id, "name" to "نقد", "currencyCode" to currency, "walletType" to "CASH", "openingBalanceMinor" to 100000L)
    private fun tx(id: Long = 1L, kind: String = "EXPENSE", amount: Long = 100L, walletId: Long = 1L, vararg extra: Pair<String, Any?>) = row("id" to id, "kind" to kind, "amountMinor" to amount, "walletId" to walletId, "currencyCode" to "YER", *extra)
    private fun debt(cash: Long = 0L) = row("personName" to "عبده مهدي", "debtType" to "OWED_BY_ME", "principalMinor" to 1000L, "currencyCode" to "YER", "walletId" to 1L, "cashMovementRecorded" to cash)
    private fun snapshot(vararg tables: Pair<String, List<Map<String, Any?>>>): Map<String, List<Map<String, Any?>>> =
        FinanceBackupIntegrity.tableNames.associateWith { emptyList<Map<String, Any?>>() } + mapOf("wallets" to listOf(wallet())) + tables

    @Test fun acceptsOrdinaryAndEmptySnapshots() {
        FinanceBackupIntegrity.validate(snapshot("transactions" to listOf(tx())))
        FinanceBackupIntegrity.validate(FinanceBackupIntegrity.tableNames.associateWith { emptyList() })
    }
    @Test fun creditPurchasesHaveNoInventedCashAndMayUseAnotherSameCurrencyFundingWallet() {
        FinanceBackupIntegrity.validate(snapshot(
            "wallets" to listOf(wallet(), wallet(2L)), "debts" to listOf(debt()),
            "transactions" to listOf(tx(walletId = 2L, extra = arrayOf("debtId" to 1L))),
            "debt_payments" to listOf(row("debtId" to 1L, "transactionId" to 1L, "amountMinor" to 100L))
        ))
    }
    @Test fun acceptsMatchingCashLoanOrigin() {
        FinanceBackupIntegrity.validate(snapshot("debts" to listOf(debt(1L)), "transactions" to listOf(tx(kind = "DEBT_RECEIVED", amount = 1000L, extra = arrayOf("debtId" to 1L)))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMissingLoanCashMovement() {
        FinanceBackupIntegrity.validate(snapshot("debts" to listOf(debt(1L))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOverpaymentEvenWhenSumExceedsLong() {
        FinanceBackupIntegrity.validate(snapshot(
            "debts" to listOf(debt() + ("principalMinor" to Long.MAX_VALUE)),
            "transactions" to listOf(tx(amount = Long.MAX_VALUE, extra = arrayOf("debtId" to 1L)), tx(2L, amount = 1L, extra = arrayOf("debtId" to 1L))),
            "debt_payments" to listOf(row("debtId" to 1L, "transactionId" to 1L, "amountMinor" to Long.MAX_VALUE), row("id" to 2L, "debtId" to 1L, "transactionId" to 2L, "amountMinor" to 1L))
        ))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsCurrencyMismatch() {
        FinanceBackupIntegrity.validate(snapshot("wallets" to listOf(wallet(currency = "USD")), "transactions" to listOf(tx())))
    }
    @Test(expected = IllegalStateException::class) fun rejectsOrphanedWallet() {
        FinanceBackupIntegrity.validate(snapshot("transactions" to listOf(tx(walletId = 99L))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnclaimedLinkedTransaction() {
        FinanceBackupIntegrity.validate(snapshot("transactions" to listOf(tx(extra = arrayOf("billId" to 123L)))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMultipleOwners() {
        FinanceBackupIntegrity.validate(snapshot("transactions" to listOf(tx(extra = arrayOf("debtId" to 1L, "savingsGoalId" to 1L)))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsWrongRepaymentDirection() {
        FinanceBackupIntegrity.validate(snapshot("debts" to listOf(debt()), "transactions" to listOf(tx(kind = "INCOME", extra = arrayOf("debtId" to 1L))), "debt_payments" to listOf(row("debtId" to 1L, "transactionId" to 1L, "amountMinor" to 100L))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateIdsAndInvalidBoolean() {
        FinanceBackupIntegrity.validate(snapshot("wallets" to listOf(wallet(), wallet())))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidBoolean() {
        FinanceBackupIntegrity.validate(snapshot("debts" to listOf(debt(2L))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsRepaymentBeforePurchase() {
        FinanceBackupIntegrity.validate(snapshot("debts" to listOf(debt()), "transactions" to listOf(tx(extra = arrayOf("debtId" to 1L))), "debt_payments" to listOf(row("createdAt" to 99L, "debtId" to 1L, "transactionId" to 1L, "amountMinor" to 100L))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsSavingsWithdrawalBeforeFundsExist() {
        FinanceBackupIntegrity.validate(snapshot(
            "savings_goals" to listOf(row("name" to "هدف", "targetMinor" to 1000L, "currencyCode" to "YER", "walletId" to 1L)),
            "transactions" to listOf(tx(kind = "SAVINGS_WITHDRAWAL", extra = arrayOf("savingsGoalId" to 1L))),
            "savings_entries" to listOf(row("goalId" to 1L, "entryType" to "WITHDRAWAL", "transactionId" to 1L, "amountMinor" to 100L))
        ))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsIncorrectTransactionAmountForPayment() {
        FinanceBackupIntegrity.validate(snapshot("debts" to listOf(debt()), "transactions" to listOf(tx(extra = arrayOf("debtId" to 1L))), "debt_payments" to listOf(row("debtId" to 1L, "transactionId" to 1L, "amountMinor" to 99L))))
    }
}
