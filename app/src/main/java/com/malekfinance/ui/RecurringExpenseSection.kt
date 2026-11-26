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
import com.malekfinance.FinanceCategory
import com.malekfinance.FinanceDb
import com.malekfinance.FinanceLedger
import com.malekfinance.FinanceRecurringExpenseEntry
import com.malekfinance.FinanceRecurringExpensePolicy
import com.malekfinance.FinanceRecurringExpenseRule
import com.malekfinance.FinanceTransaction
import com.malekfinance.FinanceWallet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun RecurringExpenseSection(
    db: FinanceDb,
    wallets: List<FinanceWallet>,
    categories: List<FinanceCategory>,
    rules: List<FinanceRecurringExpenseRule>,
    entries: List<FinanceRecurringExpenseEntry>
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var categoryName by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var monthlyDay by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val busyRules = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    LaunchedEffect(wallets) {
        if (walletId == null || wallets.none { it.id == walletId }) walletId = wallets.firstOrNull()?.id
    }
    LaunchedEffect(categories) {
        if (categories.none { it.name == categoryName }) categoryName = categories.firstOrNull()?.name.orEmpty()
    }
    val selectedWallet = wallets.firstOrNull { it.id == walletId }
    val expenseCategories = categories.filter { it.kind == "EXPENSE" || it.kind == "BOTH" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("المصروفات المتكررة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "سجّل الإيجار والفواتير والاشتراكات الدورية. لن يُخصم أي مبلغ تلقائياً؛ يجب تأكيد الدفع عند حلول الموعد.",
            color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall
        )
        if (wallets.isEmpty() || expenseCategories.isEmpty()) {
            Text(
                if (wallets.isEmpty()) "أنشئ محفظة أولاً لتحديد مصدر الدفع."
                else "أضف تصنيف مصروف نشطاً أولاً.",
                color = FinanceColors.TextSecondary
            )
        } else {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم المصروف (مثل الإيجار أو الإنترنت)") }, singleLine = true
            )
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("المبلغ (" + (selectedWallet?.currencyCode ?: "—") + ")") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )
            Text("التصنيف", color = FinanceColors.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(expenseCategories, key = { it.id }) { category ->
                    FilterChip(
                        selected = categoryName == category.name,
                        onClick = { categoryName = category.name },
                        label = { Text(category.name) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = frequency == "MONTHLY", onClick = { frequency = "MONTHLY" }, label = { Text("شهري") })
                FilterChip(selected = frequency == "WEEKLY", onClick = { frequency = "WEEKLY" }, label = { Text("أسبوعي") })
            }
            if (frequency == "MONTHLY") {
                OutlinedTextField(
                    value = monthlyDay,
                    onValueChange = { monthlyDay = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("يوم الدفع من الشهر (1 إلى 31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            Text("الدفع من المحفظة", color = FinanceColors.TextSecondary)
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
                    val amountMinor = parseRecurringExpenseAmount(amount)
                    val requestedDay = parseRecurringExpenseDay(monthlyDay)
                    when {
                        title.isBlank() -> status = "اكتب اسم المصروف الدوري."
                        title.trim().length > 60 -> status = "الاسم طويل؛ الحد الأقصى 60 حرفاً."
                        amountMinor == null || amountMinor <= 0L -> status = "أدخل مبلغاً صحيحاً أكبر من صفر."
                        selectedWallet == null -> status = "اختر محفظة للدفع."
                        expenseCategories.none { it.name == categoryName } -> status = "اختر تصنيف مصروف نشطاً."
                        frequency == "MONTHLY" && (requestedDay == null || requestedDay !in 1..31) -> status = "حدد يوم الدفع بين 1 و31."
                        else -> scope.launch {
                            if (creating) return@launch
                            creating = true
                            try {
                                val createdAt = System.currentTimeMillis()
                                val anchorDay = if (frequency == "MONTHLY") requestedDay!! else Calendar.getInstance().apply { timeInMillis = createdAt }.get(Calendar.DAY_OF_MONTH)
                                val dueAt = FinanceRecurringExpensePolicy.firstDueAt(createdAt, frequency, anchorDay) ?: error("Unsupported frequency")
                                db.recurringExpenseRuleDao().insert(
                                    FinanceRecurringExpenseRule(
                                        title = title.trim(), categoryName = categoryName,
                                        amountMinor = amountMinor, currencyCode = selectedWallet.currencyCode,
                                        walletId = selectedWallet.id, frequency = frequency,
                                        anchorDay = anchorDay, nextDueAt = dueAt
                                    )
                                )
                                title = ""
                                amount = ""
                                status = "تم إنشاء المصروف الدوري. أول موعد: " + formatRecurringExpenseDate(dueAt) + "؛ لن يُسجل إلا بعد تأكيد الدفع."
                            } catch (_: Exception) {
                                status = "تعذر إنشاء المصروف الدوري."
                            } finally { creating = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(), enabled = !creating && wallets.isNotEmpty() && expenseCategories.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
            ) { Text(if (creating) "جارٍ الإنشاء..." else "إضافة مصروف متكرر") }
        }

        HorizontalDivider(color = FinanceColors.Divider)
        if (rules.isEmpty()) {
            Text("لا توجد مصروفات متكررة مسجلة بعد.", color = FinanceColors.TextSecondary)
        } else {
            rules.forEach { rule ->
                val due = FinanceRecurringExpensePolicy.isDue(rule.nextDueAt, now)
                val wallet = wallets.firstOrNull { it.id == rule.walletId }
                val ruleEntries = entries.filter { it.ruleId == rule.id }
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = FinanceColors.SurfaceElevated)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(rule.title, fontWeight = FontWeight.Bold)
                                Text(rule.categoryName + " • " + if (rule.frequency == "MONTHLY") "شهري" else "أسبوعي",
                                    color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(formatRecurringExpenseMoney(rule.amountMinor, rule.currencyCode),
                                color = FinanceColors.Expense, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "الموعد التالي: " + formatRecurringExpenseDate(rule.nextDueAt) +
                                (wallet?.let { " • " + it.name } ?: " • المحفظة غير موجودة"),
                            color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (!rule.isActive) "متوقف" else if (due) "مستحق — بانتظار تأكيد الدفع" else "لم يحن موعد الدفع",
                            color = if (rule.isActive && due) FinanceColors.PrimaryLight else FinanceColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (ruleEntries.isNotEmpty()) {
                            Text("دفعات مسجلة: " + ruleEntries.size, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
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
                                                    val current = db.recurringExpenseRuleDao().findById(rule.id) ?: error("Rule not found")
                                                    check(current.isActive)
                                                    check(FinanceRecurringExpensePolicy.isDue(current.nextDueAt, System.currentTimeMillis()))
                                                    val occurrence = FinanceRecurringExpensePolicy.occurrenceKey(current.nextDueAt)
                                                    check(!db.recurringExpenseEntryDao().exists(current.id, occurrence))
                                                    val currentWallet = db.walletDao().findById(current.walletId) ?: error("Wallet not found")
                                                    check(currentWallet.currencyCode == current.currencyCode)
                                                    val latestTransactions = db.dao().forWallet(current.walletId)
                                                    val latestTransfers = db.transferDao().forWallet(current.walletId)
                                                    check(FinanceLedger.hasSufficientFunds(currentWallet, current.amountMinor, latestTransactions, latestTransfers))
                                                    val transactionId = db.dao().insert(
                                                        FinanceTransaction(
                                                            amountMinor = current.amountMinor, kind = "EXPENSE",
                                                            note = "مصروف دوري: " + current.title,
                                                            category = current.categoryName,
                                                            walletId = currentWallet.id, currencyCode = currentWallet.currencyCode,
                                                            recurringExpenseRuleId = current.id
                                                        )
                                                    )
                                                    db.recurringExpenseEntryDao().insert(
                                                        FinanceRecurringExpenseEntry(
                                                            ruleId = current.id, occurrenceKey = occurrence,
                                                            transactionId = transactionId, amountMinor = current.amountMinor
                                                        )
                                                    )
                                                    val nextDue = FinanceRecurringExpensePolicy.nextDueAt(current.nextDueAt, current.frequency, current.anchorDay)
                                                        ?: error("Unsupported frequency")
                                                    db.recurringExpenseRuleDao().update(current.copy(nextDueAt = nextDue))
                                                }
                                                status = "تم تأكيد الدفع وتسجيل المصروف وتحديث موعده القادم."
                                            } catch (_: Exception) {
                                                status = "لم يُسجل المصروف. تحقق من الرصيد أو موعد الاستحقاق أو احتمال تسجيله سابقاً."
                                            } finally { busyRules[rule.id] = false }
                                        }
                                    },
                                    enabled = rule.isActive && due && wallet != null && busyRules[rule.id] != true,
                                    colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                                ) { Text(if (busyRules[rule.id] == true) "جارٍ التسجيل..." else "تأكيد الدفع وتسجيل المصروف") }
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    if (busyRules[rule.id] == true) return@launch
                                    busyRules[rule.id] = true
                                    try {
                                        runCatching { FinanceScheduleOperations.toggleExpense(db, rule.id) }
                                            .onSuccess { active -> status = if (active) "تم تفعيل المصروف الدوري." else "تم إيقاف المصروف الدوري." }
                                            .onFailure { status = "تعذر تغيير حالة المصروف الدوري." }
                                    } finally { busyRules.remove(rule.id) }
                                }
                            }, enabled = busyRules[rule.id] != true) { Text(if (rule.isActive) "إيقاف" else "تفعيل", color = FinanceColors.PrimaryLight) }
                        }
                    }
                }
            }
        }
        status?.let { Text(it, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun parseRecurringExpenseAmount(raw: String): Long? {
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    val normalized = buildString {
        raw.forEach { ch ->
            val a = arabic.indexOf(ch); val p = persian.indexOf(ch)
            append(if (a >= 0) a else if (p >= 0) p else ch)
        }
    }.replace('٫', '.').replace(',', '.').trim()
    return runCatching { normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() }.getOrNull()
}

private fun parseRecurringExpenseDay(raw: String): Int? {
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    val normalized = buildString {
        raw.forEach { ch ->
            val a = arabic.indexOf(ch); val p = persian.indexOf(ch)
            append(if (a >= 0) a else if (p >= 0) p else ch)
        }
    }
    return normalized.toIntOrNull()
}

private fun formatRecurringExpenseDate(time: Long): String =
    java.text.SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date(time))

private fun formatRecurringExpenseMoney(minor: Long, currencyCode: String): String {
    val amount = BigDecimal.valueOf(minor, 2)
    val formatted = NumberFormat.getNumberInstance(Locale("ar")).format(amount)
    val suffix = when (currencyCode) {
        "YER" -> "ر.ي"; "USD" -> "$"; "SAR" -> "ر.س"; "AED" -> "د.إ"; else -> currencyCode
    }
    return "$formatted $suffix"
}
