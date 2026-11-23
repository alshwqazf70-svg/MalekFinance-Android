package com.malekfinance

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceDebtPolicyTest {
    @Test
    fun partialPaymentsReduceOutstandingWithoutGoingBelowZero() {
        val payments = listOf(
            FinanceDebtPayment(debtId = 1, transactionId = 11, amountMinor = 2_500),
            FinanceDebtPayment(debtId = 1, transactionId = 12, amountMinor = 1_000)
        )
        assertEquals(BigInteger.valueOf(3_500), FinanceDebtPolicy.paidAmount(payments))
        assertEquals(BigInteger.valueOf(6_500), FinanceDebtPolicy.outstanding(10_000, payments))
    }

    @Test
    fun exactSettlementIsAllowedButOverpaymentIsRejected() {
        val payments = listOf(FinanceDebtPayment(debtId = 1, transactionId = 11, amountMinor = 7_000))
        assertTrue(FinanceDebtPolicy.canApplyPayment(10_000, payments, 3_000))
        assertFalse(FinanceDebtPolicy.canApplyPayment(10_000, payments, 3_001))
        assertFalse(FinanceDebtPolicy.canApplyPayment(10_000, payments, 0))
        assertFalse(FinanceDebtPolicy.canApplyPayment(10_000, payments, -1))
    }

    @Test
    fun settledDebtHasNoRemainingBalanceAndCannotAcceptAnotherPayment() {
        val payments = listOf(FinanceDebtPayment(debtId = 1, transactionId = 11, amountMinor = 10_000))
        assertEquals(BigInteger.ZERO, FinanceDebtPolicy.outstanding(10_000, payments))
        assertFalse(FinanceDebtPolicy.canApplyPayment(10_000, payments, 1))
    }

    @Test
    fun outstandingCalculationUsesBigIntegerToAvoidOverflow() {
        val payments = listOf(FinanceDebtPayment(debtId = 1, transactionId = 11, amountMinor = Long.MAX_VALUE))
        assertEquals(BigInteger.ZERO, FinanceDebtPolicy.outstanding(Long.MAX_VALUE, payments))
    }

    @Test
    fun linkedDebtPaymentTransactionChangesWalletInCorrectDirection() {
        val wallet = FinanceWallet(id = 1, name = "نقدية", currencyCode = "YER", openingBalanceMinor = 10_000)
        val repayment = FinanceTransaction(
            amountMinor = 2_000,
            kind = "EXPENSE",
            note = "سداد دين",
            walletId = 1,
            currencyCode = "YER",
            debtId = 7
        )
        val collection = FinanceTransaction(
            amountMinor = 3_000,
            kind = "INCOME",
            note = "تحصيل دين",
            walletId = 1,
            currencyCode = "YER",
            debtId = 8
        )
        assertEquals(
            BigInteger.valueOf(11_000),
            FinanceLedger.calculateBalance(wallet, listOf(repayment, collection), emptyList())
        )
        assertEquals(7L, repayment.debtId)
        assertEquals(8L, collection.debtId)
    }
    @Test
    fun issuedAdvanceReducesFundsAvailableForInternalTransfers() {
        val wallet = FinanceWallet(id = 1, name = "نقدية", currencyCode = "YER", openingBalanceMinor = 10_000)
        val issuedAdvance = FinanceTransaction(
            amountMinor = 7_000,
            kind = "DEBT_ISSUED",
            note = "سلفة",
            walletId = 1,
            currencyCode = "YER",
            debtId = 30
        )
        assertTrue(FinanceLedger.hasSufficientFunds(wallet, 3_000, listOf(issuedAdvance), emptyList()))
        assertFalse(FinanceLedger.hasSufficientFunds(wallet, 3_001, listOf(issuedAdvance), emptyList()))
    }

    @Test
    fun receivingBorrowedMoneyAndIssuingAnAdvanceChangeCashButNotIncomeExpenseTotals() {
        val wallet = FinanceWallet(id = 1, name = "نقدية", currencyCode = "YER", openingBalanceMinor = 10_000)
        val principalMovements = listOf(
            FinanceTransaction(
                amountMinor = 5_000,
                kind = "DEBT_RECEIVED",
                note = "استلام قرض",
                category = "قرض مستلم",
                walletId = 1,
                currencyCode = "YER",
                debtId = 20
            ),
            FinanceTransaction(
                amountMinor = 2_000,
                kind = "DEBT_ISSUED",
                note = "إقراض سلفة",
                category = "سلفة ممنوحة",
                walletId = 1,
                currencyCode = "YER",
                debtId = 21
            )
        )
        assertEquals(
            BigInteger.valueOf(13_000),
            FinanceLedger.calculateBalance(wallet, principalMovements, emptyList())
        )
        assertEquals(0, principalMovements.count { it.kind == "INCOME" || it.kind == "EXPENSE" })
    }

    @Test
    fun invalidNonPositivePaymentsCannotIncreaseOutstandingDebt() {
        val payments = listOf(
            FinanceDebtPayment(debtId = 1, transactionId = 1, amountMinor = 300),
            FinanceDebtPayment(debtId = 1, transactionId = 2, amountMinor = -900),
            FinanceDebtPayment(debtId = 1, transactionId = 3, amountMinor = 0)
        )
        assertEquals(BigInteger.valueOf(300), FinanceDebtPolicy.paidAmount(payments))
        assertEquals(BigInteger.valueOf(700), FinanceDebtPolicy.outstanding(1_000, payments))
        assertEquals(BigInteger.ZERO, FinanceDebtPolicy.outstanding(-1, payments))
        assertFalse(FinanceDebtPolicy.canApplyPayment(1_000, payments, 701))
    }

}
