package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingModelTest {
    @Test
    fun legacySixStepIndexesMapDeterministicallyToFiveSteps() {
        assertEquals(0, migrateLegacyOnboardingStep(0))
        assertEquals(1, migrateLegacyOnboardingStep(1))
        assertEquals(2, migrateLegacyOnboardingStep(2))
        assertEquals(2, migrateLegacyOnboardingStep(3))
        assertEquals(3, migrateLegacyOnboardingStep(4))
        assertEquals(4, migrateLegacyOnboardingStep(5))
    }

    @Test
    fun migrationClampsUnknownLegacyValues() {
        assertEquals(0, migrateLegacyOnboardingStep(-99))
        assertEquals(4, migrateLegacyOnboardingStep(99))
    }

    @Test
    fun freshSessionDefaultsToLocalProfile() {
        val state = PuppyOnboardingSessionState()
        assertEquals(PuppyPlayerSetupMethod.LOCAL, state.playerSetupMethod)
        assertFalse(state.importedSave)
        assertFalse(state.birthdaySkipped)
    }

    @Test
    fun changingPlayerSetupMethodPreservesSessionFlags() {
        val imported = PuppyOnboardingSessionState(
            playerSetupMethod = PuppyPlayerSetupMethod.IMPORT_SAVE,
            importedSave = true
        )

        val switched = imported.copy(playerSetupMethod = PuppyPlayerSetupMethod.LOCAL)

        assertTrue(switched.importedSave)
        assertEquals(PuppyPlayerSetupMethod.LOCAL, switched.playerSetupMethod)
    }

    @Test
    fun birthdaySkipIsExplicitSessionState() {
        val state = PuppyOnboardingSessionState().copy(birthdaySkipped = true)
        assertTrue(state.birthdaySkipped)
    }

    @Test
    fun validLocalUsernameCanContinue() {
        assertTrue(localUsernameEligible("puppy_player"))
        assertFalse(localUsernameEligible("   "))
    }

    @Test
    fun notificationPermissionRequestedOnlyWhenNeeded() {
        assertEquals(
            PuppyNotificationPermissionDecision.REQUEST,
            notificationPermissionDecision(
                sdkInt = 35,
                notificationsEnabled = true,
                permissionGranted = false
            )
        )
        assertEquals(
            PuppyNotificationPermissionDecision.NONE,
            notificationPermissionDecision(
                sdkInt = 32,
                notificationsEnabled = true,
                permissionGranted = false
            )
        )
        assertEquals(
            PuppyNotificationPermissionDecision.NONE,
            notificationPermissionDecision(
                sdkInt = 35,
                notificationsEnabled = false,
                permissionGranted = false
            )
        )
        assertEquals(
            PuppyNotificationPermissionDecision.NONE,
            notificationPermissionDecision(
                sdkInt = 35,
                notificationsEnabled = true,
                permissionGranted = true
            )
        )
    }
}
