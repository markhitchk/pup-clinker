package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseOnlySaveContractTest {
    private val sourceRoot = File("src/main/java/com/harleytg/puppyclicker")

    @Test
    fun portableSaveImplementationIsRemoved() {
        assertFalse(File(sourceRoot, "GameSaveTransfer.kt").exists())
        assertFalse(File(sourceRoot, "ExternalGameSave.kt").exists())
        assertFalse(File(sourceRoot, "PuppyBackupPasswordStore.kt").exists())
        assertFalse(File(sourceRoot, "SaveTransferErrors.kt").exists())
    }

    @Test
    fun pupAccountCloudSaveIsAuthoritativePersistencePath() {
        val cloud = File(sourceRoot, "PupAccountCloudSave.kt").readText()
        assertTrue(cloud.contains("puppy-clicker-cloud-save"))
        assertTrue(cloud.contains("SecurePreferenceCodec.encode(main)"))
        assertTrue(cloud.contains("writePupAccountCloudSave"))
        assertTrue(cloud.contains("readPupAccountCloudSave"))
        assertTrue(cloud.contains("validateCloudMainStore"))
    }

    @Test
    fun onboardingAndSettingsDoNotExposePortableSaveUi() {
        val onboarding = File(sourceRoot, "PuppyOnboardingPlayerSetup.kt").readText()
        val settings = File(sourceRoot, "PuppySettingsUi.kt").readText()

        listOf(onboarding, settings).forEach { source ->
            assertFalse(source.contains(".pupsave"))
            assertFalse(source.contains("GameSaveTransfer"))
            assertFalse(source.contains("ExternalGameSave"))
            assertFalse(source.contains("Import / Export"))
        }

        assertTrue(settings.contains("Authority\", \"Supabase"))
        assertTrue(settings.contains("Sync to Supabase"))
    }
}
