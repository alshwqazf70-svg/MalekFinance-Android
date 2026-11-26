package com.malekfinance.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceAuditRegressionTest {
    @Test fun fractionalWordsNeverBecomePartialAmounts() {
        listOf("صرفت نصف ألف ريال بنزين", "صرفت مليون ونص ريال", "صرفت ألف وربع ريال", "صرفت 100 ونصف ريال").forEach {
            // Unsupported fraction grammar must request clarification, never guess.
            assertNull(VoiceTransactionParser.parse(it).amount)
        }
    }

    @Test fun everyKnownCollectionPhraseHasAnExplicitIncomingDirection() {
        listOf("حصلت دين من", "حصّلت الدين", "تحصيل دين من", "استلمت سداد الدين", "استلمت سداد من").forEach {
            val parsed = VoiceTransactionParser.parse("$it أحمد 100")
            assertEquals(VoiceCommandType.DEBT_PAYMENT_DRAFT, parsed.command)
            assertEquals("OWED_TO_ME", parsed.debtPaymentDirection)
            assertNull(parsed.kind)
        }
    }

    @Test fun conflictingRepaymentDirectionsRemainUnresolved() {
        assertNull(VoiceTransactionParser.parse("سددت دين واستلمت سداد من أحمد 100").debtPaymentDirection)
    }

    @Test fun explicitNonQatPurchaseIsCreditWithoutCash() {
        val parsed = VoiceTransactionParser.parse("اشتريت خبز بالدين من أحمد 100 ريال")
        assertEquals(VoiceCommandType.DEBT_CREATE_DRAFT, parsed.command)
        assertTrue(parsed.isCreditPurchase)
        assertEquals("OWED_BY_ME", parsed.debtType)
        assertEquals("أحمد", parsed.debtPersonName)
        assertFalse(parsed.description.contains("قات"))
        assertNull(parsed.kind)
    }

    @Test fun negatedGeneralCreditPurchaseDoesNotSuggestCashOrDebt() {
        val parsed = VoiceTransactionParser.parse("ما اشتريت خبز بالدين من أحمد 100")
        assertFalse(parsed.isCreditPurchase)
        assertNull(parsed.kind)
    }
}
