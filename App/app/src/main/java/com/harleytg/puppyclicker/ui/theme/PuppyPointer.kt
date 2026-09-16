package com.harleytg.puppyclicker.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.PointerIcon as AndroidPointerIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.harleytg.puppyclicker.R

private const val PUPPY_POINTER_SIZE_PX = 64
private const val PUPPY_POINTER_HOTSPOT_PX = 16f

/**
 * Creates the Puppy Clicker paw as a real Android pointer icon.
 *
 * PointerIcon is available from API 24; Puppy Clicker has minSdk 26.
 * The 64x64 canvas and 16x16 hotspot match assets/cursors/README.md.
 */
@Composable
internal fun rememberPuppyPointerIcon(): PointerIcon {
    val context = LocalContext.current

    return remember(context) {
        val drawable = checkNotNull(
            ContextCompat.getDrawable(context, R.drawable.puppy_pointer_paw)
        ) { "Missing Puppy Clicker pointer drawable" }

        val bitmap = Bitmap.createBitmap(
            PUPPY_POINTER_SIZE_PX,
            PUPPY_POINTER_SIZE_PX,
            Bitmap.Config.ARGB_8888
        )
        drawable.setBounds(0, 0, PUPPY_POINTER_SIZE_PX, PUPPY_POINTER_SIZE_PX)
        drawable.draw(Canvas(bitmap))

        PointerIcon(
            AndroidPointerIcon.create(
                bitmap,
                PUPPY_POINTER_HOTSPOT_PX,
                PUPPY_POINTER_HOTSPOT_PX
            )
        )
    }
}
