package com.harleytg.puppyclicker

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class ExchangeIdentityHello(
    val playerId: String,
    val friendCode: String,
    val username: String,
    val protocolVersion: Int,
    val appVersionCode: Int
)

internal enum class ExchangePeerValidationReason {
    INVALID_PLAYER_ID,
    INVALID_FRIEND_CODE,
    FRIEND_CODE_MISMATCH,
    BLOCKED_PLAYER,
    PROTOCOL_MISMATCH
}

internal data class ExchangePeerValidationResult(
    val accepted: Boolean,
    val reason: ExchangePeerValidationReason? = null,
    val message: String? = null
)

internal fun validateExchangePeer(
    hello: ExchangeIdentityHello,
    expectedFriendCode: String?,
    blockedPlayerIds: Set<String>
): ExchangePeerValidationResult {
    if (!PuppyPlayerIdentity.isValidPlayerId(hello.playerId)) {
        return ExchangePeerValidationResult(
            accepted = false,
            reason = ExchangePeerValidationReason.INVALID_PLAYER_ID,
            message = "The connected peer sent an invalid Player ID."
        )
    }
    if (!PuppyPlayerIdentity.isValidFriendCode(hello.friendCode)) {
        return ExchangePeerValidationResult(
            accepted = false,
            reason = ExchangePeerValidationReason.INVALID_FRIEND_CODE,
            message = "The connected peer sent an invalid Friend Code."
        )
    }
    if (expectedFriendCode != null && hello.friendCode != expectedFriendCode) {
        return ExchangePeerValidationResult(
            accepted = false,
            reason = ExchangePeerValidationReason.FRIEND_CODE_MISMATCH,
            message = "Connected player does not match the expected Friend Code."
        )
    }
    if (hello.playerId in blockedPlayerIds) {
        return ExchangePeerValidationResult(
            accepted = false,
            reason = ExchangePeerValidationReason.BLOCKED_PLAYER,
            message = "This Player ID is blocked on this device."
        )
    }
    if (hello.protocolVersion != PuppyExchangeProtocol.PROTOCOL_VERSION) {
        return ExchangePeerValidationResult(
            accepted = false,
            reason = ExchangePeerValidationReason.PROTOCOL_MISMATCH,
            message = "This player is using an incompatible Puppy Exchange protocol version."
        )
    }
    return ExchangePeerValidationResult(accepted = true)
}

internal sealed class ExchangeConnectionState {
    data object Idle : ExchangeConnectionState()
    data object CreatingOffer : ExchangeConnectionState()
    data object WaitingForAnswer : ExchangeConnectionState()
    data object ApplyingOffer : ExchangeConnectionState()
    data object Connecting : ExchangeConnectionState()
    data class Connected(val peer: ExchangeIdentityHello) : ExchangeConnectionState()
    data class Failed(val message: String) : ExchangeConnectionState()
    data object Closed : ExchangeConnectionState()
}

internal data class ExchangeSessionMessage(
    val type: String,
    val payload: JSONObject
)

internal data class ExchangeRealtimeState(
    val peerOnline: Boolean = false,
    val latencyMs: Long? = null,
    val lastPeerActivityMs: Long = 0L
)


/**
 * Coordinates manual Offer/Answer signaling with a WebRTC DataChannel.
 *
 * Friend Codes are expected-peer checks only. They do not locate another player and
 * there is intentionally no server-side signaling or account lookup in this class.
 */
