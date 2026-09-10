package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PupEyeRetryPolicyTest {

    @Test
    fun missingBrandingRetriesAfterFifteenSeconds() {
        assertEquals(
            15_000L,
            nextPupEyeBrandingRetryDelayMs(hasBitmap = false, consecutiveFailures = 1)
        )
    }

    @Test
    fun repeatedFailuresBackOffAndCapAtOneMinute() {
        assertEquals(
            30_000L,
            nextPupEyeBrandingRetryDelayMs(hasBitmap = false, consecutiveFailures = 2)
        )
        assertEquals(
            60_000L,
            nextPupEyeBrandingRetryDelayMs(hasBitmap = false, consecutiveFailures = 3)
        )
        assertEquals(
            60_000L,
            nextPupEyeBrandingRetryDelayMs(hasBitmap = false, consecutiveFailures = 10)
        )
    }

    @Test
    fun loadedBrandingStopsRetryLoop() {
        assertNull(
            nextPupEyeBrandingRetryDelayMs(hasBitmap = true, consecutiveFailures = 1)
        )
    }
}
