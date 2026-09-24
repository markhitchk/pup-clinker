package com.harleytg.puppyclicker

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class PupEyeTransferVerification(
    val accepted: Boolean,
    val migrationRequired: Boolean = false,
    val generation: Long = 0L,
    val message: String? = null
)

/**
 * PupEye's device-bound authority.
 *
 * The private signing key is generated inside Android Keystore and is never exported.
 * Portable saves carry only the public key. A valid signature therefore proves that a save
 * was produced by this Puppy Clicker installation, while the installation ID / Player ID /
 * Friend Code checks prevent a different device from silently adopting the save.
 *
 * Cross-device migration intentionally requires a future support-authorized migration flow.
 */
internal object PupEyeAuthority {
    private const val AUTHORITY_STATE_FILE = "pupeye/authority_state_v2.pup"
    private const val SIGNING_KEY_ALIAS = "pupeye_install_signing_ec_v2"
    private const val PROOF_SCHEMA = 2
    private const val STATE_SCHEMA = 2
    private const val MAX_EVENTS = 32
    private const val MAX_HARD_FLAGS = 32

    @Synchronized
    fun installationId(context: Context): String = loadState(context).getString("installationId")

    @Synchronized
    fun currentGeneration(context: Context): Long =
        loadState(context).optLong("generation", 1L).coerceAtLeast(1L)

    @Synchronized
    fun supportInstallationCode(context: Context): String {
        val installId = installationId(context)
        val keyId = publicKeyId(signingKeyPair().public.encoded)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("$installId|$keyId").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
            .take(16)
        return "PUP-" + digest.chunked(4).joinToString("-")
    }

    @Synchronized
    fun signedEnvelope(
        context: Context,
        action: String,
        payload: JSONObject
    ): JSONObject {
        val cleanAction = action.trim().lowercase().take(48)
        require(cleanAction.matches(Regex("[a-z0-9_-]+"))) { "Invalid Pupeye request action" }

        val keyPair = signingKeyPair()
        val installId = installationId(context)
        val keyId = publicKeyId(keyPair.public.encoded)
        val generation = currentGeneration(context)
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(
                requestSignatureBytes(
                    action = cleanAction,
                    installId = installId,
                    keyId = keyId,
                    generation = generation,
                    timestamp = timestamp,
                    nonce = nonce,
                    payload = payload
                )
            )
            sign()
        }

