package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal object PuppyCasinoCartoonMath {
    fun clampUnit(value: Float): Float = value.coerceIn(0f, 1f)

    fun segmentCenterDegrees(weights: List<Float>, index: Int): Float {
        if (weights.isEmpty()) return -90f
        val safeIndex = index.coerceIn(0, weights.lastIndex)
        val total = weights.sum().takeIf { it > 0f } ?: 100f
        val before = weights.take(safeIndex).sum() / total * 360f
        val half = weights[safeIndex] / total * 180f
        return -90f + before + half
    }

    fun impactPulse(progress: Float, impactAt: Float, radius: Float): Float {
        if (radius <= 0f) return 0f
        val normalized = 1f - (abs(progress - impactAt) / radius)
        return clampUnit(normalized)
    }
}

@Immutable
internal data class PuppyCasinoCartoonPalette(
    val accent: Color,
    val accentSoft: Color,
    val accentDeep: Color,
    val cream: Color,
    val ink: Color,
    val woodLight: Color,
    val woodMid: Color,
    val woodDark: Color,
    val gold: Color,
    val goldDeep: Color,
    val felt: Color,
    val feltDeep: Color,
    val glass: Color,
    val shadow: Color
)

@Composable
internal fun puppyCasinoCartoonPalette(): PuppyCasinoCartoonPalette {
    val scheme = MaterialTheme.colorScheme
    return PuppyCasinoCartoonPalette(
        accent = scheme.primary,
        accentSoft = scheme.primaryContainer,
        accentDeep = scheme.primary.copy(alpha = 0.82f),
        cream = if (scheme.surface.luminance() > 0.5f) Color(0xFFFFF7E8) else Color(0xFF25211A),
        ink = scheme.onSurface,
        woodLight = Color(0xFFC9864B),
        woodMid = Color(0xFF8E512C),
        woodDark = Color(0xFF4A281A),
        gold = Color(0xFFFFC83D),
        goldDeep = Color(0xFFB66A00),
        felt = Color(0xFF0E6B49),
        feltDeep = Color(0xFF073E2D),
        glass = Color(0xFFDDF7FF).copy(alpha = if (scheme.surface.luminance() > 0.5f) 0.72f else 0.28f),
        shadow = Color.Black.copy(alpha = if (scheme.surface.luminance() > 0.5f) 0.24f else 0.48f)
    )
}

@Composable
internal fun CartoonStageFrame(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    title: String? = null,
    background: Brush? = null,
    corner: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = puppyCasinoCartoonPalette()
    val shape = RoundedCornerShape(corner)
    val stageBrush = background ?: Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface,
            accent.copy(alpha = 0.06f)
        )
    )

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, accent.copy(alpha = 0.55f)),
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(stageBrush)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.26f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, palette.shadow.copy(alpha = 0.22f))
                        )
                    )
            )
            content()
            if (title != null) {
                CartoonBonePlaque(
                    text = title,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
internal fun CartoonBonePlaque(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val palette = puppyCasinoCartoonPalette()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = palette.cream,
        border = BorderStroke(2.dp, accent.copy(alpha = 0.72f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🐾", color = accent, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(7.dp))
            Text(
                text = text,
                color = palette.ink,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(7.dp))
            Text("🐾", color = accent, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun CartoonPawBadge(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    label: String = "🐾"
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, accent.copy(alpha = 0.7f)),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = accent, fontWeight = FontWeight.Black)
        }
    }
}

internal fun cartoonPanelGradient(
    top: Color,
    middle: Color,
    bottom: Color
): Brush = Brush.verticalGradient(listOf(top, middle, bottom))
