package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingPreferencesContractTest {
    @Test
    fun preferencesPersistFiveStepFlowVersion() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt"
        ).readText()

        assertTrue(source.contains("SETUP_FLOW_VERSION = 3"))
        assertTrue(source.contains("coerceIn(0, 4)"))
        assertTrue(source.contains("putInt(KEY_SETUP_STEP, 4)"))
        assertTrue(source.contains("putBoolean(KEY_SETUP_COMPLETE, false)"))
        assertTrue(source.contains("PuppyOnboardingStep.WELCOME.persistedIndex"))
    }
}
