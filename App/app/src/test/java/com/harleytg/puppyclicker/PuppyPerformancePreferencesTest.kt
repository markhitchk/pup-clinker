package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class PuppyPerformancePreferencesTest {
    @Test
    fun existingMotionSettingsMapDeterministically() {
        assertEquals(
            PuppyPerformancePreset.AUTOMATIC,
            PuppyPerformancePreferences.inferPreset(adaptive = true, celebrations = true, shimmer = true)
        )
        assertEquals(
            PuppyPerformancePreset.QUALITY,
            PuppyPerformancePreferences.inferPreset(adaptive = false, celebrations = true, shimmer = true)
        )
        assertEquals(
            PuppyPerformancePreset.BATTERY_SAVER,
            PuppyPerformancePreferences.inferPreset(adaptive = false, celebrations = false, shimmer = false)
        )
        assertEquals(
            PuppyPerformancePreset.AUTOMATIC,
            PuppyPerformancePreferences.inferPreset(adaptive = false, celebrations = false, shimmer = true)
        )
    }
}
