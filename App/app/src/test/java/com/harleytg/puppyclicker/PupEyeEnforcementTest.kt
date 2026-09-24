package com.harleytg.puppyclicker

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PupEyeEnforcementTest {
    @Test
    fun knownTemporaryBanRemainsLockedAfterLocalClockPassesExpiry() {
        val cached = PupEyeEnforcementSnapshot(
            mode = PupEyeEnforcementMode.GLOBAL_BANNED,
            ban = PupEyeGlobalBanInfo(
                id = "PGB-TEST",
                kind = "temporary",
                reasonCode = "SAVE_TAMPERING",
                publicReason = "Temporary enforcement.",
                issuedAtEpochMs = 1_000L,
                expiresAtEpochMs = 2_000L,
                deviceWide = true
            ),
            lastServerVerifiedAtMs = 1_500L
        )

        assertTrue(cached.blocksApp(nowMs = 9_999_999L))
    }

    @Test
    fun cleanOfflineStateDoesNotInventABan() {
        val cached = PupEyeEnforcementSnapshot.allowed(lastServerVerifiedAtMs = 1_500L)

        assertFalse(cached.blocksApp(nowMs = 9_999_999L))
    }

    @Test
    fun reviewStateBlocksApp() {
        val cached = PupEyeEnforcementSnapshot(
            mode = PupEyeEnforcementMode.REVIEW_REQUIRED,
            reviewMessage = "Support review required.",
            lastServerVerifiedAtMs = 1_500L
        )

        assertTrue(cached.blocksApp(nowMs = 1_500L))
    }

    @Test
    fun globalBanResponseParsesPublicBanEnvelope() {
        val body = JSONObject(
            """
            {
              "code":"GLOBAL_BANNED",
              "ban":{
                "id":"PGB-7F2A-91C4",
                "kind":"temporary",
                "reasonCode":"BAN_EVASION",
                "publicReason":"Temporary enforcement.",
                "issuedAt":"2026-09-24T20:52:00Z",
                "expiresAt":"2026-09-27T20:52:00Z",
                "scope":"GLOBAL",
                "deviceWide":true
              }
            }
            """.trimIndent()
        )

        val parsed = PupEyeEnforcementSnapshot.fromServerResponse(
            body = body,
            verifiedAtMs = 1234L
        )

        assertEquals(PupEyeEnforcementMode.GLOBAL_BANNED, parsed.mode)
        assertNotNull(parsed.ban)
        assertEquals("PGB-7F2A-91C4", parsed.ban?.id)
        assertEquals("temporary", parsed.ban?.kind)
        assertEquals("BAN_EVASION", parsed.ban?.reasonCode)
        assertEquals(true, parsed.ban?.deviceWide)
        assertEquals(1234L, parsed.lastServerVerifiedAtMs)
    }

    @Test
    fun reviewAndAllowedResponsesParseWithoutFabricatingBanDetails() {
        val review = PupEyeEnforcementSnapshot.fromServerResponse(
            JSONObject()
                .put("code", "REVIEW_REQUIRED")
                .put("message", "PupEye requires Support review."),
            verifiedAtMs = 20L
        )
        val allowed = PupEyeEnforcementSnapshot.fromServerResponse(
            JSONObject()
                .put("state", "ALLOWED")
                .put("serverTimeEpochMs", 30L),
            verifiedAtMs = 30L
        )

        assertEquals(PupEyeEnforcementMode.REVIEW_REQUIRED, review.mode)
        assertEquals("PupEye requires Support review.", review.reviewMessage)
        assertEquals(null, review.ban)
        assertEquals(PupEyeEnforcementMode.ALLOWED, allowed.mode)
        assertEquals(null, allowed.ban)
    }
}
