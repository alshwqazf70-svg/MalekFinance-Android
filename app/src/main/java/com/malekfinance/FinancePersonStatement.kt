package com.malekfinance

import java.math.BigDecimal
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class FinanceStatementLine(
    val timestamp: Long,
    val reference: String,
    val description: String,
    val debit: BigInteger,
    val payment: BigInteger,
    val balance: BigInteger
)

data class FinancePersonStatement(
    val person: String,
    val currency: String,
    val lines: List<FinanceStatementLine>,
    val totalDebt: BigInteger,
    val totalPaid: BigInteger,
    val outstanding: BigInteger,
    val generatedAt: Long,
    val timeZoneId: String
) {
    fun asText(): String = buildString {
        appendLine("MK Finance — كشف حساب ديون ومشتريات آجلة")
        appendLine("الشخص: $person")
        appendLine("الاتجاه: مبالغ مستحقة عليّ لهذا الشخص")
        appendLine("العملة: $currency — لا يشمل أي عملة أخرى")
        appendLine("التوقيت: $timeZoneId")
        appendLine("أُعدّ الكشف: ${FinancePersonStatements.dateTime(generatedAt, timeZoneId)}")
        appendLine("يشمل جميع سجلات هذا الشخص بهذه العملة، بما فيها المسددة والقروض إن وُجدت.")
        appendLine("────────────────────")
        lines.forEachIndexed { index, line ->
            appendLine("${index + 1}. ${FinancePersonStatements.dateTime(line.timestamp, timeZoneId)}")
            appendLine("${line.reference} — ${line.description}")
            appendLine(if (line.debit.signum() > 0) "دين/شراء: ${amount(line.debit)}" else "سداد: ${amount(line.payment)}")
            appendLine("الرصيد المتبقي بعد الحركة: ${amount(line.balance)}")
            appendLine("────────────────────")
        }
        appendLine("إجمالي الديون والمشتريات: ${amount(totalDebt)}")
        appendLine("إجمالي السداد: ${amount(totalPaid)}")
        appendLine("المتبقي عليّ: ${amount(outstanding)}")
        append("كشف من السجلات المدخلة في التطبيق، وليس إثباتاً بتحويل مصرفي أو إقراراً من الطرف الآخر.")
    }
    private fun amount(value: BigInteger) = BigDecimal(value, 2).toPlainString() + " " + currency
}

object FinancePersonStatements {
    fun normalizePerson(value: String) = value.trim().replace(Regex("\\s+"), " ")

    fun dateTime(timestamp: Long, zone: String = "Asia/Aden"): String {
        val timeZone = TimeZone.getTimeZone(zone)
        val day = SimpleDateFormat("EEEE", Locale("ar")).apply { this.timeZone = timeZone }.format(Date(timestamp))
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { this.timeZone = timeZone }.format(Date(timestamp))
        return "$day | $date"
    }

    fun settlementPlan(
        person: String, currency: String, debts: List<FinanceDebt>, payments: List<FinanceDebtPayment>,
        amountMinor: Long, now: Long = System.currentTimeMillis()
    ): List<Pair<Long, Long>> {
        val statement = build(person, currency, debts, payments, now)
        require(amountMinor > 0 && BigInteger.valueOf(amountMinor) <= statement.outstanding) { "Payment exceeds outstanding" }
        var left = amountMinor
        return buildList {
            debts.filter { normalizePerson(it.personName) == normalizePerson(person) && it.currencyCode == currency && it.debtType == "OWED_BY_ME" }
                .sortedWith(compareBy<FinanceDebt> { it.createdAt }.thenBy { it.id }).forEach { debt ->
                    if (left > 0) {
                        val remaining = FinanceDebtPolicy.outstanding(debt.principalMinor, payments.filter { it.debtId == debt.id }).toLong()
                        val allocated = minOf(left, remaining)
                        if (allocated > 0) { add(debt.id to allocated); left -= allocated }
                    }
                }
            check(left == 0L)
        }
    }

    fun build(
        personName: String,
        currencyCode: String,
        debts: List<FinanceDebt>,
        payments: List<FinanceDebtPayment>,
        generatedAt: Long = System.currentTimeMillis(),
        zone: String = "Asia/Aden"
    ): FinancePersonStatement {
        val person = normalizePerson(personName)
        require(person.isNotBlank())
        require(currencyCode in setOf("YER", "USD", "SAR", "AED"))
        require(zone in TimeZone.getAvailableIDs())
        val selected = debts.filter {
            normalizePerson(it.personName) == person && it.currencyCode == currencyCode && it.debtType == "OWED_BY_ME"
        }
        require(selected.map { it.id }.distinct().size == selected.size)
        val byId = selected.associateBy { it.id }
        val selectedPayments = payments.filter { it.debtId in byId }
        require(selectedPayments.map { it.id }.distinct().size == selectedPayments.size)
        selected.forEach { debt ->
            require(debt.principalMinor > 0L && debt.createdAt in 0..generatedAt)
            val related = selectedPayments.filter { it.debtId == debt.id }
            require(related.all { it.amountMinor > 0 && it.createdAt in debt.createdAt..generatedAt })
            require(FinanceDebtPolicy.paidAmount(related) <= BigInteger.valueOf(debt.principalMinor))
        }
        data class Event(val at: Long, val order: Int, val id: Long, val ref: String, val description: String, val debit: BigInteger, val payment: BigInteger)
        val events = selected.map {
            Event(it.createdAt, 0, it.id, "عملية #${it.id}", it.description.replace(Regex("\\s+"), " ").trim().ifBlank { if (it.cashMovementRecorded) "قرض نقدي" else "دين دون حركة نقدية" }, BigInteger.valueOf(it.principalMinor), BigInteger.ZERO)
        } + selectedPayments.map {
            Event(it.createdAt, 1, it.id, "دفعة #${it.id} / عملية #${it.debtId}", "سداد مرتبط بالسجل الأصلي", BigInteger.ZERO, BigInteger.valueOf(it.amountMinor))
        }
        var balance = BigInteger.ZERO
        val lines = events.sortedWith(compareBy<Event> { it.at }.thenBy { it.order }.thenBy { it.id }).map {
            balance = balance.add(it.debit).subtract(it.payment)
            FinanceStatementLine(it.at, it.ref, it.description, it.debit, it.payment, balance)
        }
        val totalDebt = selected.fold(BigInteger.ZERO) { sum, debt -> sum.add(BigInteger.valueOf(debt.principalMinor)) }
        val totalPaid = FinanceDebtPolicy.paidAmount(selectedPayments)
        return FinancePersonStatement(person, currencyCode, lines, totalDebt, totalPaid, totalDebt.subtract(totalPaid), generatedAt, zone)
    }
}
