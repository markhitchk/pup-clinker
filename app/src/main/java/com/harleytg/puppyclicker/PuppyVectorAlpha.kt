package com.harleytg.puppyclicker

import kotlin.math.roundToInt

/** Combines a path color's alpha with its explicit Android vector opacity. */
internal fun combinedVectorAlpha(color: Int, opacity: Float): Int {
    require(opacity.isFinite() && opacity in 0f..1f) { "Invalid vector opacity: $opacity" }
    return (((color ushr 24) and 0xff) * opacity).roundToInt().coerceIn(0, 255)
}
