package com.harleytg.puppyclicker

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal enum class PuppyDiscordLinkStatus {
    NOT_LINKED,
    CODE_SENT,
    VERIFIED,
    EXPIRED,
    LOCKED,
    DM_FAILED,
    OFFLINE
}

internal data class PuppyDiscordVerifySnapshot(
    val status: PuppyDiscordLinkStatus = PuppyDiscordLinkStatus.NOT_LINKED,
    val discordId: String? = null,
    val discordUsername: String? = null,
    val discordDisplayName: String? = null,
    val guildRole: DiscordGuildRole? = null,
    val expiresAtMs: Long? = null,
    val resendAvailableAtMs: Long? = null,
    val attemptsRemaining: Int = PuppyDiscordDmCodePolicy.MAX_ATTEMPTS,
    val message: String? = null,
    val offline: Boolean = false
)

internal enum class PuppySupportRequestType(val apiValue: String, val label: String) {
    HARDWARE_RESET("hardware_reset", "Hardware Reset"),
    ACCOUNT_RECOVERY("account_recovery", "Account Recovery"),
    BAN_APPEAL("ban_appeal", "Ban Appeal")
}

internal enum class PuppySupportRequestState(val apiValue: String, val label: String) {
    OPEN("open", "Open"),
    INFO_REQUESTED("info_requested", "Info requested"),
    APPROVED("approved", "Approved"),
    DENIED("denied", "Denied"),
    CLOSED("closed", "Closed")
}

