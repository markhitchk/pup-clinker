package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyExchangeEngineTest {
    private val playerA = "PC-0123456789ABCDEF0123456789ABCDEF"
    private val playerB = "PC-FEDCBA9876543210FEDCBA9876543210"
    private val policy = PuppyTransferPolicy(giftable = true, tradeable = true)
    private val owned = PuppyOwnershipRecord(
        recordId = "XO-source-record",
        puppyId = "v3_frog_gift",
        ownerPlayerId = playerA,
        acquisitionType = AcquisitionType.SOURCE,
        sourceOwnerPlayerId = playerA,
        acquiredAtMs = 10L,
        policySnapshot = policy.copy(sourceCopy = true)
    )

    @Test
    fun declinedGiftChangesNoOwnership() {
        val result = resolveGiftCopy(
            senderRecord = owned,
            recipientPlayerId = playerB,
            accepted = false,
            transactionId = "XT-gift-decline",
            recipientRecordId = "XO-recipient",
            recipientPolicy = policy,
            nowMs = 20L
        )

        assertEquals(owned, result.senderRecord)
        assertNull(result.recipientRecord)
    }

    @Test
    fun acceptedGiftCreatesRecipientCopyAndKeepsSource() {
        val result = resolveGiftCopy(
            senderRecord = owned,
            recipientPlayerId = playerB,
            accepted = true,
            transactionId = "XT-gift-accept",
            recipientRecordId = "XO-recipient",
            recipientPolicy = policy,
            nowMs = 20L
        )

        assertEquals(OwnershipStatus.ACTIVE, result.senderRecord.status)
        assertEquals(OwnershipStatus.ACTIVE, result.recipientRecord!!.status)
        assertEquals(playerB, result.recipientRecord!!.ownerPlayerId)
        assertEquals(owned.recordId, result.recipientRecord!!.previousRecordId)
        assertFalse(result.recipientRecord!!.policySnapshot.sourceCopy)
        assertTrue(result.recipientRecord!!.policySnapshot.tradeable)
    }

    @Test
    fun oneForOneTradeBecomesReadyOnlyAfterBothPlayersReady() {
        var draft = newTradeDraft(playerA, playerB, listOf("A1"), listOf("B1"))
        draft = setTradeReady(draft, TradeSide.A, true)
        assertEquals(ExchangeTransactionState.PENDING, draft.state)
        draft = setTradeReady(draft, TradeSide.B, true)
        assertEquals(ExchangeTransactionState.READY, draft.state)
    }

    @Test
    fun fiveItemBundleIsAllowedButSixthItemIsRejected() {
        val five = listOf("1", "2", "3", "4", "5")
        val draft = newTradeDraft(playerA, playerB, five, five)
        assertEquals(5, draft.offerARecordIds.size)

        var failed = false
        try {
            editTradeOffer(draft, TradeSide.A, five + "6")
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun editingEitherOfferResetsBothReadyFlags() {
        var draft = newTradeDraft(playerA, playerB, listOf("A1"), listOf("B1"))
        draft = setTradeReady(setTradeReady(draft, TradeSide.A, true), TradeSide.B, true)
        assertTrue(draft.playerAReady && draft.playerBReady)

        draft = editTradeOffer(draft, TradeSide.A, listOf("A1", "A2"))
        assertFalse(draft.playerAReady)
        assertFalse(draft.playerBReady)
        assertEquals(ExchangeTransactionState.PENDING, draft.state)
    }

    @Test
    fun commitHashMismatchNeverEntersCommitting() {
        var draft = newTradeDraft(playerA, playerB, listOf("A1"), listOf("B1"))
        draft = setTradeReady(setTradeReady(draft, TradeSide.A, true), TradeSide.B, true)
        val check = checkTradeCommit(draft, remoteOfferAHash = "0".repeat(64), remoteOfferBHash = draft.offerBHash)

        assertFalse(check.allowed)
        assertEquals(ExchangeTransactionState.READY, check.state)
    }

    @Test
    fun disconnectInsideCommitRequiresRecovery() {
        val state = handleTradeDisconnect(ExchangeTransactionState.COMMITTING)
        assertEquals(ExchangeTransactionState.RECOVERY_REQUIRED, state)
    }

    @Test
    fun fabricatedRecoveryTransactionCannotStart() {
        val existing = listOf(
            ExchangeTransactionRecord(
                transactionId = "XT-real-transaction",
                type = ExchangeTransactionType.TRADE,
                playerAId = playerA,
                playerBId = playerB,
                offerARecordIds = listOf("A1"),
                offerBRecordIds = listOf("B1"),
                offerAHash = PuppyExchangeProtocol.canonicalOfferHash(listOf("A1")),
                offerBHash = PuppyExchangeProtocol.canonicalOfferHash(listOf("B1")),
                createdAtMs = 1L,
                state = ExchangeTransactionState.RECOVERY_REQUIRED
            )
        )

        assertFalse(canStartRecovery("XT-made-up", existing))
        assertTrue(canStartRecovery("XT-real-transaction", existing))
    }

    @Test
    fun conflictingRecoveryChoicesRemainUnresolved() {
        assertNull(resolveRecoveryChoice(RecoveryChoice.COMPLETE, RecoveryChoice.CANCEL))
        assertEquals(RecoveryChoice.COMPLETE, resolveRecoveryChoice(RecoveryChoice.COMPLETE, RecoveryChoice.COMPLETE))
    }
}
