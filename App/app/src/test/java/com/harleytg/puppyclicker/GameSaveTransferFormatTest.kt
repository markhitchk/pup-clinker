package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class GameSaveTransferFormatTest {
    @Test
    fun encryptedTransferRequiresPassword() {
        val bytes = """{"format":"puppy-clicker-encrypted-save","version":3}"""
            .toByteArray(Charsets.UTF_8)

        assertEquals(
            PuppySavePasswordRequirement.REQUIRED,
            GameSaveTransfer.passwordRequirementFromBytes(bytes)
        )
    }

    @Test
    fun legacyTransferDoesNotRequirePassword() {
        val bytes = """{"format":"puppy-clicker-save","version":2}"""
            .toByteArray(Charsets.UTF_8)

        assertEquals(
            PuppySavePasswordRequirement.NOT_REQUIRED,
            GameSaveTransfer.passwordRequirementFromBytes(bytes)
        )
    }

    @Test
    fun unknownOrDamagedTransferIsUnknown() {
        assertEquals(
            PuppySavePasswordRequirement.UNKNOWN,
            GameSaveTransfer.passwordRequirementFromBytes("not-json".toByteArray())
        )
        assertEquals(
            PuppySavePasswordRequirement.UNKNOWN,
            GameSaveTransfer.passwordRequirementFromBytes(
                """{"format":"something-else"}""".toByteArray()
            )
        )
    }
}
