package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingPlayerSetupContractTest {
    @Test
    fun playerSetupUsesPasswordlessDiscordPupAccountOnly() {
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
        assertTrue(!source.contains("GameSaveTransfer"))
        assertTrue(!source.contains(".pupsave"))
        assertTrue(!source.contains("Restore a Save"))
        assertTrue(!source.contains("Import"))
        assertTrue(!source.contains("Confirm password"))
        assertTrue(!source.contains("Save Account & Continue"))
    }
}
