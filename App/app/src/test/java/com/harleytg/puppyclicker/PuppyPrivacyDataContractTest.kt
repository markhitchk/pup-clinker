package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyPrivacyDataContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/$name").readText()

    @Test
    fun privacyDisclosesGlobalEnforcementAndDeviceReputation() {
        val privacy = File("../assets/legal/privacy-policy.md").readText()
        assertTrue(privacy.contains("global enforcement"))
        assertTrue(privacy.contains("device reputation"))
        assertTrue(privacy.contains("save-content hash"))
        assertTrue(privacy.contains("Discord moderation notification"))
    }

    @Test
    fun onboardingExplainsOptionalConsentAndSeparatesNotificationPermission() {
        val privacy = source("PuppyPrivacyDataUi.kt")
        val onboarding = source("PuppyOnboardingUi.kt")

        assertTrue(onboarding.contains("PuppyOnboardingStep.PRIVACY"))
        assertTrue(onboarding.contains("PuppyOnboardingPrivacy"))
        assertTrue(privacy.contains("Optional anonymous diagnostics"))
        assertTrue(privacy.contains("Optional crash reports"))
        assertTrue(privacy.contains("OFF by default"))
        assertTrue(privacy.contains("Android notification permission is requested only there"))
        assertTrue(privacy.contains("Turning off optional telemetry does not disable local protection"))
    }

    @Test
    fun privacyControlsAreAvailableFromSettingsAndSupportWithdrawal() {
        val settings = source("PuppySettingsUi.kt")
        val privacy = source("PuppyPrivacyDataUi.kt")

        assertTrue(settings.contains("SettingsDestination.PRIVACY"))
        assertTrue(settings.contains("Privacy & Data"))
        assertTrue(settings.contains("PuppyPrivacyDataSettings(ui)"))
        assertTrue(privacy.contains("setAnonymousDiagnosticsEnabled"))
        assertTrue(privacy.contains("setCrashReportsEnabled"))
        assertTrue(privacy.contains("withdraws consent"))
    }

    @Test
    fun pupEyeSeparatesCurrentProtectionFromHistoricalEvents() {
        val settings = source("PuppySettingsUi.kt")
        val crypto = source("SecureSaveCrypto.kt")
        val app = source("PuppyClickerApplication.kt")

        assertTrue(settings.contains("val currentProtected"))
        assertTrue(settings.contains("val currentWarning"))
        assertTrue(settings.contains("Previous integrity events"))
        assertTrue(crypto.contains("private var authorizedWritePending = false"))
        assertTrue(crypto.contains("KEY_AUTHORIZED_WRITE_PENDING"))
        assertTrue(crypto.contains("fun noteAuthorizedPreferenceChange(context: Context)"))
        assertTrue(crypto.contains("private fun hasAuthorizedPreferenceChange(context: Context)"))
        assertTrue(crypto.contains("if (hasAuthorizedPreferenceChange(context))"))
        assertTrue(app.contains("PupEyeSaveGuard.noteAuthorizedPreferenceChange(this)"))
    }

}
