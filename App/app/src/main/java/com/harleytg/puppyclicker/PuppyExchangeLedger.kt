package com.harleytg.puppyclicker

import android.content.Context
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.json.JSONArray
import org.json.JSONObject

internal data class PuppyExchangeLedgerSnapshot(
    val ownership: List<PuppyOwnershipRecord>,
    val transactions: List<ExchangeTransactionRecord>,
    val friends: List<PuppyFriendRecord>
)

/**
 * Local Exchange ledger persisted inside the main protected Puppy Clicker preference store.
 * Changes are therefore picked up by the existing PupEye seal and encrypted Android/data mirror.
 */
internal class PuppyExchangeLedger(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = ReentrantLock()

    fun snapshot(): PuppyExchangeLedgerSnapshot = lock.withLock { decode(prefs.getString(KEY_LEDGER, null)) }

    fun activeOwnershipFor(puppyId: String, playerId: String): PuppyOwnershipRecord? = snapshot().ownership
        .firstOrNull { it.puppyId == puppyId && it.ownerPlayerId == playerId && it.status == OwnershipStatus.ACTIVE }

    fun transaction(transactionId: String): ExchangeTransactionRecord? = snapshot().transactions
        .firstOrNull { it.transactionId == transactionId }

    fun putOwnership(record: PuppyOwnershipRecord) = mutate { current ->
        require(PuppyPlayerIdentity.isValidPlayerId(record.ownerPlayerId)) { "Invalid ownership Player ID" }
        val next = current.ownership.filterNot { it.recordId == record.recordId } + record
        current.copy(ownership = next)
    }

    fun putTransaction(record: ExchangeTransactionRecord) = mutate { current ->
        require(record.offerARecordIds.size <= MAX_TRADE_ITEMS && record.offerBRecordIds.size <= MAX_TRADE_ITEMS) {
            "Puppy Exchange supports at most five puppies per side"
        }
        val next = current.transactions.filterNot { it.transactionId == record.transactionId } + record
        current.copy(transactions = next)
    }

    fun updateTransactionState(transactionId: String, state: ExchangeTransactionState) = mutate { current ->
        val existing = current.transactions.firstOrNull { it.transactionId == transactionId }
            ?: error("Unknown Puppy Exchange transaction")
        current.copy(
            transactions = current.transactions.map {
                if (it.transactionId == transactionId) existing.copy(state = state) else it
            }
        )
    }

    fun markLocalCommitApplied(transactionId: String) = mutate { current ->
        val existing = current.transactions.firstOrNull { it.transactionId == transactionId }
            ?: error("Unknown Puppy Exchange transaction")
        current.copy(
            transactions = current.transactions.map {
                if (it.transactionId == transactionId) existing.copy(localCommitApplied = true) else it
            }
        )
    }

    fun markInterruptedCommitsForRecovery() = mutate { current ->
        current.copy(
            transactions = current.transactions.map { transaction ->
                if (transaction.state == ExchangeTransactionState.COMMITTING) {
                    transaction.copy(state = ExchangeTransactionState.RECOVERY_REQUIRED)
                } else transaction
            }
        )
    }

    fun lockedPuppyIdsFor(playerId: String): Set<String> = snapshot().transactions
        .asSequence()
        .filter {
            it.type == ExchangeTransactionType.TRADE &&
                it.state in setOf(
                    ExchangeTransactionState.COMMITTING,
                    ExchangeTransactionState.RECOVERY_REQUIRED
                )
        }
        .flatMap { transaction ->
            when (playerId) {
                transaction.playerAId -> transaction.offerARecordIds.asSequence()
                transaction.playerBId -> transaction.offerBRecordIds.asSequence()
                else -> emptySequence()
            }
        }
        .toSet()

    fun setRecoveryChoice(
        transactionId: String,
        localChoice: RecoveryChoice?,
        remoteChoice: RecoveryChoice?
    ) = mutate { current ->
        val existing = current.transactions.firstOrNull { it.transactionId == transactionId }
            ?: error("Unknown Puppy Exchange transaction")
        current.copy(
            transactions = current.transactions.map {
                if (it.transactionId == transactionId) {
                    existing.copy(localRecoveryChoice = localChoice, remoteRecoveryChoice = remoteChoice)
                } else it
            }
        )
    }

    fun upsertFriend(record: PuppyFriendRecord) = mutate { current ->
        require(PuppyPlayerIdentity.isValidPlayerId(record.playerId)) { "Invalid friend Player ID" }
        require(PuppyPlayerIdentity.isValidFriendCode(record.friendCode)) { "Invalid friend code" }
        current.copy(friends = current.friends.filterNot { it.playerId == record.playerId } + record)
    }

    fun removeFriend(playerId: String) = mutate { current ->
        current.copy(friends = current.friends.filterNot { it.playerId == playerId })
    }

    fun setBlocked(playerId: String, blocked: Boolean) = mutate { current ->
        current.copy(
            friends = current.friends.map {
                if (it.playerId == playerId) it.copy(blocked = blocked) else it
            }
        )
    }

    fun isBlocked(playerId: String): Boolean = snapshot().friends.any { it.playerId == playerId && it.blocked }

    fun transferEligibility(
        record: PuppyOwnershipRecord,
        currentPolicy: PuppyTransferPolicy,
        type: ExchangeTransactionType,
        localPlayerId: String
    ): TransferEligibility {
        if (record.ownerPlayerId != localPlayerId) return TransferEligibility.denied("This puppy is not owned by this Player ID.")
        if (record.status == OwnershipStatus.TRANSFERRED) return TransferEligibility.denied("This puppy was already transferred.")
        if (record.status == OwnershipStatus.LOCKED) return TransferEligibility.denied("This puppy is locked by an unfinished trade.")
        if (currentPolicy.bound || record.policySnapshot.bound) return TransferEligibility.denied("This custom puppy is bound and cannot be transferred.")
        if (type == ExchangeTransactionType.GIFT && !(currentPolicy.giftable && record.policySnapshot.giftable)) {
            return TransferEligibility.denied("This puppy cannot be gifted.")
        }
        if (type == ExchangeTransactionType.TRADE) {
            if (currentPolicy.sourceCopy || record.policySnapshot.sourceCopy) {
                return TransferEligibility.denied("The protected source copy cannot be traded.")
            }
            if (!(currentPolicy.tradeable && record.policySnapshot.tradeable)) {
                return TransferEligibility.denied("This puppy cannot be traded.")
            }
        }
        return TransferEligibility.Allowed
    }

    fun lockOwnership(recordIds: Collection<String>) = mutate { current ->
        current.copy(
            ownership = current.ownership.map { record ->
                if (record.recordId in recordIds && record.status == OwnershipStatus.ACTIVE) {
                    record.copy(status = OwnershipStatus.LOCKED)
                } else record
            }
        )
    }

    fun unlockOwnership(recordIds: Collection<String>) = mutate { current ->
        current.copy(
            ownership = current.ownership.map { record ->
                if (record.recordId in recordIds && record.status == OwnershipStatus.LOCKED) {
                    record.copy(status = OwnershipStatus.ACTIVE)
                } else record
            }
        )
    }

    fun retireForTrade(
        recordId: String,
        destinationPlayerId: String,
        transactionId: String,
        nowMs: Long
    ): PuppyOwnershipRecord = lock.withLock {
        val current = decode(prefs.getString(KEY_LEDGER, null))
        val existing = current.ownership.firstOrNull { it.recordId == recordId }
            ?: error("Unknown ownership record")
        require(existing.status == OwnershipStatus.ACTIVE || existing.status == OwnershipStatus.LOCKED) {
            "Ownership record is not transferable"
        }
        require(!existing.policySnapshot.sourceCopy && !existing.policySnapshot.bound) {
            "Protected ownership cannot be retired by a trade"
        }
        val retired = existing.copy(
            status = OwnershipStatus.TRANSFERRED,
            transactionId = transactionId,
            transferredToPlayerId = destinationPlayerId,
            transferredAtMs = nowMs
        )
        persist(
            current.copy(
                ownership = current.ownership.map { if (it.recordId == recordId) retired else it }
            )
        )
        retired
    }

    fun createReceivedOwnership(
        puppyId: String,
        recipientPlayerId: String,
        acquisitionType: AcquisitionType,
        sourceOwnerPlayerId: String?,
        previousRecordId: String?,
        transactionId: String,
        policy: PuppyTransferPolicy,
        nowMs: Long
    ): PuppyOwnershipRecord {
        val record = PuppyOwnershipRecord(
            recordId = PuppyExchangeProtocol.newOwnershipRecordId(),
            puppyId = puppyId,
            ownerPlayerId = recipientPlayerId,
            acquisitionType = acquisitionType,
            sourceOwnerPlayerId = sourceOwnerPlayerId,
            previousRecordId = previousRecordId,
            transactionId = transactionId,
            acquiredAtMs = nowMs,
            status = OwnershipStatus.ACTIVE,
            policySnapshot = policy.copy(sourceCopy = false)
        )
        putOwnership(record)
        return record
    }

    private fun mutate(block: (PuppyExchangeLedgerSnapshot) -> PuppyExchangeLedgerSnapshot) = lock.withLock {
        persist(block(decode(prefs.getString(KEY_LEDGER, null))))
    }

    private fun persist(snapshot: PuppyExchangeLedgerSnapshot) {
        val text = encode(snapshot).toString()
        check(prefs.edit().putString(KEY_LEDGER, text).commit()) { "Unable to persist Puppy Exchange ledger" }
    }

    internal fun encode(snapshot: PuppyExchangeLedgerSnapshot): JSONObject = JSONObject().apply {
        put("schema", SCHEMA)
        put("ownership", JSONArray().apply { snapshot.ownership.forEach { put(ownershipJson(it)) } })
        put("transactions", JSONArray().apply { snapshot.transactions.forEach { put(transactionJson(it)) } })
        put("friends", JSONArray().apply { snapshot.friends.forEach { put(friendJson(it)) } })
    }

    internal fun decode(text: String?): PuppyExchangeLedgerSnapshot {
        if (text.isNullOrBlank()) return PuppyExchangeLedgerSnapshot(emptyList(), emptyList(), emptyList())
        val root = JSONObject(text)
        require(root.optInt("schema", -1) == SCHEMA) { "Unsupported Puppy Exchange ledger schema" }
        return PuppyExchangeLedgerSnapshot(
            ownership = root.getJSONArray("ownership").mapObjects(::ownershipFromJson),
            transactions = root.getJSONArray("transactions").mapObjects(::transactionFromJson),
            friends = root.getJSONArray("friends").mapObjects(::friendFromJson)
        )
    }

    private fun ownershipJson(record: PuppyOwnershipRecord) = JSONObject().apply {
        put("id", record.recordId)
        put("puppy", record.puppyId)
        put("owner", record.ownerPlayerId)
        put("acquisition", record.acquisitionType.name)
        record.sourceOwnerPlayerId?.let { put("sourceOwner", it) }
        record.previousRecordId?.let { put("previous", it) }
        record.transactionId?.let { put("transaction", it) }
        put("acquired", record.acquiredAtMs)
        put("status", record.status.name)
        record.transferredToPlayerId?.let { put("transferredTo", it) }
        record.transferredAtMs?.let { put("transferredAt", it) }
        put("policy", policyJson(record.policySnapshot))
    }

    private fun ownershipFromJson(item: JSONObject) = PuppyOwnershipRecord(
        recordId = item.getString("id"),
        puppyId = item.getString("puppy"),
        ownerPlayerId = item.getString("owner"),
        acquisitionType = AcquisitionType.valueOf(item.getString("acquisition")),
        sourceOwnerPlayerId = item.optString("sourceOwner").takeIf { it.isNotBlank() },
        previousRecordId = item.optString("previous").takeIf { it.isNotBlank() },
        transactionId = item.optString("transaction").takeIf { it.isNotBlank() },
        acquiredAtMs = item.getLong("acquired"),
        status = OwnershipStatus.valueOf(item.getString("status")),
        transferredToPlayerId = item.optString("transferredTo").takeIf { it.isNotBlank() },
        transferredAtMs = item.optLong("transferredAt").takeIf { item.has("transferredAt") },
        policySnapshot = policyFromJson(item.getJSONObject("policy"))
    )

    private fun transactionJson(record: ExchangeTransactionRecord) = JSONObject().apply {
        put("id", record.transactionId)
        put("type", record.type.name)
        put("a", record.playerAId)
        put("b", record.playerBId)
        put("offerAIds", JSONArray(record.offerARecordIds))
        put("offerBIds", JSONArray(record.offerBRecordIds))
        put("offerAHash", record.offerAHash)
        put("offerBHash", record.offerBHash)
        put("created", record.createdAtMs)
        put("state", record.state.name)
        put("protocol", record.protocolVersion)
        record.localRecoveryChoice?.let { put("localChoice", it.name) }
        record.remoteRecoveryChoice?.let { put("remoteChoice", it.name) }
        put("localCommitApplied", record.localCommitApplied)
        put("localReceivedAlreadyOwnedIds", JSONArray(record.localReceivedAlreadyOwnedIds))
        record.localSelectedPuppyBeforeCommit?.let { put("localSelectedPuppyBeforeCommit", it) }
    }

    private fun transactionFromJson(item: JSONObject) = ExchangeTransactionRecord(
        transactionId = item.getString("id"),
        type = ExchangeTransactionType.valueOf(item.getString("type")),
        playerAId = item.getString("a"),
        playerBId = item.getString("b"),
        offerARecordIds = item.getJSONArray("offerAIds").stringList(),
        offerBRecordIds = item.getJSONArray("offerBIds").stringList(),
        offerAHash = item.getString("offerAHash"),
        offerBHash = item.getString("offerBHash"),
        createdAtMs = item.getLong("created"),
        state = ExchangeTransactionState.valueOf(item.getString("state")),
        protocolVersion = item.getInt("protocol"),
        localRecoveryChoice = item.optString("localChoice").takeIf { it.isNotBlank() }?.let(RecoveryChoice::valueOf),
        remoteRecoveryChoice = item.optString("remoteChoice").takeIf { it.isNotBlank() }?.let(RecoveryChoice::valueOf),
        localCommitApplied = item.optBoolean("localCommitApplied", false),
        localReceivedAlreadyOwnedIds = item.optJSONArray("localReceivedAlreadyOwnedIds")?.stringList().orEmpty(),
        localSelectedPuppyBeforeCommit = item.optString("localSelectedPuppyBeforeCommit").takeIf { it.isNotBlank() }
    )

    private fun friendJson(record: PuppyFriendRecord) = JSONObject().apply {
        put("player", record.playerId)
        put("friendCode", record.friendCode)
        put("username", record.lastKnownUsername)
        put("added", record.addedAtMs)
        put("blocked", record.blocked)
    }

    private fun friendFromJson(item: JSONObject) = PuppyFriendRecord(
        playerId = item.getString("player"),
        friendCode = item.getString("friendCode"),
        lastKnownUsername = item.getString("username"),
        addedAtMs = item.getLong("added"),
        blocked = item.optBoolean("blocked", false)
    )

    private fun policyJson(policy: PuppyTransferPolicy) = JSONObject().apply {
        put("giftable", policy.giftable)
        put("tradeable", policy.tradeable)
        put("bound", policy.bound)
        put("sourceCopy", policy.sourceCopy)
        put("limited", policy.limited)
    }

    private fun policyFromJson(item: JSONObject) = PuppyTransferPolicy(
        giftable = item.optBoolean("giftable", false),
        tradeable = item.optBoolean("tradeable", false),
        bound = item.optBoolean("bound", false),
        sourceCopy = item.optBoolean("sourceCopy", false),
        limited = item.optBoolean("limited", false)
    )

    private fun JSONArray.stringList(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = buildList {
        for (index in 0 until length()) add(block(getJSONObject(index)))
    }

    companion object {
        private const val KEY_LEDGER = "puppy_exchange_ledger_v1"
        private const val SCHEMA = 1
        const val MAX_TRADE_ITEMS = 5
    }
}
