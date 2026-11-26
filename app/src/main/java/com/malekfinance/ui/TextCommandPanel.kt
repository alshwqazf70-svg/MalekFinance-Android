package com.malekfinance.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.withResumed
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.malekfinance.voice.SpeechCapabilities
import com.malekfinance.voice.SpeechOutcome
import com.malekfinance.voice.SpeechRoute
import com.malekfinance.voice.SpeechRoutePolicy
import com.malekfinance.voice.SystemSpeechController
import com.malekfinance.voice.VoiceRecognitionIntentFactory

/** Activity dictation is the compatibility default. Results create drafts, never ledger entries. */
@Composable
internal fun TextCommandPanel(
    enabled: Boolean,
    onCommand: (String) -> Unit,
    capabilityProbe: (Context) -> SpeechCapabilities = VoiceRecognitionIntentFactory::capabilities
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentEnabled by rememberUpdatedState(enabled)
    val currentCommand by rememberUpdatedState(onCommand)
    var command by rememberSaveable { mutableStateOf("") }
    var preferOnDevice by rememberSaveable { mutableStateOf(false) }
    // These survive rotation/process recreation while the provider's activity is in front.
    var awaitingActivityResult by rememberSaveable { mutableStateOf(false) }
    var pendingPermission by rememberSaveable { mutableStateOf(false) }
    var requestedRoute by rememberSaveable { mutableStateOf(SpeechRoute.UNAVAILABLE.name) }
    var queuedRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var lastRoute by rememberSaveable { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("اضغط ابدأ التحدث لفتح واجهة التعرف الموجودة على هاتفك، أو اكتب العبارة.") }
    var capabilities by remember { mutableStateOf(capabilityProbe(context)) }

    fun receive(text: String) {
        if (!currentEnabled) return
        command = text
        currentCommand(text) // Existing VoiceTransactionParser -> editable draft, not save.
    }

    val controller = remember(context.applicationContext) {
        SystemSpeechController(
            context,
            onPartial = { partial = it },
            onResult = { receive(it) },
            onMessage = { message = it },
            onFinished = { listening = false; partial = "" }
        )
    }

    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val outcome = VoiceRecognitionIntentFactory.decode(awaitingActivityResult && currentEnabled, result.resultCode, result.data)
        awaitingActivityResult = false // Consume before calling the parser; ignore repeated/late results.
        if (outcome != SpeechOutcome.Ignored) message = VoiceRecognitionIntentFactory.message(outcome)
        if (outcome is SpeechOutcome.Transcript) receive(outcome.text)
    }

    fun start(route: SpeechRoute) {
        if (!currentEnabled || listening || awaitingActivityResult) return
        if (route == SpeechRoute.UNAVAILABLE) {
            message = "لا توجد واجهة أو خدمة محلية متاحة للتعرف. تحقق من تفعيل خدمة Google/النظام أو استخدم الكتابة."
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            message = "إذن الميكروفون غير متاح. اسمح به من إعدادات التطبيق أو استخدم الكتابة."
            return
        }
        partial = ""
        when (route) {
            SpeechRoute.ACTIVITY -> {
                lastRoute = "واجهة الهاتف • ar-YE"
                awaitingActivityResult = true
                message = "بانتظار نتيجة واجهة الهاتف… ألغِ التسجيل بزر الرجوع في واجهتها إذا أردت."
                try {
                    activityLauncher.launch(VoiceRecognitionIntentFactory.create())
                } catch (_: ActivityNotFoundException) {
                    awaitingActivityResult = false
                    message = "لا يوجد تطبيق يفتح واجهة التعرف. تحقق من تفعيل Google/خدمة الهاتف أو استخدم الكتابة."
                } catch (_: SecurityException) {
                    awaitingActivityResult = false
                    message = "رفض النظام تشغيل واجهة الصوت. تحقق من أذونات التطبيق وخدمة الهاتف."
                } catch (_: Exception) {
                    awaitingActivityResult = false
                    message = "تعذر فتح واجهة التعرف على الهاتف. أعد المحاولة أو استخدم الكتابة."
                }
            }
            SpeechRoute.ON_DEVICE -> {
                if (!capabilityProbe(context).onDeviceAvailable) {
                    message = "التعرف المحلي المباشر لم يعد متاحاً. يمكنك اختيار واجهة الهاتف والمحاولة بنفسك."
                    return
                }
                lastRoute = "محلي مباشر • ar-YE"
                listening = true
                message = "جارٍ تشغيل التعرف المحلي المباشر…"
                controller.start()
            }
            SpeechRoute.UNAVAILABLE -> message = "لا توجد واجهة أو خدمة محلية متاحة للتعرف. تحقق من تفعيل خدمة Google/النظام أو استخدم الكتابة."
        }
    }

    // Permission callbacks may arrive before RESUMED. Queue rather than losing the tap.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = pendingPermission
        pendingPermission = false
        if (requested && granted && currentEnabled) queuedRoute = requestedRoute
        else if (requested) message = "إذن الميكروفون مرفوض. يمكنك الكتابة أو السماح به من إعدادات التطبيق."
    }
    LaunchedEffect(queuedRoute, enabled) {
        val route = queuedRoute ?: return@LaunchedEffect
        if (enabled) lifecycle.withResumed {
            if (queuedRoute == route) {
                queuedRoute = null
                start(SpeechRoute.valueOf(route))
            }
        }
    }

    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) capabilities = capabilityProbe(context)
            if (event == Lifecycle.Event.ON_STOP && listening) {
                controller.cancel()
                message = "توقف الاستماع المحلي عند مغادرة الشاشة. لم تُحفظ أي عملية."
            }
            // ON_STOP is EXPECTED for external recognition and permission UI. Do not clear
            // awaitingActivityResult/pendingPermission here or successful results will be lost.
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.cancel() }
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            pendingPermission = false
            queuedRoute = null
            awaitingActivityResult = false
            controller.cancel()
        }
    }

    val busy = listening || awaitingActivityResult || pendingPermission || queuedRoute != null
    Surface(shape = RoundedCornerShape(28.dp), color = FinanceColors.Surface, border = BorderStroke(1.dp, FinanceColors.Divider)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("مساعدك المالي الصوتي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("الصوت عبر خدمة هاتفك", color = FinanceColors.Accent)
            Text("واجهة الهاتف هي المسار الافتراضي، ومنها Google عند توفرها. لا نموذج صوتي داخل التطبيق. نطلب العمل دون اتصال، لكن هذا ليس ضماناً من مزود الخدمة؛ تحقق بوضع الطيران وإيقاف Wi-Fi.", color = FinanceColors.TextSecondary)
            if (capabilities.sdk >= 31 && capabilities.onDeviceAvailable) {
                FilterChip(
                    selected = preferOnDevice,
                    enabled = enabled && !busy,
                    onClick = { preferOnDevice = !preferOnDevice },
                    label = { Text("التعرف المحلي المباشر (اختياري)") }
                )
            }
            if (lastRoute.isNotEmpty()) Text("المسار: $lastRoute", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(message, color = FinanceColors.TextSecondary)
            if (partial.isNotBlank()) Text(partial, color = FinanceColors.Accent)
            OutlinedButton(
                enabled = enabled && (!busy || listening),
                onClick = {
                    if (listening) {
                        controller.cancel()
                        message = "أُلغي الاستماع دون حفظ."
                    } else {
                        val available = capabilityProbe(context)
                        capabilities = available
                        val route = SpeechRoutePolicy.select(available, preferOnDevice)
                        if (route == SpeechRoute.UNAVAILABLE) {
                            start(route)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            queuedRoute = route.name
                        } else {
                            requestedRoute = route.name
                            pendingPermission = true
                            try { permission.launch(Manifest.permission.RECORD_AUDIO) }
                            catch (_: Exception) { pendingPermission = false; message = "تعذر طلب إذن الميكروفون. استخدم إعدادات التطبيق أو الكتابة." }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (listening) "إلغاء الاستماع" else if (awaitingActivityResult) "بانتظار واجهة الهاتف" else "ابدأ التحدث") }
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                enabled = enabled && !busy,
                modifier = Modifier.fillMaxWidth().testTag("speech-command"),
                label = { Text("اكتب العملية أو السؤال") },
                placeholder = { Text("مثال: صرفت ألف وخمسمية ريال بنزين") },
                minLines = 2
            )
            Button(
                onClick = { currentCommand(command.trim()) },
                enabled = enabled && !busy && command.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("مراجعة العبارة") }
            Text("يُحلل الكلام إلى مسودة قابلة للتعديل. لا تُحفظ أي عملية إلا بعد تأكيدك.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
