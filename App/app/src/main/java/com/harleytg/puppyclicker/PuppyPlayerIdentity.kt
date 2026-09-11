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

    // Reserved HarleyTG developer identity. Canonical values remain protocol/storage-safe;
    // the custom values are presentation/input aliases and are never written over the
    // device-bound identity preferences.
    private const val HARLEYTG_CANONICAL_PLAYER_ID = "PC-5AD7F57F80FBE69809F96AEA963E2426"
    private const val HARLEYTG_CANONICAL_FRIEND_CODE = "PUP-5XUV-SGZB-S9CX"
    const val HARLEYTG_PUBLIC_PLAYER_ID = "PC-HARLEYTG-DEV-0001"
    const val HARLEYTG_PUBLIC_FRIEND_CODE = "PUP-HTG-DEV-0001"

    private val playerIdRegex = Regex("^PC-[0-9A-F]{32}$")
    private val friendCodeRegex = Regex("^PUP-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$")
    private val random = SecureRandom()
    private val identityLock = Any()

    private val blockedUsernameTerms = setOf(
        "ass", "bastard", "bitch", "bullshit", "cock", "cunt", "dick",
        "fag", "faggot", "fuck", "fucker", "fucking", "motherfucker",
        "nigga", "nigger", "pedo", "pedophile", "porn", "porno", "pussy",
        "rape", "rapist", "sex", "sexy", "shit", "slut", "spic", "tranny",
        "wetback", "whore", "chink", "kike"
    )

    // These are distinctive enough to reject when embedded/obfuscated in a username.
    // Shorter or more ambiguous terms above are matched as complete username tokens only.
    private val blockedUsernameFragments = setOf(
        "bitch", "bullshit", "cunt", "faggot", "fuck", "fucker", "fucking",
        "motherfucker", "nigga", "nigger", "pedophile", "porno", "pussy",
        "rapist", "shit", "slut", "spic", "tranny", "wetback", "whore",
        "chink", "kike"
    )

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

    /**
     * Official HarleyTG aliases are deliberately presentation/input aliases.
     * Keeping the canonical values underneath preserves ownership ledgers, WebRTC
     * protocol validation and PupEye's authenticated save state.
     */
    internal fun isHarleyTgDeveloperIdentity(playerId: String, friendCode: String): Boolean =
        playerId == HARLEYTG_CANONICAL_PLAYER_ID &&
            friendCode == HARLEYTG_CANONICAL_FRIEND_CODE

    fun isHarleyTgDeveloper(context: Context): Boolean =
        isHarleyTgDeveloperIdentity(playerId(context), friendCode(context))

    /**
     * PupEye fair-play enforcement is mandatory for ordinary players.
     * The reserved HarleyTG developer identity is exempt from behavioral anti-cheat
     * cooldowns only; save encryption, authentication, recovery and integrity checks
     * remain active for every identity.
     */
    fun shouldEnforcePupEyeFairPlay(context: Context): Boolean = !isHarleyTgDeveloper(context)

    fun publicPlayerId(context: Context): String =
        if (isHarleyTgDeveloper(context)) HARLEYTG_PUBLIC_PLAYER_ID else playerId(context)

    fun publicFriendCode(context: Context): String =
        if (isHarleyTgDeveloper(context)) HARLEYTG_PUBLIC_FRIEND_CODE else friendCode(context)

    fun displayPlayerId(value: String): String =
        if (value == HARLEYTG_CANONICAL_PLAYER_ID) HARLEYTG_PUBLIC_PLAYER_ID else value

    fun displayFriendCode(value: String): String =
        if (value == HARLEYTG_CANONICAL_FRIEND_CODE) HARLEYTG_PUBLIC_FRIEND_CODE else value

    fun resolveFriendCodeInput(value: String): String? {
        val normalized = value.trim().uppercase(Locale.US)
        return when {
            normalized == HARLEYTG_PUBLIC_FRIEND_CODE -> HARLEYTG_CANONICAL_FRIEND_CODE
            friendCodeRegex.matches(normalized) -> normalized
            else -> null
        }
    }

    fun isValidFriendCodeInput(value: String): Boolean = resolveFriendCodeInput(value) != null

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

    fun usernameModerationIssue(raw: String): String? {
        val normalized = normalizeUsername(raw)
        if (normalized.isBlank()) return "Enter a valid username."

        val tokens = normalized
            .split(Regex("[._-]+"))
            .filter { it.isNotBlank() }

        if (tokens.any { it in blockedUsernameTerms }) {
            return "That username contains a word or phrase that isn't allowed."
        }

        val compact = raw
            .trim()
            .lowercase(Locale.US)
            .map { char ->
                when (char) {
                    '0' -> 'o'
                    '1', '!', '|' -> 'i'
                    '3' -> 'e'
                    '4', '@' -> 'a'
                    '5' -> 's'
                    '7', '+' -> 't'
                    else -> char
                }
            }
            .filter { it in 'a'..'z' || it in '0'..'9' }
            .joinToString("")

        if (blockedUsernameFragments.any { compact.contains(it) }) {
            return "That username contains a word or phrase that isn't allowed."
        }

        return null
    }

    fun isUsernameAllowed(raw: String): Boolean = usernameModerationIssue(raw) == null
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
        put("publicPlayerId", publicPlayerId(context))
        put("publicFriendCode", publicFriendCode(context))
        put("officialDeveloper", isHarleyTgDeveloper(context))
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
