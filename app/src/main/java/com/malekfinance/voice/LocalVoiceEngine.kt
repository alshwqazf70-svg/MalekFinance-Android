package com.malekfinance.voice

import android.content.Context
import java.io.File
import java.io.InputStream
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** Embedded Vosk runtime; no Android recognition intent, cloud fallback, or audio files. */
class LocalVoiceEngine private constructor(private val model: Model) : AutoCloseable {
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null

    fun start(listener: RecognitionListener) {
        stop()
        try {
            val recognizer = Recognizer(model, 16_000f).also { this.recognizer = it }
            val service = SpeechService(recognizer, 16_000f).also { this.service = it }
            check(service.startListening(listener, 20_000))
        } catch (failure: Throwable) {
            stop()
            throw failure
        }
    }

    fun stop() {
        service?.let { it.cancel(); it.shutdown() }
        service = null
        recognizer?.close()
        recognizer = null
    }

    override fun close() { stop(); model.close() }

    companion object {
        private fun directory(context: Context) = File(context.noBackupFilesDir, "offline-voice")
        fun installed(context: Context) = File(directory(context), "model/am/final.mdl").isFile
        // Call off the main thread: model loading can take several seconds.
        fun open(context: Context): LocalVoiceEngine = LocalVoiceEngine(Model(File(directory(context), "model").absolutePath))

        /** First installation only. A failed or interrupted import never exposes a partial model. */
        @Synchronized
        fun install(context: Context, input: InputStream) {
            val base = directory(context).apply { check(mkdirs() || isDirectory) }
            check(!installed(context)) { "A model is already installed" }
            val staging = File(base, "staging")
            staging.deleteRecursively()
            try {
                val root = OfflineModelArchive.extract(input, staging)
                // Validate with the native engine before publishing the directory.
                Model(root.absolutePath).use { }
                val active = File(base, "model")
                if (active.exists()) check(active.deleteRecursively())
                check(root.renameTo(active)) { "Could not activate model" }
            } finally { staging.deleteRecursively() }
        }
    }
}
