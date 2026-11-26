package com.malekfinance.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Main-thread owner of a single, explicitly requested system recognition session. */
internal class SystemSpeechController(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onFinished: () -> Unit,
    private val createRecognizer: (Context) -> SpeechRecognizer = { appContext ->
        if (Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        else throw UnsupportedOperationException("On-device recognition requires API31")
    }
) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var timeout: Runnable? = null

    /** Optional on-device path only. Never bind the default service on Android 6–11. */
    fun start() {
        cancel(notifyFinished = false)
        val session = generation
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onFinished()
            onMessage(errorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
            return
        }
        try {
            val current = createRecognizer(context)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                private fun active() = generation == session && recognizer === current
                override fun onReadyForSpeech(params: Bundle?) {
                    if (active()) onMessage("أستمع إليك… تحدث بعبارة واحدة.")
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit // Never persist raw audio.
                override fun onEndOfSpeech() {
                    if (active()) onMessage("جارٍ التعرف على العبارة…")
                }
                override fun onError(error: Int) {
                    if (!active()) return
                    cancel()
                    onMessage(errorMessage(error))
                    // Never silently retry with a network-capable provider.
                }
                override fun onResults(results: Bundle?) {
                    if (!active()) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    cancel()
                    if (text.isEmpty()) onMessage("لم تصل عبارة واضحة. أعد المحاولة أو اكتب يدوياً.")
                    else {
                        onMessage("اكتمل التعرف وجرى تحليل النص. راجع المسودة قبل التأكيد؛ لم تُحفظ أي عملية.")
                        onResult(text)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (active()) onPartial(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            timeout = Runnable {
                if (generation == session) {
                    cancel()
                    onMessage("انتهت مهلة الاستماع. أعد المحاولة أو استخدم الكتابة.")
                }
            }.also { handler.postDelayed(it, 25_000) }
            current.startListening(recognitionIntent())
        } catch (_: Exception) {
            cancel()
            onMessage("تعذر بدء خدمة الصوت على الهاتف. تحقق من إذن الميكروفون والخدمة أو استخدم الكتابة.")
        }
    }

    fun cancel(notifyFinished: Boolean = true) {
        generation++ // Invalidate callbacks before cancel/destroy can deliver more events.
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val old = recognizer
        recognizer = null
        runCatching { old?.cancel() }
        runCatching { old?.destroy() }
        if (notifyFinished) onFinished()
    }

    companion object {
        fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceRecognitionIntentFactory.LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        fun errorMessage(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "إذن الميكروفون غير متاح. اسمح به من إعدادات التطبيق أو استخدم الكتابة."
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم أتعرف على عبارة واضحة. أعد المحاولة أو اكتب يدوياً."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "العربية غير متاحة في خدمة الهاتف الحالية. تحقق من حزمة العربية دون اتصال في إعدادات الخدمة."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "خدمة الهاتف طلبت اتصالاً. تحقق من دعم العربية دون إنترنت؛ لم ننتقل تلقائياً إلى مزود آخر."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة الصوت مشغولة. أعد المحاولة بعد إغلاق أي تسجيل آخر."
            else -> "توقف التعرف الصوتي. جرّب مجدداً أو استخدم الإدخال اليدوي."
        }
    }
}
