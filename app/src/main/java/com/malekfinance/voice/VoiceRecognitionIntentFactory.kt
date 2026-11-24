package com.malekfinance.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

/**
 * Requests Arabic dictation from the Android speech provider.
 * The provider owns the engine and may use an offline model or its network service.
 */
object VoiceRecognitionIntentFactory {
    fun create(context: Context): Intent? {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Generic Arabic is more widely advertised than the ar-YE locale.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            // Allow online recognition when no compatible offline Arabic model is installed.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "مثال: صرفت خمسة آلاف ريال بنزين")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
