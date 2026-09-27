package com.harleytg.puppyclicker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * Shared transparent fallback.png renderer for every runtime-streamed image asset.
 *
 * The fallback is precomposed into a regular BitmapPainter instead of delegating
 * drawing through a custom Painter. This keeps startup/rendering paths predictable
 * across Android/Compose versions while preserving deterministic asset backgrounds.
 */
@Composable
internal fun streamedImageFallbackPainter(
    assetKey: String,
    background: Color = streamedFallbackBackgroundColor(assetKey)
): Painter {
    val context = LocalContext.current.applicationContext
    val fallback = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.fallback)
            ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    }

    val composed = remember(assetKey, background, fallback) {
        val width = fallback.width.coerceAtLeast(2)
        val height = fallback.height.coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background.toArgb())
        canvas.drawBitmap(
            fallback,
            Rect(0, 0, fallback.width, fallback.height),
            Rect(0, 0, width, height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        bitmap
    }

    return remember(composed) {
        BitmapPainter(composed.asImageBitmap())
    }
}

internal fun streamedFallbackBackgroundColor(assetKey: String): Color {
    var hash = 0x811C9DC5.toInt()
    assetKey.lowercase().forEach { char ->
        hash = (hash xor char.code) * 16_777_619
    }

    fun channel(shift: Int): Float =
        (72 + ((hash ushr shift) and 0x7F)) / 255f

    return Color(
        red = channel(16),
        green = channel(8),
        blue = channel(0),
        alpha = 1f
    )
}
