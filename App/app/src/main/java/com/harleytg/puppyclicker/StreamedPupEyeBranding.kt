package com.harleytg.puppyclicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Streamed PupEye branding.
 *
 * Source of truth:
 * https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/PupEye.png
 *
 * Every APK also contains the repository PupEye PNG as an offline fallback. This keeps the
 * branding visible when the repository is private or GitHub is unavailable, while still allowing
 * a newer anonymously-readable repository copy to refresh the local cache later.
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
            value = PupEyeAssetStream.load(context) ?: value
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("PupEyeBranding", "Unable to load PupEye branding", error)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "PupEye",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private object PupEyeAssetStream {
    private const val URL_STRING =
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/PupEye.png"
    private const val BUNDLED_ASSET = "branding/PupEye.png"
    private const val CACHE_FILE = "PupEye.png"
    private const val PREFS = "pupeye_brand_stream_v1"
    private const val MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024
    private const val MAX_IMAGE_EDGE = 4096
    private const val REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val FAILURE_RETRY_MS = 5L * 60L * 1000L
    private val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private val mutex = Mutex()

    @Volatile
    private var memory: Bitmap? = null

    fun peek(): Bitmap? = memory

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val fallback = memory
                ?: readCached(context)?.also { memory = it }
                ?: readBundled(context)?.also { memory = it }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val checked = prefs.getLong("checked", 0L)
            val attempted = prefs.getLong("attempted", 0L)

            if (fallback != null && now - checked in 0 until REFRESH_INTERVAL_MS) {
                return@withLock fallback
            }
            if (now - attempted in 0 until FAILURE_RETRY_MS) {
                return@withLock fallback
            }

            prefs.edit().putLong("attempted", now).apply()
            val connection = URL(URL_STRING).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "image/png")
                connection.setRequestProperty("User-Agent", "PuppyClicker-Android-PupEye")

                prefs.getString("etag", null)?.takeIf { fallback != null }?.let {
                    connection.setRequestProperty("If-None-Match", it)
                }

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> {
                        if (fallback == null) throw IOException("304 without a PupEye fallback")
                        prefs.edit().putLong("checked", now).apply()
                        fallback
                    }

                    HttpURLConnection.HTTP_OK -> {
                        val length = connection.contentLengthLong
                        if (length > MAX_DOWNLOAD_BYTES) {
                            throw IOException("PupEye image exceeds size limit")
                        }

                        val bytes = readBounded(connection)
                        val decoded = decode(bytes)
                            ?: throw IOException("Invalid PupEye PNG")
                        writeCached(context, bytes)
                        memory = decoded
                        prefs.edit()
                            .putString("etag", connection.getHeaderField("ETag"))
                            .putLong("checked", now)
                            .apply()
                        decoded
                    }

                    else -> throw IOException(
                        "PupEye asset server returned HTTP ${connection.responseCode}"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("PupEyeBranding", "Using packaged/cached PupEye branding", error)
                fallback
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun cacheFile(context: Context): File =
        File(context.cacheDir, "branding/$CACHE_FILE")

    private fun readBundled(context: Context): Bitmap? = try {
        context.assets.open(BUNDLED_ASSET).use { input ->
            val bytes = input.readBytes()
            if (bytes.size > MAX_DOWNLOAD_BYTES) null else decode(bytes)
        }
    } catch (error: Exception) {
        Log.w("PupEyeBranding", "Packaged PupEye logo unavailable", error)
        null
    }

    private fun readCached(context: Context): Bitmap? {
        val file = cacheFile(context)
        if (!file.isFile || file.length() !in 1..MAX_DOWNLOAD_BYTES.toLong()) return null
        return try {
            decode(file.readBytes())
        } catch (error: Exception) {
            Log.w("PupEyeBranding", "Ignoring invalid cached PupEye image", error)
            null
        }
    }

    private fun writeCached(context: Context, bytes: ByteArray) {
        val target = cacheFile(context)
        target.parentFile?.mkdirs()
        val temp = File.createTempFile("PupEye", ".tmp", target.parentFile)
        try {
            temp.writeBytes(bytes)
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
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > MAX_IMAGE_EDGE || bounds.outHeight > MAX_IMAGE_EDGE
        ) return null

        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }
}
