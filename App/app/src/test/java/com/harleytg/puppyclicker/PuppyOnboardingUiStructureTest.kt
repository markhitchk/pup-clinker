package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingUiStructureTest {
    private fun source(name: String): String {
        val file = File("src/main/java/com/harleytg/puppyclicker/$name")
        assertTrue("$name must exist", file.exists())
        return file.readText()
    }

    @Test
    fun sharedShellUsesFiveStepResponsiveLayout() {
        val source = source("PuppyOnboardingShell.kt")
        assertTrue(source.contains("of 5"))
        assertTrue(source.contains("widthIn(max = 560.dp)"))
        assertTrue(source.contains("imePadding()"))
        assertTrue(source.contains("navigationBarsPadding()"))
        assertTrue(source.contains("LinearProgressIndicator"))
    }
}
