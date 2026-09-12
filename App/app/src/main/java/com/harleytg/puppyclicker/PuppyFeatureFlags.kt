package com.harleytg.puppyclicker

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class PuppyFeatureFlag(
    val key: String,
    val visible: Boolean,
    val enabled: Boolean,
    val status: String,
    val releaseDate: String?,
    val label: String,
    val description: String
) {
    fun isAvailable(today: LocalDate = LocalDate.now()): Boolean {
        if (!visible || !enabled || status !in setOf("released", "beta")) return false
        val release = releaseDate?.let { runCatching(LocalDate::parse).getOrNull() }
        return release == null || !today.isBefore(release)
    }

    fun statusLabel(today: LocalDate = LocalDate.now()): String {
        val release = releaseDate?.let { runCatching(LocalDate::parse).getOrNull() }
        if (release != null && today.isBefore(release)) return "Coming " + release
        return when (status) {
            "released" -> if (enabled) "Available" else "Disabled"
            "beta" -> if (enabled) "Beta" else "Disabled"
            "coming_soon" -> "Coming Soon"
            else -> "Disabled"
        }
    }
}

/**
 * Repository-backed feature availability.
 *
 * Flags are read from FlagSys/flags.json on main, cached after a valid response, and
 * refreshed periodically. JSON can disable or schedule code that is already shipped;
 * it cannot download executable Android features.
 */
internal object PuppyFeatureFlags {
    private const val FLAGS_URL =
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/FlagSys/flags.json"
    private const val PREFS = "puppy_feature_flags_v1"
    private const val CACHE_KEY = "last_valid_json"
    private const val MAX_BYTES = 256 * 1024
    private const val REFRESH_MS = 15L * 60L * 1_000L

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val fallbacks = linkedMapOf(
        "feature_flag_system" to PuppyFeatureFlag(
            "feature_flag_system", true, true, "released", null,
            "Remote Feature Flags", "Repository-backed remote feature availability."
        ),
        "puppy_clicker_account" to PuppyFeatureFlag(
            "puppy_clicker_account", true, false, "coming_soon", null,
            "Puppy Clicker Account", "Online account with username, password, and Discord authentication."
        ),
        "discord_linking" to PuppyFeatureFlag(
            "discord_linking", true, true, "released", null,
            "Discord Linking", "Optional Discord identity connection."
        ),
        "save_restore" to PuppyFeatureFlag(
            "save_restore", true, true, "released", null,
            "Save Restore", "Import an existing encrypted .pupsave file."
        )
    )

    private val _flags = MutableStateFlow<Map<String, PuppyFeatureFlag>>(fallbacks)
    val flags: StateFlow<Map<String, PuppyFeatureFlag>> = _flags.asStateFlow()

    fun initialize(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CACHE_KEY, null)
            ?.let { cached ->
                runCatching { parse(cached) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { _flags.value = fallbacks + it }
            }

        scope.launch {
            while (true) {
                refreshInternal(app)
                delay(REFRESH_MS)
            }
        }
    }

    fun flag(key: String): PuppyFeatureFlag =
        flags.value[key] ?: PuppyFeatureFlag(
            key = key,
            visible = false,
            enabled = false,
            status = "disabled",
            releaseDate = null,
            label = key,
            description = ""
        )

    suspend fun refreshNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        refreshInternal(context.applicationContext)
    }

    private fun refreshInternal(context: Context): Boolean {
        return try {
            val json = fetch()
            val parsed = parse(json)
            if (parsed.isEmpty()) throw IOException("Feature flag document contains no flags")
            _flags.value = fallbacks + parsed
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(CACHE_KEY, json)
                .apply()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            PuppyDebugLog.w("FeatureFlags", "Unable to refresh remote feature flags", error)
            false
        }
    }

    private fun parse(text: String): Map<String, PuppyFeatureFlag> {
        val root = JSONObject(text)
        if (root.optInt("schemaVersion", -1) != 1) throw IOException("Unsupported feature flag schema")
        val values = root.getJSONObject("flags")
        val result = linkedMapOf<String, PuppyFeatureFlag>()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.matches(Regex("[a-z0-9_]{1,64}"))) continue
            val item = values.getJSONObject(key)
            val status = item.optString("status", "disabled").lowercase()
            if (status !in setOf("released", "beta", "coming_soon", "disabled")) continue
            result[key] = PuppyFeatureFlag(
                key = key,
                visible = item.optBoolean("visible", false),
                enabled = item.optBoolean("enabled", false),
                status = status,
                releaseDate = item.optString("releaseDate").trim().ifBlank { null },
                label = item.optString("label", key).take(80),
                description = item.optString("description").take(240)
            )
        }
        return result
    }

    private fun fetch(): String {
        val connection = URL(FLAGS_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Feature flag endpoint returned HTTP " + connection.responseCode)
            }
            if (connection.contentLengthLong > MAX_BYTES) {
                throw IOException("Feature flag response is too large")
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_BYTES) {
                        throw IOException("Feature flag response is too large")
                    }
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }
}
