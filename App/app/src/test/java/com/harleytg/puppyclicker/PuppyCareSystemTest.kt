package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCareSystemTest {
    @Test
    fun tiredThresholdBlocksAtTenEnergy() {
        assertTrue(PuppyCareSystem.isTired(10))
        assertTrue(PuppyCareSystem.isTired(0))
        assertFalse(PuppyCareSystem.isTired(11))
    }

    @Test
    fun healthyCareAddsOneTreatPerTap() {
        val care = PuppyCareProfile(
            happiness = 80,
            fullness = 80,
            energy = 80,
            cleanliness = 80,
            bond = 25
        )
        assertEquals(1, PuppyCareSystem.tapBonus(care))
    }

    @Test
    fun thrivingCareAndBondAddsTwoTreatsPerTap() {
        val care = PuppyCareProfile(
            happiness = 95,
            fullness = 95,
            energy = 95,
            cleanliness = 95,
            bond = 70
        )
        assertEquals(2, PuppyCareSystem.tapBonus(care))
    }

    @Test
    fun neglectedCareHasNoTapBonus() {
        val care = PuppyCareProfile(
            happiness = 60,
            fullness = 50,
            energy = 65,
            cleanliness = 60,
            bond = 80
        )
        assertEquals(0, PuppyCareSystem.tapBonus(care))
    }

    @Test
    fun offlineDecayIsAppliedToOneCareProfile() {
        val decayed = PuppyCareSystem.decay(
            PuppyCareProfile(
                happiness = 100,
                fullness = 100,
                energy = 100,
                cleanliness = 100,
                bond = 55
            ),
            elapsedMinutes = 60
        )

        assertEquals(80, decayed.happiness)
        assertEquals(70, decayed.fullness)
        assertEquals(85, decayed.energy)
        assertEquals(85, decayed.cleanliness)
        assertEquals(55, decayed.bond)
    }
}
