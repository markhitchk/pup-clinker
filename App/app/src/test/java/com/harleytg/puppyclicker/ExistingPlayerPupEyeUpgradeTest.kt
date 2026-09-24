package com.harleytg.puppyclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExistingPlayerPupEyeUpgradeTest {
    @Test
    fun completedLegacySetupRunsUpgradeOnce() {
        assertTrue(
            ExistingPlayerPupEyeUpgrade.shouldRunExistingPlayerUpgrade(
                setupComplete = true,
                baselineSeen = false,
                appliedVersion = 0
            )
        )
    }

    @Test
    fun freshInstallEstablishesBaselineInsteadOfMigrating() {
        assertFalse(
            ExistingPlayerPupEyeUpgrade.shouldRunExistingPlayerUpgrade(
                setupComplete = false,
                baselineSeen = false,
                appliedVersion = 0
            )
        )
        assertFalse(
            ExistingPlayerPupEyeUpgrade.shouldRunExistingPlayerUpgrade(
                setupComplete = true,
                baselineSeen = true,
                appliedVersion = 0
            )
        )
    }

    @Test
    fun appliedMigrationDoesNotRepeat() {
        assertFalse(
            ExistingPlayerPupEyeUpgrade.shouldRunExistingPlayerUpgrade(
                setupComplete = true,
                baselineSeen = false,
                appliedVersion = 1
            )
        )
    }
}
