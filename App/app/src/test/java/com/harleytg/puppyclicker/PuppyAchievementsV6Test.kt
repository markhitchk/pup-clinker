package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyAchievementsV6Test {
    @Test
    fun stableAchievementIdsArePreserved() {
        assertEquals(
            listOf(
                "first_treat", "snack_stash", "puppy_pro", "combo_hero", "ticket_hunter",
                "big_taps", "auto_pup", "happy_home", "level_ten"
            ),
            PuppyAchievementsV6.definitions.map { it.id }
        )
    }

    @Test
    fun happyHomeRequiresAllFourCurrentCareMeters() {
        val complete = V6GameState(happiness = 90, fullness = 90, energy = 90, cleanliness = 90)
        val incomplete = complete.copy(cleanliness = 89)
        val def = PuppyAchievementsV6.definitions.first { it.id == "happy_home" }
        assertTrue(PuppyAchievementsV6.status(def, complete, emptySet()).completed)
        assertFalse(PuppyAchievementsV6.status(def, incomplete, emptySet()).completed)
    }

    @Test
    fun levelTenUsesCurrentXpDrivenLevel() {
        val def = PuppyAchievementsV6.definitions.first { it.id == "level_ten" }
        val below = V6GameState(playerXp = 8_000L)
        val at = V6GameState(playerXp = 8_100L)
        assertFalse(PuppyAchievementsV6.status(def, below, emptySet()).completed)
        assertTrue(PuppyAchievementsV6.status(def, at, emptySet()).completed)
    }

    @Test
    fun rewardedIdsControlXpRewardedStateWithoutChangingCompletion() {
        val def = PuppyAchievementsV6.definitions.first { it.id == "first_treat" }
        val state = V6GameState(lifetimeTreats = 1L)
        val before = PuppyAchievementsV6.status(def, state, emptySet())
        val after = PuppyAchievementsV6.status(def, state, setOf("first_treat"))
        assertTrue(before.completed)
        assertFalse(before.xpRewarded)
        assertTrue(after.completed)
        assertTrue(after.xpRewarded)
    }
    @Test
    fun legacyAutoPupIdNowTracksActiveBoneCollection() {
        val def = PuppyAchievementsV6.definitions.first { it.id == "auto_pup" }
        assertEquals("Bone Collector", def.title)
        assertEquals("Collect 50 Bones through active play.", def.description)
        assertEquals(50L, def.target)
        assertFalse(PuppyAchievementsV6.status(def, V6GameState(bones = 49L), emptySet()).completed)
        assertTrue(PuppyAchievementsV6.status(def, V6GameState(bones = 50L), emptySet()).completed)
    }

}
