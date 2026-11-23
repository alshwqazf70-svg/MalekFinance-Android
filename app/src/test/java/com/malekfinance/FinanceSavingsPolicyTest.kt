package com.malekfinance

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceSavingsPolicyTest {
    @Test
    fun contributionsAndWithdrawalsProduceTheCorrectGoalBalance() {
        val entries = listOf(
            FinanceSavingsEntry(goalId = 1, transactionId = 10, entryType = "CONTRIBUTION", amountMinor = 4_000),
            FinanceSavingsEntry(goalId = 1, transactionId = 11, entryType = "CONTRIBUTION", amountMinor = 2_000),
            FinanceSavingsEntry(goalId = 1, transactionId = 12, entryType = "WITHDRAWAL", amountMinor = 1_500)
        )
        assertEquals(BigInteger.valueOf(4_500), FinanceSavingsPolicy.balance(entries))
    }

    @Test
    fun contributionsCannotExceedRemainingTarget() {
        val entries = listOf(FinanceSavingsEntry(goalId = 1, transactionId = 10, entryType = "CONTRIBUTION", amountMinor = 7_000))
        assertTrue(FinanceSavingsPolicy.canContribute(10_000, entries, 3_000))
        assertFalse(FinanceSavingsPolicy.canContribute(10_000, entries, 3_001))
        assertFalse(FinanceSavingsPolicy.canContribute(10_000, entries, 0))
        assertFalse(FinanceSavingsPolicy.canContribute(10_000, entries, -1))
    }

    @Test
    fun withdrawalCannotExceedCurrentSavingsBalance() {
        val entries = listOf(FinanceSavingsEntry(goalId = 1, transactionId = 10, entryType = "CONTRIBUTION", amountMinor = 5_000))
        assertTrue(FinanceSavingsPolicy.canWithdraw(entries, 5_000))
        assertFalse(FinanceSavingsPolicy.canWithdraw(entries, 5_001))
        assertFalse(FinanceSavingsPolicy.canWithdraw(entries, 0))
    }

    @Test
    fun largeAmountsUseBigIntegerAndProgressIsCappedOnlyForDisplay() {
        val entries = listOf(FinanceSavingsEntry(goalId = 1, transactionId = 10, entryType = "CONTRIBUTION", amountMinor = Long.MAX_VALUE))
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), FinanceSavingsPolicy.balance(entries))
        assertEquals(200, FinanceSavingsPolicy.progressPercent(BigInteger.valueOf(20_000), 10_000))
    }

    @Test
    fun savingsMovementReservesAndReturnsWalletCashWithoutChangingOperatingTotals() {
        val wallet = FinanceWallet(id = 1, name = "نقدية", currencyCode = "YER", openingBalanceMinor = 10_000)
        val movements = listOf(
            FinanceTransaction(amountMinor = 3_000, kind = "SAVINGS_CONTRIBUTION", note = "مساهمة", walletId = 1, currencyCode = "YER", savingsGoalId = 1),
            FinanceTransaction(amountMinor = 1_000, kind = "SAVINGS_WITHDRAWAL", note = "سحب", walletId = 1, currencyCode = "YER", savingsGoalId = 1)
        )
        assertEquals(BigInteger.valueOf(8_000), FinanceLedger.calculateBalance(wallet, movements, emptyList()))
        assertEquals(0, movements.count { it.kind == "INCOME" || it.kind == "EXPENSE" })
        assertEquals(1L, movements.first().savingsGoalId)
    }
}
