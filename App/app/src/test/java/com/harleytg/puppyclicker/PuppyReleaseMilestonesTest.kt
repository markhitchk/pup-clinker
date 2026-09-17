package com.harleytg.puppyclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PuppyReleaseMilestonesTest {
    @Test
    fun firstClaimAddsStableRewardAndBadge() {
        val result = PuppyReleaseMilestones.claimOnePointZero(emptySet(), emptySet())
        assertTrue(result.applied)
        assertTrue(PuppyReleaseMilestones.RELEASE_1_0_CLAIM_ID in result.claims)
        assertTrue(PuppyReleaseMilestones.RELEASE_1_0_BADGE_ID in result.badges)
    }

    @Test
    fun repeatedClaimIsIdempotent() {
        val first = PuppyReleaseMilestones.claimOnePointZero(emptySet(), emptySet())
        val second = PuppyReleaseMilestones.claimOnePointZero(first.claims, first.badges)
        assertFalse(second.applied)
        assertEquals(first.claims, second.claims)
        assertEquals(first.badges, second.badges)
    }
}
