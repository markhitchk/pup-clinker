package com.harleytg.puppyclicker

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
