package com.malekfinance.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCompatibilityTest {
    @Test fun android6Through11AlwaysUsesActivityWhenAvailableEvenWithReportedLocalService() {
        for (api in 23..30) {
            for (reportedLocal in listOf(false, true)) {
                for (preferLocal in listOf(false, true)) {
                    assertEquals(SpeechRoute.ACTIVITY, SpeechRoutePolicy.select(SpeechCapabilities(api, true, reportedLocal), preferLocal))
                    assertEquals(SpeechRoute.UNAVAILABLE, SpeechRoutePolicy.select(SpeechCapabilities(api, false, reportedLocal), preferLocal))
                }
            }
        }
    }

    @Test fun modernDevicesUseCapabilitiesNotOnlyTheirApiNumber() {
        for (api in 31..35) {
            assertEquals(SpeechRoute.ACTIVITY, SpeechRoutePolicy.select(SpeechCapabilities(api, true, true)))
            assertEquals(SpeechRoute.ACTIVITY, SpeechRoutePolicy.select(SpeechCapabilities(api, true, false), true))
            assertEquals(SpeechRoute.ON_DEVICE, SpeechRoutePolicy.select(SpeechCapabilities(api, true, true), true))
            assertEquals(SpeechRoute.ON_DEVICE, SpeechRoutePolicy.select(SpeechCapabilities(api, false, true)))
            assertEquals(SpeechRoute.UNAVAILABLE, SpeechRoutePolicy.select(SpeechCapabilities(api, false, false)))
        }
    }

    @Test fun onlyActiveSuccessfulNonEmptyResultsProduceText() {
        assertEquals(SpeechOutcome.Transcript("صرفت ١٥٠٠ ريال بنزين"), SpeechResultPolicy.fromActivity(true, SpeechActivityStatus.OK, listOf("  صرفت ١٥٠٠ ريال بنزين ")))
        for (input in listOf(null, emptyList(), listOf("", "  "))) {
            assertEquals(SpeechOutcome.Empty, SpeechResultPolicy.fromActivity(true, SpeechActivityStatus.OK, input))
        }
        for (status in SpeechActivityStatus.entries) {
            assertEquals(SpeechOutcome.Ignored, SpeechResultPolicy.fromActivity(false, status, listOf("صرفت ١٥٠٠")))
        }
        assertEquals(SpeechOutcome.Cancelled, SpeechResultPolicy.fromActivity(true, SpeechActivityStatus.CANCELLED, listOf("صرفت ١٥٠٠")))
    }

    @Test fun errorsCannotProduceFinancialCommandsEvenIfAnExtraContainsText() {
        for (status in SpeechActivityStatus.entries.filter { it != SpeechActivityStatus.OK && it != SpeechActivityStatus.CANCELLED }) {
            assertEquals(SpeechOutcome.Failure(status), SpeechResultPolicy.fromActivity(true, status, listOf("صرفت ١٥٠٠ ريال بنزين")))
        }
    }

    @Test fun recognizedArabicUsesRealFinancialParser() {
        val outcome = SpeechResultPolicy.fromActivity(true, SpeechActivityStatus.OK, listOf("صرفت ألف وخمسمية ريال بنزين"))
        assertTrue(outcome is SpeechOutcome.Transcript)
        val parsed = VoiceTransactionParser.parse((outcome as SpeechOutcome.Transcript).text)
        assertEquals(VoiceCommandType.TRANSACTION, parsed.command)
        assertEquals("1500", parsed.amount)
        assertEquals("EXPENSE", parsed.kind)
        assertEquals("بنزين", parsed.category)
    }
}
