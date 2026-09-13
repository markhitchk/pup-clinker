package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyRouletteIntegrationContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun selectedBetIsPersistedBeforeWinningNumberGeneration() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        val encode = viewModel.indexOf("val wagerPayload = PuppyRouletteBetCodec.encode(bet)")
        val accept = viewModel.indexOf("val accepted = beginCasinoRound(", encode)
        val random = viewModel.indexOf("PuppyRouletteEngine.randomSpin(", accept)
        val commit = viewModel.indexOf("val committed = commitCasinoOutcome(", random)

        assertTrue(encode >= 0)
        assertTrue(accept > encode)
        assertTrue(random > accept)
        assertTrue(commit > random)
        assertTrue(viewModel.contains("wagerPayload = wagerPayload"))
    }

    @Test
    fun roundPersistenceWritesWagerPayloadWithAcceptedRound() {
        val transactions = source("PuppyCasinoTransactions.kt")

        assertTrue(transactions.contains("val wagerPayload: String? = null"))
        assertTrue(transactions.contains("round.wagerPayload?.let { put(\"wagerPayload\", it) }"))
        assertTrue(transactions.contains("wagerPayload = persistedWagerPayload"))
    }

    @Test
    fun rouletteRequiresCentralizedUmbrellaAndGameFeaturePolicy() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")
        val ui = source("PuppyRouletteUi.kt")
        val policy = source("PuppyCasinoFeaturePolicy.kt")

        assertTrue(viewModel.contains("val flagSnapshot = PuppyFeatureFlags.flags.value"))
        assertTrue(viewModel.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertTrue(ui.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertTrue(policy.contains("\"puppy_casino\""))
        assertTrue(policy.contains("\"casino_roulette\""))
    }

    @Test
    fun settlementRevalidatesBetAndOutcome() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(viewModel.contains("PuppyRouletteBetCodec.decodeAndValidate(active.wagerPayload)"))
        assertTrue(viewModel.contains("PuppyRouletteOutcomeCodec.decodeAndValidate("))
        assertTrue(viewModel.contains("expectedBet = bet"))
        assertTrue(viewModel.contains("it.payoutTreats == active.payoutTreats"))
    }

    @Test
    fun rouletteUiUsesOnlyHighLevelSpinApi() {
        val ui = source("PuppyRouletteUi.kt")

        assertTrue(ui.contains("vm.startRouletteSpin("))
        assertFalse(ui.contains("vm.beginCasinoRound("))
        assertFalse(ui.contains("vm.commitCasinoOutcome("))
    }

    @Test
    fun rouletteUiPublishesZeroAndPayoutRules() {
        val ui = source("PuppyRouletteUi.kt")

        assertTrue(ui.contains("European single-zero roulette"))
        assertTrue(ui.contains("Zero is green"))
        assertTrue(ui.contains("Straight number 0–36"))
        assertTrue(ui.contains("36× total"))
        assertTrue(ui.contains("Red / Black"))
        assertTrue(ui.contains("2× total"))
        assertTrue(ui.contains("PUBLISHED_RTP_PERCENT"))
    }
}
