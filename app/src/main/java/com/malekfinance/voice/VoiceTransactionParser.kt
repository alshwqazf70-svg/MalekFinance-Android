package com.malekfinance.voice

enum class VoiceCommandType { TRANSACTION, TRANSFER_DRAFT, DEBT_CREATE_DRAFT, DEBT_PAYMENT_DRAFT, CANCEL_DRAFT, BALANCE_QUERY, SPENDING_QUERY, INCOME_QUERY, SUMMARY_QUERY, RECENT_TRANSACTIONS_QUERY, DEBT_OWED_BY_ME_QUERY, DEBT_OWED_TO_ME_QUERY, SAVINGS_BALANCE_QUERY, SAVINGS_CONTRIBUTION_DRAFT, SAVINGS_WITHDRAWAL_DRAFT, UPCOMING_BILLS_QUERY, BILL_PAYMENT_DRAFT, BUDGET_STATUS_QUERY }

data class ParsedTransaction(
    val amount: String?,
    val kind: String?,
    val category: String,
    val description: String,
    val command: VoiceCommandType = VoiceCommandType.TRANSACTION,
    val queryPeriod: String? = null,
    val relativeDayOffset: Int? = null,
    val debtPersonName: String? = null,
    val debtType: String? = null,
    val debtPaymentDirection: String? = null
)

/**
 * Pure text understanding layer. It deliberately does not access Android speech APIs,
 * the database, or network services; callers must review every mutation before saving.
 */
object VoiceTransactionParser {
    private val digitAmount = Regex("""\d+(?:[.,٫]\d{1,2})?""")
    private val numberWords = mapOf(
        "صفر" to 0L, "واحد" to 1L, "واحدة" to 1L, "واحده" to 1L,
        "اثنين" to 2L, "اثنان" to 2L, "ثنين" to 2L, "إثنين" to 2L, "اثنتين" to 2L,
        "ثلاثة" to 3L, "ثلاث" to 3L, "اربعة" to 4L, "أربعة" to 4L, "اربع" to 4L,
        "خمسة" to 5L, "خمس" to 5L, "ستة" to 6L, "ست" to 6L,
        "سبعة" to 7L, "سبع" to 7L, "ثمانية" to 8L, "ثمان" to 8L, "ثمانيه" to 8L,
        "تسعة" to 9L, "تسع" to 9L, "عشرة" to 10L, "عشر" to 10L,
        "احدعشر" to 11L, "أحدعشر" to 11L, "اثناعشر" to 12L, "اثني عشر" to 12L,
        "عشرين" to 20L, "عشرون" to 20L, "ثلاثين" to 30L, "ثلاثون" to 30L,
        "اربعين" to 40L, "أربعين" to 40L, "خمسين" to 50L, "ستين" to 60L,
        "سبعين" to 70L, "ثمانين" to 80L, "تسعين" to 90L,
        "مئة" to 100L, "مائة" to 100L, "ميه" to 100L, "مية" to 100L,
        "مئتين" to 200L, "مائتين" to 200L, "ألف" to 1_000L, "الف" to 1_000L, "آلاف" to 1_000L, "ثلاثمئة" to 300L, "اربعمئة" to 400L,
        "خمسمئة" to 500L, "ستمئة" to 600L, "سبعمئة" to 700L, "ثمانمئة" to 800L, "تسعمئة" to 900L,
        "الفين" to 2_000L, "ألفين" to 2_000L, "مليون" to 1_000_000L, "مليونين" to 2_000_000L
    )

