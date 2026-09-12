package com.harleytg.puppyclicker

import android.content.Context

/**
 * Pure Puppy Exchange models, rules, recovery, and transaction engine.
 *
 * Consolidated as part of the six-system Kotlin architecture.
 */

// ---- PuppyExchangeModels ----
/** Transfer capabilities supplied by the streamed puppy manifest. */
data class PuppyTransferPolicy(
    val giftable: Boolean = false,
    val tradeable: Boolean = false,
    val bound: Boolean = false,
    val sourceCopy: Boolean = false,
    val limited: Boolean = false
) {
    init {
        require(!(bound && (giftable || tradeable))) {
            "Bound puppies cannot be giftable or tradeable"
        }
    }
}

enum class OwnershipStatus {
    ACTIVE,
    TRANSFERRED,
    LOCKED
}

enum class AcquisitionType {
    CLAIM,
    GIFT,
    TRADE,
    SOURCE
}

data class PuppyOwnershipRecord(
    val recordId: String,
    val puppyId: String,
    val ownerPlayerId: String,
    val acquisitionType: AcquisitionType,
    val sourceOwnerPlayerId: String? = null,
    val previousRecordId: String? = null,
    val transactionId: String? = null,
    val acquiredAtMs: Long,
    val status: OwnershipStatus = OwnershipStatus.ACTIVE,
    val transferredToPlayerId: String? = null,
    val transferredAtMs: Long? = null,
    val policySnapshot: PuppyTransferPolicy = PuppyTransferPolicy()
)

enum class ExchangeTransactionState {
    PENDING,
    READY,
    COMMITTING,
    COMPLETED,
    RECOVERY_REQUIRED,
    CANCELLED
}

enum class ExchangeTransactionType {
    GIFT,
    TRADE
}

data class ExchangeTransactionRecord(
    val transactionId: String,
    val type: ExchangeTransactionType,
    val playerAId: String,
    val playerBId: String,
    val offerARecordIds: List<String>,
    val offerBRecordIds: List<String>,
    val offerAHash: String,
    val offerBHash: String,
    val createdAtMs: Long,
    val state: ExchangeTransactionState,
    val protocolVersion: Int = PuppyExchangeProtocol.PROTOCOL_VERSION,
    val localRecoveryChoice: RecoveryChoice? = null,
    val remoteRecoveryChoice: RecoveryChoice? = null,
    val localCommitApplied: Boolean = false,
    val localReceivedAlreadyOwnedIds: List<String> = emptyList(),
    val localSelectedPuppyBeforeCommit: String? = null
)

enum class RecoveryChoice {
    COMPLETE,
    CANCEL
}

data class PuppyFriendRecord(
    val playerId: String,
    val friendCode: String,
    val lastKnownUsername: String,
    val addedAtMs: Long,
    val blocked: Boolean = false
)

data class TransferEligibility(
    val allowed: Boolean,
    val reason: String? = null
) {
    companion object {
        val Allowed = TransferEligibility(true)
        fun denied(reason: String) = TransferEligibility(false, reason)
    }
}

data class ExchangePeerIdentity(
    val playerId: String,
    val friendCode: String,
    val username: String,
    val protocolVersion: Int,
    val appVersion: String
)

data class IceCandidateSnapshot(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String
)

enum class ExchangeSignalType {
    OFFER,
    ANSWER
}

data class ExchangeSignalEnvelope(
    val type: ExchangeSignalType,
    val sessionId: String,
    val nonce: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val senderPlayerId: String,
    val senderFriendCode: String,
    val expectedFriendCode: String?,
    val sdp: String,
    val iceCandidates: List<IceCandidateSnapshot>,
    val protocolVersion: Int = PuppyExchangeProtocol.PROTOCOL_VERSION
)

data class ExchangeRecoveryEnvelope(
    val transactionId: String,
    val nonce: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val senderPlayerId: String,
    val peerPlayerId: String,
    val offerAHash: String,
    val offerBHash: String,
    val senderState: ExchangeTransactionState,
    val senderChoice: RecoveryChoice?,
    val protocolVersion: Int = PuppyExchangeProtocol.PROTOCOL_VERSION
)

