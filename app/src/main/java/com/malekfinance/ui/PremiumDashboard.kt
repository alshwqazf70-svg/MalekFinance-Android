package com.malekfinance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.malekfinance.*
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun PremiumDashboard(
    transactions: List<FinanceTransaction>, wallets: List<FinanceWallet>, transfers: List<FinanceTransfer>,
    modifier: Modifier = Modifier, hidden: Boolean, onHiddenChange: (Boolean) -> Unit, onVoice: () -> Unit, onTransactions: () -> Unit,
    onReports: () -> Unit, onMoney: () -> Unit
) {
    var currency by rememberSaveable { mutableStateOf("YER") }
    var reduction by rememberSaveable { mutableStateOf(20f) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }
    val snapshot = remember(transactions, currency, now) { FinanceInsights.snapshot(transactions, currency, now) }
    val selectedWallets = wallets.filter { it.currencyCode == currency }
    val balance = remember(wallets, transactions, transfers, currency) {
        selectedWallets.fold(BigInteger.ZERO) { sum, wallet -> sum + FinanceLedger.calculateBalance(wallet, transactions, transfers) }
    }
    fun amount(value: BigInteger): String = if (hidden) "••••••" else dashboardMoney(value, currency)

    LazyColumn(
        modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(now), color = FinanceColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                    Text("مساحتك المالية.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("وضوح اليوم، وطموح أبعد.", color = FinanceColors.TextSecondary)
                }
                IconButton(onClick = { onHiddenChange(!hidden) }) {
                    Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (hidden) "إظهار مبالغ الرئيسية" else "إخفاء مبالغ الرئيسية", tint = FinanceColors.Primary)
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("YER", "USD", "SAR", "AED")) { code ->
                    FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) },
                        leadingIcon = if (currency == code) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(30.dp), border = BorderStroke(1.dp, FinanceColors.Divider), color = FinanceColors.Surface) {
                Column(
                    Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(FinanceColors.HeroStart, FinanceColors.Surface, FinanceColors.HeroEnd))).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("النقد المتاح في محافظك", color = FinanceColors.TextSecondary)
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = FinanceColors.Primary)
                    }
                    Text(amount(balance), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("${selectedWallets.size} محافظ • $currency فقط • بعد حجز الادخار", style = MaterialTheme.typography.labelMedium, color = FinanceColors.TextSecondary)
                    HorizontalDivider(color = FinanceColors.Divider)
                    Text("حركة الشهر الحالي", color = FinanceColors.Primary, style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Metric("الدخل ↙", amount(snapshot.income), FinanceColors.Income, Modifier.weight(1f))
                        Metric("المصروف ↗", amount(snapshot.expense), FinanceColors.Expense, Modifier.weight(1f))
                    }
                    if (selectedWallets.isEmpty()) {
                        TextButton(onClick = onMoney) { Text("أنشئ أول محفظة بهذه العملة") }
                    }
                }
            }
        }
        item {
            Surface(onClick = onVoice, shape = RoundedCornerShape(24.dp), color = FinanceColors.Primary) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = CircleShape, color = FinanceColors.Background.copy(alpha = .10f), modifier = Modifier.size(54.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, tint = FinanceColors.Background, modifier = Modifier.size(28.dp)) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("قلها… واترك التفاصيل لنا", color = FinanceColors.Background, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("مساعد محلي • مراجعة قبل الحفظ", color = FinanceColors.Background, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronLeft, null, tint = FinanceColors.Background)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("إضافة عملية", Icons.Default.Add, Modifier.weight(1f), onVoice)
                QuickAction("تحليل أموالك", Icons.Default.BarChart, Modifier.weight(1f), onReports)
                QuickAction("المحافظ", Icons.Default.AccountBalanceWallet, Modifier.weight(1f), onMoney)
            }
        }
        item {
            DashboardCard {
                Eyebrow("MONEY PULSE", "نبض الإنفاق")
                Text("آخر ٧ أيام • $currency", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (hidden) {
                    Text("المخطط مخفي لحماية خصوصيتك", color = FinanceColors.TextSecondary)
                } else {
                    val max = snapshot.dailyExpenses.maxOrNull()?.max(BigInteger.ONE) ?: BigInteger.ONE
                    Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        snapshot.dailyExpenses.forEachIndexed { index, value ->
                            val ratio = BigDecimal(value).divide(BigDecimal(max), 4, java.math.RoundingMode.HALF_UP).toFloat()
                            val day = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, index - 6) }
                            val label = SimpleDateFormat("EEE", Locale("ar")).format(day.time)
                            Column(Modifier.weight(1f).semantics { contentDescription = "$label: ${dashboardMoney(value, currency)}" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.widthIn(max = 28.dp).fillMaxWidth().height((4 + ratio * 80).dp)
                                    .background(if (index == 6) FinanceColors.Primary else FinanceColors.Accent.copy(alpha = .55f), RoundedCornerShape(8.dp)))
                                Text(label, style = MaterialTheme.typography.labelSmall, color = FinanceColors.TextSecondary, maxLines = 1)
                            }
                        }
                    }
                    if (snapshot.dailyExpenses.all { it == BigInteger.ZERO }) Text("لا توجد مصروفات مسجلة خلال هذه الأيام.", color = FinanceColors.TextSecondary)
                }
            }
        }
        item {
            DashboardCard {
                Eyebrow("ON-DEVICE INSIGHTS", "بصيرة مالية")
                if (hidden) Text("التفاصيل المالية مخفية", color = FinanceColors.TextSecondary)
                else {
                    Text(if (snapshot.topCategory == null) "كل بداية تصنع فرقاً." else "أكثر إنفاقك هذا الشهر: ${snapshot.topCategory}", fontWeight = FontWeight.SemiBold)
                    Text(if (snapshot.topCategory == null) "سجّل عملياتك لتظهر قراءة مبنية على أرقامك، لا على التخمين."
                        else "${amount(snapshot.topCategoryAmount)} في هذا التصنيف. مصروف الشهر السابق كاملاً: ${amount(snapshot.previousExpense)}.", color = FinanceColors.TextSecondary)
                    Text("صافي دخل ومصروف الشهر: ${amount(snapshot.net)}", color = FinanceColors.Accent)
                    Text("تحليل محلي للسجلات؛ صافي العمليات ليس رصيد المحفظة.", style = MaterialTheme.typography.bodySmall, color = FinanceColors.TextSecondary)
                }
            }
        }
        item {
            DashboardCard {
                Eyebrow("WHAT IF", "مختبر المستقبل")
                Text("ماذا لو خفّضت إنفاقك ${reduction.toInt()}٪؟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Slider(value = reduction, onValueChange = { reduction = it }, valueRange = 0f..50f, steps = 9,
                    modifier = Modifier.semantics { contentDescription = "نسبة خفض الإنفاق الافتراضية" })
                Text(amount(snapshot.monthlySavingScenario(reduction.toInt())), color = FinanceColors.Primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("وفر افتراضي خلال ٣٠ يوماً، بناءً على متوسط إنفاق أيام الشهر المنقضية. ليس توقعاً مضموناً، ولا ينقل أي أموال.", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("أحدث الحركات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onTransactions) { Text("السجل الكامل") }
            }
        }
        val recent = transactions.filter { it.currencyCode == currency && it.createdAt <= now }.sortedByDescending { it.createdAt }.take(4)
        if (recent.isEmpty()) item {
            DashboardCard {
                Text("قصتك المالية تبدأ من هنا", fontWeight = FontWeight.Bold)
                Text("أضف أول عملية بعملة $currency. لن نملأ لوحتك بأرقام تجريبية.", color = FinanceColors.TextSecondary)
                TextButton(onClick = onVoice) { Text("إضافة أول عملية") }
            }
        }
        items(recent, key = { it.id }) { row ->
            val incoming = row.kind in listOf("INCOME", "DEBT_RECEIVED", "SAVINGS_WITHDRAWAL")
            val kind = when (row.kind) {
                "INCOME" -> "دخل"; "EXPENSE" -> "مصروف"; "DEBT_RECEIVED" -> "قرض مستلم"
                "DEBT_ISSUED" -> "إقراض"; "SAVINGS_CONTRIBUTION" -> "حجز ادخار"; "SAVINGS_WITHDRAWAL" -> "سحب ادخار"; else -> "عملية"
            }
            Surface(shape = RoundedCornerShape(20.dp), color = FinanceColors.Surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (incoming) Icons.Default.SouthWest else Icons.Default.NorthEast, null, tint = if (incoming) FinanceColors.Income else FinanceColors.Expense)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (hidden) "عملية مخفية" else row.note.ifBlank { row.category }, fontWeight = FontWeight.SemiBold)
                        Text(if (hidden) "••••" else "$kind · ${row.category}", color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(SimpleDateFormat("d MMM", Locale("ar")).format(row.createdAt), style = MaterialTheme.typography.labelSmall, color = FinanceColors.TextSecondary)
                        Text((if (incoming) "+ " else "− ") + amount(BigInteger.valueOf(row.amountMinor)), color = if (incoming) FinanceColors.Income else FinanceColors.Expense, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun QuickAction(title: String, icon: ImageVector, modifier: Modifier, action: () -> Unit) {
    Surface(onClick = action, modifier = modifier, shape = RoundedCornerShape(20.dp), color = FinanceColors.Surface, border = BorderStroke(1.dp, FinanceColors.Divider)) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = FinanceColors.Accent)
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Eyebrow(label: String, title: String) {
    Text(label, color = FinanceColors.Primary, style = MaterialTheme.typography.labelSmall)
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun DashboardCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = FinanceColors.Surface, border = BorderStroke(1.dp, FinanceColors.Divider)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

private fun dashboardMoney(value: BigInteger, currency: String): String =
    NumberFormat.getNumberInstance(Locale("ar")).apply { maximumFractionDigits = 2 }.format(BigDecimal(value, 2)) + " " + currency
