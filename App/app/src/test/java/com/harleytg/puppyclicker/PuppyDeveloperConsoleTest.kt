package com.harleytg.puppyclicker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyDeveloperConsoleTest {

    @After
    fun clearLogBuffer() {
        runCatching { PuppyDebugLog.clear() }
    }

    @Test
    fun developerModeUnlocksOnSeventhTap() {
        var taps = 0
        var unlocked = false

        repeat(6) { index ->
            val result = nextDeveloperUnlockProgress(taps, unlocked)
            taps = result.tapCount
            unlocked = result.unlocked

            assertFalse(unlocked)
            assertEquals(6 - index, result.remainingTaps)
        }

        val seventh = nextDeveloperUnlockProgress(taps, unlocked)
        assertTrue(seventh.unlocked)
        assertEquals(7, seventh.tapCount)
        assertEquals(0, seventh.remainingTaps)
    }

    @Test
    fun unlockedDeveloperModeStaysUnlocked() {
        val result = nextDeveloperUnlockProgress(7, alreadyUnlocked = true)

        assertTrue(result.unlocked)
        assertEquals(7, result.tapCount)
        assertEquals(0, result.remainingTaps)
    }

    @Test
    fun consoleRedactsSensitiveValuesBeforeStorage() {
        val unsafe = "token=abc123 password: hunter2 authorization=Bearer-SECRET " +
            "api_key=key-123 session=s-123 birthday=2001-09-09 payload=SAVEPAYLOAD " +
            "email=player@example.com path=/storage/emulated/0/Android/data/com.harleytg.puppyclicker/files/save.pup"

        PuppyDebugLog.recordForTest(
            level = PuppyLogLevel.INFO,
            tag = "SecurityTest",
            message = unsafe,
            timestampMs = 1234L
        )

        val message = PuppyDebugLog.snapshot().single().message
        assertFalse(message.contains("abc123"))
        assertFalse(message.contains("hunter2"))
        assertFalse(message.contains("Bearer-SECRET"))
        assertFalse(message.contains("key-123"))
        assertFalse(message.contains("s-123"))
        assertFalse(message.contains("2001-09-09"))
        assertFalse(message.contains("SAVEPAYLOAD"))
        assertFalse(message.contains("player@example.com"))
        assertFalse(message.contains("/storage/emulated/0/Android/data/"))
        assertTrue(message.contains("[REDACTED]"))
    }

    @Test
    fun consoleKeepsOnlyNewestFiveHundredEntries() {
        repeat(505) { index ->
            PuppyDebugLog.recordForTest(
                level = PuppyLogLevel.DEBUG,
                tag = "RingBuffer",
                message = "message-$index",
                timestampMs = index.toLong()
            )
        }

        val entries = PuppyDebugLog.snapshot()
        assertEquals(500, entries.size)
        assertEquals("message-5", entries.first().message)
        assertEquals("message-504", entries.last().message)
    }

    @Test
    fun clearRemovesAllConsoleEntries() {
        PuppyDebugLog.recordForTest(PuppyLogLevel.WARN, "Test", "one", 1L)
        PuppyDebugLog.recordForTest(PuppyLogLevel.ERROR, "Test", "two", 2L)
        assertEquals(2, PuppyDebugLog.snapshot().size)

        PuppyDebugLog.clear()

        assertTrue(PuppyDebugLog.snapshot().isEmpty())
    }
}