// ---- PuppyExchangeRules ----
internal fun evaluateTransferEligibility(
    record: PuppyOwnershipRecord,
    currentPolicy: PuppyTransferPolicy,
    type: ExchangeTransactionType,
    localPlayerId: String
): TransferEligibility {
    if (record.ownerPlayerId != localPlayerId) {
        return TransferEligibility.denied("This puppy is not owned by this Player ID.")
    }
    if (record.status == OwnershipStatus.TRANSFERRED) {
        return TransferEligibility.denied("This puppy was already transferred.")
    }
    if (record.status == OwnershipStatus.LOCKED) {
        return TransferEligibility.denied("This puppy is locked by an unfinished trade.")
    }
    if (currentPolicy.bound || record.policySnapshot.bound) {
        return TransferEligibility.denied("This custom puppy is bound and cannot be transferred.")
    }
    return when (type) {
        ExchangeTransactionType.GIFT -> {
            if (currentPolicy.giftable && record.policySnapshot.giftable) TransferEligibility.Allowed
            else TransferEligibility.denied("This puppy cannot be gifted.")
        }
        ExchangeTransactionType.TRADE -> {
            when {
                currentPolicy.sourceCopy || record.policySnapshot.sourceCopy ->
                    TransferEligibility.denied("The protected source copy cannot be traded.")
                !(currentPolicy.tradeable && record.policySnapshot.tradeable) ->
                    TransferEligibility.denied("This puppy cannot be traded.")
                else -> TransferEligibility.Allowed
            }
        }
    }
}

internal fun validateTradeBundle(recordIds: List<String>): List<String> {
    require(recordIds.size <= PuppyExchangeLedger.MAX_TRADE_ITEMS) {
        "Puppy Exchange supports at most five puppies per side"
    }
    require(recordIds.distinct().size == recordIds.size) { "The same puppy cannot appear twice in one offer" }
    return recordIds
}

internal fun nextTradeState(
    current: ExchangeTransactionState,
    bothReady: Boolean = false,
    commitMatched: Boolean = false,
    disconnected: Boolean = false
): ExchangeTransactionState = when {
    disconnected && current in setOf(ExchangeTransactionState.PENDING, ExchangeTransactionState.READY) ->
        ExchangeTransactionState.CANCELLED
    disconnected && current == ExchangeTransactionState.COMMITTING ->
        ExchangeTransactionState.RECOVERY_REQUIRED
    current == ExchangeTransactionState.PENDING && bothReady -> ExchangeTransactionState.READY
    current == ExchangeTransactionState.READY && commitMatched -> ExchangeTransactionState.COMMITTING
    current == ExchangeTransactionState.COMMITTING && commitMatched -> ExchangeTransactionState.COMPLETED
    else -> current
}

internal fun recoveryCanResolve(
    local: ExchangeTransactionRecord,
    remote: ExchangeRecoveryEnvelope,
    choice: RecoveryChoice
): Boolean {
    if (local.transactionId != remote.transactionId) return false
    if (local.state != ExchangeTransactionState.RECOVERY_REQUIRED) return false
    if (local.offerAHash != remote.offerAHash || local.offerBHash != remote.offerBHash) return false
    if (remote.senderPlayerId !in setOf(local.playerAId, local.playerBId)) return false
    if (remote.peerPlayerId !in setOf(local.playerAId, local.playerBId)) return false
    return remote.senderChoice == choice
}

// ---- PuppyExchangeRecovery ----
internal data class LocalTradePerspective(
    val sentPuppyIds: List<String>,
    val receivedPuppyIds: List<String>
)

internal fun localTradePerspective(
    transaction: ExchangeTransactionRecord,
    localPlayerId: String
): LocalTradePerspective {
    require(transaction.type == ExchangeTransactionType.TRADE) { "Recovery is only available for trades" }
    return when (localPlayerId) {
        transaction.playerAId -> LocalTradePerspective(
            sentPuppyIds = transaction.offerARecordIds,
            receivedPuppyIds = transaction.offerBRecordIds
        )
        transaction.playerBId -> LocalTradePerspective(
            sentPuppyIds = transaction.offerBRecordIds,
            receivedPuppyIds = transaction.offerARecordIds
        )
        else -> throw IllegalArgumentException("This Player ID is not part of the recovery transaction")
    }
}

