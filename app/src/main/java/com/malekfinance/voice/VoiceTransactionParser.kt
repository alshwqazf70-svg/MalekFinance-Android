package com.malekfinance.voice

data class ParsedTransaction(
    val amount: String?,
    val kind: String,
    val category: String,
    val description: String
)

object VoiceTransactionParser {
    fun parse(raw: String): ParsedTransaction {
        val normalized = normalizeDigits(raw).replace('٫', '.').replace('،', ' ')
        val match = Regex("""\d+(?:[.,]\d{1,2})?""").find(normalized)
        val amount = match?.value?.replace(',', '.')
        val lower = normalized.lowercase()
        val kind = if (listOf("دخل", "راتب", "استلمت", "قبضت", "إيراد", "ايراد", "حوّل لي", "حصلت").any(lower::contains)) {
            "INCOME"
        } else "EXPENSE"

        val category = when {
            listOf("بقالة", "سوبر", "مواد غذائية", "تموين").any(lower::contains) -> "بقالة"
            listOf("مطعم", "مطاعم", "وجبة", "غداء", "عشاء", "فطور").any(lower::contains) -> "مطاعم"
            listOf("بنزين", "وقود", "ديزل", "بترول").any(lower::contains) -> "بنزين"
            listOf("مواصلات", "باص", "تاكسي", "أجرة").any(lower::contains) -> "مواصلات"
            listOf("كهرباء", "ماء", "إنترنت", "انترنت", "فاتورة").any(lower::contains) -> "فواتير"
            listOf("راتب", "أجر", "دخل").any(lower::contains) -> "راتب"
            else -> "عام"
        }

        val description = normalized
            .replace(Regex("""\d+(?:[.,]\d{1,2})?"""), " ")
            .replace(Regex("""(?i)\b(ريال|ريالات|ر.ي)\b"""), " ")
            .replace(Regex("""(صرف|دفعت|دفعتُ|اشتريت|اشتريتُ|دفعت مبلغ|استلمت|قبضت|سجل|سجّل)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { raw.trim() }

        return ParsedTransaction(amount, kind, category, description)
    }

    private fun normalizeDigits(value: String): String {
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
}
