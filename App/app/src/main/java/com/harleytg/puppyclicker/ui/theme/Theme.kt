package com.harleytg.puppyclicker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4EFF),
    secondary = Color(0xFFFF7F67),
    tertiary = Color(0xFF2EAE78),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1EDFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A7FF),
    secondary = Color(0xFFFFB4A4),
    tertiary = Color(0xFF72DBAA),
    background = Color(0xFF111018),
    surface = Color(0xFF1A1823),
    surfaceVariant = Color(0xFF282337)
)

@Composable
fun PuppyClickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
