package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyBlackjackIntegrationContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun initialDealIsPersistedWithAcceptedWager() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        val create = viewModel.indexOf("PuppyBlackjackEngine.newRound(wagerTreats)")
        val accept = viewModel.indexOf("val accepted = beginCasinoRound(", create)

        assertTrue(create >= 0)
        assertTrue(accept > create)
        assertTrue(viewModel.contains("wagerPayload = PuppyBlackjackStateCodec.encode(blackjackState)"))
    }

    @Test
    fun hitStandDoubleAndSplitUseSavedRoundState() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(viewModel.contains("internal fun blackjackHit()"))
        assertTrue(viewModel.contains("internal fun blackjackStand()"))
        assertTrue(viewModel.contains("internal fun blackjackDouble()"))
        assertTrue(viewModel.contains("internal fun blackjackSplit()"))
        assertTrue(viewModel.contains("PuppyCasinoTransactionEngine.updateAcceptedRound("))
        assertTrue(viewModel.contains("additionalWagerTreats = actionResult.additionalWagerTreats"))
    }

    @Test
    fun doubleAndSplitAdditionalWagersAreAtomicWithHandState() {
        val transactions = source("PuppyCasinoTransactions.kt")

        assertTrue(transactions.contains("fun updateAcceptedRound("))
        assertTrue(transactions.contains("before.treats < additionalWagerTreats"))
        assertTrue(transactions.contains("wagerTreats = totalWager"))
        assertTrue(transactions.contains("wagerPayload = payload"))
        assertTrue(transactions.contains("state = before.copy(treats = before.treats - additionalWagerTreats)"))
    }

    @Test
    fun blackjackSettlementRevalidatesStateWagerAndOutcome() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(viewModel.contains("PuppyBlackjackStateCodec.decodeAndValidate(active.wagerPayload)"))
        assertTrue(viewModel.contains("PuppyBlackjackEngine.totalWager(blackjackState) == active.wagerTreats"))
        assertTrue(viewModel.contains("PuppyBlackjackOutcomeCodec.decodeAndValidate("))
        assertTrue(viewModel.contains("it.totalPayoutTreats == active.payoutTreats"))
    }

    @Test
    fun validBlackjackHandCannotUseGenericRefundEscape() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")
        val hub = source("PuppyCasinoHub.kt")

        assertTrue(viewModel.contains("active.game == PuppyCasinoGame.BLACKJACK"))
        assertTrue(viewModel.contains("PuppyBlackjackStateCodec.decodeAndValidate(active.wagerPayload) != null"))
        assertTrue(hub.contains("Resume Blackjack Hand"))
    }

    @Test
    fun blackjackUiUsesOnlyHighLevelCommands() {
        val ui = source("PuppyBlackjackUi.kt")

        assertTrue(ui.contains("vm.startBlackjackRound(wager)"))
        assertTrue(ui.contains("vm.blackjackHit()"))
        assertTrue(ui.contains("vm.blackjackStand()"))
        assertTrue(ui.contains("vm.blackjackDouble()"))
        assertTrue(ui.contains("vm.blackjackSplit()"))
        assertFalse(ui.contains("vm.beginCasinoRound("))
        assertFalse(ui.contains("vm.commitCasinoOutcome("))
    }

    @Test
    fun publishedRulesMatchBaseline() {
        val ui = source("PuppyBlackjackUi.kt")

        assertTrue(ui.contains("Stands on soft 17"))
        assertTrue(ui.contains("3:2 profit · 2.5× total"))
        assertTrue(ui.contains("First two cards · one card then stand"))
        assertTrue(ui.contains("Up to 3 player hands"))
        assertTrue(ui.contains("Split aces"))
        assertTrue(ui.contains("cannot be resplit"))
    }

    @Test
    fun acceptedBlackjackCanContinueEvenIfFlagLaterDisables() {
        val ui = source("PuppyBlackjackUi.kt")

        val actionsStart = ui.indexOf("if (\n            blackjackRound?.state == PuppyCasinoRoundState.WAGER_ACCEPTED")
        val startButton = ui.indexOf("canPlayFeature &&")

        assertTrue(actionsStart >= 0)
        assertTrue(startButton > actionsStart)
    }
}
