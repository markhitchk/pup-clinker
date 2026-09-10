package com.harleytg.puppyclicker

internal data class GiftCopyResolution(
    val senderRecord: PuppyOwnershipRecord,
    val recipientRecord: PuppyOwnershipRecord?
)

/**
 * Resolve a copy-style gift without mutating either save. The sender keeps the existing
 * ownership record; acceptance creates one provenance-linked recipient record.
 */
internal fun resolveGiftCopy(
    senderRecord: PuppyOwnershipRecord,
    recipientPlayerId: String,
    accepted: Boolean,
    transactionId: String,
    recipientRecordId: String,
    recipientPolicy: PuppyTransferPolicy,
    nowMs: Long
): GiftCopyResolution {
    if (!accepted) return GiftCopyResolution(senderRecord, null)

    require(PuppyPlayerIdentity.isValidPlayerId(recipientPlayerId)) { "Invalid gift recipient Player ID" }
    require(senderRecord.ownerPlayerId != recipientPlayerId) { "A puppy cannot be gifted to the same Player ID" }
    require(senderRecord.status == OwnershipStatus.ACTIVE) { "Only active ownership can create a gift" }

    val eligibility = evaluateTransferEligibility(
        record = senderRecord,
        currentPolicy = recipientPolicy,
        type = ExchangeTransactionType.GIFT,
        localPlayerId = senderRecord.ownerPlayerId
    )
    require(eligibility.allowed) { eligibility.reason ?: "This puppy cannot be gifted" }

    val recipient = PuppyOwnershipRecord(
        recordId = recipientRecordId,
        puppyId = senderRecord.puppyId,
        ownerPlayerId = recipientPlayerId,
        acquisitionType = AcquisitionType.GIFT,
        sourceOwnerPlayerId = senderRecord.sourceOwnerPlayerId ?: senderRecord.ownerPlayerId,
        previousRecordId = senderRecord.recordId,
        transactionId = transactionId,
        acquiredAtMs = nowMs,
        status = OwnershipStatus.ACTIVE,
        policySnapshot = recipientPolicy.copy(sourceCopy = false)
    )
    return GiftCopyResolution(senderRecord, recipient)
}

internal enum class TradeSide {
    A,
    B
}

internal data class TradeDraft(
    val transactionId: String,
    val playerAId: String,
    val playerBId: String,
    val offerARecordIds: List<String>,
    val offerBRecordIds: List<String>,
    val offerAHash: String,
    val offerBHash: String,
    val playerAReady: Boolean = false,
    val playerBReady: Boolean = false,
    val state: ExchangeTransactionState = ExchangeTransactionState.PENDING
)

internal fun newTradeDraft(
    playerAId: String,
    playerBId: String,
    offerARecordIds: List<String>,
    offerBRecordIds: List<String>,
    transactionId: String = PuppyExchangeProtocol.newTransactionId()
): TradeDraft {
    require(PuppyPlayerIdentity.isValidPlayerId(playerAId)) { "Invalid Player A ID" }
    require(PuppyPlayerIdentity.isValidPlayerId(playerBId)) { "Invalid Player B ID" }
    require(playerAId != playerBId) { "Trade players must be different" }
    val offerA = validateTradeBundle(offerARecordIds).toList()
    val offerB = validateTradeBundle(offerBRecordIds).toList()
    return TradeDraft(
        transactionId = transactionId,
        playerAId = playerAId,
        playerBId = playerBId,
        offerARecordIds = offerA,
        offerBRecordIds = offerB,
        offerAHash = PuppyExchangeProtocol.canonicalOfferHash(offerA),
        offerBHash = PuppyExchangeProtocol.canonicalOfferHash(offerB)
    )
}

internal fun editTradeOffer(
    draft: TradeDraft,
    side: TradeSide,
    recordIds: List<String>
): TradeDraft {
    require(draft.state == ExchangeTransactionState.PENDING || draft.state == ExchangeTransactionState.READY) {
        "Trade offer cannot be edited after commit begins"
    }
    val offer = validateTradeBundle(recordIds).toList()
    return when (side) {
        TradeSide.A -> draft.copy(
            offerARecordIds = offer,
            offerAHash = PuppyExchangeProtocol.canonicalOfferHash(offer),
            playerAReady = false,
            playerBReady = false,
            state = ExchangeTransactionState.PENDING
        )
        TradeSide.B -> draft.copy(
            offerBRecordIds = offer,
            offerBHash = PuppyExchangeProtocol.canonicalOfferHash(offer),
            playerAReady = false,
            playerBReady = false,
            state = ExchangeTransactionState.PENDING
        )
    }
}

internal fun setTradeReady(
    draft: TradeDraft,
    side: TradeSide,
    ready: Boolean
): TradeDraft {
    require(draft.state == ExchangeTransactionState.PENDING || draft.state == ExchangeTransactionState.READY) {
        "Trade readiness cannot change after commit begins"
    }
    val next = when (side) {
        TradeSide.A -> draft.copy(playerAReady = ready)
        TradeSide.B -> draft.copy(playerBReady = ready)
    }
    return next.copy(
        state = if (next.playerAReady && next.playerBReady) {
            ExchangeTransactionState.READY
        } else {
            ExchangeTransactionState.PENDING
        }
    )
}

internal data class TradeCommitCheck(
    val allowed: Boolean,
    val state: ExchangeTransactionState,
    val reason: String? = null
)

internal fun checkTradeCommit(
    draft: TradeDraft,
    remoteOfferAHash: String,
    remoteOfferBHash: String
): TradeCommitCheck {
    if (draft.state != ExchangeTransactionState.READY || !draft.playerAReady || !draft.playerBReady) {
        return TradeCommitCheck(false, draft.state, "Both players must be ready before commit.")
    }
    if (remoteOfferAHash != draft.offerAHash || remoteOfferBHash != draft.offerBHash) {
        return TradeCommitCheck(false, ExchangeTransactionState.READY, "Trade offer hashes do not match.")
    }
    return TradeCommitCheck(true, ExchangeTransactionState.COMMITTING)
}

internal fun handleTradeDisconnect(state: ExchangeTransactionState): ExchangeTransactionState =
    nextTradeState(current = state, disconnected = true)

internal fun canStartRecovery(
    transactionId: String,
    transactions: Collection<ExchangeTransactionRecord>
): Boolean = transactions.any {
    it.transactionId == transactionId && it.state == ExchangeTransactionState.RECOVERY_REQUIRED
}

internal fun resolveRecoveryChoice(
    local: RecoveryChoice,
    remote: RecoveryChoice
): RecoveryChoice? = local.takeIf { it == remote }
