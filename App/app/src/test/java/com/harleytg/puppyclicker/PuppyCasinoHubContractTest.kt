package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoHubContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun hubUsesExistingTreatAndTicketEconomy() {
        val source = source("PuppyCasinoHub.kt")

        assertTrue(source.contains("Treat Wallet"))
        assertTrue(source.contains("state.treats"))
        assertTrue(source.contains("state.ticketsOwned"))
        assertTrue(source.contains("No chips or second wallet"))
    }

    @Test
    fun allThreeGamesAreVisibleAndImplementedGamesHavePreviewRoutes() {
        val source = source("PuppyCasinoHub.kt")

        assertTrue(source.contains("Puppy Slots"))
        assertTrue(source.contains("Puppy Roulette"))
        assertTrue(source.contains("Puppy Blackjack"))
        assertTrue(source.contains("onOpen = { page = \"slots\" }"))
        assertTrue(source.contains("onOpen = { page = \"roulette\" }"))
        assertTrue(source.contains("onOpen = { page = \"blackjack\" }"))
        assertFalse(source.contains("beginCasinoRound("))
        assertFalse(source.contains("startSlotsSpin("))
        assertFalse(source.contains("startRouletteSpin("))
        assertFalse(source.contains("startBlackjackRound("))
    }

    @Test
    fun interruptedRoundsUseSharedRecoveryPolicyWithoutStartingANewRound() {
        val source = source("PuppyCasinoHub.kt")
        val policy = source("PuppyCasinoFeaturePolicy.kt")

        assertTrue(source.contains("Interrupted casino round"))
        assertTrue(source.contains("PuppyCasinoFeaturePolicy.recoveryAction(round)"))
        assertTrue(source.contains("vm.refundCasinoRound(round.roundId)"))
        assertTrue(source.contains("vm.settleCasinoRound(round.roundId)"))
        assertTrue(source.contains("Resume Blackjack Hand"))
        assertTrue(policy.contains("SETTLE_COMMITTED_OUTCOME"))
    }

    @Test
    fun casinoRewardsReuseExistingTicketAndRosterOwnership() {
        val source = source("PuppyCasinoHub.kt")
        val puppyRewards = source("PuppyCasinoPuppyRewards.kt")

        assertTrue(source.contains("Casino Upgrade Tickets"))
        assertTrue(source.contains("Casino Puppy Rewards"))
        assertTrue(source.contains("existing Roster"))
        assertTrue(puppyRewards.contains("unlockedPuppies = before.unlockedPuppies + styleId"))
        assertFalse(puppyRewards.contains("casinoPuppyInventory"))
    }

    @Test
    fun casinoEntryLivesInRewardsNotBottomNavigation() {
        val rewards = source("PuppyMainScreensRevamp.kt")
        val navigation = source("PuppyNavigationModel.kt")

        assertTrue(rewards.contains("Puppy Casino"))
        assertTrue(rewards.contains("onOpenCasino"))
        assertTrue(navigation.contains("CASINO(\"Puppy Casino\")"))
        assertFalse(navigation.substringBefore("enum class PuppyInternalDestination").contains("CASINO"))
    }
}