        return JSONObject().apply {
            put("schema", 1)
            put("action", cleanAction)
            put("installationId", installId)
            put("deviceKeyId", keyId)
            put("generation", generation)
            put("timestampEpochMs", timestamp)
            put("nonce", nonce)
            put("publicKey", b64(keyPair.public.encoded))
            put("payload", JSONObject(payload.toString()))
            put("signature", b64(signature))
        }
    }

    @Synchronized
    fun noteSealedState(context: Context) {
        val state = loadState(context)
        val next = state.optLong("generation", 1L)
            .coerceAtLeast(1L)
            .let { if (it == Long.MAX_VALUE) Long.MAX_VALUE else it + 1L }
        state.put("generation", next)
        state.put("lastSealedAtEpochMs", System.currentTimeMillis())
        saveState(context, state)
        SupabasePupEyeClient.queueSaveCheckpoint(context, "seal")
    }

    internal fun transferPayloadHash(payloadWithoutProof: JSONObject): String {
        require(!payloadWithoutProof.has("pupeye")) {
            "Pupeye transfer payload hash must exclude the proof wrapper"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson(payloadWithoutProof).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Synchronized
    fun createTransferProof(context: Context, payloadWithoutProof: JSONObject): JSONObject {
        require(!payloadWithoutProof.has("pupeye")) { "Transfer proof must be added last" }
        val keyPair = signingKeyPair()
        val installId = installationId(context)
        val keyId = publicKeyId(keyPair.public.encoded)
        val generation = currentGeneration(context)
        val saveId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(
                signatureBytes(
                    payload = payloadWithoutProof,
                    installId = installId,
                    keyId = keyId,
                    generation = generation,
                    saveId = saveId,
                    createdAt = createdAt
                )
            )
            sign()
        }

        return JSONObject().apply {
            put("schema", PROOF_SCHEMA)
            put("installationId", installId)
            put("deviceKeyId", keyId)
            put("generation", generation)
            put("saveId", saveId)
            put("createdAtEpochMs", createdAt)
            put("sourceSdkInt", Build.VERSION.SDK_INT)
            put("publicKey", b64(keyPair.public.encoded))
            put("signature", b64(signature))
        }
    }

    @Synchronized
    fun verifyTransfer(context: Context, payload: JSONObject): PupEyeTransferVerification {
        val proof = payload.optJSONObject("pupeye")
            ?: return PupEyeTransferVerification(
                accepted = false,
                migrationRequired = true,
                message = "This backup predates Pupeye authenticated ownership. Contact Puppy Clicker Support to migrate it."
            )

        return runCatching {
            require(proof.optInt("schema") == PROOF_SCHEMA) {
                "Unsupported Pupeye save proof"
            }

            val installId = proof.getString("installationId")
            val keyId = proof.getString("deviceKeyId")
            val generation = proof.getLong("generation").coerceAtLeast(1L)
            val saveId = proof.getString("saveId")
            val createdAt = proof.getLong("createdAtEpochMs")
            val publicKeyBytes = Base64.decode(proof.getString("publicKey"), Base64.NO_WRAP)
            val signatureValue = Base64.decode(proof.getString("signature"), Base64.NO_WRAP)

            require(publicKeyId(publicKeyBytes) == keyId) {
                "Pupeye device-key fingerprint mismatch"
            }

            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val unsignedPayload = JSONObject(payload.toString()).apply { remove("pupeye") }
            val validSignature = Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(
                    signatureBytes(
                        payload = unsignedPayload,
                        installId = installId,
                        keyId = keyId,
                        generation = generation,
                        saveId = saveId,
                        createdAt = createdAt
                    )
                )
                verify(signatureValue)
            }
            if (!validSignature) {
                recordEvent(context, "SAVE_SIGNATURE_INVALID", "Portable save signature failed verification")
                return PupEyeTransferVerification(
                    accepted = false,
                    message = "Pupeye rejected this save because its authenticated signature is invalid."
                )
            }

            val identity = payload.optJSONObject("identity")
                ?: error("Encrypted save is missing player identity")
            val importedPlayerId = identity.optString("playerId")
            val importedFriendCode = identity.optString("friendCode")
            val localPlayerId = PuppyPlayerIdentity.playerId(context)
            val localFriendCode = PuppyPlayerIdentity.friendCode(context)
            val localInstallId = installationId(context)
            val localKeyId = publicKeyId(signingKeyPair().public.encoded)

            if (
                importedPlayerId != localPlayerId ||
                importedFriendCode != localFriendCode ||
                installId != localInstallId ||
                keyId != localKeyId
            ) {
                recordEvent(
                    context,
                    "DEVICE_TRANSFER_REQUIRED",
                    "Authenticated save belongs to another registered Puppy Clicker installation"
                )
                return PupEyeTransferVerification(
                    accepted = false,
                    migrationRequired = true,
                    generation = generation,
                    message = "This save belongs to another registered Puppy Clicker installation. Contact Puppy Clicker Support to authorize a device transfer."
                )
            }

            val localGeneration = currentGeneration(context)
            if (generation < localGeneration) {
                recordEvent(
                    context,
                    "SAVE_ROLLBACK",
                    "Rejected generation $generation while local generation is $localGeneration"
                )
                return PupEyeTransferVerification(
                    accepted = false,
                    generation = generation,
                    message = "Pupeye rejected an older save generation. Contact Support if you need recovery from an older backup."
                )
            }

            PupEyeTransferVerification(
                accepted = true,
                generation = generation,
                message = "Authenticated Puppy Clicker save verified by Pupeye."
            )
        }.getOrElse { error ->
            recordEvent(
                context,
                "SAVE_PROOF_INVALID",
                error.message ?: "Pupeye transfer proof could not be validated"
            )
            PupEyeTransferVerification(
                accepted = false,
                message = "Pupeye rejected this save because its authenticated ownership proof is invalid."
            )
        }
    }

    @Synchronized
    fun acceptVerifiedTransfer(context: Context, generation: Long) {
        val state = loadState(context)
        val current = state.optLong("generation", 1L).coerceAtLeast(1L)
        val accepted = maxOf(current, generation)
        state.put(
            "generation",
            if (accepted == Long.MAX_VALUE) Long.MAX_VALUE else accepted + 1L
        )
        state.put("lastAcceptedTransferAtEpochMs", System.currentTimeMillis())
        saveState(context, state)
    }

    @Synchronized
    fun recordEvent(context: Context, code: String, detail: String) {
        val state = loadState(context)
        val events = state.optJSONArray("events") ?: JSONArray()
        events.put(
            JSONObject().apply {
                put("code", sanitizeCode(code))
                put("detail", detail.take(220))
                put("atEpochMs", System.currentTimeMillis())
            }
        )
        while (events.length() > MAX_EVENTS) {
            events.remove(0)
        }
        state.put("events", events)
        saveState(context, state)
    }

    @Synchronized
    fun recordHardFlag(context: Context, code: String, detail: String) {
        val state = loadState(context)
        val flags = jsonStringSet(state.optJSONArray("hardFlags"))
            .toMutableSet()
        flags += sanitizeCode(code)
        state.put("hardFlags", JSONArray(flags.sorted().take(MAX_HARD_FLAGS)))
        val events = state.optJSONArray("events") ?: JSONArray()
        events.put(
            JSONObject().apply {
                put("code", sanitizeCode(code))
                put("detail", detail.take(220))
                put("hard", true)
                put("atEpochMs", System.currentTimeMillis())
            }
        )
        while (events.length() > MAX_EVENTS) {
            events.remove(0)
        }
        state.put("events", events)
        saveState(context, state)
    }

    @Synchronized
    fun hardFlags(context: Context): Set<String> = runCatching {
        jsonStringSet(loadState(context).optJSONArray("hardFlags"))
    }.getOrElse { setOf("AUTHORITY_STATE_UNREADABLE") }

    fun isGameplayAllowed(context: Context): Boolean {
        if (hardFlags(context).isNotEmpty()) return false
        if (!PupEyeEconomyLedger.verify(context)) return false
        return SupabasePupEyeClient.serverAllowsProtectedGameplay(context)
    }

    private fun requestSignatureBytes(
        action: String,
        installId: String,
        keyId: String,
        generation: Long,
        timestamp: Long,
        nonce: String,
        payload: JSONObject
    ): ByteArray {
        val signed = buildString {
            append("PuppyClicker/PupEye/request/v1\n")
            append(action).append('\n')
            append(installId).append('\n')
            append(keyId).append('\n')
            append(generation).append('\n')
            append(timestamp).append('\n')
            append(nonce).append('\n')
            append(canonicalJson(payload))
        }
        return signed.toByteArray(Charsets.UTF_8)
    }

    private fun signatureBytes(
        payload: JSONObject,
        installId: String,
        keyId: String,
        generation: Long,
        saveId: String,
        createdAt: Long
    ): ByteArray {
        val signed = buildString {
            append("PuppyClicker/PupEye/transfer/v2\n")
            append(installId).append('\n')
            append(keyId).append('\n')
            append(generation).append('\n')
            append(saveId).append('\n')
            append(createdAt).append('\n')
            append(canonicalJson(payload))
        }
        return signed.toByteArray(Charsets.UTF_8)
    }

    private fun signingKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingPrivate = keyStore.getKey(SIGNING_KEY_ALIAS, null) as? java.security.PrivateKey
        val existingPublic = keyStore.getCertificate(SIGNING_KEY_ALIAS)?.publicKey
        if (existingPrivate != null && existingPublic != null) {
            return KeyPair(existingPublic, existingPrivate)
        }

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(
                SIGNING_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKeyPair()
    }

    private fun publicKeyId(encoded: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun loadState(context: Context): JSONObject {
        val file = File(context.noBackupFilesDir, AUTHORITY_STATE_FILE)
        if (!file.isFile) {
            return JSONObject().apply {
                put("schema", STATE_SCHEMA)
                put("installationId", UUID.randomUUID().toString())
                put("generation", 1L)
                put("hardFlags", JSONArray())
                put("events", JSONArray())
            }.also { saveState(context, it) }
        }

        val plain = PuppySaveCrypto.decryptDevice(file.readBytes())
        val root = JSONObject(plain.toString(Charsets.UTF_8))
        require(root.optInt("schema") == STATE_SCHEMA) { "Invalid Pupeye authority state" }
        require(root.optString("installationId").isNotBlank()) { "Missing Pupeye installation ID" }
        return root
    }

    private fun saveState(context: Context, state: JSONObject) {
        val target = File(context.noBackupFilesDir, AUTHORITY_STATE_FILE)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeBytes(
            PuppySaveCrypto.encryptDevice(state.toString().toByteArray(Charsets.UTF_8))
        )
        if (target.exists() && !target.delete()) {
            error("Unable to replace Pupeye authority state")
        }
        if (!temp.renameTo(target)) {
            target.writeBytes(temp.readBytes())
            temp.delete()
        }
    }

    private fun jsonStringSet(array: JSONArray?): Set<String> = buildSet {
        if (array == null) return@buildSet
        for (index in 0 until array.length()) {
            array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }.take(MAX_HARD_FLAGS).toSet()

    private fun sanitizeCode(raw: String): String =
        raw.uppercase()
            .map { if (it in 'A'..'Z' || it in '0'..'9' || it == '_') it else '_' }
            .joinToString("")
            .take(48)
            .ifBlank { "UNKNOWN" }

    private fun canonicalJson(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted()
            .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                JSONObject.quote(key) + ":" + canonicalJson(value.get(key))
            }
        is JSONArray -> (0 until value.length())
            .joinToString(prefix = "[", postfix = "]", separator = ",") { index ->
                canonicalJson(value.get(index))
            }
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)
}

