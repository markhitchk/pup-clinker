package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyExchangeIdentityTest {
    @Test
    fun generatedPlayerIdsUsePermanentIdentityFormat() {
        val first = PuppyPlayerIdentity.generatePlayerId()
        val second = PuppyPlayerIdentity.generatePlayerId()

        assertTrue(PuppyPlayerIdentity.isValidPlayerId(first))
        assertTrue(PuppyPlayerIdentity.isValidPlayerId(second))
        assertNotEquals(first, second)
    }

    @Test
    fun generatedFriendCodesUseShareableFormat() {
        val code = PuppyPlayerIdentity.generateFriendCode()

        assertTrue(PuppyPlayerIdentity.isValidFriendCode(code))
        assertEquals(3, code.removePrefix("PUP-").split('-').size)
    }

    @Test
    fun validatorsRejectMalformedIdentityValues() {
        assertFalse(PuppyPlayerIdentity.isValidPlayerId("pc-0123456789ABCDEF0123456789ABCDEF"))
        assertFalse(PuppyPlayerIdentity.isValidPlayerId("PC-1234"))
        assertFalse(PuppyPlayerIdentity.isValidFriendCode("pup-ABCD-EFGH-JKLM"))
        assertFalse(PuppyPlayerIdentity.isValidFriendCode("PUP-ABCI-EFGH-JKLM"))
        assertFalse(PuppyPlayerIdentity.isValidFriendCode("PUP-ABCD-EFGH"))
    }

    @Test
    fun usernameNormalizationRemainsIndependentOfExchangeIdentity() {
        assertEquals("harleytg", PuppyPlayerIdentity.normalizeUsername(" HarleyTG "))
    }
    @Test
    fun reservedHarleyTgPublicAliasesResolveToCanonicalIdentity() {
        assertEquals(
            "PC-HARLEYTG-DEV-0001",
            PuppyPlayerIdentity.displayPlayerId("PC-5AD7F57F80FBE69809F96AEA963E2426")
        )
        assertEquals(
            "PUP-HTG-DEV-0001",
            PuppyPlayerIdentity.displayFriendCode("PUP-5XUV-SGZB-S9CX")
        )
        assertEquals(
            "PUP-5XUV-SGZB-S9CX",
            PuppyPlayerIdentity.resolveFriendCodeInput("PUP-HTG-DEV-0001")
        )
        assertTrue(PuppyPlayerIdentity.isValidFriendCodeInput("PUP-HTG-DEV-0001"))
        assertTrue(PuppyPlayerIdentity.isValidPlayerId("PC-5AD7F57F80FBE69809F96AEA963E2426"))
        assertTrue(PuppyPlayerIdentity.isValidFriendCode("PUP-5XUV-SGZB-S9CX"))
        assertFalse(PuppyPlayerIdentity.isValidPlayerId("PC-HARLEYTG-DEV-0001"))
    }

}
