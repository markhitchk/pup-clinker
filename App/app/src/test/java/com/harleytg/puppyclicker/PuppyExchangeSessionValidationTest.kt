package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyExchangeSessionValidationTest {
    private val remote = ExchangeIdentityHello(
        playerId = "PC-0123456789ABCDEF0123456789ABCDEF",
        friendCode = "PUP-ABCD-EFGH-JKLM",
        username = "friend",
        protocolVersion = PuppyExchangeProtocol.PROTOCOL_VERSION,
        appVersionCode = 22
    )

    @Test
    fun matchingExpectedFriendCodeIsAccepted() {
        val result = validateExchangePeer(
            hello = remote,
            expectedFriendCode = remote.friendCode,
            blockedPlayerIds = emptySet()
        )

        assertTrue(result.accepted)
        assertEquals(null, result.reason)
    }

    @Test
    fun friendCodeMismatchIsRejected() {
        val result = validateExchangePeer(
            hello = remote,
            expectedFriendCode = "PUP-NPQR-STUV-WXYZ",
            blockedPlayerIds = emptySet()
        )

        assertFalse(result.accepted)
        assertEquals(ExchangePeerValidationReason.FRIEND_CODE_MISMATCH, result.reason)
    }

    @Test
    fun blockedPlayerIsRejected() {
        val result = validateExchangePeer(
            hello = remote,
            expectedFriendCode = remote.friendCode,
            blockedPlayerIds = setOf(remote.playerId)
        )

        assertFalse(result.accepted)
        assertEquals(ExchangePeerValidationReason.BLOCKED_PLAYER, result.reason)
    }

    @Test
    fun wrongProtocolIsRejected() {
        val result = validateExchangePeer(
            hello = remote.copy(protocolVersion = 99),
            expectedFriendCode = remote.friendCode,
            blockedPlayerIds = emptySet()
        )

        assertFalse(result.accepted)
        assertEquals(ExchangePeerValidationReason.PROTOCOL_MISMATCH, result.reason)
    }
}
