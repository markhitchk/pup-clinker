package com.harleytg.puppyclicker

import android.content.Context
import android.os.Build
import java.util.Locale
import org.json.JSONObject

/**
 * Minimal local identity metadata used inside encrypted Puppy Clicker saves.
 * No IMEI, serial number, Android ID, advertising ID, phone number, or account token is stored.
 */
internal object PuppyPlayerIdentity {
    private const val PREFS = "puppy_player_identity_v1"
    private const val KEY_USERNAME = "username"
    private const val DEFAULT_USERNAME = "localplayer"
    private const val MAX_USERNAME_LENGTH = 24

    fun username(context: Context): String =
        normalizeUsername(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_USERNAME, DEFAULT_USERNAME)
                .orEmpty()
        ).ifBlank { DEFAULT_USERNAME }

    fun setUsername(context: Context, raw: String): String {
        val normalized = normalizeUsername(raw).ifBlank { DEFAULT_USERNAME }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERNAME, normalized)
            .apply()
        return normalized
    }

    fun normalizeUsername(raw: String): String = raw
        .trim()
        .lowercase(Locale.US)
        .map { char ->
            when {
                char in 'a'..'z' || char in '0'..'9' -> char
                char == '_' || char == '-' || char == '.' -> char
                char.isWhitespace() -> '_'
                else -> '_'
            }
        }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_', '.', '-')
        .take(MAX_USERNAME_LENGTH)

    fun deviceModel(): String {
        val manufacturer = sanitizeDevicePart(Build.MANUFACTURER)
        val model = sanitizeDevicePart(Build.MODEL)
        return listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("-")
            .ifBlank { "android-device" }
            .take(48)
    }

    fun metadata(context: Context): JSONObject = JSONObject().apply {
        put("username", username(context))
        put("deviceModel", deviceModel())
        put("platform", "android")
    }

    fun applyImportedUsername(context: Context, metadata: JSONObject?) {
        val imported = metadata?.optString("username")?.takeIf { it.isNotBlank() } ?: return
        setUsername(context, imported)
    }

    private fun sanitizeDevicePart(raw: String): String = raw
        .trim()
        .lowercase(Locale.US)
        .map { char -> if (char in 'a'..'z' || char in '0'..'9') char else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
}
