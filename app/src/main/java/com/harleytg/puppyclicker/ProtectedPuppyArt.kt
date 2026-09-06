package com.harleytg.puppyclicker

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.util.Xml
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * Runtime loader for protected Puppy Clicker artwork.
 *
 * Puppy artwork is AES-256-GCM encrypted into generated APK assets during the build.
 * V1 styles share one encrypted base puppy and keep their existing tint treatment.
 * Every V2 puppy is stored as an encrypted vector payload and parsed only after decrypting in memory.
 *
 * Loading/decryption/parsing is deliberately kept off the UI thread. The puppy roster can compose many
 * portraits at once, and doing vector parsing synchronously there can stall the app on slower devices.
 *
 * This raises the bar for casual APK extraction. It is not DRM: a determined reverse engineer can
 * still recover artwork from a running client because the app must ultimately render it.
 */
@Composable
internal fun ProtectedPuppyPortrait(
    styleId: String,
    size: Dp,
    accessory: String = "None",
    unlocked: Boolean = true,
    background: Color,
    furFilter: ColorFilter?
) {
    val style = V6_PUPPY_STYLES.firstOrNull { it.id == styleId } ?: V6_PUPPY_STYLES.first()
    val isV2 = styleId in V2_PUPPY_IDS
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

    val payload = produceState<ProtectedPuppyPayload?>(
        initialValue = ProtectedPuppyAssets.peek(styleId),
        key1 = styleId
    ) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                runCatching { ProtectedPuppyAssets.load(appContext, styleId) }.getOrNull()
            }
        }
    }.value

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        when (payload) {
            is ProtectedBitmapPayload -> {
                Image(
                    bitmap = payload.bitmap,
                    contentDescription = style.name,
                    modifier = Modifier.size(size * if (isV2) 0.96f else 0.90f),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isV2) null else furFilter
                )
            }

            is ProtectedVectorPayload -> {
                ProtectedVectorImage(
                    vector = payload.vector,
                    description = style.name,
                    modifier = Modifier.size(size * 0.96f)
                )
            }

            null -> Text("🐶", fontSize = (size.value * 0.42f).sp)
        }

        Surface(
            Modifier.align(Alignment.TopEnd).padding(size * 0.05f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            Text(
                style.emoji,
                fontSize = (size.value * 0.13f).sp,
                modifier = Modifier.padding(size * 0.025f)
            )
        }

        if (!unlocked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🔒", fontSize = (size.value * 0.22f).sp)
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

@Composable
private fun ProtectedVectorImage(
    vector: SecureVector,
    description: String,
    modifier: Modifier = Modifier
) {
    // Paint creation is relatively expensive. Build immutable render commands once per decrypted vector
    // instead of allocating one or two Paint instances for every path on every Compose draw pass.
    val renderPaths = remember(vector) {
        vector.paths.map { item ->
            val fillPaint = item.fillColor?.takeIf { AndroidColor.alpha(it) != 0 && item.fillAlpha > 0f }?.let { color ->
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    this.color = color
                    alpha = (item.fillAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
                }
            }

            val strokePaint = item.strokeColor
                ?.takeIf { item.strokeWidth > 0f && AndroidColor.alpha(it) != 0 && item.strokeAlpha > 0f }
                ?.let { color ->
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        this.color = color
                        strokeWidth = item.strokeWidth
                        strokeCap = item.strokeCap
                        strokeJoin = item.strokeJoin
                        strokeMiter = item.strokeMiter
                        alpha = (item.strokeAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
                    }
                }

            SecureRenderPath(item.path, fillPaint, strokePaint)
        }
    }

    Canvas(modifier.semantics { contentDescription = description }) {
        drawIntoCanvas { composeCanvas ->
            val native = composeCanvas.nativeCanvas
            val scale = min(size.width / vector.viewportWidth, size.height / vector.viewportHeight)
            val dx = (size.width - vector.viewportWidth * scale) / 2f
            val dy = (size.height - vector.viewportHeight * scale) / 2f

            native.save()
            native.translate(dx, dy)
            native.scale(scale, scale)

            renderPaths.forEach { item ->
                item.fillPaint?.let { native.drawPath(item.path, it) }
                item.strokePaint?.let { native.drawPath(item.path, it) }
            }

            native.restore()
        }
    }
}

private sealed interface ProtectedPuppyPayload
private data class ProtectedBitmapPayload(val bitmap: ImageBitmap) : ProtectedPuppyPayload
private data class ProtectedVectorPayload(val vector: SecureVector) : ProtectedPuppyPayload

private data class SecureVector(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val paths: List<SecureVectorPath>
)

private data class SecureVectorPath(
    val path: Path,
    val fillColor: Int?,
    val fillAlpha: Float,
    val strokeColor: Int?,
    val strokeAlpha: Float,
    val strokeWidth: Float,
    val strokeCap: Paint.Cap,
    val strokeJoin: Paint.Join,
    val strokeMiter: Float
)

private data class SecureRenderPath(
    val path: Path,
    val fillPaint: Paint?,
    val strokePaint: Paint?
)

private object ProtectedPuppyAssets {
    private const val PREFIX = "puppies"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private val cache = ConcurrentHashMap<String, ProtectedPuppyPayload>()

    private const val KEY_MASK_A = "f382c0752e0bda1c7ac539661e2a2eb12a01202e848a1f2fd925bceddd3e9ca8"
    private const val KEY_MASK_B = "aad54e1243c251683af5c3709dfb34ff888e9f8de7b81104716bb120c239984f"

    fun peek(styleId: String): ProtectedPuppyPayload? = cache[assetIdFor(styleId)]

    fun load(context: Context, styleId: String): ProtectedPuppyPayload {
        val assetId = assetIdFor(styleId)
        return cache[assetId] ?: synchronized(this) {
            cache[assetId] ?: loadUncached(context, assetId).also { cache[assetId] = it }
        }
    }

    private fun assetIdFor(styleId: String): String =
        if (styleId in V2_PUPPY_IDS) styleId else "v1_base"

    private fun loadUncached(context: Context, assetId: String): ProtectedPuppyPayload {
        val protectedBytes = context.assets.open("$PREFIX/$assetId.pup").use { it.readBytes() }
        val plain = decrypt(assetId, protectedBytes)

        return if (assetId.startsWith("v2_")) {
            ProtectedVectorPayload(parseVector(plain.toString(Charsets.UTF_8)))
        } else {
            val bitmap = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                ?: error("Unable to decode protected V1 puppy artwork")
            ProtectedBitmapPayload(bitmap.asImageBitmap())
        }
    }

    private fun decrypt(assetId: String, payload: ByteArray): ByteArray {
        require(payload.size > 32) { "Protected puppy payload is too small" }
        require(payload.copyOfRange(0, 4).contentEquals(byteArrayOf('P'.code.toByte(), 'C'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte()))) {
            "Invalid protected puppy payload"
        }

        val nonce = payload.copyOfRange(4, 16)
        val ciphertext = payload.copyOfRange(16, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(assetKey(), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("puppy-clicker:$assetId".toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    private fun assetKey(): ByteArray {
        val a = hexToBytes(KEY_MASK_A)
        val b = hexToBytes(KEY_MASK_B)
        return ByteArray(a.size) { index -> (a[index].toInt() xor b[index].toInt()).toByte() }
    }

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { index ->
        hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

    private fun parseVector(xml: String): SecureVector {
        val parser = Xml.newPullParser().apply {
            setInput(StringReader(xml))
        }

        var viewportWidth = 1f
        var viewportHeight = 1f
        val paths = mutableListOf<SecureVectorPath>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "vector" -> {
                        viewportWidth = parser.androidAttr("viewportWidth")?.toFloatOrNull() ?: 1f
                        viewportHeight = parser.androidAttr("viewportHeight")?.toFloatOrNull() ?: 1f
                    }

                    "path" -> {
                        val pathData = parser.androidAttr("pathData")
                        if (!pathData.isNullOrBlank()) {
                            val path = PathParser.createPathFromPathData(pathData)
                                ?: error("Invalid protected vector path")
                            path.fillType = when (parser.androidAttr("fillType")?.lowercase()) {
                                "evenodd", "even_odd" -> Path.FillType.EVEN_ODD
                                else -> Path.FillType.WINDING
                            }

                            paths += SecureVectorPath(
                                path = path,
                                fillColor = parser.androidAttr("fillColor")?.let(::parseColor),
                                fillAlpha = parser.androidAttr("fillAlpha")?.toFloatOrNull() ?: 1f,
                                strokeColor = parser.androidAttr("strokeColor")?.let(::parseColor),
                                strokeAlpha = parser.androidAttr("strokeAlpha")?.toFloatOrNull() ?: 1f,
                                strokeWidth = parser.androidAttr("strokeWidth")?.toFloatOrNull() ?: 0f,
                                strokeCap = when (parser.androidAttr("strokeLineCap")?.lowercase()) {
                                    "round" -> Paint.Cap.ROUND
                                    "square" -> Paint.Cap.SQUARE
                                    else -> Paint.Cap.BUTT
                                },
                                strokeJoin = when (parser.androidAttr("strokeLineJoin")?.lowercase()) {
                                    "round" -> Paint.Join.ROUND
                                    "bevel" -> Paint.Join.BEVEL
                                    else -> Paint.Join.MITER
                                },
                                strokeMiter = parser.androidAttr("strokeMiterLimit")?.toFloatOrNull() ?: 4f
                            )
                        }
                    }
                }
            }
            parser.next()
        }

        return SecureVector(viewportWidth, viewportHeight, paths)
    }

    private fun XmlPullParser.androidAttr(name: String): String? =
        getAttributeValue(ANDROID_NS, name) ?: getAttributeValue(null, name)

    private fun parseColor(raw: String): Int = when {
        raw == "@android:color/transparent" -> AndroidColor.TRANSPARENT
        raw.equals("transparent", ignoreCase = true) -> AndroidColor.TRANSPARENT
        raw.startsWith("#") -> AndroidColor.parseColor(raw)
        else -> AndroidColor.TRANSPARENT
    }
}
