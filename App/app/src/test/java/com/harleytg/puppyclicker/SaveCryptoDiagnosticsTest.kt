package com.harleytg.puppyclicker

import java.security.InvalidAlgorithmParameterException
import javax.crypto.AEADBadTagException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCryptoDiagnosticsTest {
    @Test
    fun authenticationAndContainerFailuresAreTamper() {
        assertEquals(SaveCryptoFailureKind.TAMPER, classifySaveCryptoFailure(AEADBadTagException("bad tag")))
        assertEquals(SaveCryptoFailureKind.TAMPER, classifySaveCryptoFailure(IllegalArgumentException("bad header")))
        assertEquals(SaveCryptoFailureKind.TAMPER, classifySaveCryptoFailure(JSONException("bad json")))
    }

    @Test
    fun providerParameterFailureIsOperational() {
        assertEquals(
            SaveCryptoFailureKind.OPERATIONAL,
            classifySaveCryptoFailure(InvalidAlgorithmParameterException("Caller-provided IV not permitted"))
        )
    }

    @Test
    fun identicalFailuresAreSuppressedUntilCooldownAndRecoveryResetsGate() {
        val gate = RepeatedFailureLogGate(cooldownMs = 60_000L)
        val failure = InvalidAlgorithmParameterException("Caller-provided IV not permitted")

        assertTrue(gate.shouldLog(failure, nowMs = 1_000L))
        assertFalse(gate.shouldLog(failure, nowMs = 10_000L))
        assertTrue(gate.shouldLog(failure, nowMs = 61_001L))
        assertTrue(gate.markSuccess())
        assertFalse(gate.markSuccess())
        assertTrue(gate.shouldLog(failure, nowMs = 62_000L))
    }

    @Test
    fun differentFailureSignatureLogsImmediately() {
        val gate = RepeatedFailureLogGate(cooldownMs = 60_000L)
        assertTrue(gate.shouldLog(InvalidAlgorithmParameterException("first"), 1_000L))
        assertTrue(gate.shouldLog(InvalidAlgorithmParameterException("second"), 2_000L))
    }
}
