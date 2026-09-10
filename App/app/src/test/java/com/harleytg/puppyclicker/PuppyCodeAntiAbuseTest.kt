package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCodeAntiAbuseTest {
    @Test
    fun firstFourInvalidAttemptsDoNotCooldown() {
        var state = PuppyCodeAntiAbuse.State()
        repeat(4) { index ->
            state = PuppyCodeAntiAbuse.recordInvalid(state, 1_000L + index)
        }
        assertEquals(0L, state.cooldownUntilMs)
    }

    @Test
    fun fifthInvalidAttemptStartsThirtySecondCooldown() {
        var state = PuppyCodeAntiAbuse.State()
        repeat(5) { index ->
            state = PuppyCodeAntiAbuse.recordInvalid(state, 1_000L + index)
        }
        assertEquals(31_004L, state.cooldownUntilMs)
    }

    @Test
    fun fiveRapidSubmissionsEscalatePupEyeCooldown() {
        var state = PuppyCodeAntiAbuse.State()
        repeat(5) { index ->
            state = PuppyCodeAntiAbuse.recordSubmission(state, 10_000L + index * 1_000L)
        }
        assertTrue(state.pupEyeEscalated)
        assertTrue(state.cooldownUntilMs >= 10_000L + 4_000L + 60_000L)
    }

    @Test
    fun successfulClaimClearsInvalidWindowButNotActiveCooldown() {
        var state = PuppyCodeAntiAbuse.State()
        repeat(3) { index -> state = PuppyCodeAntiAbuse.recordInvalid(state, 20_000L + index) }
        val cleared = PuppyCodeAntiAbuse.recordSuccess(state)
        assertEquals(0, cleared.invalidAttemptTimes.size)
    }
}
