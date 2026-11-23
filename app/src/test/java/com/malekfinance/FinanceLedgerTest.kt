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
        val outgoing = listOf(FinanceTransfer(fromWalletId = 1, toWalletId = 2, amountMinor = 400, currencyCode = "YER"))
        assertTrue(FinanceLedger.hasSufficientFunds(wallet, 600, emptyList(), outgoing))
        assertFalse(FinanceLedger.hasSufficientFunds(wallet, 601, emptyList(), outgoing))
        assertFalse(FinanceLedger.hasSufficientFunds(wallet, 1_001, emptyList(), emptyList()))
        assertFalse(FinanceLedger.hasSufficientFunds(wallet, 0, emptyList(), emptyList()))
        assertFalse(FinanceLedger.hasSufficientFunds(wallet, -1, emptyList(), emptyList()))
    }

    @Test
    fun invalidSignedTransactionsAndTransfersCannotCreateWalletFunds() {
        val wallet = FinanceWallet(id = 1, name = "نقدية", currencyCode = "YER", openingBalanceMinor = 1_000)
        val transactions = listOf(
            FinanceTransaction(amountMinor = -50_000, kind = "EXPENSE", note = "invalid negative expense", walletId = 1, currencyCode = "YER"),
            FinanceTransaction(amountMinor = -40_000, kind = "INCOME", note = "invalid negative income", walletId = 1, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 20_000, kind = "INCOME", note = "wrong wallet", walletId = 2, currencyCode = "YER")
        )
        val transfers = listOf(
            FinanceTransfer(fromWalletId = 1, toWalletId = 2, amountMinor = -30_000, currencyCode = "YER"),
            FinanceTransfer(fromWalletId = 1, toWalletId = 1, amountMinor = 50_000, currencyCode = "YER"),
            FinanceTransfer(fromWalletId = 1, toWalletId = 3, amountMinor = 70_000, currencyCode = "USD"),
            FinanceTransfer(fromWalletId = 4, toWalletId = 5, amountMinor = 90_000, currencyCode = "YER")
        )

        assertEquals(BigInteger.valueOf(1_000), FinanceLedger.calculateBalance(wallet, transactions, transfers))
    }

    @Test
    fun largeLedgerTotalsDoNotOverflowLong() {
        val wallet = FinanceWallet(id = 1, name = "نقدية", currencyCode = "YER", openingBalanceMinor = Long.MAX_VALUE)
        val transactions = listOf(
            FinanceTransaction(amountMinor = Long.MAX_VALUE, kind = "INCOME", note = "large income", walletId = 1, currencyCode = "YER")
        )

        assertEquals(
            BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2)),
            FinanceLedger.calculateBalance(wallet, transactions, emptyList())
        )
    }

    @Test
    fun everySupportedTransactionKindChangesWalletBalanceInItsCorrectDirection() {
        val wallet = FinanceWallet(id = 9, name = "دفتر شامل", currencyCode = "YER", openingBalanceMinor = 10_000)
        val transactions = listOf(
            FinanceTransaction(amountMinor = 1_000, kind = "INCOME", note = "income", walletId = 9, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 2_000, kind = "EXPENSE", note = "expense", walletId = 9, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 300, kind = "DEBT_ISSUED", note = "loan issued", walletId = 9, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 400, kind = "DEBT_RECEIVED", note = "loan received", walletId = 9, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 500, kind = "SAVINGS_CONTRIBUTION", note = "saved", walletId = 9, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 600, kind = "SAVINGS_WITHDRAWAL", note = "withdrawn", walletId = 9, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 99_999, kind = "UNKNOWN_KIND", note = "unknown must not affect balance", walletId = 9, currencyCode = "YER")
        )

        assertEquals(
            BigInteger.valueOf(9_200),
            FinanceLedger.calculateBalance(wallet, transactions, emptyList())
        )
    }

    @Test
    fun incomingAndOutgoingTransfersAffectOnlyTheirOwnWallet() {
        val wallet = FinanceWallet(id = 1, name = "الرئيسية", currencyCode = "YER", openingBalanceMinor = 5_000)
        val other = FinanceWallet(id = 2, name = "الثانية", currencyCode = "YER", openingBalanceMinor = 7_000)
        val transfers = listOf(
            FinanceTransfer(fromWalletId = 2, toWalletId = 1, amountMinor = 1_250, currencyCode = "YER"),
            FinanceTransfer(fromWalletId = 1, toWalletId = 3, amountMinor = 750, currencyCode = "YER")
        )

        assertEquals(BigInteger.valueOf(5_500), FinanceLedger.calculateBalance(wallet, emptyList(), transfers))
        assertEquals(BigInteger.valueOf(5_750), FinanceLedger.calculateBalance(other, emptyList(), transfers))
    }

    @Test
    fun unsupportedTransactionKindsAndMismatchedCurrenciesNeverChangeBalance() {
        val wallet = FinanceWallet(id = 3, name = "ريال", currencyCode = "YER", openingBalanceMinor = 2_000)
        val transactions = listOf(
            FinanceTransaction(amountMinor = 500, kind = "TRANSFER", note = "transfers are separate", walletId = 3, currencyCode = "YER"),
            FinanceTransaction(amountMinor = 700, kind = "INCOME", note = "wrong currency", walletId = 3, currencyCode = "USD")
        )

        assertEquals(BigInteger.valueOf(2_000), FinanceLedger.calculateBalance(wallet, transactions, emptyList()))
    }

}
