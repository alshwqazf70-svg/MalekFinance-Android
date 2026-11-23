package com.malekfinance.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTransactionParserTest {
    @Test fun parsesArabicDigitsAndExpenseCategory() {
        val parsed = VoiceTransactionParser.parse("صرف ١٥٠٠ ريال بنزين")
        assertEquals("1500", parsed.amount)
        assertEquals("EXPENSE", parsed.kind)
        assertEquals("بنزين", parsed.category)
        assertEquals("بنزين", parsed.description)
    }

    @Test fun detectsSalaryAsIncome() {
        val parsed = VoiceTransactionParser.parse("استلمت راتب 25000 ريال")
        assertEquals("25000", parsed.amount)
        assertEquals("INCOME", parsed.kind)
        assertEquals("راتب", parsed.category)
    }

    @Test fun supportsPersianDigitsAndDecimalAmounts() {
        val parsed = VoiceTransactionParser.parse("مطعم ۱۲۳٫۵٠")
        assertEquals("123.50", parsed.amount)
        assertEquals("مطاعم", parsed.category)
    }

    @Test fun doesNotGuessWhenIncomeAndExpenseIntentAreBothPresent() {
        val parsed = VoiceTransactionParser.parse("استلمت ١٠٠٠ ثم صرفت ٢٠٠")
        assertEquals(null, parsed.kind)
    }

    @Test fun doesNotGuessTransactionTypeFromAmountAndCategoryAlone() {
        val parsed = VoiceTransactionParser.parse("١٥٠٠ ريال بنزين")
        assertEquals(null, parsed.kind)
    }

    @Test fun detectsExpenseIntentEvenWhenAmountIsMissing() {
        val parsed = VoiceTransactionParser.parse("اشتريت أغراض من السوق")
        assertEquals(null, parsed.amount)
        assertEquals("EXPENSE", parsed.kind)
    }
    @Test fun parsesSpokenYemeniThousandsAndTens() {
        val parsed = VoiceTransactionParser.parse("صرفت خمسة آلاف ريال بنزين")
        assertEquals("5000", parsed.amount)
        assertEquals("EXPENSE", parsed.kind)
        assertEquals("بنزين", parsed.category)
    }

    @Test fun parsesCompoundSpokenAmounts() {
        assertEquals("150000", VoiceTransactionParser.parse("استلمت مئة وخمسين ألف راتب").amount)
        assertEquals("25000", VoiceTransactionParser.parse("دفعت خمسة وعشرين ألف إيجار").amount)
        assertEquals("2000", VoiceTransactionParser.parse("صرفت ألفين للمواصلات").amount)
    }

    @Test fun recognizesBalanceAndPeriodQueriesWithoutCreatingTransactions() {
        val balance = VoiceTransactionParser.parse("كم باقي معي في المحفظة؟")
        assertEquals(VoiceCommandType.BALANCE_QUERY, balance.command)
        assertEquals(null, balance.kind)

        val spending = VoiceTransactionParser.parse("كم صرفت هذا الأسبوع؟")
        assertEquals(VoiceCommandType.SPENDING_QUERY, spending.command)
        assertEquals("WEEK", spending.queryPeriod)
        assertEquals(null, spending.kind)
    }

    @Test fun recognizesIncomeSummaryAndYemeniTodayExpressions() {
        val income = VoiceTransactionParser.parse("كم استلمت هذا الشهر؟")
        assertEquals(VoiceCommandType.INCOME_QUERY, income.command)
        assertEquals("MONTH", income.queryPeriod)
        assertEquals("عام", VoiceTransactionParser.parse("كم دخل هذا الشهر؟").category)

        val summary = VoiceTransactionParser.parse("اعطني ملخص مالي")
        assertEquals(VoiceCommandType.SUMMARY_QUERY, summary.command)
    }

    @Test fun negatedActionsDoNotBecomeFinancialTransactions() {
        assertEquals(null, VoiceTransactionParser.parse("ما صرفت شيء").kind)
        assertEquals(null, VoiceTransactionParser.parse("لم أستلم راتبي").kind)
    }

    @Test fun keepsAmbiguousMultipleMoneyIntentsUnresolved() {
        assertEquals(null, VoiceTransactionParser.parse("استلمت ألف ثم صرفت خمسمئة").kind)
    }

    @Test fun parsesRelativeTransactionDatesWithoutGuessingOtherDates() {
        assertEquals(-1, VoiceTransactionParser.parse("دفعت ألف ريال بنزين أمس").relativeDayOffset)
        assertEquals(0, VoiceTransactionParser.parse("صرفت مئة ريال اليوم").relativeDayOffset)
        assertEquals(-2, VoiceTransactionParser.parse("اشتريت أغراض أول أمس").relativeDayOffset)
        assertEquals(null, VoiceTransactionParser.parse("صرفت مئة ريال").relativeDayOffset)
    }

    @Test fun recognizesSpokenDraftCancellation() {
        assertEquals(VoiceCommandType.CANCEL_DRAFT, VoiceTransactionParser.parse("ألغ المسودة").command)
        assertEquals(VoiceCommandType.CANCEL_DRAFT, VoiceTransactionParser.parse("لا تسجلها").command)
    }

    @Test fun recognizesRecentTransactionHistoryQuery() {
        assertEquals(
            VoiceCommandType.RECENT_TRANSACTIONS_QUERY,
            VoiceTransactionParser.parse("اعرض آخر العمليات").command
        )
    }

    @Test fun recognizesBothDebtBalanceQueryDirections() {
        assertEquals(
            VoiceCommandType.DEBT_OWED_BY_ME_QUERY,
            VoiceTransactionParser.parse("كم علي من ديون؟").command
        )
        assertEquals(
            VoiceCommandType.DEBT_OWED_TO_ME_QUERY,
            VoiceTransactionParser.parse("كم لي عند أحمد؟").command
        )
        assertEquals("أحمد", VoiceTransactionParser.parse("كم لي عند أحمد؟").debtPersonName)
    }

    @Test fun recognizesSavingsBalanceQuestion() {
        assertEquals(
            VoiceCommandType.SAVINGS_BALANCE_QUERY,
            VoiceTransactionParser.parse("كم ادخرت حتى الآن؟").command
        )
    }

    @Test fun recognizesUpcomingBillsQuery() {
        assertEquals(
            VoiceCommandType.UPCOMING_BILLS_QUERY,
            VoiceTransactionParser.parse("ما الفواتير المستحقة؟").command
        )
    }

    @Test fun recognizesTransferRequestAsDraftNotIncomeOrExpense() {
        val parsed = VoiceTransactionParser.parse("حوّل ١٠٠ ريال من النقد إلى البنك")
        assertEquals(VoiceCommandType.TRANSFER_DRAFT, parsed.command)
        assertEquals("100", parsed.amount)
        assertEquals(null, parsed.kind)
    }

    @Test fun recognizesBudgetStatusQuestions() {
        assertEquals(
            VoiceCommandType.BUDGET_STATUS_QUERY,
            VoiceTransactionParser.parse("كم باقي في الميزانية؟").command
        )
    }

    @Test fun parsesLentAndBorrowedDebtDraftsWithoutSavingThem() {
        val lent = VoiceTransactionParser.parse("أعطيت أحمد عشرين ألف سلفة")
        assertEquals(VoiceCommandType.DEBT_CREATE_DRAFT, lent.command)
        assertEquals("OWED_TO_ME", lent.debtType)
        assertEquals("أحمد", lent.debtPersonName)
        assertEquals("20000", lent.amount)

        val borrowed = VoiceTransactionParser.parse("استلفت من خالد خمسة آلاف")
        assertEquals(VoiceCommandType.DEBT_CREATE_DRAFT, borrowed.command)
        assertEquals("OWED_BY_ME", borrowed.debtType)
        assertEquals("خالد", borrowed.debtPersonName)
        assertEquals("5000", borrowed.amount)

        val borrowedYemeni = VoiceTransactionParser.parse("سلفت من أحمد خمسة آلاف")
        assertEquals("OWED_BY_ME", borrowedYemeni.debtType)
        assertEquals("أحمد", borrowedYemeni.debtPersonName)
    }

    @Test fun distinguishesFirstDayBeforeYesterdayFromYesterdayAndPreviousPeriods() {
        assertEquals("DAY_BEFORE_YESTERDAY", VoiceTransactionParser.parse("كم صرفت أول أمس؟").queryPeriod)
        assertEquals("YESTERDAY", VoiceTransactionParser.parse("كم صرفت أمس؟").queryPeriod)
        assertEquals("LAST_MONTH", VoiceTransactionParser.parse("كم صرفت الشهر الماضي؟").queryPeriod)
        assertEquals("LAST_WEEK", VoiceTransactionParser.parse("كم صرفت الأسبوع الماضي؟").queryPeriod)
    }

    @Test fun parsesSavingsContributionAndWithdrawalDrafts() {
        val contribution = VoiceTransactionParser.parse("أضف للادخار ٥٠٠ ريال")
        assertEquals(VoiceCommandType.SAVINGS_CONTRIBUTION_DRAFT, contribution.command)
        assertEquals("500", contribution.amount)

        val withdrawal = VoiceTransactionParser.parse("اسحب من الادخار ٢٠٠ ريال")
        assertEquals(VoiceCommandType.SAVINGS_WITHDRAWAL_DRAFT, withdrawal.command)
        assertEquals("200", withdrawal.amount)
    }

    @Test fun recognizesBillPaymentDraftRequests() {
        assertEquals(
            VoiceCommandType.BILL_PAYMENT_DRAFT,
            VoiceTransactionParser.parse("سدد فاتورة الكهرباء").command
        )
    }

    @Test fun recognizesBillPaymentAsReviewableDraft() {
        assertEquals(
            VoiceCommandType.BILL_PAYMENT_DRAFT,
            VoiceTransactionParser.parse("ادفع فاتورة الكهرباء").command
        )
    }

    @Test fun recognizesSpokenDebtRepaymentAndCollectionAsSeparateDrafts() {
        val repayment = VoiceTransactionParser.parse("سددت دين أحمد ٥٠٠ ريال")
        assertEquals(VoiceCommandType.DEBT_PAYMENT_DRAFT, repayment.command)
        assertEquals("OWED_BY_ME", repayment.debtPaymentDirection)
        assertEquals("500", repayment.amount)

        val collection = VoiceTransactionParser.parse("حصلت دين أحمد ٧٥٠ ريال")
        assertEquals(VoiceCommandType.DEBT_PAYMENT_DRAFT, collection.command)
        assertEquals("OWED_TO_ME", collection.debtPaymentDirection)
        assertEquals("750", collection.amount)
    }

    @Test fun ordinaryMoneyReceivedFromSomeoneIsNotMisclassifiedAsDebtCollection() {
        val parsed = VoiceTransactionParser.parse("حصلت من أحمد ٥٠٠ ريال")
        assertEquals(VoiceCommandType.TRANSACTION, parsed.command)
        assertEquals("INCOME", parsed.kind)
        assertEquals(null, parsed.debtPaymentDirection)
    }

    @Test fun recognizesPartialDebtPaymentOrCollectionDraft() {
        val parsed = VoiceTransactionParser.parse("سدد دين أحمد ٣٠٠ ريال")
        assertEquals(VoiceCommandType.DEBT_PAYMENT_DRAFT, parsed.command)
        assertEquals("أحمد", parsed.debtPersonName)
        assertEquals("300", parsed.amount)
    }

    @Test fun recognizesSpokenAmountCorrectionWithoutCreatingANewTransaction() {
        val correction = VoiceTransactionParser.parse("لا، المبلغ الصحيح ثلاثة آلاف")
        assertEquals(VoiceCommandType.CORRECT_AMOUNT, correction.command)
        assertEquals("3000", correction.amount)
        assertEquals(null, correction.kind)
    }

    @Test fun parsesMultiWordTwelve() {
        assertEquals("12", VoiceTransactionParser.parse("دفعت اثني عشر ريال").amount)
        assertEquals("12", VoiceTransactionParser.parse("دفعت اثنا عشر ريال").amount)
    }

    @Test fun parsesFormalAndColloquialHundredsAcrossArabicSpellings() {
        assertEquals("500", VoiceTransactionParser.parse("دفعت خمسمائة ريال").amount)
        assertEquals("300", VoiceTransactionParser.parse("دفعت ثلاثمائة ريال").amount)
        assertEquals("400", VoiceTransactionParser.parse("دفعت أربعمائة ريال").amount)
        assertEquals("600", VoiceTransactionParser.parse("دفعت ستمائة ريال").amount)
        assertEquals("700", VoiceTransactionParser.parse("دفعت سبعمائة ريال").amount)
        assertEquals("800", VoiceTransactionParser.parse("دفعت ثمانمائة ريال").amount)
        assertEquals("900", VoiceTransactionParser.parse("دفعت تسعمائة ريال").amount)
        assertEquals("100", VoiceTransactionParser.parse("دفعت مائة ريال").amount)
    }

    @Test fun understandsYemeniColloquialNumbersAndQueries() {
        assertEquals("500", VoiceTransactionParser.parse("صرفت خمسمية ريال").amount)
        assertEquals("300", VoiceTransactionParser.parse("دفعت ثلاثمية ريال").amount)
        assertEquals(VoiceCommandType.SPENDING_QUERY, VoiceTransactionParser.parse("قديش صرفت هذا الشهر؟").command)
        assertEquals(VoiceCommandType.BALANCE_QUERY, VoiceTransactionParser.parse("قديش معي؟").command)
    }


    @Test fun extractsSpokenAmountsAttachedToArabicPrepositions() {
        assertEquals("500", VoiceTransactionParser.parse("شريت مقاضي بخمسمية ريال").amount)
        assertEquals("2000", VoiceTransactionParser.parse("دفعت بألفين ريال").amount)
        assertEquals("500", VoiceTransactionParser.parse("دفعت و خمسمية ريال").amount)
    }

    @Test fun understandsCommonYemeniPurchaseAndSalePhrases() {
        val purchase = VoiceTransactionParser.parse("شريت مقاضي بخمسمية ريال")
        assertEquals("500", purchase.amount)
        assertEquals("EXPENSE", purchase.kind)
        assertEquals("بقالة", purchase.category)
        val sale = VoiceTransactionParser.parse("بعت غرض بألفين ريال")
        assertEquals("2000", sale.amount)
        assertEquals("INCOME", sale.kind)
    }

    @Test fun recognizesColloquialIncomeAndProfitWithoutGuessingWhenMixed() {
        val received = VoiceTransactionParser.parse("جاني مبلغ ثلاثة آلاف")
        assertEquals("3000", received.amount)
        assertEquals("INCOME", received.kind)
        val mixed = VoiceTransactionParser.parse("جاني ألف وصرفت منه مئتين")
        assertEquals(null, mixed.kind)
    }

    @Test fun recognizesColloquialLossAsExpense() {
        val parsed = VoiceTransactionParser.parse("خسرت مية ريال")
        assertEquals("100", parsed.amount)
        assertEquals("EXPENSE", parsed.kind)
    }


    @Test fun negatedYemeniColloquialActionsNeverBecomeTransactions() {
        assertEquals(null, VoiceTransactionParser.parse("ما شريت شيء").kind)
        assertEquals(null, VoiceTransactionParser.parse("ما بعت شيء").kind)
        assertEquals(null, VoiceTransactionParser.parse("ما خسرت فلوس").kind)
        assertEquals(null, VoiceTransactionParser.parse("ما جاني دخل").kind)
    }

}
