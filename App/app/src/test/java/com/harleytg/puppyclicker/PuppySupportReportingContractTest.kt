package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppySupportReportingContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun androidClientUsesNeutralRelayAndNeverEmbedsDiscordWebhook() {
        val reporting = source(
            "src/main/java/com/harleytg/puppyclicker/PuppySupportReporting.kt"
        )
        val gradle = source("build.gradle.kts")

        assertTrue(reporting.contains("BuildConfig.PUPPY_SUPPORT_RELAY_URL"))
        assertTrue(gradle.contains("PUPPY_SUPPORT_RELAY_URL"))
        assertFalse(reporting.contains("discord.com/api/webhooks/"))
    }

    @Test
    fun telemetryAndCrashUploadsRemainSeparatelyConsentGated() {
        val reporting = source(
            "src/main/java/com/harleytg/puppyclicker/PuppySupportReporting.kt"
        )

        assertTrue(reporting.contains("if (!ui.anonymousDiagnosticsEnabled) return"))
        assertTrue(reporting.contains("if (ui.crashReportsEnabled && isConfigured())"))
    }

    @Test
    fun supportIdentityIsSeparateOptInAndEconomyDataStaysExcluded() {
        val reporting = source(
            "src/main/java/com/harleytg/puppyclicker/PuppySupportReporting.kt"
        )

        assertTrue(reporting.contains("if (!ui.supportIdentityEnabled) return"))
        assertTrue(reporting.contains("PuppyPlayerIdentity.username(context)"))
        assertTrue(reporting.contains("PuppyPlayerIdentity.publicPlayerId(context)"))
        assertTrue(reporting.contains("PuppyPlayerIdentity.publicFriendCode(context)"))
        assertTrue(reporting.contains("DiscordSignupAuth.account(context)"))
        assertFalse(reporting.contains("birthday"))
        assertFalse(reporting.contains("treats"))
        assertFalse(reporting.contains("accessToken"))
        assertTrue(reporting.contains(".put(\"android_sdk\", Build.VERSION.SDK_INT)"))
        assertTrue(reporting.contains(".put(\"app_version\", BuildConfig.VERSION_NAME)"))
    }

    @Test
    fun applicationInstallsSupportReportingAtStartup() {
        val app = source(
            "src/main/java/com/harleytg/puppyclicker/PuppyClickerApplication.kt"
        )

        assertTrue(
            app.contains(
                "startupSafely(\"support reporting\") { PuppySupportReporting.initialize(this) }"
            )
        )
    }

    @Test
    fun serverRelayBrandsDiscordEmbedsWithPuppyClickerLogoAndName() {
        val relay = source(
            "../../services/puppy-support-relay.php"
        )

        assertTrue(relay.contains("PUPPY_APP_NAME = 'Puppy Clicker'"))
        assertTrue(relay.contains("assets/logos/puppy_clicker.png"))
        assertTrue(relay.contains("'avatar_url' => PUPPY_APP_LOGO_URL"))
        assertTrue(relay.contains("'icon_url' => PUPPY_APP_LOGO_URL"))
        assertTrue(relay.contains("'thumbnail' => ['url' => PUPPY_APP_LOGO_URL]"))
        assertTrue(relay.contains("'Puppy Clicker User'"))
        assertTrue(relay.contains("'Discord Identity'"))
    }

    @Test
    fun serverRelayKeepsDiscordCredentialsInEnvironmentOnly() {
        val relay = source(
            "../../services/puppy-support-relay.php"
        )

        assertTrue(relay.contains("PUPPY_DISCORD_TELEMETRY_WEBHOOK"))
        assertTrue(relay.contains("PUPPY_DISCORD_CRASH_WEBHOOK"))
        assertTrue(relay.contains("allowed_mentions"))
        assertFalse(relay.contains("/api/webhooks/154"))
    }

    @Test
    fun privacyUiDisclosesSupportDiscordRelay() {
        val privacy = source(
            "src/main/java/com/harleytg/puppyclicker/PuppyPrivacyDataUi.kt"
        )

        assertTrue(privacy.contains("Discord support diagnostics channel"))
        assertTrue(privacy.contains("Include account identity in support reports"))
        assertTrue(privacy.contains("Turning this off withdraws consent"))
    }
}
