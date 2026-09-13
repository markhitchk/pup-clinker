package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingFlowContractTest {
    @Test
    fun flowRoutesExactlySixFocusedSteps() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt"
        ).readText()
        val shell = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingShell.kt"
        ).readText()

        assertTrue(source.contains("coerceIn(0, 5)"))
        assertTrue(source.contains("PuppyOnboardingPlayerSetup("))
        assertTrue(source.contains("PuppyOnboardingPersonalize("))
        assertTrue(source.contains("PuppyOnboardingPrivacy("))
        assertTrue(source.contains("PuppyOnboardingNotifications("))
        assertTrue(source.contains("PuppyOnboardingReady("))
        assertTrue(shell.contains("repeat(6)"))
        assertTrue(shell.contains("\" of 6\""))
    }

    @Test
    fun birthdaySkipStartsAsExplicitFalseChoice() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt"
        ).readText()

        assertTrue(
            source.contains(
                "var birthdaySkipped by rememberSaveable { mutableStateOf(false) }"
            )
        )
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
