package com.malekfinance.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.malekfinance.FinanceWallet
import com.malekfinance.FinanceTransfer
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
    val wallets by db.walletDao().observe().collectAsState(initial = emptyList())
    val transfers by db.transferDao().observe().collectAsState(initial = emptyList())
    // Never combine different currencies into one misleading total.
    val yerTransactions = transactions.filter { it.currencyCode == "YER" }
    val income = yerTransactions.filter { it.kind == "INCOME" }
        .fold(java.math.BigInteger.ZERO) { total, row -> total.add(java.math.BigInteger.valueOf(row.amountMinor)) }
    val expenses = yerTransactions.filter { it.kind == "EXPENSE" }
        .fold(java.math.BigInteger.ZERO) { total, row -> total.add(java.math.BigInteger.valueOf(row.amountMinor)) }

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
                    FinanceSection.VOICE -> VoiceLandingScreen(db, wallets, Modifier.padding(padding))
                    FinanceSection.REPORTS -> ReportsLandingScreen(income, expenses, transactions.size, Modifier.padding(padding))
                    FinanceSection.MONEY -> MoneyLandingScreen(db, wallets, transactions, transfers, Modifier.padding(padding))
            }
        }
        }
    }
}

@Composable
private fun HomeDashboard(
    income: java.math.BigInteger,
    expenses: java.math.BigInteger,
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
                    Text("صافي العمليات المسجلة بالريال اليمني", color = FinanceColors.TextSecondary)
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
                            (if (row.kind == "INCOME") "+" else "−") + formatCurrencyMoney(row.amountMinor, row.currencyCode),
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
    var search by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf("ALL") }
    var categoryFilter by remember { mutableStateOf("الكل") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editAmount by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("") }
    var editKind by remember { mutableStateOf("EXPENSE") }
    var status by remember { mutableStateOf<String?>(null) }

    val categories = rows.map { it.category }.distinct().sorted()
    val filteredRows = rows.filter { row ->
        (kindFilter == "ALL" || row.kind == kindFilter) &&
            (categoryFilter == "الكل" || row.category == categoryFilter) &&
            (search.isBlank() || row.note.contains(search.trim(), ignoreCase = true) ||
                row.category.contains(search.trim(), ignoreCase = true) ||
                row.amountMinor.toString().contains(search.trim()))
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("ابحث بالوصف أو التصنيف أو المبلغ", color = FinanceColors.TextSecondary)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("بحث في العمليات") },
                singleLine = true
            )
        }
        item {
            Text("نوع العملية", color = FinanceColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kindFilter == "ALL", onClick = { kindFilter = "ALL" }, label = { Text("الكل") })
                FilterChip(selected = kindFilter == "INCOME", onClick = { kindFilter = "INCOME" }, label = { Text("الدخل") })
                FilterChip(selected = kindFilter == "EXPENSE", onClick = { kindFilter = "EXPENSE" }, label = { Text("المصروفات") })
            }
        }
        if (categories.isNotEmpty()) {
            item {
                Text("التصنيف", color = FinanceColors.TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = categoryFilter == "الكل", onClick = { categoryFilter = "الكل" }, label = { Text("الكل") })
                    }
                    items(categories) { value ->
                        FilterChip(selected = categoryFilter == value, onClick = { categoryFilter = value }, label = { Text(value) })
                    }
                }
            }
        }
        if (status != null) item { Text(status!!, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        if (filteredRows.isEmpty()) {
            item {
                SectionCard(
                    if (rows.isEmpty()) "لا توجد عمليات بعد" else "لا توجد نتائج مطابقة",
                    if (rows.isEmpty()) "أضف أول دخل أو مصروف من الرئيسية." else "غيّر عبارة البحث أو الفلاتر لعرض عمليات أخرى."
                )
            }
        } else {
            items(filteredRows, key = { it.id }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editingId == row.id) {
                            Text("تعديل العملية", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = editKind == "EXPENSE", onClick = { editKind = "EXPENSE" }, label = { Text("مصروف") })
                                FilterChip(selected = editKind == "INCOME", onClick = { editKind = "INCOME" }, label = { Text("دخل") })
                            }
                            OutlinedTextField(
                                value = editAmount,
                                onValueChange = { editAmount = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("المبلغ بعملة ${row.currencyCode}") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editNote,
                                onValueChange = { editNote = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("الوصف") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editCategory,
                                onValueChange = { editCategory = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("التصنيف") },
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val normalized = normalizeFinanceAmount(editAmount).replace('٫', '.').replace(',', '.').trim()
                                    val amountMinor = runCatching {
                                        normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact()
                                    }.getOrNull()
                                    when {
                                        amountMinor == null || amountMinor <= 0L -> status = "أدخل مبلغاً صحيحاً أكبر من صفر."
                                        editNote.isBlank() -> status = "لا يمكن حفظ وصف فارغ."
                                        editCategory.isBlank() -> status = "أدخل تصنيفاً للعملية."
                                        else -> scope.launch {
                                            runCatching {
                                                db.dao().update(row.copy(
                                                    amountMinor = amountMinor,
                                                    kind = editKind,
                                                    note = editNote.trim(),
                                                    category = editCategory.trim()
                                                ))
                                            }.onSuccess {
                                                editingId = null
                                                status = "تم تحديث العملية."
                                            }.onFailure {
                                                status = "تعذر تحديث العملية. حاول مرة أخرى."
                                            }
                                        }
                                    }
                                }) { Text("حفظ التعديل") }
                                TextButton(onClick = { editingId = null }) { Text("إلغاء") }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(row.note, fontWeight = FontWeight.SemiBold)
                                    Text(row.category + " • " + if (row.kind == "INCOME") "دخل" else "مصروف", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        (if (row.kind == "INCOME") "+" else "−") + formatCurrencyMoney(row.amountMinor, row.currencyCode),
                                        color = if (row.kind == "INCOME") FinanceColors.Income else FinanceColors.Expense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column {
                                    TextButton(onClick = {
                                        editingId = row.id
                                        editAmount = java.math.BigDecimal.valueOf(row.amountMinor, 2).stripTrailingZeros().toPlainString()
                                        editNote = row.note
                                        editCategory = row.category
                                        editKind = row.kind
                                        status = null
                                    }) { Text("تعديل", color = FinanceColors.PrimaryLight) }
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { db.dao().delete(row) }
                                                .onSuccess { status = "تم حذف العملية." }
                                                .onFailure { status = "تعذر حذف العملية. حاول مرة أخرى." }
                                        }
                                    }) { Text("حذف", color = FinanceColors.Expense) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceLandingScreen(db: FinanceDb, wallets: List<FinanceWallet>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var transcript by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("عام") }
    var kind by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("تحدث عن العملية، ثم راجع كل التفاصيل قبل الحفظ.") }
    var hasDraft by remember { mutableStateOf(false) }
    var selectedWalletId by remember { mutableStateOf<Long?>(null) }
    val selectedWallet = wallets.firstOrNull { it.id == selectedWalletId } ?: wallets.firstOrNull()

    LaunchedEffect(wallets) {
        if (selectedWalletId == null && wallets.isNotEmpty()) selectedWalletId = wallets.first().id
    }

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
        val wallet = selectedWallet
        if (wallet == null) {
            status = "أنشئ محفظة أولاً من قسم مالي."
            return
        }
        scope.launch {
            runCatching {
                db.dao().insert(
                    FinanceTransaction(
                        amountMinor = minor,
                        kind = selectedKind,
                        note = description.trim(),
                        category = category,
                        walletId = wallet.id,
                        currencyCode = wallet.currencyCode
                    )
                )
            }.onSuccess {
                transcript = ""
                amount = ""
                description = ""
                kind = null
                category = "عام"
                hasDraft = false
                status = "تم حفظ العملية في ${wallet.name}."
            }.onFailure {
                status = "تعذر حفظ العملية. بيانات المسودة ما زالت موجودة؛ حاول مجدداً."
            }
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
                    Text("المحفظة التي ستُسجّل فيها العملية", color = FinanceColors.TextSecondary)
                    if (wallets.isEmpty()) {
                        Text("لا توجد محفظة. أنشئ محفظة من قسم «مالي» أولاً.", color = FinanceColors.Expense)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(wallets, key = { it.id }) { wallet ->
                                FilterChip(
                                    selected = selectedWallet?.id == wallet.id,
                                    onClick = { selectedWalletId = wallet.id },
                                    label = { Text("${wallet.name} (${wallet.currencyCode})") }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("المبلغ بعملة ${selectedWallet?.currencyCode ?: "YER"}") },
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
private fun ReportsLandingScreen(income: java.math.BigInteger, expenses: java.math.BigInteger, count: Int, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionCard("إجمالي الدخل بالريال اليمني", formatMoney(income)) }
        item { SectionCard("إجمالي المصروفات بالريال اليمني", formatMoney(expenses)) }
        item { SectionCard("صافي العمليات بالريال اليمني", formatMoney(income.subtract(expenses))) }
        item { SectionCard("عدد العمليات", count.toString(), "الرسوم البيانية والتقارير الزمنية ستضاف في مرحلة التقارير.") }
    }
}

@Composable
private fun MoneyLandingScreen(
    db: FinanceDb,
    wallets: List<FinanceWallet>,
    transactions: List<FinanceTransaction>,
    transfers: List<FinanceTransfer>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var walletName by remember { mutableStateOf("") }
    var openingBalance by remember { mutableStateOf("0") }
    var walletCurrency by remember { mutableStateOf("YER") }
    var walletType by remember { mutableStateOf("CASH") }
    var fromWalletId by remember { mutableStateOf<Long?>(null) }
    var toWalletId by remember { mutableStateOf<Long?>(null) }
    var transferAmount by remember { mutableStateOf("") }
    var transferNote by remember { mutableStateOf("تحويل داخلي") }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(wallets) {
        if (fromWalletId == null && wallets.isNotEmpty()) fromWalletId = wallets.first().id
        if (toWalletId == null && wallets.size > 1) toWalletId = wallets.first { it.id != fromWalletId }.id
    }
    val fromWallet = wallets.firstOrNull { it.id == fromWalletId } ?: wallets.firstOrNull()
    val toWallet = wallets.firstOrNull { it.id == toWalletId && it.id != fromWallet?.id }
        ?: wallets.firstOrNull { it.id != fromWallet?.id }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("محافظك وأرصدتك", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("كل محفظة تحتفظ برصيدها وعملتها بشكل مستقل؛ لا نجمع عملات مختلفة في رصيد واحد.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (wallets.isEmpty()) {
            item { SectionCard("لم تُنشأ محفظة بعد", "ابدأ بإنشاء محفظتك الرئيسية لتسجيل الدخل والمصروفات.") }
        } else {
            items(wallets, key = { it.id }) { wallet ->
                val balance = com.malekfinance.FinanceLedger.calculateBalance(wallet, transactions, transfers)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = FinanceColors.SurfaceElevated)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(wallet.name, fontWeight = FontWeight.Bold)
                            Text(walletTypeLabel(wallet.walletType), color = FinanceColors.PrimaryLight, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(formatCurrencyMoney(balance, wallet.currencyCode), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("العملة: " + wallet.currencyCode + " • الرصيد الافتتاحي: " + formatCurrencyMoney(wallet.openingBalanceMinor, wallet.currencyCode), color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إنشاء محفظة جديدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = walletName, onValueChange = { walletName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المحفظة") }, singleLine = true)
                    OutlinedTextField(
                        value = openingBalance,
                        onValueChange = { openingBalance = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("الرصيد الافتتاحي ($walletCurrency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    Text("العملة", color = FinanceColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("YER", "USD", "SAR", "AED").forEach { code ->
                            FilterChip(selected = walletCurrency == code, onClick = { walletCurrency = code }, label = { Text(code) })
                        }
                    }
                    Text("نوع المحفظة", color = FinanceColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CASH" to "نقدية", "BANK" to "بنكية", "E_WALLET" to "إلكترونية").forEach { (code, label) ->
                            FilterChip(selected = walletType == code, onClick = { walletType = code }, label = { Text(label) })
                        }
                    }
                    Button(
                        onClick = {
                            val name = walletName.trim()
                            val normalized = normalizeFinanceAmount(openingBalance).replace('٫', '.').replace(',', '.').trim()
                            val openingMinor = runCatching { normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() }.getOrNull()
                            when {
                                name.isBlank() -> status = "اكتب اسماً للمحفظة."
                                wallets.any { it.name.equals(name, ignoreCase = true) } -> status = "يوجد بالفعل محفظة بهذا الاسم."
                                openingMinor == null -> status = "أدخل رصيداً افتتاحياً صحيحاً."
                                else -> scope.launch {
                                    runCatching {
                                        db.walletDao().insert(FinanceWallet(name = name, currencyCode = walletCurrency, openingBalanceMinor = openingMinor, walletType = walletType))
                                    }.onSuccess {
                                        walletName = ""
                                        openingBalance = "0"
                                        status = "تم إنشاء المحفظة بنجاح."
                                    }.onFailure {
                                        status = "تعذر إنشاء المحفظة. حاول مرة أخرى."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                    ) { Text("إنشاء المحفظة") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تحويل بين محافظك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("التحويل الداخلي لا يُسجّل دخلاً أو مصروفاً. التحويل الحالي متاح بين محافظ العملة نفسها فقط.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("من المحفظة", color = FinanceColors.TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(wallets, key = { it.id }) { wallet ->
                            FilterChip(selected = fromWallet?.id == wallet.id, onClick = { fromWalletId = wallet.id }, label = { Text(wallet.name) })
                        }
                    }
                    Text("إلى المحفظة", color = FinanceColors.TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(wallets.filter { it.id != fromWallet?.id }, key = { it.id }) { wallet ->
                            FilterChip(selected = toWallet?.id == wallet.id, onClick = { toWalletId = wallet.id }, label = { Text(wallet.name) })
                        }
                    }
                    OutlinedTextField(value = transferAmount, onValueChange = { transferAmount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("المبلغ (" + (fromWallet?.currencyCode ?: "—") + ")") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = transferNote, onValueChange = { transferNote = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظة التحويل") }, singleLine = true)
                    if (fromWallet != null && toWallet != null && fromWallet.currencyCode != toWallet.currencyCode) {
                        Text("لا يمكن تحويل " + fromWallet.currencyCode + " إلى " + toWallet.currencyCode + " قبل تحديد سعر صرف موثوق.", color = FinanceColors.Expense, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            val normalized = normalizeFinanceAmount(transferAmount).replace('٫', '.').replace(',', '.').trim()
                            val amountMinor = runCatching { normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() }.getOrNull()
                            when {
                                fromWallet == null || toWallet == null -> status = "أنشئ محفظتين على الأقل لإجراء التحويل."
                                fromWallet.id == toWallet.id -> status = "اختر محفظتين مختلفتين."
                                fromWallet.currencyCode != toWallet.currencyCode -> status = "التحويل بين عملتين مختلفتين يحتاج سعر صرف واضحاً."
                                amountMinor == null || amountMinor <= 0L -> status = "أدخل مبلغ تحويل صحيحاً أكبر من صفر."
                                !com.malekfinance.FinanceLedger.hasSufficientFunds(fromWallet, amountMinor, transactions, transfers) -> status = "رصيد المحفظة المصدر لا يكفي لإتمام التحويل."
                                else -> scope.launch {
                                    runCatching {
                                        db.transferDao().insert(FinanceTransfer(
                                            fromWalletId = fromWallet.id,
                                            toWalletId = toWallet.id,
                                            amountMinor = amountMinor,
                                            currencyCode = fromWallet.currencyCode,
                                            note = transferNote.trim()
                                        ))
                                    }.onSuccess {
                                        transferAmount = ""
                                        status = "تم تسجيل التحويل الداخلي وتحديث رصيدي المحفظتين."
                                    }.onFailure {
                                        status = "تعذر تسجيل التحويل. لم يتم تعديل أي رصيد."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                    ) { Text("تأكيد التحويل") }
                }
            }
        }
        if (status != null) item { Text(status!!, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        item { SectionCard("الميزانيات", "تحديد حدود الإنفاق ومتابعتها — لم تُفعّل بعد.") }
        item { SectionCard("الديون والسلف", "تسجيل المستحقات والسداد الجزئي — لم تُفعّل بعد.") }
        item { SectionCard("الادخار", "أهداف الادخار ومتابعة التقدم — لم تُفعّل بعد.") }
        item { SectionCard("الرواتب والفواتير", "الدخل المتكرر والاستحقاقات — لم تُفعّل بعد.") }
    }
}


private fun formatCurrencyMoney(minor: Long, currencyCode: String): String =
    formatCurrencyMoney(java.math.BigInteger.valueOf(minor), currencyCode)

private fun formatCurrencyMoney(minor: java.math.BigInteger, currencyCode: String): String {
    val amount = java.math.BigDecimal(minor, 2)
    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale("ar")).format(amount)
    val suffix = when (currencyCode) {
        "YER" -> "ر.ي"
        "USD" -> "$"
        "SAR" -> "ر.س"
        "AED" -> "د.إ"
        else -> currencyCode
    }
    return "$formatted $suffix"
}

private fun walletTypeLabel(type: String): String = when (type) {
    "BANK" -> "بنكية"
    "E_WALLET" -> "إلكترونية"
    else -> "نقدية"
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

private fun formatMoney(minor: java.math.BigInteger): String {
    val value = java.math.BigDecimal(minor, 2)
    return java.text.NumberFormat.getNumberInstance(java.util.Locale("ar")).format(value) + " ر.ي"
}
