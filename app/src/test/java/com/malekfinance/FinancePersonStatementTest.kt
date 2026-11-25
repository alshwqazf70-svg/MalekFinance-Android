package com.malekfinance

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancePersonStatementTest {
    private val now = 1_795_642_000_000L
    private fun debt(id: Long = 1, person: String = "عبده مهدي", currency: String = "YER", at: Long = now - 86400000, principal: Long = 100000) = FinanceDebt(
        id = id, personName = person, description = "شراء آجل: قات", debtType = "OWED_BY_ME", principalMinor = principal,
        currencyCode = currency, walletId = 1, cashMovementRecorded = false, createdAt = at
    )
    private fun payment(id: Long = 1, debtId: Long = 1, amount: Long = 50000, at: Long = now - 1000) = FinanceDebtPayment(id, debtId, id, amount, at)

    @Test fun statementKeepsEveryPurchaseDateAndPaymentAndCorrectTotals() {
        val statement = FinancePersonStatements.build("عبده مهدي", "YER", listOf(debt(2, at = now - 10000), debt()), listOf(payment()), now)
        assertEquals(3, statement.lines.size)
        assertEquals(now - 86400000, statement.lines.first().timestamp)
        assertEquals(BigInteger.valueOf(200000), statement.totalDebt)
        assertEquals(BigInteger.valueOf(50000), statement.totalPaid)
        assertEquals(BigInteger.valueOf(150000), statement.outstanding)
        assertTrue(statement.asText().contains("عبده مهدي"))
        assertTrue(statement.asText().contains("Asia/Aden"))
        assertTrue(statement.asText().contains("عملية #2"))
    }
    @Test fun neverMixesNamesCurrenciesOrAmountsOwedToMe() {
        val otherDirection = debt(5).copy(debtType = "OWED_TO_ME")
        val statement = FinancePersonStatements.build("عبده مهدي", "YER", listOf(debt(), debt(2, person = "عبده مهدي صالح"), debt(3, currency = "USD"), otherDirection), listOf(payment(debtId = 2)), now)
        assertEquals(1, statement.lines.size)
        assertEquals(BigInteger.valueOf(100000), statement.outstanding)
    }
    @Test fun onlyWhitespaceIsNormalizedInNames() {
        val statement = FinancePersonStatements.build("  عبده   مهدي ", "YER", listOf(debt()), emptyList(), now)
        assertEquals("عبده مهدي", statement.person)
        assertEquals(1, statement.lines.size)
    }
    @Test fun totalsUseBigIntegerAndPaidAccountsRemainInHistory() {
        val rows = listOf(debt(principal = Long.MAX_VALUE), debt(2, principal = Long.MAX_VALUE))
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), FinancePersonStatements.build("عبده مهدي", "YER", rows, emptyList(), now).totalDebt)
        val paid = FinancePersonStatements.build("عبده مهدي", "YER", listOf(debt()), listOf(payment(amount = 100000)), now)
        assertEquals(BigInteger.ZERO, paid.outstanding)
        assertEquals(2, paid.lines.size)
    }
    @Test fun sameTimestampPutsOriginalDebtBeforeItsRepayment() {
        val statement = FinancePersonStatements.build("عبده مهدي", "YER", listOf(debt(at = now)), listOf(payment(at = now)), now)
        assertEquals(BigInteger.valueOf(100000), statement.lines[0].balance)
        assertEquals(BigInteger.valueOf(50000), statement.lines[1].balance)
    }
    @Test fun timezoneIsExplicitAndStableAcrossDeviceTimezoneChanges() {
        assertTrue(FinancePersonStatements.dateTime(0).contains("1970-01-01 03:00:00"))
    }
    @Test fun fifoSettlementAllocatesPartialAndSkipsPaidRecords() {
        val rows = listOf(debt(2, at = now - 1000), debt())
        val plan = FinancePersonStatements.settlementPlan("عبده مهدي", "YER", rows, listOf(payment(amount = 80000, at = now)), 50000, now)
        assertEquals(listOf(1L to 20000L, 2L to 30000L), plan)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOverpaymentPlan() {
        FinancePersonStatements.settlementPlan("عبده مهدي", "YER", listOf(debt()), emptyList(), 100001, now)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsCorruptPaymentBeforePurchaseInsteadOfSharingMisleadingReport() {
        FinancePersonStatements.build("عبده مهدي", "YER", listOf(debt(at = now)), listOf(payment(at = now - 1)), now)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsCorruptExcessPayments() {
        FinancePersonStatements.build("عبده مهدي", "YER", listOf(debt()), listOf(payment(amount = 100001)), now)
    }
}
