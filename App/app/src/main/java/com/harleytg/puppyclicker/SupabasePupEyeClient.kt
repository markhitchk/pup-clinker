package com.harleytg.puppyclicker

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class SupabaseDiscordAuthResult(
    val account: DiscordPlayerAccount,
    val guildAccess: DiscordGuildAccess?,
    val message: String
)

internal data class PupEyeBackendState(
    val state: String,
    val lastVerifiedAtMs: Long,
    val lastError: String?
) {
    val blocksProtectedGameplay: Boolean
        get() = state in setOf("MIGRATION_REQUIRED", "REVIEW_REQUIRED", "BLOCKED")
}

/**
 * Supabase transport for Puppy Clicker account authority and PupEye.
 *
 * The APK contains only the project's publishable key. The server-side secret key remains in
 * Supabase Edge Functions. Every state-changing request is additionally signed by the installation's
 * non-exportable Android Keystore key through [PupEyeAuthority].
 */
internal object SupabasePupEyeClient {
    private const val SESSION_FILE = "pupeye/supabase_session_v1.pup"
    private const val STATUS_PREFS = "pupeye_supabase_status_v1"
    private const val KEY_STATE = "state"
    private const val KEY_LAST_VERIFIED = "last_verified_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val CHECKPOINT_THROTTLE_MS = 30_000L
    private const val MAX_RESPONSE_BYTES = 256 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastCheckpointQueuedAt = AtomicLong(0L)

    val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    val publishableKey: String
        get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

    fun isConfigured(): Boolean =
        baseUrl.startsWith("https://") &&
            publishableKey.startsWith("sb_publishable_")

    fun initialize(context: Context) {
        if (!isConfigured()) return
        val app = context.applicationContext
        scope.launch {
            runCatching {
                ensureRegistered(app)
                sendSaveCheckpoint(app, reason = "startup")
            }.onFailure { error ->
                noteTransientError(app, error.message ?: "Supabase initialization failed")
            }
        }
    }

    fun backendState(context: Context): PupEyeBackendState {
        val prefs = context.applicationContext
            .getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
        return PupEyeBackendState(
            state = prefs.getString(KEY_STATE, "UNREGISTERED").orEmpty().ifBlank { "UNREGISTERED" },
            lastVerifiedAtMs = prefs.getLong(KEY_LAST_VERIFIED, 0L).coerceAtLeast(0L),
            lastError = prefs.getString(KEY_LAST_ERROR, null)
        )
    }

    fun serverAllowsProtectedGameplay(context: Context): Boolean =
        !backendState(context).blocksProtectedGameplay

    fun hasSession(context: Context): Boolean = readSession(context) != null

    suspend fun authenticateDiscord(
        context: Context,
        discordAccessToken: String
    ): SupabaseDiscordAuthResult = withContext(Dispatchers.IO) {
        require(discordAccessToken.isNotBlank()) { "Discord did not return an access token" }
        val app = context.applicationContext
        val session = ensureRegistered(app)
            ?: error(
                when (backendState(app).state) {
                    "MIGRATION_REQUIRED" ->
                        "Pupeye requires Support authorization before this installation can use the existing player."
                    else -> "Pupeye could not register this installation with the account service."
                }
            )

        val payload = JSONObject().apply {
            put("discordAccessToken", discordAccessToken)
        }
        val envelope = PupEyeAuthority.signedEnvelope(
            context = app,
            action = "auth-discord",
            payload = payload
        )
        val response = invoke(
            functionName = "pupeye-auth-discord",
            envelope = envelope,
            sessionToken = session.token
        )
        if (response.status !in 200..299) {
            handleAuthoritativeFailure(app, response)
            error(response.message("Supabase Discord verification failed"))
        }

        val accountJson = response.body.optJSONObject("discord")
            ?: error("Pupeye auth response is missing the Discord account")
        val account = DiscordPlayerAccount(
            id = accountJson.getString("id"),
            username = accountJson.getString("username"),
            globalName = accountJson.optString("globalName").takeIf { it.isNotBlank() && it != "null" },
            avatarHash = accountJson.optString("avatarHash").takeIf { it.isNotBlank() && it != "null" },
            email = accountJson.optString("email").takeIf { it.isNotBlank() && it != "null" }
        )
        val role = response.body.optString("guildRole")
            .takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { DiscordGuildRole.valueOf(it) }.getOrNull() }
        val guildAccess = role?.let {
            DiscordGuildAccess(
                guildId = DiscordSignupAuth.GUILD_ID,
                role = it,
                verifiedAtMs = System.currentTimeMillis()
            )
        }

