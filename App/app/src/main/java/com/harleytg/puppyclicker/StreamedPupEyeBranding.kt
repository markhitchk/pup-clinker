package com.harleytg.puppyclicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class PupEyeBrandingStatus(
    val ready: Boolean,
    val lastCheckedAtMs: Long,
    val lastAttemptedAtMs: Long,
    val source: String?
)

internal fun nextPupEyeBrandingRetryDelayMs(
    hasBitmap: Boolean,
    consecutiveFailures: Int
): Long? {
    if (hasBitmap) return null
    return when (consecutiveFailures.coerceAtLeast(1)) {
        1 -> 15_000L
        2 -> 30_000L
        else -> 60_000L
    }
}

/**
 * Streamed-only PupEye branding.
 *
 * Source of truth:
 * https://github.com/markhitchk/pup-clinker/blob/main/assets/PupEye.png
 *
 * PupEye.png is never bundled into the APK. The app downloads the repository asset at runtime,
 * validates it as PNG data, downsamples oversized source images safely, and keeps the last valid
 * streamed copy in the app cache. A stream failure never blocks the rest of the UI.
 */
@Composable
internal fun StreamedPupEyeBranding(
    modifier: Modifier = Modifier,
    contentDescription: String = "PupEye"
) {
    val context = LocalContext.current.applicationContext

    val bitmap by produceState<Bitmap?>(
        initialValue = PupEyeAssetStream.peek(),
        key1 = context
    ) {
        var consecutiveFailures = 0

        while (true) {
            val loaded = try {
                PupEyeAssetStream.load(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("PupEyeBranding", "Unable to load streamed PupEye branding", error)
                null
            }

            if (loaded != null) {
                value = loaded
                break
            }

            consecutiveFailures += 1
            val retryDelay = nextPupEyeBrandingRetryDelayMs(
                hasBitmap = false,
                consecutiveFailures = consecutiveFailures
            ) ?: break

            Log.i(
                "PupEyeBranding",
                "PupEye branding unavailable; retrying stream in ${retryDelay / 1000L}s"
            )
            delay(retryDelay)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // Keep layout usable while the remote image is loading or unavailable.
            // This placeholder is branding-only and does not claim a PupEye verification result.
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("👁")
                }
            }
        }
    }
}

internal object PupEyeAssetStream {
    // Raw GitHub is the source of truth. jsDelivr is a GitHub-backed transport fallback.
    private val URLS = listOf(
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/PupEye.png",
        "https://cdn.jsdelivr.net/gh/markhitchk/pup-clinker@main/assets/PupEye.png"
    )

    private const val CACHE_FILE = "PupEye.png"
    // Keep v2 so existing installs retain their valid refresh metadata/cache behavior.
    private const val PREFS = "pupeye_brand_stream_v2"

    private const val MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024
    private const val MAX_SOURCE_EDGE = 16_384
    private const val MAX_SOURCE_PIXELS = 64L * 1024L * 1024L
    private const val MAX_DECODE_EDGE = 2_048

    private const val REFRESH_INTERVAL_MS = 60L * 60L * 1000L
    private const val FAILURE_RETRY_MS = 15L * 1000L

    private val pngSignature = byteArrayOf(
        137.toByte(), 80, 78, 71, 13, 10, 26, 10
    )

    private val mutex = Mutex()

    @Volatile
    private var memory: Bitmap? = null

    fun peek(): Bitmap? = memory

    fun status(context: Context): PupEyeBrandingStatus {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ready = memory != null || readCached(appContext) != null

        return PupEyeBrandingStatus(
            ready = ready,
            lastCheckedAtMs = prefs.getLong("checked", 0L).coerceAtLeast(0L),
            lastAttemptedAtMs = prefs.getLong("attempted", 0L).coerceAtLeast(0L),
            source = prefs.getString("source", null)
        )
    }

    suspend fun refreshNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("checked")
            .remove("attempted")
            .apply()

