package com.malekfinance

import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.malekfinance.voice.SystemSpeechController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No microphone is opened; these tests can be run on a real phone. */
@RunWith(AndroidJUnit4::class)
class SystemSpeechRequestTest {
    @Test fun requestUsesArabicAndPrefersOfflineWithoutFinancialContext() {
        val intent = SystemSpeechController.recognitionIntent()
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("ar-YE", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertEquals(1, intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0))
        assertEquals(
            setOf(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.EXTRA_LANGUAGE,
                RecognizerIntent.EXTRA_PREFER_OFFLINE, RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                RecognizerIntent.EXTRA_MAX_RESULTS),
            intent.extras!!.keySet()
        )
    }

    @Test fun missingOfflineLanguageDoesNotClaimRecognitionSucceeded() {
        assertTrue(SystemSpeechController.errorMessage(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE).contains("حزمة العربية"))
        assertTrue(SystemSpeechController.errorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS).contains("إذن الميكروفون"))
        assertTrue(SystemSpeechController.errorMessage(SpeechRecognizer.ERROR_NETWORK).contains("لم ننتقل تلقائياً"))
    }
}
