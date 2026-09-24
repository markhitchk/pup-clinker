package com.harleytg.puppyclicker

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
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
        PuppyUiPreferences.setCasinoDisclaimerHidden(app, true)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun supportReportingInitializesSafelyWithoutConfiguredRelay() {
        PuppyUiPreferences.setAnonymousDiagnosticsEnabled(app, true)
        PuppyUiPreferences.setCrashReportsEnabled(app, true)

        PuppySupportReporting.initialize(app)
        PuppySupportReporting.reportTelemetry(app, "instrumentation_smoke")
    }

    @Test
    fun casinoFirstEntryWarnsAboutSimulatedGambling() {
        PuppyUiPreferences.setCasinoDisclaimerHidden(app, false)
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

        composeRule.onNodeWithText("Puppy Casino — Simulated Gambling").assertExists()
        composeRule.onNodeWithText(
            "No real money is used, no prizes have real-world cash value, and nothing can be cashed out."
        ).assertExists()
        composeRule.onNodeWithText("Don’t show again").assertExists()
        composeRule.onNodeWithText("Continue").assertExists()
        composeRule.onNodeWithText("Go Back").assertExists()
    }

    @Test
    fun casinoHubRendersAllSixGamesAndRewardSystems() {
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
        composeRule.onNodeWithText(
            "Simulated gambling • No real-money wagering or cash prizes."
        ).assertExists()
        composeRule.onNodeWithText("Casino Chip Wallet").assertExists()
        composeRule.onNodeWithText("🐾 Get Casino Chips").assertExists()
        composeRule.onNodeWithText("Puppy Slots").assertExists()
        composeRule.onNodeWithText("Puppy Roulette").assertExists()
        composeRule.onNodeWithText("Puppy Blackjack").assertExists()
        composeRule.onNodeWithText("Pup Plinko").assertExists()
        composeRule.onNodeWithText("Pup Scratchers").assertExists()
        composeRule.onNodeWithText("Lucky Pup Wheel").assertExists()
        composeRule.onNodeWithText("🎟️ Casino Upgrade Tickets").assertExists()
        composeRule.onNodeWithText("🐶 Casino Puppy Rewards").assertExists()
    }

    @Test
    fun allCasinoGameScreensRenderWithoutCrashing() {
        val vm = PuppyClickerV6ViewModel(app)
        val selectedGame = mutableStateOf(PuppyCasinoGame.SLOTS)

        composeRule.setContent {
            MaterialTheme {
                when (selectedGame.value) {
                    PuppyCasinoGame.SLOTS ->
                        PuppySlotsScreen(vm.state.value, vm, onBack = {})
                    PuppyCasinoGame.ROULETTE ->
                        PuppyRouletteScreen(vm.state.value, vm, onBack = {})
                    PuppyCasinoGame.BLACKJACK ->
                        PuppyBlackjackScreen(vm.state.value, vm, onBack = {})
                    PuppyCasinoGame.PLINKO ->
                        PuppyPlinkoScreen(vm.state.value, vm, onBack = {})
                    PuppyCasinoGame.SCRATCHERS ->
                        PuppyScratchersScreen(vm.state.value, vm, onBack = {})
                    PuppyCasinoGame.LUCKY_WHEEL ->
                        PuppyLuckyWheelScreen(vm.state.value, vm, onBack = {})
                }
            }
        }

        val expectedTitles = listOf(
            PuppyCasinoGame.SLOTS to "Puppy Slots",
            PuppyCasinoGame.ROULETTE to "Puppy Roulette",
            PuppyCasinoGame.BLACKJACK to "Puppy Blackjack",
            PuppyCasinoGame.PLINKO to "Pup Plinko",
            PuppyCasinoGame.SCRATCHERS to "Pup Scratchers",
            PuppyCasinoGame.LUCKY_WHEEL to "Lucky Pup Wheel"
        )

        expectedTitles.forEach { (game, title) ->
            composeRule.runOnIdle {
                selectedGame.value = game
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(title).assertExists()
        }
    }

    @Test
    fun casinoRuntimeGuardContainsUnexpectedCommandExceptions() {
        val result = PuppyCasinoRuntimeGuard.run(
            PuppyCasinoGame.SLOTS,
            "instrumentation_failure"
        ) {
            error("intentional casino runtime guard test")
        }

        org.junit.Assert.assertTrue(result.isFailure)
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
        editor.putLong("casino_chips_v7", 900L)
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
    fun pupCoinAccessoryPurchasePersistsOwnershipAndBlocksFreeEquip() {
        check(prefs.edit().putLong("pup_coins_v7", 200L).commit())
        val vm = PuppyClickerV6ViewModel(app)

        vm.setAccessory("Bandana")
        org.junit.Assert.assertEquals("None", vm.state.value.accessory)

        org.junit.Assert.assertTrue(vm.buyAccessoryWithPupCoins("Bandana"))
        org.junit.Assert.assertEquals(50L, vm.state.value.pupCoins)
        org.junit.Assert.assertTrue("Bandana" in vm.state.value.ownedAccessories)

        vm.setAccessory("Bandana")
        org.junit.Assert.assertEquals("Bandana", vm.state.value.accessory)

        val reloaded = PuppyClickerV6ViewModel(app)
        org.junit.Assert.assertTrue("Bandana" in reloaded.state.value.ownedAccessories)
        org.junit.Assert.assertEquals("Bandana", reloaded.state.value.accessory)
    }

    @Test
    fun systemRewardClaimIsIdempotentAcrossRepeatedClaims() {
        app.getSharedPreferences(PuppyNotificationHistory.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        PuppyNotificationHistory.initialize(app)
        val rewardId = "test-system-reward-idempotency"
        PuppyNotificationHistory.recordSystemReward(
            context = app,
            id = rewardId,
            title = "System reward",
            body = "Claim this reward.",
            currency = PuppyRewardCurrency.TREATS,
            amount = 500L,
            createdAtMs = 1234L
        )

        val vm = PuppyClickerV6ViewModel(app)
        org.junit.Assert.assertTrue(vm.claimSystemReward(rewardId))
        org.junit.Assert.assertEquals(500L, vm.state.value.treats)
        org.junit.Assert.assertEquals(500L, vm.state.value.lifetimeTreats)

        org.junit.Assert.assertTrue(vm.claimSystemReward(rewardId))
        org.junit.Assert.assertEquals(500L, vm.state.value.treats)
        org.junit.Assert.assertEquals(500L, vm.state.value.lifetimeTreats)

        val claimed = PuppyNotificationHistory.findById(app, rewardId)
        org.junit.Assert.assertTrue(claimed?.claimed == true)
        org.junit.Assert.assertTrue(claimed?.read == true)
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
            "New wagers are blocked to protect your Casino Chip balance.",
            substring = true
        ).assertExists()
    }
}
