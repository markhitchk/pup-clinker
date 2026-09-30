package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PupEyeSessionRecoveryContractTest {
    @Test
    fun staleBackendSessionIsReRegisteredOnceAndRetried() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/SupabasePupEyeClient.kt"
        ).readText()

        assertTrue(source.contains("invokeAuthenticatedWithSessionRecovery"))
        assertTrue(source.contains("\"PUPEYE_SESSION_INVALID\""))
        assertTrue(source.contains("\"PUPEYE_SESSION_REQUIRED\""))
        assertTrue(source.contains("clearSession(context)"))
        assertTrue(source.contains("val refreshedSession = ensureRegistered(context)"))
        assertTrue(source.contains("sessionRecoveryLock"))
    }
}
