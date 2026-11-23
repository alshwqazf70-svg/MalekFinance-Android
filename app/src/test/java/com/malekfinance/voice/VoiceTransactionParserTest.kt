package com.malekfinance.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTransactionParserTest {
    @Test fun parsesArabicDigitsAndExpenseCategory() {
        val parsed = VoiceTransactionParser.parse("صرف ١٥٠٠ ريال بنزين")
        assertEquals("1500", parsed.amount)
        assertEquals("EXPENSE", parsed.kind)
        assertEquals("بنزين", parsed.category)
        assertEquals("بنزين", parsed.description)
    }

    @Test fun detectsSalaryAsIncome() {
        val parsed = VoiceTransactionParser.parse("استلمت راتب 25000 ريال")
        assertEquals("25000", parsed.amount)
        assertEquals("INCOME", parsed.kind)
        assertEquals("راتب", parsed.category)
    }

    @Test fun supportsPersianDigitsAndDecimalAmounts() {
        val parsed = VoiceTransactionParser.parse("مطعم ۱۲۳٫۵٠")
        assertEquals("123.50", parsed.amount)
        assertEquals("مطاعم", parsed.category)
    }

    @Test fun doesNotGuessWhenIncomeAndExpenseIntentAreBothPresent() {
        val parsed = VoiceTransactionParser.parse("استلمت ١٠٠٠ ثم صرفت ٢٠٠")
        assertEquals(null, parsed.kind)
    }

    @Test fun doesNotGuessTransactionTypeFromAmountAndCategoryAlone() {
        val parsed = VoiceTransactionParser.parse("١٥٠٠ ريال بنزين")
        assertEquals(null, parsed.kind)
    }

    @Test fun detectsExpenseIntentEvenWhenAmountIsMissing() {
        val parsed = VoiceTransactionParser.parse("اشتريت أغراض من السوق")
        assertEquals(null, parsed.amount)
        assertEquals("EXPENSE", parsed.kind)
    }
    @Test fun parsesSpokenYemeniThousandsAndTens() {
        val parsed = VoiceTransactionParser.parse("صرفت خمسة آلاف ريال بنزين")
        assertEquals("5000", parsed.amount)
        assertEquals("EXPENSE", parsed.kind)
        assertEquals("بنزين", parsed.category)
    }

    @Test fun parsesCompoundSpokenAmounts() {
        assertEquals("150000", VoiceTransactionParser.parse("استلمت مئة وخمسين ألف راتب").amount)
        assertEquals("25000", VoiceTransactionParser.parse("دفعت خمسة وعشرين ألف إيجار").amount)
        assertEquals("2000", VoiceTransactionParser.parse("صرفت ألفين للمواصلات").amount)
    }

    @Test fun recognizesBalanceAndPeriodQueriesWithoutCreatingTransactions() {
        val balance = VoiceTransactionParser.parse("كم باقي معي في المحفظة؟")
        assertEquals(VoiceCommandType.BALANCE_QUERY, balance.command)
        assertEquals(null, balance.kind)

        val spending = VoiceTransactionParser.parse("كم صرفت هذا الأسبوع؟")
        assertEquals(VoiceCommandType.SPENDING_QUERY, spending.command)
        assertEquals("WEEK", spending.queryPeriod)
        assertEquals(null, spending.kind)
    }

    @Test fun recognizesIncomeSummaryAndYemeniTodayExpressions() {
        val income = VoiceTransactionParser.parse("كم استلمت هذا الشهر؟")
        assertEquals(VoiceCommandType.INCOME_QUERY, income.command)
        assertEquals("MONTH", income.queryPeriod)

        val summary = VoiceTransactionParser.parse("اعطني ملخص مالي")
        assertEquals(VoiceCommandType.SUMMARY_QUERY, summary.command)
    }

    @Test fun negatedActionsDoNotBecomeFinancialTransactions() {
        assertEquals(null, VoiceTransactionParser.parse("ما صرفت شيء").kind)
        assertEquals(null, VoiceTransactionParser.parse("لم أستلم راتبي").kind)
    }

    @Test fun keepsAmbiguousMultipleMoneyIntentsUnresolved() {
        assertEquals(null, VoiceTransactionParser.parse("استلمت ألف ثم صرفت خمسمئة").kind)
    }

}