internal class PuppyExchangeSession(
    context: Context,
    private val blockedPlayerIds: () -> Set<String> = { emptySet() }
) : PuppyExchangeWebRtc.Listener {
    private val appContext = context.applicationContext
    private val nonceStore = ConsumedNonceStore(appContext)
    private val transport = PuppyExchangeWebRtc(appContext, this)
    private val localHello = ExchangeIdentityHello(
        playerId = PuppyPlayerIdentity.playerId(appContext),
        friendCode = PuppyPlayerIdentity.friendCode(appContext),
        username = PuppyPlayerIdentity.username(appContext),
        protocolVersion = PuppyExchangeProtocol.PROTOCOL_VERSION,
        appVersionCode = BuildConfig.VERSION_CODE
    )

    private val _state = MutableStateFlow<ExchangeConnectionState>(ExchangeConnectionState.Idle)
    val state: StateFlow<ExchangeConnectionState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<ExchangeSessionMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<ExchangeSessionMessage> = _messages.asSharedFlow()

    private val _realtime = MutableStateFlow(ExchangeRealtimeState())
    val realtime: StateFlow<ExchangeRealtimeState> = _realtime.asStateFlow()

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingPings = ConcurrentHashMap<String, Long>()
    private var heartbeatJob: Job? = null

    @Volatile
    private var expectedRemoteFriendCode: String? = null

    @Volatile
    private var verifiedPeer: ExchangeIdentityHello? = null

    suspend fun createOffer(expectedFriendCode: String): String {
        require(PuppyPlayerIdentity.isValidFriendCode(expectedFriendCode)) { "Invalid Friend Code" }
        require(expectedFriendCode != localHello.friendCode) { "You cannot connect Puppy Exchange to this device itself" }
        expectedRemoteFriendCode = expectedFriendCode
        verifiedPeer = null
        _state.value = ExchangeConnectionState.CreatingOffer

        return try {
            val description = transport.createOffer()
            val now = System.currentTimeMillis()
            val envelope = ExchangeSignalEnvelope(
                type = ExchangeSignalType.OFFER,
                sessionId = description.sessionId,
                nonce = PuppyExchangeProtocol.newNonce(),
                createdAtMs = now,
                expiresAtMs = now + PuppyExchangeProtocol.CODE_TTL_MS,
                senderPlayerId = localHello.playerId,
                senderFriendCode = localHello.friendCode,
                expectedFriendCode = expectedFriendCode,
                sdp = description.sdp,
                iceCandidates = description.iceCandidates
            )
            _state.value = ExchangeConnectionState.WaitingForAnswer
            PuppyExchangeProtocol.encodeSignal(envelope)
        } catch (error: Exception) {
            fail("Unable to create a direct Puppy Exchange offer: ${error.message ?: "unknown error"}")
            throw error
        }
    }

    suspend fun acceptOfferAndCreateAnswer(offerCode: String): String {
        val offer = PuppyExchangeProtocol.decodeSignal(offerCode)
        require(offer.type == ExchangeSignalType.OFFER) { "Expected a Puppy Exchange Offer Code" }
        require(!nonceStore.isConsumed(offer.nonce)) { "This Offer Code was already used on this device" }
        require(offer.expectedFriendCode == null || offer.expectedFriendCode == localHello.friendCode) {
            "This Offer Code was created for a different Friend Code"
        }
        require(offer.senderFriendCode != localHello.friendCode) { "You cannot connect Puppy Exchange to this device itself" }

        expectedRemoteFriendCode = offer.senderFriendCode
        verifiedPeer = null
        _state.value = ExchangeConnectionState.ApplyingOffer

        return try {
            val description = transport.acceptOffer(
                sessionId = offer.sessionId,
                remoteSdp = offer.sdp,
                remoteCandidates = offer.iceCandidates
            )
            val now = System.currentTimeMillis()
            val answer = ExchangeSignalEnvelope(
                type = ExchangeSignalType.ANSWER,
                sessionId = offer.sessionId,
                nonce = PuppyExchangeProtocol.newNonce(),
                createdAtMs = now,
                expiresAtMs = now + PuppyExchangeProtocol.CODE_TTL_MS,
                senderPlayerId = localHello.playerId,
                senderFriendCode = localHello.friendCode,
                expectedFriendCode = offer.senderFriendCode,
                sdp = description.sdp,
                iceCandidates = description.iceCandidates
            )
            nonceStore.markConsumed(offer.nonce)
            _state.value = ExchangeConnectionState.Connecting
            PuppyExchangeProtocol.encodeSignal(answer)
        } catch (error: Exception) {
            fail("Unable to apply the Puppy Exchange offer: ${error.message ?: "unknown error"}")
            throw error
        }
    }

    suspend fun applyAnswer(answerCode: String) {
        val answer = PuppyExchangeProtocol.decodeSignal(answerCode)
        require(answer.type == ExchangeSignalType.ANSWER) { "Expected a Puppy Exchange Answer Code" }
        require(!nonceStore.isConsumed(answer.nonce)) { "This Answer Code was already used on this device" }
        require(answer.expectedFriendCode == null || answer.expectedFriendCode == localHello.friendCode) {
            "This Answer Code was created for a different Friend Code"
        }
        val expected = expectedRemoteFriendCode
        require(expected == null || answer.senderFriendCode == expected) {
            "Answer Code does not match the expected Friend Code"
        }
        transport.applyAnswer(
            sessionId = answer.sessionId,
            remoteSdp = answer.sdp,
            remoteCandidates = answer.iceCandidates
        )
        nonceStore.markConsumed(answer.nonce)
        _state.value = ExchangeConnectionState.Connecting
    }

    fun sendMessage(type: String, payload: JSONObject = JSONObject()) {
        require(type.matches(Regex("[a-z0-9_.-]{1,48}"))) { "Invalid Puppy Exchange message type" }
        check(verifiedPeer != null) { "Puppy Exchange peer is not verified" }
        val wire = JSONObject()
            .put("v", PuppyExchangeProtocol.PROTOCOL_VERSION)
            .put("type", type)
            .put("payload", payload)
            .toString()
        require(wire.toByteArray(Charsets.UTF_8).size <= MAX_WIRE_BYTES) { "Puppy Exchange message is too large" }
        check(transport.sendText(wire)) { "Puppy Exchange DataChannel is not open" }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        pendingPings.clear()
        verifiedPeer = null
        expectedRemoteFriendCode = null
        _realtime.value = ExchangeRealtimeState()
        transport.close()
        _state.value = ExchangeConnectionState.Closed
    }

    override fun onChannelOpen() {
        val hello = JSONObject()
            .put("v", PuppyExchangeProtocol.PROTOCOL_VERSION)
            .put("type", HELLO_TYPE)
            .put("payload", JSONObject().apply {
                put("playerId", localHello.playerId)
                put("friendCode", localHello.friendCode)
                put("username", localHello.username)
                put("protocolVersion", localHello.protocolVersion)
                put("appVersionCode", localHello.appVersionCode)
            })
            .toString()
        if (!transport.sendText(hello)) {
            fail("Puppy Exchange DataChannel opened but the identity handshake could not be sent.")
        }
    }

    override fun onTextMessage(message: String) {
        if (message.toByteArray(Charsets.UTF_8).size > MAX_WIRE_BYTES) {
            fail("Puppy Exchange peer sent an oversized message.")
            return
        }
        val root = runCatching { JSONObject(message) }.getOrElse {
            fail("Puppy Exchange peer sent malformed session data.")
            return
        }
        if (root.optInt("v", -1) != PuppyExchangeProtocol.PROTOCOL_VERSION) {
            fail("Puppy Exchange peer is using an incompatible protocol.")
            return
        }
        val type = root.optString("type")
        val payload = root.optJSONObject("payload") ?: JSONObject()

        if (verifiedPeer == null) {
            if (type != HELLO_TYPE) {
                fail("Puppy Exchange peer sent data before identity verification.")
                return
            }
            val hello = runCatching {
                ExchangeIdentityHello(
                    playerId = payload.getString("playerId"),
                    friendCode = payload.getString("friendCode"),
                    username = PuppyPlayerIdentity.normalizeUsername(payload.getString("username")),
                    protocolVersion = payload.getInt("protocolVersion"),
                    appVersionCode = payload.getInt("appVersionCode")
                )
            }.getOrElse {
                fail("Puppy Exchange peer sent an invalid identity handshake.")
                return
            }
            val validation = validateExchangePeer(hello, expectedRemoteFriendCode, blockedPlayerIds())
            if (!validation.accepted) {
                fail(validation.message ?: "Puppy Exchange peer identity was rejected.")
                return
            }
            verifiedPeer = hello
            val now = System.currentTimeMillis()
            _realtime.value = ExchangeRealtimeState(
                peerOnline = true,
                latencyMs = null,
                lastPeerActivityMs = now
            )
            _state.value = ExchangeConnectionState.Connected(hello)
            startHeartbeat()
            return
        }

        val now = System.currentTimeMillis()
        _realtime.value = _realtime.value.copy(
            peerOnline = true,
            lastPeerActivityMs = now
        )

        if (type == HELLO_TYPE) return
        if (type == PING_TYPE) {
            val nonce = payload.optString("nonce")
            if (nonce.isNotBlank()) {
                runCatching {
                    sendMessage(PONG_TYPE, JSONObject().put("nonce", nonce))
                }
            }
            return
        }
        if (type == PONG_TYPE) {
            val nonce = payload.optString("nonce")
            val sentAt = pendingPings.remove(nonce)
            if (sentAt != null) {
                _realtime.value = _realtime.value.copy(
                    peerOnline = true,
                    latencyMs = (now - sentAt).coerceAtLeast(0L),
                    lastPeerActivityMs = now
                )
            }
            return
        }
        if (!type.matches(Regex("[a-z0-9_.-]{1,48}"))) {
            fail("Puppy Exchange peer sent an invalid message type.")
            return
        }
        _messages.tryEmit(ExchangeSessionMessage(type, payload))
    }

    override fun onDirectConnectionFailed(reason: String) {
        fail(
            "Direct Connection Failed. $reason Try another network or Wi-Fi and create a fresh connection code."
        )
    }

    override fun onClosed() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        pendingPings.clear()
        _realtime.value = _realtime.value.copy(peerOnline = false)
        if (_state.value !is ExchangeConnectionState.Failed) {
            _state.value = ExchangeConnectionState.Closed
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = sessionScope.launch {
            while (isActive && verifiedPeer != null) {
                val nonce = PuppyExchangeProtocol.newNonce()
                val sentAt = System.currentTimeMillis()
                pendingPings[nonce] = sentAt
                runCatching {
                    sendMessage(PING_TYPE, JSONObject().put("nonce", nonce))
                }
                val cutoff = sentAt - PEER_STALE_MS
                pendingPings.entries.removeIf { it.value < cutoff }
                val current = _realtime.value
                if (current.lastPeerActivityMs > 0L && sentAt - current.lastPeerActivityMs > PEER_STALE_MS) {
                    _realtime.value = current.copy(peerOnline = false)
                }
                delay(HEARTBEAT_MS)
            }
        }
    }

    private fun fail(message: String) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        pendingPings.clear()
        verifiedPeer = null
        _realtime.value = _realtime.value.copy(peerOnline = false)
        _state.value = ExchangeConnectionState.Failed(message)
        transport.close()
    }

    companion object {
        private const val HELLO_TYPE = "hello"
        private const val PING_TYPE = "ping"
        private const val PONG_TYPE = "pong"
        private const val HEARTBEAT_MS = 2_500L
        private const val PEER_STALE_MS = 10_000L
        private const val MAX_WIRE_BYTES = 256 * 1024
    }
}
