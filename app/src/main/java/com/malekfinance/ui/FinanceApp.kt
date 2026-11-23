package com.malekfinance.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.malekfinance.FinanceDb
import com.malekfinance.FinanceTransaction
import com.malekfinance.FinanceWallet
import com.malekfinance.FinanceTransfer
import com.malekfinance.FinanceReportAnalytics
import com.malekfinance.FinanceReportRange
import com.malekfinance.FinanceReportExporter
import com.malekfinance.FinanceBackupManager
import com.malekfinance.voice.VoiceTransactionParser
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.malekfinance.voice.VoiceCommandType
import com.malekfinance.voice.VoiceQueryEngine
import com.malekfinance.voice.VoiceRecognitionIntentFactory
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
    val savingsGoals by db.savingsGoalDao().observeAll().collectAsState(initial = emptyList())
    val savingsEntries by db.savingsEntryDao().observeAll().collectAsState(initial = emptyList())
    val recurringIncomeRules by db.recurringIncomeRuleDao().observeAll().collectAsState(initial = emptyList())
    val recurringIncomeEntries by db.recurringIncomeEntryDao().observeAll().collectAsState(initial = emptyList())
val recurringExpenseRules by db.recurringExpenseRuleDao().observeAll().collectAsState(initial = emptyList())
    val recurringExpenseEntries by db.recurringExpenseEntryDao().observeAll().collectAsState(initial = emptyList())
    val bills by db.billDao().observeAll().collectAsState(initial = emptyList())
    val billPayments by db.billPaymentDao().observeAll().collectAsState(initial = emptyList())
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
                            Text("MK Finance", fontWeight = FontWeight.Bold)
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
                    FinanceSection.REPORTS -> ReportsLandingScreen(db, transactions, wallets, Modifier.padding(padding))
                    FinanceSection.MONEY -> MoneyLandingScreen(db, wallets, transactions, transfers, categories, budgets, debts, debtPayments, savingsGoals, savingsEntries, recurringIncomeRules, recurringIncomeEntries, recurringExpenseRules, recurringExpenseEntries, bills, billPayments, Modifier.padding(padding))
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
                            Text(row.category + " • " + when (row.kind) { "INCOME" -> "دخل"; "EXPENSE" -> "مصروف"; "DEBT_RECEIVED" -> "استلام قرض"; "DEBT_ISSUED" -> "إقراض سلفة"; "SAVINGS_CONTRIBUTION" -> "مساهمة ادخار"; "SAVINGS_WITHDRAWAL" -> "سحب ادخار"; else -> "عملية" }, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            (if (row.kind == "INCOME" || row.kind == "DEBT_RECEIVED" || row.kind == "SAVINGS_WITHDRAWAL") "+" else "−") + formatCurrencyMoney(row.amountMinor, row.currencyCode),
                            color = if (row.kind == "INCOME" || row.kind == "DEBT_RECEIVED" || row.kind == "SAVINGS_WITHDRAWAL") FinanceColors.Income else FinanceColors.Expense,
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
                                    Text(row.category + " • " + when (row.kind) { "INCOME" -> "دخل"; "EXPENSE" -> "مصروف"; "DEBT_RECEIVED" -> "استلام قرض"; "DEBT_ISSUED" -> "إقراض سلفة"; "SAVINGS_CONTRIBUTION" -> "مساهمة ادخار"; "SAVINGS_WITHDRAWAL" -> "سحب ادخار"; else -> "عملية" }, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        (if (row.kind == "INCOME" || row.kind == "DEBT_RECEIVED" || row.kind == "SAVINGS_WITHDRAWAL") "+" else "−") + formatCurrencyMoney(row.amountMinor, row.currencyCode),
                                        color = if (row.kind == "INCOME" || row.kind == "DEBT_RECEIVED" || row.kind == "SAVINGS_WITHDRAWAL") FinanceColors.Income else FinanceColors.Expense,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (row.debtId == null && row.savingsGoalId == null && row.recurringIncomeRuleId == null && row.recurringExpenseRuleId == null && row.billId == null) {
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
                                    Text(
                                        when { row.debtId != null -> "مرتبطة بدين؛ تُدار من قسم الديون"; row.savingsGoalId != null -> "مرتبطة بهدف ادخار؛ تُدار من قسم الادخار"; row.billId != null -> "فاتورة مدفوعة؛ تُدار من قسم الفواتير"; row.recurringExpenseRuleId != null -> "مصروف دوري؛ يُدار من قسم المصروفات المتكررة"; else -> "دخل دوري؛ يُدار من قسم الرواتب" },
                                        color = FinanceColors.TextSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
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
    var isSavingDraft by remember { mutableStateOf(false) }
    var draftCreatedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var hasTransferDraft by remember { mutableStateOf(false) }
    var transferAmountText by remember { mutableStateOf("") }
    var transferNote by remember { mutableStateOf("") }
    var transferFromWalletId by remember { mutableStateOf<Long?>(null) }
    var transferToWalletId by remember { mutableStateOf<Long?>(null) }
    var isTransferSaving by remember { mutableStateOf(false) }
    var hasDebtDraft by remember { mutableStateOf(false) }
    var debtPersonDraft by remember { mutableStateOf("") }
    var debtDescriptionDraft by remember { mutableStateOf("") }
    var debtAmountDraft by remember { mutableStateOf("") }
    var debtTypeDraft by remember { mutableStateOf("OWED_BY_ME") }
    var debtWalletDraftId by remember { mutableStateOf<Long?>(null) }
    var debtRecordCashMovement by remember { mutableStateOf(true) }
    var isDebtSaving by remember { mutableStateOf(false) }
    var hasDebtPaymentDraft by remember { mutableStateOf(false) }
    var debtPaymentDirection by remember { mutableStateOf("OWED_BY_ME") }
    var debtPaymentAmountDraft by remember { mutableStateOf("") }
    var debtPaymentDebtId by remember { mutableStateOf<Long?>(null) }
    var isDebtPaymentSaving by remember { mutableStateOf(false) }
    var hasSavingsDraft by remember { mutableStateOf(false) }
    var savingsAmountDraft by remember { mutableStateOf("") }
    var savingsGoalDraftId by remember { mutableStateOf<Long?>(null) }
    var savingsDraftType by remember { mutableStateOf("CONTRIBUTION") }
    var isSavingsSaving by remember { mutableStateOf(false) }
    var hasBillDraft by remember { mutableStateOf(false) }
    var billDraftId by remember { mutableStateOf<Long?>(null) }
    var isBillSaving by remember { mutableStateOf(false) }
    val voiceDebts by db.debtDao().observeAll().collectAsState(initial = emptyList())
    val voiceDebtPayments by db.debtPaymentDao().observeAll().collectAsState(initial = emptyList())
    val voiceBills by db.billDao().observeAll().collectAsState(initial = emptyList())
    val voiceSavingsGoals by db.savingsGoalDao().observeAll().collectAsState(initial = emptyList())
    var selectedWalletId by remember { mutableStateOf<Long?>(null) }
    val selectedWallet = wallets.firstOrNull { it.id == selectedWalletId } ?: wallets.firstOrNull()
    val compatibleCategories = activeCategories.filter {
        kind == null || com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, kind!!)
    }

    LaunchedEffect(activeCategories, kind) {
        if (activeCategories.isNotEmpty() && activeCategories.none {
                it.name == category && (kind == null || com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, kind!!))
            }) {
            category = activeCategories.firstOrNull {
                it.name == "عام" && (kind == null || com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, kind!!))
            }?.name ?: activeCategories.firstOrNull {
                kind == null || com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, kind!!)
            }?.name ?: "عام"
        }
    }

    LaunchedEffect(wallets) {
        if (selectedWalletId == null && wallets.isNotEmpty()) selectedWalletId = wallets.first().id
    }

    fun clearDebtPaymentDraft() {
        hasDebtPaymentDraft = false
        debtPaymentAmountDraft = ""
        debtPaymentDebtId = null
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val alternatives = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        val confidence = result.data?.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES)
        val bestIndex = if (confidence != null && confidence.size == alternatives.size) confidence.indices.maxByOrNull { confidence[it] } ?: 0 else 0
        val spoken = alternatives.getOrNull(bestIndex)
        if (spoken.isNullOrBlank()) {
            status = "لم يتم التقاط كلام واضح. يمكنك إعادة المحاولة أو الكتابة يدوياً."
        } else {
            val parsed = VoiceTransactionParser.parse(spoken)
            transcript = spoken
            if (parsed.command == VoiceCommandType.CANCEL_DRAFT) {
                transcript = ""
                amount = ""
                description = ""
                kind = null
                category = "عام"
                hasDraft = false
                hasTransferDraft = false
                hasDebtDraft = false
                clearDebtPaymentDraft()
                hasSavingsDraft = false
                savingsAmountDraft = ""
                savingsGoalDraftId = null
                transferFromWalletId = null
                transferToWalletId = null
                debtWalletDraftId = null
                debtPersonDraft = ""
                debtDescriptionDraft = ""
                debtAmountDraft = ""
                transferAmountText = ""
                transferNote = ""
                status = "تم إلغاء المسودة بأمرك الصوتي دون حفظ أي عملية."
            } else if (parsed.command == VoiceCommandType.CORRECT_AMOUNT) {
                val corrected = parsed.amount?.let { raw ->
                    runCatching {
                        normalizeFinanceAmount(raw).replace('٫', '.').replace(',', '.').toBigDecimalOrNull()
                    }.getOrNull()
                }
                if (corrected == null || corrected <= java.math.BigDecimal.ZERO) {
                    status = "لم أتعرف على مبلغ تصحيح صحيح أكبر من صفر."
                } else {
                    val normalizedCorrection = corrected.stripTrailingZeros().toPlainString()
                    when {
                        hasDraft -> amount = normalizedCorrection
                        hasTransferDraft -> transferAmountText = normalizedCorrection
                        hasDebtDraft -> debtAmountDraft = normalizedCorrection
                        hasDebtPaymentDraft -> debtPaymentAmountDraft = normalizedCorrection
                        hasSavingsDraft -> savingsAmountDraft = normalizedCorrection
                        hasBillDraft -> status = "مبلغ الفاتورة محفوظ في سجلها. عدّل الفاتورة من قسم الفواتير قبل الدفع."
                        else -> status = "لا توجد مسودة حالية لتصحيح مبلغها. ابدأ بإدخال عملية أولاً."
                    }
                    if (!hasBillDraft && (hasDraft || hasTransferDraft || hasDebtDraft || hasDebtPaymentDraft || hasSavingsDraft)) {
                        status = "تم تحديث مبلغ المسودة إلى " + normalizedCorrection + ". راجع بقية التفاصيل قبل التأكيد."
                    }
                }
            } else if (parsed.command == VoiceCommandType.DEBT_PAYMENT_DRAFT) {
                hasDraft = false
                hasTransferDraft = false
                hasDebtDraft = false
                hasSavingsDraft = false
                clearDebtPaymentDraft()
                hasBillDraft = false
                amount = ""
                description = ""
                kind = null
                hasDebtPaymentDraft = true
                debtPaymentDirection = parsed.debtPaymentDirection ?: "OWED_BY_ME"
                debtPaymentAmountDraft = parsed.amount.orEmpty()
                debtPaymentDebtId = voiceDebts.firstOrNull { debt ->
                    debt.debtType == debtPaymentDirection && spoken.contains(debt.personName, ignoreCase = true) &&
                        com.malekfinance.FinanceDebtPolicy.outstanding(debt.principalMinor, voiceDebtPayments.filter { it.debtId == debt.id }).signum() > 0
                }?.id
                status = "اختر الدين الصحيح وراجع مبلغ الدفعة. لن يتم السداد أو التحصيل إلا بعد التأكيد."
            } else if (parsed.command == VoiceCommandType.BILL_PAYMENT_DRAFT) {
                hasDraft = false
                hasTransferDraft = false
                hasDebtDraft = false
                hasSavingsDraft = false
                amount = ""
                clearDebtPaymentDraft()
                description = ""
                kind = null
                hasBillDraft = true
                billDraftId = voiceBills.firstOrNull {
                    it.isActive && com.malekfinance.FinanceBillPolicy.isDue(it.nextDueAt, System.currentTimeMillis()) &&
                        spoken.contains(it.title, ignoreCase = true)
                }?.id
                status = "اختر الفاتورة وراجع المبلغ وموعد الاستحقاق. لن يتم تسجيل الدفع إلا بعد التأكيد."
            } else if (parsed.command == VoiceCommandType.SAVINGS_CONTRIBUTION_DRAFT ||
                parsed.command == VoiceCommandType.SAVINGS_WITHDRAWAL_DRAFT) {
                hasDraft = false
                hasTransferDraft = false
                hasDebtDraft = false
                transferAmountText = ""
                clearDebtPaymentDraft()
                transferNote = ""
                transferFromWalletId = null
                transferToWalletId = null
                debtPersonDraft = ""
                debtDescriptionDraft = ""
                debtAmountDraft = ""
                debtWalletDraftId = null
                amount = ""
                description = ""
                kind = null
                hasSavingsDraft = true
                savingsDraftType = if (parsed.command == VoiceCommandType.SAVINGS_CONTRIBUTION_DRAFT) "CONTRIBUTION" else "WITHDRAWAL"
                savingsAmountDraft = parsed.amount.orEmpty()
                savingsGoalDraftId = voiceSavingsGoals.firstOrNull { spoken.contains(it.name, ignoreCase = true) }?.id
                status = "راجع هدف الادخار والمبلغ. لن يتم تسجيل المساهمة أو السحب إلا بعد التأكيد."
            } else if (parsed.command == VoiceCommandType.DEBT_CREATE_DRAFT) {
                hasDraft = false
                hasTransferDraft = false
                hasSavingsDraft = false
                clearDebtPaymentDraft()
                savingsAmountDraft = ""
                savingsGoalDraftId = null
                transferAmountText = ""
                transferNote = ""
                transferFromWalletId = null
                transferToWalletId = null
                amount = ""
                description = ""
                kind = null
                hasDebtDraft = true
                debtPersonDraft = parsed.debtPersonName.orEmpty()
                debtDescriptionDraft = parsed.description
                debtAmountDraft = parsed.amount.orEmpty()
                debtTypeDraft = parsed.debtType ?: "OWED_BY_ME"
                debtWalletDraftId = selectedWallet?.id
                debtRecordCashMovement = true
                status = "راجع اسم الشخص واتجاه الدين والمبلغ والمحفظة. لن يُسجّل الدين إلا بعد التأكيد."
            } else if (parsed.command == VoiceCommandType.TRANSFER_DRAFT) {
                hasDraft = false
                clearDebtPaymentDraft()
                amount = ""
                description = ""
                kind = null
                hasDebtDraft = false
                hasSavingsDraft = false
                savingsAmountDraft = ""
                savingsGoalDraftId = null
                debtPersonDraft = ""
                debtDescriptionDraft = ""
                debtAmountDraft = ""
                hasTransferDraft = true
                transferAmountText = parsed.amount.orEmpty()
                transferNote = spoken
                val mentionedWallets = wallets.filter { spoken.contains(it.name, ignoreCase = true) }
                transferFromWalletId = mentionedWallets.getOrNull(0)?.id
                transferToWalletId = mentionedWallets.getOrNull(1)?.id
                status = "راجع محفظة المصدر والوجهة والمبلغ. لن يتم التحويل إلا بعد ضغط زر التأكيد."
            } else if (parsed.command != VoiceCommandType.TRANSACTION) {
                // Read-only voice queries must not silently discard an unconfirmed draft.
                scope.launch {
                    val wallet = selectedWallet
                    if (wallet == null) {
                        status = "أنشئ محفظة أولاً حتى أستطيع قراءة بياناتك المالية."
                    } else {
                        runCatching {
                            val walletTransactions = db.dao().forWallet(wallet.id)
                            val transfers = db.transferDao().forWallet(wallet.id)
                            when (parsed.command) {
                                VoiceCommandType.BALANCE_QUERY -> {
                                    val balance = com.malekfinance.FinanceLedger.calculateBalance(wallet, walletTransactions, transfers)
                                    val major = balance.divide(java.math.BigInteger.valueOf(100))
                                    val minor = balance.remainder(java.math.BigInteger.valueOf(100)).abs()
                                    "رصيد " + wallet.name + ": " + String.format(java.util.Locale.ROOT, "%s.%02d %s", major.toString(), minor.toInt(), wallet.currencyCode)
                                }
                                VoiceCommandType.SPENDING_QUERY -> {
                                    val result = VoiceQueryEngine.calculate(
                                        walletTransactions, "EXPENSE", wallet.id, wallet.currencyCode,
                                        parsed.queryPeriod, category = parsed.category.takeIf { it != "عام" }
                                    )
                                    "مصروفات " + (parsed.category.takeIf { it != "عام" }?.let { "تصنيف " + it + " في " } ?: "في ") +
                                        wallet.name + " خلال الفترة المطلوبة: " + VoiceQueryEngine.formatAmount(result)
                                }
                                VoiceCommandType.INCOME_QUERY -> {
                                    val result = VoiceQueryEngine.calculate(
                                        walletTransactions, "INCOME", wallet.id, wallet.currencyCode,
                                        parsed.queryPeriod, category = parsed.category.takeIf { it != "عام" }
                                    )
                                    "الدخل " + (parsed.category.takeIf { it != "عام" }?.let { "من تصنيف " + it + " في " } ?: "في ") +
                                        wallet.name + " خلال الفترة المطلوبة: " + VoiceQueryEngine.formatAmount(result)
                                }
                                VoiceCommandType.SUMMARY_QUERY -> {
                                    val income = VoiceQueryEngine.calculate(walletTransactions, "INCOME", wallet.id, wallet.currencyCode, parsed.queryPeriod)
                                    val expense = VoiceQueryEngine.calculate(walletTransactions, "EXPENSE", wallet.id, wallet.currencyCode, parsed.queryPeriod)
                                    "ملخص " + wallet.name + ": الدخل " + VoiceQueryEngine.formatAmount(income) + "، المصروف " + VoiceQueryEngine.formatAmount(expense)
                                }
                                VoiceCommandType.RECENT_TRANSACTIONS_QUERY -> {
                                    val transactionItems = walletTransactions.map { item ->
                                        item.createdAt to run {
                                            val kindLabel = when (item.kind) {
                                                "INCOME" -> "دخل"
                                                "EXPENSE" -> "مصروف"
                                                "DEBT_ISSUED" -> "إقراض"
                                                "DEBT_RECEIVED" -> "قرض مستلم"
                                                "SAVINGS_CONTRIBUTION" -> "ادخار"
                                                "SAVINGS_WITHDRAWAL" -> "سحب من الادخار"
                                                else -> item.kind
                                            }
                                            kindLabel + " " + java.math.BigDecimal.valueOf(item.amountMinor, 2).toPlainString() +
                                                " " + item.currencyCode + " — " + item.note
                                        }
                                    }
                                    val transferItems = transfers.map { transfer ->
                                        transfer.createdAt to run {
                                            val outgoing = transfer.fromWalletId == wallet.id
                                            val otherWalletId = if (outgoing) transfer.toWalletId else transfer.fromWalletId
                                            val otherWalletName = wallets.firstOrNull { it.id == otherWalletId }?.name ?: "محفظة أخرى"
                                            (if (outgoing) "تحويل صادر إلى " else "تحويل وارد من ") + otherWalletName +
                                                " بمبلغ " + java.math.BigDecimal.valueOf(transfer.amountMinor, 2).toPlainString() +
                                                " " + transfer.currencyCode
                                        }
                                    }
                                    val recent = (transactionItems + transferItems).sortedByDescending { it.first }.take(5)
                                    if (recent.isEmpty()) "لا توجد عمليات مسجلة في " + wallet.name + "."
                                    else "آخر العمليات في " + wallet.name + ": " + recent.joinToString("؛ ") { it.second }
                                }
                                VoiceCommandType.DEBT_OWED_BY_ME_QUERY, VoiceCommandType.DEBT_OWED_TO_ME_QUERY -> {
                                    val debtType = if (parsed.command == VoiceCommandType.DEBT_OWED_BY_ME_QUERY) "OWED_BY_ME" else "OWED_TO_ME"
                                    val allDebts = db.debtDao().observeAll().first()
                                    val allPayments = db.debtPaymentDao().observeAll().first()
                                    val outstanding = allDebts.filter {
                                        it.walletId == wallet.id && it.currencyCode == wallet.currencyCode && it.debtType == debtType &&
                                            (parsed.debtPersonName == null || it.personName.contains(parsed.debtPersonName, ignoreCase = true))
                                    }.fold(java.math.BigInteger.ZERO) { sum, debt ->
                                        sum.add(com.malekfinance.FinanceDebtPolicy.outstanding(
                                            debt.principalMinor, allPayments.filter { it.debtId == debt.id }
                                        ))
                                    }
                                    val amountText = java.math.BigDecimal(outstanding, 2).toPlainString() + " " + wallet.currencyCode
                                    if (debtType == "OWED_BY_ME") "المتبقي عليك من الديون" + (parsed.debtPersonName?.let { " لـ " + it } ?: "") + " في " + wallet.name + ": " + amountText
                                    else "المبالغ المستحقة لك" + (parsed.debtPersonName?.let { " لدى " + it } ?: "") + " في " + wallet.name + ": " + amountText
                                }
                                VoiceCommandType.SAVINGS_BALANCE_QUERY -> {
                                    val goals = db.savingsGoalDao().observeAll().first()
                                    val entries = db.savingsEntryDao().observeAll().first()
                                    val saved = goals.filter { it.walletId == wallet.id && it.currencyCode == wallet.currencyCode }
                                        .fold(java.math.BigInteger.ZERO) { sum, goal ->
                                            sum.add(com.malekfinance.FinanceSavingsPolicy.balance(entries.filter { it.goalId == goal.id }).max(java.math.BigInteger.ZERO))
                                        }
                                    "إجمالي مدخراتك في " + wallet.name + ": " + java.math.BigDecimal(saved, 2).toPlainString() + " " + wallet.currencyCode
                                }
                                VoiceCommandType.UPCOMING_BILLS_QUERY -> {
                                    val bills = db.billDao().observeAll().first()
                                        .filter { it.isActive && it.walletId == wallet.id && it.currencyCode == wallet.currencyCode }
                                        .sortedBy { it.nextDueAt }
                                        .take(5)
                                    if (bills.isEmpty()) "لا توجد فواتير نشطة في " + wallet.name + "."
                                    else "الفواتير القادمة في " + wallet.name + ": " + bills.joinToString("؛ ") {
                                        it.title + "، " + java.math.BigDecimal.valueOf(it.amountMinor, 2).toPlainString() + " " +
                                            it.currencyCode + "، الاستحقاق " + com.malekfinance.FinanceBillPolicy.formatLocalDate(it.nextDueAt)
                                    }
                                }
                                VoiceCommandType.BUDGET_STATUS_QUERY -> {
                                    val window = com.malekfinance.FinanceBudgetPolicy.monthWindow(System.currentTimeMillis())
                                    val budgets = db.budgetDao().observeAll().first()
                                        .filter { it.currencyCode == wallet.currencyCode && it.periodKey == window.periodKey }
                                    val allTransactions = db.dao().observe().first()
                                    if (budgets.isEmpty()) "لا توجد ميزانيات شهرية نشطة بعملة " + wallet.currencyCode + "."
                                    else budgets.joinToString("؛ ") { budget ->
                                        val spent = com.malekfinance.FinanceBudgetPolicy.calculateSpent(
                                            budget, allTransactions, window.startMillis, window.endExclusiveMillis
                                        )
                                        val remaining = java.math.BigInteger.valueOf(budget.limitMinor).subtract(spent)
                                        budget.categoryName + ": صُرف " + java.math.BigDecimal(spent, 2).toPlainString() +
                                            "، المتبقي " + java.math.BigDecimal(remaining, 2).toPlainString() + " " + wallet.currencyCode
                                    }
                                }
                                VoiceCommandType.TRANSACTION, VoiceCommandType.CANCEL_DRAFT, VoiceCommandType.CORRECT_AMOUNT, VoiceCommandType.TRANSFER_DRAFT, VoiceCommandType.DEBT_CREATE_DRAFT, VoiceCommandType.DEBT_PAYMENT_DRAFT, VoiceCommandType.SAVINGS_CONTRIBUTION_DRAFT, VoiceCommandType.SAVINGS_WITHDRAWAL_DRAFT, VoiceCommandType.BILL_PAYMENT_DRAFT -> ""
                            }
                        }.onSuccess { status = it }
                            .onFailure { status = "تعذر قراءة الملخص المالي من قاعدة البيانات. حاول مرة أخرى." }
                    }
                }
            } else {
                // A newly parsed transaction replaces any older, unconfirmed special draft.
                clearDebtPaymentDraft()
                hasTransferDraft = false
                hasDebtDraft = false
                hasDebtPaymentDraft = false
                debtPaymentAmountDraft = ""
                debtPaymentDebtId = null
                hasSavingsDraft = false
                savingsAmountDraft = ""
                savingsGoalDraftId = null
                transferAmountText = ""
                transferNote = ""
                transferFromWalletId = null
                transferToWalletId = null
                debtPersonDraft = ""
                debtDescriptionDraft = ""
                debtAmountDraft = ""
                debtWalletDraftId = null
                amount = parsed.amount.orEmpty()
                description = parsed.description
                kind = parsed.kind
                draftCreatedAt = parsed.relativeDayOffset?.let { offset ->
                    java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, offset) }.timeInMillis
                } ?: System.currentTimeMillis()
                category = activeCategories.firstOrNull {
                    it.name == parsed.category && (parsed.kind == null || com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, parsed.kind))
                }?.name ?: activeCategories.firstOrNull {
                    it.name == "عام" && (parsed.kind == null || com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, parsed.kind))
                }?.name ?: activeCategories.firstOrNull {
                    parsed.kind == null || com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, parsed.kind)
                }?.name ?: "عام"
                hasDraft = true
                status = when {
                    parsed.amount == null && parsed.kind == null -> "لم أتعرف على المبلغ أو نوع العملية. أكمل البيانات وحدد دخل أو مصروف."
                    parsed.amount == null -> "حدد المبلغ قبل الحفظ."
                    parsed.kind == null -> "لم يتضح هل هي عملية دخل أم مصروف. اختر النوع بنفسك."
                    else -> "راجع المبلغ والنوع والوصف والتصنيف. لن تُحفظ العملية قبل تأكيدك."
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!launchFinanceRecognition(context, speechLauncher)) status = "خدمة التعرف الصوتي غير متاحة على هذا الجهاز. يمكنك الكتابة يدوياً."
        } else status = "إذن الميكروفون مرفوض. يمكنك إدخال العملية يدوياً."
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (!launchFinanceRecognition(context, speechLauncher)) status = "خدمة التعرف الصوتي غير متاحة على هذا الجهاز. يمكنك الكتابة يدوياً."
        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun cancelVoiceDraft() {
        if (isSavingDraft || isTransferSaving || isDebtSaving || isDebtPaymentSaving || isSavingsSaving || isBillSaving) return
        transcript = ""
        amount = ""
        description = ""
        kind = null
        category = "عام"
        draftCreatedAt = System.currentTimeMillis()
        hasDraft = false
        hasTransferDraft = false
        hasDebtDraft = false
        clearDebtPaymentDraft()
        hasSavingsDraft = false
        savingsAmountDraft = ""
        savingsGoalDraftId = null
        hasBillDraft = false
        billDraftId = null
        transferAmountText = ""
        transferNote = ""
        debtPersonDraft = ""
        debtDescriptionDraft = ""
        debtAmountDraft = ""
        debtWalletDraftId = null
        transferFromWalletId = null
        transferToWalletId = null
        debtRecordCashMovement = true
        status = "تم إلغاء المسودة دون حفظ أي عملية."
    }

    fun saveReviewedDraft() {
        if (isSavingDraft || hasTransferDraft || hasDebtDraft || hasDebtPaymentDraft || hasSavingsDraft || hasBillDraft) return
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
        val resolvedCategory = activeCategories.firstOrNull {
            it.name == category && com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, selectedKind)
        }?.name ?: activeCategories.firstOrNull {
            it.name == "عام" && com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, selectedKind)
        }?.name ?: activeCategories.firstOrNull {
            com.malekfinance.FinanceCategoryPolicy.isCompatible(it.kind, selectedKind)
        }?.name ?: "عام"
        val wallet = selectedWallet
        if (wallet == null) {
            status = "أنشئ محفظة أولاً من قسم مالي."
            return
        }
        isSavingDraft = true
        scope.launch {
            runCatching {
                db.dao().insert(
                    FinanceTransaction(
                        amountMinor = minor,
                        kind = selectedKind,
                        note = description.trim(),
                        category = resolvedCategory,
                        walletId = wallet.id,
                        currencyCode = wallet.currencyCode,
                        createdAt = draftCreatedAt
                    )
                )
            }.onSuccess {
                transcript = ""
                amount = ""
                description = ""
                kind = null
                category = "عام"
                draftCreatedAt = System.currentTimeMillis()
                hasDraft = false
                status = "تم حفظ العملية في ${wallet.name}."
            }.onFailure {
                status = "تعذر حفظ العملية. بيانات المسودة ما زالت موجودة؛ حاول مجدداً."
            }
            isSavingDraft = false
        }
    }

    fun confirmVoiceTransfer() {
        if (isTransferSaving) return
        val source = wallets.firstOrNull { it.id == transferFromWalletId }
        val destination = wallets.firstOrNull { it.id == transferToWalletId }
        val minor = runCatching {
            normalizeFinanceAmount(transferAmountText).replace('٫', '.').replace(',', '.').toBigDecimalOrNull()
                ?.movePointRight(2)?.longValueExact()
        }.getOrNull()
        if (source == null || destination == null) {
            status = "اختر محفظة مصدر ومحفظة وجهة قبل التأكيد."
            return
        }
        if (source.id == destination.id) {
            status = "يجب أن تكون محفظة الوجهة مختلفة عن محفظة المصدر."
            return
        }
        if (minor == null || minor <= 0L) {
            status = "أدخل مبلغ تحويل صحيحاً أكبر من صفر."
            return
        }
        isTransferSaving = true
        scope.launch {
            runCatching {
                com.malekfinance.FinanceTransferOperations.transfer(
                    db, source.id, destination.id, minor, transferNote
                )
            }.onSuccess {
                hasTransferDraft = false
                transferAmountText = ""
                transferNote = ""
                transferFromWalletId = null
                transferToWalletId = null
                transcript = ""
                amount = ""
                description = ""
                kind = null
                hasDraft = false
                status = "تم التحويل بنجاح من " + source.name + " إلى " + destination.name + "."
            }.onFailure {
                status = if (it is com.malekfinance.InsufficientWalletFundsException) {
                    "الرصيد المتاح في محفظة المصدر غير كافٍ لهذا التحويل."
                } else {
                    "تعذر تنفيذ التحويل: تحقق من العملة والمبلغ والمحفظتين."
                }
            }
            isTransferSaving = false
        }
    }

    fun confirmVoiceDebt() {
        if (isDebtSaving) return
        val person = debtPersonDraft.trim()
        val wallet = wallets.firstOrNull { it.id == debtWalletDraftId }
        val minor = runCatching {
            normalizeFinanceAmount(debtAmountDraft).replace('٫', '.').replace(',', '.').toBigDecimalOrNull()
                ?.movePointRight(2)?.longValueExact()
        }.getOrNull()
        when {
            person.isBlank() -> status = "اكتب اسم الشخص أو الجهة المرتبطة بالدين."
            wallet == null -> status = "اختر محفظة لتحديد عملة الدين."
            minor == null || minor <= 0L -> status = "أدخل مبلغ الدين صحيحاً وأكبر من صفر."
            else -> {
                isDebtSaving = true
                scope.launch {
                    runCatching {
                        com.malekfinance.FinanceDebtOperations.create(
                            db = db,
                            personName = person,
                            description = debtDescriptionDraft,
                            debtType = debtTypeDraft,
                            principalMinor = minor,
                            walletId = wallet.id,
                            recordCashMovement = debtRecordCashMovement
                        )
                    }.onSuccess {
                        hasDebtDraft = false
                        debtPersonDraft = ""
                        debtDescriptionDraft = ""
                        debtAmountDraft = ""
                        debtWalletDraftId = null
                        transcript = ""
                        status = if (debtRecordCashMovement) {
                            "تم تسجيل الدين وحركة المحفظة معاً."
                        } else {
                            "تم تسجيل الدين دون تعديل رصيد المحفظة."
                        }
                    }.onFailure {
                        status = if (it is com.malekfinance.InsufficientWalletFundsException) {
                            "رصيد المحفظة لا يكفي لإقراض هذا المبلغ."
                        } else {
                            "تعذر تسجيل الدين؛ لم يتم حفظ أي جزء من العملية."
                        }
                    }
                    isDebtSaving = false
                }
            }
        }
    }

    fun confirmVoiceDebtPayment() {
        if (isDebtPaymentSaving) return
        val debt = voiceDebts.firstOrNull { it.id == debtPaymentDebtId && it.debtType == debtPaymentDirection }
        val minor = runCatching {
            normalizeFinanceAmount(debtPaymentAmountDraft).replace('٫', '.').replace(',', '.').toBigDecimalOrNull()
                ?.movePointRight(2)?.longValueExact()
        }.getOrNull()
        val outstanding = debt?.let {
            com.malekfinance.FinanceDebtPolicy.outstanding(
                it.principalMinor, voiceDebtPayments.filter { payment -> payment.debtId == it.id }
            )
        }
        when {
            debt == null -> status = "اختر ديناً قائماً من القائمة قبل التأكيد."
            minor == null || minor <= 0L -> status = "أدخل مبلغ دفعة صحيحاً أكبر من صفر."
            outstanding == null || java.math.BigInteger.valueOf(minor) > outstanding -> status = "مبلغ الدفعة أكبر من المتبقي من الدين."
            else -> {
                isDebtPaymentSaving = true
                scope.launch {
                    runCatching { com.malekfinance.FinanceDebtPaymentOperations.applyPayment(db, debt.id, minor) }
                        .onSuccess {
                            hasDebtPaymentDraft = false
                            debtPaymentAmountDraft = ""
                            debtPaymentDebtId = null
                            transcript = ""
                            status = if (debtPaymentDirection == "OWED_BY_ME") "تم تسجيل سداد الدفعة وربطها بالمحفظة والدين." else "تم تسجيل تحصيل الدفعة وربطها بالمحفظة والدين."
                        }
                        .onFailure {
                            status = if (it is com.malekfinance.InsufficientWalletFundsException) "رصيد المحفظة لا يكفي لإتمام سداد الدين." else "تعذر تسجيل الدفعة؛ لم يُحفظ أي جزء منها. تحقق من المتبقي وحاول مجدداً."
                        }
                    isDebtPaymentSaving = false
                }
            }
        }
    }

    fun confirmVoiceSavings() {
        if (isSavingsSaving) return
        val goal = voiceSavingsGoals.firstOrNull { it.id == savingsGoalDraftId }
        val minor = runCatching {
            normalizeFinanceAmount(savingsAmountDraft).replace('٫', '.').replace(',', '.').toBigDecimalOrNull()
                ?.movePointRight(2)?.longValueExact()
        }.getOrNull()
        if (goal == null) {
            status = "اختر هدف الادخار المرتبط بهذه العملية."
            return
        }
        if (minor == null || minor <= 0L) {
            status = "أدخل مبلغاً صحيحاً أكبر من صفر."
            return
        }
        isSavingsSaving = true
        scope.launch {
            runCatching {
                if (savingsDraftType == "CONTRIBUTION") {
                    com.malekfinance.FinanceSavingsOperations.contribute(db, goal.id, minor)
                } else {
                    com.malekfinance.FinanceSavingsOperations.withdraw(db, goal.id, minor)
                }
            }.onSuccess {
                hasSavingsDraft = false
                savingsAmountDraft = ""
                savingsGoalDraftId = null
                transcript = ""
                status = if (savingsDraftType == "CONTRIBUTION") {
                    "تمت إضافة المساهمة إلى هدف " + goal.name + " وتحديث المحفظة."
                } else {
                    "تم تسجيل السحب من هدف " + goal.name + " وإعادة المبلغ للمحفظة."
                }
            }.onFailure {
                status = when {
                    it is com.malekfinance.InsufficientWalletFundsException -> "رصيد المحفظة لا يكفي لهذه المساهمة."
                    savingsDraftType == "CONTRIBUTION" -> "المساهمة تتجاوز المتبقي من الهدف أو تعذر حفظها."
                    else -> "السحب يتجاوز رصيد الادخار المتاح أو تعذر حفظه."
                }
            }
            isSavingsSaving = false
        }
    }

    fun confirmVoiceBillPayment() {
        if (isBillSaving) return
        val bill = voiceBills.firstOrNull { it.id == billDraftId }
        if (bill == null) {
            status = "اختر فاتورة نشطة قبل التأكيد."
            return
        }
        if (!bill.isActive) {
            status = "هذه الفاتورة غير نشطة."
            return
        }
        if (!com.malekfinance.FinanceBillPolicy.isDue(bill.nextDueAt, System.currentTimeMillis())) {
            status = "لم يحِن موعد استحقاق هذه الفاتورة بعد."
            return
        }
        isBillSaving = true
        scope.launch {
            runCatching { com.malekfinance.FinanceBillOperations.pay(db, bill.id) }
                .onSuccess {
                    hasBillDraft = false
                    billDraftId = null
                    transcript = ""
                    status = "تم تأكيد دفع فاتورة " + bill.title + " وتسجيل المصروف."
                }
                .onFailure {
                    status = if (it is com.malekfinance.InsufficientWalletFundsException) {
                        "رصيد المحفظة لا يكفي لدفع هذه الفاتورة."
                    } else {
                        "تعذر الدفع؛ ربما سُددت هذه الدورة بالفعل أو حدث خطأ. لم يتم اعتماد دفع جزئي."
                    }
                }
            isBillSaving = false
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
                    Text(
                        "قد تعالج خدمة التعرف الموجودة على جهازك الصوت وقد تحتاج إلى الإنترنت. لا يرسل التطبيق سجل معاملاتك المالية إلى خدمة التعرف.",
                        color = FinanceColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
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
        if (hasDebtPaymentDraft) {
            item {
                val eligibleDebts = voiceDebts.filter { debt ->
                    debt.debtType == debtPaymentDirection && com.malekfinance.FinanceDebtPolicy.outstanding(
                        debt.principalMinor, voiceDebtPayments.filter { it.debtId == debt.id }
                    ).signum() > 0
                }
                Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (debtPaymentDirection == "OWED_BY_ME") "مراجعة سداد الدين صوتياً" else "مراجعة تحصيل الدين صوتياً", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = debtPaymentAmountDraft, onValueChange = { debtPaymentAmountDraft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("مبلغ الدفعة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !isDebtPaymentSaving)
                        Text("اختر الدين المرتبط بالدفعة", color = FinanceColors.TextSecondary)
                        LazyColumn(modifier = Modifier.heightIn(max = 190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(eligibleDebts, key = { it.id }) { debt ->
                                val outstanding = com.malekfinance.FinanceDebtPolicy.outstanding(debt.principalMinor, voiceDebtPayments.filter { it.debtId == debt.id })
                                FilterChip(selected = debtPaymentDebtId == debt.id, onClick = { debtPaymentDebtId = debt.id }, enabled = !isDebtPaymentSaving, label = { Text(debt.personName + " · المتبقي " + formatCurrencyMoney(outstanding, debt.currencyCode)) })
                            }
                        }
                        if (eligibleDebts.isEmpty()) Text("لا توجد ديون مفتوحة من النوع المطلوب. سجّل الدين أولاً.", color = FinanceColors.TextSecondary)
                        Text(status, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = ::confirmVoiceDebtPayment, modifier = Modifier.fillMaxWidth(), enabled = !isDebtPaymentSaving && eligibleDebts.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)) { Text(if (isDebtPaymentSaving) "جارٍ تسجيل الدفعة..." else "مراجعة وتأكيد الدفعة") }
                        TextButton(onClick = ::cancelVoiceDraft, modifier = Modifier.fillMaxWidth(), enabled = !isDebtPaymentSaving) { Text("إلغاء الدفعة") }
                    }
                }
            }
        }
        if (hasDebtDraft) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("مراجعة الدين الصوتي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = debtPersonDraft,
                            onValueChange = { debtPersonDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("اسم الشخص أو الجهة") },
                            singleLine = true,
                            enabled = !isDebtSaving
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = debtTypeDraft == "OWED_BY_ME",
                                onClick = { debtTypeDraft = "OWED_BY_ME" },
                                enabled = !isDebtSaving,
                                label = { Text("أنا مدين") }
                            )
                            FilterChip(
                                selected = debtTypeDraft == "OWED_TO_ME",
                                onClick = { debtTypeDraft = "OWED_TO_ME" },
                                enabled = !isDebtSaving,
                                label = { Text("لي عنده") }
                            )
                        }
                        OutlinedTextField(
                            value = debtAmountDraft,
                            onValueChange = { debtAmountDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("المبلغ الأصلي للدين") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !isDebtSaving
                        )
                        OutlinedTextField(
                            value = debtDescriptionDraft,
                            onValueChange = { debtDescriptionDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("وصف الدين (اختياري)") },
                            singleLine = true,
                            enabled = !isDebtSaving
                        )
                        Text("محفظة الدين", color = FinanceColors.TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(wallets, key = { it.id }) { item ->
                                FilterChip(
                                    selected = debtWalletDraftId == item.id,
                                    onClick = { debtWalletDraftId = item.id },
                                    enabled = !isDebtSaving,
                                    label = { Text(item.name + " · " + item.currencyCode) }
                                )
                            }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = debtRecordCashMovement,
                                onCheckedChange = { debtRecordCashMovement = it },
                                enabled = !isDebtSaving
                            )
                            Text("تحديث رصيد المحفظة بأصل الدين", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(status, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = ::confirmVoiceDebt,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isDebtSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                        ) { Text(if (isDebtSaving) "جارٍ تسجيل الدين..." else "مراجعة وتأكيد تسجيل الدين") }
                        TextButton(
                            onClick = ::cancelVoiceDraft,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isDebtSaving
                        ) { Text("إلغاء تسجيل الدين") }
                    }
                }
            }
        }
        if (hasTransferDraft) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("مراجعة التحويل الصوتي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("من المحفظة", color = FinanceColors.TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(wallets, key = { it.id }) { item ->
                                FilterChip(
                                    selected = transferFromWalletId == item.id,
                                    onClick = { transferFromWalletId = item.id },
                                    enabled = !isTransferSaving,
                                    label = { Text(item.name + " · " + item.currencyCode) }
                                )
                            }
                        }
                        Text("إلى المحفظة", color = FinanceColors.TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(wallets, key = { it.id }) { item ->
                                FilterChip(
                                    selected = transferToWalletId == item.id,
                                    onClick = { transferToWalletId = item.id },
                                    enabled = !isTransferSaving,
                                    label = { Text(item.name + " · " + item.currencyCode) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = transferAmountText,
                            onValueChange = { transferAmountText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("مبلغ التحويل") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !isTransferSaving
                        )
                        OutlinedTextField(
                            value = transferNote,
                            onValueChange = { transferNote = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ملاحظة التحويل") },
                            singleLine = true,
                            enabled = !isTransferSaving
                        )
                        Text(status, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = ::confirmVoiceTransfer,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isTransferSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                        ) { Text(if (isTransferSaving) "جارٍ تنفيذ التحويل..." else "مراجعة وتأكيد التحويل") }
                        TextButton(
                            onClick = ::cancelVoiceDraft,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isTransferSaving
                        ) { Text("إلغاء التحويل") }
                    }
                }
            }
        }
        if (!hasTransferDraft && !hasDebtDraft && !hasDebtPaymentDraft && !hasSavingsDraft && !hasBillDraft) item {
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
                        enabled = !isSavingDraft,
                        colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                    ) {
                        Text(if (isSavingDraft) "جارٍ حفظ العملية..." else "تأكيد وحفظ العملية")
                    }
                    if (hasDraft) {
                        TextButton(
                            onClick = ::cancelVoiceDraft,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSavingDraft
                        ) { Text("إلغاء المسودة دون حفظ") }
                    }
                }
            }
        }
    }
}

