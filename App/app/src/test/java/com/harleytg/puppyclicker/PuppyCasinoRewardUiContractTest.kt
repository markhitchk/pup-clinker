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
    fun puppyRewardsRemainDeferredToVerifiedRosterMilestone() {
        val hub = source("PuppyCasinoHub.kt")

        assertTrue(hub.contains("Casino puppy rewards remain separate from this milestone"))
        assertTrue(hub.contains("verified existing roster IDs"))
    }
}
