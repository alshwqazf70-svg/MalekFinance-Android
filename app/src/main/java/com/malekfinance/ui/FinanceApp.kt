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
import androidx.room.withTransaction

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
    val categories by db.categoryDao().observeAll().collectAsState(initial = emptyList())
    val budgets by db.budgetDao().observeAll().collectAsState(initial = emptyList())
    val debts by db.debtDao().observeAll().collectAsState(initial = emptyList())
    val debtPayments by db.debtPaymentDao().observeAll().collectAsState(initial = emptyList())
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
                    FinanceSection.TRANSACTIONS -> TransactionListScreen(db, transactions, categories.filter { it.isActive }, Modifier.padding(padding))
                    FinanceSection.VOICE -> VoiceLandingScreen(db, wallets, categories.filter { it.isActive }, Modifier.padding(padding))
                    FinanceSection.REPORTS -> ReportsLandingScreen(income, expenses, transactions.size, Modifier.padding(padding))
                    FinanceSection.MONEY -> MoneyLandingScreen(db, wallets, transactions, transfers, categories, budgets, debts, debtPayments, Modifier.padding(padding))
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
                    Text(formatMoney(income.subtract(expenses)), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = FinanceColors.TextPrimary)
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
                            Text(row.category + " • " + when (row.kind) { "INCOME" -> "دخل"; "EXPENSE" -> "مصروف"; "DEBT_RECEIVED" -> "استلام قرض"; "DEBT_ISSUED" -> "إقراض سلفة"; else -> "عملية" }, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            (if (row.kind == "INCOME" || row.kind == "DEBT_RECEIVED") "+" else "−") + formatCurrencyMoney(row.amountMinor, row.currencyCode),
                            color = if (row.kind == "INCOME" || row.kind == "DEBT_RECEIVED") FinanceColors.Income else FinanceColors.Expense,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionListScreen(db: FinanceDb, rows: List<FinanceTransaction>, activeCategories: List<com.malekfinance.FinanceCategory>, modifier: Modifier = Modifier) {
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
                            Text("التصنيف", color = FinanceColors.TextSecondary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(activeCategories.filter { com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, editKind) }, key = { it.id }) { item ->
                                    FilterChip(
                                        selected = editCategory == item.name,
                                        onClick = { editCategory = item.name },
                                        label = { Text(item.name) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val normalized = normalizeFinanceAmount(editAmount).replace('٫', '.').replace(',', '.').trim()
                                    val amountMinor = runCatching {
                                        normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact()
                                    }.getOrNull()
                                    when {
                                        amountMinor == null || amountMinor <= 0L -> status = "أدخل مبلغاً صحيحاً أكبر من صفر."
                                        editNote.isBlank() -> status = "لا يمكن حفظ وصف فارغ."
                                        editCategory.isBlank() -> status = "اختر تصنيفاً للعملية."
                                        activeCategories.none { it.name == editCategory && com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, editKind) } -> status = "اختر تصنيفاً يناسب نوع العملية."
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
                                    Text(row.category + " • " + when (row.kind) { "INCOME" -> "دخل"; "EXPENSE" -> "مصروف"; "DEBT_RECEIVED" -> "استلام قرض"; "DEBT_ISSUED" -> "إقراض سلفة"; else -> "عملية" }, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        (if (row.kind == "INCOME" || row.kind == "DEBT_RECEIVED") "+" else "−") + formatCurrencyMoney(row.amountMinor, row.currencyCode),
                                        color = if (row.kind == "INCOME" || row.kind == "DEBT_RECEIVED") FinanceColors.Income else FinanceColors.Expense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (row.debtId == null) {
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
                                } else {
                                    Text("مرتبطة بدين؛ تُدار من قسم الديون", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
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
private fun VoiceLandingScreen(db: FinanceDb, wallets: List<FinanceWallet>, activeCategories: List<com.malekfinance.FinanceCategory>, modifier: Modifier = Modifier) {
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
    val compatibleCategories = activeCategories.filter {
        kind == null || com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, kind!!)
    }

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
        if (activeCategories.none {
                it.name == category && (selectedKind == null || com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, selectedKind))
            }) {
            status = "اختر تصنيفاً نشطاً يناسب نوع العملية."
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(compatibleCategories, key = { it.id }) { item ->
                            FilterChip(
                                selected = category == item.name,
                                onClick = { category = item.name },
                                label = { Text(item.name) }
                            )
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
private fun DebtManagementSection(
    db: FinanceDb,
    wallets: List<FinanceWallet>,
    debts: List<com.malekfinance.FinanceDebt>,
    debtPayments: List<com.malekfinance.FinanceDebtPayment>
) {
    val scope = rememberCoroutineScope()
    var person by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var debtType by remember { mutableStateOf("OWED_BY_ME") }
    var recordCashMovement by remember { mutableStateOf(true) }
    var walletId by remember { mutableStateOf<Long?>(null) }
    var paymentInputs by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(wallets) {
        if (walletId == null || wallets.none { it.id == walletId }) walletId = wallets.firstOrNull()?.id
    }

    Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("الديون والسلف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("سجّل الدين أولاً، ثم أضف السداد أو التحصيل على دفعات. كل دفعة تُسجّل كعملية دخل أو مصروف مرتبطة بالدين، حتى يبقى رصيد المحفظة صحيحاً.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = person, onValueChange = { person = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم الشخص أو الجهة") }, singleLine = true)
            OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("وصف الدين أو السلفة (اختياري)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = debtType == "OWED_BY_ME", onClick = { debtType = "OWED_BY_ME" }, label = { Text("أنا مدين") })
                FilterChip(selected = debtType == "OWED_TO_ME", onClick = { debtType = "OWED_TO_ME" }, label = { Text("لي عنده") })
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = recordCashMovement, onCheckedChange = { recordCashMovement = it })
                Text("تحديث رصيد المحفظة بأصل الدين (ألغِ التحديد إذا كان ديناً قديماً مسجلاً سابقاً)", style = MaterialTheme.typography.bodySmall)
            }
            Text("محفظة وعملة الدين", color = FinanceColors.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(wallets, key = { it.id }) { wallet ->
                    FilterChip(selected = walletId == wallet.id, onClick = { walletId = wallet.id }, label = { Text(wallet.name + " (" + wallet.currencyCode + ")") })
                }
            }
            OutlinedTextField(
                value = principal,
                onValueChange = { principal = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("المبلغ الأصلي (" + (wallets.firstOrNull { it.id == walletId }?.currencyCode ?: "YER") + ")") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            Button(
                onClick = {
                    val cleanName = person.trim()
                    val cleanDescription = description.trim()
                    val selectedDebtType = debtType
                    val shouldRecordCashMovement = recordCashMovement
                    val normalized = normalizeFinanceAmount(principal).replace('٫', '.').replace(',', '.').trim()
                    val amountMinor = runCatching { normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() }.getOrNull()
                    val wallet = wallets.firstOrNull { it.id == walletId }
                    when {
                        cleanName.isBlank() -> status = "اكتب اسم الشخص أو الجهة."
                        amountMinor == null || amountMinor <= 0L -> status = "أدخل مبلغاً أصلياً صحيحاً أكبر من صفر."
                        wallet == null -> status = "أنشئ محفظة أولاً لتحديد عملة الدين."
                        else -> scope.launch {
                            if (busy) return@launch
                            busy = true
                            try {
                                db.withTransaction {
                                    val currentWallet = db.walletDao().findById(wallet.id) ?: error("Wallet not found")
                                    check(currentWallet.currencyCode == wallet.currencyCode)
                                    if (shouldRecordCashMovement && selectedDebtType == "OWED_TO_ME") {
                                        val currentTransactions = db.dao().forWallet(currentWallet.id)
                                        val currentTransfers = db.transferDao().forWallet(currentWallet.id)
                                        if (!com.malekfinance.FinanceLedger.hasSufficientFunds(currentWallet, amountMinor, currentTransactions, currentTransfers)) {
                                            throw com.malekfinance.InsufficientWalletFundsException()
                                        }
                                    }
                                    val debtId = db.debtDao().insert(com.malekfinance.FinanceDebt(
                                        personName = cleanName,
                                        description = cleanDescription,
                                        debtType = selectedDebtType,
                                        principalMinor = amountMinor,
                                        currencyCode = wallet.currencyCode,
                                        walletId = wallet.id,
                                        cashMovementRecorded = shouldRecordCashMovement
                                    ))
                                    if (recordCashMovement) db.dao().insert(FinanceTransaction(
                                        amountMinor = amountMinor,
                                        kind = if (selectedDebtType == "OWED_BY_ME") "DEBT_RECEIVED" else "DEBT_ISSUED",
                                        note = (if (selectedDebtType == "OWED_BY_ME") "استلام قرض من: " else "إقراض سلفة إلى: ") + cleanName,
                                        category = if (selectedDebtType == "OWED_BY_ME") "قرض مستلم" else "سلفة ممنوحة",
                                        walletId = wallet.id,
                                        currencyCode = wallet.currencyCode,
                                        debtId = debtId
                                    ))
                                }
                                person = ""
                                description = ""
                                principal = ""
                                status = if (shouldRecordCashMovement) "تم تسجيل أصل الدين وحركة المحفظة معاً." else "تم تسجيل الدين السابق دون تعديل رصيد المحفظة."
                            } catch (_: com.malekfinance.InsufficientWalletFundsException) {
                                status = "رصيد المحفظة لا يكفي لإقراض هذا المبلغ."
                            } catch (_: Exception) {
                                status = "تعذر تسجيل الدين؛ لم يتم حفظ أي جزء من العملية."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && wallets.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
            ) { Text("تسجيل الدين أو السلفة") }

            HorizontalDivider(color = FinanceColors.Divider)
            if (debts.isEmpty()) {
                Text("لا توجد ديون مسجلة حالياً.", color = FinanceColors.TextSecondary)
            } else {
                debts.forEach { debt ->
                    val rows = debtPayments.filter { it.debtId == debt.id }
                    val outstanding = com.malekfinance.FinanceDebtPolicy.outstanding(debt.principalMinor, rows)
                    val paid = com.malekfinance.FinanceDebtPolicy.paidAmount(rows)
                    val iOwe = debt.debtType == "OWED_BY_ME"
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(debt.personName, fontWeight = FontWeight.Bold)
                            Text(if (iOwe) "أنا مدين" else "لي عنده", color = FinanceColors.PrimaryLight, style = MaterialTheme.typography.labelMedium)
                        }
                        if (debt.description.isNotBlank()) Text(debt.description, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text("الأصل: " + formatCurrencyMoney(debt.principalMinor, debt.currencyCode) + " • المدفوع: " + formatCurrencyMoney(paid, debt.currencyCode), color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (outstanding.signum() == 0) "تمت التسوية بالكامل" else "المتبقي: " + formatCurrencyMoney(outstanding, debt.currencyCode),
                            color = if (outstanding.signum() == 0) FinanceColors.Income else FinanceColors.PrimaryLight,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (outstanding.signum() > 0) {
                            OutlinedTextField(
                                value = paymentInputs[debt.id].orEmpty(),
                                onValueChange = { value -> paymentInputs = paymentInputs + (debt.id to value) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (iOwe) "مبلغ السداد الجزئي" else "مبلغ التحصيل الجزئي") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    val normalized = normalizeFinanceAmount(paymentInputs[debt.id].orEmpty()).replace('٫', '.').replace(',', '.').trim()
                                    val amountMinor = runCatching { normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() }.getOrNull()
                                    when {
                                        amountMinor == null || amountMinor <= 0L -> status = "أدخل مبلغ دفعة صحيحاً أكبر من صفر."
                                        java.math.BigInteger.valueOf(amountMinor) > outstanding -> status = "مبلغ الدفعة أكبر من المتبقي."
                                        else -> scope.launch {
                                            if (busy) return@launch
                                            busy = true
                                            try {
                                                db.withTransaction {
                                                    val current = db.debtDao().findById(debt.id) ?: error("Debt not found")
                                                    val currentPayments = db.debtPaymentDao().forDebt(debt.id)
                                                    check(com.malekfinance.FinanceDebtPolicy.canApplyPayment(current.principalMinor, currentPayments, amountMinor))
                                                    val paymentWallet = db.walletDao().findById(current.walletId) ?: error("Wallet not found")
                                                    check(paymentWallet.currencyCode == current.currencyCode)
                                                    if (current.debtType == "OWED_BY_ME") {
                                                        val latestTransactions = db.dao().forWallet(paymentWallet.id)
                                                        val latestTransfers = db.transferDao().forWallet(paymentWallet.id)
                                                        if (!com.malekfinance.FinanceLedger.hasSufficientFunds(paymentWallet, amountMinor, latestTransactions, latestTransfers)) {
                                                            throw com.malekfinance.InsufficientWalletFundsException()
                                                        }
                                                    }
                                                    val transactionId = db.dao().insert(FinanceTransaction(
                                                        amountMinor = amountMinor,
                                                        kind = if (current.debtType == "OWED_BY_ME") "EXPENSE" else "INCOME",
                                                        note = (if (current.debtType == "OWED_BY_ME") "سداد دين: " else "تحصيل دين: ") + current.personName,
                                                        category = if (current.debtType == "OWED_BY_ME") "سداد دين" else "تحصيل دين",
                                                        walletId = paymentWallet.id,
                                                        currencyCode = paymentWallet.currencyCode,
                                                        debtId = current.id
                                                    ))
                                                    db.debtPaymentDao().insert(com.malekfinance.FinanceDebtPayment(
                                                        debtId = current.id,
                                                        transactionId = transactionId,
                                                        amountMinor = amountMinor
                                                    ))
                                                }
                                                paymentInputs = paymentInputs - debt.id
                                                status = "تم تسجيل الدفعة وربطها بالعملية المالية."
                                            } catch (_: com.malekfinance.InsufficientWalletFundsException) {
                                                status = "رصيد المحفظة لا يكفي لإتمام السداد."
                                            } catch (_: Exception) {
                                                status = "تعذر تسجيل الدفعة؛ لم يُحفظ أي جزء منها. تحقق من المتبقي وحاول مجدداً."
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                            ) { Text(if (iOwe) "تسجيل السداد" else "تسجيل التحصيل") }
                        }
                        HorizontalDivider(color = FinanceColors.Divider)
                    }
                }
            }
            status?.let { Text(it, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
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
    categories: List<com.malekfinance.FinanceCategory>,
    budgets: List<com.malekfinance.FinanceBudget>,
    debts: List<com.malekfinance.FinanceDebt>,
    debtPayments: List<com.malekfinance.FinanceDebtPayment>,
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
    var isCreatingWallet by remember { mutableStateOf(false) }
    var isCreatingCategory by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    var categoryKind by remember { mutableStateOf("EXPENSE") }
    var isTransferring by remember { mutableStateOf(false) }
    var budgetCategoryName by remember { mutableStateOf("") }
    var budgetCurrency by remember { mutableStateOf("YER") }
    var budgetLimit by remember { mutableStateOf("") }
    var isCreatingBudget by remember { mutableStateOf(false) }
    val budgetMonth = remember { com.malekfinance.FinanceBudgetPolicy.monthWindow(System.currentTimeMillis()) }
    val budgetCategories = categories.filter { it.isActive && (it.kind == "EXPENSE" || it.kind == "BOTH") }

    LaunchedEffect(budgetCategories) {
        if (budgetCategories.none { it.name == budgetCategoryName }) {
            budgetCategoryName = budgetCategories.firstOrNull()?.name.orEmpty()
        }
    }

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
                    Text("إدارة التصنيفات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("أنشئ تصنيفاتك الخاصة أو أعد تفعيل المؤرشف منها. أرشفة التصنيف لا تغيّر العمليات القديمة.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اسم التصنيف الجديد") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = categoryKind == "EXPENSE", onClick = { categoryKind = "EXPENSE" }, label = { Text("مصروف") })
                        FilterChip(selected = categoryKind == "INCOME", onClick = { categoryKind = "INCOME" }, label = { Text("دخل") })
                        FilterChip(selected = categoryKind == "BOTH", onClick = { categoryKind = "BOTH" }, label = { Text("كلاهما") })
                    }
                    Button(
                        onClick = {
                            val name = com.malekfinance.FinanceCategoryPolicy.normalizedName(categoryName)
                            when {
                                name == null && categoryName.isBlank() -> status = "اكتب اسم التصنيف."
                                name == null -> status = "اسم التصنيف طويل؛ الحد الأقصى 32 حرفاً."
                                com.malekfinance.FinanceCategoryPolicy.nameAlreadyExists(categories.map { it.name }, name) -> status = "هذا التصنيف موجود بالفعل؛ يمكنك إعادة تفعيله من القائمة."
                                else -> scope.launch {
                                    if (isCreatingCategory) return@launch
                                    isCreatingCategory = true
                                    try {
                                        db.categoryDao().insert(com.malekfinance.FinanceCategory(name = name, kind = categoryKind))
                                        categoryName = ""
                                        status = "تمت إضافة التصنيف."
                                    } catch (_: Exception) {
                                        status = "تعذر إضافة التصنيف. تحقق من الاسم وحاول مجدداً."
                                    } finally {
                                        isCreatingCategory = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreatingCategory,
                        colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                    ) { Text(if (isCreatingCategory) "جارٍ إضافة التصنيف..." else "إضافة التصنيف") }
                    categories.forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (item.kind) { "INCOME" -> "تصنيف دخل"; "BOTH" -> "دخل ومصروف"; else -> "تصنيف مصروف" } +
                                        if (item.isActive) " • نشط" else " • مؤرشف",
                                    color = FinanceColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        if (item.isActive) db.categoryDao().archive(item.id)
                                        else db.categoryDao().restore(item.id)
                                    }.onSuccess {
                                        status = if (item.isActive) "تمت أرشفة التصنيف؛ العمليات السابقة محفوظة." else "تمت إعادة تفعيل التصنيف."
                                    }.onFailure {
                                        status = "تعذر تحديث حالة التصنيف."
                                    }
                                }
                            }) {
                                Text(if (item.isActive) "أرشفة" else "تفعيل", color = if (item.isActive) FinanceColors.Expense else FinanceColors.PrimaryLight)
                            }
                        }
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
                                    if (isCreatingWallet) return@launch
                                    isCreatingWallet = true
                                    try {
                                        db.walletDao().insert(FinanceWallet(name = name, currencyCode = walletCurrency, openingBalanceMinor = openingMinor, walletType = walletType))
                                        walletName = ""
                                        openingBalance = "0"
                                        status = "تم إنشاء المحفظة بنجاح."
                                    } catch (_: Exception) {
                                        status = "تعذر إنشاء المحفظة. حاول مرة أخرى."
                                    } finally {
                                        isCreatingWallet = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreatingWallet,
                        colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                    ) { Text(if (isCreatingWallet) "جارٍ إنشاء المحفظة..." else "إنشاء المحفظة") }
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
                                !com.malekfinance.FinanceLedger.hasSufficientFunds(fromWallet, amountMinor ?: 0L, transactions, transfers) -> status = "رصيد المحفظة المصدر لا يكفي لإتمام التحويل."
                                else -> scope.launch {
                                    if (isTransferring) return@launch
                                    isTransferring = true
                                    try {
                                        db.withTransaction {
                                            val latestTransactions = db.dao().forWallet(fromWallet.id)
                                            val latestTransfers = db.transferDao().forWallet(fromWallet.id)
                                            if (!com.malekfinance.FinanceLedger.hasSufficientFunds(fromWallet, amountMinor ?: 0L, latestTransactions, latestTransfers)) {
                                                throw com.malekfinance.InsufficientWalletFundsException()
                                            }
                                            db.transferDao().insert(FinanceTransfer(
                                                fromWalletId = fromWallet.id,
                                                toWalletId = toWallet.id,
                                                amountMinor = amountMinor ?: 0L,
                                                currencyCode = fromWallet.currencyCode,
                                                note = transferNote.trim()
                                            ))
                                        }
                                        transferAmount = ""
                                        status = "تم تسجيل التحويل الداخلي وتحديث رصيدي المحفظتين."
                                    } catch (_: com.malekfinance.InsufficientWalletFundsException) {
                                        status = "رصيد المحفظة المصدر لا يكفي لإتمام التحويل."
                                    } catch (_: Exception) {
                                        status = "تعذر تسجيل التحويل. لم يتم تعديل أي رصيد."
                                    } finally {
                                        isTransferring = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTransferring,
                        colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                    ) { Text(if (isTransferring) "جارٍ تنفيذ التحويل..." else "تأكيد التحويل") }
                }
            }
        }
        if (status != null) item { Text(status!!, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        item { DebtManagementSection(db, wallets, debts, debtPayments) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("الميزانيات الشهرية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("حدد سقفاً لكل تصنيف وعملة. الإنفاق يُحسب من المصروفات المسجلة خلال الشهر الحالي فقط.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    if (budgetCategories.isEmpty()) {
                        Text("أضف تصنيف مصروف نشطاً أولاً.", color = FinanceColors.TextSecondary)
                    } else {
                        Text("التصنيف", color = FinanceColors.TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(budgetCategories, key = { it.id }) { category ->
                                FilterChip(
                                    selected = budgetCategoryName == category.name,
                                    onClick = { budgetCategoryName = category.name },
                                    label = { Text(category.name) }
                                )
                            }
                        }
                        Text("العملة", color = FinanceColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("YER", "USD", "SAR", "AED").forEach { code ->
                                FilterChip(selected = budgetCurrency == code, onClick = { budgetCurrency = code }, label = { Text(code) })
                            }
                        }
                        OutlinedTextField(
                            value = budgetLimit,
                            onValueChange = { budgetLimit = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("حد الميزانية ($budgetCurrency) لهذا الشهر") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val selectedCategory = budgetCategoryName.ifBlank { budgetCategories.first().name }
                                val normalized = normalizeFinanceAmount(budgetLimit).replace('٫', '.').replace(',', '.').trim()
                                val limitMinor = runCatching { normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() }.getOrNull()
                                when {
                                    limitMinor == null || limitMinor <= 0L -> status = "أدخل حداً صحيحاً للميزانية أكبر من صفر."
                                    budgetCategories.none { it.name == selectedCategory } -> status = "اختر تصنيف مصروف نشطاً."
                                    budgets.any { it.categoryName == selectedCategory && it.currencyCode == budgetCurrency && it.periodKey == budgetMonth.periodKey } -> status = "توجد ميزانية لهذا التصنيف والعملة في الشهر الحالي."
                                    else -> scope.launch {
                                        if (isCreatingBudget) return@launch
                                        isCreatingBudget = true
                                        try {
                                            db.budgetDao().insert(com.malekfinance.FinanceBudget(
                                                categoryName = selectedCategory,
                                                currencyCode = budgetCurrency,
                                                periodKey = budgetMonth.periodKey,
                                                limitMinor = limitMinor
                                            ))
                                            budgetCategoryName = selectedCategory
                                            budgetLimit = ""
                                            status = "تم إنشاء الميزانية الشهرية."
                                        } catch (_: Exception) {
                                            status = "تعذر إنشاء الميزانية؛ قد تكون موجودة بالفعل."
                                        } finally {
                                            isCreatingBudget = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCreatingBudget && budgetCategories.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                        ) { Text(if (isCreatingBudget) "جارٍ إنشاء الميزانية..." else "إضافة ميزانية لهذا الشهر") }
                    }
                    val currentBudgets = budgets.filter { it.periodKey == budgetMonth.periodKey }
                    if (currentBudgets.isEmpty()) {
                        Text("لم تُحدد ميزانيات لهذا الشهر بعد.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        currentBudgets.forEach { budget ->
                            val spent = com.malekfinance.FinanceBudgetPolicy.calculateSpent(budget, transactions, budgetMonth.startMillis, budgetMonth.endExclusiveMillis)
                            val percent = com.malekfinance.FinanceBudgetPolicy.usagePercent(spent, budget.limitMinor)
                            val alert = com.malekfinance.FinanceBudgetPolicy.alertLevel(spent, budget.limitMinor)
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(budget.categoryName, fontWeight = FontWeight.SemiBold)
                                    Text("$percent%", color = if (alert == "OVER_LIMIT") FinanceColors.Expense else if (alert == "NEAR_LIMIT") FinanceColors.PrimaryLight else FinanceColors.TextSecondary)
                                }
                                LinearProgressIndicator(
                                    progress = { (spent.toDouble() / budget.limitMinor.toDouble()).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (alert == "OVER_LIMIT") FinanceColors.Expense else FinanceColors.Primary
                                )
                                Text(
                                    formatCurrencyMoney(spent, budget.currencyCode) + " من " + formatCurrencyMoney(budget.limitMinor, budget.currencyCode) +
                                        when (alert) {
                                            "OVER_LIMIT" -> " • تجاوزت الميزانية"
                                            "NEAR_LIMIT" -> " • اقتربت من الحد (80% أو أكثر)"
                                            else -> ""
                                        },
                                    color = if (alert == "OVER_LIMIT") FinanceColors.Expense else FinanceColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { db.budgetDao().delete(budget.id) }
                                            .onSuccess { status = "تم حذف الميزانية." }
                                            .onFailure { status = "تعذر حذف الميزانية." }
                                    }
                                }) { Text("حذف الميزانية") }
                            }
                        }
                    }
                }
            }
        }
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
