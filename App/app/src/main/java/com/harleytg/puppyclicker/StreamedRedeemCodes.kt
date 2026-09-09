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
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Loads the redeem-code catalogue as data only. No downloaded content is executed.
 *
 * A valid downloaded/cached catalogue is authoritative: removing a hash remotely
 * revokes that code. The compiled catalogue is used only until no valid streamed
 * catalogue has ever been loaded on this installation.
 */
internal object StreamedRedeemCodes {
    const val CATALOG_URL =
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/redeem-codes.json"

    private const val TAG = "PuppyRedeemStream"
    private const val SALT = "PUPPY_CLICKER_LOCAL_2026_V1|"
    private const val SCHEMA = 1
    private const val MAX_DOWNLOAD_BYTES = 256 * 1024
    private const val MAX_REWARDS = 1_000
    private const val MAX_TREATS = 10_000_000L
    private const val REFRESH_INTERVAL_MS = 60L * 60L * 1_000L
    private const val RETRY_INTERVAL_MS = 5L * 60L * 1_000L
    private const val LOOP_INTERVAL_MS = 15L * 60L * 1_000L
    private val hashRegex = Regex("[0-9a-f]{64}")
    private val idRegex = Regex("[a-z0-9_]{1,64}")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    @Volatile
    private var catalog: Map<String, LocalRedeemReward>? = null

    fun initialize(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            loadPersistentCache(app)
            while (true) {
                refresh(app)
                delay(LOOP_INTERVAL_MS)
            }
        }
    }

    fun find(rawCode: String): LocalRedeemReward? {
        val normalized = rawCode.trim()
            .uppercase(Locale.US)
            .replace(Regex("\\s+"), "")
        if (normalized.length !in 6..64) return null
        val hash = sha256(SALT + normalized)
        val streamed = catalog
        // Once a streamed or cached catalogue exists, it is authoritative. This
        // allows remote removals to revoke codes instead of falling back to APK data.
        return if (streamed != null) streamed[hash] else LocalRedeemCodes.find(rawCode)
    }

    fun snapshot(): Map<String, LocalRedeemReward>? = catalog

    /** Test hook: null means no streamed catalogue has been loaded. */
    internal fun replaceForTest(value: Map<String, LocalRedeemReward>?) {
        catalog = value
    }

    private fun cacheFile(context: Context): File =
        File(context.filesDir, "redeem-code-catalog-v1.json")

    private fun prefs(context: Context) =
        context.getSharedPreferences("redeem_code_stream_v1", Context.MODE_PRIVATE)

    private fun loadPersistentCache(context: Context) {
        val file = cacheFile(context)
        if (!file.isFile || file.length() !in 1..MAX_DOWNLOAD_BYTES.toLong()) return
        try {
            catalog = parse(file.readText(Charsets.UTF_8))
        } catch (error: Exception) {
            Log.w(TAG, "Ignoring invalid cached redeem catalogue", error)
        }
    }

    private fun refresh(context: Context) {
        val settings = prefs(context)
        val now = System.currentTimeMillis()
        val checked = settings.getLong("checked", 0L)
        val attempted = settings.getLong("attempted", 0L)
        val hasCatalog = catalog != null
        if (hasCatalog && now - checked in 0 until REFRESH_INTERVAL_MS) return
        if (now - attempted in 0 until RETRY_INTERVAL_MS) return

        settings.edit().putLong("attempted", now).apply()
        val connection = URL(CATALOG_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android")
            val etag = settings.getString("etag", null)
            if (hasCatalog && !etag.isNullOrBlank()) {
                connection.setRequestProperty("If-None-Match", etag)
            }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    if (catalog == null) throw IOException("304 without a redeem catalogue cache")
                    settings.edit().putLong("checked", now).apply()
                }
                HttpURLConnection.HTTP_OK -> {
                    val length = connection.contentLengthLong
                    if (length > MAX_DOWNLOAD_BYTES) throw IOException("Redeem catalogue exceeds size limit")
                    val bytes = readBounded(connection)
                    val text = bytes.toString(Charsets.UTF_8)
                    val parsed = parse(text)
                    persist(context, text)
                    // Publish only after validation and durable replacement succeed.
                    catalog = parsed
                    settings.edit()
                        .putString("etag", connection.getHeaderField("ETag"))
                        .putLong("checked", now)
                        .apply()
                }
                else -> throw IOException("Redeem catalogue server returned HTTP ${connection.responseCode}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Using cached or compiled redeem catalogue", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun persist(context: Context, text: String) {
        val target = cacheFile(context)
        target.parentFile?.mkdirs()
        val temp = File.createTempFile("redeem-catalog", ".tmp", target.parentFile)
        try {
            temp.writeText(text, Charsets.UTF_8)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    internal fun parse(text: String): Map<String, LocalRedeemReward> {
        val root = JSONObject(text)
        if (root.optInt("schema", -1) != SCHEMA) throw IOException("Unsupported redeem catalogue schema")
        val array = root.getJSONArray("rewards")
        if (array.length() > MAX_REWARDS) throw IOException("Too many redeem rewards")

        val rewards = LinkedHashMap<String, LocalRedeemReward>(array.length())
        val ids = HashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            if (!item.optBoolean("enabled", true)) continue

            val hash = item.getString("hash").lowercase(Locale.US)
            if (!hashRegex.matches(hash)) throw IOException("Invalid redeem hash at index $index")
            val id = item.getString("id")
            if (!idRegex.matches(id)) throw IOException("Invalid redeem id at index $index")
            val treats = if (item.has("treats")) item.getLong("treats") else 0L
            if (treats !in 0..MAX_TREATS) throw IOException("Invalid treat reward at index $index")
            val puppyId = item.optString("puppyId", "").trim().ifEmpty { null }
            if (puppyId != null) {
                if (puppyId !in V6_PUPPY_IDS) throw IOException("Unknown puppy reward at index $index")
                // Event-only puppies can never be granted by streamed redeem data.
                if (SeasonalPuppyEvents.isSeasonal(puppyId)) continue
            }
            val message = item.getString("message").trim()
            if (message.isBlank() || message.length > 240) throw IOException("Invalid reward message at index $index")
            if (rewards.containsKey(hash)) throw IOException("Duplicate redeem hash")
            if (!ids.add(id)) throw IOException("Duplicate redeem id")

            rewards[hash] = LocalRedeemReward(id = id, treats = treats, puppyId = puppyId, message = message)
        }
        return rewards.toMap()
    }

    private fun readBounded(connection: HttpURLConnection): ByteArray {
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_DOWNLOAD_BYTES) {
                    throw IOException("Redeem catalogue exceeds size limit")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
