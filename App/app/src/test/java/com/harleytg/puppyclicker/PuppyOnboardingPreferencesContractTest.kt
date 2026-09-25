package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingPreferencesContractTest {
    @Test
    fun preferencesPersistSixStepV4FlowAndPrivacyVersionFive() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt"
        ).readText()

        assertTrue(source.contains("SETUP_FLOW_VERSION = 4"))
        assertTrue(source.contains("CURRENT_PRIVACY_CONSENT_VERSION = 5"))
        assertTrue(source.contains("coerceIn(0, 5)"))
        assertTrue(source.contains("PuppyOnboardingStep.READY.persistedIndex"))
        assertTrue(source.contains("anonymousDiagnosticsEnabled: Boolean = false"))
        assertTrue(source.contains("crashReportsEnabled: Boolean = false"))
        assertTrue(source.contains("store.getBoolean(KEY_ANONYMOUS_DIAGNOSTICS, false)"))
        assertTrue(source.contains("store.getBoolean(KEY_CRASH_REPORTS, false)"))
    }

    @Test
    fun completedPlayersAreNotForcedBackThroughOnboarding() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt"
        ).readText()

        assertTrue(source.contains("setupComplete -> PuppyOnboardingStep.READY.persistedIndex"))
        assertTrue(source.contains(".putBoolean(KEY_SETUP_COMPLETE, setupComplete)"))
        assertTrue(source.contains("previousVersion >= 3 -> migrateV3OnboardingStepToV4(previousStep)"))
    }
}
