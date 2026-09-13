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
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * Consent-gated support reporting.
 *
 * The APK never contains a Discord webhook URL. Reports go to a neutral HTTPS relay configured
 * at build time; that relay owns the Discord webhook credentials server-side.
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
        if (!isConfigured()) return

        val safeEvent = event
            .lowercase()
            .replace(Regex("[^a-z0-9_.-]"), "_")
            .take(64)
            .ifBlank { "unknown" }

        val payload = basePayload("telemetry")
            .put("event", safeEvent)
        appendSupportIdentity(app, payload, ui)

        Thread(
            { post(payload) },
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
                "This report was prepared locally. Puppy Clicker uses a minimal support relay " +
                    "instead of a full ticket API. It is only submitted after the relay " +
                    "returns a successful delivery response."
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

    fun isRelayConfigured(): Boolean = isConfigured()

    fun submitPreparedReport(
        context: Context,
        report: PuppyPreparedSupportReport
    ): PuppySupportDeliveryResult {
        if (!isConfigured()) {
            return PuppySupportDeliveryResult(
                success = false,
                error = "support_relay_not_configured"
            )
        }

        val app = context.applicationContext
        val ui = PuppyUiPreferences.current(app)
        val payload = basePayload("user_report")
            .put("report_id", report.reportId)
            .put("report_type", report.type.label)
            .put("subject", report.subject)
            .put("body", report.body.take(6_000))
        appendSupportIdentity(app, payload, ui)
        return post(payload)
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
                if (ui.crashReportsEnabled && isConfigured()) {
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
            { post(payload) },
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

    private fun isConfigured(): Boolean =
        BuildConfig.PUPPY_SUPPORT_RELAY_URL.startsWith("https://")

    private fun post(payload: JSONObject): PuppySupportDeliveryResult {
        val endpoint = BuildConfig.PUPPY_SUPPORT_RELAY_URL
        if (!endpoint.startsWith("https://")) {
            return PuppySupportDeliveryResult(
                success = false,
                error = "support_relay_not_configured"
            )
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            if (code in 200..299) {
                PuppySupportDeliveryResult(
                    success = true,
                    statusCode = code
                )
            } else {
                Log.w(TAG, "Support relay returned HTTP $code")
                PuppySupportDeliveryResult(
                    success = false,
                    statusCode = code,
                    error = "support_relay_rejected"
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "Support relay request failed", error)
            PuppySupportDeliveryResult(
                success = false,
                error = "support_relay_unavailable"
            )
        } finally {
            connection.disconnect()
        }
    }
}