        load(appContext) != null
    }

    fun clearCache(context: Context) {
        val appContext = context.applicationContext
        memory = null
        cacheFile(appContext).delete()
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        mutex.withLock {
            // Memory is a cache candidate, not an unconditional return. We still evaluate the
            // refresh timestamp so long-running app processes can receive a newer streamed logo.
            val cached = memory ?: readCached(appContext)?.also { memory = it }

            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val checked = prefs.getLong("checked", 0L)
            val attempted = prefs.getLong("attempted", 0L)

            if (cached != null && now - checked in 0 until REFRESH_INTERVAL_MS) {
                return@withLock cached
            }

            if (now - attempted in 0 until FAILURE_RETRY_MS) {
                return@withLock cached
            }

            prefs.edit().putLong("attempted", now).apply()

            var lastError: Exception? = null

            for (url in URLS) {
                try {
                    val bytes = download(url)
                    Log.i("PupEyeBranding", "Downloaded ${bytes.size} bytes from $url")

                    val decoded = decode(bytes)
                        ?: throw IOException("Invalid or unsafe PupEye PNG returned by $url")

                    writeCached(appContext, bytes)
                    memory = decoded

                    prefs.edit()
                        .putLong("checked", now)
                        .putString("source", url)
                        .apply()

                    Log.i(
                        "PupEyeBranding",
                        "Loaded streamed PupEye ${decoded.width}x${decoded.height} from $url"
                    )
                    return@withLock decoded
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    lastError = error
                    Log.w("PupEyeBranding", "PupEye stream endpoint failed: $url", error)
                }
            }

            Log.w(
                "PupEyeBranding",
                "All PupEye stream endpoints failed; retaining last valid streamed cache",
                lastError
            )
            cached
        }
    }

    private fun download(urlString: String): ByteArray {
        val connection = URL(urlString).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "image/png,image/*;q=0.9,*/*;q=0.1")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android-PupEye/3")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("PupEye asset server returned HTTP $responseCode from $urlString")
            }

            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                throw IOException("PupEye image exceeds size limit: $contentLength bytes")
            }

            return readBounded(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(connection: HttpURLConnection): ByteArray {
        val output = ByteArrayOutputStream(16 * 1024)

        connection.inputStream.use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break

                if (output.size() + count > MAX_DOWNLOAD_BYTES) {
                    throw IOException("PupEye image exceeds size limit while streaming")
                }

                output.write(buffer, 0, count)
            }
        }

        return output.toByteArray()
    }

    private fun cacheFile(context: Context): File =
        File(context.cacheDir, "branding/$CACHE_FILE")

    private fun readCached(context: Context): Bitmap? {
        val file = cacheFile(context)
        if (!file.isFile || file.length() !in 1..MAX_DOWNLOAD_BYTES.toLong()) return null

        return try {
            decode(file.readBytes()) ?: run {
                file.delete()
                null
            }
        } catch (error: Exception) {
            Log.w("PupEyeBranding", "Ignoring invalid cached PupEye image", error)
            file.delete()
            null
        }
    }

    private fun writeCached(context: Context, bytes: ByteArray) {
        val target = cacheFile(context)
        val parent = target.parentFile ?: return

        if (!parent.exists() && !parent.mkdirs()) {
            Log.w("PupEyeBranding", "Unable to create PupEye cache directory")
            return
        }

        val temp = File.createTempFile("PupEye", ".tmp", parent)

        try {
            temp.writeBytes(bytes)

            // Prefer an atomic-ish rename when possible. Fall back to overwrite-copy on devices
            // or filesystems where renameTo cannot replace the existing destination.
            if (target.exists() && !target.delete()) {
                Log.w("PupEyeBranding", "Unable to replace previous PupEye cache file")
            }

            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
            }
        } catch (error: Exception) {
            Log.w("PupEyeBranding", "Failed to write PupEye cache", error)
        } finally {
            temp.delete()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        if (
            bytes.size < pngSignature.size ||
            !bytes.copyOfRange(0, pngSignature.size).contentEquals(pngSignature)
        ) {
            Log.w("PupEyeBranding", "Rejected streamed asset with invalid PNG signature")
            return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val width = bounds.outWidth
        val height = bounds.outHeight

        if (width <= 0 || height <= 0) return null
        if (width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE) return null
        if (width.toLong() * height.toLong() > MAX_SOURCE_PIXELS) return null

        var sampleSize = 1
        while (
            width / sampleSize > MAX_DECODE_EDGE ||
            height / sampleSize > MAX_DECODE_EDGE
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }
}
