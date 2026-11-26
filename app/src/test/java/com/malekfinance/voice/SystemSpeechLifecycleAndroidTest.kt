package com.malekfinance.voice

import android.Manifest
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SystemSpeechLifecycleAndroidTest {
    private class Session {
        val results = mutableListOf<String>()
        val partials = mutableListOf<String>()
        val messages = mutableListOf<String>()
        var finished = 0
        val app: android.app.Application = RuntimeEnvironment.getApplication()
        // Real controller with a JVM speech shadow as its resource. Never captures real audio.
        val controller = SystemSpeechController(app, partials::add, results::add, messages::add, { finished++ },
            createRecognizer = { SpeechRecognizer.createSpeechRecognizer(it) })
        fun start(): org.robolectric.shadows.ShadowSpeechRecognizer {
            shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
            controller.start()
            shadowOf(Looper.getMainLooper()).idle()
            return shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        }
        fun result(text: String) = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
        }
    }

    @Test fun successDestroysResourceRemovesTimeoutAndIgnoresDuplicateCallbacks() {
        val session = Session()
        val speech = session.start()
        speech.triggerOnPartialResults(session.result("صرفت"))
        assertTrue(session.results.isEmpty())
        speech.triggerOnResults(session.result("صرفت ١٥٠٠ ريال بنزين"))
        assertTrue(speech.isDestroyed)
        assertEquals(listOf("صرفت ١٥٠٠ ريال بنزين"), session.results)
        assertEquals(1, session.finished)
        speech.triggerOnResults(session.result("صرفت ٩٩٩٩"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(1, session.results.size)
        assertEquals(1, session.finished)
    }

    @Test fun cancelOrBackgroundDisposalDestroysResourceAndRejectsLateText() {
        val session = Session()
        val speech = session.start()
        session.controller.cancel() // Same release path used by ON_STOP and onDispose.
        assertTrue(speech.isDestroyed)
        speech.triggerOnResults(session.result("صرفت ١٥٠٠"))
        assertTrue(session.results.isEmpty())
    }

    @Test fun timeoutReleasesMicrophoneAndRejectsLateResults() {
        val session = Session()
        val speech = session.start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(26))
        assertTrue(speech.isDestroyed)
        assertEquals(1, session.finished)
        assertTrue(session.messages.last().contains("مهلة"))
        speech.triggerOnResults(session.result("صرفت ١٥٠٠"))
        assertTrue(session.results.isEmpty())
    }

    @Test fun unsupportedLanguageReleasesResourceWithoutAutomaticProviderFallback() {
        val session = Session()
        val speech = session.start()
        speech.triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertTrue(speech.isDestroyed)
        assertTrue(session.messages.last().contains("حزمة العربية"))
        assertEquals(1, session.finished)
        assertTrue(session.results.isEmpty())
    }

    @Test fun replacementInvalidatesOldSessionAndEmptyResultsDoNotProduceACommand() {
        val session = Session()
        val old = session.start()
        val current = session.start()
        assertTrue(old.isDestroyed)
        old.triggerOnResults(session.result("صرفت ٩٩٩٩"))
        assertTrue(session.results.isEmpty())
        current.triggerOnResults(Bundle())
        assertTrue(current.isDestroyed)
        assertTrue(session.results.isEmpty())
        assertTrue(session.messages.last().contains("لم تصل عبارة"))
    }

    @Test fun deniedPermissionNeverCreatesARecognizer() {
        val session = Session()
        shadowOf(session.app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        session.controller.start()
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        assertEquals(1, session.finished)
        assertTrue(session.messages.last().contains("إذن الميكروفون"))
    }

    @Test fun factoryFailureIsReportedWithoutCreatingAnotherProvider() {
        val app: android.app.Application = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        var calls = 0
        var finished = 0
        val messages = mutableListOf<String>()
        val controller = SystemSpeechController(app, {}, {}, messages::add, { finished++ }, createRecognizer = {
            calls++
            throw UnsupportedOperationException("No local provider")
        })
        controller.start()
        assertEquals(1, calls)
        assertEquals(1, finished)
        assertTrue(messages.last().contains("تعذر بدء"))
    }
}
