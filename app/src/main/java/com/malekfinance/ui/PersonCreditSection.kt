package com.malekfinance.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.malekfinance.*
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Timestamped purchases on credit and a reviewed, currency-isolated person statement. */
@Composable
internal fun PersonCreditSection(
    db: FinanceDb, wallets: List<FinanceWallet>, debts: List<FinanceDebt>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var person by rememberSaveable { mutableStateOf("") }
    var item by rememberSaveable { mutableStateOf("قات") }
    var amount by rememberSaveable { mutableStateOf("") }
    var walletId by rememberSaveable { mutableStateOf<Long?>(null) }
    var customAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedPerson by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCurrency by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAccount = selectedPerson?.let { personName -> selectedCurrency?.let { personName to it } }
    var repayment by rememberSaveable { mutableStateOf("") }
    var paymentWalletId by rememberSaveable { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statementText by remember { mutableStateOf<String?>(null) }
    var settlementRequest by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val accounts = debts.filter { it.debtType == "OWED_BY_ME" }.map {
        FinancePersonStatements.normalizePerson(it.personName) to it.currencyCode
    }.distinct().sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
    val account = selectedAccount?.takeIf { it in accounts } ?: accounts.firstOrNull()
    val wallet = wallets.firstOrNull { it.id == walletId } ?: wallets.firstOrNull()
    val paymentWallets = wallets.filter { it.currencyCode == account?.second }
    val paymentWallet = paymentWallets.firstOrNull { it.id == paymentWalletId } ?: paymentWallets.firstOrNull()
    fun minor(text: String): Long? = runCatching {
        text.map { c -> val digit = Character.digit(c, 10); if (digit >= 0) ('0'.code + digit).toChar() else c }
            .joinToString("").replace('٫', '.').replace(',', '.').trim().toBigDecimalOrNull()?.movePointRight(2)?.longValueExact()
    }.getOrNull()
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = statementText
        if (uri != null && text != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                    ?: error("Cannot write statement")
            } }.onSuccess { status = "حُفظ كشف الحساب النصي." }.onFailure { status = "تعذر حفظ الكشف؛ بيانات الحساب لم تتغير." }
        }
    }
    if (statementText != null) AlertDialog(
        onDismissRequest = { statementText = null },
        title = { Text("راجع الكشف قبل مشاركته") },
        text = { SelectionContainer { Text(statementText!!, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = {
            val text = statementText ?: return@TextButton
            if (text.length > 150_000) { status = "الكشف طويل؛ احفظه كملف نصي بدلاً من المشاركة المباشرة."; return@TextButton }
            runCatching {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "كشف حساب"); putExtra(Intent.EXTRA_TEXT, text)
                }, "اختر التطبيق والمستلم بنفسك"))
            }.onFailure { status = "تعذر فتح تطبيق مشاركة؛ استخدم حفظ الملف النصي." }
        }) { Text("اختيار تطبيق للمشاركة") } },
        dismissButton = { Row {
            TextButton(onClick = { saveText.launch("MK-Finance-person-statement.txt") }) { Text("حفظ TXT") }
            TextButton(onClick = { statementText = null }) { Text("إغلاق") }
        } }
    )
    if (settlementRequest != null) AlertDialog(
        onDismissRequest = { if (!busy) settlementRequest = null },
        title = { Text("تأكيد سداد الحساب") },
        text = { Text("سداد ${java.math.BigDecimal.valueOf(settlementRequest!!.first, 2)} ${account?.second} إلى ${account?.first} من المحفظة المختارة. يُوزَّع على أقدم الديون أولاً، بما فيها القروض إن وجدت. هذا تسجيل فقط وليس تحويل أموال.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val request = settlementRequest ?: return@TextButton
            val selected = account ?: return@TextButton
            busy = true
            scope.launch {
                runCatching { FinancePersonSettlementOperations.pay(db, selected.first, selected.second, request.second, request.first) }
                    .onSuccess { repayment = ""; settlementRequest = null; status = "سُجّل السداد. افتح كشف الحساب لرؤية الدفعة والمتبقي." }
                    .onFailure { status = if (it is InsufficientWalletFundsException) "رصيد محفظة السداد غير كافٍ." else "تعذر تسجيل السداد؛ راجع المبلغ والمتبقي والمحفظة."; settlementRequest = null }
                busy = false
            }
        }) { Text(if (busy) "جارٍ الحفظ…" else "تأكيد تسجيل السداد") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { settlementRequest = null }) { Text("إلغاء") } }
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("دفتر المشتريات بالدين وكشف الشخص", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("كل شراء سجل مستقل بمبلغه ويومه وتاريخه ووقته. لا تتغير المحفظة عند الشراء الآجل؛ تسجَّل حركة المصروف عند السداد.", color = FinanceColors.TextSecondary)
        OutlinedTextField(person, { person = it.take(120) }, label = { Text("اسم البائع كاملاً، مثل عبده مهدي") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(item, { item = it.take(160) }, label = { Text("المشتريات، مثل قات") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("مبلغ الشراء بالدين") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        Text("محفظة مرجعية للعملة فقط؛ لا يزيد رصيدها ولا ينقص عند الشراء.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        wallets.forEach { w -> FilterChip(selected = wallet?.id == w.id, onClick = { walletId = w.id }, enabled = !busy, label = { Text("${w.name} (${w.currencyCode})") }) }
        Text(if (customAt == null) "التاريخ والوقت: يُسجَّلان تلقائياً عند الحفظ (توقيت عدن)." else FinancePersonStatements.dateTime(customAt!!), color = FinanceColors.Accent)
        Row {
            TextButton(enabled = !busy, onClick = {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Aden")).apply { timeInMillis = customAt ?: System.currentTimeMillis() }
                DatePickerDialog(context, { _, year, month, day ->
                    cal.set(year, month, day)
                    TimePickerDialog(context, { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                        customAt = cal.timeInMillis
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }) { Text("تحديد يوم ووقت الشراء") }
            if (customAt != null) TextButton(onClick = { customAt = null }, enabled = !busy) { Text("الوقت الحالي") }
        }
        Button(enabled = !busy && wallet != null, modifier = Modifier.fillMaxWidth(), onClick = {
            val value = minor(amount)
            val selectedWallet = wallet
            val cleanPerson = FinancePersonStatements.normalizePerson(person)
            val whenAt = customAt ?: System.currentTimeMillis()
            if (value == null || value <= 0 || cleanPerson.isBlank() || item.isBlank() || selectedWallet == null || whenAt > System.currentTimeMillis()) {
                status = "أكمل الاسم والمشتريات والمبلغ والتاريخ؛ لا يُقبل وقت مستقبلي."
            } else {
                val description = "شراء آجل: ${item.trim()}"
                busy = true
                scope.launch {
                    runCatching { FinanceDebtOperations.create(db, cleanPerson, description, "OWED_BY_ME", value, selectedWallet.id, false, whenAt) }
                        .onSuccess { selectedPerson = cleanPerson; selectedCurrency = selectedWallet.currencyCode; amount = ""; customAt = null; status = "سُجّل الشراء بالدين مع تاريخه ووقته دون تغيير النقد." }
                        .onFailure { status = "تعذر حفظ الشراء؛ لم تُحفظ عملية جزئية." }
                    busy = false
                }
            }
        }) { Text("تأكيد تسجيل شراء بالدين") }
        HorizontalDivider()
        Text("كشف حساب شخص — يشمل المشتريات والديون والدفعات", fontWeight = FontWeight.Bold)
        accounts.forEach { key -> FilterChip(selected = account == key, onClick = { selectedPerson = key.first; selectedCurrency = key.second; repayment = ""; paymentWalletId = null }, enabled = !busy, label = { Text("${key.first} (${key.second})") }) }
        if (account == null) Text("لا يوجد حساب مستحق عليك بعد.", color = FinanceColors.TextSecondary)
        else {
            Text("تُجمع الأسماء المتطابقة فقط بعد إزالة المسافات الزائدة. لا تُخلط العملات أو حسابات «لي عنده».", style = MaterialTheme.typography.bodySmall, color = FinanceColors.TextSecondary)
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true
                val selected = account
                scope.launch {
                    runCatching { db.withTransaction { FinancePersonStatements.build(selected.first, selected.second, db.debtDao().observeAll().first(), db.debtPaymentDao().observeAll().first()).asText() } }
                        .onSuccess { statementText = it }
                        .onFailure { status = "تعذر إعداد كشف متسق؛ راجع تواريخ السجلات والدفعات." }
                    busy = false
                }
            }) { Text("معاينة كشف الأيام والتاريخ والوقت ومشاركته") }
            OutlinedTextField(repayment, { repayment = it }, label = { Text("مبلغ سداد لهذا الشخص (${account.second})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
            paymentWallets.forEach { w -> FilterChip(selected = paymentWallet?.id == w.id, onClick = { paymentWalletId = w.id }, enabled = !busy, label = { Text("السداد من: ${w.name}") }) }
            Button(enabled = !busy && paymentWallet != null, onClick = {
                val value = minor(repayment)
                if (value == null || value <= 0) status = "أدخل مبلغ سداد صحيحاً." else settlementRequest = value to paymentWallet!!.id
            }) { Text("مراجعة سداد الحساب") }
        }
        if (status.isNotBlank()) Text(status, color = FinanceColors.PrimaryLight)
    }
}
