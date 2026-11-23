package com.malekfinance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.malekfinance.FinanceDb
import com.malekfinance.FinanceRecurringIncomeEntry
import com.malekfinance.FinanceRecurringIncomePolicy
import com.malekfinance.FinanceRecurringIncomeRule
import com.malekfinance.FinanceTransaction
import com.malekfinance.FinanceWallet
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun RecurringIncomeSection(
    db: FinanceDb,
    wallets: List<FinanceWallet>,
    rules: List<FinanceRecurringIncomeRule>,
    entries: List<FinanceRecurringIncomeEntry>
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var incomeType by remember { mutableStateOf("SALARY") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var monthlyDay by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val busyRules = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(rules) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    LaunchedEffect(wallets) {
        if (walletId == null || wallets.none { it.id == walletId }) walletId = wallets.firstOrNull()?.id
    }
    val selectedWallet = wallets.firstOrNull { it.id == walletId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الرواتب والدخل المتكرر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "أنشئ استحقاقاً دورياً. لن يُضاف إلى دخلك تلقائياً؛ أنت من يراجع الاستحقاق ويضغط تسجيل عند وصوله، لمنع تسجيل أموال لم تستلمها.",
            color = FinanceColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        if (wallets.isEmpty()) {
            Text("أنشئ محفظة أولاً لتحديد مكان استلام الراتب أو الدخل.", color = FinanceColors.TextSecondary)
        } else {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم الراتب أو مصدر الدخل") }, singleLine = true
            )
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("المبلغ (" + (selectedWallet?.currencyCode ?: "—") + ")") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = incomeType == "SALARY", onClick = { incomeType = "SALARY" }, label = { Text("راتب") })
                FilterChip(selected = incomeType == "RECURRING", onClick = { incomeType = "RECURRING" }, label = { Text("دخل متكرر") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = frequency == "MONTHLY", onClick = { frequency = "MONTHLY" }, label = { Text("شهري") })
                FilterChip(selected = frequency == "WEEKLY", onClick = { frequency = "WEEKLY" }, label = { Text("أسبوعي") })
            }
            if (frequency == "MONTHLY") {
                OutlinedTextField(
                    value = monthlyDay, onValueChange = { monthlyDay = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("يوم الاستحقاق من الشهر (1 إلى 31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            Text("إيداع الدخل في المحفظة", color = FinanceColors.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(wallets, key = { it.id }) { wallet ->
                    FilterChip(
                        selected = selectedWallet?.id == wallet.id,
                        onClick = { walletId = wallet.id },
                        label = { Text(wallet.name + " • " + wallet.currencyCode) }
                    )
                }
            }
            Button(
                onClick = {
                    val amountMinor = parseRecurringIncomeAmount(amount)
                    val requestedDay = parseRecurringDay(monthlyDay)
                    when {
                        title.isBlank() -> status = "اكتب اسم الراتب أو مصدر الدخل."
                        title.trim().length > 60 -> status = "الاسم طويل؛ الحد الأقصى 60 حرفاً."
                        amountMinor == null || amountMinor <= 0L -> status = "أدخل مبلغاً صحيحاً أكبر من صفر."
                        selectedWallet == null -> status = "اختر محفظة لاستلام الدخل."
                        frequency == "MONTHLY" && (requestedDay == null || requestedDay !in 1..31) -> status = "حدد يوم الاستحقاق بين 1 و31."
                        else -> scope.launch {
                            if (creating) return@launch
                            creating = true
                            try {
                                val now = System.currentTimeMillis()
                                val anchorDay = if (frequency == "MONTHLY") requestedDay!! else Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_MONTH)
                                val firstDue = FinanceRecurringIncomePolicy.firstDueAt(now, frequency, anchorDay) ?: error("Unsupported recurrence")
                                db.recurringIncomeRuleDao().insert(
                                    FinanceRecurringIncomeRule(
                                        title = title.trim(),
                                        incomeType = incomeType,
                                        amountMinor = amountMinor,
                                        currencyCode = selectedWallet.currencyCode,
                                        walletId = selectedWallet.id,
                                        frequency = frequency,
                                        anchorDay = anchorDay,
                                        nextDueAt = firstDue
                                    )
                                )
                                title = ""
                                amount = ""
                                status = "تم إنشاء الاستحقاق. أول موعد: " + formatRecurringDueDate(firstDue) + "، ولن يسجل إلا بعد تأكيدك."
                            } catch (_: Exception) {
                                status = "تعذر إنشاء الاستحقاق؛ لم يتم تسجيل أي دخل."
                            } finally { creating = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !creating && wallets.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
            ) { Text(if (creating) "جارٍ إنشاء الاستحقاق..." else "إضافة راتب أو دخل متكرر") }
        }

        HorizontalDivider(color = FinanceColors.Divider)
        if (rules.isEmpty()) {
            Text("لا توجد رواتب أو مصادر دخل متكررة مسجلة بعد.", color = FinanceColors.TextSecondary)
        } else {
            rules.forEach { rule ->
                val due = FinanceRecurringIncomePolicy.isDue(rule.nextDueAt, now)
                val wallet = wallets.firstOrNull { it.id == rule.walletId }
                val ruleEntries = entries.filter { it.ruleId == rule.id }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = FinanceColors.SurfaceElevated)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(rule.title, fontWeight = FontWeight.Bold)
                                Text(
                                    (if (rule.incomeType == "SALARY") "راتب" else "دخل متكرر") +
                                        " • " + (if (rule.frequency == "MONTHLY") "شهري" else "أسبوعي"),
                                    color = FinanceColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                formatRecurringIncomeMoney(rule.amountMinor, rule.currencyCode),
                                color = FinanceColors.Income,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "الموعد التالي: " + formatRecurringDueDate(rule.nextDueAt) +
                                (wallet?.let { " • " + it.name } ?: " • المحفظة غير موجودة"),
                            color = FinanceColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (!rule.isActive) "متوقف" else if (due) "مستحق — بانتظار تأكيد الاستلام" else "لم يحن موعد الاستحقاق",
                            color = if (rule.isActive && due) FinanceColors.PrimaryLight else FinanceColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (ruleEntries.isNotEmpty()) {
                            Text("عمليات مسجلة: " + ruleEntries.size, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (rule.isActive) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (busyRules[rule.id] == true) return@launch
                                            busyRules[rule.id] = true
                                            try {
                                                db.withTransaction {
                                                    val current = db.recurringIncomeRuleDao().findById(rule.id) ?: error("Rule not found")
                                                    check(current.isActive)
                                                    val now = System.currentTimeMillis()
                                                    check(FinanceRecurringIncomePolicy.isDue(current.nextDueAt, now))
                                                    val occurrence = FinanceRecurringIncomePolicy.occurrenceKey(current.nextDueAt)
                                                    check(db.recurringIncomeEntryDao().forRule(current.id).none { it.occurrenceKey == occurrence })
                                                    val currentWallet = db.walletDao().findById(current.walletId) ?: error("Wallet not found")
                                                    check(currentWallet.currencyCode == current.currencyCode)
                                                    val transactionId = db.dao().insert(
                                                        FinanceTransaction(
                                                            amountMinor = current.amountMinor,
                                                            kind = "INCOME",
                                                            note = (if (current.incomeType == "SALARY") "راتب: " else "دخل متكرر: ") + current.title,
                                                            category = if (current.incomeType == "SALARY") "راتب" else "دخل متكرر",
                                                            walletId = currentWallet.id,
                                                            currencyCode = currentWallet.currencyCode,
                                                            recurringIncomeRuleId = current.id
                                                        )
                                                    )
                                                    db.recurringIncomeEntryDao().insert(
                                                        FinanceRecurringIncomeEntry(
                                                            ruleId = current.id,
                                                            occurrenceKey = occurrence,
                                                            transactionId = transactionId,
                                                            amountMinor = current.amountMinor
                                                        )
                                                    )
                                                    val nextDue = FinanceRecurringIncomePolicy.nextDueAt(current.nextDueAt, current.frequency, current.anchorDay)
                                                        ?: error("Unsupported recurrence")
                                                    db.recurringIncomeRuleDao().update(current.copy(nextDueAt = nextDue))
                                                }
                                                status = "تم تسجيل الدخل وربطه بالمحفظة، وانتقل الاستحقاق إلى موعده التالي."
                                            } catch (_: Exception) {
                                                status = "لم يُسجل الدخل. قد يكون الموعد غير مستحق أو سبق تسجيله."
                                            } finally { busyRules[rule.id] = false }
                                        }
                                    },
                                    enabled = rule.isActive && due && wallet != null && busyRules[rule.id] != true,
                                    colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                                ) { Text(if (busyRules[rule.id] == true) "جارٍ التسجيل..." else "تأكيد استلام وتسجيل") }
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            val latest = db.recurringIncomeRuleDao().findById(rule.id) ?: error("Rule not found")
                                            db.recurringIncomeRuleDao().update(latest.copy(isActive = !latest.isActive))
                                        }.onSuccess { status = if (rule.isActive) "تم إيقاف الاستحقاق الدوري." else "تم تفعيل الاستحقاق الدوري." }
                                            .onFailure { status = "تعذر تغيير حالة الاستحقاق." }
                                    }
                                }
                            ) { Text(if (rule.isActive) "إيقاف" else "تفعيل", color = FinanceColors.PrimaryLight) }
                        }
                    }
                }
            }
        }
        status?.let { Text(it, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun parseRecurringIncomeAmount(raw: String): Long? {
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    val normalized = buildString {
        raw.forEach { ch ->
            val a = arabic.indexOf(ch)
            val p = persian.indexOf(ch)
            append(if (a >= 0) a else if (p >= 0) p else ch)
        }
    }.replace('٫', '.').replace(',', '.').trim()
    return runCatching { normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() }.getOrNull()
}

private fun parseRecurringDay(raw: String): Int? {
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    val normalized = buildString {
        raw.forEach { ch ->
            val a = arabic.indexOf(ch)
            val p = persian.indexOf(ch)
            append(if (a >= 0) a else if (p >= 0) p else ch)
        }
    }
    return normalized.toIntOrNull()
}

private fun formatRecurringDueDate(timestamp: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date(timestamp))

private fun formatRecurringIncomeMoney(minor: Long, currency: String): String {
    val value = BigDecimal(BigInteger.valueOf(minor), 2)
    val formatted = java.text.NumberFormat.getNumberInstance(Locale("ar")).format(value)
    val suffix = when (currency) {
        "YER" -> "ر.ي"
        "USD" -> "$"
        "SAR" -> "ر.س"
        "AED" -> "د.إ"
        else -> currency
    }
    return "$formatted $suffix"
}