internal data class PuppySupportRequest(
    val id: String,
    val type: PuppySupportRequestType,
    val state: PuppySupportRequestState,
    val subject: String,
    val details: String,
    val staffNote: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

internal data class PuppyAuthBotEvent(
    val type: String,
    val title: String,
    val body: String,
    val requestId: String? = null,
    val banId: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * HTTP + live-update client for the standalone Puppy Clicker Auth bot.
 * Authenticate every call with the player's PupEye session.
 */
internal object PuppyAuthBotClient {
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val MAX_BODY_BYTES = 128 * 1024
    private const val POLL_MS = 20_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var liveStarted = false

    fun start(context: Context) {
        if (liveStarted || !isConfigured()) return
        liveStarted = true
        val app = context.applicationContext
        scope.launch {
            runCatching { refreshVerificationStatus(app) }
            runCatching { refreshSupportRequests(app) }
            listenForLiveUpdates(app)
        }
    }

    private val mutableVerify = MutableStateFlow(PuppyDiscordVerifySnapshot())
    val verifyState: StateFlow<PuppyDiscordVerifySnapshot> = mutableVerify.asStateFlow()

    private val mutableRequests = MutableStateFlow<List<PuppySupportRequest>>(emptyList())
    val requests: StateFlow<List<PuppySupportRequest>> = mutableRequests.asStateFlow()

    private val mutableOnline = MutableStateFlow(true)
    val online: StateFlow<Boolean> = mutableOnline.asStateFlow()

    val baseUrl: String
        get() = BuildConfig.PUPPY_AUTH_BOT_URL.trim().trimEnd('/')

    fun isConfigured(): Boolean = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")

    suspend fun sendVerificationCode(context: Context, account: DiscordPlayerAccount): PuppyDiscordVerifySnapshot =
        postVerify(context, "/v1/verify/send", JSONObject().apply {
            put("discordId", account.id)
            put("discordUsername", account.username)
            account.globalName?.let { put("discordGlobalName", it) }
            account.avatarHash?.let { put("discordAvatarHash", it) }
            account.email?.let { put("discordEmail", it) }
        })

    suspend fun submitVerificationCode(context: Context, code: String): PuppyDiscordVerifySnapshot =
        postVerify(context, "/v1/verify/submit", JSONObject().apply {
            put("code", PuppyDiscordDmCodePolicy.normalize(code))
        })

    suspend fun resendVerificationCode(context: Context): PuppyDiscordVerifySnapshot =
        postVerify(context, "/v1/verify/resend", JSONObject())

    suspend fun refreshVerificationStatus(context: Context): PuppyDiscordVerifySnapshot =
        getVerify(context, "/v1/verify/status")

    suspend fun submitSupportRequest(
        context: Context,
        type: PuppySupportRequestType,
        subject: String,
        details: String,
        banId: String? = null
    ): PuppySupportRequest {
        val body = request(
            context,
            "POST",
            "/v1/support/requests",
            JSONObject().apply {
                put("type", type.apiValue)
                put("subject", subject.take(120))
                put("details", details.take(2_000))
                banId?.takeIf { it.isNotBlank() }?.let { put("banId", it) }
            }
        )
        val created = parseRequest(body.optJSONObject("request") ?: body)
            ?: error(body.optString("message").ifBlank { "Support could not create that request." })
        mutableRequests.value = (listOf(created) + mutableRequests.value).distinctBy { it.id }
        return created
    }

    suspend fun refreshSupportRequests(context: Context): List<PuppySupportRequest> {
        val body = request(context, "GET", "/v1/support/requests", null)
        val parsed = parseRequestList(body.optJSONArray("requests"))
        mutableRequests.value = parsed
        return parsed
    }

    suspend fun listenForLiveUpdates(context: Context) {
        val app = context.applicationContext
        while (true) {
            val streamed = runCatching { streamEvents(app, "/v1/live") }.getOrDefault(false)
            if (!streamed) {
                runCatching { refreshVerificationStatus(app) }
                runCatching { refreshSupportRequests(app) }
                delay(POLL_MS)
            }
        }
    }

    fun applyLiveEvent(context: Context, event: PuppyAuthBotEvent) {
        val type = event.type.lowercase()
        if (
            type.contains("ban") || type.contains("unban") || type.contains("appeal") ||
            type.contains("request") || type.contains("support") || type.contains("verify")
        ) {
            val notificationType = if (type.contains("verify")) {
                PuppyNotificationType.APP_UPDATE
            } else {
                PuppyNotificationType.SUPPORT
            }
            PuppyNotificationHistory.record(
                context,
                PuppyNotificationItem(
                    id = "bot:${type}:${event.requestId ?: event.banId ?: event.createdAtMs}",
                    type = notificationType,
                    title = event.title.ifBlank { "Puppy Clicker Auth" },
                    body = event.body,
                    createdAtMs = event.createdAtMs,
                    read = false,
                    route = if (notificationType == PuppyNotificationType.SUPPORT) {
                        PuppyNotificationRoute.SUPPORT
                    } else {
                        PuppyNotificationRoute.NONE
                    }
                )
            )
        }
    }

    private suspend fun postVerify(context: Context, path: String, payload: JSONObject): PuppyDiscordVerifySnapshot {
        val snapshot = runCatching { parseVerify(request(context, "POST", path, payload)) }
            .getOrElse { offlineSnapshot(it.message) }
        mutableVerify.value = snapshot
        mutableOnline.value = !snapshot.offline
        return snapshot
    }

    private suspend fun getVerify(context: Context, path: String): PuppyDiscordVerifySnapshot {
        val snapshot = runCatching { parseVerify(request(context, "GET", path, null)) }
            .getOrElse { offlineSnapshot(it.message) }
        mutableVerify.value = snapshot
        mutableOnline.value = !snapshot.offline
        return snapshot
    }

    private fun offlineSnapshot(message: String?): PuppyDiscordVerifySnapshot {
        val current = mutableVerify.value
        return current.copy(
            status = if (current.status == PuppyDiscordLinkStatus.VERIFIED) {
                current.status
            } else {
                PuppyDiscordLinkStatus.OFFLINE
            },
            offline = true,
            message = message?.take(220) ?: "Puppy Clicker Auth is offline. Try again in a moment."
        )
    }

    private suspend fun request(
        context: Context,
        method: String,
        path: String,
        payload: JSONObject?
    ): JSONObject = withContext(Dispatchers.IO) {
        require(isConfigured()) { "Puppy Clicker Auth bot URL is not configured." }
        val session = SupabasePupEyeClient.sessionAuth(context)
            ?: error("PupEye session required. Open Puppy Clicker on this device first.")
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Pupeye-Session", session.token)
            setRequestProperty("X-Puppy-Player-Id", session.backendPlayerId)
            setRequestProperty("X-Puppy-Installation-Id", session.backendInstallationId)
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (payload != null) {
                val bytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use(::readBounded).orEmpty()
            val body = runCatching {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            }.getOrDefault(JSONObject().put("raw", text.take(400)))
            if (status !in 200..299) {
                throw IllegalStateException(
                    body.optString("message").ifBlank { "Puppy Clicker Auth returned HTTP $status." }
                )
            }
            mutableOnline.value = true
            body
        } catch (error: Exception) {
            mutableOnline.value = false
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun streamEvents(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        val session = SupabasePupEyeClient.sessionAuth(context) ?: return@withContext false
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = 45_000
            setRequestProperty("Accept", "text/event-stream, application/json")
            setRequestProperty("X-Pupeye-Session", session.token)
            setRequestProperty("X-Puppy-Player-Id", session.backendPlayerId)
            setRequestProperty("X-Puppy-Installation-Id", session.backendInstallationId)
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext false
            val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            val buffer = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("data:") -> buffer.append(line.removePrefix("data:").trim())
                    line.isBlank() && buffer.isNotBlank() -> {
                        parseLivePayload(context, buffer.toString())
                        buffer.clear()
                    }
                    line.startsWith("{") -> parseLivePayload(context, line)
                }
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLivePayload(context: Context, raw: String) {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val event = PuppyAuthBotEvent(
            type = json.optString("type").ifBlank { json.optString("event") },
            title = json.optString("title").ifBlank { "Puppy Clicker Auth" },
            body = json.optString("body").ifBlank { json.optString("message") },
            requestId = json.optString("requestId").takeIf { it.isNotBlank() },
            banId = json.optString("banId").takeIf { it.isNotBlank() },
            createdAtMs = json.optLong("createdAtMs", System.currentTimeMillis())
        )
        applyLiveEvent(context, event)
        if (event.type.contains("verify", ignoreCase = true)) {
            runCatching { mutableVerify.value = parseVerify(json.optJSONObject("verify") ?: json) }
        }
    }

    private fun parseVerify(body: JSONObject): PuppyDiscordVerifySnapshot {
        val status = when (body.optString("status").lowercase()) {
            "verified", "linked" -> PuppyDiscordLinkStatus.VERIFIED
            "code_sent", "pending", "sent" -> PuppyDiscordLinkStatus.CODE_SENT
            "expired" -> PuppyDiscordLinkStatus.EXPIRED
            "locked" -> PuppyDiscordLinkStatus.LOCKED
            "dm_failed" -> PuppyDiscordLinkStatus.DM_FAILED
            "offline" -> PuppyDiscordLinkStatus.OFFLINE
            else -> PuppyDiscordLinkStatus.NOT_LINKED
        }
        val role = body.optString("guildRole").takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { DiscordGuildRole.valueOf(it) }.getOrNull() }
        val dmFailed = body.optBoolean("dmFailed", status == PuppyDiscordLinkStatus.DM_FAILED)
        val message = body.optString("message").takeIf { it.isNotBlank() }
            ?: if (dmFailed) {
                "The Auth bot could not DM you. Enable DMs from server members in Discord Privacy Settings, or join the Puppy Clicker server, then tap Resend."
            } else {
                null
            }
        return PuppyDiscordVerifySnapshot(
            status = if (dmFailed) PuppyDiscordLinkStatus.DM_FAILED else status,
            discordId = body.optString("discordId").takeIf { it.isNotBlank() },
            discordUsername = body.optString("discordUsername").takeIf { it.isNotBlank() },
            discordDisplayName = body.optString("discordDisplayName").takeIf { it.isNotBlank() },
            guildRole = role,
            expiresAtMs = parseTime(body, "expiresAt", "expiresAtMs"),
            resendAvailableAtMs = parseTime(body, "resendAvailableAt", "resendAvailableAtMs"),
            attemptsRemaining = body.optInt("attemptsRemaining", PuppyDiscordDmCodePolicy.MAX_ATTEMPTS),
            message = message,
            offline = false
        )
    }

    private fun parseTime(body: JSONObject, isoKey: String, msKey: String): Long? {
        if (body.has(msKey) && !body.isNull(msKey)) {
            val value = body.optLong(msKey, 0L)
            if (value > 0L) return value
        }
        val raw = body.optString(isoKey)
        if (raw.isBlank()) return null
        return runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
    }

    private fun parseRequestList(array: JSONArray?): List<PuppySupportRequest> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                parseRequest(array.optJSONObject(index))?.let(::add)
            }
        }
    }

    private fun parseRequest(json: JSONObject?): PuppySupportRequest? {
        if (json == null) return null
        val id = json.optString("id").ifBlank { return null }
        val type = PuppySupportRequestType.entries.firstOrNull {
            it.apiValue.equals(json.optString("type"), ignoreCase = true)
        } ?: return null
        val state = PuppySupportRequestState.entries.firstOrNull {
            it.apiValue.equals(json.optString("state"), ignoreCase = true) ||
                it.apiValue.equals(json.optString("status"), ignoreCase = true)
        } ?: PuppySupportRequestState.OPEN
        return PuppySupportRequest(
            id = id,
            type = type,
            state = state,
            subject = json.optString("subject"),
            details = json.optString("details"),
            staffNote = json.optString("staffNote").takeIf { it.isNotBlank() }
                ?: json.optString("note").takeIf { it.isNotBlank() },
            createdAtMs = json.optLong("createdAtMs", System.currentTimeMillis()),
            updatedAtMs = json.optLong("updatedAtMs", System.currentTimeMillis())
        )
    }

    private fun readBounded(stream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
        val builder = StringBuilder()
        val buffer = CharArray(4096)
        var total = 0
        while (true) {
            val read = reader.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_BODY_BYTES) break
            builder.appendRange(buffer, 0, read)
        }
        return builder.toString()
    }
}
