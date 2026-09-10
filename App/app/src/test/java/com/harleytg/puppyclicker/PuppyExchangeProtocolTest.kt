package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyExchangeProtocolTest {
    private val playerA = "PC-0123456789ABCDEF0123456789ABCDEF"
    private val playerB = "PC-FEDCBA9876543210FEDCBA9876543210"
    private val friendA = "PUP-ABCD-EFGH-JKLM"
    private val friendB = "PUP-MNPQ-RSTU-VWXY"

    @Test
    fun offerRoundTripsThroughCompressedTextCode() {
        val now = 1_700_000_000_000L
        val envelope = ExchangeSignalEnvelope(
            type = ExchangeSignalType.OFFER,
            sessionId = "XS-abcdefghijklmnopqrstuvwx",
            nonce = "abcdefghijklmnopqrstuvwx",
            createdAtMs = now,
            expiresAtMs = now + PuppyExchangeProtocol.CODE_TTL_MS,
            senderPlayerId = playerA,
            senderFriendCode = friendA,
            expectedFriendCode = friendB,
            sdp = "v=0\r\na=test\r\n",
            iceCandidates = listOf(IceCandidateSnapshot("0", 0, "candidate:1"))
        )

        val code = PuppyExchangeProtocol.encodeSignal(envelope)
        val decoded = PuppyExchangeProtocol.decodeSignal(code, now + 1_000L)

        assertTrue(code.startsWith("PUP-O1-"))
        assertEquals(envelope, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun expiredConnectionCodeIsRejected() {
        val now = 1_700_000_000_000L
        val envelope = ExchangeSignalEnvelope(
            type = ExchangeSignalType.ANSWER,
            sessionId = "XS-abcdefghijklmnopqrstuvwx",
            nonce = "abcdefghijklmnopqrstuvwx",
            createdAtMs = now,
            expiresAtMs = now + 60_000L,
            senderPlayerId = playerB,
            senderFriendCode = friendB,
            expectedFriendCode = friendA,
            sdp = "v=0\r\n",
            iceCandidates = emptyList()
        )

        val code = PuppyExchangeProtocol.encodeSignal(envelope)
        PuppyExchangeProtocol.decodeSignal(code, now + 60_001L)
    }

    @Test
    fun recoveryRoundTripsAndReferencesExistingTransactionShape() {
        val now = 1_700_000_000_000L
        val emptyHash = PuppyExchangeProtocol.canonicalOfferHash(emptyList())
        val envelope = ExchangeRecoveryEnvelope(
            transactionId = "XT-abcdefghijklmnopqrstuvwx",
            nonce = "abcdefghijklmnopqrstuvwx",
            createdAtMs = now,
            expiresAtMs = now + PuppyExchangeProtocol.CODE_TTL_MS,
            senderPlayerId = playerA,
            peerPlayerId = playerB,
            offerAHash = emptyHash,
            offerBHash = emptyHash,
            senderState = ExchangeTransactionState.RECOVERY_REQUIRED,
            senderChoice = RecoveryChoice.CANCEL
        )

        val decoded = PuppyExchangeProtocol.decodeRecovery(
            PuppyExchangeProtocol.encodeRecovery(envelope),
            now + 1_000L
        )
        assertEquals(envelope, decoded)
    }

    @Test
    fun offerHashIsOrderIndependentAndStable() {
        val first = PuppyExchangeProtocol.canonicalOfferHash(listOf("b", "a", "a"))
        val second = PuppyExchangeProtocol.canonicalOfferHash(listOf("a", "b"))
        assertEquals(first, second)
        assertEquals(64, first.length)
    }
}
