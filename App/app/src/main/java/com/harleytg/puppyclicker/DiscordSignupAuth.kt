package com.harleytg.puppyclicker

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Base64
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class DiscordPlayerAccount(
    val id: String,
    val username: String,
    val globalName: String?,
    val avatarHash: String?,
    val email: String?
) {
    val displayName: String
        get() = globalName?.takeIf { it.isNotBlank() } ?: username
}

internal enum class DiscordGuildRole(val label: String) {
    DEVELOPER("Developer"),
    ADMIN("Admin"),
    PUP_MEMBER("Pup Member"),
    GUEST("Guest")
}

internal data class DiscordGuildAccess(
    val guildId: String,
    val role: DiscordGuildRole,
    val verifiedAtMs: Long
)

internal enum class DiscordSignupPhase {
    IDLE,
    AUTHORIZING,
    EXCHANGING,
    CONNECTED,
    ERROR
}

internal data class DiscordSignupState(
    val phase: DiscordSignupPhase = DiscordSignupPhase.IDLE,
    val account: DiscordPlayerAccount? = null,
    val guildAccess: DiscordGuildAccess? = null,
    val message: String? = null
)

/**
 * Discord-backed Puppy Clicker signup.
 *
 * The app is a public mobile OAuth client and therefore uses PKCE. Authorization requests use
 * identify, email, guilds, guilds.join, and guilds.members.read. The Discord access token is
 * intentionally kept only long enough for the current authorization session and is never persisted.
 * After PKCE exchange it is sent once to Puppy Clicker's Supabase Pupeye Edge Function, where the
 * Discord account and configured guild role are independently verified before being trusted.
 *
 * Puppy Clicker's Player ID and Friend Code remain device-bound and independent of Discord.
 */
internal object DiscordSignupAuth {
    val CLIENT_ID: String get() = BuildConfig.DISCORD_CLIENT_ID
    const val CALLBACK_SCHEME = "discord-1547982688898777118"
    const val REDIRECT_URI = "$CALLBACK_SCHEME:/authorize/callback"
    const val OAUTH_SCOPES = "identify email guilds guilds.join guilds.members.read"

    val GUILD_ID: String get() = BuildConfig.DISCORD_GUILD_ID
    val ROLE_DEVELOPER_ID: String get() = BuildConfig.DISCORD_ROLE_DEVELOPER_ID
    val ROLE_ADMIN_ID: String get() = BuildConfig.DISCORD_ROLE_ADMIN_ID
    val ROLE_PUP_MEMBERS_ID: String get() = BuildConfig.DISCORD_ROLE_PUP_MEMBERS_ID
    val ROLE_GUEST_ID: String get() = BuildConfig.DISCORD_ROLE_GUEST_ID

    private const val PREFS = "puppy_discord_signup_v1"
    private const val KEY_DISCORD_ID = "discord_id"
    private const val KEY_USERNAME = "discord_username"
    private const val KEY_GLOBAL_NAME = "discord_global_name"
    private const val KEY_AVATAR_HASH = "discord_avatar_hash"
    private const val KEY_EMAIL = "discord_email"
    private const val KEY_LINKED_AT = "discord_linked_at"
    private const val KEY_GUILD_ID = "discord_guild_id"
    private const val KEY_GUILD_ROLE = "discord_guild_role"
    private const val KEY_GUILD_VERIFIED_AT = "discord_guild_verified_at"
    private const val KEY_AUTH_V2_MIGRATION_NOTICE_SHOWN = "discord_auth_v2_migration_notice_shown"
    private const val KEY_PENDING_STATE = "oauth_pending_state"
    private const val KEY_PENDING_VERIFIER = "oauth_pending_verifier"
    private const val KEY_PENDING_EXPECTED_DISCORD_ID = "oauth_pending_expected_discord_id"
    private const val KEY_PENDING_GUILD_VERIFY = "oauth_pending_guild_verify"

    private const val AUTHORIZE_ENDPOINT = "https://discord.com/oauth2/authorize"
    private const val TOKEN_ENDPOINT = "https://discord.com/api/oauth2/token"
    private const val CURRENT_USER_ENDPOINT = "https://discord.com/api/v10/users/@me"

