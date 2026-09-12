package com.harleytg.puppyclicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class RepoLogoAsset(val fileName: String) {
    PUPPY_CLICKER("puppy_clicker.png"),
    HARLEYS_STUDIOS("harleys_studios.png")
}

@Composable
internal fun streamedRepoLogoPainter(
    asset: RepoLogoAsset,
    @DrawableRes fallbackDrawable: Int
): Painter {
    val context = LocalContext.current.applicationContext

    val bitmap by produceState<Bitmap?>(
        initialValue = RepoLogoAssetStream.peek(asset),
        key1 = context,
        key2 = asset
    ) {
        var failures = 0
        while (true) {
            val loaded = try {
                RepoLogoAssetStream.load(context, asset)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("RepoLogoStream", "Unable to stream ${asset.fileName}", error)
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

    val loaded = bitmap
    return if (loaded != null) {
        remember(loaded) { BitmapPainter(loaded.asImageBitmap()) }
    } else {
        painterResource(fallbackDrawable)
    }
}

internal object RepoLogoAssetStream {
    private const val RAW_BASE =
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/logos"
    private const val CDN_BASE =
        "https://cdn.jsdelivr.net/gh/markhitchk/pup-clinker@main/assets/logos"

    private const val PREFS = "repo_logo_stream_v1"
    private const val MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024
    private const val MAX_SOURCE_EDGE = 16_384
    private const val MAX_SOURCE_PIXELS = 64L * 1024L * 1024L
    private const val MAX_DECODE_EDGE = 2_048
    private const val REFRESH_INTERVAL_MS = 60L * 60L * 1000L
    private const val FAILURE_RETRY_MS = 15L * 1000L

    private val pngSignature = byteArrayOf(
        137.toByte(), 80, 78, 71, 13, 10, 26, 10
    )
    private val memory = ConcurrentHashMap<RepoLogoAsset, Bitmap>()
    private val mutexes = RepoLogoAsset.entries.associateWith { Mutex() }

    fun peek(asset: RepoLogoAsset): Bitmap? = memory[asset]

    suspend fun load(context: Context, asset: RepoLogoAsset): Bitmap? =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val mutex = mutexes.getValue(asset)

            mutex.withLock {
                val cached = memory[asset] ?: readCached(appContext, asset)?.also {
                    memory[asset] = it
                }

                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val prefix = asset.name.lowercase()
                val now = System.currentTimeMillis()
                val checked = prefs.getLong("${prefix}_checked", 0L)
                val attempted = prefs.getLong("${prefix}_attempted", 0L)

                if (cached != null && now - checked in 0 until REFRESH_INTERVAL_MS) {
                    return@withLock cached
                }
                if (now - attempted in 0 until FAILURE_RETRY_MS) {
                    return@withLock cached
                }

                prefs.edit().putLong("${prefix}_attempted", now).apply()

                val urls = listOf(
                    "$RAW_BASE/${asset.fileName}",
                    "$CDN_BASE/${asset.fileName}"
                )
                var lastError: Exception? = null

                for (url in urls) {
                    try {
                        val bytes = download(url, asset)
                        val decoded = decode(bytes)
                            ?: throw IOException("Invalid or unsafe logo PNG returned by $url")

                        writeCached(appContext, asset, bytes)
                        memory[asset] = decoded
                        prefs.edit()
                            .putLong("${prefix}_checked", now)
                            .putString("${prefix}_source", url)
                            .apply()

                        Log.i(
                            "RepoLogoStream",
                            "Loaded ${asset.fileName} ${decoded.width}x${decoded.height} from $url"
                        )
                        return@withLock decoded
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        lastError = error
                        Log.w("RepoLogoStream", "Logo stream endpoint failed: $url", error)
                    }
                }

                Log.w(
                    "RepoLogoStream",
                    "All endpoints failed for ${asset.fileName}; using cached/bundled fallback",
                    lastError
                )
                cached
            }
        }

    private fun download(urlString: String, asset: RepoLogoAsset): ByteArray {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "image/png,image/*;q=0.9,*/*;q=0.1")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android-Branding/1")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} for $urlString")
            }
            if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) {
                throw IOException("${asset.fileName} exceeds size limit")
            }

            return readBounded(connection, asset)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(
        connection: HttpURLConnection,
        asset: RepoLogoAsset
    ): ByteArray {
        val output = ByteArrayOutputStream(16 * 1024)
        connection.inputStream.use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_DOWNLOAD_BYTES) {
                    throw IOException("${asset.fileName} exceeds size limit while streaming")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun cacheFile(context: Context, asset: RepoLogoAsset): File =
        File(context.cacheDir, "branding/${asset.fileName}")

    private fun readCached(context: Context, asset: RepoLogoAsset): Bitmap? {
        val file = cacheFile(context, asset)
        if (!file.isFile || file.length() !in 1..MAX_DOWNLOAD_BYTES.toLong()) return null

        return try {
            decode(file.readBytes()) ?: run {
                file.delete()
                null
            }
        } catch (error: Exception) {
            Log.w("RepoLogoStream", "Ignoring invalid cached ${asset.fileName}", error)
            file.delete()
            null
        }
    }

    private fun writeCached(context: Context, asset: RepoLogoAsset, bytes: ByteArray) {
        val target = cacheFile(context, asset)
        val parent = target.parentFile ?: return
        if (!parent.exists() && !parent.mkdirs()) return

        val temp = File.createTempFile("repo-logo-", ".tmp", parent)
        try {
            temp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) temp.copyTo(target, overwrite = true)
        } finally {
            temp.delete()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        if (
            bytes.size < pngSignature.size ||
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
