package com.harleytg.puppyclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyPerformancePolicyTest {
    @Test
    fun automaticUsesAdaptivePerformance() {
        val resolved = PuppyPerformancePolicy.resolve(
            PuppyPerformancePreset.AUTOMATIC,
            reducedMotion = false
        )
        assertTrue(resolved.adaptivePerformance)
        assertTrue(resolved.celebrations)
        assertTrue(resolved.shimmer)
    }

    @Test
    fun qualityKeepsFullEffectsWithoutAdaptiveDowngrade() {
        val resolved = PuppyPerformancePolicy.resolve(
            PuppyPerformancePreset.QUALITY,
            reducedMotion = false
        )
        assertFalse(resolved.adaptivePerformance)
        assertTrue(resolved.celebrations)
        assertTrue(resolved.shimmer)
    }

    @Test
    fun batterySaverCutsNonessentialEffects() {
        val resolved = PuppyPerformancePolicy.resolve(
            PuppyPerformancePreset.BATTERY_SAVER,
            reducedMotion = false
        )
        assertFalse(resolved.adaptivePerformance)
        assertFalse(resolved.celebrations)
        assertFalse(resolved.shimmer)
        assertTrue(resolved.coreFeedback)
    }

    @Test
    fun reducedMotionOverridesAnyPresetAnimationIntent() {
        PuppyPerformancePreset.entries.forEach { preset ->
            val resolved = PuppyPerformancePolicy.resolve(preset, reducedMotion = true)
            assertFalse(resolved.nonessentialMotion)
        }
    }
}
