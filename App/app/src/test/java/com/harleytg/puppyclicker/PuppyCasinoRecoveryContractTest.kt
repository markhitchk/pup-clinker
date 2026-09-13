package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoRecoveryContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    private fun methodSlice(
        source: String,
        start: String,
        next: String
    ): String {
        val from = source.indexOf(start)
        val to = source.indexOf(next, from + start.length)
        require(from >= 0 && to > from)
        return source.substring(from, to)
    }

    @Test
    fun onlyNewRoundAcceptanceConsultsFeaturePolicy() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        val begin = methodSlice(
            viewModel,
            "internal fun beginCasinoRound(",
            "internal fun startSlotsSpin("
        )
        val settle = methodSlice(
            viewModel,
            "internal fun settleCasinoRound(",
            "internal fun refundCasinoRound("
        )
        val refund = methodSlice(
            viewModel,
            "internal fun refundCasinoRound(",
            "fun tapPuppy()"
        )

        assertTrue(begin.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertTrue(begin.contains("val flagSnapshot = PuppyFeatureFlags.flags.value"))
        assertFalse(settle.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertFalse(settle.contains("PuppyFeatureFlags.flag("))
        assertFalse(refund.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertFalse(refund.contains("PuppyFeatureFlags.flag("))
    }

    @Test
    fun blackjackContinuationActionsDoNotConsultKillSwitch() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        val actions = methodSlice(
            viewModel,
            "internal fun blackjackHit()",
            "internal fun commitCasinoOutcome("
        )

        assertTrue(actions.contains("blackjackStand()"))
        assertTrue(actions.contains("blackjackDouble()"))
        assertTrue(actions.contains("blackjackSplit()"))
        assertTrue(actions.contains("finalizePendingBlackjackRound()"))
        assertFalse(actions.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertFalse(actions.contains("PuppyFeatureFlags.flag("))
    }

    @Test
    fun remoteHideStillExposesRecoveryEntry() {
        val rewards = source("PuppyMainScreensRevamp.kt")
        val policy = source("PuppyCasinoFeaturePolicy.kt")

        assertTrue(rewards.contains("showCasinoEntry"))
        assertTrue(rewards.contains("activeCasinoRound"))
        assertTrue(rewards.contains("Recovery required · finish the saved casino round."))
        assertTrue(rewards.contains("Recovery warning · saved Casino data needs attention."))
        assertTrue(rewards.contains("hasRecoveryIssue = casinoRecoveryIssue != null"))
        assertTrue(rewards.contains("\"Recover\""))
        assertTrue(policy.contains("hasRecoveryIssue ||"))
    }

    @Test
    fun gameScreensUsePolicyOnlyForNewRoundButtons() {
        val slots = source("PuppySlotsUi.kt")
        val roulette = source("PuppyRouletteUi.kt")
        val blackjack = source("PuppyBlackjackUi.kt")

        assertTrue(slots.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertTrue(roulette.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))
        assertTrue(blackjack.contains("PuppyCasinoFeaturePolicy.canStartNewRound("))

        assertTrue(slots.contains("vm.settleCasinoRound(round.roundId)"))
        assertTrue(roulette.contains("vm.settleCasinoRound(round.roundId)"))
        assertTrue(blackjack.contains("vm.settleCasinoRound(round.roundId)"))
    }

    @Test
    fun destructiveProgressionActionsCannotRunWithActiveCasinoRound() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(
            viewModel.contains(
                "fun resetRunWithoutPrestige() {\n        if (_casinoRound.value != null) return"
            )
        )
        assertTrue(
            viewModel.contains(
                "fun prestige() {\n        if (_casinoRound.value != null) return"
            )
        )
    }

    @Test
    fun saveImportReloadsRoundAndBothRewardLedgers() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(
            viewModel.contains(
                "val casinoInspection = PuppyCasinoPersistence.inspectActiveRound(prefs)"
            )
        )
        assertTrue(viewModel.contains("_casinoRound.value = casinoInspection.round"))
        assertTrue(viewModel.contains("_casinoRecoveryIssue.value = casinoInspection.issue"))
        assertTrue(
            viewModel.contains(
                "_casinoRewardLedger.value = PuppyCasinoRewardPersistence.load(prefs)"
            )
        )
        assertTrue(
            viewModel.contains(
                "_casinoPuppyRewardLedger.value = PuppyCasinoPuppyRewardPersistence.load(prefs)"
            )
        )
    }
}
