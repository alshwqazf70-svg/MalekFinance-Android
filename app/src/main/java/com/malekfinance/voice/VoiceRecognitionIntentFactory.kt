package com.malekfinance.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent

/** Android speech adapter; text interpretation remains in VoiceTransactionParser. */
object VoiceRecognitionIntentFactory {
    fun create(context: Context): Intent? {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // ar-YE is not offered by every installed speech provider. Request generic Arabic
            // so providers can select their available Arabic model/dialect instead of failing
            // before the user can speak.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "مثال: صرفت خمسة آلاف ريال بنزين")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Do not force offline recognition: many Android devices have no Arabic offline
            // model installed, while their online speech provider can still recognize Arabic.
        }
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
