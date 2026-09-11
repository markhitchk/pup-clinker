package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingFlowContractTest {
    @Test
    fun flowRoutesExactlyFiveFocusedSteps() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt"
        ).readText()

        assertTrue(source.contains("coerceIn(0, 4)"))
        assertTrue(source.contains("PuppyOnboardingPlayerSetup("))
        assertTrue(source.contains("PuppyOnboardingPersonalize("))
        assertTrue(source.contains("PuppyOnboardingNotifications("))
        assertTrue(source.contains("PuppyOnboardingReady("))
        assertFalse(source.contains("repeat(6)"))
        assertFalse(source.contains("Step " + "$" + "{step + 1} of 6"))
    }

    @Test
    fun flowCompletesSetupOnlyFromReadyCallback() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt"
        ).readText()

        assertTrue(source.contains("PuppyUiPreferences.finishSetup(context)"))
        assertTrue(source.contains("vm.dismissSeasonalIntro()"))
        assertTrue(source.contains("onStartPlaying"))
    }
}
