package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
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
        assertTrue(url.contains("scope=identify"))
        assertTrue(url.contains("state=state-value"))
        assertTrue(url.contains("code_challenge=challenge-value"))
        assertTrue(url.contains("code_challenge_method=S256"))
    }

    @Test
    fun codeChallengeMatchesRfc7636Vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

        assertEquals(expected, DiscordSignupAuth.codeChallenge(verifier))
    }
}
