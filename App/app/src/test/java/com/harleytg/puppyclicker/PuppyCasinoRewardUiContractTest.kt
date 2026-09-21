package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoRewardUiContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun hubShowsCapsWeightsAndDuplicateProtection() {
        val hub = source("PuppyCasinoHub.kt")

        assertTrue(hub.contains("Casino Upgrade Tickets"))
        assertTrue(hub.contains("Daily casino cap"))
        assertTrue(hub.contains("Inventory cap"))
        assertTrue(hub.contains("60 / 25 / 10 / 4 / 1"))
        assertTrue(hub.contains("at most 1 existing Upgrade Ticket"))
        assertTrue(hub.contains("cannot reroll the Ticket"))
    }

    @Test
    fun hubDoesNotInventNewTicketRarities() {
        val hub = source("PuppyCasinoHub.kt")
        val rewards = source("PuppyCasinoRewards.kt")

        assertTrue(hub.contains("reward.rarity?.displayName"))
        assertTrue(rewards.contains("TicketRarity.entries"))
        assertTrue(rewards.contains("rarity.rarityWeight"))
    }

    @Test
    fun puppyRewardsUseExistingRosterOwnershipAndShowVerifiedPool() {
        val hub = source("PuppyCasinoHub.kt")
        val rewards = source("PuppyCasinoPuppyRewards.kt")

        assertTrue(hub.contains("Casino Puppy Rewards"))
        assertTrue(hub.contains("existing Roster"))
        assertTrue(hub.contains("eligibleStyles"))
        assertTrue(hub.contains("Reserved: seasonal, developer, secret, tribute, and branded special puppies"))
        assertTrue(rewards.contains("unlockedPuppies = before.unlockedPuppies + styleId"))
        assertTrue(rewards.contains("V6_PUPPY_STYLES.associateBy"))
        assertTrue(rewards.contains("require(styles.all { it.redeemOnly })"))
    }
    @Test
    fun hubPublishesRareTicketBandsAndOnePerDayCap() {
        val hub = source("PuppyCasinoHub.kt")
        val rewards = source("PuppyCasinoRewards.kt")

        assertTrue(hub.contains("profitable <2× 0.25%"))
        assertTrue(hub.contains("100×+ 5%"))
        assertTrue(hub.contains("No tier is guaranteed"))
        assertTrue(hub.contains("At most 1 Casino-earned Upgrade Ticket"))
        assertTrue(rewards.contains("const val MAX_DAILY_CASINO_TICKETS = 1"))
        assertTrue(rewards.contains("ratioAtLeast(payout, wager, 100L) -> 500"))
    }

}
