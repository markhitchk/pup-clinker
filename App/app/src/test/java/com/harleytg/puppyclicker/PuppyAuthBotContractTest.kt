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
    fun settingsUsesSingleStepDiscordOauthLinking() {
        val source = source("PuppySettingsUi.kt")
        assertTrue(source.contains("Text(\"Unlink\")"))
        assertTrue(source.contains("Text(\"Link\")"))
        assertTrue(source.contains("Not linked"))
        assertTrue(source.contains("No secondary DM code is required."))
        assertFalse(source.contains("Enter code"))
        assertFalse(source.contains("Text(\"Resend\")"))
        assertFalse(source.contains("Code sent"))
        assertTrue(source.contains("title = \"Advanced\""))
        assertTrue(source.contains("title = \"Account details\""))
        assertTrue(source.contains("PuppyIdentityHeaderCard"))
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
