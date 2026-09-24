package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyAdaptiveLayoutTest {
    @Test
    fun widthBreakpointsMatchAppContract() {
        assertEquals(PuppyWindowClass.COMPACT, PuppyAdaptiveLayout.classify(0f))
        assertEquals(PuppyWindowClass.COMPACT, PuppyAdaptiveLayout.classify(599.9f))
        assertEquals(PuppyWindowClass.MEDIUM, PuppyAdaptiveLayout.classify(600f))
        assertEquals(PuppyWindowClass.MEDIUM, PuppyAdaptiveLayout.classify(839.9f))
        assertEquals(PuppyWindowClass.EXPANDED, PuppyAdaptiveLayout.classify(840f))
        assertEquals(PuppyWindowClass.EXPANDED, PuppyAdaptiveLayout.classify(2000f))
    }

    @Test
    fun legacyWidthApiStillMatchesApprovedBreakpoints() {
        assertEquals(PuppyWindowWidthClass.COMPACT, PuppyAdaptiveLayout.widthClass(599.9f))
        assertEquals(PuppyWindowWidthClass.MEDIUM, PuppyAdaptiveLayout.widthClass(600f))
        assertEquals(PuppyWindowWidthClass.EXPANDED, PuppyAdaptiveLayout.widthClass(840f))
    }

    @Test
    fun rosterUsesTwoPaneOnlyWhenExpanded() {
        assertFalse(PuppyAdaptiveLayout.useRosterTwoPane(PuppyWindowWidthClass.COMPACT))
        assertFalse(PuppyAdaptiveLayout.useRosterTwoPane(PuppyWindowWidthClass.MEDIUM))
        assertTrue(PuppyAdaptiveLayout.useRosterTwoPane(PuppyWindowWidthClass.EXPANDED))
    }

    @Test
    fun heightBreakpointsIdentifyShortAndTallWindows() {
        assertEquals(PuppyHeightClass.SHORT, PuppyAdaptiveLayout.classifyHeight(599.9f))
        assertEquals(PuppyHeightClass.REGULAR, PuppyAdaptiveLayout.classifyHeight(600f))
        assertEquals(PuppyHeightClass.REGULAR, PuppyAdaptiveLayout.classifyHeight(839.9f))
        assertEquals(PuppyHeightClass.TALL, PuppyAdaptiveLayout.classifyHeight(840f))
    }

    @Test
    fun viewportCombinesWidthAndHeightClasses() {
        val viewport = PuppyAdaptiveLayout.viewport(widthDp = 720f, heightDp = 540f)
        assertEquals(PuppyWindowClass.MEDIUM, viewport.widthClass)
        assertEquals(PuppyHeightClass.SHORT, viewport.heightClass)
    }
}
