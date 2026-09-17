package com.harleytg.puppyclicker

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Test

class PuppyProgressionTest {
    @Test
    fun missingBondUsesDefault() {
        assertEquals(10, PuppyProgression.bondFor(emptyMap(), "classic"))
    }

    @Test
    fun bondDeltaClampsToValidRange() {
        assertEquals(100, PuppyProgression.withBondDelta(mapOf("classic" to 95), "classic", 20)["classic"])
        assertEquals(0, PuppyProgression.withBondDelta(mapOf("classic" to 5), "classic", -20)["classic"])
    }

    @Test
    fun milestoneBoundariesAreStable() {
        assertEquals(PuppyBondMilestone.NEW_FRIEND, PuppyProgression.milestoneFor(0))
        assertEquals(PuppyBondMilestone.NEW_FRIEND, PuppyProgression.milestoneFor(24))
        assertEquals(PuppyBondMilestone.BUDDY, PuppyProgression.milestoneFor(25))
        assertEquals(PuppyBondMilestone.CLOSE_PAL, PuppyProgression.milestoneFor(50))
        assertEquals(PuppyBondMilestone.BEST_FRIEND, PuppyProgression.milestoneFor(70))
        assertEquals(PuppyBondMilestone.FOREVER_FRIEND, PuppyProgression.milestoneFor(90))
        assertEquals(PuppyBondMilestone.FOREVER_FRIEND, PuppyProgression.milestoneFor(100))
    }

    @Test
    fun xpMigrationClampsNegativeLifetimeTreats() {
        assertEquals(0L, PuppyProgression.seedXpFromLifetimeTreats(-1L))
        assertEquals(450L, PuppyProgression.seedXpFromLifetimeTreats(450L))
    }

    @Test
    fun levelBoundariesMatchApprovedCurve() {
        val cases = mapOf(
            0L to 1,
            99L to 1,
            100L to 2,
            399L to 2,
            400L to 3,
            899L to 3,
            900L to 4
        )
        cases.forEach { (xp, expected) ->
            assertEquals("xp=$xp", expected, PuppyProgression.levelForXp(xp))
        }
    }

    @Test
    fun xpAdditionIsMonotonicAndOverflowSafe() {
        assertEquals(1L, PuppyProgression.addXp(0L, PuppyXpEvent.MANUAL_TAP))
        assertEquals(5L, PuppyProgression.addXp(0L, PuppyXpEvent.CARE_ACTION))
        assertEquals(Long.MAX_VALUE, PuppyProgression.addXp(Long.MAX_VALUE - 10L, PuppyXpEvent.NEW_PUPPY))
        assertEquals(PuppyXpEvent.EVENT_REWARD.amount, PuppyProgression.addXp(-500L, PuppyXpEvent.EVENT_REWARD))
    }

    @Test
    fun migratedXpPreservesLegacyLevelForRepresentativeValues() {
        val values = listOf(0L, 1L, 99L, 100L, 399L, 400L, 1_000L, 10_000L, 1_000_000L)
        values.forEach { lifetime ->
            val oldLevel = 1 + sqrt(lifetime.toDouble() / 100.0).toInt()
            assertEquals(
                "lifetime=$lifetime",
                oldLevel,
                PuppyProgression.levelForXp(PuppyProgression.seedXpFromLifetimeTreats(lifetime))
            )
        }
    }
}
