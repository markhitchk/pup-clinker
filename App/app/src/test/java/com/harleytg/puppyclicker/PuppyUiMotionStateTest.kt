package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyUiMotionStateTest {
    @Test
    fun uiStateDefaultsToBalancedMediumMotion() {
        val state = PuppyUiState()

        assertEquals(PuppyMotionPreset.BALANCED, state.motionPreset)
        assertEquals(PuppyMotionIntensity.MEDIUM, state.motionIntensity)
        assertTrue(state.screenTransitions)
        assertTrue(state.cardAnimations)
        assertTrue(state.counterAnimations)
        assertTrue(state.celebrationAnimations)
        assertTrue(state.warningAnimations)
        assertTrue(state.loadingAnimations)
        assertTrue(state.shimmerEffects)
        assertTrue(state.adaptivePerformance)
        assertFalse(state.reducedMotion)
    }

    @Test
    fun uiStateConvertsToRuntimeMotionConfigWithoutLosingOverrides() {
        val state = PuppyUiState(
            motionPreset = PuppyMotionPreset.CUSTOM,
            motionIntensity = PuppyMotionIntensity.HIGH,
            animatedUi = true,
            buttonAnimations = false,
            screenTransitions = false,
            cardAnimations = true,
            counterAnimations = false,
            celebrationAnimations = true,
            warningAnimations = false,
            loadingAnimations = true,
            shimmerEffects = false,
            adaptivePerformance = false,
            reducedMotion = true
        )

        val config = state.motionConfig()
        assertEquals(PuppyMotionPreset.CUSTOM, config.preset)
        assertEquals(PuppyMotionIntensity.HIGH, config.intensity)
        assertTrue(config.animatedUi)
        assertFalse(config.buttonAnimations)
        assertFalse(config.screenTransitions)
        assertTrue(config.cardAnimations)
        assertFalse(config.counterAnimations)
        assertTrue(config.celebrations)
        assertFalse(config.warningAnimations)
        assertTrue(config.loadingAnimations)
        assertFalse(config.shimmerEffects)
        assertFalse(config.adaptivePerformance)
        assertTrue(config.reducedMotion)
    }
}
