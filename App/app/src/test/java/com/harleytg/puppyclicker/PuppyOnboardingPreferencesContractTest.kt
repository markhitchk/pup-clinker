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

        assertTrue(source.contains("SETUP_FLOW_VERSION = 2"))
        assertTrue(source.contains("coerceIn(0, 4)"))
        assertTrue(source.contains("putInt(KEY_SETUP_STEP, 4)"))
    }
}
