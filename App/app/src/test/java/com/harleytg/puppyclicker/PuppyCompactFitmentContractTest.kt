package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCompactFitmentContractTest {
    private fun appSource(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun generatedShellDoesNotDoubleApplySafeDrawingInsets() {
        val patch = File("../../tools/patch_roster_navigation.py").readText()

        assertTrue(patch.contains(".padding(padding)"))
        assertFalse(
            patch.contains(
                ".padding(padding)\n                .windowInsetsPadding(WindowInsets.safeDrawing)"
            )
        )
        assertTrue(patch.contains(".height(48.dp)"))
        assertTrue(patch.contains(".windowInsetsPadding(WindowInsets.statusBars)"))
    }

    @Test
    fun primaryScreensUseCompactVerticalContentPadding() {
        listOf(
            "PuppySlotsUi.kt",
            "PuppyRouletteUi.kt",
            "PuppyBlackjackUi.kt",
            "PuppyCasinoHub.kt",
            "PuppySettingsUi.kt",
            "PuppyRosterScreen.kt",
            "PuppyDeveloperConsole.kt",
            "PuppyExchangeUi.kt"
        ).forEach { name ->
            val source = appSource(name)
            assertTrue(
                name + " should use compact 4dp vertical padding",
                source.contains("vertical = 4.dp")
            )
        }
    }

    @Test
    fun onboardingKeepsSystemNavigationSafetyButUsesSmallerChrome() {
        val shell = appSource("PuppyOnboardingShell.kt")

        assertTrue(shell.contains(".navigationBarsPadding()"))
        assertTrue(shell.contains("headerVerticalPadding = if (preferViewportFit) 4.dp else 8.dp"))
        assertTrue(shell.contains("bodyVerticalPadding = if (preferViewportFit) 2.dp else 4.dp"))
        assertTrue(shell.contains("navigationVerticalPadding = if (preferViewportFit) 4.dp else 6.dp"))
    }
}
