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
    fun v3UnfinishedNotificationOrReadyStepsRouteThroughPrivacy() {
        assertEquals(PuppyOnboardingStep.WELCOME.persistedIndex, migrateV3OnboardingStepToV4(0))
        assertEquals(PuppyOnboardingStep.PLAYER_SETUP.persistedIndex, migrateV3OnboardingStepToV4(1))
        assertEquals(PuppyOnboardingStep.PERSONALIZE.persistedIndex, migrateV3OnboardingStepToV4(2))
        assertEquals(PuppyOnboardingStep.PRIVACY.persistedIndex, migrateV3OnboardingStepToV4(3))
        assertEquals(PuppyOnboardingStep.PRIVACY.persistedIndex, migrateV3OnboardingStepToV4(4))
    }

    @Test
    fun freshSessionDefaultsToPasswordlessDiscordPupAccount() {
        val state = PuppyOnboardingSessionState()
        assertEquals(PuppyPlayerSetupMethod.DISCORD, state.playerSetupMethod)
        assertFalse(state.birthdaySkipped)
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
    fun blockedUsernameCanStillReachModerationPopup() {
        assertTrue(localUsernameSubmissionEnabled("fuck"))
        assertFalse(localUsernameSubmissionEnabled("   "))
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
