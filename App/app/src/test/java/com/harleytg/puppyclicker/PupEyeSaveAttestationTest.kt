package com.harleytg.puppyclicker

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PupEyeSaveAttestationTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/$name").readText()

    @Test
    fun canonicalTransferHashIgnoresObjectInsertionOrder() {
        val a = JSONObject()
            .put("format", "puppy-clicker-transfer-payload")
            .put("version", 3)
            .put("stores", JSONObject().put("b", 2).put("a", 1))

        val b = JSONObject()
            .put("stores", JSONObject().put("a", 1).put("b", 2))
            .put("version", 3)
            .put("format", "puppy-clicker-transfer-payload")

        assertEquals(
            PupEyeAuthority.transferPayloadHash(a),
            PupEyeAuthority.transferPayloadHash(b)
        )
    }
    @Test
    fun exportAttestationIsQueuedOnlyAfterEncryptedFileWriteSucceeds() {
        val source = source("GameSaveTransfer.kt")
        val write = source.indexOf("output.use { it.write(encrypted) }")
        val queue = source.indexOf("SupabasePupEyeClient.queueSaveAttestation", write)

        assertTrue(source.contains("val unsignedPayload = JSONObject().apply"))
        assertTrue(source.contains("PupEyeAuthority.transferPayloadHash(unsignedPayload)"))
        assertTrue(source.contains("PupEyeAuthority.createTransferProof(context, unsignedPayload)"))
        assertTrue(write >= 0)
        assertTrue(queue > write)
    }

    @Test
    fun importAttestationIsQueuedOnlyAfterRestoredSaveIsSealedAndWritten() {
        val source = source("GameSaveTransfer.kt")
        val write = source.indexOf("ExternalGameSave.write(context, mainPrefs)")
        val lastQueue = source.lastIndexOf("SupabasePupEyeClient.queueSaveAttestation")

        assertTrue(source.contains("JSONObject(payload.toString()).apply { remove(\"pupeye\") }"))
        assertTrue(source.contains("importedAttestation"))
        assertTrue(write >= 0)
        assertTrue(lastQueue > write)
    }

    @Test
    fun supabaseClientSendsSignedSaveAttestation() {
        val source = source("SupabasePupEyeClient.kt")

        assertTrue(source.contains("fun queueSaveAttestation("))
        assertTrue(source.contains("action = \"save-attestation\""))
        assertTrue(source.contains("functionName = \"pupeye-save-attestation\""))
        assertTrue(source.contains("payloadHashSha256.matches(Regex(\"[0-9a-f]{64}\"))"))
        assertTrue(source.contains("handleAuthoritativeFailure(app, response)"))
    }
}