internal object PupEyeEconomyLedger {
    private const val LEDGER_FILE = "pupeye/economy_ledger_v2.pup"
    private const val LEDGER_SCHEMA = 2
    private const val MAX_RECORDS = 2048

    @Synchronized
    fun verify(context: Context): Boolean = runCatching {
        loadAndVerify(context)
        true
    }.getOrElse { error ->
        runCatching {
            PupEyeAuthority.recordHardFlag(
                context,
                "ECONOMY_LEDGER_INVALID",
                error.message ?: "Pupeye economy ledger authentication failed"
            )
        }
        false
    }

    @Synchronized
    fun record(
        context: Context,
        transactionId: String,
        source: String,
        details: String
    ): Boolean = runCatching {
        require(transactionId.isNotBlank()) { "Missing Pupeye transaction ID" }
        val root = loadAndVerify(context)
        val records = root.getJSONArray("records")

        for (index in 0 until records.length()) {
            if (records.getJSONObject(index).optString("transactionId") == transactionId) {
                PupEyeAuthority.recordHardFlag(
                    context,
                    "DUPLICATE_TRANSACTION",
                    "Duplicate authenticated economy transaction: $transactionId"
                )
                return false
            }
        }

        val previousHash = if (records.length() == 0) {
            root.optString("anchorHash")
        } else {
            records.getJSONObject(records.length() - 1).getString("hash")
        }
        val at = System.currentTimeMillis()
        val generation = PupEyeAuthority.currentGeneration(context)
        val cleanSource = source.uppercase().take(40)
        val cleanDetails = details.take(360)
        val hash = recordHash(
            previousHash = previousHash,
            transactionId = transactionId,
            source = cleanSource,
            details = cleanDetails,
            at = at,
            generation = generation
        )

        records.put(
            JSONObject().apply {
                put("transactionId", transactionId)
                put("source", cleanSource)
                put("details", cleanDetails)
                put("atEpochMs", at)
                put("generation", generation)
                put("previousHash", previousHash)
                put("hash", hash)
            }
        )

        while (records.length() > MAX_RECORDS) {
            val removed = records.getJSONObject(0)
            root.put("anchorHash", removed.getString("hash"))
            records.remove(0)
        }

        root.put("records", records)
        save(context, root)
        SupabasePupEyeClient.queueEconomyTransaction(
            context = context,
            transactionId = transactionId,
            source = cleanSource,
            details = cleanDetails,
            generation = generation
        )
        true
    }.getOrElse { error ->
        runCatching {
            PupEyeAuthority.recordHardFlag(
                context,
                "ECONOMY_LEDGER_WRITE_FAILED",
                error.message ?: "Unable to write Pupeye economy ledger"
            )
        }
        false
    }

