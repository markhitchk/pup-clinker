package com.harleytg.puppyclicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
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

/**
 * Runtime-streamed Pup Coin artwork used by Puppy Casino Pup Scratchers.
 *
 * Source of truth:
 * https://github.com/markhitchk/pup-clinker/blob/main/assets/pup_coin.png
 *
 * The PNG is intentionally not bundled into the APK. A valid streamed copy is cached so the
 * scratcher remains usable through temporary network failures after the first successful load.
 */
@Composable
internal fun streamedPupCoinPainter(): Painter? {
    val context = LocalContext.current.applicationContext

    val bitmap by produceState<Bitmap?>(
        initialValue = PupCoinAssetStream.peek(),
        key1 = context
    ) {
        var failures = 0

        while (true) {
            val loaded = try {
                PupCoinAssetStream.load(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("PupCoinStream", "Unable to stream Pup Coin artwork", error)
                null
            }

            if (loaded != null) {
                value = loaded
                break
            }

            failures += 1
            if (failures >= 3) break
            delay(if (failures == 1) 15_000L else 30_000L)
        }
    }

    val loaded = bitmap ?: return null
    return remember(loaded) { BitmapPainter(loaded.asImageBitmap()) }
}

internal object PupCoinAssetStream {
    private val URLS = listOf(
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/pup_coin.png",
        "https://cdn.jsdelivr.net/gh/markhitchk/pup-clinker@main/assets/pup_coin.png"
    )

    private const val PREFS = "pup_coin_stream_v1"
    private const val CACHE_FILE = "pup_coin.png"
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

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        mutex.withLock {
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
                    val decoded = decode(bytes)
                        ?: throw IOException("Invalid or unsafe Pup Coin PNG returned by $url")

                    writeCached(appContext, bytes)
                    memory = decoded
                    prefs.edit()
                        .putLong("checked", now)
                        .putString("source", url)
                        .apply()

                    Log.i(
                        "PupCoinStream",
                        "Loaded Pup Coin ${decoded.width}x${decoded.height} from $url"
                    )
                    return@withLock decoded
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    lastError = error
                    Log.w("PupCoinStream", "Pup Coin stream endpoint failed: $url", error)
                }
            }

            Log.w(
                "PupCoinStream",
                "All Pup Coin stream endpoints failed; retaining last valid cached asset",
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
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android-PupCoin/1")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for $urlString")
            }
            if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) {
                throw IOException("Pup Coin image exceeds size limit")
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
                    throw IOException("Pup Coin image exceeds size limit while streaming")
                }

                output.write(buffer, 0, count)
            }
        }

        return output.toByteArray()
    }

    private fun cacheFile(context: Context): File =
        File(context.cacheDir, "streamed-assets/$CACHE_FILE")

    private fun readCached(context: Context): Bitmap? {
        val file = cacheFile(context)
        if (!file.isFile || file.length() !in 1..MAX_DOWNLOAD_BYTES.toLong()) return null

        return try {
            decode(file.readBytes()) ?: run {
                file.delete()
                null
            }
        } catch (error: Exception) {
            Log.w("PupCoinStream", "Ignoring invalid cached Pup Coin image", error)
            file.delete()
            null
        }
    }

    private fun writeCached(context: Context, bytes: ByteArray) {
        val target = cacheFile(context)
        val parent = target.parentFile ?: return

        if (!parent.exists() && !parent.mkdirs()) {
            Log.w("PupCoinStream", "Unable to create Pup Coin cache directory")
            return
        }

        val temp = File.createTempFile("pup-coin-", ".tmp", parent)

        try {
            temp.writeBytes(bytes)
            if (target.exists() && !target.delete()) {
                Log.w("PupCoinStream", "Unable to replace previous Pup Coin cache file")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
            }
        } catch (error: Exception) {
            Log.w("PupCoinStream", "Failed to write Pup Coin cache", error)
        } finally {
            temp.delete()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        if (
            bytes.size < pngSignature.size ||
            !bytes.copyOfRange(0, pngSignature.size).contentEquals(pngSignature)
        ) {
            Log.w("PupCoinStream", "Rejected streamed Pup Coin with invalid PNG signature")
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
