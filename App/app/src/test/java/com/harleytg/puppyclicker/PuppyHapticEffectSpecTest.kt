package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyHapticEffectSpecTest {
    @Test
    fun directFallbackUsesDeterministicOneShotSpecs() {
        val light = PuppyHaptics.directEffectSpec(PuppyHapticStrength.LIGHT)
        val medium = PuppyHaptics.directEffectSpec(PuppyHapticStrength.MEDIUM)
        val strong = PuppyHaptics.directEffectSpec(PuppyHapticStrength.STRONG)

        assertEquals(22L, light.durationMs)
        assertEquals(38L, medium.durationMs)
        assertEquals(58L, strong.durationMs)
        assertTrue(light.amplitude < medium.amplitude)
        assertTrue(medium.amplitude < strong.amplitude)
    }

    @Test
    fun semanticKindsAreStableForUiEvents() {
        assertEquals(PuppySemanticHaptic.VIRTUAL_KEY, PuppyHaptics.semanticKind(PuppyHapticEvent.TAP))
        assertEquals(PuppySemanticHaptic.CLOCK_TICK, PuppyHaptics.semanticKind(PuppyHapticEvent.SELECTION))
        assertEquals(PuppySemanticHaptic.CLOCK_TICK, PuppyHaptics.semanticKind(PuppyHapticEvent.NAVIGATION))
        assertEquals(PuppySemanticHaptic.CLOCK_TICK, PuppyHaptics.semanticKind(PuppyHapticEvent.TOGGLE))
        assertEquals(PuppySemanticHaptic.CONTEXT_CLICK, PuppyHaptics.semanticKind(PuppyHapticEvent.SUCCESS))
        assertEquals(PuppySemanticHaptic.LONG_PRESS, PuppyHaptics.semanticKind(PuppyHapticEvent.ERROR))
    }
}
