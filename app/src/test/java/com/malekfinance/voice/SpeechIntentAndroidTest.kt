package com.malekfinance.voice

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.speech.RecognizerIntent
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Android framework simulation on the JVM, not a device/emulator or proof of offline Arabic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29, 30, 31, 34], manifest = Config.NONE)
class SpeechIntentAndroidTest {
    @Test fun legacyIntentRestoresWorkingYemeniLocaleAndPrompt() {
        val intent = VoiceRecognitionIntentFactory.create()
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("ar-YE", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals("مثال: صرفت ١٥٠٠ ريال بنزين", intent.getStringExtra(RecognizerIntent.EXTRA_PROMPT))
        assertEquals(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM, intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE))
        assertEquals(setOf(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.EXTRA_LANGUAGE,
            RecognizerIntent.EXTRA_PROMPT, RecognizerIntent.EXTRA_PREFER_OFFLINE, RecognizerIntent.EXTRA_MAX_RESULTS), intent.extras!!.keySet())
    }

    @Test fun activityUsesExtraResultsAndDoesNotRequireBindableRecognitionService() {
        val context: Context = org.robolectric.RuntimeEnvironment.getApplication()
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "test.speech"; name = "test.speech.Dictation"; exported = true }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(VoiceRecognitionIntentFactory.create(), info)
        val available = VoiceRecognitionIntentFactory.capabilities(context)
        assertTrue(available.activityAvailable)
        assertEquals(SpeechRoute.ACTIVITY, SpeechRoutePolicy.select(available))
        val result = Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("صرفت ١٥٠٠ ريال بنزين"))
        assertEquals(SpeechOutcome.Transcript("صرفت ١٥٠٠ ريال بنزين"), VoiceRecognitionIntentFactory.decode(true, Activity.RESULT_OK, result))
        assertEquals(SpeechOutcome.Ignored, VoiceRecognitionIntentFactory.decode(false, Activity.RESULT_OK, result))
        val parsed = VoiceTransactionParser.parse((VoiceRecognitionIntentFactory.decode(true, Activity.RESULT_OK, result) as SpeechOutcome.Transcript).text)
        assertEquals("1500", parsed.amount)
        assertEquals("EXPENSE", parsed.kind)
    }

    @Test fun cancellationErrorsAndEmptyResultsAreNotTranscripts() {
        assertEquals(SpeechOutcome.Cancelled, VoiceRecognitionIntentFactory.decode(true, Activity.RESULT_CANCELED, null))
        assertEquals(SpeechOutcome.Empty, VoiceRecognitionIntentFactory.decode(true, Activity.RESULT_OK, null))
        assertEquals(SpeechOutcome.Empty, VoiceRecognitionIntentFactory.decode(true, Activity.RESULT_OK, Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("  "))))
        for (code in listOf(RecognizerIntent.RESULT_AUDIO_ERROR, RecognizerIntent.RESULT_CLIENT_ERROR,
            RecognizerIntent.RESULT_NETWORK_ERROR, RecognizerIntent.RESULT_SERVER_ERROR, RecognizerIntent.RESULT_NO_MATCH, 987)) {
            assertTrue(VoiceRecognitionIntentFactory.decode(true, code, null) is SpeechOutcome.Failure)
        }
    }

    @Test fun directIntentAlsoKeepsYemeniLocale() {
        val intent = SystemSpeechController.recognitionIntent()
        assertEquals("ar-YE", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
    }
}
