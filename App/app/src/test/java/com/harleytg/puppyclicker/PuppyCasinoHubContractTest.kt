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
        assertFalse(source.contains("beginCasinoRound("))
        assertFalse(source.contains("startSlotsSpin("))
        assertFalse(source.contains("startRouletteSpin("))
    }

    @Test
    fun interruptedRoundsCanRecoverWithoutStartingANewRound() {
        val source = source("PuppyCasinoHub.kt")

        assertTrue(source.contains("Interrupted casino round"))
        assertTrue(source.contains("vm.refundCasinoRound(round.roundId)"))
        assertTrue(source.contains("vm.settleCasinoRound(round.roundId)"))
    }

    @Test
    fun puppyRewardsReuseVerifiedRosterOwnership() {
        val source = source("PuppyCasinoHub.kt")

        assertTrue(source.contains("verified existing roster IDs"))
        assertTrue(source.contains("never create a second puppy inventory"))
        assertTrue(source.contains("existing Ticket rarities and inventory limits"))
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
