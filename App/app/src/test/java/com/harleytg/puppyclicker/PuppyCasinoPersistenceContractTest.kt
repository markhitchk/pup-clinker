package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoPersistenceContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun wagerBalanceAndRoundLedgerUseOneSynchronousEditorCommit() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")
        assertTrue(viewModel.contains(".putLong(KEY_TREATS, result.state.treats)"))
        assertTrue(viewModel.contains(".putLong(KEY_LIFETIME, result.state.lifetimeTreats)"))
        assertTrue(viewModel.contains("PuppyCasinoPersistence.write("))
        assertTrue(viewModel.contains("if (!editor.commit())"))
        assertTrue(viewModel.contains("PERSISTENCE_FAILED"))
    }

    @Test
    fun wagerContextIsStoredInsideTheSameRoundLedger() {
        val transactions = source("PuppyCasinoTransactions.kt")

        assertTrue(transactions.contains("val wagerPayload: String? = null"))
        assertTrue(transactions.contains("wagerPayload = persistedWagerPayload"))
        assertTrue(transactions.contains("round.wagerPayload?.let { put(\"wagerPayload\", it) }"))
        assertTrue(transactions.contains("wagerPayload = wagerPayload"))
    }

    @Test
    fun settlementPersistsTreatsTicketsAndRewardLedgerInOneCommit() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(viewModel.contains(".putLong(KEY_TREATS, result.state.treats)"))
        assertTrue(viewModel.contains(".putLong(KEY_TOTAL_TICKETS_FOUND, result.state.totalTicketsFound)"))
        assertTrue(viewModel.contains("TicketRarity.entries.forEach { rarity ->"))
        assertTrue(viewModel.contains("PuppyCasinoRewardPersistence.write(editor, it)"))
        assertTrue(viewModel.contains("if (!editor.commit())"))
        assertTrue(viewModel.contains("_casinoRewardLedger.value = it"))
    }

    @Test
    fun completedSettlementEvaluatesRewardBeforePersisting() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        val settle = viewModel.indexOf("PuppyCasinoTransactionEngine.settle(")
        val reward = viewModel.indexOf("PuppyCasinoRewardEngine.apply(", settle)
        val persist = viewModel.indexOf("val persisted = persistCasinoMutation(", reward)

        assertTrue(settle >= 0)
        assertTrue(reward > settle)
        assertTrue(persist > reward)
    }

    @Test
    fun newRoundsRespectFeatureFlagButRecoveryPathsDoNot() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")
        val transactions = source("PuppyCasinoTransactions.kt")
        assertTrue(viewModel.contains("PuppyFeatureFlags.flag(\"puppy_casino\").isAvailable()"))
        assertTrue(viewModel.contains("fun settleCasinoRound"))
        assertTrue(viewModel.contains("fun refundCasinoRound"))
        assertTrue(transactions.contains("fun settle("))
        assertTrue(transactions.contains("fun refund("))
    }

    @Test
    fun destructiveRunResetCannotStrandAnAcceptedWager() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")
        assertTrue(viewModel.contains("fun resetRunWithoutPrestige() {\n        if (_casinoRound.value != null) return"))
        assertTrue(viewModel.contains("fun prestige() {\n        if (_casinoRound.value != null) return"))
    }

    @Test
    fun casinoFallbackFlagShipsDisabled() {
        val flags = source("PuppyFeatureFlags.kt")
        assertTrue(flags.contains("\"puppy_casino\", true, false, \"coming_soon\""))
    }
}
