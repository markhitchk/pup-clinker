package com.harleytg.puppyclicker.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.harleytg.puppyclicker.PuppyThemeMode
import com.harleytg.puppyclicker.PuppyUiPreferences
import com.harleytg.puppyclicker.PuppyUiScale

val LocalPuppyReducedMotion = staticCompositionLocalOf { false }
val LocalPuppyAnimatedUi = staticCompositionLocalOf { true }
val LocalPuppyButtonAnimations = staticCompositionLocalOf { true }
val LocalPuppyUiScale = staticCompositionLocalOf { PuppyUiScale.DEFAULT }

private fun colorFromHex(hex: String): Color = runCatching {
    Color(AndroidColor.parseColor(PuppyUiPreferences.normalizeHex(hex) ?: PuppyUiPreferences.DEFAULT_ACCENT))
}.getOrDefault(Color(0xFF00B8F0))

private fun readableOn(color: Color): Color =
    if (color.luminance() > 0.52f) Color(0xFF071014) else Color.White

private fun lightColors(accent: Color, highContrast: Boolean) = lightColorScheme(
    primary = accent,
    onPrimary = readableOn(accent),
    primaryContainer = accent.copy(alpha = if (highContrast) 0.26f else 0.16f),
    onPrimaryContainer = Color(0xFF071014),
    secondary = Color(0xFF35657A),
    onSecondary = Color.White,
    tertiary = Color(0xFF2E8B64),
    onTertiary = Color.White,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF171A1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A1D),
    surfaceVariant = if (highContrast) Color(0xFFE5E9EC) else Color(0xFFF1F3F5),
    onSurfaceVariant = if (highContrast) Color(0xFF252A2E) else Color(0xFF555C62),
    outline = if (highContrast) Color(0xFF5A6269) else Color(0xFFB6BDC2),
    outlineVariant = if (highContrast) Color(0xFF8B9399) else Color(0xFFD7DCE0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private fun darkColors(accent: Color, highContrast: Boolean) = darkColorScheme(
    primary = accent,
    onPrimary = readableOn(accent),
    primaryContainer = accent.copy(alpha = if (highContrast) 0.36f else 0.22f),
    onPrimaryContainer = Color(0xFFF6FBFF),
    secondary = Color(0xFF9DCBE0),
    onSecondary = Color(0xFF0A2632),
    tertiary = Color(0xFF80D7AE),
    onTertiary = Color(0xFF053823),
    background = Color(0xFF0C0F12),
    onBackground = Color(0xFFF2F5F7),
    surface = Color(0xFF151A1F),
    onSurface = Color(0xFFF2F5F7),
    surfaceVariant = if (highContrast) Color(0xFF252D34) else Color(0xFF1C232A),
    onSurfaceVariant = if (highContrast) Color(0xFFE7EDF1) else Color(0xFFB8C1C8),
    outline = if (highContrast) Color(0xFFABB5BD) else Color(0xFF515B64),
    outlineVariant = if (highContrast) Color(0xFF747E87) else Color(0xFF313941),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun PuppyClickerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val ui by PuppyUiPreferences.observe(context).collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (ui.themeMode) {
        PuppyThemeMode.LIGHT -> false
        PuppyThemeMode.DARK -> true
        PuppyThemeMode.SYSTEM -> systemDark
    }
    val accent = colorFromHex(ui.accentHex)
    val colors = if (dark) darkColors(accent, ui.highContrast) else lightColors(accent, ui.highContrast)
    val baseDensity = LocalDensity.current
    val densityScale = when (ui.uiScale) {
        PuppyUiScale.COMPACT -> 0.94f
        PuppyUiScale.DEFAULT -> 1.0f
        PuppyUiScale.LARGE -> 1.08f
    }
    val scaledDensity = Density(
        density = baseDensity.density * densityScale,
        fontScale = baseDensity.fontScale
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalPuppyReducedMotion provides ui.reducedMotion,
        LocalPuppyAnimatedUi provides (ui.animatedUi && !ui.reducedMotion),
        LocalPuppyButtonAnimations provides (ui.buttonAnimations && !ui.reducedMotion),
        LocalPuppyUiScale provides ui.uiScale
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography(),
            content = content
        )
    }
}
