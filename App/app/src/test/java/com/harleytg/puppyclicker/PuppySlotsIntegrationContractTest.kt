package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppySlotsIntegrationContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun viewModelCommitsOutcomeBeforeUiSettlement() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        val accept = viewModel.indexOf("val accepted = beginCasinoRound(")
        val generate = viewModel.indexOf("PuppySlotsEngine.randomSpin(wagerTreats)")
        val commit = viewModel.indexOf("val committed = commitCasinoOutcome(")

        assertTrue(accept >= 0)
        assertTrue(generate > accept)
        assertTrue(commit > generate)
        assertTrue(viewModel.contains("PuppySlotsOutcomeCodec.encode(outcome)"))
        assertTrue(viewModel.contains("payoutTreats = outcome.payoutTreats"))
    }

    @Test
    fun slotsRequireCentralizedUmbrellaAndGameFeaturePolicy() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")
        val ui = source("PuppySlotsUi.kt")
        val policy = source("PuppyCasinoFeaturePolicy.kt")

        assertTrue(viewModel.contains("val flagSnapshot = PuppyFeatureFlags.flags.value"))
        assertTrue(viewModel.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertTrue(ui.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertTrue(policy.contains("\"puppy_casino\""))
        assertTrue(policy.contains("\"casino_slots\""))
    }

    @Test
    fun settlementRevalidatesPersistedSlotsPayload() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(viewModel.contains("PuppySlotsOutcomeCodec.decodeAndValidate("))
        assertTrue(viewModel.contains("it.payoutTreats == active.payoutTreats"))
        assertTrue(viewModel.contains("failure = PuppyCasinoTransactionFailure.INVALID_OUTCOME"))
    }

    @Test
    fun slotsUiNeverCallsRawCasinoRoundApis() {
        val ui = source("PuppySlotsUi.kt")

        assertTrue(ui.contains("vm.startSlotsSpin(wager)"))
        assertFalse(ui.contains("vm.beginCasinoRound("))
        assertFalse(ui.contains("vm.commitCasinoOutcome("))
    }

    @Test
    fun slotsUiPublishesRulesWeightsAndTotalReturnMeaning() {
        val ui = source("PuppySlotsUi.kt")

        assertTrue(ui.contains("Published payout table"))
        assertTrue(ui.contains("Published reel weights"))
        assertTrue(ui.contains("total Treat return, including the original wager"))
        assertTrue(ui.contains("The Ticket symbol is visual in Slots; this engine pays Treats only."))
        assertTrue(ui.contains("PUBLISHED_RTP_PERCENT"))
    }
}
