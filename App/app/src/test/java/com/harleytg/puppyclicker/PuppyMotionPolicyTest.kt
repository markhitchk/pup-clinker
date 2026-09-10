package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyMotionPolicyTest {
    @Test
    fun balancedPresetMatchesApprovedDefault() {
        val config = PuppyMotionPresets.config(PuppyMotionPreset.BALANCED)

        assertEquals(PuppyMotionIntensity.MEDIUM, config.intensity)
        assertTrue(config.animatedUi)
        assertTrue(config.buttonAnimations)
        assertTrue(config.screenTransitions)
        assertTrue(config.cardAnimations)
        assertTrue(config.counterAnimations)
        assertTrue(config.celebrations)
        assertTrue(config.warningAnimations)
        assertTrue(config.loadingAnimations)
        assertTrue(config.shimmerEffects)
        assertTrue(config.adaptivePerformance)
    }

    @Test
    fun minimalPresetRemovesDecorativeMotionButKeepsEssentialFeedback() {
        val config = PuppyMotionPresets.config(PuppyMotionPreset.MINIMAL)

        assertEquals(PuppyMotionIntensity.LOW, config.intensity)
        assertTrue(config.animatedUi)
        assertFalse(config.buttonAnimations)
        assertTrue(config.screenTransitions)
        assertFalse(config.cardAnimations)
        assertFalse(config.counterAnimations)
        assertFalse(config.celebrations)
        assertTrue(config.warningAnimations)
        assertTrue(config.loadingAnimations)
        assertFalse(config.shimmerEffects)
        assertTrue(config.adaptivePerformance)
    }

    @Test
    fun playfulPresetUsesHighIntensityWithFullCategories() {
        val config = PuppyMotionPresets.config(PuppyMotionPreset.PLAYFUL)

        assertEquals(PuppyMotionIntensity.HIGH, config.intensity)
        assertTrue(config.animatedUi)
        assertTrue(config.buttonAnimations)
        assertTrue(config.screenTransitions)
        assertTrue(config.cardAnimations)
        assertTrue(config.counterAnimations)
        assertTrue(config.celebrations)
        assertTrue(config.warningAnimations)
        assertTrue(config.loadingAnimations)
        assertTrue(config.shimmerEffects)
        assertTrue(config.adaptivePerformance)
    }

    @Test
    fun reduceMotionAlwaysWinsWithoutChangingSavedPreset() {
        val config = PuppyMotionPresets.config(PuppyMotionPreset.PLAYFUL).copy(reducedMotion = true)

        assertEquals(PuppyMotionMode.REDUCED, PuppyMotionPolicy.mode(config, performanceConstrained = false))
        assertEquals(PuppyMotionPreset.PLAYFUL, config.preset)
    }

    @Test
    fun animatedUiOffResolvesStaticBeforeAdaptivePerformance() {
        val config = PuppyMotionPresets.config(PuppyMotionPreset.BALANCED).copy(animatedUi = false)

        assertEquals(PuppyMotionMode.STATIC, PuppyMotionPolicy.mode(config, performanceConstrained = true))
    }

    @Test
    fun adaptivePerformanceOnlyEngagesWhenEnabledAndConstrained() {
        val enabled = PuppyMotionPresets.config(PuppyMotionPreset.BALANCED)
        val disabled = enabled.copy(adaptivePerformance = false)

        assertEquals(PuppyMotionMode.PERFORMANCE, PuppyMotionPolicy.mode(enabled, performanceConstrained = true))
        assertEquals(PuppyMotionMode.FULL, PuppyMotionPolicy.mode(disabled, performanceConstrained = true))
        assertEquals(PuppyMotionMode.FULL, PuppyMotionPolicy.mode(enabled, performanceConstrained = false))
    }

    @Test
    fun manualMotionOverrideSwitchesPresetToCustom() {
        assertEquals(
            PuppyMotionPreset.CUSTOM,
            PuppyMotionPolicy.afterManualOverride(PuppyMotionPreset.BALANCED)
        )
        assertEquals(
            PuppyMotionPreset.CUSTOM,
            PuppyMotionPolicy.afterManualOverride(PuppyMotionPreset.PLAYFUL)
        )
    }
}
