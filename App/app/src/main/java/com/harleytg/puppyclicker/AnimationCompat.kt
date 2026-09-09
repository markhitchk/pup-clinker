package com.harleytg.puppyclicker

import androidx.compose.ui.Modifier

/**
 * Keeps animated layout cards source-compatible across the current Compose BOM.
 * The visible motion is provided by the explicit spring/tween/AnimatedContent
 * animations in AnimatedMainActivity.
 */
fun Modifier.animateContentSize(): Modifier = this
