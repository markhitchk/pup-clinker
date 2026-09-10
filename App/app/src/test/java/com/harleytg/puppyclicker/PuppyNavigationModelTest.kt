package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PuppyNavigationModelTest {
    @Test
    fun bottomNavigationContainsExactlyFiveApprovedDestinations() {
        assertEquals(
            listOf("Play", "Care", "Roster", "Shop", "Rewards"),
            PuppyMainDestination.entries.map { it.label }
        )
        assertFalse(PuppyMainDestination.entries.any { it.label == "Settings" })
        assertFalse(PuppyMainDestination.entries.any { it.label == "Prestige" })
    }

    @Test
    fun settingsAndPrestigeRemainInternalDestinations() {
        assertEquals(
            listOf("Settings", "Prestige"),
            PuppyInternalDestination.entries.map { it.label }
        )
    }
}
