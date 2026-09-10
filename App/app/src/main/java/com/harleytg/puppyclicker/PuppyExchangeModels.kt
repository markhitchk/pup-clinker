package com.harleytg.puppyclicker

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
    val remoteRecoveryChoice: RecoveryChoice? = null
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
