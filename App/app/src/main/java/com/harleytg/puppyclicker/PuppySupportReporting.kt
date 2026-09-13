package com.harleytg.puppyclicker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Consent-gated support reporting.
 *
 * Support delivery uses only an AES-GCM encrypted Discord webhook bundled as ciphertext.
 * The plaintext webhook URL is never stored in source or BuildConfig.
 */
internal enum class PuppySupportReportType(
    val emoji: String,
    val label: String
) {
    BUG("🐛", "Bug / App Problem"),
    CASINO("🎰", "Casino"),
    SAVE_DATA("💾", "Save / Data"),
    ACCOUNT("👤", "Account / Identity"),
    PLAYER("🚩", "Player / User Report"),
    OTHER("📝", "Other")
}

internal data class PuppySupportReportDraft(
    val type: PuppySupportReportType,
    val subject: String,
    val description: String,
    val context: String,
    val reportedUser: String = "",
    val includeIdentity: Boolean = false,
    val includeDiagnostics: Boolean = false
)

internal data class PuppyPreparedSupportReport(
    val reportId: String,
    val type: PuppySupportReportType,
    val subject: String,
    val body: String
)

internal data class PuppySupportDeliveryResult(
    val success: Boolean,
    val statusCode: Int? = null,
    val error: String? = null
)

internal object PuppySupportReporting {
    private const val TAG = "PuppySupportReporting"
    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 2_000
    private const val CRASH_FLUSH_TIMEOUT_MS = 1_500L
    private const val MAX_MESSAGE_CHARS = 600
    private const val MAX_STACK_CHARS = 12_000
    private const val REPORT_PREFS = "puppy_support_reporting_v1"
    private const val KEY_LAST_PREPARED_REPORT_ID = "last_prepared_report_id"
    private const val KEY_LAST_PREPARED_REPORT_TIME = "last_prepared_report_time"
    private const val MAX_REPORT_DIAGNOSTIC_ENTRIES = 30
    private const val MAX_REPORT_DIAGNOSTIC_MESSAGE_CHARS = 300
    private const val DIRECT_SUPPORT_IV_B64 = "mM16rdsma5ahLLsk"
    private const val DIRECT_SUPPORT_CIPHER_B64 =
        "IAjSrBvcYRZJV82Cn3NoUqzkqCkILQZc/If418xZYamjOlvN4BllM6rOOFN9z7w/hItK/CfBYPZFhSIAe2uTwOInRSSw8NzYhYVmTZPM17gM6/W9a2vcgvfvLuOmcwTsd05Er2R5+20cv/aGrKD+to1wS24GVspqq2nDp9yxjrY9kP6SYx95hSY="
    private const val DIRECT_SUPPORT_KEY_MASK_A =
        "a388d111547a364c35f359b4e1319e8c0e4c6f1ecb5244c3d92246eb75c82118"
    private const val DIRECT_SUPPORT_KEY_MASK_B =
        "86b6947496e6b3d33ea01edda2a36752ace78709e4c484ee5d632d1e177ed3f4"
    private const val DIRECT_SUPPORT_AAD = "puppy-clicker-direct-support-v1"

    private val initialized = AtomicBoolean(false)
    private val reportRandom = SecureRandom()

    fun initialize(context: Context) {
        val app = context.applicationContext
        if (initialized.compareAndSet(false, true)) {
            installCrashHandler(app)
        }
        reportTelemetry(app, "app_start")
    }

