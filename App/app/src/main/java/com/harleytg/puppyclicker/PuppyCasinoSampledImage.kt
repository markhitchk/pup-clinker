package com.harleytg.puppyclicker

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

/**
 * Decodes large Casino PNG artwork with a bounded in-memory size.
 *
 * The source PNG remains untouched in res/drawable, but screens no longer need
 * to keep multi-megapixel ARGB bitmaps resident when the artwork is displayed
 * at phone-sized dimensions. If sampled decoding fails for any reason, Compose
 * falls back to painterResource rather than crashing the game screen.
 */
@Composable
internal fun PuppyCasinoSampledImage(
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    maxDimensionPx: Int = 720
) {
    val resources = LocalContext.current.resources
    val bitmap = remember(drawableRes, maxDimensionPx) {
        runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                inScaled = false
            }
            BitmapFactory.decodeResource(resources, drawableRes, bounds)

            val sourceMax = maxOf(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            val target = maxDimensionPx.coerceAtLeast(256)
            while (sourceMax / sampleSize > target && sampleSize < 16) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inScaled = false
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeResource(resources, drawableRes, options)?.asImageBitmap()
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
