package com.harleytg.puppyclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyExchangeLiveTradeTest {
    @Test
    fun mirroredPeersAcceptTheSameTradeFingerprint() {
        val transactionId = "XT-abcdefghijklmnopqrstuvwx"
        val playerAOffer = listOf("v3_alpha")
        val playerBOffer = listOf("v3_beta")
        val payloadFromA = liveTradeFingerprintPayload(
            transactionId = transactionId,
            localTradeIds = playerAOffer,
            remoteTradeIds = playerBOffer
        )

        assertTrue(
            matchesLiveTradeFingerprint(
                payload = payloadFromA,
                transactionId = transactionId,
                localTradeIds = playerBOffer,
                remoteTradeIds = playerAOffer
            )
        )
    }

    @Test
    fun changedOfferInvalidatesReadyOrCommitFingerprint() {
        val transactionId = "XT-abcdefghijklmnopqrstuvwx"
        val payload = liveTradeFingerprintPayload(
            transactionId = transactionId,
            localTradeIds = listOf("v3_alpha"),
            remoteTradeIds = listOf("v3_beta")
        )

        assertFalse(
            matchesLiveTradeFingerprint(
                payload = payload,
                transactionId = transactionId,
                localTradeIds = listOf("v3_beta"),
                remoteTradeIds = listOf("v3_changed")
            )
        )
    }

    @Test
    fun differentTransactionIdIsRejected() {
        val payload = liveTradeFingerprintPayload(
            transactionId = "XT-abcdefghijklmnopqrstuvwx",
            localTradeIds = listOf("v3_alpha"),
            remoteTradeIds = listOf("v3_beta")
        )

        assertFalse(
            matchesLiveTradeFingerprint(
                payload = payload,
                transactionId = "XT-zyxwvutsrqponmlkjihgfedc",
                localTradeIds = listOf("v3_beta"),
                remoteTradeIds = listOf("v3_alpha")
            )
        )
    }
}