    fun reportTelemetry(
        context: Context,
        event: String
    ) {
        val app = context.applicationContext
        val ui = PuppyUiPreferences.current(app)
        if (!ui.anonymousDiagnosticsEnabled) return
        if (!isSubmissionConfigured()) return

        val safeEvent = event
            .lowercase()
            .replace(Regex("[^a-z0-9_.-]"), "_")
            .take(64)
            .ifBlank { "unknown" }

        val payload = basePayload("telemetry")
            .put("event", safeEvent)
        appendSupportIdentity(app, payload, ui)

        Thread(
            { postEncryptedDiscordPayload(payload) },
            "puppy-support-telemetry"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun prepareUserReport(
        context: Context,
        draft: PuppySupportReportDraft
    ): PuppyPreparedSupportReport {
        val app = context.applicationContext
        val ui = PuppyUiPreferences.current(app)
        val safeSubject = draft.subject.trim().take(80).ifBlank { draft.type.label }
        val reportId = newReportId()
        val timestamp = Instant.now().toString()
        val attachIdentity = draft.includeIdentity && ui.supportIdentityEnabled
        val attachDiagnostics = draft.includeDiagnostics && ui.anonymousDiagnosticsEnabled

        val body = buildString {
            appendLine("PUPPY CLICKER — TIER 1 SUPPORT REPORT")
            appendLine("Report ID: ${reportId}")
            appendLine("Type: ${draft.type.label}")
            appendLine("Created: ${timestamp}")
            appendLine()
            appendLine("SUBJECT")
            appendLine(safeSubject)
            appendLine()
            appendLine("DESCRIPTION")
            appendLine(draft.description.trim().take(2_000))
            appendLine()
            appendLine("STEPS / CONTEXT")
            appendLine(draft.context.trim().take(1_500).ifBlank { "Not provided" })

            if (draft.type == PuppySupportReportType.PLAYER) {
                appendLine()
                appendLine("REPORTED USER")
                appendLine(draft.reportedUser.trim().take(100).ifBlank { "Not provided" })
            }

            appendLine()
            appendLine("APP INFORMATION")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${PuppyPlayerIdentity.deviceModel()}")

            if (attachIdentity) {
                appendLine()
                appendLine("SUPPORT IDENTITY — PRIVACY CONSENT ENABLED")
                appendLine("Username: ${PuppyPlayerIdentity.username(app)}")
                appendLine("Player ID: ${PuppyPlayerIdentity.publicPlayerId(app)}")
                appendLine("Friend Code: ${PuppyPlayerIdentity.publicFriendCode(app)}")
                DiscordSignupAuth.account(app)?.let { account ->
                    appendLine("Discord User ID: ${account.id}")
                    appendLine("Discord Username: @${account.username}")
                    appendLine("Discord Display Name: ${account.displayName}")
                }
            }

            if (attachDiagnostics) {
                appendLine()
                appendLine("SANITIZED SESSION DIAGNOSTICS — PRIVACY CONSENT ENABLED")
                val entries = PuppyDebugLog.snapshot().takeLast(MAX_REPORT_DIAGNOSTIC_ENTRIES)
                if (entries.isEmpty()) {
                    appendLine("No in-app diagnostic entries available.")
                } else {
                    entries.forEach { entry ->
                        val message = PuppyDebugLog.redactForConsole(entry.message)
                            .take(MAX_REPORT_DIAGNOSTIC_MESSAGE_CHARS)
                        appendLine(
                            "${entry.timestampMs} ${entry.level.shortName}/${entry.tag}: ${message}"
                        )
                    }
                }
            }

            appendLine()
            appendLine("DELIVERY")
            appendLine(
                "Press Submit in Puppy Clicker to send this report to the Tier 1 Discord " +
                    "support inbox. Copy and Android Share are fallback options. The app " +
                    "shows Submitted only after the configured Discord delivery path succeeds."
            )
        }

        app.getSharedPreferences(REPORT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PREPARED_REPORT_ID, reportId)
            .putLong(KEY_LAST_PREPARED_REPORT_TIME, System.currentTimeMillis())
            .apply()

        return PuppyPreparedSupportReport(
            reportId = reportId,
            type = draft.type,
            subject = safeSubject,
            body = body
        )
    }

    fun lastPreparedReportId(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(REPORT_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PREPARED_REPORT_ID, null)
            ?.takeIf { it.isNotBlank() }

    fun copyPreparedReport(
        context: Context,
        report: PuppyPreparedSupportReport
    ) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "Puppy Clicker Support ${report.reportId}",
                report.body
            )
        )
    }

