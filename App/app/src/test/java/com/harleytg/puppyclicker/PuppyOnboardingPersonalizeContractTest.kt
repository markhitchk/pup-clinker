package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingPersonalizeContractTest {
    @Test
    fun personalizeCombinesOptionalBirthdayAndAppearance() {
        val file = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingPersonalize.kt"
        )
        assertTrue("PuppyOnboardingPersonalize.kt must exist", file.exists())
        val source = file.readText()

        assertTrue(source.contains("PuppyUiPreferences.setBirthday"))
        assertTrue(source.contains("PuppyUiPreferences.clearBirthday"))
        assertTrue(source.contains("PuppyUiPreferences.setThemeMode"))
        assertTrue(source.contains("PuppyUiPreferences.setAccent"))
        assertTrue(source.contains("PuppyUiPreferences.setUiScale"))
        assertTrue(source.contains("PuppyUiPreferences.setReducedMotion"))
        assertTrue(source.contains("Skip birthday"))
        assertTrue(source.contains("Add birthday instead"))
    }
}
