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
        composeRule.onNodeWithText("Add Friend").assertExists()
        composeRule.onNodeWithText("Connect").assertExists().performClick()
        composeRule.onNodeWithText("Gifts").assertExists()
        composeRule.onNodeWithText("Trade").assertExists()
        composeRule.onNodeWithText("History").assertExists()
        composeRule.onNodeWithText("Connection Setup").assertExists()
        composeRule.onNodeWithText("Your Friend Code").assertExists()
        composeRule.onNodeWithText("Start Connection").assertExists()
        composeRule.onNodeWithText("Advanced Direct Connection").assertExists()
    }
    @Test
    fun recoveryRequiredTradeExposesRecoveryWorkflow() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val ledger = PuppyExchangeLedger(app)
        val localPlayerId = PuppyPlayerIdentity.playerId(app)
        val remotePlayerId = if (localPlayerId == "PC-11111111111111111111111111111111") {
            "PC-22222222222222222222222222222222"
        } else {
            "PC-11111111111111111111111111111111"
        }
        val localOffer = listOf("classic")
        val remoteOffer = listOf("v2_frog")

        ledger.putTransaction(
            ExchangeTransactionRecord(
                transactionId = "XT-recovery-ui-test",
                type = ExchangeTransactionType.TRADE,
                playerAId = localPlayerId,
                playerBId = remotePlayerId,
                offerARecordIds = localOffer,
                offerBRecordIds = remoteOffer,
                offerAHash = PuppyExchangeProtocol.canonicalOfferHash(localOffer),
                offerBHash = PuppyExchangeProtocol.canonicalOfferHash(remoteOffer),
                createdAtMs = System.currentTimeMillis(),
                state = ExchangeTransactionState.RECOVERY_REQUIRED
            )
        )

        composeRule.setContent {
            MaterialTheme {
                PuppyExchangeScreen(
                    state = vm.state.value,
                    vm = vm,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("History").assertExists().performClick()
        composeRule.onNodeWithText("Recover Trade").assertExists().performClick()
        composeRule.onNodeWithText("Complete Trade").assertExists()
        composeRule.onNodeWithText("Cancel Trade").assertExists()
        composeRule.onNodeWithText("Generate My Recovery Code").assertExists()
        composeRule.onNodeWithText("Other Player's Recovery Code").assertExists()
        composeRule.onNodeWithText("Apply Other Player Code").assertExists()
    }

}
