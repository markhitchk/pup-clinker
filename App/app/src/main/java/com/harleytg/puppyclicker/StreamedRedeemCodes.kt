package com.harleytg.puppyclicker

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PuppyCodeClaimAuthorizationProbe(val authorized: Boolean)

sealed class PuppyCodeRefreshResult {
    data class Authorized(
        val snapshot: PuppyCodeCatalogSnapshot,
        val authoritativeTimeMs: Long?,
        val notModified: Boolean
    ) : PuppyCodeRefreshResult()

    data class Unavailable(val message: String) : PuppyCodeRefreshResult()
}

/**
 * GitHub-backed Puppy Code catalogue.
 *
 * Background refresh/cache exists for display resilience. New claims are different:
 * every claim must call [refreshForClaim] and receive [PuppyCodeRefreshResult.Authorized].
 * A stale cache by itself never authorizes a new redemption.
 */
internal object StreamedRedeemCodes {
    const val CATALOG_URL =
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/redeem-codes.json"

    private const val TAG = "PuppyRedeemStream"
    private const val MAX_DOWNLOAD_BYTES = 256 * 1024
    private const val REFRESH_INTERVAL_MS = 60L * 60L * 1_000L
    private const val RETRY_INTERVAL_MS = 5L * 60L * 1_000L
    private const val LOOP_INTERVAL_MS = 15L * 60L * 1_000L
    private const val NETWORK_MESSAGE =
        "Puppy Codes are temporarily unavailable. Connect to the internet and try again."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    @Volatile
    private var catalog: PuppyCodeCatalogSnapshot? = null

    fun initialize(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            loadPersistentCache(app)
            while (true) {
                refreshForDisplay(app)
                delay(LOOP_INTERVAL_MS)
            }
        }
    }

    fun snapshot(): PuppyCodeCatalogSnapshot? = catalog

    /**
     * Performs a real GitHub request for each claim. HTTP 304 is acceptable only when
     * this installation already has a validated schema-2 cache to which the ETag refers.
     */
    suspend fun refreshForClaim(context: Context): PuppyCodeRefreshResult = withContext(Dispatchers.IO) {
        performLiveRefresh(context.applicationContext, claimAuthorization = true)
    }

    /** Clears only the streamed catalogue/cache. Full app-data erasure calls this. */
    fun clearCache(context: Context) {
        catalog = null
        runCatching { cacheFile(context.applicationContext).delete() }
        prefs(context.applicationContext).edit().clear().apply()
    }

    /**
     * Temporary source-compatibility bridge while the V6 generated-source redemption
     * path is migrated to refreshForClaim. It deliberately keeps legacy behavior only
     * for the old call site and must not be used by the schema-2 claim flow.
     */
    @Deprecated("Schema-2 claims must use refreshForClaim")
    fun find(rawCode: String): LocalRedeemReward? = LocalRedeemCodes.find(rawCode)

    internal fun claimAuthorizationForTest(
        liveCheckSucceeded: Boolean,
        notModified: Boolean,
        hasValidatedCache: Boolean
    ): PuppyCodeClaimAuthorizationProbe {
        val authorized = liveCheckSucceeded && (!notModified || hasValidatedCache)
        return PuppyCodeClaimAuthorizationProbe(authorized)
    }

    /** Test hook for schema-2 catalogue consumers. */
    internal fun replaceSnapshotForTest(value: PuppyCodeCatalogSnapshot?) {
        catalog = value
    }

    private fun cacheFile(context: Context): File =
        File(context.filesDir, "redeem-code-catalog-v2.json")

    private fun prefs(context: Context) =
        context.getSharedPreferences("redeem_code_stream_v2", Context.MODE_PRIVATE)

    private fun loadPersistentCache(context: Context) {
        val file = cacheFile(context)
        if (!file.isFile || file.length() !in 1..MAX_DOWNLOAD_BYTES.toLong()) return
        try {
            catalog = PuppyCodeCatalog.parse(file.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            Log.w(TAG, "Ignoring invalid cached Puppy Code catalogue", error)
        }
    }

    private fun refreshForDisplay(context: Context) {
        val settings = prefs(context)
        val now = System.currentTimeMillis()
        val checked = settings.getLong("checked", 0L)
        val attempted = settings.getLong("attempted", 0L)
        if (catalog != null && now - checked in 0 until REFRESH_INTERVAL_MS) return
        if (now - attempted in 0 until RETRY_INTERVAL_MS) return
        performLiveRefresh(context, claimAuthorization = false)
    }

    /**
     * claimAuthorization=true bypasses background refresh throttling and always performs
     * a network request. The boolean exists to keep logging behavior explicit; neither
     * mode ever treats a network failure as claim authorization.
     */
    private fun performLiveRefresh(
        context: Context,
        claimAuthorization: Boolean
    ): PuppyCodeRefreshResult {
        val settings = prefs(context)
        val attemptedAt = System.currentTimeMillis()
        settings.edit().putLong("attempted", attemptedAt).apply()

        val connection = URL(CATALOG_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android")

            val current = catalog
            val etag = settings.getString("etag", null)
            if (current != null && !etag.isNullOrBlank()) {
                connection.setRequestProperty("If-None-Match", etag)
            }

            val responseCode = connection.responseCode
            val authoritativeTimeMs = parseHttpDate(connection.getHeaderField("Date"))
            return when (responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    val cached = catalog
                    if (cached == null) {
                        Log.w(TAG, "GitHub returned 304 without a validated schema-2 cache")
                        PuppyCodeRefreshResult.Unavailable(NETWORK_MESSAGE)
                    } else {
                        settings.edit().putLong("checked", attemptedAt).apply()
                        PuppyCodeRefreshResult.Authorized(cached, authoritativeTimeMs, notModified = true)
                    }
                }

                HttpURLConnection.HTTP_OK -> {
                    val length = connection.contentLengthLong
                    if (length > MAX_DOWNLOAD_BYTES) throw IOException("Puppy Code catalogue exceeds size limit")
                    val bytes = readBounded(connection)
                    val text = bytes.toString(Charsets.UTF_8)
                    val parsed = PuppyCodeCatalog.parse(text)
                    persist(context, text)
                    catalog = parsed
                    settings.edit()
                        .putString("etag", connection.getHeaderField("ETag"))
                        .putLong("checked", attemptedAt)
                        .apply()
                    PuppyCodeRefreshResult.Authorized(parsed, authoritativeTimeMs, notModified = false)
                }

                else -> throw IOException("Puppy Code catalogue returned HTTP $responseCode")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val purpose = if (claimAuthorization) "claim validation" else "background refresh"
            Log.w(TAG, "Puppy Code $purpose failed; no claim authorization issued", error)
            return PuppyCodeRefreshResult.Unavailable(NETWORK_MESSAGE)
        } finally {
            connection.disconnect()
        }
    }

    private fun persist(context: Context, text: String) {
        val target = cacheFile(context)
        target.parentFile?.mkdirs()
        val temp = File.createTempFile("redeem-catalog-v2", ".tmp", target.parentFile)
        try {
            temp.writeText(text, Charsets.UTF_8)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    private fun readBounded(connection: HttpURLConnection): ByteArray {
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_DOWNLOAD_BYTES) {
                    throw IOException("Puppy Code catalogue exceeds size limit")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun parseHttpDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
