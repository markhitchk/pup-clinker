package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingPlayerSetupContractTest {
    @Test
    fun playerSetupUsesPasswordlessDiscordPupAccountAndLegacyImport() {
        val file = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingPlayerSetup.kt"
        )
        assertTrue("PuppyOnboardingPlayerSetup.kt must exist", file.exists())
        val source = file.readText()

        assertTrue(source.contains("Pup Account · T0"))
        assertTrue(source.contains("Passwordless sign-in with Discord"))
        assertTrue(source.contains("PuppyPlayerSetupMethod.DISCORD"))
        assertTrue(source.contains("DiscordSignupAuth.observe"))
        assertTrue(source.contains("PupAccountCloudSave.activateAndAwait"))
        assertTrue(source.contains("GameSaveTransfer.passwordRequirement"))
        assertTrue(source.contains("GameSaveTransfer.import"))
        assertTrue(source.contains("PupAccountCloudSave.queueSync(context, reason = \"legacy-import\")"))
        assertTrue(source.contains("vm.reloadImportedSave()"))
        assertTrue(!source.contains("Confirm password"))
        assertTrue(!source.contains("Save Account & Continue"))
    }
}
