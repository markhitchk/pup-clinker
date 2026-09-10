package com.harleytg.puppyclicker

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import org.json.JSONArray
import org.json.JSONObject

internal object PuppyExchangeProtocol {
    const val PROTOCOL_VERSION = 1
    const val CODE_TTL_MS = 10L * 60L * 1_000L
    private const val MAX_DECODED_BYTES = 256 * 1024
    private const val MAX_CODE_CHARS = 512 * 1024
    private const val OFFER_PREFIX = "PUP-O1-"
    private const val ANSWER_PREFIX = "PUP-A1-"
    private const val RECOVERY_PREFIX = "PUP-R1-"
    private val random = SecureRandom()

    fun newSessionId(): String = "XS-${randomToken(18)}"
    fun newTransactionId(): String = "XT-${randomToken(18)}"
    fun newOwnershipRecordId(): String = "XO-${randomToken(18)}"
    fun newNonce(): String = randomToken(24)

    fun encodeSignal(envelope: ExchangeSignalEnvelope): String {
        validateSignal(envelope, envelope.createdAtMs)
        val json = JSONObject().apply {
            put("v", envelope.protocolVersion)
            put("type", envelope.type.name)
            put("session", envelope.sessionId)
            put("nonce", envelope.nonce)
            put("created", envelope.createdAtMs)
            put("expires", envelope.expiresAtMs)
            put("senderPlayerId", envelope.senderPlayerId)
            put("senderFriendCode", envelope.senderFriendCode)
            envelope.expectedFriendCode?.let { put("expectedFriendCode", it) }
            put("sdp", envelope.sdp)
            put("ice", JSONArray().apply {
                envelope.iceCandidates.forEach { candidate ->
                    put(JSONObject().apply {
                        candidate.sdpMid?.let { put("mid", it) }
                        put("line", candidate.sdpMLineIndex)
                        put("candidate", candidate.candidate)
                    })
                }
            })
        }
        val prefix = if (envelope.type == ExchangeSignalType.OFFER) OFFER_PREFIX else ANSWER_PREFIX
        return prefix + encodePayload(json.toString().toByteArray(Charsets.UTF_8))
    }

    fun decodeSignal(code: String, nowMs: Long = System.currentTimeMillis()): ExchangeSignalEnvelope {
        require(code.length <= MAX_CODE_CHARS) { "Connection code is too large" }
        val type = when {
            code.startsWith(OFFER_PREFIX) -> ExchangeSignalType.OFFER
            code.startsWith(ANSWER_PREFIX) -> ExchangeSignalType.ANSWER
            else -> throw IllegalArgumentException("Invalid Puppy Exchange connection code")
        }
        val prefix = if (type == ExchangeSignalType.OFFER) OFFER_PREFIX else ANSWER_PREFIX
        val root = JSONObject(decodePayload(code.removePrefix(prefix)).toString(Charsets.UTF_8))
        val iceJson = root.getJSONArray("ice")
        require(iceJson.length() <= 128) { "Too many ICE candidates" }
        val ice = buildList {
            for (index in 0 until iceJson.length()) {
                val item = iceJson.getJSONObject(index)
                add(
                    IceCandidateSnapshot(
                        sdpMid = item.optString("mid").takeIf { it.isNotBlank() },
                        sdpMLineIndex = item.getInt("line"),
                        candidate = item.getString("candidate")
                    )
                )
            }
        }
        val envelope = ExchangeSignalEnvelope(
            type = ExchangeSignalType.valueOf(root.getString("type")),
            sessionId = root.getString("session"),
            nonce = root.getString("nonce"),
            createdAtMs = root.getLong("created"),
            expiresAtMs = root.getLong("expires"),
            senderPlayerId = root.getString("senderPlayerId"),
            senderFriendCode = root.getString("senderFriendCode"),
            expectedFriendCode = root.optString("expectedFriendCode").takeIf { it.isNotBlank() },
            sdp = root.getString("sdp"),
            iceCandidates = ice,
            protocolVersion = root.getInt("v")
        )
        require(envelope.type == type) { "Connection code type mismatch" }
        validateSignal(envelope, nowMs)
        return envelope
    }

    fun encodeRecovery(envelope: ExchangeRecoveryEnvelope): String {
        validateRecovery(envelope, envelope.createdAtMs)
        val json = JSONObject().apply {
            put("v", envelope.protocolVersion)
            put("transaction", envelope.transactionId)
            put("nonce", envelope.nonce)
            put("created", envelope.createdAtMs)
            put("expires", envelope.expiresAtMs)
            put("sender", envelope.senderPlayerId)
            put("peer", envelope.peerPlayerId)
            put("offerA", envelope.offerAHash)
            put("offerB", envelope.offerBHash)
            put("state", envelope.senderState.name)
            envelope.senderChoice?.let { put("choice", it.name) }
        }
        return RECOVERY_PREFIX + encodePayload(json.toString().toByteArray(Charsets.UTF_8))
    }

