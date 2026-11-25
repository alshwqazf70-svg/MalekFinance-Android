package com.malekfinance

import java.math.BigInteger

/** Semantic validation of a decoded v12 snapshot. No Android, SQL or destructive writes. */
object FinanceBackupIntegrity {
    val tableNames = listOf(
        "wallets", "categories", "transactions", "transfers", "budgets", "debts", "debt_payments",
        "savings_goals", "savings_entries", "recurring_income_rules", "recurring_income_entries",
        "recurring_expense_rules", "recurring_expense_entries", "bills", "bill_payments"
    )
    private val currencies = setOf("YER", "USD", "SAR", "AED")
    private val links = listOf("debtId", "savingsGoalId", "recurringIncomeRuleId", "recurringExpenseRuleId", "billId")
    private fun Map<String, Any?>.number(key: String): Long = (get(key) as? Long) ?: error("Invalid integer: $key")
    private fun Map<String, Any?>.text(key: String): String = (get(key) as? String) ?: error("Invalid text: $key")

    fun validate(snapshot: Map<String, List<Map<String, Any?>>>) {
        require(snapshot.keys == tableNames.toSet()) { "Unexpected backup tables" }
        val indexed = snapshot.mapValues { (_, rows) ->
            val index = rows.associateBy { it.number("id") }
            require(index.size == rows.size && index.keys.all { it > 0 }) { "Invalid or duplicate row ID" }
            index
        }
        fun rows(table: String) = snapshot.getValue(table)
        fun row(table: String, id: Long) = indexed.getValue(table)[id] ?: error("Missing $table reference")
        fun moneyRecord(record: Map<String, Any?>, amount: String = "amountMinor") {
            require(record.number(amount) > 0) { "Non-positive amount" }
            require(record.text("currencyCode") in currencies)
            require(row("wallets", record.number("walletId")).text("currencyCode") == record.text("currencyCode")) { "Wallet currency mismatch" }
        }
        rows("wallets").forEach {
            require(it.text("name").isNotBlank() && it.text("currencyCode") in currencies)
            require(it.text("walletType") in setOf("CASH", "BANK", "E_WALLET"))
            it.number("openingBalanceMinor") // Negative opening balance is a legitimate liability.
        }
        rows("categories").forEach {
            require(it.text("name").isNotBlank() && it.text("kind") in setOf("INCOME", "EXPENSE", "BOTH"))
        }
        rows("budgets").forEach {
            require(it.number("limitMinor") > 0 && it.text("currencyCode") in currencies)
            require(Regex("\\d{4}-(0[1-9]|1[0-2])").matches(it.text("periodKey")))
            require(it.text("categoryName").isNotBlank())
        }
        val transactions = rows("transactions")
        transactions.forEach { record ->
            moneyRecord(record)
            require(links.count { record[it] != null } <= 1) { "Multiple transaction owners" }
            require(record.text("kind") in setOf("INCOME", "EXPENSE", "DEBT_RECEIVED", "DEBT_ISSUED", "SAVINGS_CONTRIBUTION", "SAVINGS_WITHDRAWAL"))
        }
        rows("transfers").forEach {
            require(it.number("amountMinor") > 0 && it.number("fromWalletId") != it.number("toWalletId"))
            val currency = it.text("currencyCode")
            require(currency in currencies)
            require(row("wallets", it.number("fromWalletId")).text("currencyCode") == currency)
            require(row("wallets", it.number("toWalletId")).text("currencyCode") == currency)
        }
        rows("debts").forEach {
            moneyRecord(it, "principalMinor")
            require(it.text("personName").isNotBlank() && it.text("debtType") in setOf("OWED_BY_ME", "OWED_TO_ME"))
        }
        rows("savings_goals").forEach { moneyRecord(it, "targetMinor"); require(it.text("name").isNotBlank()) }
        listOf("recurring_income_rules", "recurring_expense_rules", "bills").forEach { table ->
            rows(table).forEach {
                moneyRecord(it)
                require(it.text("title").isNotBlank())
                require(it.text("frequency") in if (table == "bills") setOf("ONCE", "MONTHLY", "YEARLY") else setOf("WEEKLY", "MONTHLY"))
                require(it.number("anchorDay") in 1..31 && it.number("nextDueAt") >= 0)
                if (table == "bills") require(it.number("anchorMonth") in 0..11)
                if (table == "recurring_income_rules") require(it.text("incomeType") in setOf("SALARY", "RECURRING"))
            }
        }
        val claimed = mutableSetOf<Long>()
        fun claim(entry: Map<String, Any?>, owner: Map<String, Any?>, link: String, kind: String, sameWallet: Boolean = true) {
            val txId = entry.number("transactionId")
            require(claimed.add(txId)) { "Transaction linked more than once" }
            val tx = row("transactions", txId)
            require(tx[link] == owner.number("id") && tx.text("kind") == kind)
            require(tx.number("amountMinor") == entry.number("amountMinor") && tx.text("currencyCode") == owner.text("currencyCode"))
            if (sameWallet) require(tx.number("walletId") == owner.number("walletId"))
        }
        val debtTransactions = transactions.filter { it["debtId"] != null }.groupBy { it.number("debtId") }
        rows("debts").forEach { debt ->
            val originals = debtTransactions[debt.number("id")].orEmpty().filter { it.text("kind") in setOf("DEBT_RECEIVED", "DEBT_ISSUED") }
            if (debt.number("cashMovementRecorded") == 1L) {
                require(originals.size == 1) { "Debt cash movement missing or duplicated" }
                claim(mapOf("transactionId" to originals.single().number("id"), "amountMinor" to debt.number("principalMinor")), debt, "debtId", if (debt.text("debtType") == "OWED_BY_ME") "DEBT_RECEIVED" else "DEBT_ISSUED")
            } else require(originals.isEmpty()) { "Credit purchase must not create cash" }
        }
        val paidByDebt = mutableMapOf<Long, BigInteger>()
        rows("debt_payments").forEach { entry ->
            require(entry.number("amountMinor") > 0)
            val debt = row("debts", entry.number("debtId"))
            require(entry.number("createdAt") >= debt.number("createdAt"))
            // Account settlement may legitimately use another wallet in the same currency.
            claim(entry, debt, "debtId", if (debt.text("debtType") == "OWED_BY_ME") "EXPENSE" else "INCOME", sameWallet = false)
            val id = debt.number("id")
            paidByDebt[id] = (paidByDebt[id] ?: BigInteger.ZERO) + BigInteger.valueOf(entry.number("amountMinor"))
            require(paidByDebt.getValue(id) <= BigInteger.valueOf(debt.number("principalMinor"))) { "Debt overpayment" }
        }
        val saved = mutableMapOf<Long, BigInteger>()
        rows("savings_entries").sortedWith(compareBy<Map<String, Any?>> { it.number("createdAt") }.thenBy { it.number("id") }).forEach { entry ->
            require(entry.number("amountMinor") > 0 && entry.text("entryType") in setOf("CONTRIBUTION", "WITHDRAWAL"))
            val goal = row("savings_goals", entry.number("goalId"))
            val contribution = entry.text("entryType") == "CONTRIBUTION"
            claim(entry, goal, "savingsGoalId", if (contribution) "SAVINGS_CONTRIBUTION" else "SAVINGS_WITHDRAWAL")
            val id = goal.number("id")
            saved[id] = (saved[id] ?: BigInteger.ZERO) + BigInteger.valueOf(entry.number("amountMinor")) * BigInteger.valueOf(if (contribution) 1L else -1L)
            require(saved.getValue(id) >= BigInteger.ZERO && saved.getValue(id) <= BigInteger.valueOf(goal.number("targetMinor"))) { "Invalid savings balance" }
        }
        listOf(
            listOf("recurring_income_entries", "recurring_income_rules", "ruleId", "recurringIncomeRuleId", "INCOME"),
            listOf("recurring_expense_entries", "recurring_expense_rules", "ruleId", "recurringExpenseRuleId", "EXPENSE"),
            listOf("bill_payments", "bills", "billId", "billId", "EXPENSE")
        ).forEach { (entries, owners, foreignKey, link, kind) ->
            val occurrences = mutableSetOf<Pair<Long, String>>()
            rows(entries).forEach { entry ->
                require(entry.number("amountMinor") > 0 && entry.text("occurrenceKey").isNotBlank())
                require(occurrences.add(entry.number(foreignKey) to entry.text("occurrenceKey"))) { "Duplicate occurrence" }
                claim(entry, row(owners, entry.number(foreignKey)), link, kind)
            }
        }
        transactions.forEach {
            if (links.any { key -> it[key] != null }) require(it.number("id") in claimed) { "Orphaned linked transaction" }
            else require(it.text("kind") in setOf("INCOME", "EXPENSE")) { "Unlinked special transaction" }
        }
        snapshot.values.flatten().forEach { record ->
            listOf("isActive", "cashMovementRecorded").filter { it in record }.forEach { require(record.number(it) in 0..1) }
            listOf("createdAt", "recordedAt", "paidAt").filter { it in record }.forEach { require(record.number(it) >= 0) }
        }
    }
}
