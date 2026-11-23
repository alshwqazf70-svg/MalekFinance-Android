package com.malekfinance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.malekfinance.*
import kotlinx.coroutines.launch
import java.math.BigInteger

@Composable
internal fun SavingsManagementSection(
    db: FinanceDb,
    wallets: List<FinanceWallet>,
    goals: List<FinanceSavingsGoal>,
    entries: List<FinanceSavingsEntry>
) {
    val scope = rememberCoroutineScope()
    var goalName by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var selectedWalletId by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val amountInputs = remember { mutableStateMapOf<Long, String>() }
    val busyGoals = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(wallets) {
        if (selectedWalletId == null || wallets.none { it.id == selectedWalletId }) {
            selectedWalletId = wallets.firstOrNull()?.id
        }
    }
    val selectedWallet = wallets.firstOrNull { it.id == selectedWalletId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("أهداف الادخار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "أنشئ هدفاً وحدد محفظته. المساهمة تحجز المال من رصيد المحفظة دون تسجيل مصروف، والسحب يعيده دون تسجيل دخل.",
            color = FinanceColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        if (wallets.isEmpty()) {
            Text("أنشئ محفظة أولاً حتى تتمكن من ربط هدف الادخار بها.", color = FinanceColors.TextSecondary)
        } else {
            OutlinedTextField(value = goalName, onValueChange = { goalName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم هدف الادخار") }, singleLine = true)
            OutlinedTextField(
                value = targetAmount, onValueChange = { targetAmount = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("المبلغ المستهدف (" + (selectedWallet?.currencyCode ?: "—") + ")") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )
            Text("المحفظة المرتبطة بالهدف", color = FinanceColors.TextSecondary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(wallets, key = { it.id }) { wallet ->
                    FilterChip(selected = selectedWallet?.id == wallet.id, onClick = { selectedWalletId = wallet.id }, label = { Text(wallet.name + " • " + wallet.currencyCode) })
                }
            }
            Button(
                onClick = {
                    val targetMinor = parseSavingsAmount(targetAmount)
                    when {
                        goalName.isBlank() -> status = "اكتب اسماً لهدف الادخار."
                        goalName.trim().length > 60 -> status = "اسم الهدف طويل؛ الحد الأقصى 60 حرفاً."
                        targetMinor == null || targetMinor <= 0L -> status = "أدخل مبلغاً مستهدفاً صحيحاً أكبر من صفر."
                        selectedWallet == null -> status = "اختر محفظة للهدف."
                        else -> scope.launch {
                            if (creating) return@launch
                            creating = true
                            try {
                                db.savingsGoalDao().insert(FinanceSavingsGoal(
                                    name = goalName.trim(), targetMinor = targetMinor,
                                    currencyCode = selectedWallet.currencyCode, walletId = selectedWallet.id
                                ))
                                goalName = ""
                                targetAmount = ""
                                status = "تم إنشاء هدف الادخار."
                            } catch (_: Exception) {
                                status = "تعذر إنشاء الهدف؛ تحقق من البيانات وحاول مجدداً."
                            } finally { creating = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(), enabled = !creating && wallets.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
            ) { Text(if (creating) "جارٍ إنشاء الهدف..." else "إنشاء هدف الادخار") }
        }

        HorizontalDivider(color = FinanceColors.Divider)
        if (goals.isEmpty()) {
            Text("لم تنشئ أهداف ادخار بعد.", color = FinanceColors.TextSecondary)
        } else {
            goals.forEach { goal ->
                val goalEntries = entries.filter { it.goalId == goal.id }
                val balance = FinanceSavingsPolicy.balance(goalEntries)
                val remaining = BigInteger.valueOf(goal.targetMinor).subtract(balance).max(BigInteger.ZERO)
                val percent = FinanceSavingsPolicy.progressPercent(balance, goal.targetMinor)
                val goalWallet = wallets.firstOrNull { it.id == goal.walletId }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = FinanceColors.SurfaceElevated)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(goal.name, fontWeight = FontWeight.Bold)
                            Text(if (remaining.signum() == 0) "اكتمل الهدف" else percent.toString() + "%", color = FinanceColors.PrimaryLight, fontWeight = FontWeight.SemiBold)
                        }
                        Text(formatSavingsMoney(balance, goal.currencyCode) + " من " + formatSavingsMoney(goal.targetMinor, goal.currencyCode), color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { (balance.toDouble() / goal.targetMinor.toDouble()).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(), color = FinanceColors.Primary
                        )
                        Text("المتبقي للوصول: " + formatSavingsMoney(remaining, goal.currencyCode) + (goalWallet?.let { " • " + it.name } ?: ""), color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                        if (remaining.signum() > 0) {
                            OutlinedTextField(
                                value = amountInputs[goal.id].orEmpty(), onValueChange = { amountInputs[goal.id] = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("مبلغ المساهمة (" + goal.currencyCode + ")") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                            )
                            Button(
                                onClick = {
                                    val amountMinor = parseSavingsAmount(amountInputs[goal.id].orEmpty())
                                    when {
                                        amountMinor == null || !FinanceSavingsPolicy.canContribute(goal.targetMinor, goalEntries, amountMinor) -> status = "المساهمة يجب أن تكون موجبة ولا تتجاوز المتبقي من الهدف."
                                        goalWallet == null -> status = "المحفظة المرتبطة بالهدف غير موجودة."
                                        else -> scope.launch {
                                            if (busyGoals[goal.id] == true) return@launch
                                            busyGoals[goal.id] = true
                                            try {
                                                db.withTransaction {
                                                    val currentGoal = db.savingsGoalDao().findById(goal.id) ?: error("Savings goal not found")
                                                    val latestEntries = db.savingsEntryDao().forGoal(goal.id)
                                                    check(FinanceSavingsPolicy.canContribute(currentGoal.targetMinor, latestEntries, amountMinor))
                                                    val wallet = db.walletDao().findById(currentGoal.walletId) ?: error("Wallet not found")
                                                    check(wallet.currencyCode == currentGoal.currencyCode)
                                                    val latestTransactions = db.dao().forWallet(wallet.id)
                                                    val latestTransfers = db.transferDao().forWallet(wallet.id)
                                                    if (!FinanceLedger.hasSufficientFunds(wallet, amountMinor, latestTransactions, latestTransfers)) throw InsufficientWalletFundsException()
                                                    val transactionId = db.dao().insert(FinanceTransaction(
                                                        amountMinor = amountMinor, kind = "SAVINGS_CONTRIBUTION",
                                                        note = "مساهمة ادخار: " + currentGoal.name, category = "ادخار",
                                                        walletId = wallet.id, currencyCode = wallet.currencyCode, savingsGoalId = currentGoal.id
                                                    ))
                                                    db.savingsEntryDao().insert(FinanceSavingsEntry(
                                                        goalId = currentGoal.id, transactionId = transactionId,
                                                        entryType = "CONTRIBUTION", amountMinor = amountMinor
                                                    ))
                                                }
                                                amountInputs.remove(goal.id)
                                                status = "تمت إضافة المساهمة إلى الهدف وحجزها من المحفظة."
                                            } catch (_: InsufficientWalletFundsException) {
                                                status = "رصيد المحفظة المتاح لا يكفي لهذه المساهمة."
                                            } catch (_: Exception) {
                                                status = "تعذرت المساهمة؛ لم يتم حفظ أي جزء منها."
                                            } finally { busyGoals[goal.id] = false }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(), enabled = busyGoals[goal.id] != true,
                                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                            ) { Text("إضافة مساهمة") }
                        }
                        if (balance.signum() > 0) {
                            val withdrawalKey = goal.id xor Long.MIN_VALUE
                            OutlinedTextField(
                                value = amountInputs[withdrawalKey].orEmpty(), onValueChange = { amountInputs[withdrawalKey] = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text("مبلغ السحب (" + goal.currencyCode + ")") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                            )
                            Button(
                                onClick = {
                                    val amountMinor = parseSavingsAmount(amountInputs[withdrawalKey].orEmpty())
                                    when {
                                        amountMinor == null || !FinanceSavingsPolicy.canWithdraw(goalEntries, amountMinor) -> status = "مبلغ السحب يجب أن يكون موجباً ولا يتجاوز مدخرات الهدف."
                                        goalWallet == null -> status = "المحفظة المرتبطة بالهدف غير موجودة."
                                        else -> scope.launch {
                                            if (busyGoals[goal.id] == true) return@launch
                                            busyGoals[goal.id] = true
                                            try {
                                                db.withTransaction {
                                                    val currentGoal = db.savingsGoalDao().findById(goal.id) ?: error("Savings goal not found")
                                                    val latestEntries = db.savingsEntryDao().forGoal(goal.id)
                                                    check(FinanceSavingsPolicy.canWithdraw(latestEntries, amountMinor))
                                                    val wallet = db.walletDao().findById(currentGoal.walletId) ?: error("Wallet not found")
                                                    check(wallet.currencyCode == currentGoal.currencyCode)
                                                    val transactionId = db.dao().insert(FinanceTransaction(
                                                        amountMinor = amountMinor, kind = "SAVINGS_WITHDRAWAL",
                                                        note = "سحب من الادخار: " + currentGoal.name, category = "ادخار",
                                                        walletId = wallet.id, currencyCode = wallet.currencyCode, savingsGoalId = currentGoal.id
                                                    ))
                                                    db.savingsEntryDao().insert(FinanceSavingsEntry(
                                                        goalId = currentGoal.id, transactionId = transactionId,
                                                        entryType = "WITHDRAWAL", amountMinor = amountMinor
                                                    ))
                                                }
                                                amountInputs.remove(withdrawalKey)
                                                status = "تم تسجيل السحب وإعادة المبلغ إلى المحفظة."
                                            } catch (_: Exception) {
                                                status = "تعذر تسجيل السحب؛ لم يتم تعديل أي رصيد."
                                            } finally { busyGoals[goal.id] = false }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(), enabled = busyGoals[goal.id] != true,
                                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)
                            ) { Text("سحب من الادخار") }
                        }
                    }
                }
            }
        }
        status?.let { Text(it, color = FinanceColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun parseSavingsAmount(raw: String): Long? {
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

private fun formatSavingsMoney(value: Long, currency: String): String =
    formatSavingsMoney(BigInteger.valueOf(value), currency)

private fun formatSavingsMoney(value: BigInteger, currency: String): String {
    val amount = java.math.BigDecimal(value, 2)
    val formatted = java.text.NumberFormat.getNumberInstance(java.util.Locale("ar")).format(amount)
    val suffix = when (currency) {
        "YER" -> "ر.ي"
        "USD" -> "$"
        "SAR" -> "ر.س"
        "AED" -> "د.إ"
        else -> currency
    }
    return formatted + " " + suffix
}