    fun sharePreparedReport(
        context: Context,
        report: PuppyPreparedSupportReport
    ) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                "[Puppy Clicker] ${report.type.label} · ${report.reportId} · ${report.subject}"
            )
            putExtra(Intent.EXTRA_TEXT, report.body)
        }
        context.startActivity(
            Intent.createChooser(send, "Share Puppy Clicker support report")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun isSubmissionConfigured(): Boolean =
        decryptDirectDiscordWebhook() != null

    fun submitPreparedReport(
        context: Context,
        report: PuppyPreparedSupportReport
    ): PuppySupportDeliveryResult {
        val payload = basePayload("user_report")
            .put("report_id", report.reportId)
            .put("report_type", report.type.label)
            .put("subject", report.subject)
            .put("body", report.body.take(6_000))
        return postEncryptedDiscordPayload(payload)
    }

    private fun postEncryptedDiscordPayload(
        payload: JSONObject
    ): PuppySupportDeliveryResult {
        val webhook = decryptDirectDiscordWebhook()
            ?: return PuppySupportDeliveryResult(
                success = false,
                error = "discord_destination_unavailable"
            )

        val kind = payload.optString("kind")
        val fields = JSONArray()
        val appVersion = payload.optString("app_version")
        val appVersionCode = payload.optString("app_version_code")
        val androidSdk = payload.optString("android_sdk")
        val timestamp = payload.optString("timestamp_ms")

        if (appVersion.isNotBlank()) {
            fields.put(
                JSONObject()
                    .put("name", "App")
                    .put("value", "$appVersion ($appVersionCode)")
                    .put("inline", true)
            )
        }
        if (androidSdk.isNotBlank()) {
            fields.put(
                JSONObject()
                    .put("name", "Android SDK")
                    .put("value", androidSdk)
                    .put("inline", true)
            )
        }
        if (timestamp.isNotBlank()) {
            fields.put(
                JSONObject()
                    .put("name", "Timestamp")
                    .put("value", timestamp)
                    .put("inline", false)
            )
        }

        val username = payload.optString("username")
        val playerId = payload.optString("player_id")
        val friendCode = payload.optString("friend_code")
        val discordUserId = payload.optString("discord_user_id")
        val discordUsername = payload.optString("discord_username")
        val discordDisplayName = payload.optString("discord_display_name")

        if (username.isNotBlank()) {
            fields.put(
                JSONObject()
                    .put("name", "Puppy Clicker User")
                    .put("value", username.take(1024))
                    .put("inline", true)
            )
        }
        if (playerId.isNotBlank()) {
            fields.put(
                JSONObject()
                    .put("name", "Player ID")
                    .put("value", playerId.take(1024))
                    .put("inline", true)
            )
        }
        if (friendCode.isNotBlank()) {
            fields.put(
                JSONObject()
                    .put("name", "Friend Code")
                    .put("value", friendCode.take(1024))
                    .put("inline", true)
            )
        }
        if (
            discordUserId.isNotBlank() ||
            discordUsername.isNotBlank() ||
            discordDisplayName.isNotBlank()
        ) {
            val discordValue = buildString {
                if (discordDisplayName.isNotBlank()) append(discordDisplayName)
                if (discordUsername.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append("@").append(discordUsername)
                }
                if (discordUserId.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append("ID: ").append(discordUserId)
                }
            }
            fields.put(
                JSONObject()
                    .put("name", "Discord Identity")
                    .put("value", discordValue.take(1024))
                    .put("inline", false)
            )
        }

        val title: String
        val description: String
        val footer: String
        val webhookUsername: String

        when (kind) {
            "telemetry" -> {
                val event = payload.optString("event").ifBlank { "unknown" }.take(64)
                title = "📊 Puppy Clicker Anonymous Diagnostics"
                description = "Event: **$event**"
                footer = "Consent-gated Puppy Clicker diagnostics"
                webhookUsername = "Puppy Clicker Diagnostics"
            }

            "crash" -> {
                val exception = payload.optString("exception")
                    .ifBlank { "Unknown exception" }
                    .take(240)
                val message = payload.optString("message").take(MAX_MESSAGE_CHARS)
                val thread = payload.optString("thread").take(120)
                val stack = payload.optString("stack").take(900)

                title = "🐛 Puppy Clicker Crash Report"
                description = buildString {
                    append("**").append(exception).append("**")
                    if (message.isNotBlank()) append("\n").append(message)
                }
                if (thread.isNotBlank()) {
                    fields.put(
                        JSONObject()
                            .put("name", "Thread")
                            .put("value", thread)
                            .put("inline", true)
                    )
                }
                if (stack.isNotBlank()) {
                    fields.put(
                        JSONObject()
                            .put("name", "Stack trace")
                            .put("value", stack)
                            .put("inline", false)
                    )
                }
                footer = "Consent-gated Puppy Clicker crash reporting"
                webhookUsername = "Puppy Clicker Crash Handler"
            }

            "user_report" -> {
                val reportId = payload.optString("report_id").take(64)
                val reportType = payload.optString("report_type")
                    .ifBlank { "Other" }
                    .take(64)
                val subject = payload.optString("subject")
                    .ifBlank { "User report" }
                    .take(120)
                val body = payload.optString("body").take(3_800)

                fields.put(
                    JSONObject()
                        .put("name", "Report ID")
                        .put("value", reportId)
                        .put("inline", true)
                )
                fields.put(
                    JSONObject()
                        .put("name", "Category")
                        .put("value", reportType)
                        .put("inline", true)
                )
                fields.put(
                    JSONObject()
                        .put("name", "Status")
                        .put("value", "🟡 New · Tier 1")
                        .put("inline", true)
                )

                title = "🐾 Puppy Clicker Tier 1 User Report"
                description = buildString {
                    append("**").append(subject).append("**")
                    if (body.isNotBlank()) append("\n\n").append(body)
                }.take(4_096)
                footer = "Puppy Clicker Tier 1 Support · Encrypted Discord webhook"
                webhookUsername = "Puppy Clicker Support"
            }

            else -> {
                return PuppySupportDeliveryResult(
                    success = false,
                    error = "unsupported_support_payload"
                )
            }
        }

        val embed = JSONObject()
            .put("title", title)
            .put("description", description)
            .put("fields", fields)
            .put("footer", JSONObject().put("text", footer))

        val discordPayload = JSONObject()
            .put("username", webhookUsername)
            .put(
                "avatar_url",
                "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/logos/puppy_clicker.png"
            )
            .put(
                "allowed_mentions",
                JSONObject().put("parse", JSONArray())
            )
            .put("embeds", JSONArray().put(embed))

        val connection = (URL(webhook).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PuppyClicker/${BuildConfig.VERSION_NAME}")
        }

        return try {
            connection.outputStream.use { output ->
                output.write(discordPayload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            if (code in 200..299) {
                PuppySupportDeliveryResult(
                    success = true,
                    statusCode = code
                )
            } else {
                PuppyDebugLog.w(TAG, "Discord support delivery returned HTTP $code")
                PuppySupportDeliveryResult(
                    success = false,
                    statusCode = code,
                    error = "discord_delivery_rejected"
                )
            }
        } catch (error: Exception) {
            PuppyDebugLog.w(TAG, "Discord support delivery failed", error)
            PuppySupportDeliveryResult(
                success = false,
                error = "discord_delivery_unavailable"
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun decryptDirectDiscordWebhook(): String? = runCatching {
        val maskA = hexToBytes(DIRECT_SUPPORT_KEY_MASK_A)
        val maskB = hexToBytes(DIRECT_SUPPORT_KEY_MASK_B)
        require(maskA.size == 32 && maskB.size == 32)
        val key = ByteArray(32) { index ->
            (maskA[index].toInt() xor maskB[index].toInt()).toByte()
        }
        val iv = Base64.getDecoder().decode(DIRECT_SUPPORT_IV_B64)
        val encrypted = Base64.getDecoder().decode(DIRECT_SUPPORT_CIPHER_B64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv)
        )
        cipher.updateAAD(DIRECT_SUPPORT_AAD.toByteArray(Charsets.UTF_8))
        val plaintext = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        require(plaintext.startsWith("https://discord.com/"))
        plaintext
    }.getOrNull()

    private fun hexToBytes(value: String): ByteArray {
        require(value.length % 2 == 0)
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun newReportId(): String {
        val date = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val suffix = reportRandom.nextInt(0x10000)
            .toString(16)
            .uppercase(Locale.US)
            .padStart(4, '0')
        return "PC-RPT-${date}-${suffix}"
    }

    private fun installCrashHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val ui = PuppyUiPreferences.current(context)
                if (ui.crashReportsEnabled && isSubmissionConfigured()) {
                    val payload = basePayload("crash")
                        .put("event", "uncaught_exception")
                        .put("thread", thread.name.take(120))
                        .put("exception", throwable.javaClass.name.take(240))
                        .put(
                            "message",
                            (throwable.message ?: "")
                                .replace(Regex("[\\r\\n\\t]+"), " ")
                                .take(MAX_MESSAGE_CHARS)
                        )
                        .put(
                            "stack",
                            throwable.stackTraceToString().take(MAX_STACK_CHARS)
                        )
                    appendSupportIdentity(context, payload, ui)
                    postCrashWithDeadline(payload)
                }
            }.onFailure {
                Log.w(TAG, "Unable to queue crash report", it)
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private fun postCrashWithDeadline(payload: JSONObject) {
        val worker = Thread(
            { postEncryptedDiscordPayload(payload) },
            "puppy-support-crash"
        )
        worker.start()
        runCatching { worker.join(CRASH_FLUSH_TIMEOUT_MS) }
    }

    private fun basePayload(kind: String): JSONObject =
        JSONObject()
            .put("schema", 1)
            .put("kind", kind)
            .put("timestamp_ms", System.currentTimeMillis())
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("app_version_code", BuildConfig.VERSION_CODE)
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("package", BuildConfig.APPLICATION_ID)

    private fun appendSupportIdentity(
        context: Context,
        payload: JSONObject,
        ui: PuppyUiState
    ) {
        if (!ui.supportIdentityEnabled) return

        payload
            .put("username", PuppyPlayerIdentity.username(context))
            .put("player_id", PuppyPlayerIdentity.publicPlayerId(context))
            .put("friend_code", PuppyPlayerIdentity.publicFriendCode(context))

        DiscordSignupAuth.account(context)?.let { account ->
            payload
                .put("discord_user_id", account.id)
                .put("discord_username", account.username)
                .put("discord_display_name", account.displayName)
        }
    }

}
