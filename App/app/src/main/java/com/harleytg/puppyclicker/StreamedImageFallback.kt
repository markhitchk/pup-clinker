package com.harleytg.puppyclicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

/**
 * Shared transparent fallback.png renderer for every runtime-streamed image asset.
 *
 * The background is deterministic per asset key so a missing remote image remains
 * visually identifiable and stable between launches. Callers with an existing
 * contextual background (for example a puppy style) can pass it explicitly.
 */
@Composable
internal fun streamedImageFallbackPainter(
    assetKey: String,
    background: Color = streamedFallbackBackgroundColor(assetKey)
): Painter {
    val foreground = painterResource(R.drawable.fallback)
    return remember(foreground, assetKey, background) {
        StreamedFallbackPainter(
            foreground = foreground,
            background = background
        )
    }
}

internal fun streamedFallbackBackgroundColor(assetKey: String): Color {
    var hash = 0x811C9DC5.toInt()
    assetKey.lowercase().forEach { char ->
        hash = (hash xor char.code) * 16_777_619
    }

    // Keep colors away from near-black/near-white so fallback.png stays readable.
    fun channel(shift: Int): Float =
        (72 + ((hash ushr shift) and 0x7F)) / 255f

    return Color(
        red = channel(16),
        green = channel(8),
        blue = channel(0),
        alpha = 1f
    )
}

private class StreamedFallbackPainter(
    private val foreground: Painter,
    private val background: Color
) : Painter() {
    override val intrinsicSize: Size
        get() = foreground.intrinsicSize

    override fun DrawScope.onDraw() {
        drawRect(background)
        val targetSize = size
        with(foreground) {
            draw(
                size = targetSize,
                alpha = 1f,
                colorFilter = null
            )
        }
    }
}
