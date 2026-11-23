package com.malekfinance

import android.content.Context
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.malekfinance.ui.FinanceApp
import com.malekfinance.ui.FinanceTheme
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
}