    fun parse(raw: String): ParsedTransaction {
        val normalized = normalizeDigits(raw)
            .replace('٫', '.')
            .replace('،', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        val lower = normalized.lowercase()
        val command = detectCommand(lower)
        val amount = extractAmount(normalized)
        val incomeIntent = containsAny(lower, listOf(
            "دخل", "استلمت", "قبضت", "إيراد", "ايراد", "حول لي", "حوّل لي",
            "حصلت", "راتب", "رجع لي", "رجّع لي", "رجعلي", "وصلني"
        ))
        val expenseIntent = containsAny(lower, listOf(
            "صرف", "صرفت", "دفعت", "اشتريت", "شراء", "مصروف", "كلفني", "دفعت حق"
        ))
        val negatedExpense = containsAny(lower, listOf("ما صرفت", "لم اصرف", "لم أصرف", "ما دفعت", "لم ادفع", "لم أدفع"))
        val negatedIncome = containsAny(lower, listOf("ما استلمت", "لم استلم", "لم أستلم", "ما قبضت"))
        val kind = when {
            command != VoiceCommandType.TRANSACTION -> null
            negatedExpense || negatedIncome -> null
            incomeIntent && !expenseIntent -> "INCOME"
            expenseIntent && !incomeIntent -> "EXPENSE"
            else -> null
        }

        val category = when {
            containsAny(lower, listOf("بقالة", "سوبر", "مواد غذائية", "تموين", "مقاضي")) -> "بقالة"
            containsAny(lower, listOf("مطعم", "مطاعم", "وجبة", "غداء", "عشاء", "فطور")) -> "مطاعم"
            containsAny(lower, listOf("بنزين", "وقود", "ديزل", "بترول")) -> "بنزين"
            containsAny(lower, listOf("مواصلات", "باص", "تاكسي", "أجرة")) -> "مواصلات"
            containsAny(lower, listOf("كهرباء", "ماء", "إنترنت", "انترنت", "فاتورة")) -> "فواتير"
            containsAny(lower, listOf("راتب", "أجر")) -> "راتب"
            else -> "عام"
        }

        val description = normalized
            .replace(digitAmount, " ")
            .replace(Regex("""\b(صفر|واحد|واحدة|واحده|اثنين|اثنان|ثنين|ثلاثة|ثلاث|اربعة|أربعة|اربع|خمسة|خمس|ستة|ست|سبعة|سبع|ثمانية|ثمان|تسعة|تسع|عشرة|عشر|عشرين|ثلاثين|اربعين|أربعين|خمسين|ستين|سبعين|ثمانين|تسعين|مئة|مائة|ميه|مية|الف|ألف|آلاف|الفين|ألفين|مليون|مليونين)\b"""), " ")
            .replace(Regex("""(ريال|ريالات|ر\.ي|صرف|صرفت|دفعت|اشتريت|استلمت|قبضت|سجل|سجّل)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { raw.trim() }

        return ParsedTransaction(
            amount = amount,
            kind = kind,
            category = category,
            description = description,
            command = command,
            queryPeriod = detectPeriod(lower),
            relativeDayOffset = detectRelativeDay(lower),
            debtPersonName = extractDebtPersonName(normalized),
            debtType = detectDebtType(lower),
            debtPaymentDirection = detectDebtPaymentDirection(lower)
        )
    }

    private fun detectCommand(text: String): VoiceCommandType = when {
        containsAny(text, listOf("كم باقي في الميزانية", "كم تبقى من الميزانية", "هل تجاوزت الميزانية", "حالة الميزانية", "وضع الميزانية")) -> VoiceCommandType.BUDGET_STATUS_QUERY
        containsAny(text, listOf(
            "سدد دين", "سددت دين", "سداد دين", "سدد الدين", "دفعت دين", "ادفع دين", "ادفع الدين", "أدفع الدين",
            "حصلت دين", "حصلت دين من", "حصّلت الدين", "تحصيل دين", "تحصيل دين من", "استلمت سداد الدين", "استلمت سداد من"
        )) -> VoiceCommandType.DEBT_PAYMENT_DRAFT
        containsAny(text, listOf("حوّل", "حول", "تحويل", "انقل")) && containsAny(text, listOf("من")) && containsAny(text, listOf("إلى", "الى")) -> VoiceCommandType.TRANSFER_DRAFT
        containsAny(text, listOf("أعطيت", "اعطيت", "أقرضت", "اقرضت", "سلفت", "استلفت", "اقترضت", "أخذت قرض", "اخذت قرض")) -> VoiceCommandType.DEBT_CREATE_DRAFT
        containsAny(text, listOf("ما الفواتير المستحقة", "ما علي من فواتير", "الفواتير القادمة", "متى الفاتورة القادمة", "ما هي الفواتير")) -> VoiceCommandType.UPCOMING_BILLS_QUERY
        containsAny(text, listOf("سدد فاتورة", "سدد الفاتورة", "ادفع فاتورة", "ادفع الفاتورة", "دفع فاتورة", "دفع الفاتورة")) -> VoiceCommandType.BILL_PAYMENT_DRAFT
        containsAny(text, listOf("كم ادخرت", "كم ادخاري", "كم وفرت", "رصيد الادخار", "رصيد التوفير")) -> VoiceCommandType.SAVINGS_BALANCE_QUERY
        containsAny(text, listOf("اسحب من الادخار", "اسحب من الادخار", "سحب من الادخار", "سحبت من الادخار")) -> VoiceCommandType.SAVINGS_WITHDRAWAL_DRAFT
        containsAny(text, listOf("أضف للادخار", "اضف للادخار", "أضف ادخار", "اضف ادخار", "ساهم في الادخار", "أودع في الادخار", "اودع في الادخار", "ادخرت مبلغ")) -> VoiceCommandType.SAVINGS_CONTRIBUTION_DRAFT
        containsAny(text, listOf("كم علي من ديون", "كم علي", "كم أنا مدين", "كم انا مدين", "كم باقي علي")) -> VoiceCommandType.DEBT_OWED_BY_ME_QUERY
        containsAny(text, listOf("كم لي عند", "كم مستحق لي", "كم الناس لي", "كم عند الناس لي")) -> VoiceCommandType.DEBT_OWED_TO_ME_QUERY
        containsAny(text, listOf("آخر العمليات", "اخر العمليات", "اعرض العمليات", "أعرض العمليات", "آخر المصروفات", "اخر المصروفات")) -> VoiceCommandType.RECENT_TRANSACTIONS_QUERY
        containsAny(text, listOf("ألغ المسودة", "الغ المسودة", "إلغاء المسودة", "الغِ العملية", "ألغ العملية", "لا تسجلها")) -> VoiceCommandType.CANCEL_DRAFT
        containsAny(text, listOf("كم رصيدي", "كم معي", "رصيد محفظتي", "كم باقي", "كم تبقى")) -> VoiceCommandType.BALANCE_QUERY
        containsAny(text, listOf("كم صرفت", "كم انفقت", "كم أنفقت", "إجمالي المصروف", "مجموع المصروف")) -> VoiceCommandType.SPENDING_QUERY
        containsAny(text, listOf("كم دخل", "كم استلمت", "إجمالي الدخل", "مجموع الدخل")) -> VoiceCommandType.INCOME_QUERY
        containsAny(text, listOf("ملخص مالي", "ملخص حسابي", "اعطني ملخص", "أعطني ملخص", "التقرير المالي")) -> VoiceCommandType.SUMMARY_QUERY
        else -> VoiceCommandType.TRANSACTION
    }


    private fun detectDebtPaymentDirection(text: String): String? = when {
        containsAny(text, listOf("سدد دين", "سددت دين", "سداد دين", "سدد الدين", "دفعت دين", "ادفع دين", "ادفع الدين", "أدفع الدين")) -> "OWED_BY_ME"
        containsAny(text, listOf("حصلت دين", "حصّلت الدين", "تحصيل دين", "استلمت سداد الدين")) -> "OWED_TO_ME"
        else -> null
    }

    private fun detectDebtType(text: String): String? = when {
        containsAny(text, listOf("استلفت", "اقترضت", "سلفت من", "أخذت قرض", "اخذت قرض")) -> "OWED_BY_ME"
        containsAny(text, listOf("أعطيت", "اعطيت", "أقرضت", "اقرضت", "سلفت")) -> "OWED_TO_ME"
        else -> null
    }

    private fun extractDebtPersonName(text: String): String? {
        val queryMatch = Regex("""(?:عند|لدى)\s+([^؟،,]+)""").find(text)
        if (queryMatch != null) return queryMatch.groupValues[1].trim().trimEnd('؟', '?', '.', '،', ',').ifBlank { null }
        val loanMatch = Regex("""(?:استلفت|اقترضت|سلفت)\s+من\s+([^\s،؟,]+)""").find(text)
            ?: Regex("""(?:أعطيت|اعطيت|أقرضت|اقرضت|سلفت)\s+(?:لـ)?([^\s،؟,]+)""").find(text)
            ?: Regex("""(?:سدد دين|سداد دين|سددت دين|تحصيل دين|حصلت من)\s+([^\s،؟,]+)""").find(text)
            ?: Regex("""(?:سددت دين|سدد الدين|دفعت دين)\s+(?:لـ|ل)?([^\s،؟,]+)""").find(text)
            ?: Regex("""(?:حصلت دين|حصلت دين من|تحصيل دين من|استلمت سداد من)\s+([^\s،؟,]+)""").find(text)
        return loanMatch?.groupValues?.get(1)?.trim()?.trimStart('ل')?.trimEnd('؟', '?', '.', '،', ',')?.ifBlank { null }
    }

    private fun detectRelativeDay(text: String): Int? = when {
        containsAny(text, listOf("أول أمس", "اول امس")) -> -2
        containsAny(text, listOf("أمس", "امس")) -> -1
        containsAny(text, listOf("اليوم", "هذا اليوم")) -> 0
        else -> null
    }

    private fun detectPeriod(text: String): String? = when {
        containsAny(text, listOf("أول أمس", "اول امس")) -> "DAY_BEFORE_YESTERDAY"
        containsAny(text, listOf("اليوم", "هذا اليوم")) -> "TODAY"
        containsAny(text, listOf("امس", "أمس")) -> "YESTERDAY"
        containsAny(text, listOf("الأسبوع الماضي", "الاسبوع الماضي")) -> "LAST_WEEK"
        containsAny(text, listOf("هذا الاسبوع", "هذا الأسبوع", "الاسبوع الحالي", "الأسبوع الحالي")) -> "WEEK"
        containsAny(text, listOf("الشهر الماضي")) -> "LAST_MONTH"
        containsAny(text, listOf("هذا الشهر", "الشهر الحالي")) -> "MONTH"
        containsAny(text, listOf("السنة الماضية", "العام الماضي")) -> "LAST_YEAR"
        containsAny(text, listOf("هذه السنة", "هذا العام", "السنة الحالية")) -> "YEAR"
        else -> null
    }

    private fun extractAmount(text: String): String? {
        digitAmount.find(text)?.value?.replace('٫', '.')?.replace(',', '.')?.let { return it }
        val tokens = text.lowercase().replace(Regex("""[،,؟?.!]"""), " ").split(Regex("""\s+"""))
        var total = java.math.BigInteger.ZERO
        var group = java.math.BigInteger.ZERO
        var found = false
        for (token in tokens) {
            val value = numberWords[token] ?: numberWords[token.removePrefix("و")] ?: continue
            found = true
            when (value) {
                1_000_000L, 1_000L -> {
                    val multiplier = java.math.BigInteger.valueOf(value)
                    total = total.add((if (group == java.math.BigInteger.ZERO) java.math.BigInteger.ONE else group).multiply(multiplier))
                    group = java.math.BigInteger.ZERO
                }
                2_000_000L, 2_000L -> {
                    total = total.add(java.math.BigInteger.valueOf(value))
                    group = java.math.BigInteger.ZERO
                }
                else -> group = group.add(java.math.BigInteger.valueOf(value))
            }
        }
        if (!found) return null
        return total.add(group).toString()
    }

    private fun containsAny(text: String, phrases: List<String>) = phrases.any(text::contains)

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
