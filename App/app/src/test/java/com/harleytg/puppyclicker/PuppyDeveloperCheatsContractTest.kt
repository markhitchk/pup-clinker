package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyDeveloperCheatsContractTest {
    private fun appSource(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun removedCasinoInspectorHasNoRuntimeHooks() {
        val settings = appSource("PuppySettingsUi.kt")
        val activity = appSource("PuppyClickerV6Activity.kt")
        val viewModel = appSource("PuppyClickerV6ViewModel.kt")

        assertFalse(settings.contains("PuppyDeveloperCheatsSettings"))
        assertFalse(activity.contains("PuppyDeveloperCheatOverlay"))
        assertFalse(viewModel.contains("PuppyDeveloperCheatsSession"))
        assertFalse(viewModel.contains("developerAddTreats"))
        assertFalse(viewModel.contains("developerAddTicket"))
        assertFalse(viewModel.contains("developerFillCare"))
        assertFalse(viewModel.contains("developerAddSkillPoints"))
        assertFalse(viewModel.contains("developerUnlockPuppy"))
        assertFalse(viewModel.contains("clearDeveloperCheatOverrides"))
    }

    @Test
    fun casinoTransactionsAlwaysUseNormalRoundEconomy() {
        val viewModel = appSource("PuppyClickerV6ViewModel.kt")
        val transactions = appSource("PuppyCasinoTransactions.kt")

        assertTrue(viewModel.contains("val roundId = PuppyCasinoRoundIds.newId()"))
        assertFalse(viewModel.contains("chargeWager = false"))
        assertFalse(viewModel.contains("creditPayout = false"))
        assertFalse(viewModel.contains("refundWager = false"))
        assertFalse(viewModel.contains("chargeAdditionalWager = false"))

        assertTrue(transactions.contains("chargeWager: Boolean = true"))
        assertTrue(transactions.contains("creditPayout: Boolean = true"))
        assertTrue(transactions.contains("refundWager: Boolean = true"))
        assertTrue(transactions.contains("chargeAdditionalWager: Boolean = true"))
    }
}
