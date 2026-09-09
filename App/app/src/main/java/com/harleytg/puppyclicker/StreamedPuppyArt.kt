package com.harleytg.puppyclicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** V6 portrait renderer backed by the dynamic roster's exact manifest asset path. */
@Composable
internal fun StreamedPuppyPortrait(
    styleId: String,
    size: Dp,
    accessory: String = "None",
    unlocked: Boolean = true,
    background: Color,
    @Suppress("UNUSED_PARAMETER") furFilter: ColorFilter? = null
) {
    val style = DynamicPuppyRoster.style(styleId)
        ?: DynamicPuppyRoster.style("classic")
        ?: V1_PUPPY_STYLES.first()
    val context = LocalContext.current.applicationContext
    val assetId = RemotePuppyAssets.assetIdFor(style.id)
    val portrait = produceState<Bitmap?>(
        initialValue = RemotePuppyAssets.peek(assetId),
        key1 = context,
        key2 = assetId
    ) {
        try {
            value = RemotePuppyAssets.cached(context, assetId) ?: value
            RemotePuppyAssets.refresh(context, assetId)?.let { value = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("PuppyClickerArt", "Unable to refresh $assetId", error)
        }
    }.value

    if (portrait == null && style.id in V6_PUPPY_IDS && style.id != "v2_harleytg" && style.id !in EXTRA_PUPPY_IDS) {
        ProtectedPuppyPortrait(style.id, size, accessory, unlocked, background, null)
        return
    }

    Box(
        Modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center
    ) {
        if (portrait != null) {
            Image(
                bitmap = portrait.asImageBitmap(),
                contentDescription = style.name,
                modifier = Modifier.size(size * 0.96f),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("🐶", fontSize = (size.value * 0.42f).sp)
        }

        Surface(
            Modifier.align(Alignment.TopEnd).padding(size * 0.05f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            Text(style.emoji, fontSize = (size.value * 0.13f).sp, modifier = Modifier.padding(size * 0.025f))
        }

        if (!unlocked) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(size * 0.06f),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Text("🔒", fontSize = (size.value * 0.13f).sp, modifier = Modifier.padding(size * 0.035f))
            }
        } else {
            when (accessory) {
                "Bandana" -> Text("🧣", fontSize = (size.value * 0.18f).sp, modifier = Modifier.align(Alignment.BottomCenter))
                "Bow" -> Text("🎀", fontSize = (size.value * 0.16f).sp, modifier = Modifier.align(Alignment.TopCenter))
                "Crown" -> Text("👑", fontSize = (size.value * 0.18f).sp, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/** Network/disk image operations. Remote data is validated PNG only. */
internal object RemotePuppyAssets {
    private const val BASE = "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets"
    private const val BUNDLED_ROOT = "canonical-puppies"
    private const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
    private const val MAX_IMAGE_EDGE = 4096
    private const val DECODE_EDGE = 1536
    private const val DISK_LIMIT = 64L * 1024 * 1024
    private const val REFRESH_INTERVAL = 6L * 60 * 60 * 1000
    private const val FAILURE_RETRY = 5L * 60 * 1000
    private val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val memory = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun assetIdFor(styleId: String): String = DynamicPuppyRoster.asset(styleId)?.assetId
        ?: error("Unknown puppy style: $styleId")

    internal fun bundledPathFor(styleId: String): String {
        val asset = DynamicPuppyRoster.asset(styleId) ?: error("Unknown puppy style: $styleId")
        return "$BUNDLED_ROOT/${asset.folder}/${asset.fileName}"
    }

    private fun checkedId(assetId: String): String {
        require(assetId.matches(Regex("[a-z0-9_]{1,64}"))) { "Invalid asset ID" }
        require(DynamicPuppyRoster.assetByAssetId(assetId) != null) { "Unknown puppy asset: $assetId" }
        return assetId
    }

    fun peek(assetId: String): Bitmap? = memory.get(checkedId(assetId))

    private fun directory(context: Context): File =
        File(context.cacheDir, "puppy-stream-v3").apply { mkdirs() }

    private fun file(context: Context, assetId: String): File =
        File(directory(context), "${checkedId(assetId)}.png")

    private fun prefs(context: Context) =
        context.getSharedPreferences("puppy_stream_v3", Context.MODE_PRIVATE)

    private fun lock(assetId: String): Mutex = locks.computeIfAbsent(checkedId(assetId)) { Mutex() }

    suspend fun cached(context: Context, assetId: String): Bitmap? = withContext(Dispatchers.IO) {
        lock(assetId).withLock { readAvailable(context, checkedId(assetId)) }
    }

    private fun readAvailable(context: Context, assetId: String): Bitmap? {
        memory.get(assetId)?.let { return it }
        val disk = file(context, assetId)
        if (disk.isFile && disk.length() in 1..MAX_DOWNLOAD_BYTES.toLong()) {
            try {
                decode(disk.readBytes())?.let { bitmap ->
                    memory.put(assetId, bitmap)
                    return bitmap
                }
            } catch (error: Exception) {
                Log.w("PuppyClickerArt", "Ignoring invalid cached image: $assetId", error)
            }
        }
        return readBundled(context, assetId)
    }

    private fun readBundled(context: Context, assetId: String): Bitmap? {
        val id = checkedId(assetId)
        val asset = DynamicPuppyRoster.assetByAssetId(id) ?: return null
        val path = "$BUNDLED_ROOT/${asset.folder}/${asset.fileName}"
        return try {
            val bytes = context.assets.open(path).use { it.readBytes() }
            if (bytes.size > MAX_DOWNLOAD_BYTES) null else decode(bytes)?.also { memory.put(id, it) }
        } catch (error: Exception) {
            // A remotely discovered puppy may not exist in this APK yet; its PNG can
            // still be downloaded below when the public stream is reachable.
            Log.w("PuppyClickerArt", "No bundled puppy at $path", error)
            null
        }
    }

    suspend fun refresh(context: Context, assetId: String): Bitmap? = withContext(Dispatchers.IO) {
        lock(assetId).withLock {
            val id = checkedId(assetId)
            val asset = DynamicPuppyRoster.assetByAssetId(id) ?: return@withLock null
            val cached = readAvailable(context, id)
            val settings = prefs(context)
            val now = System.currentTimeMillis()
            val checked = settings.getLong("$id.checked", 0L)
            val attempted = settings.getLong("$id.attempted", 0L)
            if (cached != null && now - checked in 0 until REFRESH_INTERVAL) return@withLock cached
            if (now - attempted in 0 until FAILURE_RETRY) return@withLock cached

            settings.edit().putLong("$id.attempted", now).apply()
            val url = URL("$BASE/${asset.folder}/${asset.fileName}")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "image/png")
                connection.setRequestProperty("User-Agent", "PuppyClicker-Android")
                val etag = settings.getString("$id.etag", null)
                if (cached != null && !etag.isNullOrBlank()) connection.setRequestProperty("If-None-Match", etag)

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> {
                        if (cached == null) throw IOException("304 without cached/bundled image")
                        settings.edit().putLong("$id.checked", now).apply()
                        cached
                    }
                    HttpURLConnection.HTTP_OK -> {
                        val length = connection.contentLengthLong
                        if (length > MAX_DOWNLOAD_BYTES) throw IOException("Puppy image exceeds size limit")
                        val bytes = readBounded(connection)
                        val bitmap = decode(bytes) ?: throw IOException("Invalid PNG artwork: $id")
                        val target = file(context, id)
                        val temp = File.createTempFile(id, ".tmp", target.parentFile)
                        try {
                            temp.writeBytes(bytes)
                            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        } finally {
                            temp.delete()
                        }
                        memory.put(id, bitmap)
                        settings.edit()
                            .putString("$id.etag", connection.getHeaderField("ETag"))
                            .putLong("$id.checked", now)
                            .apply()
                        trimDisk(directory(context), target)
                        bitmap
                    }
                    else -> throw IOException("Artwork server returned HTTP ${connection.responseCode}")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("PuppyClickerArt", "Using bundled/cached artwork for $id", error)
                cached
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun readBounded(connection: HttpURLConnection): ByteArray {
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_DOWNLOAD_BYTES) throw IOException("Puppy image exceeds size limit")
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.size < pngSignature.size || !bytes.copyOfRange(0, 8).contentEquals(pngSignature)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width > MAX_IMAGE_EDGE || height > MAX_IMAGE_EDGE) return null
        var sample = 1
        while (width / sample > DECODE_EDGE || height / sample > DECODE_EDGE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    private fun trimDisk(directory: File, keep: File) {
        val files = directory.listFiles { candidate -> candidate.isFile && candidate.extension == "png" }
            ?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (candidate in files) {
            if (total <= DISK_LIMIT) break
            if (candidate == keep) continue
            val size = candidate.length()
            if (candidate.delete()) total -= size
        }
    }
}
