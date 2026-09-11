package com.harleytg.puppyclicker

import android.content.Context

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
