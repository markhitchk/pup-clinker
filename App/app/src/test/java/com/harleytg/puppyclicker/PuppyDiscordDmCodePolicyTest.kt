package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyDiscordDmCodePolicyTest {
    @Test
    fun normalizesAndAcceptsSixToEightCharacterCodes() {
        assertEquals("AB12CD", PuppyDiscordDmCodePolicy.normalize(" ab-12 cd "))
        assertTrue(PuppyDiscordDmCodePolicy.isWellFormed("ab12cd"))
        assertTrue(PuppyDiscordDmCodePolicy.isWellFormed("ABCD1234"))
        assertFalse(PuppyDiscordDmCodePolicy.isWellFormed("abcde"))
        assertFalse(PuppyDiscordDmCodePolicy.isWellFormed("ABCDEFGHI"))
        assertFalse(PuppyDiscordDmCodePolicy.isWellFormed("ab$12c"))
    }

    @Test
    fun hashIsStableAndNeverReturnsPlaintext() {
        val hash = PuppyDiscordDmCodePolicy.hash("ab12cd", "player", "install", "discord")
        assertEquals(64, hash.length)
        assertFalse(hash.contains("AB12CD", ignoreCase = true))
        assertEquals(hash, PuppyDiscordDmCodePolicy.hash("AB-12 CD", "player", "install", "discord"))
        assertTrue(
            PuppyDiscordDmCodePolicy.hash("ab12cd", "player", "install", "other") != hash
        )
    }

    @Test
    fun resendHonorsCooldownAndHourlyCap() {
        val now = 1_000_000L
        val cooldown = PuppyDiscordDmCodePolicy.canResend(now - 10_000L, 1, now - 10_000L, now)
        assertFalse(cooldown.allowed)
        assertEquals("WAIT_COOLDOWN", cooldown.reason)

        val cap = PuppyDiscordDmCodePolicy.canResend(
            lastSentAtMs = now - 61_000L,
            sendsInWindow = 5,
            windowStartedAtMs = now - 120_000L,
            nowMs = now
        )
        assertFalse(cap.allowed)
        assertEquals("HOUR_CAP", cap.reason)

        val ok = PuppyDiscordDmCodePolicy.canResend(now - 61_000L, 1, now - 120_000L, now)
        assertTrue(ok.allowed)
    }

    @Test
    fun fifthFailedAttemptLocksTheCode() {
        val fourth = PuppyDiscordDmCodePolicy.nextAttemptState(3)
        assertFalse(fourth.locked)
        assertEquals(1, fourth.remaining)
        val fifth = PuppyDiscordDmCodePolicy.nextAttemptState(4)
        assertTrue(fifth.locked)
        assertEquals(0, fifth.remaining)
    }
}
