package com.harleytg.puppyclicker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

/**
 * Shared theme-aware fallback for every runtime-streamed image surface.
 *
 * The fallback is deliberately local so first launch, airplane mode, CDN failures and
 * corrupted remote image responses never leave a blank image slot.
 */
@Composable
internal fun streamedImageFallbackPainter(): Painter {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return painterResource(
        if (darkTheme) R.drawable.stream_fallback_dark
        else R.drawable.stream_fallback_light
    )
}
