package com.malekfinance

/** Suggestions only. Unknown, overlapping or duplicated wallet names never select a pair. */
object FinanceVoiceDraftPolicy {
    fun transferParties(spoken: String, wallets: List<FinanceWallet>): Pair<Long?, Long?> {
        val text = spoken.replace(Regex("""\s+"""), " ").trim()
        val direction = Regex("""(?:^|\s)من\s+(.+?)\s+(?:إلى|الى)\s+(.+)$""").find(text)
            ?: return null to null
        // Multiple direction clauses cannot safely be interpreted as a single transfer.
        if (Regex("""(?:^|\s)(?:من|إلى|الى)(?:\s|$)""").containsMatchIn(direction.groupValues[1]) ||
            Regex("""(?:^|\s)(?:من|إلى|الى)(?:\s|$)""").containsMatchIn(direction.groupValues[2])) return null to null
        fun match(segment: String): FinanceWallet? = wallets.filter { wallet ->
            val name = wallet.name.trim().replace(Regex("""\s+"""), " ")
            name.isNotEmpty() && Regex("""(?<![\p{L}\p{N}])${Regex.escape(name)}(?![\p{L}\p{N}])""",
                RegexOption.IGNORE_CASE).containsMatchIn(segment)
        }.singleOrNull()
        val from = match(direction.groupValues[1]) ?: return null to null
        val to = match(direction.groupValues[2]) ?: return null to null
        if (from.id == to.id || from.currencyCode != to.currencyCode) return null to null
        return from.id to to.id
    }
}
