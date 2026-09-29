package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/**
 * Full-width Compose dialogs on Android 15+ (and always when targeting SDK 35/36) are laid out
 * edge-to-edge: the dialog window is sized from the full display, so content that fills the
 * dialog would otherwise draw underneath the status bar, display cutout, and the gesture /
 * three-button navigation bar.
 *
 * These dialogs opt out of decor fitting explicitly so behavior is identical on every API level,
 * and the content applies [puppyDialogSafeDrawingPadding] itself. Scrims and full-bleed
 * backgrounds can still draw behind the system bars while interactive content stays inside the
 * safe drawing area.
 */
internal fun puppyEdgeToEdgeDialogProperties(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true
): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false
)

/**
 * Keeps dialog content clear of the status bar, display cutouts, the navigation bar, and the IME.
 */
internal fun Modifier.puppyDialogSafeDrawingPadding(): Modifier =
    this.safeDrawingPadding()
