package com.malekfinance.ui

import android.Manifest
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.withResumed
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.malekfinance.voice.SystemSpeechController

/** System speech fills an editable field; neither partial nor final results save money. */
@Composable
internal fun TextCommandPanel(enabled: Boolean, onCommand: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentEnabled by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()
    var permissionGeneration by remember { mutableIntStateOf(0) }
    var command by rememberSaveable { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("استخدم خدمة الصوت الموجودة على هاتفك أو اكتب العبارة يدوياً.") }
    var showProviderWarning by remember { mutableStateOf(false) }
    var pendingPermission by remember { mutableStateOf(false) }
    var pendingOnDevice by remember { mutableStateOf(false) }
    val controller = remember(context.applicationContext) {
        SystemSpeechController(
            context,
            onPartial = { partial = it },
            onResult = { command = it },
            onMessage = { message = it },
            onFinished = { listening = false; partial = "" }
        )
    }

    fun start(onDevice: Boolean) {
        if (!currentEnabled || listening || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        listening = true
        partial = ""
        message = if (onDevice) "جارٍ تشغيل التعرف المحلي على الهاتف…" else "جارٍ تشغيل خدمة الهاتف مع طلب العمل دون اتصال…"
        controller.start(onDevice)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = pendingPermission
        pendingPermission = false
        if (requested && granted) {
            val requestGeneration = permissionGeneration
            val onDevice = pendingOnDevice
            scope.launch {
                lifecycle.withResumed {
                    if (permissionGeneration == requestGeneration) start(onDevice)
                }
            }
        } else if (requested) message = "إذن الميكروفون مرفوض. يمكنك الكتابة أو السماح به من إعدادات التطبيق."
    }

    fun requestStart(onDevice: Boolean) {
        if (!currentEnabled || listening || pendingPermission) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            start(onDevice)
        } else {
            pendingOnDevice = onDevice
            pendingPermission = true
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                permissionGeneration++
                pendingPermission = false
                showProviderWarning = false
                controller.cancel()
                message = "توقف الاستماع عند مغادرة الشاشة. لم تُحفظ أي عملية."
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); permissionGeneration++; pendingPermission = false; controller.cancel() }
    }
    LaunchedEffect(enabled) {
        if (!enabled) { permissionGeneration++; pendingPermission = false; showProviderWarning = false; controller.cancel() }
    }

    if (showProviderWarning) {
        AlertDialog(
            onDismissRequest = { showProviderWarning = false },
            title = { Text("خدمة التعرف على الهاتف") },
            text = { Text("واجهة التعرف المحلي الصريح غير متاحة على هذا الهاتف. سنطلب من خدمة النظام (مثل Google) العمل دون إنترنت، لكن بعض الخدمات قد تتجاهل الطلب. لضمان عدم الاتصال، فعّل وضع الطيران وأوقف Wi-Fi قبل المتابعة. قد تحتاج العربية إلى حزمة مثبتة في إعدادات الخدمة؛ التطبيق لا ينزّل نموذجاً.") },
            confirmButton = { TextButton(onClick = { showProviderWarning = false; requestStart(false) }) { Text("متابعة بخدمة الهاتف") } },
            dismissButton = { TextButton(onClick = { showProviderWarning = false }) { Text("الكتابة بدلاً من ذلك") } }
        )
    }

    Surface(shape = RoundedCornerShape(28.dp), color = FinanceColors.Surface, border = BorderStroke(1.dp, FinanceColors.Divider)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("مساعدك المالي الصوتي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("الصوت عبر خدمة هاتفك", color = FinanceColors.Accent)
            Text("لا محرك أو نموذج صوتي داخل التطبيق. يستخدم التعرف المحلي الصريح عند توفره؛ دعم العربية دون إنترنت يعتمد على خدمة الهاتف وحزمها.", color = FinanceColors.TextSecondary)
            Text(message, color = FinanceColors.TextSecondary)
            if (partial.isNotBlank()) Text(partial, color = FinanceColors.Accent)
            OutlinedButton(
                enabled = enabled && !pendingPermission,
                onClick = {
                    if (listening) {
                        controller.cancel()
                        message = "أُلغي الاستماع دون حفظ."
                    } else if (controller.hasOnDeviceService()) {
                        requestStart(true)
                    } else if (controller.hasSystemService()) {
                        showProviderWarning = true
                    } else {
                        message = "لا توجد خدمة تعرف صوتي متاحة. تحقق من تفعيل خدمة Google/النظام أو استخدم الكتابة."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (listening) "إلغاء الاستماع" else "ابدأ التحدث") }
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                enabled = enabled && !listening,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اكتب العملية أو السؤال") },
                placeholder = { Text("مثال: صرفت ألف وخمسمية ريال بنزين") },
                minLines = 2
            )
            Button(
                onClick = { onCommand(command.trim()) },
                enabled = enabled && !listening && command.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("مراجعة العبارة") }
            Text("راجع النص ثم المسودة؛ لا تُحفظ أي عملية إلا بعد تأكيدك.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
