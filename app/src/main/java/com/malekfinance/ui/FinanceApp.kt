package com.malekfinance.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.malekfinance.FinanceDb
import com.malekfinance.FinanceTransaction
import kotlinx.coroutines.flow.collectLatest

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
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                when (section) {
                    FinanceSection.HOME -> com.malekfinance.FinanceHome(db, Modifier.padding(padding))
                    FinanceSection.TRANSACTIONS -> TransactionListScreen(transactions, Modifier.padding(padding))
                    FinanceSection.VOICE -> VoiceLandingScreen(Modifier.padding(padding))
                    FinanceSection.REPORTS -> ReportsLandingScreen(income, expenses, transactions.size, Modifier.padding(padding))
                    FinanceSection.MONEY -> MoneyLandingScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun TransactionListScreen(rows: List<FinanceTransaction>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("جميع العمليات المسجلة على هذا الجهاز", color = FinanceColors.TextSecondary) }
        if (rows.isEmpty()) {
            item { SectionCard("لا توجد عمليات بعد", "أضف أول دخل أو مصروف من الرئيسية.") }
        } else {
            items(rows, key = { it.id }) { row ->
                SectionCard(
                    row.note,
                    row.category + " • " + if (row.kind == "INCOME") "دخل" else "مصروف",
                    (if (row.kind == "INCOME") "+" else "−") + formatMoney(row.amountMinor)
                )
            }
        }
    }
}

@Composable
private fun VoiceLandingScreen(modifier: Modifier = Modifier) {
    var transcript by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        transcript = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionCard("مساعدك المالي الصوتي", "قل مثلاً: صرفت ١٥٠٠ ريال بنزين. ستحتاج إلى مراجعة البيانات في الشاشة الحالية قبل حفظها.")
        }
        item {
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-YE")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث عن العملية المالية")
                    }
                    runCatching { launcher.launch(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ابدأ التحدث")
            }
        }
        if (transcript.isNotBlank()) item { SectionCard("النص الذي تم التعرف عليه", transcript, "هذه نتيجة أولية؛ لم تُحفظ كعملية مالية.") }
        item { Text("ملاحظة: ربط نتيجة التعرف الصوتي بدورة المراجعة والحفظ سيُستكمل في مرحلة الصوت.", color = FinanceColors.TextSecondary) }
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
