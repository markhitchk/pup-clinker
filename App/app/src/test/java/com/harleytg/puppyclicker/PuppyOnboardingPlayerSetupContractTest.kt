package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingPlayerSetupContractTest {
    @Test
    fun playerSetupRendersOnlySelectedMethodAndReusesExistingServices() {
        val file = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingPlayerSetup.kt"
        )
        assertTrue("PuppyOnboardingPlayerSetup.kt must exist", file.exists())
        val source = file.readText()

        assertTrue(source.contains("when (session.playerSetupMethod)"))
        assertTrue(source.contains("PuppyPlayerSetupMethod.LOCAL"))
        assertTrue(source.contains("PuppyPlayerSetupMethod.DISCORD"))
        assertTrue(source.contains("PuppyPlayerSetupMethod.IMPORT_SAVE"))
        assertTrue(source.contains("DiscordSignupAuth.observe"))
        assertTrue(source.contains("GameSaveTransfer.passwordRequirement"))
        assertTrue(source.contains("GameSaveTransfer.import"))
        assertTrue(source.contains("vm.reloadImportedSave()"))
    }
}
