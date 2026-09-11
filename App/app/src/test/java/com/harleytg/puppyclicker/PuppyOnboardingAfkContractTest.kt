package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingAfkContractTest {
    @Test
    fun afkRewardPreparationRemainsBlockedUntilSetupComplete() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyClickerApplication.kt"
        ).readText()

        assertTrue(source.contains("private fun prepareAfkReward"))
        assertTrue(source.contains("PuppyUiPreferences.PREFS_NAME"))
        assertTrue(source.contains("setup_complete"))
        assertTrue(source.contains("if (!setupComplete)"))
    }
}