    fun decodeRecovery(code: String, nowMs: Long = System.currentTimeMillis()): ExchangeRecoveryEnvelope {
        require(code.length <= MAX_CODE_CHARS && code.startsWith(RECOVERY_PREFIX)) {
            "Invalid Puppy Exchange recovery code"
        }
        val root = JSONObject(decodePayload(code.removePrefix(RECOVERY_PREFIX)).toString(Charsets.UTF_8))
        val envelope = ExchangeRecoveryEnvelope(
            transactionId = root.getString("transaction"),
            nonce = root.getString("nonce"),
            createdAtMs = root.getLong("created"),
            expiresAtMs = root.getLong("expires"),
            senderPlayerId = root.getString("sender"),
            peerPlayerId = root.getString("peer"),
            offerAHash = root.getString("offerA"),
            offerBHash = root.getString("offerB"),
            senderState = ExchangeTransactionState.valueOf(root.getString("state")),
            senderChoice = root.optString("choice").takeIf { it.isNotBlank() }?.let(RecoveryChoice::valueOf),
            protocolVersion = root.getInt("v")
        )
        validateRecovery(envelope, nowMs)
        return envelope
    }

    fun canonicalOfferHash(recordIds: List<String>): String {
        val canonical = recordIds.distinct().sorted().joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xFF) }
    }

    private fun validateSignal(envelope: ExchangeSignalEnvelope, nowMs: Long) {
        require(envelope.protocolVersion == PROTOCOL_VERSION) { "Unsupported Puppy Exchange protocol" }
        require(envelope.sessionId.startsWith("XS-") && envelope.sessionId.length in 12..80) { "Invalid session ID" }
        require(envelope.nonce.length in 16..128) { "Invalid signaling nonce" }
        require(PuppyPlayerIdentity.isValidPlayerId(envelope.senderPlayerId)) { "Invalid sender Player ID" }
        require(PuppyPlayerIdentity.isValidFriendCode(envelope.senderFriendCode)) { "Invalid sender Friend Code" }
        envelope.expectedFriendCode?.let {
            require(PuppyPlayerIdentity.isValidFriendCode(it)) { "Invalid expected Friend Code" }
        }
        require(envelope.sdp.isNotBlank() && envelope.sdp.length <= 196_608) { "Invalid SDP payload" }
        validateTimeWindow(envelope.createdAtMs, envelope.expiresAtMs, nowMs)
    }

    private fun validateRecovery(envelope: ExchangeRecoveryEnvelope, nowMs: Long) {
        require(envelope.protocolVersion == PROTOCOL_VERSION) { "Unsupported Puppy Exchange recovery protocol" }
        require(envelope.transactionId.startsWith("XT-") && envelope.transactionId.length in 12..80) { "Invalid transaction ID" }
        require(envelope.nonce.length in 16..128) { "Invalid recovery nonce" }
        require(PuppyPlayerIdentity.isValidPlayerId(envelope.senderPlayerId)) { "Invalid recovery sender" }
        require(PuppyPlayerIdentity.isValidPlayerId(envelope.peerPlayerId)) { "Invalid recovery peer" }
        require(envelope.offerAHash.matches(Regex("^[0-9a-f]{64}$"))) { "Invalid offer A hash" }
        require(envelope.offerBHash.matches(Regex("^[0-9a-f]{64}$"))) { "Invalid offer B hash" }
        validateTimeWindow(envelope.createdAtMs, envelope.expiresAtMs, nowMs)
    }

    private fun validateTimeWindow(createdAtMs: Long, expiresAtMs: Long, nowMs: Long) {
        require(createdAtMs > 0L && expiresAtMs > createdAtMs) { "Invalid code timestamps" }
        require(expiresAtMs - createdAtMs <= CODE_TTL_MS) { "Code lifetime exceeds ten minutes" }
        require(nowMs <= expiresAtMs) { "Puppy Exchange code expired" }
        require(createdAtMs <= nowMs + 60_000L) { "Puppy Exchange code timestamp is in the future" }
    }

    private fun randomToken(bytes: Int): String {
        val raw = ByteArray(bytes).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    private fun encodePayload(raw: ByteArray): String {
        require(raw.size <= MAX_DECODED_BYTES) { "Puppy Exchange payload is too large" }
        val compressed = ByteArrayOutputStream().use { output ->
            DeflaterOutputStream(output).use { it.write(raw) }
            output.toByteArray()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    private fun decodePayload(encoded: String): ByteArray {
        require(encoded.isNotBlank()) { "Empty Puppy Exchange code" }
        val compressed = try {
            Base64.getUrlDecoder().decode(encoded)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed Puppy Exchange code", error)
        }
        val result = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(result.size() + read <= MAX_DECODED_BYTES) { "Decoded Puppy Exchange payload is too large" }
                result.write(buffer, 0, read)
            }
        }
        return result.toByteArray()
    }
}

/** Local replay guard. Nonces are marked consumed only after a successful connection/recovery use. */
internal class ConsumedNonceStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("puppy_exchange_consumed_nonces_v1", Context.MODE_PRIVATE)

    fun isConsumed(nonce: String): Boolean = nonce in prefs.getStringSet(KEY_NONCES, emptySet()).orEmpty()

    fun markConsumed(nonce: String) {
        require(nonce.length in 16..128) { "Invalid nonce" }
        val next = prefs.getStringSet(KEY_NONCES, emptySet()).orEmpty().toMutableSet()
        next += nonce
        while (next.size > MAX_NONCES) next.remove(next.first())
        check(prefs.edit().putStringSet(KEY_NONCES, next).commit()) { "Unable to persist replay protection" }
    }

    companion object {
        private const val KEY_NONCES = "consumed"
        private const val MAX_NONCES = 512
    }
}