    fun newTransactionId(prefix: String): String =
        prefix.lowercase()
            .map { if (it in 'a'..'z' || it in '0'..'9' || it == '_') it else '_' }
            .joinToString("")
            .take(24)
            .ifBlank { "txn" } + ":" + UUID.randomUUID().toString()

    fun recordGacha(
        context: Context,
        transactionId: String,
        payment: PuppyGachaPayment,
        puppyId: String,
        costTreats: Long,
        costTickets: Int
    ): Boolean = record(
        context = context,
        transactionId = transactionId,
        source = "GACHA",
        details = "payment=${payment.name};puppy=$puppyId;costTreats=$costTreats;costTickets=$costTickets"
    )

    fun recordCasinoResult(
        context: Context,
        completedBefore: List<String>,
        result: PuppyCasinoTransactionResult
    ): Boolean {
        if (!result.success) return true
        val active = result.activeRound
        if (active != null) {
            return record(
                context = context,
                transactionId = "casino:${active.roundId}:${active.state.name}:${UUID.randomUUID()}",
                source = "CASINO",
                details = "game=${active.game.name};state=${active.state.name};wager=${active.wagerTreats};payout=${active.payoutTreats}"
            )
        }

        val completedBeforeSet = completedBefore.toSet()
        val newlyCompleted = result.completedRoundIds.filterNot(completedBeforeSet::contains)
        if (newlyCompleted.isEmpty()) return true

        return newlyCompleted.all { roundId ->
            record(
                context = context,
                transactionId = "casino:$roundId:COMPLETED",
                source = "CASINO",
                details = "completed=true;treats=${result.state.treats};chips=${result.state.casinoChips}"
            )
        }
    }

