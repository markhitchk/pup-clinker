package com.harleytg.puppyclicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
        try {
            value = PupEyeAssetStream.load(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("PupEyeBranding", "Unable to load streamed PupEye branding", error)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { loaded ->
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private object PupEyeAssetStream {
    // Raw GitHub stays the source of truth. jsDelivr is only a GitHub-backed transport fallback
    // for devices/networks that fail to reach raw.githubusercontent.com reliably.
    private val URLS = listOf(
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/PupEye.png",
        "https://cdn.jsdelivr.net/gh/markhitchk/pup-clinker@main/assets/PupEye.png"
    )

    private const val CACHE_FILE = "PupEye.png"
    private const val PREFS = "pupeye_brand_stream_v2"
    private const val MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024
    private const val MAX_SOURCE_EDGE = 16_384
    private const val MAX_SOURCE_PIXELS = 64L * 1024L * 1024L
    private const val MAX_DECODE_EDGE = 2_048
    private const val REFRESH_INTERVAL_MS = 60L * 60L * 1000L
    private const val FAILURE_RETRY_MS = 20L * 1000L

    private val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private val mutex = Mutex()

    @Volatile
    private var memory: Bitmap? = null

    fun peek(): Bitmap? = memory

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cached = memory ?: readCached(context)?.also { memory = it }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
                        ?: throw IOException("Invalid or unsafe PupEye PNG returned by $url")

                    writeCached(context, bytes)
                    memory = decoded
                    prefs.edit()
                        .putLong("checked", now)
                        .putString("source", url)
                        .apply()

                    Log.i("PupEyeBranding", "Loaded streamed PupEye branding from $url")
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
            connection.connectTimeout = 6_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "image/png,image/*;q=0.9,*/*;q=0.1")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android-PupEye/2")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("PupEye asset server returned HTTP $responseCode")
            }

            val length = connection.contentLengthLong
            if (length > MAX_DOWNLOAD_BYTES) {
                throw IOException("PupEye image exceeds size limit: $length bytes")
            }

            return readBounded(connection)
        } finally {
            connection.disconnect()
        }
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
            temp.copyTo(target, overwrite = true)
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
                    throw IOException("PupEye image exceeds size limit")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.size < pngSignature.size ||
            !bytes.copyOfRange(0, pngSignature.size).contentEquals(pngSignature)
        ) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        if (width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE) return null
        if (width.toLong() * height.toLong() > MAX_SOURCE_PIXELS) return null

        var sampleSize = 1
        while (width / sampleSize > MAX_DECODE_EDGE || height / sampleSize > MAX_DECODE_EDGE) {
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
