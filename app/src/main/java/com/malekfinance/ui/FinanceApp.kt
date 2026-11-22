package com.malekfinance.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.malekfinance.FinanceDb
import com.malekfinance.FinanceTransaction
import com.malekfinance.voice.VoiceTransactionParser
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private enum class FinanceSection(val title: String) {
    HOME("الرئيسية"), TRANSACTIONS("العمليات"), VOICE("الصوت"), REPORTS("التقارير"), MONEY("مالي")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(db: FinanceDb) {
    var section by remember { mutableStateOf(FinanceSection.HOME) }
    val transactions by db.dao().observe().collectAsState(initial = emptyList())
    val income = transactions.filter { it.kind == "INCOME" }.sumOf { it.amountMinor }
    val expenses = transactions.filter { it.kind == "EXPENSE" }.sumOf { it.amountMinor }

    FinanceTheme {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Scaffold(
            containerColor = FinanceColors.Background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("مالك فاينانس", fontWeight = FontWeight.Bold)
                            Text(section.title, style = MaterialTheme.typography.labelSmall, color = FinanceColors.TextSecondary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = FinanceColors.Background)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = FinanceColors.Surface) {
                    FinanceSection.values().forEach { item ->
                        val icon = when (item) {
                            FinanceSection.HOME -> Icons.Default.Home
                            FinanceSection.TRANSACTIONS -> Icons.Default.ReceiptLong
                            FinanceSection.VOICE -> Icons.Default.Mic
                            FinanceSection.REPORTS -> Icons.Default.BarChart
                            FinanceSection.MONEY -> Icons.Default.AccountBalanceWallet
                        }
                        NavigationBarItem(
                            selected = section == item,
                            onClick = { section = item },
                            icon = { Icon(icon, contentDescription = item.title) },
                            label = { Text(item.title, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FinanceColors.PrimaryLight,
                                selectedTextColor = FinanceColors.PrimaryLight,
                                indicatorColor = FinanceColors.PrimaryTint,
                                unselectedIconColor = FinanceColors.TextSecondary,
                                unselectedTextColor = FinanceColors.TextSecondary
                            )
                        )
                    }
                }
            }
        ) { padding ->
            when (section) {
                    FinanceSection.HOME -> HomeDashboard(income, expenses, transactions, Modifier.padding(padding), onVoice = { section = FinanceSection.VOICE }, onTransactions = { section = FinanceSection.TRANSACTIONS })
                    FinanceSection.TRANSACTIONS -> TransactionListScreen(db, transactions, Modifier.padding(padding))
                    FinanceSection.VOICE -> VoiceLandingScreen(db, Modifier.padding(padding))
                    FinanceSection.REPORTS -> ReportsLandingScreen(income, expenses, transactions.size, Modifier.padding(padding))
                    FinanceSection.MONEY -> MoneyLandingScreen(Modifier.padding(padding))
            }
        }
        }
    }
}

