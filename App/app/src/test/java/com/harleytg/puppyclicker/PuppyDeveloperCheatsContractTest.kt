package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyDeveloperCheatsContractTest {
    private fun appSource(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun casinoXrayIsRemovedFromShippedUi() {
        val cheats = appSource("PuppyDeveloperCheats.kt")
        val settings = appSource("PuppySettingsUi.kt")
        val activity = appSource("PuppyClickerV6Activity.kt")

        assertFalse(cheats.contains("Casino X-Ray"))
        assertFalse(cheats.contains("X-RAY"))
        assertFalse(cheats.contains("developerCasinoXrayLines"))
        assertFalse(cheats.contains("PuppyDeveloperCheatOverlay"))
        assertFalse(cheats.contains("PuppyDeveloperCheatsSettings"))
        assertFalse(settings.contains("PuppyDeveloperCheatsSettings("))
        assertFalse(activity.contains("PuppyDeveloperCheatOverlay("))
    }

    @Test
    fun removedInspectorCannotActivateNewDeveloperCasinoRounds() {
        val cheats = appSource("PuppyDeveloperCheats.kt")

        assertTrue(cheats.contains("fun isActive(): Boolean = false"))
        assertFalse(cheats.contains("fun activate()"))
        assertFalse(cheats.contains("fun disable()"))
    }

    @Test
    fun legacyDeveloperRoundPrefixRemainsRecognizedForSafeRecovery() {
        val cheats = appSource("PuppyDeveloperCheats.kt")

        assertTrue(cheats.contains("DEV_ROUND_PREFIX"))
        assertTrue(cheats.contains("devtest_"))
        assertTrue(cheats.contains("isTestRoundId"))
    }
}
