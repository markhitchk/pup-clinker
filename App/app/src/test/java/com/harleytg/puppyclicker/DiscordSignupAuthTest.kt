package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordSignupAuthTest {
    @Test
    fun authorizationUrlUsesDiscordMobilePkceParameters() {
        val url = DiscordSignupAuth.buildAuthorizationUrl(
            oauthState = "state-value",
            codeChallenge = "challenge-value"
        )

        assertTrue(url.startsWith("https://discord.com/oauth2/authorize?"))
        assertTrue(url.contains("client_id=1547982688898777118"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("redirect_uri=discord-1547982688898777118%3A%2Fauthorize%2Fcallback"))
        assertTrue(url.contains("scope=identify+email+guilds+guilds.join+guilds.members.read"))
        assertTrue(url.contains("state=state-value"))
        assertTrue(url.contains("code_challenge=challenge-value"))
        assertTrue(url.contains("code_challenge_method=S256"))
    }

    @Test
    fun discordGuildRolePriorityUsesConfiguredIds() {
        assertEquals(
            DiscordGuildRole.DEVELOPER,
            DiscordSignupAuth.classifyGuildRole(
                setOf(DiscordSignupAuth.ROLE_GUEST_ID, DiscordSignupAuth.ROLE_DEVELOPER_ID)
            )
        )
        assertEquals(
            DiscordGuildRole.ADMIN,
            DiscordSignupAuth.classifyGuildRole(setOf(DiscordSignupAuth.ROLE_ADMIN_ID))
        )
        assertEquals(
            DiscordGuildRole.PUP_MEMBER,
            DiscordSignupAuth.classifyGuildRole(setOf(DiscordSignupAuth.ROLE_PUP_MEMBERS_ID))
        )
        assertEquals(
            DiscordGuildRole.GUEST,
            DiscordSignupAuth.classifyGuildRole(setOf(DiscordSignupAuth.ROLE_GUEST_ID))
        )
        assertNull(DiscordSignupAuth.classifyGuildRole(setOf("123")))
    }

    @Test
    fun discordRolesMapToConfiguredV2Puppies() {
        assertEquals("v2_dev_pup", DiscordSignupAuth.unlockPuppyIdFor(DiscordGuildRole.DEVELOPER))
        assertNull(DiscordSignupAuth.unlockPuppyIdFor(DiscordGuildRole.ADMIN))
        assertEquals("v2_discord_pup", DiscordSignupAuth.unlockPuppyIdFor(DiscordGuildRole.PUP_MEMBER))
        assertNull(DiscordSignupAuth.unlockPuppyIdFor(DiscordGuildRole.GUEST))

        val developerRewards = DiscordSignupAuth.unlockPuppyIdsFor(DiscordGuildRole.DEVELOPER)
        assertTrue("v2_dev_pup" in developerRewards)
        assertTrue("v2_discord_pup" in developerRewards)
        assertEquals(
            setOf("v2_discord_pup"),
            DiscordSignupAuth.unlockPuppyIdsFor(DiscordGuildRole.PUP_MEMBER)
        )
    }

    @Test
    fun legacyDiscordAccountGetsOneMigrationNoticeUntilServerRoleIsVerified() {
        val account = DiscordPlayerAccount(
            id = "1547298673006747678",
            username = "puppy-member",
            globalName = null,
            avatarHash = null,
            email = null
        )
        assertTrue(DiscordSignupAuth.shouldNotifyLegacyAuthUpgrade(account, null, false))
        assertTrue(!DiscordSignupAuth.shouldNotifyLegacyAuthUpgrade(account, null, true))
        assertTrue(
            !DiscordSignupAuth.shouldNotifyLegacyAuthUpgrade(
                account,
                DiscordGuildAccess(
                    guildId = DiscordSignupAuth.GUILD_ID,
                    role = DiscordGuildRole.PUP_MEMBER,
                    verifiedAtMs = 1L
                ),
                false
            )
        )
        assertTrue(!DiscordSignupAuth.shouldNotifyLegacyAuthUpgrade(null, null, false))
    }

    @Test
    fun discordSnowflakeValidationRequiresDigitsAndExpectedLength() {
        assertTrue(DiscordSignupAuth.isDiscordSnowflake("1547298673006747678"))
        assertTrue(!DiscordSignupAuth.isDiscordSnowflake("discord-user"))
        assertTrue(!DiscordSignupAuth.isDiscordSnowflake("1234"))
    }

    @Test
    fun codeChallengeMatchesRfc7636Vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

        assertEquals(expected, DiscordSignupAuth.codeChallenge(verifier))
    }
}