private fun launchFinanceRecognition(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
): Boolean {
    val intent = VoiceRecognitionIntentFactory.create(context) ?: return false
    return runCatching { launcher.launch(intent) }.isSuccess
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
                                com.malekfinance.FinanceDebtOperations.create(
                                    db = db,
                                    personName = cleanName,
                                    description = cleanDescription,
                                    debtType = selectedDebtType,
                                    principalMinor = amountMinor,
                                    walletId = wallet.id,
                                    recordCashMovement = shouldRecordCashMovement
                                )
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
                                                com.malekfinance.FinanceDebtPaymentOperations.applyPayment(db, debt.id, amountMinor)
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
private fun ReportsLandingScreen(
    db: FinanceDb,
    transactions: List<FinanceTransaction>,
    wallets: List<FinanceWallet>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportStatus by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var backupBusy by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var customStartText by remember { mutableStateOf("") }
    var customEndText by remember { mutableStateOf("") }
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndExclusive by remember { mutableStateOf<Long?>(null) }
    var customRangeStatus by remember { mutableStateOf("") }
    var selectedWalletId by remember { mutableStateOf<Long?>(null) }
    var selectedRange by remember { mutableStateOf(FinanceReportRange.MONTHLY) }
    LaunchedEffect(wallets) {
        if (selectedWalletId == null && wallets.isNotEmpty()) selectedWalletId = wallets.first().id
    }
    val wallet = wallets.firstOrNull { it.id == selectedWalletId } ?: wallets.firstOrNull()
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null && wallet != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { FinanceReportExporter.writeCsv(it, wallet, transactions) } ?: error("Unable to open output")
        }.onSuccess { exportStatus = "تم تصدير ملف CSV." }.onFailure { exportStatus = "تعذر تصدير CSV." }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null && wallet != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { FinanceReportExporter.writeXlsx(it, wallet, transactions) } ?: error("Unable to open output")
        }.onSuccess { exportStatus = "تم تصدير ملف Excel." }.onFailure { exportStatus = "تعذر تصدير Excel." }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && wallet != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { FinanceReportExporter.writePdf(it, wallet, transactions) } ?: error("Unable to open output")
        }.onSuccess { exportStatus = "تم تصدير ملف PDF." }.onFailure { exportStatus = "تعذر تصدير PDF." }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && backupPassword.length >= 8 && backupPassword == backupPasswordConfirm && !backupBusy) {
            backupBusy = true
            val password = backupPassword.toCharArray()
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { FinanceBackupManager.export(db, it, password) }
                            ?: error("Unable to open backup destination")
                    }
                }.onSuccess { exportStatus = "تم إنشاء نسخة احتياطية مشفرة." }
                    .onFailure { exportStatus = "تعذر إنشاء النسخة الاحتياطية: " + (it.message ?: "خطأ غير معروف") }
                backupPassword = ""
                backupPasswordConfirm = ""
                backupBusy = false
            }
        } else if (backupPassword.length < 8) exportStatus = "اجعل كلمة المرور 8 أحرف على الأقل."
        else if (backupPassword != backupPasswordConfirm) exportStatus = "كلمتا المرور غير متطابقتين."
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && backupPassword.length >= 8 && !backupBusy) {
            backupBusy = true
            val password = backupPassword.toCharArray()
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openInputStream(uri) ?: error("Unable to open backup")
                        FinanceBackupManager.restore(db, stream, password)
                    }
                }.onSuccess { exportStatus = "تمت استعادة النسخة الاحتياطية. أُعيد تحميل بيانات الحساب." }
                    .onFailure { exportStatus = "فشلت الاستعادة ولم تُعتمد بيانات جزئية: " + (it.message ?: "تحقق من الملف وكلمة المرور") }
                backupPassword = ""
                backupPasswordConfirm = ""
                backupBusy = false
            }
        } else if (backupPassword.length < 8) exportStatus = "أدخل كلمة مرور النسخة الاحتياطية أولاً."
    }
    val timeline = wallet?.let {
        FinanceReportAnalytics.timeline(transactions, it.id, it.currencyCode, selectedRange)
    }.orEmpty()
    val current = timeline.lastOrNull()
    val categories = wallet?.let {
        FinanceReportAnalytics.categoryBreakdown(transactions, it.id, it.currencyCode, selectedRange)
    }.orEmpty()
    val maximum = timeline.flatMap { listOf(it.incomeMinor, it.expenseMinor) }
        .maxOrNull()?.coerceAtLeast(java.math.BigInteger.ONE) ?: java.math.BigInteger.ONE
    val customResult = if (wallet != null && customStartMillis != null && customEndExclusive != null) {
        FinanceReportAnalytics.customRange(transactions, wallet.id, wallet.currencyCode, customStartMillis!!, customEndExclusive!!)
    } else null
    val customCategories = if (wallet != null && customStartMillis != null && customEndExclusive != null) {
        FinanceReportAnalytics.customCategoryBreakdown(transactions, wallet.id, wallet.currencyCode, customStartMillis!!, customEndExclusive!!)
    } else emptyList()

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("النسخ الاحتياطي المشفر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = backupPassword,
                onValueChange = { backupPassword = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("كلمة مرور النسخة (8 أحرف على الأقل)") }
            )
            OutlinedTextField(
                value = backupPasswordConfirm,
                onValueChange = { backupPasswordConfirm = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("تأكيد كلمة المرور عند إنشاء نسخة") }
            )
            if (backupPassword.isNotBlank() && backupPassword != backupPasswordConfirm) {
                Text("كلمتا المرور غير متطابقتين.", color = FinanceColors.Warning)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { backupLauncher.launch("malek-finance-backup.mfb") }, enabled = !backupBusy && backupPassword.length >= 8 && backupPassword == backupPasswordConfirm) {
                    Text(if (backupBusy) "جارٍ العمل..." else "إنشاء نسخة")
                }
                OutlinedButton(onClick = { showRestoreConfirmation = true }, enabled = !backupBusy && backupPassword.length >= 8) {
                    Text("استعادة نسخة")
                }
            }
            Text("النسخة مشفرة محلياً بكلمة مرور تختارها. لا تحفظ كلمة المرور في التطبيق؛ ستحتاجها عند الاستعادة.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (showRestoreConfirmation) {
                AlertDialog(
                    onDismissRequest = { showRestoreConfirmation = false },
                    title = { Text("استبدال البيانات الحالية؟") },
                    text = { Text("ستستبدل الاستعادة بيانات التطبيق الحالية بالكامل. أنشئ نسخة احتياطية من بياناتك الحالية قبل المتابعة.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showRestoreConfirmation = false
                            restoreLauncher.launch(arrayOf("application/octet-stream", "application/x-malek-finance-backup", "*/*"))
                        }) { Text("متابعة الاستعادة") }
                    },
                    dismissButton = { TextButton(onClick = { showRestoreConfirmation = false }) { Text("إلغاء") } }
                )
            }
        }
        item {
            Text("تصدير التقارير", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { csvLauncher.launch("malek-finance-report.csv") }, enabled = wallet != null) { Text("CSV") }
                TextButton(onClick = { xlsxLauncher.launch("malek-finance-report.xlsx") }, enabled = wallet != null) { Text("Excel") }
                TextButton(onClick = { pdfLauncher.launch("malek-finance-report.pdf") }, enabled = wallet != null) { Text("PDF") }
            }
            if (exportStatus.isNotBlank()) Text(exportStatus, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("التقارير حسب المحفظة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("لا نخلط العملات المختلفة؛ اختر المحفظة لعرض أرقامها وحدها.", color = FinanceColors.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(wallets, key = { it.id }) { item ->
                    FilterChip(selected = wallet?.id == item.id, onClick = { selectedWalletId = item.id }, label = { Text(item.name + " · " + item.currencyCode) })
                }
            }
        }
        if (wallet == null) {
            item { SectionCard("لا توجد محافظ", "أنشئ محفظة أولاً لبدء التقارير.") }
        } else {
            item {
                Text("الفترة الزمنية", color = FinanceColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        FinanceReportRange.DAILY to "يومي",
                        FinanceReportRange.WEEKLY to "أسبوعي",
                        FinanceReportRange.MONTHLY to "شهري",
                        FinanceReportRange.YEARLY to "سنوي"
                    ).forEach { (range, label) ->
                        FilterChip(selected = selectedRange == range, onClick = { selectedRange = range }, label = { Text(label) })
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = FinanceColors.Surface), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ملخص الفترة الحالية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("الدخل: " + reportMoney(current?.incomeMinor ?: java.math.BigInteger.ZERO, wallet.currencyCode), color = FinanceColors.Income)
                        Text("المصروف: " + reportMoney(current?.expenseMinor ?: java.math.BigInteger.ZERO, wallet.currencyCode), color = FinanceColors.Expense)
                        Text("الصافي: " + reportMoney((current?.incomeMinor ?: java.math.BigInteger.ZERO).subtract(current?.expenseMinor ?: java.math.BigInteger.ZERO), wallet.currencyCode), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Text("التدفق المالي عبر الفترات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    timeline.forEach { bucket ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(bucket.label, style = MaterialTheme.typography.labelMedium, color = FinanceColors.TextSecondary)
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("دخل", modifier = Modifier.width(36.dp), color = FinanceColors.Income, style = MaterialTheme.typography.labelSmall)
                                Box(Modifier.weight(1f).height(9.dp).background(FinanceColors.SurfaceElevated, RoundedCornerShape(8.dp))) {
                                    Box(Modifier.fillMaxWidth((bucket.incomeMinor.toDouble() / maximum.toDouble()).coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().background(FinanceColors.Income, RoundedCornerShape(8.dp)))
                                }
                                Text("مصروف", modifier = Modifier.width(48.dp), color = FinanceColors.Expense, style = MaterialTheme.typography.labelSmall)
                                Box(Modifier.weight(1f).height(9.dp).background(FinanceColors.SurfaceElevated, RoundedCornerShape(8.dp))) {
                                    Box(Modifier.fillMaxWidth((bucket.expenseMinor.toDouble() / maximum.toDouble()).coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().background(FinanceColors.Expense, RoundedCornerShape(8.dp)))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(reportMoney(bucket.incomeMinor, wallet.currencyCode), color = FinanceColors.Income, style = MaterialTheme.typography.labelSmall)
                                Text(reportMoney(bucket.expenseMinor, wallet.currencyCode), color = FinanceColors.Expense, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item {
                Text("مقارنة فترة مخصصة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = customStartText,
                    onValueChange = { customStartText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("من تاريخ YYYY-MM-DD") }
                )
                OutlinedTextField(
                    value = customEndText,
                    onValueChange = { customEndText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("إلى تاريخ YYYY-MM-DD") }
                )
                Button(onClick = {
                    val start = com.malekfinance.FinanceBillPolicy.parseLocalDate(customStartText)
                    val end = com.malekfinance.FinanceBillPolicy.parseLocalDate(customEndText)
                    if (start == null || end == null || end < start) {
                        customRangeStatus = "أدخل تاريخي بداية ونهاية صحيحين، وتأكد أن النهاية ليست قبل البداية."
                        customStartMillis = null
                        customEndExclusive = null
                    } else {
                        val startDay = java.util.Calendar.getInstance().apply {
                            timeInMillis = start
                            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                        }
                        val endDay = java.util.Calendar.getInstance().apply {
                            timeInMillis = end
                            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                            add(java.util.Calendar.DAY_OF_YEAR, 1)
                        }
                        customStartMillis = startDay.timeInMillis
                        customEndExclusive = endDay.timeInMillis
                        customRangeStatus = "تم تحديث نتائج الفترة المخصصة."
                    }
                }, enabled = wallet != null) { Text("عرض الفترة المخصصة") }
                if (customRangeStatus.isNotBlank()) Text(customRangeStatus, color = FinanceColors.TextSecondary)
                if (customResult != null && wallet != null) {
                    Text("الدخل: " + reportMoney(customResult.incomeMinor, wallet.currencyCode), color = FinanceColors.Income)
                    Text("المصروف: " + reportMoney(customResult.expenseMinor, wallet.currencyCode), color = FinanceColors.Expense)
                    customCategories.forEach { item ->
                        Text(item.category + ": " + reportMoney(item.amountMinor, wallet.currencyCode), color = FinanceColors.TextSecondary)
                    }
                }
            }
            item {
                Text("توزيع المصروفات حسب التصنيف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (categories.isEmpty()) Text("لا توجد مصروفات في هذه الفترة.", color = FinanceColors.TextSecondary)
                else {
                    val total = categories.fold(java.math.BigInteger.ZERO) { sum, item -> sum.add(item.amountMinor) }
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        categories.forEach { item ->
                            val fraction = (item.amountMinor.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.category)
                                Text(reportMoney(item.amountMinor, wallet.currencyCode), color = FinanceColors.TextSecondary)
                            }
                            Box(Modifier.fillMaxWidth().height(8.dp).background(FinanceColors.SurfaceElevated, RoundedCornerShape(8.dp))) {
                                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(FinanceColors.Primary, RoundedCornerShape(8.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun reportMoney(minor: java.math.BigInteger, currency: String): String {
    val major = minor.divide(java.math.BigInteger.valueOf(100))
    val fraction = minor.remainder(java.math.BigInteger.valueOf(100)).abs()
    return String.format(java.util.Locale.ROOT, "%s.%02d %s", major.toString(), fraction.toInt(), currency)
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
    savingsGoals: List<com.malekfinance.FinanceSavingsGoal>,
    savingsEntries: List<com.malekfinance.FinanceSavingsEntry>,
    recurringIncomeRules: List<com.malekfinance.FinanceRecurringIncomeRule>,
    recurringIncomeEntries: List<com.malekfinance.FinanceRecurringIncomeEntry>,
    recurringExpenseRules: List<com.malekfinance.FinanceRecurringExpenseRule>,
    recurringExpenseEntries: List<com.malekfinance.FinanceRecurringExpenseEntry>,
    bills: List<com.malekfinance.FinanceBill>,
    billPayments: List<com.malekfinance.FinanceBillPayment>,
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
    var editingBudgetId by remember { mutableStateOf<Long?>(null) }
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
                                        com.malekfinance.FinanceTransferOperations.transfer(
                                            db = db,
                                            fromWalletId = fromWallet.id,
                                            toWalletId = toWallet.id,
                                            amountMinor = amountMinor ?: 0L,
                                            note = transferNote
                                        )
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
                                    enabled = editingBudgetId == null,
                                    label = { Text(category.name) }
                                )
                            }
                        }
                        Text("العملة", color = FinanceColors.TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("YER", "USD", "SAR", "AED").forEach { code ->
                                FilterChip(selected = budgetCurrency == code, onClick = { budgetCurrency = code }, enabled = editingBudgetId == null, label = { Text(code) })
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
                                val normalized = normalizeFinanceAmount(budgetLimit).replace('٫', '.').replace(',', '.').trim()
                                val limitMinor = runCatching { normalized.toBigDecimalOrNull()?.movePointRight(2)?.longValueExact() }.getOrNull()
                                val editingBudget = editingBudgetId?.let { id -> budgets.firstOrNull { it.id == id } }
                                val selectedCategory = editingBudget?.categoryName ?: budgetCategoryName.ifBlank { budgetCategories.first().name }
                                val selectedCurrency = editingBudget?.currencyCode ?: budgetCurrency
                                when {
                                    limitMinor == null || limitMinor <= 0L -> status = "أدخل حداً صحيحاً للميزانية أكبر من صفر."
                                    editingBudgetId != null && editingBudget == null -> {
                                        editingBudgetId = null
                                        budgetLimit = ""
                                        status = "لم تعد الميزانية المحددة موجودة؛ حدّث القائمة وحاول مجدداً."
                                    }
                                    editingBudget == null && budgetCategories.none { it.name == selectedCategory } -> status = "اختر تصنيف مصروف نشطاً."
                                    editingBudget == null && budgets.any { it.categoryName == selectedCategory && it.currencyCode == selectedCurrency && it.periodKey == budgetMonth.periodKey } -> status = "توجد ميزانية لهذا التصنيف والعملة في الشهر الحالي."
                                    else -> scope.launch {
                                        if (isCreatingBudget) return@launch
                                        isCreatingBudget = true
                                        try {
                                            if (editingBudget != null) {
                                                val latest = budgets.firstOrNull { it.id == editingBudget.id } ?: error("Budget not found")
                                                val changed = db.budgetDao().update(latest.copy(limitMinor = limitMinor))
                                                check(changed == 1) { "Budget was not updated" }
                                                editingBudgetId = null
                                                status = "تم تحديث حد الميزانية دون تغيير تصنيفها أو عملتها أو شهرها."
                                            } else {
                                                db.budgetDao().insert(com.malekfinance.FinanceBudget(
                                                    categoryName = selectedCategory,
                                                    currencyCode = selectedCurrency,
                                                    periodKey = budgetMonth.periodKey,
                                                    limitMinor = limitMinor
                                                ))
                                                budgetCategoryName = selectedCategory
                                                status = "تم إنشاء الميزانية الشهرية."
                                            }
                                            budgetLimit = ""
                                        } catch (_: Exception) {
                                            status = "تعذر حفظ الميزانية؛ لم يتم تغيير بياناتها."
                                        } finally {
                                            isCreatingBudget = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCreatingBudget && budgetCategories.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                        ) { Text(if (isCreatingBudget) "جارٍ الحفظ..." else if (editingBudgetId != null) "حفظ تعديل الميزانية" else "إضافة ميزانية لهذا الشهر") }
                        if (editingBudgetId != null) {
                            TextButton(
                                onClick = {
                                    editingBudgetId = null
                                    budgetLimit = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("إلغاء تعديل الميزانية", color = FinanceColors.TextSecondary) }
                        }
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
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        editingBudgetId = budget.id
                                        budgetCategoryName = budget.categoryName
                                        budgetCurrency = budget.currencyCode
                                        budgetLimit = java.math.BigDecimal.valueOf(budget.limitMinor, 2).stripTrailingZeros().toPlainString()
                                    }) { Text("تعديل الحد", color = FinanceColors.PrimaryLight) }
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { db.budgetDao().delete(budget.id) }
                                                .onSuccess {
                                                    if (editingBudgetId == budget.id) {
                                                        editingBudgetId = null
                                                        budgetLimit = ""
                                                    }
                                                    status = "تم حذف الميزانية."
                                                }
                                                .onFailure { status = "تعذر حذف الميزانية." }
                                        }
                                    }) { Text("حذف الميزانية") }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SavingsManagementSection(db, wallets, savingsGoals, savingsEntries) }
        item { RecurringIncomeSection(db, wallets, recurringIncomeRules, recurringIncomeEntries) }
        item { RecurringExpenseSection(db, wallets, budgetCategories, recurringExpenseRules, recurringExpenseEntries) }
        item { BillManagementSection(db, wallets, categories, bills, billPayments) }
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
