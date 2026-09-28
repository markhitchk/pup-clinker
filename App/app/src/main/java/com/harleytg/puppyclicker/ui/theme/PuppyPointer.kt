package com.harleytg.puppyclicker.ui.theme

import android.graphics.BitmapFactory
import android.view.PointerIcon as AndroidPointerIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalContext
import com.harleytg.puppyclicker.R

private const val PUPPY_POINTER_SIZE_PX = 64
private const val PUPPY_POINTER_HOTSPOT_PX = 16f

/**
 * Creates the Puppy Clicker paw as a real Android pointer icon.
 *
 * The packaged PNG is rendered directly from assets/cursors/paw_default.svg so
 * the runtime cursor preserves the designed gradients, outline, transparency,
 * and drop shadow instead of approximating the SVG as an Android vector.
 *
 * PointerIcon is available from API 24; Puppy Clicker has minSdk 26.
 * The 64x64 bitmap and 16x16 hotspot match assets/cursors/README.md.
 */
@Composable
internal fun rememberPuppyPointerIcon(): PointerIcon {
    val context = LocalContext.current

    return remember(context) {
        val bitmap = checkNotNull(
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.puppy_pointer_paw_exact
            )
        ) { "Missing Puppy Clicker pointer bitmap" }

        check(bitmap.width == PUPPY_POINTER_SIZE_PX && bitmap.height == PUPPY_POINTER_SIZE_PX) {
            "Puppy Clicker pointer must be 64x64 px; got ${bitmap.width}x${bitmap.height}"
        }

        PointerIcon(
            AndroidPointerIcon.create(
                bitmap,
                PUPPY_POINTER_HOTSPOT_PX,
                PUPPY_POINTER_HOTSPOT_PX
            )
        )
    }
}
