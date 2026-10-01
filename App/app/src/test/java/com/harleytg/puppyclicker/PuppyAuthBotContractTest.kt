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
        assertFalse(source.contains("bot token"))
    }

    @Test
    fun pupAccountLoginIsGatedByBotVerificationCode() {
        val settings = source("PuppySettingsUi.kt")
        val auth = source("DiscordSignupAuth.kt")
        val activity = source("PuppyClickerV6Activity.kt")
        val codeUi = source("PuppyDiscordCodeEntryUi.kt")

        assertTrue(settings.contains("Text(\"Unlink\")"))
        assertTrue(settings.contains("Text(\"Link\")"))
        assertTrue(settings.contains("Not linked"))
        assertTrue(settings.contains("one-time verification code"))
        assertFalse(settings.contains("No secondary DM code is required."))

        assertTrue(auth.contains("savePendingAccount(app, account)"))
        assertTrue(auth.contains("PuppyAuthBotClient.sendVerificationCode(app, account)"))
        assertTrue(auth.contains("phase = DiscordSignupPhase.CODE_PENDING"))
        assertTrue(auth.contains("snapshot.status != PuppyDiscordLinkStatus.VERIFIED"))
        assertTrue(auth.contains("PupAccountCloudSave.activate(app)"))

        assertTrue(activity.contains("PuppyDiscordCodeEntryDialog"))
        assertTrue(activity.contains("DiscordSignupAuth.completeVerifiedLink"))
        assertTrue(activity.contains("DiscordSignupAuth.cancelPendingVerification"))
        assertTrue(codeUi.contains("DiscordSignupAuth.resendPendingVerification"))

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
