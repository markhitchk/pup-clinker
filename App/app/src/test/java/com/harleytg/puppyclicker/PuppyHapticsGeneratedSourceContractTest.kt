package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyHapticsGeneratedSourceContractTest {
    @Test
    fun settingsCompatibilityRunnerDelegatesLegacyHelperToCentralHaptics() {
        val runner = File("../tools/patch_settings_setup_revamp_runner.py").readText()

        assertTrue(runner.contains("PuppyHaptics.perform"))
        assertTrue(runner.contains("PuppyHapticEvent.TEST"))
        assertTrue(runner.contains("PuppyHapticEvent.TAP"))
        assertFalse(runner.contains("VibrationEffect.createPredefined"))
    }
}
