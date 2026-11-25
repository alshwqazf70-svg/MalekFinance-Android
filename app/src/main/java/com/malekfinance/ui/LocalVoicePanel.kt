package com.malekfinance.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.malekfinance.voice.LocalVoiceEngine
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.android.RecognitionListener

@Composable
internal fun LocalVoicePanel(enabled: Boolean, onTranscript: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val currentTranscript by rememberUpdatedState(onTranscript)
    val currentEnabled by rememberUpdatedState(enabled)
    var engine by remember { mutableStateOf<LocalVoiceEngine?>(null) }
    var installed by remember { mutableStateOf(LocalVoiceEngine.installed(context)) }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("ثبّت نموذج Vosk عربياً مرة واحدة لبدء التعرف المحلي.") }
    var partial by remember { mutableStateOf("") }

    fun stop() {
        generation++ // Ignore callbacks already queued by a finished recording.
        engine?.stop()
        listening = false
        partial = ""
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { stop(); message = "توقف الميكروفون عند مغادرة التطبيق." }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop(); engine?.close(); engine = null }
    }
    LaunchedEffect(enabled) { if (!enabled) stop() }

    fun listen() {
        if (busy || !installed || !currentEnabled) return
        val requestGeneration = ++generation
        busy = true
        // Finish/close native allocations even if navigation cancels the UI scope mid-load.
        scope.launch {
            withContext(NonCancellable) {
                try {
                    if (engine == null) {
                        message = "جارٍ تحميل النموذج على جهازك…"
                        val loaded = withContext(Dispatchers.IO) { LocalVoiceEngine.open(context) }
                        if (!scope.isActive || !currentEnabled || generation != requestGeneration || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            loaded.close()
                            return@withContext
                        }
                        engine = loaded
                    }
                    if (!scope.isActive || !currentEnabled || generation != requestGeneration || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@withContext
                    val sessionGeneration = requestGeneration
                    listening = true
                    partial = ""
                    message = "أستمع إليك محلياً… تحدث بعبارة واحدة."
                    engine!!.start(object : RecognitionListener {
                        private fun active() = listening && generation == sessionGeneration
                        private fun finish(json: String) {
                            if (!active()) return
                            val text = runCatching { JSONObject(json).optString("text") }.getOrDefault("")
                            if (text.isBlank()) return
                            stop()
                            message = "اكتمل التعرف المحلي. راجع المسودة قبل الحفظ."
                            currentTranscript(text)
                        }
                        override fun onPartialResult(hypothesis: String) {
                            if (active()) partial = runCatching { JSONObject(hypothesis).optString("partial") }.getOrDefault("")
                        }
                        override fun onResult(hypothesis: String) = finish(hypothesis)
                        override fun onFinalResult(hypothesis: String) = finish(hypothesis)
                        override fun onError(exception: Exception) {
                            if (active()) { stop(); message = "تعذر تشغيل الميكروفون. جرّب مجدداً أو استخدم الإدخال اليدوي." }
                        }
                        override fun onTimeout() {
                            if (active()) { stop(); message = "انتهت مهلة الاستماع دون نتيجة مكتملة. أعد المحاولة أو اكتب يدوياً." }
                        }
                    })
                } catch (_: LinkageError) {
                    stop()
                    message = "المحرك الأصلي غير متوافق مع هذا الجهاز. الإدخال اليدوي متاح؛ يلزم فحص نسخة Android ومعمارية الجهاز."
                } catch (_: Exception) {
                    stop()
                    engine?.close(); engine = null
                    message = "تعذر تحميل النموذج أو بدء التسجيل. تأكد من توافق نموذج Vosk وتوفر الذاكرة وإذن الميكروفون."
                } finally { busy = false }
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else message = "إذن الميكروفون مرفوض. الإدخال اليدوي متاح دائماً."
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy && !installed) {
            busy = true
            message = "جارٍ فحص النموذج وتثبيته… قد يستغرق ذلك بعض الوقت."
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { LocalVoiceEngine.install(context, it) }
                            ?: error("Cannot open model")
                    }
                    installed = LocalVoiceEngine.installed(context)
                    message = "النموذج جاهز. التعرف يعمل داخل التطبيق دون إنترنت."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: LinkageError) {
                    message = "لا يمكن تحميل مكتبة الصوت الأصلية على هذا الجهاز. استخدم الإدخال اليدوي حتى التحقق من التوافق."
                } catch (_: Exception) {
                    installed = LocalVoiceEngine.installed(context)
                    message = "لم يُثبت النموذج. اختر ZIP صالحاً لنموذج Vosk مع مساحة كافية (حتى ٢ جيجابايت بعد الفك)."
                } finally {
                    busy = false
                }
            }
        }
    }

    Surface(shape = RoundedCornerShape(28.dp), color = FinanceColors.Surface, border = BorderStroke(1.dp, FinanceColors.Divider)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(FinanceColors.HeroStart, FinanceColors.Surface))).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("PRIVATE VOICE", color = FinanceColors.Primary, style = MaterialTheme.typography.labelSmall)
            Surface(shape = CircleShape, color = FinanceColors.PrimaryTint, modifier = Modifier.size(76.dp).align(Alignment.CenterHorizontally)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, tint = FinanceColors.Primary, modifier = Modifier.size(34.dp)) }
            }
            Text("مساعدك المالي الصوتي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("صوتك يبقى على جهازك.", color = FinanceColors.Accent, style = MaterialTheme.typography.titleMedium)
            Text("محرك Vosk مدمج • لا خدمة نظام ولا رجوع سحابي. يحتاج نموذجاً عربياً متوافقاً من مصدر موثوق؛ جودة اللهجة تعتمد على النموذج.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(message, color = FinanceColors.TextSecondary)
            if (partial.isNotBlank()) Text(partial, color = FinanceColors.Accent)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!installed) {
                OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, enabled = !busy && enabled, modifier = Modifier.fillMaxWidth()) {
                    Text("تثبيت نموذج عربي من ملف ZIP")
                }
            } else {
                Button(onClick = {
                    if (listening) { stop(); message = "أُلغي الاستماع دون حفظ." }
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = !busy && enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (listening) "إلغاء الاستماع" else "ابدأ التحدث محلياً")
                }
            }
            Text("مثال: صرفت ألف وخمسمية ريال بنزين. لا تُحفظ أي عملية إلا بعد تأكيدك.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
