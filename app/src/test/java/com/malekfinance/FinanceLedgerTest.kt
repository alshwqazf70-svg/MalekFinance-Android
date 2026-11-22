package com.malekfinance

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceLedgerTest {
    @Test
    fun balanceIncludesOpeningTransactionsAndTransfersWithoutTreatingTransfersAsIncome() {
        val cash = FinanceWallet(id = 1, name = "نقدية", currencyCode = "YER", openingBalanceMinor = 10_000)
        val bank = FinanceWallet(id = 2, name = "بنك", currencyCode = "YER", openingBalanceMinor = 1_000)
        val transactions = listOf(
            FinanceTransaction(amountMinor = 5_000, kind = "INCOME", note = "راتب", walletId = 1, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 2_000, kind = "EXPENSE", note = "مشتريات", walletId = 1, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 99_000, kind = "INCOME", note = "دولار", walletId = 1, currencyCode = "USD")
        )
        val transfers = listOf(
            FinanceTransfer(fromWalletId = 1, toWalletId = 2, amountMinor = 3_000, currencyCode = "YER")
        )

        assertEquals(BigInteger.valueOf(10_000), FinanceLedger.calculateBalance(cash, transactions, transfers))
        assertEquals(BigInteger.valueOf(4_000), FinanceLedger.calculateBalance(bank, transactions, transfers))
    }

    @Test
    fun balancesDoNotMixWalletsOrCurrencies() {
        val wallet = FinanceWallet(id = 7, name = "دولار", currencyCode = "USD", openingBalanceMinor = 500)
        val transactions = listOf(
            FinanceTransaction(amountMinor = 200, kind = "INCOME", note = "USD", walletId = 7, currencyCode = "USD"),
            FinanceTransaction(amountMinor = 900, kind = "INCOME", note = "YER", walletId = 7, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 800, kind = "INCOME", note = "other wallet", walletId = 8, currencyCode = "USD")
        )

        assertEquals(BigInteger.valueOf(700), FinanceLedger.calculateBalance(wallet, transactions, emptyList()))
    }

    @Test
    fun transferRequiresPositiveAmountAndAvailableFunds() {
        val wallet = FinanceWallet(id = 1, name = "نقدية", currencyCode = "YER", openingBalanceMinor = 1_000)

        assertTrue(FinanceLedger.hasSufficientFunds(wallet, 700, emptyList(), emptyList()))
        assertFalse(FinanceLedger.hasSufficientFunds(wallet, 1_001, emptyList(), emptyList()))
        assertFalse(FinanceLedger.hasSufficientFunds(wallet, 0, emptyList(), emptyList()))
        assertFalse(FinanceLedger.hasSufficientFunds(wallet, -1, emptyList(), emptyList()))
    }
}
