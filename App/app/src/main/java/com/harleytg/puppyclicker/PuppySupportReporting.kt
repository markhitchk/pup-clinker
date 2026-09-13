package com.harleytg.puppyclicker

import android.content.Context
import android.os.Build
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * Consent-gated support reporting.
 *
 * The APK never contains a Discord webhook URL. Reports go to a neutral HTTPS relay configured
 * at build time; that relay owns the Discord webhook credentials server-side.
 */
internal object PuppySupportReporting {
    private const val TAG = "PuppySupportReporting"
    private const val CONNECT_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 2_000
    private const val CRASH_FLUSH_TIMEOUT_MS = 1_500L
    private const val MAX_MESSAGE_CHARS = 600
    private const val MAX_STACK_CHARS = 12_000

    private val initialized = AtomicBoolean(false)

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

    private fun post(payload: JSONObject) {
        val endpoint = BuildConfig.PUPPY_SUPPORT_RELAY_URL
        if (!endpoint.startsWith("https://")) return

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PuppyClicker/${BuildConfig.VERSION_NAME}")
        }

        try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Support relay returned HTTP $code")
            }
        } catch (error: Exception) {
            Log.w(TAG, "Support relay request failed", error)
        } finally {
            connection.disconnect()
        }
    }
}
