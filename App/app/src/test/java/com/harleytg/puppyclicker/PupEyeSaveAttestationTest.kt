package com.harleytg.puppyclicker

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PupEyeSaveAttestationTest {
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
}
