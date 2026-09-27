package com.harleytg.puppyclicker

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

/**
 * One shared transparent PNG fallback for every runtime-streamed image surface.
 *
 * The caller owns the surface/background color. Puppy portraits therefore keep
 * their style-specific dynamic background even when their streamed PNG is missing.
 */
@Composable
internal fun streamedImageFallbackPainter(): Painter =
    painterResource(R.drawable.fallback)
