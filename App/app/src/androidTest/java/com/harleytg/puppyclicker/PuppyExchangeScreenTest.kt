package com.harleytg.puppyclicker

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PuppyExchangeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var vm: PuppyClickerV6ViewModel

    @Before
    fun setUp() {
        vm = PuppyClickerV6ViewModel(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    fun exchangeUsesDedicatedFiveTabUi() {
        composeRule.setContent {
            MaterialTheme {
                PuppyExchangeScreen(
                    state = vm.state.value,
                    vm = vm,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Puppy Exchange").assertExists()
        composeRule.onNodeWithText("Friends").assertExists()
        composeRule.onNodeWithText("Connect").assertExists().performClick()
        composeRule.onNodeWithText("Gifts").assertExists()
        composeRule.onNodeWithText("Trade").assertExists()
        composeRule.onNodeWithText("History").assertExists()
        composeRule.onNodeWithText("Manual Offer / Answer").assertExists()
    }
}
