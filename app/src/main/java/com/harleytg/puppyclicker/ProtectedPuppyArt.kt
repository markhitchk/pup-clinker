package com.harleytg.puppyclicker

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.util.Xml
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/**
 * Runtime renderer for individually encrypted Puppy Clicker Android vectors.
 *
 * V1 and V2 keep their existing PCP1 assets and independent vector models.
 * Loading and parsing happen off the UI thread. The source format is a flat
 * Android VectorDrawable subset: paths with solid fills and strokes. Unsupported
 * features are rejected rather than silently rendered incorrectly. In particular,
 * raw decrypted XML must not be passed to Android's compiled-resource inflater.
 */
@Composable
internal fun ProtectedPuppyPortrait(
    styleId: String,
    size: Dp,
    accessory: String = "None",
    unlocked: Boolean = true,
    background: Color,
    @Suppress("UNUSED_PARAMETER") furFilter: ColorFilter?
) {
    val style = V6_PUPPY_STYLES.firstOrNull { it.id == styleId } ?: V6_PUPPY_STYLES.first()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

    val vector = produceState<SecureVector?>(
        initialValue = ProtectedPuppyAssets.peek(style.id),
        key1 = appContext,
        key2 = style.id
    ) {
        if (value == null) {
            value = try {
                withContext(Dispatchers.IO) {
                    ProtectedPuppyAssets.load(appContext, style.id)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("PuppyClickerArt", "Unable to load puppy ${style.id}", error)
                null
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
        if (vector != null) {
            ProtectedVectorImage(
                vector = vector,
                description = style.name,
                modifier = Modifier.size(size * 0.96f)
            )
        } else {
            Text("🐶", fontSize = (size.value * 0.42f).sp)
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
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(size * 0.06f),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    "🔒",
                    fontSize = (size.value * 0.13f).sp,
                    modifier = Modifier.padding(size * 0.035f)
                )
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
    val renderPaths = remember(vector) {
        vector.paths.map { item ->
            val fillPaint = item.fillColor
                ?.takeIf { AndroidColor.alpha(it) != 0 && item.fillAlpha > 0f }
                ?.let { color ->
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        this.color = color
                        alpha = combinedVectorAlpha(color, item.fillAlpha)
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
                        alpha = combinedVectorAlpha(color, item.strokeAlpha)
                    }
                }

            SecureRenderPath(item.path, fillPaint, strokePaint)
        }
    }

    Canvas(modifier.semantics { contentDescription = description }) {
        drawIntoCanvas { composeCanvas ->
            if (size.width <= 0f || size.height <= 0f) return@drawIntoCanvas
            val native = composeCanvas.nativeCanvas
            val scale = min(size.width / vector.viewportWidth, size.height / vector.viewportHeight)
            val dx = (size.width - vector.viewportWidth * scale) / 2f
            val dy = (size.height - vector.viewportHeight * scale) / 2f

            val saveCount = native.save()
            try {
                native.translate(dx, dy)
                native.scale(scale, scale)
                native.clipRect(0f, 0f, vector.viewportWidth, vector.viewportHeight)
                renderPaths.forEach { item ->
                    item.fillPaint?.let { native.drawPath(item.path, it) }
                    item.strokePaint?.let { native.drawPath(item.path, it) }
                }
            } finally {
                native.restoreToCount(saveCount)
            }
        }
    }
}

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
    private const val MAX_ASSET_BYTES = 2 * 1024 * 1024
    private const val MAX_PATHS = 8192
    private val cache = ConcurrentHashMap<String, SecureVector>()

    private const val KEY_MASK_A = "f382c0752e0bda1c7ac539661e2a2eb12a01202e848a1f2fd925bceddd3e9ca8"
    private const val KEY_MASK_B = "aad54e1243c251683af5c3709dfb34ff888e9f8de7b81104716bb120c239984f"

    fun peek(styleId: String): SecureVector? = cache[assetIdFor(styleId)]

    fun load(context: Context, styleId: String): SecureVector {
        val assetId = assetIdFor(styleId)
        cache[assetId]?.let { return it }
        val loaded = loadUncached(context, assetId)
        return cache.putIfAbsent(assetId, loaded) ?: loaded
    }

    private fun assetIdFor(styleId: String): String {
        require(styleId in V6_PUPPY_IDS) { "Unknown puppy style: $styleId" }
        return if (styleId in V2_PUPPY_IDS) styleId else "v1_$styleId"
    }

    private fun loadUncached(context: Context, assetId: String): SecureVector {
        val protectedBytes = context.assets.open("$PREFIX/$assetId.pup").use { it.readBytes() }
        require(protectedBytes.size <= MAX_ASSET_BYTES) { "Protected puppy asset is too large" }
        val plain = decrypt(assetId, protectedBytes)
        require(plain.size <= MAX_ASSET_BYTES) { "Decrypted puppy asset is too large" }
        return parseVector(plain.toString(Charsets.UTF_8))
    }

    private fun decrypt(assetId: String, payload: ByteArray): ByteArray {
        require(payload.size > 32) { "Protected puppy payload is too small" }
        require(
            payload.copyOfRange(0, 4).contentEquals(
                byteArrayOf('P'.code.toByte(), 'C'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())
            )
        ) { "Invalid protected puppy payload" }

        val nonce = payload.copyOfRange(4, 16)
        val ciphertext = payload.copyOfRange(16, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(assetKey(), "AES"),
            GCMParameterSpec(128, nonce)
        )
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
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "DOCTYPE is not allowed in puppy vectors" }
        require(!xml.contains("<!ENTITY", ignoreCase = true)) { "Entities are not allowed in puppy vectors" }
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        var viewportWidth = Float.NaN
        var viewportHeight = Float.NaN
        var rootSeen = false
        val paths = mutableListOf<SecureVectorPath>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "vector" -> {
                        require(!rootSeen && parser.depth == 1) { "Unexpected nested vector" }
                        rootSeen = true
                        viewportWidth = parser.vectorFloat("viewportWidth", Float.NaN, 0f, Float.MAX_VALUE, exclusiveMin = true)
                        viewportHeight = parser.vectorFloat("viewportHeight", Float.NaN, 0f, Float.MAX_VALUE, exclusiveMin = true)
                        require(parser.androidAttr("alpha") == null && parser.androidAttr("tint") == null && parser.androidAttr("autoMirrored") == null) {
                            "Unsupported root vector effect"
                        }
                    }

                    "path" -> {
                        require(rootSeen && parser.depth == 2) { "Unsupported nested vector path" }
                        require(paths.size < MAX_PATHS) { "Too many vector paths" }
                        val pathData = requireNotNull(parser.androidAttr("pathData")) { "Missing vector pathData" }
                        require(pathData.isNotBlank()) { "Empty vector pathData" }
                        val path = PathParser.createPathFromPathData(pathData)
                            ?: error("Invalid protected vector path")
                        path.fillType = when (parser.androidAttr("fillType")?.lowercase()) {
                            null, "nonzero", "winding" -> Path.FillType.WINDING
                            "evenodd", "even_odd" -> Path.FillType.EVEN_ODD
                            else -> error("Unsupported vector fillType")
                        }

                        paths += SecureVectorPath(
                            path = path,
                            fillColor = parser.androidAttr("fillColor")?.let(::parseColor),
                            fillAlpha = parser.vectorFloat("fillAlpha", 1f, 0f, 1f),
                            strokeColor = parser.androidAttr("strokeColor")?.let(::parseColor),
                            strokeAlpha = parser.vectorFloat("strokeAlpha", 1f, 0f, 1f),
                            strokeWidth = parser.vectorFloat("strokeWidth", 0f, 0f, Float.MAX_VALUE),
                            strokeCap = when (parser.androidAttr("strokeLineCap")?.lowercase()) {
                                null, "butt" -> Paint.Cap.BUTT
                                "round" -> Paint.Cap.ROUND
                                "square" -> Paint.Cap.SQUARE
                                else -> error("Unsupported vector strokeLineCap")
                            },
                            strokeJoin = when (parser.androidAttr("strokeLineJoin")?.lowercase()) {
                                null, "miter" -> Paint.Join.MITER
                                "round" -> Paint.Join.ROUND
                                "bevel" -> Paint.Join.BEVEL
                                else -> error("Unsupported vector strokeLineJoin")
                            },
                            strokeMiter = parser.vectorFloat("strokeMiterLimit", 4f, 0f, Float.MAX_VALUE, exclusiveMin = true)
                        )
                        require(parser.androidAttr("trimPathStart") == null && parser.androidAttr("trimPathEnd") == null && parser.androidAttr("trimPathOffset") == null) {
                            "Unsupported vector path trimming"
                        }
                    }

                    else -> error("Unsupported protected vector element: ${parser.name}")
                }
            }
            parser.next()
        }

        require(rootSeen && paths.isNotEmpty()) { "Empty protected vector" }
        return SecureVector(viewportWidth, viewportHeight, paths)
    }

    private fun XmlPullParser.androidAttr(name: String): String? =
        getAttributeValue(ANDROID_NS, name) ?: getAttributeValue(null, name)

    private fun XmlPullParser.vectorFloat(
        name: String,
        default: Float,
        min: Float,
        max: Float,
        exclusiveMin: Boolean = false
    ): Float {
        val raw = androidAttr(name)
        val value = if (raw == null) default else raw.toFloatOrNull()
        require(value != null && value.isFinite() && value <= max && (if (exclusiveMin) value > min else value >= min)) {
            "Invalid vector $name: $raw"
        }
        return value
    }

    private fun parseColor(raw: String): Int = when {
        raw == "@android:color/transparent" -> AndroidColor.TRANSPARENT
        raw.equals("transparent", ignoreCase = true) -> AndroidColor.TRANSPARENT
        raw.startsWith("#") -> AndroidColor.parseColor(raw)
        else -> error("Unsupported vector color: $raw")
    }
}