@Composable
private fun HomeDashboard(
    income: Long,
    expenses: Long,
    transactions: List<FinanceTransaction>,
    modifier: Modifier = Modifier,
    onVoice: () -> Unit,
    onTransactions: () -> Unit
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("مرحباً بك 👋", color = FinanceColors.TextSecondary)
                Text("مالك فاينانس", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("أموالك تحت سيطرتك، بصوتك فقط", color = FinanceColors.TextSecondary)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = FinanceColors.SurfaceElevated)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("صافي رصيد العمليات المسجلة", color = FinanceColors.TextSecondary)
                    Text(formatMoney(income - expenses), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = FinanceColors.TextPrimary)
                    HorizontalDivider(color = FinanceColors.Divider)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("إجمالي الدخل", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                            Text(formatMoney(income), color = FinanceColors.Income, fontWeight = FontWeight.Bold)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("إجمالي المصروفات", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                            Text(formatMoney(expenses), color = FinanceColors.Expense, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = onVoice,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("سجّل عملية بصوتك", fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("آخر العمليات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onTransactions) { Text("عرض الكل", color = FinanceColors.PrimaryLight) }
            }
        }
        if (transactions.isEmpty()) {
            item { SectionCard("لا توجد عمليات حتى الآن", "ابدأ بإضافة أول دخل أو مصروف، وستظهر ملخصاتك هنا.") }
        } else {
            items(transactions.take(5), key = { it.id }) { row ->
                Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(row.note, fontWeight = FontWeight.SemiBold)
                            Text(row.category + " • " + if (row.kind == "INCOME") "دخل" else "مصروف", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            (if (row.kind == "INCOME") "+" else "−") + formatMoney(row.amountMinor),
                            color = if (row.kind == "INCOME") FinanceColors.Income else FinanceColors.Expense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionListScreen(db: FinanceDb, rows: List<FinanceTransaction>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("جميع العمليات المسجلة على هذا الجهاز", color = FinanceColors.TextSecondary) }
        if (rows.isEmpty()) {
            item { SectionCard("لا توجد عمليات بعد", "أضف أول دخل أو مصروف من الرئيسية.") }
        } else {
            items(rows, key = { it.id }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(row.note, fontWeight = FontWeight.SemiBold)
                            Text(row.category + " • " + if (row.kind == "INCOME") "دخل" else "مصروف", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Text((if (row.kind == "INCOME") "+" else "−") + formatMoney(row.amountMinor), color = if (row.kind == "INCOME") FinanceColors.Income else FinanceColors.Expense, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { scope.launch { db.dao().delete(row) } }) { Text("حذف", color = FinanceColors.Expense) }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceLandingScreen(db: FinanceDb, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var transcript by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("عام") }
    var kind by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("تحدث عن العملية، ثم راجع كل التفاصيل قبل الحفظ.") }
    var hasDraft by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (spoken.isNullOrBlank()) {
            status = "لم يتم التقاط كلام واضح. يمكنك إعادة المحاولة أو الكتابة يدوياً."
        } else {
            val parsed = VoiceTransactionParser.parse(spoken)
            transcript = spoken
            amount = parsed.amount.orEmpty()
            description = parsed.description
            category = parsed.category
            kind = parsed.kind
            hasDraft = true
            status = when {
                parsed.amount == null && parsed.kind == null -> "لم أتعرف على المبلغ أو نوع العملية. أكمل البيانات وحدد دخل أو مصروف."
                parsed.amount == null -> "حدد المبلغ قبل الحفظ."
                parsed.kind == null -> "لم يتضح هل هي عملية دخل أم مصروف. اختر النوع بنفسك."
                else -> "راجع المبلغ والنوع والوصف والتصنيف. لن تُحفظ العملية قبل تأكيدك."
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchFinanceRecognition(speechLauncher)
        else status = "إذن الميكروفون مرفوض. يمكنك إدخال العملية يدوياً."
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchFinanceRecognition(speechLauncher)
        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun saveReviewedDraft() {
        val normalized = normalizeFinanceAmount(amount).replace('٫', '.').replace(',', '.').trim()
        val minor = runCatching { normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() }.getOrNull()
        val selectedKind = kind
        if (minor == null || minor <= 0L) {
            status = "أدخل مبلغاً صحيحاً أكبر من صفر."
            return
        }
        if (selectedKind == null) {
            status = "اختر نوع العملية: دخل أو مصروف."
            return
        }
        if (description.isBlank()) {
            status = "أدخل وصفاً للعملية قبل الحفظ."
            return
        }
        scope.launch {
            db.dao().insert(
                FinanceTransaction(
                    amountMinor = minor,
                    kind = selectedKind,
                    note = description.trim(),
                    category = category
                )
            )
            transcript = ""
            amount = ""
            description = ""
            kind = null
            category = "عام"
            hasDraft = false
            status = "تم حفظ العملية التي راجعتها بنجاح."
        }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = FinanceColors.SurfaceElevated)
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = FinanceColors.PrimaryTint,
                        modifier = Modifier.size(84.dp)
                    ) {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Icon(Icons.Default.Mic, contentDescription = "الميكروفون", tint = FinanceColors.PrimaryLight, modifier = Modifier.size(38.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("مساعدك المالي الصوتي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("مثال: صرفت ١٥٠٠ ريال بنزين", color = FinanceColors.TextSecondary)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = ::startListening,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("ابدأ التحدث")
                    }
                }
            }
        }
        if (transcript.isNotBlank()) {
            item {
                SectionCard("العبارة التي سمعتها", transcript, "مسودة للمراجعة فقط")
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (hasDraft) "راجع وعدّل قبل الحفظ" else "إدخال يدوي بديل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("نوع العملية", color = FinanceColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == "EXPENSE", onClick = { kind = "EXPENSE" }, label = { Text("مصروف") })
                        FilterChip(selected = kind == "INCOME", onClick = { kind = "INCOME" }, label = { Text("دخل") })
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("المبلغ بالريال اليمني") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("وصف العملية") },
                        singleLine = true
                    )
                    Text("التصنيف", color = FinanceColors.TextSecondary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("عام", "بقالة", "مطاعم", "مواصلات").forEach { value ->
                                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("بنزين", "فواتير", "راتب").forEach { value ->
                                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value) })
                            }
                        }
                    }
                    Text(status, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = ::saveReviewedDraft,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                    ) {
                        Text("تأكيد وحفظ العملية")
                    }
                }
            }
        }
    }
}

private fun launchFinanceRecognition(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-YE")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "مثال: صرفت ١٥٠٠ ريال بنزين")
    }
    runCatching { launcher.launch(intent) }
}

private fun normalizeFinanceAmount(value: String): String {
    val arabic = "٠١٢٣٤٥٦٧٨٩"
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    return buildString {
        value.forEach { ch ->
            val a = arabic.indexOf(ch)
            val p = persian.indexOf(ch)
            append(if (a >= 0) a else if (p >= 0) p else ch)
        }
    }
}

@Composable
private fun ReportsLandingScreen(income: Long, expenses: Long, count: Int, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionCard("إجمالي الدخل", formatMoney(income)) }
        item { SectionCard("إجمالي المصروفات", formatMoney(expenses)) }
        item { SectionCard("صافي العمليات", formatMoney(income - expenses)) }
        item { SectionCard("عدد العمليات", count.toString(), "الرسوم البيانية والتقارير الزمنية ستضاف في مرحلة التقارير.") }
    }
}

@Composable
private fun MoneyLandingScreen(modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionCard("المحافظ والعملات", "إدارة أرصدتك ومحافظك المتعددة — ضمن المرحلة المالية القادمة.") }
        item { SectionCard("الميزانيات", "تحديد حدود الإنفاق ومتابعتها — لم تُفعّل بعد.") }
        item { SectionCard("الديون والسلف", "تسجيل المستحقات والسداد الجزئي — لم تُفعّل بعد.") }
        item { SectionCard("الادخار", "أهداف الادخار ومتابعة التقدم — لم تُفعّل بعد.") }
        item { SectionCard("الرواتب والفواتير", "الدخل المتكرر والاستحقاقات — لم تُفعّل بعد.") }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, trailing: String? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (trailing != null) Text(trailing, color = FinanceColors.PrimaryLight, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatMoney(minor: Long): String {
    val value = java.math.BigDecimal.valueOf(minor, 2)
    return java.text.NumberFormat.getNumberInstance(java.util.Locale("ar")).format(value) + " ر.ي"
}
