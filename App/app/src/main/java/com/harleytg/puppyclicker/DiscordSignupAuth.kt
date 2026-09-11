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
    val avatarHash: String?
) {
    val displayName: String
        get() = globalName?.takeIf { it.isNotBlank() } ?: username
}

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
    val message: String? = null
)

/**
 * Discord-backed Puppy Clicker signup.
 *
 * The app is a public mobile OAuth client and therefore uses PKCE. The Discord access token is
 * intentionally kept only long enough to call /users/@me and is never persisted. Puppy Clicker's
 * Player ID and Friend Code remain device-bound and independent of Discord.
 */
internal object DiscordSignupAuth {
    const val CLIENT_ID = "1547982688898777118"
    const val CALLBACK_SCHEME = "discord-1547982688898777118"
    const val REDIRECT_URI = "$CALLBACK_SCHEME:/authorize/callback"

    private const val PREFS = "puppy_discord_signup_v1"
    private const val KEY_DISCORD_ID = "discord_id"
    private const val KEY_USERNAME = "discord_username"
    private const val KEY_GLOBAL_NAME = "discord_global_name"
    private const val KEY_AVATAR_HASH = "discord_avatar_hash"
    private const val KEY_LINKED_AT = "discord_linked_at"
    private const val KEY_PENDING_STATE = "oauth_pending_state"
    private const val KEY_PENDING_VERIFIER = "oauth_pending_verifier"

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

    fun isCallback(uri: Uri?): Boolean =
        uri != null &&
            uri.scheme.equals(CALLBACK_SCHEME, ignoreCase = true) &&
            uri.path == "/authorize/callback"

    fun startSignup(context: Context) {
        val app = context.applicationContext
        ensure(app)

        val verifier = generateCodeVerifier()
        val challenge = codeChallenge(verifier)
        val oauthState = randomUrlSafe(24)

        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_STATE, oauthState)
            .putString(KEY_PENDING_VERIFIER, verifier)
            .apply()

        mutableState.value = DiscordSignupState(
            phase = DiscordSignupPhase.AUTHORIZING,
            account = mutableState.value.account,
            message = "Finish authorization in Discord."
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
                val account = fetchCurrentUser(accessToken)
                saveAccount(app, account)
                syncPlayerUsername(app, account)
                clearPending(app)
                mutableState.value = DiscordSignupState(
                    phase = DiscordSignupPhase.CONNECTED,
                    account = account,
                    message = "Discord account connected."
                )
                true
            } catch (_: Exception) {
                clearPending(app)
                mutableState.value = DiscordSignupState(
                    phase = DiscordSignupPhase.ERROR,
                    account = mutableState.value.account,
                    message = "Discord signup could not be completed. Check your connection and try again."
                )
                true
            }
        }
    }

    fun disconnect(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DISCORD_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_GLOBAL_NAME)
            .remove(KEY_AVATAR_HASH)
            .remove(KEY_LINKED_AT)
            .remove(KEY_PENDING_STATE)
            .remove(KEY_PENDING_VERIFIER)
            .apply()
        mutableState.value = DiscordSignupState(
            phase = DiscordSignupPhase.IDLE,
            account = null,
            message = "Discord account disconnected. Local game progress was not deleted."
        )
    }

    internal fun buildAuthorizationUrl(oauthState: String, codeChallenge: String): String {
        val params = linkedMapOf(
            "client_id" to CLIENT_ID,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "scope" to "identify",
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
            mutableState.value = DiscordSignupState(
                phase = if (account != null) DiscordSignupPhase.CONNECTED else DiscordSignupPhase.IDLE,
                account = account
            )
            initialized = true
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
            avatarHash = prefs.getString(KEY_AVATAR_HASH, null)
        )
    }

    private fun saveAccount(context: Context, account: DiscordPlayerAccount) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISCORD_ID, account.id)
            .putString(KEY_USERNAME, account.username)
            .putString(KEY_GLOBAL_NAME, account.globalName)
            .putString(KEY_AVATAR_HASH, account.avatarHash)
            .putLong(KEY_LINKED_AT, System.currentTimeMillis())
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
            avatarHash = payload.optString("avatar").takeIf { it.isNotBlank() && it != "null" }
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
