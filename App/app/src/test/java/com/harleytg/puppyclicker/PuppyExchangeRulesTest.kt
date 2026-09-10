package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyExchangeRulesTest {
    private val owner = "PC-0123456789ABCDEF0123456789ABCDEF"
    private val peer = "PC-FEDCBA9876543210FEDCBA9876543210"
    private val transferable = PuppyTransferPolicy(giftable = true, tradeable = true)

    private fun record(
        policy: PuppyTransferPolicy = transferable,
        status: OwnershipStatus = OwnershipStatus.ACTIVE
    ) = PuppyOwnershipRecord(
        recordId = "XO-test",
        puppyId = "v3_test",
        ownerPlayerId = owner,
        acquisitionType = AcquisitionType.CLAIM,
        acquiredAtMs = 1L,
        status = status,
        policySnapshot = policy
    )

    @Test
    fun giftAndTradeAreIndependentlyEnforced() {
        val giftOnly = PuppyTransferPolicy(giftable = true)
        val owned = record(policy = giftOnly)

        assertTrue(evaluateTransferEligibility(owned, giftOnly, ExchangeTransactionType.GIFT, owner).allowed)
        assertFalse(evaluateTransferEligibility(owned, giftOnly, ExchangeTransactionType.TRADE, owner).allowed)
    }

    @Test
    fun sourceCopyCanGiftButCannotTradeEvenWhenRecipientsMayTrade() {
        val rosterPolicy = PuppyTransferPolicy(giftable = true, tradeable = true)
        val sourceOwnershipPolicy = rosterPolicy.copy(sourceCopy = true)
        val sourceRecord = record(policy = sourceOwnershipPolicy)

        assertTrue(evaluateTransferEligibility(sourceRecord, rosterPolicy, ExchangeTransactionType.GIFT, owner).allowed)
        assertFalse(evaluateTransferEligibility(sourceRecord, rosterPolicy, ExchangeTransactionType.TRADE, owner).allowed)

        val recipientRecord = sourceRecord.copy(
            recordId = "XO-recipient",
            ownerPlayerId = peer,
            acquisitionType = AcquisitionType.GIFT,
            policySnapshot = rosterPolicy.copy(sourceCopy = false)
        )
        assertTrue(evaluateTransferEligibility(recipientRecord, rosterPolicy, ExchangeTransactionType.TRADE, peer).allowed)
    }

    @Test
    fun transactionLockedPuppyCannotTransferAgain() {
        val eligibility = evaluateTransferEligibility(
            record(status = OwnershipStatus.LOCKED),
            transferable,
            ExchangeTransactionType.TRADE,
            owner
        )
        assertFalse(eligibility.allowed)
        assertTrue(eligibility.reason!!.contains("unfinished trade"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sixItemTradeBundleIsRejected() {
        validateTradeBundle(listOf("1", "2", "3", "4", "5", "6"))
    }

    @Test
    fun disconnectDuringCommitRequiresRecovery() {
        assertEquals(
            ExchangeTransactionState.RECOVERY_REQUIRED,
            nextTradeState(ExchangeTransactionState.COMMITTING, disconnected = true)
        )
        assertEquals(
            ExchangeTransactionState.CANCELLED,
            nextTradeState(ExchangeTransactionState.READY, disconnected = true)
        )
    }

    @Test
    fun recoveryRequiresMatchingJournalAndChoice() {
        val hash = PuppyExchangeProtocol.canonicalOfferHash(listOf("XO-test"))
        val transaction = ExchangeTransactionRecord(
            transactionId = "XT-abcdefghijklmnopqrstuvwx",
            type = ExchangeTransactionType.TRADE,
            playerAId = owner,
            playerBId = peer,
            offerARecordIds = listOf("XO-test"),
            offerBRecordIds = emptyList(),
            offerAHash = hash,
            offerBHash = PuppyExchangeProtocol.canonicalOfferHash(emptyList()),
            createdAtMs = 1L,
            state = ExchangeTransactionState.RECOVERY_REQUIRED
        )
        val recovery = ExchangeRecoveryEnvelope(
            transactionId = transaction.transactionId,
            nonce = "abcdefghijklmnopqrstuvwx",
            createdAtMs = 1L,
            expiresAtMs = 2L,
            senderPlayerId = peer,
            peerPlayerId = owner,
            offerAHash = transaction.offerAHash,
            offerBHash = transaction.offerBHash,
            senderState = ExchangeTransactionState.RECOVERY_REQUIRED,
            senderChoice = RecoveryChoice.COMPLETE
        )

        assertTrue(recoveryCanResolve(transaction, recovery, RecoveryChoice.COMPLETE))
        assertFalse(recoveryCanResolve(transaction, recovery, RecoveryChoice.CANCEL))
    }
}
