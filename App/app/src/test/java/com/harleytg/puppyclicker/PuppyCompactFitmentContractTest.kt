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
    fun primaryScreensUseMinimalTopAndNoBottomContentPadding() {
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
            assertTrue(name + " should use a 2dp top inset", source.contains("top = 2.dp"))
            assertTrue(name + " should remove extra bottom padding", source.contains("bottom = 0.dp"))
        }

        val finalPatch = File("../../tools/patch_compact_viewport.py").readText()
        val buildWiring = File("../../tools/seasonal.gradle.kts").readText()
        assertTrue(finalPatch.contains("top = 2.dp"))
        assertTrue(finalPatch.contains("bottom = 0.dp"))
        assertTrue(buildWiring.contains("compactViewportPatch"))
    }

    @Test
    fun onboardingKeepsSystemNavigationSafetyButUsesSmallerChrome() {
        val shell = appSource("PuppyOnboardingShell.kt")

        assertTrue(shell.contains(".navigationBarsPadding()"))
        assertTrue(shell.contains("headerVerticalPadding = if (preferViewportFit) 2.dp else 4.dp"))
        assertTrue(shell.contains("bodyVerticalPadding = if (preferViewportFit) 0.dp else 2.dp"))
        assertTrue(shell.contains("navigationVerticalPadding = if (preferViewportFit) 2.dp else 4.dp"))
    }
}