    private fun loadAndVerify(context: Context): JSONObject {
        val file = File(context.noBackupFilesDir, LEDGER_FILE)
        if (!file.isFile) {
            return JSONObject().apply {
                put("schema", LEDGER_SCHEMA)
                put("anchorHash", "")
                put("records", JSONArray())
            }
        }

        val plain = PuppySaveCrypto.decryptDevice(file.readBytes())
        val root = JSONObject(plain.toString(Charsets.UTF_8))
        require(root.optInt("schema") == LEDGER_SCHEMA) { "Invalid Pupeye ledger schema" }
        val records = root.optJSONArray("records") ?: error("Missing Pupeye ledger records")

        var expectedPrevious: String? = null
        for (index in 0 until records.length()) {
            val record = records.getJSONObject(index)
            val previous = record.getString("previousHash")
            if (expectedPrevious != null) {
                require(previous == expectedPrevious) { "Pupeye ledger chain mismatch" }
            }
            val expectedHash = recordHash(
                previousHash = previous,
                transactionId = record.getString("transactionId"),
                source = record.getString("source"),
                details = record.getString("details"),
                at = record.getLong("atEpochMs"),
                generation = record.getLong("generation")
            )
            require(
                MessageDigest.isEqual(
                    expectedHash.toByteArray(Charsets.US_ASCII),
                    record.getString("hash").toByteArray(Charsets.US_ASCII)
                )
            ) { "Pupeye ledger record authentication mismatch" }
            expectedPrevious = record.getString("hash")
        }
        return root
    }

    private fun save(context: Context, root: JSONObject) {
        val target = File(context.noBackupFilesDir, LEDGER_FILE)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeBytes(
            PuppySaveCrypto.encryptDevice(root.toString().toByteArray(Charsets.UTF_8))
        )
        if (target.exists() && !target.delete()) {
            error("Unable to replace Pupeye economy ledger")
        }
        if (!temp.renameTo(target)) {
            target.writeBytes(temp.readBytes())
            temp.delete()
        }
    }

    private fun recordHash(
        previousHash: String,
        transactionId: String,
        source: String,
        details: String,
        at: Long,
        generation: Long
    ): String {
        val material = buildString {
            append("PuppyClicker/PupEye/economy/v2\n")
            append(previousHash).append('\n')
            append(transactionId).append('\n')
            append(source).append('\n')
            append(details).append('\n')
            append(at).append('\n')
            append(generation)
        }.toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(material)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
