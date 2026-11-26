package com.malekfinance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.malekfinance.*

/** Review-only UI: database writes live in the explicit confirmation callbacks. */
@Composable
internal fun VoiceBillDraftCard(
    bills: List<FinanceBill>, wallets: List<FinanceWallet>, now: Long,
    selectedId: Long?, onSelect: (Long) -> Unit, busy: Boolean, status: String,
    onConfirm: () -> Unit, onCancel: () -> Unit
) {
    val eligible = bills.filter { it.isActive && FinanceBillPolicy.isDue(it.nextDueAt, now) }
    val selected = eligible.firstOrNull { it.id == selectedId }
    Card(Modifier.fillMaxWidth().testTag("voice-bill-draft"), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("مراجعة دفع الفاتورة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("لن يتم الخصم قبل التأكيد. اختر فاتورة مستحقة وراجع محفظتها ومبلغها.", color = FinanceColors.TextSecondary)
            LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(eligible, key = { it.id }) { bill ->
                    FilterChip(selected = selectedId == bill.id, onClick = { onSelect(bill.id) }, enabled = !busy,
                        label = { Text(bill.title + " · " + FinanceMoney.format(bill.amountMinor, bill.currencyCode)) })
                }
            }
            if (eligible.isEmpty()) Text("لا توجد فواتير نشطة مستحقة. أضف فاتورة من قسم الفواتير أولاً.")
            selected?.let { bill ->
                Text("المحفظة: " + (wallets.firstOrNull { it.id == bill.walletId }?.name ?: "غير موجودة"))
                Text("المبلغ: " + FinanceMoney.format(bill.amountMinor, bill.currencyCode))
                Text("موعد الاستحقاق: " + FinanceBillPolicy.formatLocalDate(bill.nextDueAt))
            }
            Text(status, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onConfirm, enabled = !busy && selected != null,
                modifier = Modifier.fillMaxWidth().testTag("confirm-voice-bill")) {
                Text(if (busy) "جارٍ تسجيل الدفع..." else "تأكيد دفع الفاتورة")
            }
            TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("cancel-voice-bill")) {
                Text("إلغاء مسودة الفاتورة")
            }
        }
    }
}

@Composable
internal fun VoiceSavingsDraftCard(
    goals: List<FinanceSavingsGoal>, entries: List<FinanceSavingsEntry>, wallets: List<FinanceWallet>,
    selectedId: Long?, onSelect: (Long) -> Unit, withdrawal: Boolean,
    amount: String, onAmountChange: (String) -> Unit, busy: Boolean, status: String,
    onConfirm: () -> Unit, onCancel: () -> Unit
) {
    val selected = goals.firstOrNull { it.id == selectedId }
    Card(Modifier.fillMaxWidth().testTag("voice-savings-draft"), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (withdrawal) "مراجعة السحب من الادخار" else "مراجعة مساهمة الادخار",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (withdrawal) "يعاد المبلغ إلى المحفظة دون تسجيل دخل." else "يحجز المبلغ من المحفظة دون تسجيل مصروف.",
                color = FinanceColors.TextSecondary)
            LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(goals, key = { it.id }) { goal ->
                    FilterChip(selected = selectedId == goal.id, onClick = { onSelect(goal.id) }, enabled = !busy,
                        label = { Text(goal.name + " · " + goal.currencyCode) })
                }
            }
            if (goals.isEmpty()) Text("لا توجد أهداف ادخار. أنشئ هدفاً مرتبطاً بمحفظة أولاً.")
            selected?.let { goal ->
                Text("المحفظة: " + (wallets.firstOrNull { it.id == goal.walletId }?.name ?: "غير موجودة"))
                val balance = FinanceSavingsPolicy.balance(entries.filter { it.goalId == goal.id })
                Text("المدخر حالياً: " + FinanceMoney.format(balance, goal.currencyCode))
                Text("المستهدف: " + FinanceMoney.format(goal.targetMinor, goal.currencyCode))
            }
            OutlinedTextField(value = amount, onValueChange = onAmountChange, enabled = !busy, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text("مبلغ الادخار (" + (selected?.currencyCode ?: "اختر هدفاً") + ")") },
                modifier = Modifier.fillMaxWidth().testTag("voice-savings-amount"))
            Text(status, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onConfirm, enabled = !busy && selected != null,
                modifier = Modifier.fillMaxWidth().testTag("confirm-voice-savings")) {
                Text(if (busy) "جارٍ التسجيل..." else if (withdrawal) "تأكيد السحب من الادخار" else "تأكيد مساهمة الادخار")
            }
            TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("cancel-voice-savings")) {
                Text("إلغاء مسودة الادخار")
            }
        }
    }
}
