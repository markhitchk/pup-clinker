package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoCartoonUiTest {
    @Test
    fun clampUnitKeepsAnimationValuesInRange() {
        assertEquals(0f, PuppyCasinoCartoonMath.clampUnit(-0.25f), 0.0001f)
        assertEquals(0.45f, PuppyCasinoCartoonMath.clampUnit(0.45f), 0.0001f)
        assertEquals(1f, PuppyCasinoCartoonMath.clampUnit(1.4f), 0.0001f)
    }

    @Test
    fun wheelSegmentCenterUsesPublishedWeights() {
        val weights = listOf(2f, 8f, 15f, 25f, 25f, 15f, 10f)
        val center0 = PuppyCasinoCartoonMath.segmentCenterDegrees(weights, 0)
        val center3 = PuppyCasinoCartoonMath.segmentCenterDegrees(weights, 3)

        assertEquals(-86.4f, center0, 0.01f)
        assertEquals(90f, center3, 0.01f)
    }

    @Test
    fun bouncePulsePeaksAtImpactAndFadesAway() {
        assertEquals(1f, PuppyCasinoCartoonMath.impactPulse(0.5f, 0.5f, 0.1f), 0.0001f)
        assertTrue(PuppyCasinoCartoonMath.impactPulse(0.45f, 0.5f, 0.1f) > 0f)
        assertEquals(0f, PuppyCasinoCartoonMath.impactPulse(0.2f, 0.5f, 0.1f), 0.0001f)
    }
}
