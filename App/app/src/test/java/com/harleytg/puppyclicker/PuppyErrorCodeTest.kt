package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyErrorCodeTest {
    @Test
    fun puppyClickerErrorsUsePuppyNamespace() {
        PuppyClickerErrorCode.entries.forEach { error ->
            assertTrue(error.code.startsWith("PUPPY-"))
            assertEquals(PuppyErrorDomain.PUPPY_CLICKER, error.domain)
        }
    }

    @Test
    fun pupEyeErrorsUsePupEyeNamespace() {
        PupEyeErrorCode.entries.forEach { error ->
            assertTrue(error.code.startsWith("PUPEYE-"))
            assertEquals(PuppyErrorDomain.PUPEYE, error.domain)
        }
    }

    @Test
    fun allPublishedErrorCodesAreUnique() {
        val codes = PuppyClickerErrorCode.entries.map { it.code } +
            PupEyeErrorCode.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun deviceTransferUsesStablePupEyeCode() {
        assertEquals("PUPEYE-ID-401", PupEyeErrorCode.DEVICE_TRANSFER_REQUIRED.code)
    }

    @Test
    fun networkFailureUsesStablePuppyClickerCode() {
        assertEquals("PUPPY-NET-701", PuppyClickerErrorCode.NETWORK_UNAVAILABLE.code)
    }
}
