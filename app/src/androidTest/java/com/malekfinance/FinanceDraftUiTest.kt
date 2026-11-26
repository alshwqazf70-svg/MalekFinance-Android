package com.malekfinance

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.malekfinance.ui.FinanceApp
import com.malekfinance.ui.FinanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Compile in CI; execute only on a physical Android device. No microphone required. */
class FinanceDraftUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: FinanceDb
    private lateinit var restoration: StateRestorationTester
    private var walletId = 0L
    private var goalId = 0L
    private var billId = 0L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FinanceDb::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            walletId = db.walletDao().insert(FinanceWallet(name = "محفظة اختبار", openingBalanceMinor = 1_000_000))
            goalId = db.savingsGoalDao().insert(FinanceSavingsGoal(name = "هدف اختبار", targetMinor = 100_000, currencyCode = "YER", walletId = walletId))
            billId = db.billDao().insert(FinanceBill(title = "كهرباء اختبار", categoryName = "عام", amountMinor = 10_000,
                currencyCode = "YER", walletId = walletId, frequency = "MONTHLY", anchorDay = 1, anchorMonth = 0,
                nextDueAt = System.currentTimeMillis() - 86_400_000))
        }
        restoration = StateRestorationTester(compose)
        restoration.setContent { FinanceTheme { FinanceApp(db) } }
        compose.onNodeWithText("المساعد").performClick()
    }

    @After fun tearDown() { if (::db.isInitialized) db.close() }

    private fun command(text: String) {
        compose.onNodeWithTag("speech-command").performScrollTo().performTextClearance()
        compose.onNodeWithTag("speech-command").performTextInput(text)
        compose.onNodeWithText("مراجعة العبارة").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("voice-workspace").performScrollToNode(hasTestTag(tag))
    }

    @Test fun billReviewAndRestorationDoNotPayUntilExplicitConfirmation() = runBlocking {
        command("ادفع فاتورة كهرباء اختبار")
        scrollTo("voice-bill-draft")
        compose.onNodeWithText("كهرباء اختبار · 100.00 YER").performScrollTo().performClick()
        assertEquals(0, db.dao().forWallet(walletId).size)
        restoration.emulateSavedInstanceStateRestore()
        scrollTo("confirm-voice-bill")
        assertEquals(0, db.billPaymentDao().forBill(billId).size)
        compose.onNodeWithTag("confirm-voice-bill").performClick()
        compose.waitUntil(5_000) { runBlocking { db.billPaymentDao().forBill(billId).size == 1 } }
        assertEquals(1, db.dao().forWallet(walletId).size)
        assertEquals(10_000L, db.dao().forWallet(walletId).single().amountMinor)
    }

    @Test fun savingsContributionAndWithdrawalRequireSeparateConfirmations() = runBlocking {
        command("أضف للادخار هدف اختبار 100")
        scrollTo("voice-savings-draft")
        compose.onNodeWithText("هدف اختبار · YER").performScrollTo().performClick()
        assertEquals(0, db.savingsEntryDao().forGoal(goalId).size)
        scrollTo("confirm-voice-savings")
        compose.onNodeWithTag("confirm-voice-savings").performClick()
        compose.waitUntil(5_000) { runBlocking { db.savingsEntryDao().forGoal(goalId).size == 1 } }
        command("اسحب من الادخار هدف اختبار 25")
        scrollTo("voice-savings-draft")
        compose.onNodeWithText("هدف اختبار · YER").performScrollTo().performClick()
        assertEquals(1, db.savingsEntryDao().forGoal(goalId).size)
        scrollTo("confirm-voice-savings")
        compose.onNodeWithTag("confirm-voice-savings").performClick()
        compose.waitUntil(5_000) { runBlocking { db.savingsEntryDao().forGoal(goalId).size == 2 } }
        assertEquals("7500", FinanceSavingsPolicy.balance(db.savingsEntryDao().forGoal(goalId)).toString())
        assertEquals(listOf("SAVINGS_CONTRIBUTION", "SAVINGS_WITHDRAWAL"), db.dao().forWallet(walletId).sortedBy { it.id }.map { it.kind })
    }

    @Test fun spokenCancellationClearsBillAndAllowsNewOrdinaryDraft() = runBlocking {
        command("ادفع فاتورة كهرباء اختبار")
        command("ألغ المسودة")
        command("صرفت 50 ريال بنزين")
        scrollTo("manual-amount")
        compose.onNodeWithTag("manual-amount").assertTextContains("50")
        compose.onNodeWithTag("voice-bill-draft").assertDoesNotExist()
        assertEquals(0, db.dao().forWallet(walletId).size)
    }

    @Test fun newDraftReplacesBillButReadOnlyQueryPreservesSavings() = runBlocking {
        command("ادفع فاتورة كهرباء اختبار")
        command("أضف للادخار هدف اختبار 100")
        scrollTo("voice-savings-draft")
        command("كم رصيدي")
        scrollTo("voice-savings-draft")
        compose.onNodeWithTag("voice-savings-draft").assertIsDisplayed()
        compose.onNodeWithTag("voice-bill-draft").assertDoesNotExist()
        scrollTo("cancel-voice-savings")
        compose.onNodeWithTag("cancel-voice-savings").performClick()
        scrollTo("manual-amount")
        assertEquals(0, db.dao().forWallet(walletId).size)
    }

    @Test fun unknownBillCannotBeConfirmedAndCanBeCancelled() = runBlocking {
        command("ادفع فاتورة مجهولة")
        scrollTo("confirm-voice-bill")
        compose.onNodeWithTag("confirm-voice-bill").assertIsNotEnabled()
        scrollTo("cancel-voice-bill")
        compose.onNodeWithTag("cancel-voice-bill").performClick()
        scrollTo("manual-amount")
        assertEquals(0, db.billPaymentDao().forBill(billId).size)
    }
}
