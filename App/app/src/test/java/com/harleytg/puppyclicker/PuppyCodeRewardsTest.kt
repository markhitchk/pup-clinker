package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCodeRewardsTest {
    @Test
    fun treatPuppyAndTicketBundleAppliesAtomically() {
        val before = V6GameState(treats = 100, lifetimeTreats = 200)
        val rewards = listOf(
            PuppyCodeReward.Treats(2_500),
            PuppyCodeReward.Puppy("v2_flurry"),
            PuppyCodeReward.UpgradeTickets(TicketRarity.RARE, 2)
        )

        val result = RewardGrantEngine.applyTo(before, rewards)

        assertTrue(result.success)
        assertEquals(2_600, result.state.treats)
        assertEquals(2_700, result.state.lifetimeTreats)
        assertTrue("v2_flurry" in result.state.unlockedPuppies)
        assertEquals(2, result.state.ticketInventory[TicketRarity.RARE])
    }

    @Test
    fun unsupportedPersistedRewardDoesNotMutateState() {
        val before = V6GameState(treats = 100)
        val result = RewardGrantEngine.applyTo(
            before,
            listOf(PuppyCodeReward.Badge("founder"), PuppyCodeReward.Treats(500))
        )

        assertFalse(result.success)
        assertEquals(before, result.state)
        assertEquals(PuppyRewardGrantFailure.UNSUPPORTED_REWARD, result.failure)
    }

    @Test
    fun unknownPuppyDoesNotMutateState() {
        val before = V6GameState(treats = 100)
        val result = RewardGrantEngine.applyTo(
            before,
            listOf(PuppyCodeReward.Treats(500), PuppyCodeReward.Puppy("not_a_real_puppy"))
        )

        assertFalse(result.success)
        assertEquals(before, result.state)
    }

    @Test
    fun historySummaryNeverContainsPlaintextCode() {
        val definition = PuppyCodeDefinition(
            id = "bundle_1",
            hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            status = PuppyCodeStatus.ACTIVE,
            rewards = listOf(PuppyCodeReward.Treats(500)),
            message = "Reward claimed."
        )
        val entry = PuppyCodeHistory.createEntry(definition, 123_456L)
        assertEquals("bundle_1", entry.redemptionId)
        assertEquals(123_456L, entry.redeemedAtMs)
        assertFalse(entry.summary.contains("SECRET-CODE"))
    }
}
