package com.malekfinance

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertTextContains
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
    private lateinit var restoration: androidx.compose.ui.test.junit4.StateRestorationTester

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDb::class.java)
            .allowMainThreadQueries()
            .build()
        restoration = androidx.compose.ui.test.junit4.StateRestorationTester(composeRule)
        restoration.setContent {
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
        composeRule.onNodeWithTag("voice-workspace").performScrollToNode(hasText("إدخال يدوي بديل"))
        composeRule.onNodeWithText("إدخال يدوي بديل").assertExists()
    }

    @Test
    fun homePrivacySurvivesTabNavigation() {
        composeRule.onNodeWithContentDescription("إخفاء مبالغ الرئيسية").performClick()
        composeRule.onNodeWithText("الصوت").performClick()
        composeRule.onNodeWithText("الرئيسية").performClick()
        composeRule.onNodeWithContentDescription("إظهار مبالغ الرئيسية").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("إظهار مبالغ الرئيسية").performClick()
        composeRule.onNodeWithContentDescription("إخفاء مبالغ الرئيسية").assertIsDisplayed()
    }

    @Test
    fun offlineVoiceOffersModelImportWithoutSystemService() {
        composeRule.onNodeWithText("الصوت").performClick()
        composeRule.onNodeWithText("صوتك يبقى على جهازك.").assertExists()
        composeRule.onNodeWithTag("voice-workspace").performScrollToNode(hasText("تثبيت نموذج عربي من ملف ZIP"))
        composeRule.onNodeWithText("تثبيت نموذج عربي من ملف ZIP").assertExists()
    }

    @Test
    fun moneyToolsDoNotStackUnrelatedForms() {
        composeRule.onNodeWithText("مالي").performClick()
        composeRule.onNodeWithText("التحويلات").performClick()
        composeRule.onNodeWithText("تحويل بين محافظك").assertExists()
        composeRule.onNodeWithText("اسم التصنيف الجديد").assertDoesNotExist()
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
        composeRule.onNodeWithTag("voice-workspace").performScrollToNode(hasText("إدخال يدوي بديل"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("اختبار (YER)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("مصروف").performScrollTo().performClick()
        composeRule.onNodeWithText("المبلغ بعملة YER").performScrollTo().performTextInput("12.50")
        composeRule.onNodeWithText("وصف العملية").performScrollTo().performTextInput("اختبار الحفظ")
        composeRule.onNodeWithText("تأكيد وحفظ العملية").performScrollTo().performClick()

        composeRule.waitUntil(5_000) {
            runBlocking { db.dao().forWallet(walletId).isNotEmpty() }
        }
        val saved = db.dao().forWallet(walletId).single()
        assertEquals(1_250L, saved.amountMinor)
        assertEquals("EXPENSE", saved.kind)
        assertEquals("اختبار الحفظ", saved.note)
    }

    @Test
    fun voiceReviewSaveUsesCategoryWhenDescriptionIsEmpty() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "اختبار", currencyCode = "YER"))
        composeRule.onNodeWithText("الصوت").performClick()
        composeRule.onNodeWithTag("voice-workspace").performScrollToNode(hasText("إدخال يدوي بديل"))
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("اختبار (YER)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("مصروف").performScrollTo().performClick()
        composeRule.onNodeWithText("المبلغ بعملة YER").performScrollTo().performTextInput("7.25")
        composeRule.onNodeWithText("تأكيد وحفظ العملية").performScrollTo().performClick()

        composeRule.waitUntil(5_000) {
            runBlocking { db.dao().forWallet(walletId).isNotEmpty() }
        }
        val saved = db.dao().forWallet(walletId).single()
        assertEquals(725L, saved.amountMinor)
        assertEquals("EXPENSE", saved.kind)
        assertEquals("عام", saved.note)
    }


    @Test fun manuallyEnteredVoiceDraftSurvivesSavedStateRestoration() {
        composeRule.onNodeWithText("الصوت").performClick()
        composeRule.onNodeWithTag("voice-workspace").performScrollToNode(hasText("إدخال يدوي بديل"))
        composeRule.onNodeWithTag("manual-amount").performScrollTo().performTextInput("1500")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("voice-workspace").performScrollToNode(hasText("إدخال يدوي بديل"))
        composeRule.onNodeWithTag("manual-amount").performScrollTo().assertTextContains("1500")
    }
}
