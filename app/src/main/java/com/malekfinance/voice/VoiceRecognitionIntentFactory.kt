package com.malekfinance.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** The activity-based API that worked on the owner's Samsung Note 9 / Android 10. */
object VoiceRecognitionIntentFactory {
    const val LANGUAGE = "ar-YE"
    const val PROMPT = "مثال: صرفت ١٥٠٠ ريال بنزين"

    fun create(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PROMPT, PROMPT)
        // A preference only; never a promise about an external provider's network behaviour.
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    fun capabilities(context: Context): SpeechCapabilities = SpeechCapabilities(
        sdk = Build.VERSION.SDK_INT,
        activityAvailable = runCatching { create().resolveActivity(context.packageManager) != null }.getOrDefault(false),
        onDeviceAvailable = Build.VERSION.SDK_INT >= 31 &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    )

    /** Activity results use EXTRA_RESULTS, not the direct service's RESULTS_RECOGNITION. */
    fun decode(awaitingResult: Boolean, resultCode: Int, data: Intent?): SpeechOutcome {
        val status = when (resultCode) {
            Activity.RESULT_OK -> SpeechActivityStatus.OK
            Activity.RESULT_CANCELED -> SpeechActivityStatus.CANCELLED
            RecognizerIntent.RESULT_NO_MATCH -> SpeechActivityStatus.NO_MATCH
            RecognizerIntent.RESULT_AUDIO_ERROR -> SpeechActivityStatus.AUDIO_ERROR
            RecognizerIntent.RESULT_CLIENT_ERROR -> SpeechActivityStatus.CLIENT_ERROR
            RecognizerIntent.RESULT_NETWORK_ERROR -> SpeechActivityStatus.NETWORK_ERROR
            RecognizerIntent.RESULT_SERVER_ERROR -> SpeechActivityStatus.SERVER_ERROR
            else -> SpeechActivityStatus.UNKNOWN_ERROR
        }
        val candidates = runCatching { data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) }.getOrNull()
        return SpeechResultPolicy.fromActivity(awaitingResult, status, candidates)
    }

    fun message(outcome: SpeechOutcome): String = when (outcome) {
        is SpeechOutcome.Transcript -> "اكتمل التعرف وجرى تحليل النص. راجع المسودة وعدّلها قبل التأكيد؛ لم تُحفظ أي عملية."
        SpeechOutcome.Cancelled -> "أُلغيت واجهة التعرف دون حفظ. يمكنك المحاولة مجدداً أو الكتابة."
        SpeechOutcome.Empty -> "لم تُرجع خدمة الهاتف كلاماً واضحاً. تحقق من العربية أو أعد المحاولة أو اكتب يدوياً."
        SpeechOutcome.Ignored -> "أُهملت نتيجة جلسة منتهية."
        is SpeechOutcome.Failure -> when (outcome.status) {
            SpeechActivityStatus.NO_MATCH -> "لم تلتقط الخدمة كلاماً واضحاً أو انتهت المهلة. تحقق من توفر العربية وأعد المحاولة."
            SpeechActivityStatus.NETWORK_ERROR -> "طلبت خدمة الهاتف اتصالاً ولم تكتمل. تحقق من حزمة العربية دون اتصال؛ لن نبدّل إلى خادم أو مزود آخر."
            SpeechActivityStatus.AUDIO_ERROR -> "تعذر التقاط الصوت. تحقق من الميكروفون وإذنه لدى التطبيق وخدمة الهاتف."
            SpeechActivityStatus.CLIENT_ERROR -> "تعذر تشغيل التعرف بهذه الخدمة. تحقق من تفعيل خدمة Google/النظام والعربية في إعداداتها."
            else -> "أعادت خدمة الهاتف خطأً أثناء التعرف. تحقق من دعم العربية أو جرّب الكتابة؛ لم تُحفظ أي عملية."
        }
    }
}
