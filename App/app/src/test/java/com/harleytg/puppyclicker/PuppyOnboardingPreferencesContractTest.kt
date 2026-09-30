package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingPreferencesContractTest {
    @Test
    fun preferencesPersistSixStepV5PupAccountFlowAndPrivacyVersionFive() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt"
        ).readText()

        assertTrue(source.contains("SETUP_FLOW_VERSION = 5"))
        assertTrue(source.contains("CURRENT_PRIVACY_CONSENT_VERSION = 5"))
        assertTrue(source.contains("coerceIn(0, 5)"))
        assertTrue(source.contains("PuppyOnboardingStep.READY.persistedIndex"))
        assertTrue(source.contains("anonymousDiagnosticsEnabled: Boolean = false"))
        assertTrue(source.contains("crashReportsEnabled: Boolean = false"))
        assertTrue(source.contains("store.getBoolean(KEY_ANONYMOUS_DIAGNOSTICS, false)"))
        assertTrue(source.contains("store.getBoolean(KEY_CRASH_REPORTS, false)"))
    }

    @Test
    fun completedV4PlayersAreRoutedOnceThroughPupAccountSetup() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt"
        ).readText()

        assertTrue(source.contains("setupComplete && previousVersion < 5"))
        assertTrue(source.contains(".putBoolean(KEY_SETUP_COMPLETE, false)"))
        assertTrue(source.contains(".putInt(KEY_SETUP_STEP, PuppyOnboardingStep.PLAYER_SETUP.persistedIndex)"))
        assertTrue(source.contains("previousVersion >= 4 -> previousStep.coerceIn"))
    }
}
