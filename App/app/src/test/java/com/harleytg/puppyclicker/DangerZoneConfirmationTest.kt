package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DangerZoneConfirmationTest {
    @Test
    fun everyDangerZoneActionRequiresTenSecondHold() {
        val actions = listOf(
            DangerZoneAction.RESET_SETTINGS,
            DangerZoneAction.RESET_PROGRESS,
            DangerZoneAction.ERASE_ALL_DATA
        )

        assertEquals(3, actions.size)
        actions.forEach { action ->
            assertEquals(10_000L, action.holdDurationMs)
        }
    }

    @Test
    fun holdProgressFillsFromZeroToOne() {
        assertEquals(0f, DangerHoldConfirmation.progress(null, 5_000L), 0.0001f)
        assertEquals(0f, DangerHoldConfirmation.progress(1_000L, 1_000L), 0.0001f)
        assertEquals(0.5f, DangerHoldConfirmation.progress(1_000L, 6_000L), 0.0001f)
        assertEquals(1f, DangerHoldConfirmation.progress(1_000L, 11_000L), 0.0001f)
        assertEquals(1f, DangerHoldConfirmation.progress(1_000L, 15_000L), 0.0001f)
    }

    @Test
    fun releasingCancelsCompletionUntilAFullNewHoldCompletes() {
        assertFalse(DangerHoldConfirmation.isComplete(2_000L, 11_999L))
        assertTrue(DangerHoldConfirmation.isComplete(2_000L, 12_000L))
        assertFalse(DangerHoldConfirmation.isComplete(null, 99_000L))
        assertFalse(DangerHoldConfirmation.isComplete(20_000L, 29_999L))
        assertTrue(DangerHoldConfirmation.isComplete(20_000L, 30_000L))
    }

    @Test
    fun remainingTimeRoundsUpForCountdownDisplay() {
        assertEquals(10_000L, DangerHoldConfirmation.remainingMs(null, 99_000L))
        assertEquals(10_000L, DangerHoldConfirmation.remainingMs(1_000L, 1_000L))
        assertEquals(5_001L, DangerHoldConfirmation.remainingMs(1_000L, 5_999L))
        assertEquals(0L, DangerHoldConfirmation.remainingMs(1_000L, 11_000L))
    }
}
