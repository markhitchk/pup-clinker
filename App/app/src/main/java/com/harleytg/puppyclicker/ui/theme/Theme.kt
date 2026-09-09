package com.harleytg.puppyclicker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harleytg.puppyclicker.StreamedPupEyeBranding

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
        typography = Typography()
    ) {
        Box(Modifier.fillMaxSize()) {
            content()

            // PupEye is intentionally streamed from assets/PupEye.png so the
            // security branding can be updated independently of an APK release.
            Box(
                Modifier
                    .matchParentSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                StreamedPupEyeBranding(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(38.dp)
                        .alpha(0.94f),
                    contentDescription = "PupEye fair-play protection"
                )
            }
        }
    }
}
