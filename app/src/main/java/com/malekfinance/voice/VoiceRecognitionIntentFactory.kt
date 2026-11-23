package com.malekfinance.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

/** Android speech adapter; text interpretation remains in VoiceTransactionParser. */
object VoiceRecognitionIntentFactory {
    fun create(context: Context): Intent? {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-YE")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-YE")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "مثال: صرفت خمسة آلاف ريال بنزين")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
