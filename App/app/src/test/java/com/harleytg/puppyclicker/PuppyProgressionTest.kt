package com.harleytg.puppyclicker

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
            24L to 1,
            25L to 2,
            99L to 2,
            100L to 3,
            624L to 5,
            625L to 6,
            886L to 6,
            900L to 7,
            245_025L to 100,
            Long.MAX_VALUE to 100
        )
        cases.forEach { (xp, expected) ->
            assertEquals("xp=$xp", expected, PuppyProgression.levelForXp(xp))
        }
    }

    @Test
    fun xpAdditionIsMonotonicAndOverflowSafe() {
        assertEquals(1L, PuppyProgression.addXp(0L, PuppyXpEvent.MANUAL_TAP))
        assertEquals(5L, PuppyProgression.addXp(0L, PuppyXpEvent.CARE_ACTION))
        assertEquals(250_000L, PuppyProgression.addXp(Long.MAX_VALUE - 10L, PuppyXpEvent.NEW_PUPPY))
        assertEquals(PuppyXpEvent.EVENT_REWARD.amount, PuppyProgression.addXp(-500L, PuppyXpEvent.EVENT_REWARD))
    }

    @Test
    fun legacyBalancesRemainSafeButXpAndLevelsUseBoundedCurve() {
        assertEquals(250_000L, PuppyProgression.seedXpFromLifetimeTreats(Long.MAX_VALUE))
        assertEquals(100, PuppyProgression.levelForXp(9_000_000_010_023_864L))
        assertEquals(6, PuppyProgression.levelForXp(886L))
        assertEquals(250_000L, PuppyProgression.addXp(249_999L, PuppyXpEvent.MANUAL_TAP))
        assertEquals(250_000L, PuppyProgression.addXp(250_000L, PuppyXpEvent.NEW_PUPPY))
    }
}