    private val random = SecureRandom()
    private val mutableState = MutableStateFlow(DiscordSignupState())
    @Volatile private var initialized = false

    fun observe(context: Context): StateFlow<DiscordSignupState> {
        ensure(context.applicationContext)
        return mutableState.asStateFlow()
    }

    fun account(context: Context): DiscordPlayerAccount? {
        ensure(context.applicationContext)
        return mutableState.value.account
    }

    fun isConnected(context: Context): Boolean = account(context) != null

    internal fun hasPersistedAccount(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_DISCORD_ID, null).isNullOrBlank() &&
            !prefs.getString(KEY_USERNAME, null).isNullOrBlank()
    }

    fun isCallback(uri: Uri?): Boolean =
        uri != null &&
            uri.scheme.equals(CALLBACK_SCHEME, ignoreCase = true) &&
            uri.path == "/authorize/callback"

    fun startSignup(
        context: Context,
        expectedDiscordId: String? = null,
        verifyGuildRole: Boolean = false
    ) {
        val app = context.applicationContext
        ensure(app)

        val normalizedExpectedId = expectedDiscordId?.trim().orEmpty()
        if (verifyGuildRole && !isDiscordSnowflake(normalizedExpectedId)) {
            mutableState.value = DiscordSignupState(
                phase = DiscordSignupPhase.ERROR,
                account = mutableState.value.account,
                guildAccess = mutableState.value.guildAccess,
                message = "Enter a valid Discord user ID before server verification."
            )
            return
        }

        val verifier = generateCodeVerifier()
        val challenge = codeChallenge(verifier)
        val oauthState = randomUrlSafe(24)

        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_STATE, oauthState)
            .putString(KEY_PENDING_VERIFIER, verifier)
            .apply {
                if (verifyGuildRole) {
                    putString(KEY_PENDING_EXPECTED_DISCORD_ID, normalizedExpectedId)
                    putBoolean(KEY_PENDING_GUILD_VERIFY, true)
                } else {
                    remove(KEY_PENDING_EXPECTED_DISCORD_ID)
                    remove(KEY_PENDING_GUILD_VERIFY)
                }
            }
            .apply()

        mutableState.value = DiscordSignupState(
            phase = DiscordSignupPhase.AUTHORIZING,
            account = mutableState.value.account,
            guildAccess = mutableState.value.guildAccess,
            message = if (verifyGuildRole) {
                "Finish Discord authorization to verify your Puppy Clicker server role."
            } else {
                "Finish authorization in Discord."
            }
        )

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(buildAuthorizationUrl(oauthState, challenge))).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                clearPending(app)
                mutableState.value = DiscordSignupState(
                    phase = DiscordSignupPhase.ERROR,
                    account = mutableState.value.account,
                    guildAccess = mutableState.value.guildAccess,
                    message = "Unable to open Discord authorization."
                )
            }
    }

    suspend fun handleRedirect(context: Context, uri: Uri): Boolean {
        val app = context.applicationContext
        ensure(app)
        if (!isCallback(uri)) return false

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expectedState = prefs.getString(KEY_PENDING_STATE, null)
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null)
        val expectedDiscordId = prefs.getString(KEY_PENDING_EXPECTED_DISCORD_ID, null)
        val verifyGuildRole = prefs.getBoolean(KEY_PENDING_GUILD_VERIFY, false)
        val returnedState = uri.getQueryParameter("state")

        val oauthError = uri.getQueryParameter("error")
        if (!oauthError.isNullOrBlank()) {
            clearPending(app)
            mutableState.value = DiscordSignupState(
                phase = DiscordSignupPhase.ERROR,
                account = mutableState.value.account,
                message = if (oauthError == "access_denied") {
                    "Discord signup was cancelled."
                } else {
                    "Discord authorization failed."
                }
            )
            return true
        }

        if (expectedState.isNullOrBlank() || returnedState != expectedState || verifier.isNullOrBlank()) {
            clearPending(app)
            mutableState.value = DiscordSignupState(
                phase = DiscordSignupPhase.ERROR,
                account = mutableState.value.account,
                message = "Discord signup could not be verified. Please try again."
            )
            return true
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            clearPending(app)
            mutableState.value = DiscordSignupState(
                phase = DiscordSignupPhase.ERROR,
                account = mutableState.value.account,
                message = "Discord did not return an authorization code."
            )
            return true
        }

        mutableState.value = DiscordSignupState(
            phase = DiscordSignupPhase.EXCHANGING,
            account = mutableState.value.account,
            message = "Verifying Discord account…"
        )

        return withContext(Dispatchers.IO) {
            try {
                val accessToken = exchangeAuthorizationCode(code, verifier)
                val verified = SupabasePupEyeClient.authenticateDiscord(app, accessToken)
                val account = verified.account

                if (verifyGuildRole && account.id != expectedDiscordId) {
                    clearPending(app)
                    mutableState.value = DiscordSignupState(
                        phase = DiscordSignupPhase.ERROR,
                        account = mutableState.value.account,
                        guildAccess = mutableState.value.guildAccess,
                        message = "The authorized Discord account does not match the Discord ID you entered."
                    )
                    return@withContext true
                }

                val guildAccess = verified.guildAccess

                saveAccount(app, account)
                if (guildAccess != null) saveGuildAccess(app, guildAccess) else clearGuildAccess(app)
                syncPlayerUsername(app, account)
                clearPending(app)
                mutableState.value = DiscordSignupState(
                    phase = DiscordSignupPhase.CONNECTED,
                    account = account,
                    guildAccess = guildAccess,
                    message = verified.message
                )
                true
            } catch (error: Exception) {
                clearPending(app)
                mutableState.value = DiscordSignupState(
                    phase = DiscordSignupPhase.ERROR,
                    account = mutableState.value.account,
                    guildAccess = mutableState.value.guildAccess,
                    message = error.message?.take(180)
                        ?: "Discord signup could not be completed. Check your connection and try again."
                )
                true
            }
        }
    }

    fun disconnect(context: Context) {
        val app = context.applicationContext
        SupabasePupEyeClient.unlinkDiscordAsync(app)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DISCORD_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_GLOBAL_NAME)
            .remove(KEY_AVATAR_HASH)
            .remove(KEY_EMAIL)
            .remove(KEY_LINKED_AT)
            .remove(KEY_GUILD_ID)
            .remove(KEY_GUILD_ROLE)
            .remove(KEY_GUILD_VERIFIED_AT)
            .remove(KEY_PENDING_STATE)
            .remove(KEY_PENDING_VERIFIER)
            .remove(KEY_PENDING_EXPECTED_DISCORD_ID)
            .remove(KEY_PENDING_GUILD_VERIFY)
            .apply()
        mutableState.value = DiscordSignupState(
            phase = DiscordSignupPhase.IDLE,
            account = null,
            guildAccess = null,
            message = "Discord account disconnected. Local game progress was not deleted."
        )
    }

    internal fun buildAuthorizationUrl(oauthState: String, codeChallenge: String): String {
        val params = linkedMapOf(
            "client_id" to CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "scope" to OAUTH_SCOPES,
            "state" to oauthState,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256"
        )
        return AUTHORIZE_ENDPOINT + "?" + params.entries.joinToString("&") {
            formEncode(it.key) + "=" + formEncode(it.value)
        }
    }

    internal fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val account = readAccount(context)
            val guildAccess = readGuildAccess(context)
                ?.takeIf { SupabasePupEyeClient.hasSession(context) }
            mutableState.value = DiscordSignupState(
                phase = if (account != null) DiscordSignupPhase.CONNECTED else DiscordSignupPhase.IDLE,
                account = account,
                guildAccess = guildAccess
            )
            notifyLegacyDiscordAuthUpgradeIfNeeded(context, account, guildAccess)
            initialized = true
        }
    }

    internal fun shouldNotifyLegacyAuthUpgrade(
        account: DiscordPlayerAccount?,
        guildAccess: DiscordGuildAccess?,
        noticeShown: Boolean
    ): Boolean = account != null && guildAccess == null && !noticeShown

    private fun notifyLegacyDiscordAuthUpgradeIfNeeded(
        context: Context,
        account: DiscordPlayerAccount?,
        guildAccess: DiscordGuildAccess?
    ) {
        if (ExistingPlayerPupEyeUpgrade.wasApplied(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown = prefs.getBoolean(KEY_AUTH_V2_MIGRATION_NOTICE_SHOWN, false)
        if (!shouldNotifyLegacyAuthUpgrade(account, guildAccess, shown)) return

        val noticeId = "discord-auth-v2-reauthorize"
        PuppyNotificationHistory.record(
            context,
            PuppyNotificationItem(
                id = noticeId,
                type = PuppyNotificationType.APP_UPDATE,
                title = "Discord verification updated",
                body = "You connected Discord before server-role verification was added. Re-authorize in Settings → Discord to verify your server role and unlock Discord Pup if you are a Pup Member.",
                createdAtMs = System.currentTimeMillis(),
                read = false,
                route = PuppyNotificationRoute.NONE
            )
        )
        PuppyNotificationCenter.notifyDiscordAuthUpgrade(context)
        if (PuppyNotificationHistory.findById(context, noticeId) != null) {
            prefs.edit().putBoolean(KEY_AUTH_V2_MIGRATION_NOTICE_SHOWN, true).apply()
        }
    }

    private fun readAccount(context: Context): DiscordPlayerAccount? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_DISCORD_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        return DiscordPlayerAccount(
            id = id,
            username = username,
            globalName = prefs.getString(KEY_GLOBAL_NAME, null),
            avatarHash = prefs.getString(KEY_AVATAR_HASH, null),
            email = prefs.getString(KEY_EMAIL, null)
        )
    }

    private fun readGuildAccess(context: Context): DiscordGuildAccess? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val guildId = prefs.getString(KEY_GUILD_ID, null)?.takeIf { it == GUILD_ID } ?: return null
        val role = prefs.getString(KEY_GUILD_ROLE, null)
            ?.let { runCatching { DiscordGuildRole.valueOf(it) }.getOrNull() }
            ?: return null
        val verifiedAt = prefs.getLong(KEY_GUILD_VERIFIED_AT, 0L)
        if (verifiedAt <= 0L) return null
        return DiscordGuildAccess(guildId = guildId, role = role, verifiedAtMs = verifiedAt)
    }

    private fun saveAccount(context: Context, account: DiscordPlayerAccount) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISCORD_ID, account.id)
            .putString(KEY_USERNAME, account.username)
            .putString(KEY_GLOBAL_NAME, account.globalName)
            .putString(KEY_AVATAR_HASH, account.avatarHash)
            .putString(KEY_EMAIL, account.email)
            .putLong(KEY_LINKED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun saveGuildAccess(context: Context, access: DiscordGuildAccess) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GUILD_ID, access.guildId)
            .putString(KEY_GUILD_ROLE, access.role.name)
            .putLong(KEY_GUILD_VERIFIED_AT, access.verifiedAtMs)
            .apply()
    }

    private fun clearGuildAccess(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_GUILD_ID)
            .remove(KEY_GUILD_ROLE)
            .remove(KEY_GUILD_VERIFIED_AT)
            .apply()
    }

    private fun syncPlayerUsername(context: Context, account: DiscordPlayerAccount) {
        val preferred = account.username
        val safeUsername = if (PuppyPlayerIdentity.isUsernameAllowed(preferred)) {
            preferred
        } else {
            "player_" + account.id.takeLast(8)
        }
        PuppyPlayerIdentity.setUsername(context, safeUsername)
    }

    private fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_STATE)
            .remove(KEY_PENDING_VERIFIER)
            .remove(KEY_PENDING_EXPECTED_DISCORD_ID)
            .remove(KEY_PENDING_GUILD_VERIFY)
            .apply()
    }

    private fun generateCodeVerifier(): String = randomUrlSafe(48)

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun exchangeAuthorizationCode(code: String, verifier: String): String {
        val form = linkedMapOf(
            "client_id" to CLIENT_ID,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to REDIRECT_URI,
            "code_verifier" to verifier
        ).entries.joinToString("&") {
            formEncode(it.key) + "=" + formEncode(it.value)
        }

        val connection = (URL(TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        connection.outputStream.use { it.write(form.toByteArray(StandardCharsets.UTF_8)) }
        val payload = readJsonResponse(connection, "Discord token exchange")
        return payload.optString("access_token").takeIf { it.isNotBlank() }
            ?: error("Discord token exchange returned no access token")
    }

    private fun fetchCurrentGuildAccess(accessToken: String): DiscordGuildAccess? {
        if (GUILD_ID.isBlank()) return null
        val connection = (URL("https://discord.com/api/v10/users/@me/guilds/$GUILD_ID/member").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            connection.errorStream?.close()
            connection.disconnect()
            return null
        }

        val payload = readJsonResponse(connection, "Discord guild member request")
        val rolesJson = payload.optJSONArray("roles") ?: return null
        val roleIds = buildSet {
            for (index in 0 until rolesJson.length()) {
                rolesJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        val role = classifyGuildRole(roleIds) ?: return null
        return DiscordGuildAccess(
            guildId = GUILD_ID,
            role = role,
            verifiedAtMs = System.currentTimeMillis()
        )
    }

    internal fun classifyGuildRole(roleIds: Set<String>): DiscordGuildRole? = when {
        ROLE_DEVELOPER_ID.isNotBlank() && ROLE_DEVELOPER_ID in roleIds -> DiscordGuildRole.DEVELOPER
        ROLE_ADMIN_ID.isNotBlank() && ROLE_ADMIN_ID in roleIds -> DiscordGuildRole.ADMIN
        ROLE_PUP_MEMBERS_ID.isNotBlank() && ROLE_PUP_MEMBERS_ID in roleIds -> DiscordGuildRole.PUP_MEMBER
        ROLE_GUEST_ID.isNotBlank() && ROLE_GUEST_ID in roleIds -> DiscordGuildRole.GUEST
        else -> null
    }

    private fun configuredPuppyIdFor(role: DiscordGuildRole): String? {
        val configured = when (role) {
            DiscordGuildRole.DEVELOPER -> BuildConfig.DISCORD_UNLOCK_DEVELOPER_PUPPY_ID
            DiscordGuildRole.ADMIN -> BuildConfig.DISCORD_UNLOCK_ADMIN_PUPPY_ID
            DiscordGuildRole.PUP_MEMBER -> BuildConfig.DISCORD_UNLOCK_PUP_MEMBERS_PUPPY_ID
            DiscordGuildRole.GUEST -> BuildConfig.DISCORD_UNLOCK_GUEST_PUPPY_ID
        }.trim()
        return configured.takeIf { it.isNotBlank() && it in V2_PUPPY_IDS }
    }

    internal fun unlockPuppyIdsFor(role: DiscordGuildRole): Set<String> {
        val effectiveRoles = if (role == DiscordGuildRole.DEVELOPER) {
            listOf(
                DiscordGuildRole.DEVELOPER,
                DiscordGuildRole.ADMIN,
                DiscordGuildRole.PUP_MEMBER,
                DiscordGuildRole.GUEST
            )
        } else {
            listOf(role)
        }
        return effectiveRoles
            .mapNotNull(::configuredPuppyIdFor)
            .toCollection(linkedSetOf())
    }

    internal fun unlockPuppyIdFor(role: DiscordGuildRole): String? =
        unlockPuppyIdsFor(role).firstOrNull()

    internal fun isDiscordSnowflake(value: String): Boolean =
        value.length in 17..20 && value.all(Char::isDigit)

    private fun fetchCurrentUser(accessToken: String): DiscordPlayerAccount {
        val connection = (URL(CURRENT_USER_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val payload = readJsonResponse(connection, "Discord profile request")
        val id = payload.optString("id").takeIf { it.isNotBlank() }
            ?: error("Discord profile returned no user ID")
        val username = payload.optString("username").takeIf { it.isNotBlank() }
            ?: error("Discord profile returned no username")
        return DiscordPlayerAccount(
            id = id,
            username = username,
            globalName = payload.optString("global_name").takeIf { it.isNotBlank() && it != "null" },
            avatarHash = payload.optString("avatar").takeIf { it.isNotBlank() && it != "null" },
            email = payload.optString("email").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun readJsonResponse(connection: HttpURLConnection, operation: String): JSONObject {
        val status = connection.responseCode
        val stream: InputStream? = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
        }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) error("$operation failed with HTTP $status")
        return JSONObject(body)
    }

    private fun formEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
