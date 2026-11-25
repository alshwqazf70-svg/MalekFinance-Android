package com.malekfinance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Lightweight typed-command entry. No recognizer, model import or microphone access. */
@Composable
internal fun TextCommandPanel(enabled: Boolean, onCommand: (String) -> Unit) {
    var command by rememberSaveable { mutableStateOf("") }
    Surface(shape = RoundedCornerShape(28.dp), color = FinanceColors.Surface, border = BorderStroke(1.dp, FinanceColors.Divider)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("مساعدك المالي النصي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("التعرف الصوتي غير مفعّل في هذه النسخة.", color = FinanceColors.Accent)
            Text("أُزيل محرك الصوت وخيار استيراد النماذج لتقليل حجم التطبيق. لا تنزيلات صوتية ولا تسجيل ولا خدمة سحابية.", color = FinanceColors.TextSecondary)
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اكتب العملية أو السؤال") },
                placeholder = { Text("مثال: صرفت ألف وخمسمية ريال بنزين") },
                minLines = 2
            )
            Button(
                onClick = { onCommand(command.trim()) },
                enabled = enabled && command.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("مراجعة العبارة") }
            Text("لا تُحفظ أي عملية إلا بعد مراجعتك وتأكيدك.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
