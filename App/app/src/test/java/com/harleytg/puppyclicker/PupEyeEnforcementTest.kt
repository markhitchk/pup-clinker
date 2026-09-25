package com.harleytg.puppyclicker

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PupEyeEnforcementTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/$name").readText()

    @Test
    fun olderAllowedResponseCannotClearNewerBan() {
        val gate = PupEyeEnforcementResponseGate()
        val olderRequest = gate.capture()
        gate.markRestrictive()

        assertFalse(gate.canAcceptAllowed(olderRequest))
        assertTrue(gate.canAcceptAllowed(gate.capture()))
    }

    @Test
    fun ordinarySuccessfulRequestsDoNotClearCachedBan() {
        val client = source("SupabasePupEyeClient.kt")
        val connectedMethod = client.substringAfter("private fun markConnected(context: Context) {")
            .substringBefore("private fun markBackendConnectedOnly")
        assertFalse(connectedMethod.contains("saveEnforcement("))
    }

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
    @Test
    fun encryptedCacheRoundTripPreservesPermanentBan() {
        val original = PupEyeEnforcementSnapshot(
            mode = PupEyeEnforcementMode.GLOBAL_BANNED,
            ban = PupEyeGlobalBanInfo(
                id = "PGB-PERM-0001",
                kind = "permanent",
                reasonCode = "BAN_EVASION",
                publicReason = "Permanent enforcement.",
                issuedAtEpochMs = 100L,
                expiresAtEpochMs = null,
                deviceWide = true
            ),
            lastServerVerifiedAtMs = 200L
        )

        assertEquals(original, PupEyeEnforcementSnapshot.fromJson(original.toJson()))
    }

    @Test
    fun clientPersistsEnforcementInEncryptedNoBackupStorage() {
        val source = source("SupabasePupEyeClient.kt")

        assertTrue(source.contains("pupeye/enforcement_v1.pup"))
        assertTrue(source.contains("PuppySaveCrypto.encryptDevice"))
        assertTrue(source.contains("PuppySaveCrypto.decryptDevice"))
        assertTrue(source.contains("noBackupFilesDir"))
        assertTrue(source.contains("StateFlow<PupEyeEnforcementSnapshot>"))
    }

    @Test
    fun clientRefreshesSignedServerEnforcementWithoutSession() {
        val source = source("SupabasePupEyeClient.kt")

        assertTrue(source.contains("fun refreshEnforcementAsync(context: Context)"))
        assertTrue(source.contains("action = \"enforcement-status\""))
        assertTrue(source.contains("functionName = \"pupeye-enforcement-status\""))
        assertTrue(source.contains("sessionToken = null"))
        assertTrue(source.contains("\"GLOBAL_BANNED\""))
        assertTrue(source.contains("\"REVIEW_REQUIRED\""))
    }

    @Test
    fun protectedGameplayAlsoHonorsCachedEnforcement() {
        val source = source("SupabasePupEyeClient.kt")

        assertTrue(source.contains("enforcementSnapshot(context).blocksApp"))
    }
}
