package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyDeveloperCheatsContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun pinIsNotStoredAsPlaintextAndVerifierUsesTwoKeystoreLayers() {
        val cheats = source("PuppyDeveloperCheats.kt")

        assertFalse(cheats.contains("\"01440\""))
        assertTrue(cheats.contains("PBKDF2WithHmacSHA256"))
        assertTrue(cheats.contains("KEY_ALIAS_INNER"))
        assertTrue(cheats.contains("KEY_ALIAS_OUTER"))
        assertTrue(cheats.contains("AES/GCM/NoPadding"))
        assertTrue(cheats.contains("AndroidKeyStore"))
    }

    @Test
    fun activationClearlyVoidsDeveloperCasinoWinningsAndCanBeDisabled() {
        val cheats = source("PuppyDeveloperCheats.kt")

        assertTrue(cheats.contains("Activate Casino X-Ray?"))
        assertTrue(cheats.contains("voids all winnings"))
        assertTrue(cheats.contains("CASINO X-RAY ACTIVE · WINNINGS VOID"))
        assertTrue(cheats.contains("Disable Casino X-Ray"))
        assertTrue(cheats.contains("PuppyDeveloperCheatsSession.disable()"))
    }

    @Test
    fun xrayReadsEveryCasinoGamesCommittedHiddenStateWithoutMutationControls() {
        val cheats = source("PuppyDeveloperCheats.kt")

        assertTrue(cheats.contains("BLACKJACK X-RAY"))
        assertTrue(cheats.contains("Dealer hole:"))
        assertTrue(cheats.contains("SLOTS X-RAY"))
        assertTrue(cheats.contains("Reels:"))
        assertTrue(cheats.contains("ROULETTE X-RAY"))
        assertTrue(cheats.contains("Winning pocket:"))
        assertTrue(cheats.contains("PLINKO X-RAY"))
        assertTrue(cheats.contains("Path:"))
        assertTrue(cheats.contains("SCRATCHER X-RAY"))
        assertTrue(cheats.contains("Under coating:"))
        assertTrue(cheats.contains("WHEEL X-RAY"))
        assertTrue(cheats.contains("Selected segment"))
        assertFalse(cheats.contains("developerAddTreats(1_000L)"))
        assertFalse(cheats.contains("developerAddTicket(rarity)"))
        assertFalse(cheats.contains("developerFillCare()"))
        assertFalse(cheats.contains("developerAddSkillPoints(10)"))
    }

    @Test
    fun developerRoundsNeverChargeOrCreditTheRealCasinoEconomy() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")
        val transactions = source("PuppyCasinoTransactions.kt")

        assertTrue(viewModel.contains("PuppyDeveloperCheatsSession.newTestRoundId()"))
        assertTrue(viewModel.contains("chargeWager = !developerTest"))
        assertTrue(viewModel.contains("creditPayout = !developerTest"))
        assertTrue(viewModel.contains("chargeAdditionalWager = !PuppyDeveloperCheatsSession.isTestRoundId"))
        assertTrue(viewModel.contains("refundWager = !PuppyDeveloperCheatsSession.isTestRoundId"))
        assertTrue(transactions.contains("chargeWager: Boolean = true"))
        assertTrue(transactions.contains("creditPayout: Boolean = true"))
        assertTrue(transactions.contains("refundWager: Boolean = true"))
        assertTrue(transactions.contains("chargeAdditionalWager: Boolean = true"))
    }
}