internal fun validateRecoveryEnvelopeForTransaction(
    transaction: ExchangeTransactionRecord,
    envelope: ExchangeRecoveryEnvelope,
    localPlayerId: String
) {
    require(transaction.type == ExchangeTransactionType.TRADE) { "Recovery is only available for trades" }
    require(transaction.state == ExchangeTransactionState.RECOVERY_REQUIRED) {
        "This trade does not currently require recovery"
    }
    val perspective = localTradePerspective(transaction, localPlayerId)
    require(perspective.sentPuppyIds.size <= PuppyExchangeLedger.MAX_TRADE_ITEMS) {
        "Invalid local recovery offer"
    }
    val peerPlayerId = if (localPlayerId == transaction.playerAId) {
        transaction.playerBId
    } else {
        transaction.playerAId
    }
    require(envelope.transactionId == transaction.transactionId) { "Recovery code references a different transaction" }
    require(envelope.senderPlayerId == peerPlayerId) { "Recovery code came from the wrong Player ID" }
    require(envelope.peerPlayerId == localPlayerId) { "Recovery code was created for a different Player ID" }
    require(envelope.offerAHash == transaction.offerAHash && envelope.offerBHash == transaction.offerBHash) {
        "Recovery journals do not match"
    }
    require(envelope.senderState == ExchangeTransactionState.RECOVERY_REQUIRED) {
        "Other device is not in recovery state"
    }
    require(envelope.senderChoice != null) { "Other player must choose Complete or Cancel before sharing a recovery code" }
}

internal fun agreedRecoveryChoice(transaction: ExchangeTransactionRecord): RecoveryChoice? =
    transaction.localRecoveryChoice?.takeIf { it == transaction.remoteRecoveryChoice }

/**
 * Handles one-use, ten-minute manual recovery codes for an already-journaled trade.
 * Recovery codes can never create a transaction: the local RECOVERY_REQUIRED journal
 * must already exist and match both players and both canonical offer hashes.
 */
internal class PuppyExchangeRecovery(
    context: Context,
    private val ledger: PuppyExchangeLedger
) {
    private val appContext = context.applicationContext
    private val localPlayerId = PuppyPlayerIdentity.playerId(appContext)
    private val nonceStore = ConsumedNonceStore(appContext)

    fun createCode(transactionId: String, choice: RecoveryChoice): String {
        val transaction = ledger.transaction(transactionId)
            ?: throw IllegalArgumentException("Unknown Puppy Exchange recovery transaction")
        require(transaction.state == ExchangeTransactionState.RECOVERY_REQUIRED) {
            "This trade does not currently require recovery"
        }
        val perspective = localTradePerspective(transaction, localPlayerId)
        require(perspective.sentPuppyIds.size <= PuppyExchangeLedger.MAX_TRADE_ITEMS) {
            "Invalid local recovery journal"
        }

        val peerPlayerId = if (localPlayerId == transaction.playerAId) {
            transaction.playerBId
        } else {
            transaction.playerAId
        }
        val now = System.currentTimeMillis()
        ledger.setRecoveryChoice(
            transactionId = transactionId,
            localChoice = choice,
            remoteChoice = transaction.remoteRecoveryChoice
        )
        return PuppyExchangeProtocol.encodeRecovery(
            ExchangeRecoveryEnvelope(
                transactionId = transaction.transactionId,
                nonce = PuppyExchangeProtocol.newNonce(),
                createdAtMs = now,
                expiresAtMs = now + PuppyExchangeProtocol.CODE_TTL_MS,
                senderPlayerId = localPlayerId,
                peerPlayerId = peerPlayerId,
                offerAHash = transaction.offerAHash,
                offerBHash = transaction.offerBHash,
                senderState = ExchangeTransactionState.RECOVERY_REQUIRED,
                senderChoice = choice
            )
        )
    }

    fun acceptPeerCode(code: String): ExchangeTransactionRecord {
        val envelope = PuppyExchangeProtocol.decodeRecovery(code)
        require(!nonceStore.isConsumed(envelope.nonce)) {
            "This Recovery Code was already used on this device"
        }
        val transaction = ledger.transaction(envelope.transactionId)
            ?: throw IllegalArgumentException("Recovery Code does not match a local unresolved trade")

        validateRecoveryEnvelopeForTransaction(
            transaction = transaction,
            envelope = envelope,
            localPlayerId = localPlayerId
        )

        ledger.setRecoveryChoice(
            transactionId = transaction.transactionId,
            localChoice = transaction.localRecoveryChoice,
            remoteChoice = envelope.senderChoice
        )
        nonceStore.markConsumed(envelope.nonce)
        return ledger.transaction(transaction.transactionId)
            ?: error("Recovery transaction disappeared after validation")
    }
}

// ---- PuppyExchangeEngine ----
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