package com.harleytg.puppyclicker

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class PuppyRemoteFeatureFlag(
    val key: String,
    val visible: Boolean,
    val enabled: Boolean,
    val status: String,
    val releaseDate: LocalDate?,
    val label: String,
    val description: String
) {
    fun isAvailable(today: LocalDate = LocalDate.now()): Boolean {
        val releasedState = status.lowercase(Locale.US) in setOf("released", "beta")
        return visible &&
            enabled &&
            releasedState &&
            (releaseDate == null || !today.isBefore(releaseDate))
    }

    fun badgeText(today: LocalDate = LocalDate.now()): String {
        val date = releaseDate
        if (date != null && today.isBefore(date)) {
            val formatter = DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
            return "Releases " + date.format(formatter)
        }

        return when (status.lowercase(Locale.US)) {
            "coming_soon" -> "Coming Soon"
            "beta" -> "Beta"
            "disabled" -> "Disabled"
            "released" -> if (enabled) "Available" else "Disabled"
            else -> if (enabled) "Available" else "Unavailable"
        }
    }
}

internal data class PuppyFeatureFlagSnapshot(
    val flags: Map<String, PuppyRemoteFeatureFlag>,
    val updatedAt: String?,
    val fetchedAtMs: Long,
    val remote: Boolean
) {
    fun flag(key: String): PuppyRemoteFeatureFlag =
        flags[key] ?: PuppyRemoteFeatureFlag(
            key = key,
            visible = false,
            enabled = false,
            status = "disabled",
            releaseDate = null,
            label = key,
            description = ""
        )
}

/**
 * Repository-backed feature availability.
 *
 * This can remotely show, hide, enable, disable, or date-gate code paths that are
 * already included in the installed APK. It cannot download or execute new app code.
 */
internal object PuppyFeatureFlags {
    const val REMOTE_URL =
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/FlagSys/flags.json"

    private const val PREFS = "puppy_feature_flags_v1"
    private const val KEY_JSON = "last_good_json"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val MAX_BYTES = 128 * 1024
    private const val SCHEMA_VERSION = 1

    fun cached(context: Context): PuppyFeatureFlagSnapshot {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null)
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L).coerceAtLeast(0L)

        return if (raw != null) {
            parse(raw, fetchedAtMs = fetchedAt, remote = false) ?: defaults()
        } else {
            defaults()
        }
    }

    suspend fun refresh(context: Context): PuppyFeatureFlagSnapshot =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val fallback = cached(app)

            runCatching {
                val connection = (URL(REMOTE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "PuppyClicker-FeatureFlags/1")
                }

                try {
                    require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                        "Feature flag request failed: HTTP " + connection.responseCode
                    }
                    val length = connection.contentLengthLong
                    require(length <= 0L || length <= MAX_BYTES) {
                        "Feature flag response is too large"
                    }

                    val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                        it.readText()
                    }
                    require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
                        "Feature flag response is too large"
                    }

                    val fetchedAt = System.currentTimeMillis()
                    val snapshot = parse(
                        raw = raw,
                        fetchedAtMs = fetchedAt,
                        remote = true
                    ) ?: error("Invalid feature flag document")

                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_JSON, raw)
                        .putLong(KEY_FETCHED_AT, fetchedAt)
                        .apply()

                    snapshot
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { fallback }
        }

    private fun parse(
        raw: String,
        fetchedAtMs: Long,
        remote: Boolean
    ): PuppyFeatureFlagSnapshot? = runCatching {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION)

        val sourceFlags = root.getJSONObject("flags")
        val parsed = linkedMapOf<String, PuppyRemoteFeatureFlag>()
        val keys = sourceFlags.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val entry = sourceFlags.getJSONObject(key)
            val releaseDate = entry
                .optString("releaseDate")
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let(LocalDate::parse)

            parsed[key] = PuppyRemoteFeatureFlag(
                key = key,
                visible = entry.optBoolean("visible", true),
                enabled = entry.optBoolean("enabled", false),
                status = entry.optString("status", "disabled"),
                releaseDate = releaseDate,
                label = entry.optString("label", key),
                description = entry.optString("description", "")
            )
        }

        PuppyFeatureFlagSnapshot(
            flags = parsed,
            updatedAt = root.optString("updatedAt").takeIf { it.isNotBlank() },
            fetchedAtMs = fetchedAtMs,
            remote = remote
        )
    }.getOrNull()

    private fun defaults(): PuppyFeatureFlagSnapshot =
        PuppyFeatureFlagSnapshot(
            flags = mapOf(
                "feature_flag_system" to PuppyRemoteFeatureFlag(
                    key = "feature_flag_system",
                    visible = true,
                    enabled = true,
                    status = "released",
                    releaseDate = LocalDate.of(2026, 9, 12),
                    label = "Remote Feature Flags",
                    description = "Repository-backed remote feature availability."
                ),
                "puppy_clicker_account" to PuppyRemoteFeatureFlag(
                    key = "puppy_clicker_account",
                    visible = true,
                    enabled = false,
                    status = "coming_soon",
                    releaseDate = null,
                    label = "Puppy Clicker Account",
                    description =
                        "Online account with username, password, and Discord authentication."
                ),
                "discord_linking" to PuppyRemoteFeatureFlag(
                    key = "discord_linking",
                    visible = true,
                    enabled = true,
                    status = "released",
                    releaseDate = null,
                    label = "Discord Linking",
                    description = "Optional Discord identity connection."
                ),
                "save_restore" to PuppyRemoteFeatureFlag(
                    key = "save_restore",
                    visible = true,
                    enabled = true,
                    status = "released",
                    releaseDate = null,
                    label = "Save Restore",
                    description = "Import an existing encrypted .pupsave file."
                )
            ),
            updatedAt = null,
            fetchedAtMs = 0L,
            remote = false
        )
}
