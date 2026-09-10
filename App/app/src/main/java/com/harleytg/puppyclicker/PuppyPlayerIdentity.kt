package com.harleytg.puppyclicker

import android.content.Context
import android.os.Build
import java.security.SecureRandom
import java.util.Locale
import org.json.JSONObject

/**
 * Local Puppy Clicker identity metadata.
 *
 * Username is editable presentation data. Player ID and Friend Code are generated once
 * for this local device identity and are deliberately not replaced by save imports.
 * No IMEI, serial number, Android ID, advertising ID, phone number, or account token is stored.
 */
internal object PuppyPlayerIdentity {
    private const val PREFS = "puppy_player_identity_v1"
    private const val KEY_USERNAME = "username"
    private const val KEY_PLAYER_ID = "player_id"
    private const val KEY_FRIEND_CODE = "friend_code"
    private const val DEFAULT_USERNAME = "localplayer"
    private const val MAX_USERNAME_LENGTH = 24
    private const val PLAYER_ID_BYTES = 16
    private const val FRIEND_CODE_CHARS = 12
    private const val FRIEND_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val playerIdRegex = Regex("^PC-[0-9A-F]{32}$")
    private val friendCodeRegex = Regex("^PUP-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$")
    private val random = SecureRandom()
    private val identityLock = Any()

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

    /** Permanent internal identity for this local Puppy Clicker installation/device identity. */
    fun playerId(context: Context): String = synchronized(identityLock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_PLAYER_ID, null)
            ?.takeIf(::isValidPlayerId)
            ?: generatePlayerId().also { generated ->
                check(prefs.edit().putString(KEY_PLAYER_ID, generated).commit()) {
                    "Unable to persist Puppy Clicker Player ID"
                }
            }
    }

    /** Permanent public code used to identify the expected peer during manual WebRTC setup. */
    fun friendCode(context: Context): String = synchronized(identityLock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_FRIEND_CODE, null)
            ?.takeIf(::isValidFriendCode)
            ?: generateFriendCode().also { generated ->
                check(prefs.edit().putString(KEY_FRIEND_CODE, generated).commit()) {
                    "Unable to persist Puppy Clicker Friend Code"
                }
            }
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

    fun isValidPlayerId(value: String): Boolean = playerIdRegex.matches(value)

    fun isValidFriendCode(value: String): Boolean = friendCodeRegex.matches(value)

    internal fun generatePlayerId(): String {
        val bytes = ByteArray(PLAYER_ID_BYTES).also(random::nextBytes)
        return buildString(3 + PLAYER_ID_BYTES * 2) {
            append("PC-")
            bytes.forEach { byte -> append("%02X".format(Locale.US, byte.toInt() and 0xFF)) }
        }
    }

    internal fun generateFriendCode(): String {
        val raw = buildString(FRIEND_CODE_CHARS) {
            repeat(FRIEND_CODE_CHARS) {
                append(FRIEND_ALPHABET[random.nextInt(FRIEND_ALPHABET.length)])
            }
        }
        return "PUP-${raw.substring(0, 4)}-${raw.substring(4, 8)}-${raw.substring(8, 12)}"
    }

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
        put("playerId", playerId(context))
        put("friendCode", friendCode(context))
        put("deviceModel", deviceModel())
        put("platform", "android")
    }

    /**
     * Save imports may update the display username but must never replace the destination
     * device's Player ID or Friend Code. Identity migration is intentionally a separate concern.
     */
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
