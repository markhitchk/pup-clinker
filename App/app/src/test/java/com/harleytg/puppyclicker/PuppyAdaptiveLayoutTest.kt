package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyAdaptiveLayoutTest {
    @Test
    fun breakpointsMatchApprovedSpec() {
        assertEquals(PuppyWindowWidthClass.COMPACT, PuppyAdaptiveLayout.widthClass(0f))
        assertEquals(PuppyWindowWidthClass.COMPACT, PuppyAdaptiveLayout.widthClass(599.9f))
        assertEquals(PuppyWindowWidthClass.MEDIUM, PuppyAdaptiveLayout.widthClass(600f))
        assertEquals(PuppyWindowWidthClass.MEDIUM, PuppyAdaptiveLayout.widthClass(839.9f))
        assertEquals(PuppyWindowWidthClass.EXPANDED, PuppyAdaptiveLayout.widthClass(840f))
    }

    @Test
    fun rosterUsesTwoPaneOnlyWhenExpanded() {
        assertFalse(PuppyAdaptiveLayout.useRosterTwoPane(PuppyWindowWidthClass.COMPACT))
        assertFalse(PuppyAdaptiveLayout.useRosterTwoPane(PuppyWindowWidthClass.MEDIUM))
        assertTrue(PuppyAdaptiveLayout.useRosterTwoPane(PuppyWindowWidthClass.EXPANDED))
    }
}
