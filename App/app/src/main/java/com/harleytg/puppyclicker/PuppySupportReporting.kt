package com.harleytg.puppyclicker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
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
 * Manual app support/report delivery uses one AES-256-GCM encrypted Discord webhook
 * bundled as ciphertext. That credential is never used for telemetry or crash reporting.
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
    private const val KEY_LAST_CRASH = "last_crash"
    private const val KEY_LAST_CRASH_TIME = "last_crash_time"
    private const val MAX_REPORT_DIAGNOSTIC_ENTRIES = 30
    private const val MAX_REPORT_DIAGNOSTIC_MESSAGE_CHARS = 300
    private const val USER_REPORT_WEBHOOK_IV_B64 = "zys1g4ENIYNeiKi7"
    private const val USER_REPORT_WEBHOOK_CIPHER_B64 =
        "lz/HARH4xERv3h3dPu5y+OuPsB4KXeKxz/YXWQSpgPImGR6kohPz2XtqiSdjA9elQyS3IwtNSXzUNEnid1PdMY/AOZZbhIoB7bqrtx8MEIco0fp+dUVU0VM3m3qziObQb9A3I+jzkQebiHwycpgT+g+ewm3UUPdvz6AJ3JwqhFeWI+90ymiSXLg="
    private const val USER_REPORT_WEBHOOK_KEY_MASK_A =
        "520d3d6905580e2d997a8f2eff0307b4274578b462463c9198f79eb58548dded"
    private const val USER_REPORT_WEBHOOK_KEY_MASK_B =
        "651e39847c4b34a87742b7629a7d30fbd91eed7b4ff130729e5be323c718fd10"
    private const val USER_REPORT_WEBHOOK_AAD = "puppy-clicker-app-support-v1"

    private val initialized = AtomicBoolean(false)
    private val reportRandom = SecureRandom()

    @Volatile
    private var previousCrashHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(context: Context) {
        val app = context.applicationContext
        if (!initialized.compareAndSet(false, true)) return

        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            captureCrashLocally(app, thread, throwable)

            val previous = previousCrashHandler
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                runCatching {
                    Log.e(TAG, "Uncaught exception with no previous Android crash handler", throwable)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }

        if (PuppyUiPreferences.current(app).crashReportsEnabled) {
            lastCrashSummary(app)?.let { crash ->
                PuppyDebugLog.e(
                    TAG,
                    "Previous local crash is available for support diagnostics: " +
                        crash.lineSequence().firstOrNull().orEmpty().take(MAX_MESSAGE_CHARS)
                )
            }
        }
    }

    fun lastCrashSummary(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(REPORT_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)
            ?.takeIf { it.isNotBlank() }

    private fun captureCrashLocally(
        context: Context,
        thread: Thread,
        throwable: Throwable
    ) {
        runCatching {
            val app = context.applicationContext
            if (!PuppyUiPreferences.current(app).crashReportsEnabled) {
                return@runCatching
            }

            val stack = StringWriter().also { writer ->
                PrintWriter(writer).use { printer ->
                    throwable.printStackTrace(printer)
                }
            }.toString().take(MAX_STACK_CHARS)

            val summary = buildString {
                appendLine("PUPPY CLICKER LOCAL CRASH")
                appendLine("Timestamp: ${Instant.now()}")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
                appendLine("Device: ${PuppyPlayerIdentity.deviceModel()}")
                appendLine("Thread: ${thread.name.take(80)}")
                appendLine("Casino page: ${PuppyCasinoRuntimeGuard.currentPage()}")
                appendLine("Exception: ${throwable.javaClass.name}")
                throwable.message?.takeIf { it.isNotBlank() }?.let {
                    appendLine("Message: ${it.take(MAX_MESSAGE_CHARS)}")
                }
                appendLine()
                append(stack)
            }.take(MAX_STACK_CHARS)

            app.getSharedPreferences(REPORT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_CRASH, summary)
                .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
                .commit()

            PuppyDebugLog.e(
                TAG,
                "Captured local crash on casino page ${PuppyCasinoRuntimeGuard.currentPage()}",
                throwable
            )
        }.onFailure { captureFailure ->
            runCatching {
                Log.e(TAG, "Unable to persist local crash diagnostics", captureFailure)
            }
        }
    }

    fun reportTelemetry(
        context: Context,
        event: String
    ) {
        val app = context.applicationContext
        val ui = PuppyUiPreferences.current(app)
        if (!ui.anonymousDiagnosticsEnabled) return

        val safeEvent = event
            .lowercase()
            .replace(Regex("[^a-z0-9_.-]"), "_")
            .take(64)
            .ifBlank { "unknown" }

        PuppyDebugLog.i(
            TAG,
            "Local anonymous diagnostic: $safeEvent (remote diagnostics destination not configured)"
        )
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

                if (ui.crashReportsEnabled) {
                    lastCrashSummary(app)?.let { crash ->
                        appendLine()
                        appendLine("LAST LOCAL CRASH — CRASH COLLECTION CONSENT ENABLED")
                        appendLine(crash.take(MAX_STACK_CHARS))
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
        decryptUserReportWebhook() != null

    fun submitPreparedReport(
        context: Context,
        report: PuppyPreparedSupportReport
    ): PuppySupportDeliveryResult {
        context.applicationContext
        return postUserReportToDiscord(report)
    }

    private fun postUserReportToDiscord(
        report: PuppyPreparedSupportReport
    ): PuppySupportDeliveryResult {
        val webhook = decryptUserReportWebhook()
            ?: return PuppySupportDeliveryResult(
                success = false,
                error = "discord_destination_unavailable"
            )

        val fields = JSONArray()
            .put(
                JSONObject()
                    .put("name", "Report ID")
                    .put("value", report.reportId)
                    .put("inline", true)
            )
            .put(
                JSONObject()
                    .put("name", "Category")
                    .put("value", report.type.label)
                    .put("inline", true)
            )
            .put(
                JSONObject()
                    .put("name", "Status")
                    .put("value", "🟡 New · Tier 1")
                    .put("inline", true)
            )

        val embed = JSONObject()
            .put("title", "🐾 Puppy Clicker Tier 1 User Report")
            .put(
                "description",
                buildString {
                    append("**").append(report.subject).append("**")
                    append("\n\n")
                    append(report.body)
                }.take(4_096)
            )
            .put("fields", fields)
            .put(
                "footer",
                JSONObject().put(
                    "text",
                    "Puppy Clicker App Reporting & Support · Encrypted Discord webhook"
                )
            )

        val discordPayload = JSONObject()
            .put("username", "Puppy Clicker Support")
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

    private fun decryptUserReportWebhook(): String? = runCatching {
        val maskA = hexToBytes(USER_REPORT_WEBHOOK_KEY_MASK_A)
        val maskB = hexToBytes(USER_REPORT_WEBHOOK_KEY_MASK_B)
        require(maskA.size == 32 && maskB.size == 32)
        val key = ByteArray(32) { index ->
            (maskA[index].toInt() xor maskB[index].toInt()).toByte()
        }
        val iv = Base64.getDecoder().decode(USER_REPORT_WEBHOOK_IV_B64)
        val encrypted = Base64.getDecoder().decode(USER_REPORT_WEBHOOK_CIPHER_B64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv)
        )
        cipher.updateAAD(USER_REPORT_WEBHOOK_AAD.toByteArray(Charsets.UTF_8))
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

    private fun basePayload(kind: String): JSONObject =
        JSONObject()
            .put("schema", 1)
            .put("kind", kind)
            .put("timestamp_ms", System.currentTimeMillis())
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("app_version_code", BuildConfig.VERSION_CODE)
            .put("android_sdk", Build.VERSION.SDK_INT)
            .put("package", BuildConfig.APPLICATION_ID)


}
