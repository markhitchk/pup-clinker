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
        assertFalse(PuppyMainDestination.entries.any { it.label == "Puppy Exchange" })
    }

    @Test
    fun settingsPrestigeAndExchangeRemainInternalDestinations() {
        assertEquals(
            listOf("Settings", "Prestige", "Puppy Exchange"),
            PuppyInternalDestination.entries.map { it.label }
        )
    }
}
