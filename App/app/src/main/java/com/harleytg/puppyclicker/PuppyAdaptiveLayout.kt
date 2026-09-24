package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class PuppyWindowClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

internal enum class PuppyHeightClass {
    SHORT,
    REGULAR,
    TALL
}

@Immutable
internal data class PuppyViewport(
    val widthDp: Float,
    val heightDp: Float,
    val widthClass: PuppyWindowClass,
    val heightClass: PuppyHeightClass
) {
    val horizontalPadding: Dp
        get() = when (widthClass) {
            PuppyWindowClass.COMPACT -> 12.dp
            PuppyWindowClass.MEDIUM -> 20.dp
            PuppyWindowClass.EXPANDED -> 28.dp
        }

    val primaryContentMaxWidth: Dp
        get() = when (widthClass) {
            PuppyWindowClass.COMPACT -> 600.dp
            PuppyWindowClass.MEDIUM -> 780.dp
            PuppyWindowClass.EXPANDED -> 1180.dp
        }

    val readableContentMaxWidth: Dp
        get() = when (widthClass) {
            PuppyWindowClass.COMPACT -> 600.dp
            PuppyWindowClass.MEDIUM -> 720.dp
            PuppyWindowClass.EXPANDED -> 820.dp
        }

    val dialogMaxWidth: Dp
        get() = when (widthClass) {
            PuppyWindowClass.COMPACT -> 600.dp
            PuppyWindowClass.MEDIUM -> 680.dp
            PuppyWindowClass.EXPANDED -> 760.dp
        }

    val isCompact: Boolean get() = widthClass == PuppyWindowClass.COMPACT
    val isExpanded: Boolean get() = widthClass == PuppyWindowClass.EXPANDED
    val isShort: Boolean get() = heightClass == PuppyHeightClass.SHORT
}

internal object PuppyAdaptiveLayout {
    const val MEDIUM_WIDTH_DP = 600f
    const val EXPANDED_WIDTH_DP = 840f
    const val REGULAR_HEIGHT_DP = 600f
    const val TALL_HEIGHT_DP = 840f

    fun classify(widthDp: Float): PuppyWindowClass = when {
        widthDp < MEDIUM_WIDTH_DP -> PuppyWindowClass.COMPACT
        widthDp < EXPANDED_WIDTH_DP -> PuppyWindowClass.MEDIUM
        else -> PuppyWindowClass.EXPANDED
    }

    fun classifyHeight(heightDp: Float): PuppyHeightClass = when {
        heightDp < REGULAR_HEIGHT_DP -> PuppyHeightClass.SHORT
        heightDp < TALL_HEIGHT_DP -> PuppyHeightClass.REGULAR
        else -> PuppyHeightClass.TALL
    }

    fun viewport(widthDp: Float, heightDp: Float): PuppyViewport = PuppyViewport(
        widthDp = widthDp,
        heightDp = heightDp,
        widthClass = classify(widthDp),
        heightClass = classifyHeight(heightDp)
    )
}

internal val LocalPuppyViewport = staticCompositionLocalOf {
    PuppyAdaptiveLayout.viewport(widthDp = 360f, heightDp = 800f)
}

/**
 * One app-wide source of truth for the currently available Compose viewport.
 *
 * BoxWithConstraints means this follows split-screen, foldables, tablets, rotation and
 * free-form window resizing instead of assuming the physical display size.
 */
@Composable
internal fun PuppyAdaptiveRoot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewport = PuppyAdaptiveLayout.viewport(
            widthDp = maxWidth.value,
            heightDp = maxHeight.value
        )
        CompositionLocalProvider(LocalPuppyViewport provides viewport) {
            content()
        }
    }
}

/** Centers normal app content and prevents phone-oriented surfaces from stretching on tablets. */
@Composable
internal fun PuppyResponsiveContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = LocalPuppyViewport.current.primaryContentMaxWidth,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxSize()
        ) {
            content()
        }
    }
}
