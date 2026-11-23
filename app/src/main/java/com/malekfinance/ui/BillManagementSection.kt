package com.malekfinance.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.malekfinance.FinanceBill
import com.malekfinance.FinanceBillPayment
import com.malekfinance.FinanceBillPolicy
import com.malekfinance.FinanceCategory
import com.malekfinance.FinanceDb
import com.malekfinance.FinanceLedger
import com.malekfinance.FinanceTransaction
import com.malekfinance.FinanceWallet
import com.malekfinance.notifications.BillReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@Composable
internal fun BillManagementSection(
    db: FinanceDb,
    wallets: List<FinanceWallet>,
    categories: List<FinanceCategory>,
    bills: List<FinanceBill>,
    payments: List<FinanceBillPayment>
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotificationPermission = granted
    }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(FinanceBillPolicy.formatLocalDate(System.currentTimeMillis())) }
    var categoryName by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("ONCE") }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var status by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val busyBills = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    LaunchedEffect(bills, hasNotificationPermission) {
        bills.forEach { bill ->
            runCatching {
                val occurrenceKey = FinanceBillPolicy.occurrenceKey(bill.nextDueAt)
                if (bill.isActive && hasNotificationPermission && bill.lastReminderOccurrenceKey != occurrenceKey) {
                    BillReminderScheduler.schedule(context, bill.id, bill.nextDueAt)
                } else {
                    BillReminderScheduler.cancel(context, bill.id)
                }
            }.onFailure {
                status = "تعذر تحديث أحد تذكيرات الفواتير؛ ستبقى مواعيدها ظاهرة داخل التطبيق."
            }
        }
    }
    val expenseCategories = categories.filter { it.isActive && (it.kind == "EXPENSE" || it.kind == "BOTH") }
    LaunchedEffect(wallets) {
        if (walletId == null || wallets.none { it.id == walletId }) walletId = wallets.firstOrNull()?.id
    }
    LaunchedEffect(expenseCategories) {
        if (expenseCategories.none { it.name == categoryName }) {
            categoryName = expenseCategories.firstOrNull { it.name == "فواتير" }?.name
                ?: expenseCategories.firstOrNull()?.name.orEmpty()
        }
    }
    val selectedWallet = wallets.firstOrNull { it.id == walletId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الفواتير ومواعيد الاستحقاق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "أضف فواتير الكهرباء والإنترنت والإيجار وغيرها. يظهر التذكير داخل التطبيق، ولا يُسجل أي مصروف أو يُخصم أي مبلغ إلا بعد تأكيد الدفع.",
            color = FinanceColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission) {
            Text(
                "إذن الإشعارات غير مفعّل؛ ستبقى مواعيد الفواتير ظاهرة هنا، لكن لن تصلك تنبيهات خارج التطبيق.",
                color = FinanceColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("تفعيل إشعارات الفواتير", color = FinanceColors.PrimaryLight)
            }
        }
        if (wallets.isEmpty() || expenseCategories.isEmpty()) {
            Text(
                if (wallets.isEmpty()) "أنشئ محفظة أولاً لتحديد مصدر دفع الفاتورة."
                else "أضف تصنيف مصروف نشطاً أولاً.",
                color = FinanceColors.TextSecondary
            )
        } else {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("اسم الفاتورة (مثل الكهرباء أو الإنترنت)") },
                singleLine = true
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("المبلغ (" + (selectedWallet?.currencyCode ?: "—") + ")") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("تاريخ الاستحقاق (YYYY-MM-DD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                supportingText = { Text("أدخل التاريخ بصيغة 2026-12-01") },
                singleLine = true
            )
            Text("نوع الاستحقاق", color = FinanceColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = frequency == "ONCE", onClick = { frequency = "ONCE" }, label = { Text("مرة واحدة") })
                FilterChip(selected = frequency == "MONTHLY", onClick = { frequency = "MONTHLY" }, label = { Text("شهري") })
                FilterChip(selected = frequency == "YEARLY", onClick = { frequency = "YEARLY" }, label = { Text("سنوي") })
            }
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
            Text("محفظة الدفع", color = FinanceColors.TextSecondary)
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
                    val amountMinor = parseBillAmount(amount)
                    val dueAt = FinanceBillPolicy.parseLocalDate(dueDate)
                    when {
                        title.isBlank() -> status = "اكتب اسم الفاتورة."
                        title.trim().length > 80 -> status = "اسم الفاتورة طويل؛ الحد الأقصى 80 حرفاً."
                        amountMinor == null || amountMinor <= 0L -> status = "أدخل مبلغاً صحيحاً أكبر من صفر وبحد أقصى منزلتين عشريتين."
                        dueAt == null -> status = "تاريخ الاستحقاق غير صحيح؛ استخدم YYYY-MM-DD."
                        selectedWallet == null -> status = "اختر محفظة الدفع."
                        expenseCategories.none { it.name == categoryName } -> status = "اختر تصنيف مصروف نشطاً."
                        else -> {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch {
                            if (creating) return@launch
                            creating = true
                            try {
                                val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
                                db.billDao().insert(
                                    FinanceBill(
                                        title = title.trim(),
                                        categoryName = categoryName,
                                        amountMinor = amountMinor,
                                        currencyCode = selectedWallet.currencyCode,
                                        walletId = selectedWallet.id,
                                        frequency = frequency,
                                        anchorDay = calendar.get(Calendar.DAY_OF_MONTH),
                                        anchorMonth = calendar.get(Calendar.MONTH),
                                        nextDueAt = dueAt
                                    )
                                )
                                title = ""
                                amount = ""
                                status = if (hasNotificationPermission) {
                                    "تمت إضافة الفاتورة. موعدها القادم: " + FinanceBillPolicy.formatLocalDate(dueAt) + "، وسيُجدول تذكيرها."
                                } else {
                                    "تمت إضافة الفاتورة. فعّل إذن الإشعارات لتلقي تذكير قبل الاستحقاق؛ الموعد سيظل ظاهراً داخل التطبيق."
                                }
                            } catch (_: Exception) {
                                status = "تعذرت إضافة الفاتورة؛ تحقق من البيانات وحاول مجدداً."
                            } finally {
                                creating = false
                            }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !creating && wallets.isNotEmpty() && expenseCategories.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
            ) { Text(if (creating) "جارٍ الإضافة..." else "إضافة فاتورة") }
        }

        HorizontalDivider(color = FinanceColors.Divider)
        if (bills.isEmpty()) {
            Text("لا توجد فواتير مسجلة بعد.", color = FinanceColors.TextSecondary)
        } else {
            bills.forEach { bill ->
                val due = FinanceBillPolicy.isDue(bill.nextDueAt, now)
                val wallet = wallets.firstOrNull { it.id == bill.walletId }
                val billPayments = payments.filter { it.billId == bill.id }
                val oneTimeAlreadyPaid = bill.frequency == "ONCE" && billPayments.any {
                    it.occurrenceKey == FinanceBillPolicy.occurrenceKey(bill.nextDueAt)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = FinanceColors.SurfaceElevated)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(bill.title, fontWeight = FontWeight.Bold)
                                Text(
                                    bill.categoryName + " • " + when (bill.frequency) {
                                        "MONTHLY" -> "شهري"
                                        "YEARLY" -> "سنوي"
                                        else -> "مرة واحدة"
                                    },
                                    color = FinanceColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                formatBillMoney(bill.amountMinor, bill.currencyCode),
                                color = FinanceColors.Expense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "الاستحقاق: " + FinanceBillPolicy.formatLocalDate(bill.nextDueAt) +
                                (wallet?.let { " • " + it.name } ?: " • المحفظة غير موجودة"),
                            color = FinanceColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            when {
                                oneTimeAlreadyPaid -> "مدفوعة"
                                !bill.isActive -> "متوقفة"
                                due -> "مستحقة — بانتظار تأكيد الدفع"
                                else -> "قادمة"
                            },
                            color = if (bill.isActive && due) FinanceColors.PrimaryLight else FinanceColors.TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (billPayments.isNotEmpty()) {
                            Text("دفعات مؤكدة: " + billPayments.size, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (bill.isActive && due) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (busyBills[bill.id] == true) return@launch
                                            busyBills[bill.id] = true
                                            try {
                                                db.withTransaction {
                                                    val current = db.billDao().findById(bill.id) ?: error("Bill not found")
                                                    check(current.isActive)
                                                    check(FinanceBillPolicy.isDue(current.nextDueAt, System.currentTimeMillis()))
                                                    val occurrence = FinanceBillPolicy.occurrenceKey(current.nextDueAt)
                                                    check(!db.billPaymentDao().exists(current.id, occurrence))
                                                    val currentWallet = db.walletDao().findById(current.walletId) ?: error("Wallet not found")
                                                    check(currentWallet.currencyCode == current.currencyCode)
                                                    val transactions = db.dao().forWallet(current.walletId)
                                                    val transfers = db.transferDao().forWallet(current.walletId)
                                                    check(FinanceLedger.hasSufficientFunds(currentWallet, current.amountMinor, transactions, transfers))
                                                    val paidAt = System.currentTimeMillis()
                                                    val transactionId = db.dao().insert(
                                                        FinanceTransaction(
                                                            amountMinor = current.amountMinor,
                                                            kind = "EXPENSE",
                                                            note = "دفع فاتورة: " + current.title,
                                                            category = current.categoryName,
                                                            createdAt = paidAt,
                                                            walletId = current.walletId,
                                                            currencyCode = current.currencyCode,
                                                            billId = current.id
                                                        )
                                                    )
                                                    db.billPaymentDao().insert(
                                                        FinanceBillPayment(
                                                            billId = current.id,
                                                            occurrenceKey = occurrence,
                                                            transactionId = transactionId,
                                                            amountMinor = current.amountMinor,
                                                            paidAt = paidAt
                                                        )
                                                    )
                                                    if (current.frequency == "ONCE") {
                                                        db.billDao().update(current.copy(isActive = false))
                                                    } else {
                                                        val next = FinanceBillPolicy.nextDueAt(
                                                            current.nextDueAt,
                                                            current.frequency,
                                                            current.anchorDay,
                                                            current.anchorMonth
                                                        ) ?: error("Unsupported bill frequency")
                                                        db.billDao().update(current.copy(nextDueAt = next))
                                                    }
                                                }
                                                status = "تم تأكيد دفع الفاتورة وتسجيل المصروف."
                                            } catch (_: Exception) {
                                                status = "لم يتم تسجيل الدفع. تحقق من الرصيد أو الموعد وحاول مجدداً."
                                            } finally {
                                                busyBills.remove(bill.id)
                                            }
                                        }
                                    },
                                    enabled = busyBills[bill.id] != true,
                                    colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                                ) { Text(if (busyBills[bill.id] == true) "جارٍ التسجيل..." else "تأكيد دفع الفاتورة") }
                            }
                            if (!oneTimeAlreadyPaid) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                val current = db.billDao().findById(bill.id) ?: error("Bill not found")
                                                val updated = current.copy(isActive = !current.isActive)
                                                db.billDao().update(updated)
                                                updated
                                            }.onSuccess { updated ->
                                                status = if (updated.isActive) "تمت إعادة تفعيل الفاتورة." else "تم إيقاف الفاتورة."
                                            }.onFailure { status = "تعذر تغيير حالة الفاتورة." }
                                        }
                                    }
                                ) { Text(if (bill.isActive) "إيقاف" else "تفعيل", color = FinanceColors.PrimaryLight) }
                            }
                        }
                    }
                }
            }
        }
        status?.let { Text(it, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun parseBillAmount(raw: String): Long? = runCatching {
    val normalized = raw.trim().map { ch ->
        when (ch) {
            in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
            in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
            '٫' -> '.'
            else -> ch
        }
    }.joinToString("")
    BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
}.getOrNull()

private fun formatBillMoney(minor: Long, currencyCode: String): String {
    val value = NumberFormat.getNumberInstance(Locale("ar")).format(BigDecimal.valueOf(minor, 2))
    val suffix = when (currencyCode) {
        "YER" -> "ر.ي"
        "USD" -> "$"
        "SAR" -> "ر.س"
        "AED" -> "د.إ"
        else -> currencyCode
    }
    return "$value $suffix"
}
