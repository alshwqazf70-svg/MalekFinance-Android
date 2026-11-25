package com.malekfinance.voice

enum class VoiceCommandType { TRANSACTION, TRANSFER_DRAFT, DEBT_CREATE_DRAFT, DEBT_PAYMENT_DRAFT, CANCEL_DRAFT, CORRECT_AMOUNT, BALANCE_QUERY, SPENDING_QUERY, INCOME_QUERY, SUMMARY_QUERY, RECENT_TRANSACTIONS_QUERY, DEBT_OWED_BY_ME_QUERY, DEBT_OWED_TO_ME_QUERY, SAVINGS_BALANCE_QUERY, SAVINGS_CONTRIBUTION_DRAFT, SAVINGS_WITHDRAWAL_DRAFT, UPCOMING_BILLS_QUERY, BILL_PAYMENT_DRAFT, BUDGET_STATUS_QUERY }

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
    val debtPaymentDirection: String? = null,
    val isCreditPurchase: Boolean = false,
    val requiresDebtClarification: Boolean = false
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
        "مئة" to 100L, "مائة" to 100L, "مائه" to 100L, "مئه" to 100L, "ميه" to 100L, "مية" to 100L,
        "مئتين" to 200L, "مائتين" to 200L, "ميتين" to 200L,
        "ثلاثمية" to 300L, "ثلاثميه" to 300L, "ثلاثمئة" to 300L, "ثلاثمائة" to 300L,
        "اربعمية" to 400L, "أربعمية" to 400L, "اربعمئة" to 400L, "أربعمئة" to 400L, "اربعمائة" to 400L, "أربعمائة" to 400L,
        "خمسمية" to 500L, "خمسمئة" to 500L, "خمسمائة" to 500L,
        "ستمية" to 600L, "ستمئة" to 600L, "ستمائة" to 600L,
        "سبعمية" to 700L, "سبعمئة" to 700L, "سبعمائة" to 700L,
        "ثمانمية" to 800L, "ثمانمئة" to 800L, "ثمانمائة" to 800L,
        "تسعمية" to 900L, "تسعمئة" to 900L, "تسعمائة" to 900L,
        "ألف" to 1_000L, "الف" to 1_000L, "آلاف" to 1_000L,
        "الفين" to 2_000L, "ألفين" to 2_000L, "مليون" to 1_000_000L, "مليونين" to 2_000_000L
    )

    // Keep description cleanup synchronized with the supported amount vocabulary.
    private val spokenNumberPattern = Regex(
        """(?<![\p{L}\p{N}])(?:[وفبكل]?)(?:${numberWords.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})(?![\p{L}\p{N}])"""
    )

    fun parse(raw: String): ParsedTransaction {
        val normalized = normalizeDigits(raw)
            .replace('٫', '.')
            .replace('،', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        val lower = normalized.lowercase()
        val detected = detectCommand(lower)
        val negated = creditNegated(lower)
        val command = if (negated && detected in setOf(
            VoiceCommandType.DEBT_CREATE_DRAFT, VoiceCommandType.DEBT_PAYMENT_DRAFT,
            VoiceCommandType.TRANSFER_DRAFT, VoiceCommandType.SAVINGS_CONTRIBUTION_DRAFT,
            VoiceCommandType.SAVINGS_WITHDRAWAL_DRAFT, VoiceCommandType.BILL_PAYMENT_DRAFT
        )) VoiceCommandType.TRANSACTION else detected
        val creditPurchase = command == VoiceCommandType.DEBT_CREATE_DRAFT && (isExplicitCreditPurchase(lower) || isShortCreditPurchase(lower))
        val amount = extractAmount(normalized)
        val incomeIntent = containsAny(lower, listOf(
            "دخل", "استلمت", "قبضت", "إيراد", "ايراد", "حول لي", "حوّل لي",
            "حصلت", "راتب", "رجع لي", "رجّع لي", "رجعلي", "وصلني", "جاني", "جاءني", "بعت", "ربحت"
        ))
        val expenseIntent = containsAny(lower, listOf(
            "صرف", "صرفت", "دفعت", "اشتريت", "شريت", "شراء", "مصروف", "كلفني", "دفعت حق", "خسرت"
        ))
        val negatedExpense = containsAny(lower, listOf(
            "ما صرفت", "لم اصرف", "لم أصرف", "ما دفعت", "لم ادفع", "لم أدفع",
            "ما شريت", "لم اشتر", "لم أشتري", "ما خسرت", "لم أخسر"
        ))
        val negatedIncome = containsAny(lower, listOf(
            "ما استلمت", "لم استلم", "لم أستلم", "ما قبضت",
            "ما بعت", "لم أبع", "لم ابيع", "ما ربحت", "لم أربح", "ما جاني", "ما جاءني"
        ))
        val kind = when {
            command != VoiceCommandType.TRANSACTION -> null
            negated || negatedExpense || negatedIncome -> null
            incomeIntent && !expenseIntent -> "INCOME"
            expenseIntent && !incomeIntent -> "EXPENSE"
            else -> null
        }

        val category = when {
            Regex("""(?<![\p{L}])قات(?![\p{L}])""").containsMatchIn(lower) -> "قات"
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
            .replace(spokenNumberPattern, " ")
            .replace(Regex("""(ريال|ريالات|ر\.ي|صرف|صرفت|دفعت|اشتريت|شريت|بعت|جاني|جاءني|خسرت|ربحت|استلمت|قبضت|سجل|سجّل)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifBlank { raw.trim() }

        return ParsedTransaction(
            amount = amount,
            kind = kind,
            category = category,
            description = if (creditPurchase) "شراء آجل: قات" else description,
            command = command,
            queryPeriod = detectPeriod(lower),
            relativeDayOffset = detectRelativeDay(lower),
            debtPersonName = if (creditPurchase) extractCreditPersonName(normalized) else extractDebtPersonName(normalized),
            debtType = if (creditPurchase) "OWED_BY_ME" else detectDebtType(lower),
            debtPaymentDirection = detectDebtPaymentDirection(lower),
            isCreditPurchase = creditPurchase,
            requiresDebtClarification = creditPurchase && !isExplicitCreditPurchase(lower)
        )
    }

    private fun detectCommand(text: String): VoiceCommandType = when {
        containsAny(text, listOf("ألغ المسودة", "الغ المسودة", "إلغاء المسودة", "الغِ العملية", "ألغ العملية", "لا تسجلها")) -> VoiceCommandType.CANCEL_DRAFT
        containsAny(text, listOf("صحح المبلغ", "صحح لي المبلغ", "عدل المبلغ", "عدّل المبلغ", "غير المبلغ", "غيّر المبلغ", "لا المبلغ", "المبلغ الصحيح")) -> VoiceCommandType.CORRECT_AMOUNT
        containsAny(text, listOf("كم باقي في الميزانية", "كم تبقى من الميزانية", "هل تجاوزت الميزانية", "حالة الميزانية", "وضع الميزانية", "قديش باقي في الميزانية")) -> VoiceCommandType.BUDGET_STATUS_QUERY
        containsAny(text, listOf(
            "سدد دين", "سددت دين", "سداد دين", "سدد الدين", "دفعت دين", "ادفع دين", "ادفع الدين", "أدفع الدين",
            "حصلت دين", "حصلت دين من", "حصّلت الدين", "تحصيل دين", "تحصيل دين من", "استلمت سداد الدين", "استلمت سداد من"
        )) -> VoiceCommandType.DEBT_PAYMENT_DRAFT
        containsAny(text, listOf("حوّل", "حول", "تحويل", "انقل")) && containsAny(text, listOf("من")) && containsAny(text, listOf("إلى", "الى")) -> VoiceCommandType.TRANSFER_DRAFT
        isExplicitCreditPurchase(text) || isShortCreditPurchase(text) -> VoiceCommandType.DEBT_CREATE_DRAFT
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
        containsAny(text, listOf("كم رصيدي", "كم معي", "قديش معي", "كم باقي معي", "رصيد محفظتي", "كم باقي", "كم تبقى")) -> VoiceCommandType.BALANCE_QUERY
        containsAny(text, listOf("كم صرفت", "قديش صرفت", "بكم صرفت", "كم صرفنا", "كم دفعت", "كم انفقت", "كم أنفقت", "إجمالي المصروف", "مجموع المصروف")) -> VoiceCommandType.SPENDING_QUERY
        containsAny(text, listOf("كم دخل", "قديش دخل", "كم دخل علي", "كم استلمت", "إجمالي الدخل", "مجموع الدخل")) -> VoiceCommandType.INCOME_QUERY
        containsAny(text, listOf("ملخص مالي", "ملخص حسابي", "اعطني ملخص", "أعطني ملخص", "التقرير المالي")) -> VoiceCommandType.SUMMARY_QUERY
        else -> VoiceCommandType.TRANSACTION
    }


    private fun creditNegated(text: String) = Regex("""(?:^|\s)(?:ما|لم|لا)\s""").containsMatchIn(text)
    private fun isExplicitCreditPurchase(text: String): Boolean =
        !creditNegated(text) && Regex("""(?<![\p{L}])قات(?![\p{L}])""").containsMatchIn(text) &&
            containsAny(text, listOf("بالدين", "على الحساب", "عالحساب", "آجل", "اجل", "تدينت", "تديّنت", "سجل علي", "سجّل علي")) &&
            !containsAny(text, listOf("استلفت", "اقترضت", "قرض", "سلفت"))

    private fun isShortCreditPurchase(text: String): Boolean =
        !creditNegated(text) && Regex("""^.+?\s+حق\s+قات\s+.+$""").matches(text) &&
            !containsAny(text, listOf("صرفت", "دفعت", "اشتريت", "شريت", "استلفت", "اقترضت", "سلفت", "قرض", "سدد", "سداد", "حصلت", "استلمت"))

    private fun extractCreditPersonName(text: String): String? {
        val tail = Regex("""(?:من|عند)\s+(.+)$""").find(text)?.groupValues?.get(1)
            ?: Regex("""حق\s+قات\s+(.+)$""").find(text)?.groupValues?.get(1)
            ?: return null
        val stops = listOfNotNull(
            digitAmount.find(tail)?.range?.first,
            spokenNumberPattern.find(tail)?.range?.first,
            Regex("""(?:^|\s)(?:قات|حق|بألف|بالف|ريال|بالدين|اليوم|أمس|امس|اول|أول)(?:\s|$)""").find(tail)?.range?.first
        )
        return tail.take(stops.minOrNull() ?: tail.length).trim().trim('،', ',', '.', '؟', '?')
            .replace(Regex("""\s+"""), " ").ifBlank { null }
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
        // Capture the full name, not its first token. Never strip an actual initial ل (لؤي).
        val tail = Regex("""(?:استلفت|اقترضت|سلفت)\s+من\s+(.+)$""").find(text)?.groupValues?.get(1)
            ?: Regex("""(?:أعطيت|اعطيت|أقرضت|اقرضت|سلفت)\s+(?:لـ)?(.+)$""").find(text)?.groupValues?.get(1)
            ?: Regex("""(?:حصلت دين من|تحصيل دين من|استلمت سداد من|حصلت دين|تحصيل دين|سددت دين|سدد الدين|سدد دين|سداد دين|دفعت دين)\s+(.+)$""").find(text)?.groupValues?.get(1)
            ?: return null
        return personBeforeAmount(tail)
    }

    private fun personBeforeAmount(tail: String): String? {
        val stops = listOfNotNull(
            digitAmount.find(tail)?.range?.first,
            spokenNumberPattern.find(tail)?.range?.first,
            Regex("""(?:^|\s)(?:ريال|سلفة|قرض|اليوم|أمس|امس|بالدين|حق)(?:\s|$)""").find(tail)?.range?.first
        )
        return tail.take(stops.minOrNull() ?: tail.length).trim().trim('؟', '?', '.', '،', ',')
            .replace(Regex("""\s+"""), " ").ifBlank { null }
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
        containsAny(text, listOf("هذا الاسبوع", "هذا الأسبوع", "الاسبوع الحالي", "الأسبوع الحالي", "هذا الاسبوع الحالي")) -> "WEEK"
        containsAny(text, listOf("الشهر الماضي")) -> "LAST_MONTH"
        containsAny(text, listOf("هذا الشهر", "الشهر الحالي")) -> "MONTH"
        containsAny(text, listOf("السنة الماضية", "العام الماضي")) -> "LAST_YEAR"
        containsAny(text, listOf("هذه السنة", "هذا العام", "السنة الحالية")) -> "YEAR"
        else -> null
    }

    private fun extractAmount(text: String): String? {
        // A single complete numeric token only: never turn -100 into 100, truncate
        // 123.456, select one of several amounts, or misread 2 ألف as 2.
        if (Regex("""[+\-−]\s*\d""").containsMatchIn(text)) return null
        val numericTokens = Regex("""[+\-−]?\d+(?:[.,٫٬]\d+)*""").findAll(text).toList()
        if (numericTokens.isNotEmpty()) {
            if (numericTokens.size != 1 || spokenNumberPattern.containsMatchIn(text)) return null
            val value = numericTokens.single().value.replace('٫', '.').replace(',', '.')
            return value.takeIf { Regex("""\d+(?:\.\d{1,2})?""").matches(it) }
        }
        val spokenParts = spokenNumberPattern.findAll(text).toList()
        if (spokenParts.zipWithNext().any { (a, b) ->
                text.substring(a.range.last + 1, b.range.first).any { !it.isWhitespace() && it != 'و' }
            }) return null
        val tokens = text.lowercase().replace(Regex("""[،,؟?.!]"""), " ").split(Regex("""\s+"""))
        var total = java.math.BigInteger.ZERO
        var group = java.math.BigInteger.ZERO
        var found = false
        var index = 0
        while (index < tokens.size) {
            val rawToken = tokens[index]
            val token = listOf(
                rawToken,
                rawToken.removePrefix("و"),
                rawToken.removePrefix("ف"),
                rawToken.removePrefix("ب"),
                rawToken.removePrefix("ل"),
                rawToken.removePrefix("ك")
            ).firstOrNull { it in numberWords } ?: rawToken
            val value = when {
                (token == "اثني" || token == "اثنا") && tokens.getOrNull(index + 1) == "عشر" -> {
                    index++
                    12L
                }
                else -> numberWords[token]
            }
            index++
            if (value == null) continue
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
