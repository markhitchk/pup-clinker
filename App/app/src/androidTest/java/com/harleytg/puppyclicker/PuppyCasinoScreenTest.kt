package com.harleytg.puppyclicker

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PuppyCasinoScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private val prefs
        get() = app.getSharedPreferences(
            PuppyClickerV6ViewModel.PREFS_NAME,
            Context.MODE_PRIVATE
        )

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun casinoHubRendersAllThreeGamesAndRewardSystems() {
        val vm = PuppyClickerV6ViewModel(app)

        composeRule.setContent {
            MaterialTheme {
                PuppyCasinoHub(
                    state = vm.state.value,
                    vm = vm,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Puppy Casino").assertExists()
        composeRule.onNodeWithText("Puppy Slots").assertExists()
        composeRule.onNodeWithText("Puppy Roulette").assertExists()
        composeRule.onNodeWithText("Puppy Blackjack").assertExists()
        composeRule.onNodeWithText("🎟️ Casino Upgrade Tickets").assertExists()
        composeRule.onNodeWithText("🐶 Casino Puppy Rewards").assertExists()
    }

    @Test
    fun acceptedSlotsRoundExposesRefundRecoveryAfterViewModelRestart() {
        val editor = prefs.edit()
        PuppyCasinoPersistence.write(
            editor = editor,
            activeRound = PuppyCasinoRound(
                roundId = "android_slots_recovery_01",
                game = PuppyCasinoGame.SLOTS,
                wagerTreats = 100L,
                state = PuppyCasinoRoundState.WAGER_ACCEPTED,
                acceptedAtMs = 1_000L
            ),
            completedRoundIds = emptyList()
        )
        editor.putLong("treats", 900L)
        check(editor.commit())

        val vm = PuppyClickerV6ViewModel(app)

        composeRule.setContent {
            MaterialTheme {
                PuppyCasinoHub(
                    state = vm.state.value,
                    vm = vm,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Interrupted casino round").assertExists()
        composeRule.onNodeWithText("Refund Accepted Wager").assertExists()
    }

    @Test
    fun malformedPersistedRoundShowsRecoveryProtectionAndBlocksFreshPlay() {
        check(
            prefs.edit()
                .putString(PuppyCasinoPersistence.ACTIVE_ROUND_KEY, "{bad-json")
                .commit()
        )

        val vm = PuppyClickerV6ViewModel(app)

        composeRule.setContent {
            MaterialTheme {
                PuppyCasinoHub(
                    state = vm.state.value,
                    vm = vm,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("⚠️ Casino recovery protection").assertExists()
        composeRule.onNodeWithText(
            "New wagers are blocked to protect your Treat balance.",
            substring = true
        ).assertExists()
    }
}
