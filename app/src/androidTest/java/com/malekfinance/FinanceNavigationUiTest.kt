package com.malekfinance

import android.content.Context
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.malekfinance.ui.FinanceApp
import com.malekfinance.ui.FinanceTheme
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FinanceNavigationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: FinanceDb

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDb::class.java)
            .allowMainThreadQueries()
            .build()
        composeRule.setContent {
            FinanceTheme {
                FinanceApp(db)
            }
        }
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun voiceScreenOpensAndShowsManualFallback() {
        composeRule.onNodeWithText("الصوت").performClick()
        composeRule.onNodeWithText("مساعدك المالي الصوتي").assertIsDisplayed()
        composeRule.onNodeWithText("إدخال يدوي بديل").assertExists()
    }

    @Test
    fun reportsScreenExplainsThatWalletIsRequired() {
        composeRule.onNodeWithText("التقارير").performClick()
        composeRule.onNodeWithText("التقارير حسب المحفظة").assertIsDisplayed()
        composeRule.onNodeWithText("لا توجد محافظ").assertExists()
    }

    @Test
    fun moneyScreenRemainsReachableFromBottomNavigation() {
        composeRule.onNodeWithText("مالي").performClick()
        composeRule.onNodeWithText("محافظك وأرصدتك").assertExists()
    }

    @Test
    fun manualVoiceReviewSavePersistsTransactionToRoom() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "اختبار", currencyCode = "YER"))
        composeRule.onNodeWithText("الصوت").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("اختبار (YER)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("مصروف").performClick()
        composeRule.onNodeWithText("المبلغ بعملة YER").performTextInput("12.50")
        composeRule.onNodeWithText("وصف العملية").performTextInput("اختبار الحفظ")
        composeRule.onNodeWithText("تأكيد وحفظ العملية").performClick()

        composeRule.waitUntil(5_000) {
            runBlocking { db.dao().forWallet(walletId).isNotEmpty() }
        }
        val saved = db.dao().forWallet(walletId).single()
        assertEquals(1_250L, saved.amountMinor)
        assertEquals("EXPENSE", saved.kind)
        assertEquals("اختبار الحفظ", saved.note)
    }

}
