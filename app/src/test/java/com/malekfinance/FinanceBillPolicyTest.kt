package com.malekfinance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar

class FinanceBillPolicyTest {
    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 9, 0, 0)
        }.timeInMillis

    private fun parts(value: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = value }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun parsesOnlyValidIsoLocalDates() {
        assertNotNull(FinanceBillPolicy.parseLocalDate("2024-02-29"))
        assertNotNull(FinanceBillPolicy.parseLocalDate("٢٠٢٤-٠٢-٢٩"))
        assertNotNull(FinanceBillPolicy.parseLocalDate("۲۰۲۴-۰۲-۲۹"))
        assertNull(FinanceBillPolicy.parseLocalDate("2025-02-29"))
        assertNull(FinanceBillPolicy.parseLocalDate("2026-13-01"))
        assertNull(FinanceBillPolicy.parseLocalDate("01-12-2026"))
    }

    @Test
    fun monthlyDueDateClipsFebruaryThenReturnsToAnchorDay() {
        val february = FinanceBillPolicy.nextDueAt(date(2025, 1, 31), "MONTHLY", 31, 0)!!
        val march = FinanceBillPolicy.nextDueAt(february, "MONTHLY", 31, 0)!!
        assertEquals(Triple(2025, 2, 28), parts(february))
        assertEquals(Triple(2025, 3, 31), parts(march))
    }

    @Test
    fun yearlyDueDateHandlesLeapDayWithoutDriftingMonth() {
        val next = FinanceBillPolicy.nextDueAt(date(2024, 2, 29), "YEARLY", 29, 1)!!
        assertEquals(Triple(2025, 2, 28), parts(next))
        val following = FinanceBillPolicy.nextDueAt(next, "YEARLY", 29, 1)!!
        assertEquals(Triple(2026, 2, 28), parts(following))
        val leapReturn = FinanceBillPolicy.nextDueAt(following, "YEARLY", 29, 1)!!
        assertEquals(Triple(2027, 2, 28), parts(leapReturn))
        val nextLeap = FinanceBillPolicy.nextDueAt(leapReturn, "YEARLY", 29, 1)!!
        assertEquals(Triple(2028, 2, 29), parts(nextLeap))
    }

    @Test
    fun oneTimeBillsDoNotGenerateAnotherOccurrence() {
        assertNull(FinanceBillPolicy.nextDueAt(date(2026, 1, 1), "ONCE", 1, 0))
    }

    @Test
    fun reminderIsScheduledOneDayBeforeDueOrImmediatelyWhenCloser() {
        val now = date(2026, 11, 23)
        assertEquals(now + 2 * 86_400_000L, FinanceBillPolicy.reminderAt(now + 3 * 86_400_000L, now))
        assertEquals(now, FinanceBillPolicy.reminderAt(now + 12 * 60 * 60 * 1000L, now))
        assertEquals(now, FinanceBillPolicy.reminderAt(now - 60_000L, now))
    }

    @Test
    fun alreadyDeliveredReminderIsNotScheduledAgainForSameOccurrence() {
        val dueAt = date(2026, 11, 23)
        val bill = FinanceBill(
            id = 4,
            title = "Internet",
            categoryName = "Bills",
            amountMinor = 12_000,
            currencyCode = "YER",
            walletId = 1,
            frequency = "MONTHLY",
            anchorDay = 23,
            anchorMonth = 10,
            nextDueAt = dueAt
        )
        assertEquals(true, FinanceBillPolicy.shouldScheduleReminder(bill))
        val delivered = bill.copy(lastReminderOccurrenceKey = FinanceBillPolicy.occurrenceKey(dueAt))
        assertEquals(false, FinanceBillPolicy.shouldScheduleReminder(delivered))
        assertEquals(false, FinanceBillPolicy.shouldScheduleReminder(delivered.copy(isActive = false)))
        val nextCycle = delivered.copy(nextDueAt = FinanceBillPolicy.nextDueAt(dueAt, "MONTHLY", 23, 10)!!)
        assertEquals(true, FinanceBillPolicy.shouldScheduleReminder(nextCycle))
    }

    @Test
    fun occurrenceKeyUsesLocalCalendarDate() {
        assertEquals("2026-11-23", FinanceBillPolicy.occurrenceKey(date(2026, 11, 23)))
    }

    @Test
    fun confirmedBillPaymentIsAnExpenseAndReducesOnlyItsWallet() {
        val wallet = FinanceWallet(id = 9, name = "المحفظة", currencyCode = "YER", openingBalanceMinor = 80_000)
        val billTransaction = FinanceTransaction(
            amountMinor = 20_000,
            kind = "EXPENSE",
            note = "دفع فاتورة: الإنترنت",
            category = "فواتير",
            walletId = wallet.id,
            currencyCode = wallet.currencyCode,
            billId = 5
        )
        assertEquals(java.math.BigInteger.valueOf(60_000), FinanceLedger.calculateBalance(wallet, listOf(billTransaction), emptyList()))
        assertEquals(5L, billTransaction.billId)
    }
}
