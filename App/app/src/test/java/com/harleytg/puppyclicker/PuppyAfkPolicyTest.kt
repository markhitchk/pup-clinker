package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PuppyAfkPolicyTest {
    @Test
    fun rejectsInvalidIntervals() {
        assertNull(PuppyAfkPolicy.prepare(0L, 1_000L))
        assertNull(PuppyAfkPolicy.prepare(-1L, 1_000L))
        assertNull(PuppyAfkPolicy.prepare(2_000L, 2_000L))
        assertNull(PuppyAfkPolicy.prepare(2_000L, 1_000L))
    }

    @Test
    fun oneDayEarnsCurrentDailyRate() {
        val result = PuppyAfkPolicy.prepare(1_000L, 1_000L + PuppyAfkPolicy.DAY_MS)
        assertNotNull(result)
        assertEquals(PuppyAfkPolicy.DAY_MS, result!!.creditedAwayMs)
        assertEquals(1_000L, result.earnedTreats)
    }

    @Test
    fun accrualClampsAtExactlySevenDays() {
        val start = 5_000L
        val result = PuppyAfkPolicy.prepare(start, start + 8L * PuppyAfkPolicy.DAY_MS)!!
        assertEquals(PuppyAfkPolicy.MAX_AWAY_MS, result.creditedAwayMs)
        assertEquals(7_000L, result.earnedTreats)
        assertEquals(start + PuppyAfkPolicy.MAX_AWAY_MS, result.endedAtMs)
    }

    @Test
    fun settlementIdIsStableForSameEffectiveInterval() {
        val a = PuppyAfkPolicy.prepare(10L, 10L + 2L * PuppyAfkPolicy.DAY_MS)!!
        val b = PuppyAfkPolicy.prepare(10L, 10L + 2L * PuppyAfkPolicy.DAY_MS)!!
        val c = PuppyAfkPolicy.prepare(11L, 11L + 2L * PuppyAfkPolicy.DAY_MS)!!
        assertEquals(a.settlementId, b.settlementId)
        assertNotEquals(a.settlementId, c.settlementId)
    }
}