        markConnected(app)
        SupabaseDiscordAuthResult(
            account = account,
            guildAccess = guildAccess,
            message = if (guildAccess != null) {
                "Discord verified by Pupeye: ${guildAccess.role.label}."
            } else {
                "Discord verified by Pupeye."
            }
        )
    }

    fun unlinkDiscordAsync(context: Context) {
        if (!isConfigured()) return
        val app = context.applicationContext
        scope.launch {
            val session = ensureRegistered(app) ?: return@launch
            val envelope = PupEyeAuthority.signedEnvelope(
                context = app,
                action = "unlink-discord",
                payload = JSONObject()
            )
            val response = invoke(
                functionName = "pupeye-auth-discord",
                envelope = envelope,
                sessionToken = session.token
            )
            if (response.status !in 200..299) {
                noteTransientError(app, response.message("Unable to unlink Discord from Pupeye"))
            } else {
                markConnected(app)
            }
        }
    }

    fun queueSaveCheckpoint(context: Context, reason: String) {
        if (!isConfigured()) return
        val now = System.currentTimeMillis()
        val previous = lastCheckpointQueuedAt.get()
        if (now - previous < CHECKPOINT_THROTTLE_MS) return
        if (!lastCheckpointQueuedAt.compareAndSet(previous, now)) return

        val app = context.applicationContext
        scope.launch {
            runCatching {
                ensureRegistered(app)
                sendSaveCheckpoint(app, reason.take(40))
            }.onFailure { error ->
                noteTransientError(app, error.message ?: "Pupeye save checkpoint failed")
            }
        }
    }

    fun queueSaveAttestation(
        context: Context,
        saveId: String,
        generation: Long,
        payloadHashSha256: String
    ) {
        if (!isConfigured()) return
        require(saveId.isNotBlank()) { "Missing PupEye save ID" }
        require(generation >= 1L) { "Invalid PupEye save generation" }
        require(payloadHashSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Invalid PupEye save SHA-256"
        }

        val app = context.applicationContext
        scope.launch {
            runCatching {
                val session = ensureRegistered(app) ?: return@runCatching
                val payload = JSONObject().apply {
                    put("saveId", saveId)
                    put("generation", generation)
                    put("payloadHashSha256", payloadHashSha256)
                }
                val envelope = PupEyeAuthority.signedEnvelope(
                    context = app,
                    action = "save-attestation",
                    payload = payload
                )
                val response = invoke(
                    functionName = "pupeye-save-attestation",
                    envelope = envelope,
                    sessionToken = session.token
                )
                if (response.status !in 200..299) {
                    handleAuthoritativeFailure(app, response)
                    if (response.status !in setOf(
                            HttpURLConnection.HTTP_CONFLICT,
                            HttpURLConnection.HTTP_FORBIDDEN
                        )
                    ) {
                        noteTransientError(
                            app,
                            response.message("Unable to attest Puppy Clicker save")
                        )
                    }
                } else {
                    markConnected(app)
                }
            }.onFailure { error ->
                noteTransientError(
                    app,
                    error.message ?: "PupEye save attestation failed"
                )
            }
        }
    }

    fun queueEconomyTransaction(
        context: Context,
        transactionId: String,
        source: String,
        details: String,
        generation: Long
    ) {
        if (!isConfigured()) return
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val session = ensureRegistered(app) ?: return@runCatching
                val payload = JSONObject().apply {
                    put("transactionId", transactionId)
                    put("source", source.take(40))
                    put("details", details.take(360))
                    put("generation", generation.coerceAtLeast(1L))
                }
                val envelope = PupEyeAuthority.signedEnvelope(
                    context = app,
                    action = "economy-transaction",
                    payload = payload
                )
                val response = invoke(
                    functionName = "pupeye-transaction",
                    envelope = envelope,
                    sessionToken = session.token
                )
                if (response.status !in 200..299) {
                    handleAuthoritativeFailure(app, response)
                    if (response.status == HttpURLConnection.HTTP_CONFLICT) {
                        PupEyeAuthority.recordEvent(
                            app,
                            "SERVER_TRANSACTION_REJECTED",
                            response.message("Supabase rejected a protected economy transaction")
                        )
                    } else {
                        noteTransientError(
                            app,
                            response.message("Unable to sync protected economy transaction")
                        )
                    }
                } else {
                    markConnected(app)
                }
            }.onFailure { error ->
                noteTransientError(app, error.message ?: "Pupeye economy sync failed")
            }
        }
    }

    fun clearSession(context: Context) {
        runCatching { sessionFile(context).delete() }
        context.applicationContext
            .getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, "UNREGISTERED")
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private fun sendSaveCheckpoint(context: Context, reason: String) {
        val session = ensureRegistered(context) ?: return
        val payload = JSONObject().apply {
            put("generation", PupEyeAuthority.currentGeneration(context))
            put("supportCode", PupEyeAuthority.supportInstallationCode(context))
            put("reason", reason)
            put("hardFlags", JSONArray(PupEyeAuthority.hardFlags(context).sorted()))
        }
        val envelope = PupEyeAuthority.signedEnvelope(
            context = context,
            action = "save-checkpoint",
            payload = payload
        )
        val response = invoke(
            functionName = "pupeye-sync",
            envelope = envelope,
            sessionToken = session.token
        )
        if (response.status !in 200..299) {
            handleAuthoritativeFailure(context, response)
            if (response.status !in setOf(HttpURLConnection.HTTP_CONFLICT, HttpURLConnection.HTTP_FORBIDDEN)) {
                noteTransientError(context, response.message("Pupeye save checkpoint could not be verified"))
            }
        } else {
            markConnected(context)
        }
    }

    private fun ensureRegistered(context: Context): BackendSession? {
        readSession(context)?.let { return it }
        if (!isConfigured()) return null

        val payload = JSONObject().apply {
            put("playerId", PuppyPlayerIdentity.playerId(context))
            put("friendCode", PuppyPlayerIdentity.friendCode(context))
            put("username", PuppyPlayerIdentity.username(context))
            put("supportCode", PupEyeAuthority.supportInstallationCode(context))
            put("deviceModel", PuppyPlayerIdentity.deviceModel())
            put("platform", "android")
            put("appVersion", BuildConfig.VERSION_NAME)
        }
        val envelope = PupEyeAuthority.signedEnvelope(
            context = context,
            action = "register",
            payload = payload
        )
        val response = invoke(
            functionName = "pupeye-register",
            envelope = envelope,
            sessionToken = null
        )
        if (response.status !in 200..299) {
            handleAuthoritativeFailure(context, response)
            if (response.status !in setOf(HttpURLConnection.HTTP_CONFLICT, HttpURLConnection.HTTP_FORBIDDEN)) {
                noteTransientError(context, response.message("Pupeye registration failed"))
            }
            return null
        }

        val token = response.body.optString("sessionToken").takeIf { it.isNotBlank() }
            ?: error("Pupeye registration returned no session token")
        val expiresAt = response.body.optLong("expiresAtEpochMs", 0L)
        require(expiresAt > System.currentTimeMillis()) { "Pupeye registration returned an expired session" }

        val session = BackendSession(
            token = token,
            expiresAtEpochMs = expiresAt,
            backendPlayerId = response.body.optString("backendPlayerId"),
            backendInstallationId = response.body.optString("backendInstallationId")
        )
        writeSession(context, session)
        markConnected(context)
        return session
    }

    private fun handleAuthoritativeFailure(context: Context, response: ApiResponse) {
        val code = response.body.optString("code")
        val nextState = when (code) {
            "DEVICE_MIGRATION_REQUIRED" -> "MIGRATION_REQUIRED"
            "SAVE_ROLLBACK", "DUPLICATE_TRANSACTION", "TRANSACTION_CONFLICT" -> "REVIEW_REQUIRED"
            "PLAYER_BLOCKED", "INSTALLATION_REVOKED" -> "BLOCKED"
            else -> null
        } ?: return

        context.applicationContext
            .getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, nextState)
            .putLong(KEY_LAST_VERIFIED, System.currentTimeMillis())
            .putString(KEY_LAST_ERROR, response.message("Pupeye server rejected the request").take(220))
            .apply()

        if (nextState == "BLOCKED") {
            clearSessionFileOnly(context)
        }
    }

    private fun markConnected(context: Context) {
        context.applicationContext
            .getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, "CONNECTED")
            .putLong(KEY_LAST_VERIFIED, System.currentTimeMillis())
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private fun noteTransientError(context: Context, message: String) {
        val prefs = context.applicationContext
            .getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_STATE, "UNREGISTERED").orEmpty()
        val state = if (existing in setOf("MIGRATION_REQUIRED", "REVIEW_REQUIRED", "BLOCKED")) {
            existing
        } else {
            "OFFLINE"
        }
        prefs.edit()
            .putString(KEY_STATE, state)
            .putString(KEY_LAST_ERROR, message.take(220))
            .apply()
    }

    private fun invoke(
        functionName: String,
        envelope: JSONObject,
        sessionToken: String?
    ): ApiResponse {
        require(isConfigured()) { "Supabase is not configured" }
        val connection = (
            URL("$baseUrl/functions/v1/$functionName")
                .openConnection() as HttpURLConnection
            ).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", publishableKey)
            if (!sessionToken.isNullOrBlank()) {
                setRequestProperty("X-Pupeye-Session", sessionToken)
            }
        }

        val bytes = envelope.toString().toByteArray(StandardCharsets.UTF_8)
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }

        val status = connection.responseCode
        val stream: InputStream? =
            if (status in 200..299) connection.inputStream else connection.errorStream
        val bodyText = stream?.use(::readBoundedText).orEmpty()
        connection.disconnect()

        val body = runCatching {
            if (bodyText.isBlank()) JSONObject() else JSONObject(bodyText)
        }.getOrElse {
            JSONObject().put("message", bodyText.take(500))
        }
        return ApiResponse(status = status, body = body)
    }

    private fun readBoundedText(input: InputStream): String {
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        val out = StringBuilder()
        val chars = CharArray(4096)
        while (out.length <= MAX_RESPONSE_BYTES) {
            val count = reader.read(chars)
            if (count < 0) break
            out.append(chars, 0, count)
        }
        require(out.length <= MAX_RESPONSE_BYTES) { "Supabase response exceeded 256 KB" }
        return out.toString()
    }

    private fun readSession(context: Context): BackendSession? = runCatching {
        val file = sessionFile(context)
        if (!file.isFile) return null
        val plain = PuppySaveCrypto.decryptDevice(file.readBytes())
        val json = JSONObject(plain.toString(Charsets.UTF_8))
        val session = BackendSession(
            token = json.getString("token"),
            expiresAtEpochMs = json.getLong("expiresAtEpochMs"),
            backendPlayerId = json.optString("backendPlayerId"),
            backendInstallationId = json.optString("backendInstallationId")
        )
        if (session.expiresAtEpochMs <= System.currentTimeMillis()) {
            file.delete()
            null
        } else {
            session
        }
    }.getOrElse {
        clearSessionFileOnly(context)
        null
    }

    private fun writeSession(context: Context, session: BackendSession) {
        val file = sessionFile(context)
        file.parentFile?.mkdirs()
        val json = JSONObject().apply {
            put("token", session.token)
            put("expiresAtEpochMs", session.expiresAtEpochMs)
            put("backendPlayerId", session.backendPlayerId)
            put("backendInstallationId", session.backendInstallationId)
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(
            PuppySaveCrypto.encryptDevice(json.toString().toByteArray(Charsets.UTF_8))
        )
        if (file.exists() && !file.delete()) error("Unable to replace Pupeye backend session")
        if (!temp.renameTo(file)) {
            file.writeBytes(temp.readBytes())
            temp.delete()
        }
    }

    private fun clearSessionFileOnly(context: Context) {
        runCatching { sessionFile(context).delete() }
    }

    private fun sessionFile(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, SESSION_FILE)

    private data class BackendSession(
        val token: String,
        val expiresAtEpochMs: Long,
        val backendPlayerId: String,
        val backendInstallationId: String
    )

    private data class ApiResponse(
        val status: Int,
        val body: JSONObject
    ) {
        fun message(fallback: String): String =
            body.optString("message").takeIf { it.isNotBlank() }
                ?: body.optString("error").takeIf { it.isNotBlank() }
                ?: fallback
    }
}
