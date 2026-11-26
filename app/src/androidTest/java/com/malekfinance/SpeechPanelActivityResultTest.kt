package com.malekfinance

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextContains
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.rule.GrantPermissionRule
import com.malekfinance.ui.FinanceTheme
import com.malekfinance.ui.TextCommandPanel
import com.malekfinance.voice.SpeechCapabilities
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Instrumentation tests with a test registry, not an external recorder. Real-device execution. */
class SpeechPanelActivityResultTest {
    @get:Rule val composeRule = createComposeRule()
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private class Registry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        var requestCode = 0
        var input: Intent? = null
        var failLaunch = false
        override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            if (failLaunch) throw ActivityNotFoundException("Test provider disappeared")
            requestCode = code
            this.input = input as Intent
        }
        fun complete(code: Int, text: String? = null) {
            val data = text?.let { Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(it)) }
            dispatchResult(requestCode, code, data)
        }
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun externalActivityStopAndStateRestorationDoNotDiscardItsResultOrDeliverItTwice() {
        val provider = Registry()
        val owner = Owner()
        val results = mutableListOf<String>()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides provider, LocalLifecycleOwner provides owner) {
                FinanceTheme { TextCommandPanel(true, results::add) { SpeechCapabilities(29, true, false) } }
            }
        }
        composeRule.onNodeWithText("ابدأ التحدث").performClick()
        composeRule.waitForIdle()
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, provider.input!!.action)
        assertEquals("ar-YE", provider.input!!.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            provider.complete(Activity.RESULT_OK, "صرفت ١٥٠٠ ريال بنزين")
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.onNodeWithTag("speech-command").assertTextContains("صرفت ١٥٠٠ ريال بنزين")
        assertEquals(listOf("صرفت ١٥٠٠ ريال بنزين"), results)
        composeRule.runOnIdle { provider.complete(Activity.RESULT_OK, "صرفت ٩٩٩٩") }
        assertEquals(1, results.size)
    }

    @Test fun cancelEmptyResultAndLaunchFailureDoNotCreateDraftsAndAllowRetry() {
        val provider = Registry()
        val results = mutableListOf<String>()
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides provider) {
                FinanceTheme { TextCommandPanel(true, results::add) { SpeechCapabilities(29, true, false) } }
            }
        }
        composeRule.onNodeWithText("ابدأ التحدث").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { provider.complete(Activity.RESULT_CANCELED) }
        composeRule.onNodeWithText("ابدأ التحدث").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { provider.complete(Activity.RESULT_OK) }
        composeRule.runOnIdle { provider.failLaunch = true }
        composeRule.onNodeWithText("ابدأ التحدث").performClick()
        composeRule.waitForIdle()
        assertTrue(results.isEmpty())
        composeRule.onNodeWithText("ابدأ التحدث").assertExists()
    }
}
