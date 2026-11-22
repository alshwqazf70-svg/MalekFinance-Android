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

    @Test fun keepsManualReviewWhenNoAmountIsSpoken() {
        val parsed = VoiceTransactionParser.parse("اشتريت أغراض من السوق")
        assertEquals(null, parsed.amount)
        assertEquals(null, parsed.kind)
    }
}
