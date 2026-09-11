package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingImportSafetyContractTest {
    @Test
    fun importedUiPreferencesCannotBypassActiveOnboarding() {
        val transfer = File(
            "src/main/java/com/harleytg/puppyclicker/GameSaveTransfer.kt"
        ).readText()
        val preferences = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt"
        ).readText()

        assertTrue(transfer.contains("preserveIncompleteSetup"))
        assertTrue(transfer.contains("keepSetupIncompleteAfterImport"))
        assertTrue(preferences.contains("fun keepSetupIncompleteAfterImport"))
    }
}
