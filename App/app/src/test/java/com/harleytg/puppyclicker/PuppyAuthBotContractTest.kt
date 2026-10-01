package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyAuthBotContractTest {
    private fun source(name: String): String {
        val file = File("src/main/java/com/harleytg/puppyclicker/$name")
        return if (file.isFile) file.readText() else File("../src/main/java/com/harleytg/puppyclicker/$name").readText()
    }

    @Test
    fun botClientUsesSessionHeadersAndDocumentedRoutes() {
        val source = source("PuppyAuthBotClient.kt")
        assertTrue(source.contains("X-Pupeye-Session"))
        assertTrue(source.contains("/v1/verify/send"))
        assertTrue(source.contains("/v1/verify/submit"))
        assertTrue(source.contains("/v1/verify/resend"))
        assertTrue(source.contains("/v1/verify/status"))
        assertTrue(source.contains("/v1/support/requests"))
        assertTrue(source.contains("/v1/live"))
        assertTrue(source.contains("hardware_reset"))
        assertTrue(source.contains("account_recovery"))
        assertTrue(source.contains("ban_appeal"))
        assertTrue(source.contains("offlineSnapshot(it.message, preserveVerified = false)"))
        assertTrue(source.contains("offlineSnapshot(it.message, preserveVerified = true)"))
        assertFalse(source.contains("bot token"))
    }

    @Test
    fun pupAccountOauthAndRoleDmVerificationAreSeparate() {
        val settings = source("PuppySettingsUi.kt")
        val auth = source("DiscordSignupAuth.kt")
        val activity = source("PuppyClickerV6Activity.kt")
        val codeUi = source("PuppyDiscordCodeEntryUi.kt")
        val onboarding = source("PuppyOnboardingPlayerSetup.kt")

        assertTrue(settings.contains("Text(\"Unlink\")"))
        assertTrue(settings.contains("Text(\"Link\")"))
        assertTrue(settings.contains("Not linked"))
        assertTrue(settings.contains("DiscordSignupAuth.startRoleVerification(context)"))
        assertTrue(settings.contains("Verify role"))
        assertFalse(settings.contains("verifyGuildRole = true"))

        assertTrue(auth.contains("saveAccount(app, account)"))
        assertTrue(auth.contains("PupAccountCloudSave.activate(app)"))
        assertTrue(auth.contains("suspend fun startRoleVerification"))
        assertTrue(auth.contains("PuppyAuthBotClient.sendVerificationCode(app, account)"))
        assertTrue(auth.contains("fun completeRoleVerification"))
        assertTrue(auth.contains("snapshot.discordId != account.id"))
        assertTrue(auth.contains("Settings -> Verify server role is the separate DM-code proof."))

        assertTrue(activity.contains("PuppyDiscordCodeEntryDialog"))
        assertTrue(activity.contains("DiscordSignupAuth.completeRoleVerification"))
        assertTrue(activity.contains("DiscordSignupAuth.cancelRoleVerification"))
        assertTrue(codeUi.contains("DiscordSignupAuth.resendRoleVerification"))
        assertTrue(codeUi.contains("server-role verification code"))

        assertTrue(onboarding.contains("No separate Puppy Clicker password or bot code is required for login."))
        assertFalse(onboarding.contains("Verification code pending…"))

        assertTrue(settings.contains("title = \"Advanced\""))
        assertTrue(settings.contains("title = \"Account details\""))
        assertTrue(settings.contains("PuppyIdentityHeaderCard"))
    }

    @Test
    fun banScreenAddsCheckAgainWithoutRemovingSupport() {
        val source = source("PupEyeEnforcementUi.kt")
        assertTrue(source.contains("Check again"))
        assertTrue(source.contains("Contact Support"))
        assertTrue(source.contains("PuppyInAppSupportPanel"))
    }

    @Test
    fun cleartextIsLimitedToTheAuthBotHost() {
        val config = File("src/main/res/xml/network_security_config.xml").let {
            if (it.isFile) it.readText() else File("../src/main/res/xml/network_security_config.xml").readText()
        }
        assertTrue(config.contains("fi13.bot-hosting.cloud"))
        assertTrue(config.contains("cleartextTrafficPermitted=\"true\""))
        assertFalse(config.contains("includeSubdomains=\"true\""))
    }
}
