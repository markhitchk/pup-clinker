package com.harleytg.puppyclicker

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PuppyRosterScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var vm: PuppyClickerV6ViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = PuppyClickerV6ViewModel(app)
    }

    @Test
    fun rosterExposesRequiredFirstClassControls() {
        composeRule.setContent {
            MaterialTheme {
                PuppyRosterScreen(
                    state = vm.state.value,
                    vm = vm,
                    onUseConfirmed = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("Puppy Roster").assertExists()
        composeRule.onNodeWithText("All").assertExists()
        composeRule.onNodeWithText("Unlocked").assertExists()
        composeRule.onNodeWithText("Locked").assertExists()
        composeRule.onNodeWithText("Search puppies or ID").assertExists()
        composeRule.onNodeWithContentDescription("Sort puppies").assertExists()
        composeRule.onNodeWithContentDescription("Show favorites only").assertExists()
        composeRule.onNodeWithContentDescription("Open settings").assertExists()
    }
}
