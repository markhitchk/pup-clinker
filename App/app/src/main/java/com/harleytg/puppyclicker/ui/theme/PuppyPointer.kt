package com.harleytg.puppyclicker.ui.theme

import android.graphics.BitmapFactory
import android.view.PointerIcon as AndroidPointerIcon
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalContext
import com.harleytg.puppyclicker.R

private const val PUPPY_POINTER_SIZE_PX = 64
private const val PUPPY_POINTER_HOTSPOT_PX = 16f

/**
 * Cursor states backed by exact 64x64 PNG resources in drawable-nodpi.
 *
 * Keeping the bitmaps in drawable-nodpi prevents Android density scaling before
 * PointerIcon is created, so the hotspot and artwork stay pixel-accurate.
 */
internal enum class PuppyPointerState {
    DEFAULT,
    HOVER,
    CLICK,
    TEXT_SELECT,
    LINK,
    GRAB,
    GRABBING,
    NOT_ALLOWED,
    WORKING,
    PRECISION,
    ALTERNATE,
    SECONDARY
}

@DrawableRes
private fun puppyPointerResource(
    darkTheme: Boolean,
    state: PuppyPointerState
): Int = when (state) {
    PuppyPointerState.DEFAULT ->
        if (darkTheme) R.drawable.puppy_pointer_dark_default
        else R.drawable.puppy_pointer_light_default

    PuppyPointerState.HOVER ->
        if (darkTheme) R.drawable.puppy_pointer_dark_hover
        else R.drawable.puppy_pointer_light_hover

    PuppyPointerState.CLICK ->
        if (darkTheme) R.drawable.puppy_pointer_dark_click
        else R.drawable.puppy_pointer_light_click

    PuppyPointerState.TEXT_SELECT ->
        if (darkTheme) R.drawable.puppy_pointer_dark_text_select
        else R.drawable.puppy_pointer_light_text_select

    PuppyPointerState.LINK ->
        if (darkTheme) R.drawable.puppy_pointer_dark_link
        else R.drawable.puppy_pointer_light_link

    PuppyPointerState.GRAB ->
        if (darkTheme) R.drawable.puppy_pointer_dark_grab
        else R.drawable.puppy_pointer_light_grab

    PuppyPointerState.GRABBING ->
        if (darkTheme) R.drawable.puppy_pointer_dark_grabbing
        else R.drawable.puppy_pointer_light_grabbing

    PuppyPointerState.NOT_ALLOWED ->
        if (darkTheme) R.drawable.puppy_pointer_dark_not_allowed
        else R.drawable.puppy_pointer_light_not_allowed

    PuppyPointerState.WORKING ->
        if (darkTheme) R.drawable.puppy_pointer_dark_working
        else R.drawable.puppy_pointer_light_working

    PuppyPointerState.PRECISION ->
        if (darkTheme) R.drawable.puppy_pointer_dark_precision
        else R.drawable.puppy_pointer_light_precision

    PuppyPointerState.ALTERNATE ->
        if (darkTheme) R.drawable.puppy_pointer_dark_alternate
        else R.drawable.puppy_pointer_light_alternate

    PuppyPointerState.SECONDARY ->
        if (darkTheme) R.drawable.puppy_pointer_dark_secondary
        else R.drawable.puppy_pointer_light_secondary
}

/**
 * Creates a Puppy Clicker pointer icon from the exact packaged PNG for the
 * requested app theme and cursor state.
 *
 * All resources are 64x64 transparent PNGs in drawable-nodpi and use the
 * canonical (16, 16) hotspot.
 */
@Composable
internal fun rememberPuppyPointerIcon(
    darkTheme: Boolean,
    state: PuppyPointerState = PuppyPointerState.DEFAULT
): PointerIcon {
    val context = LocalContext.current
    val resourceId = puppyPointerResource(darkTheme, state)

    return remember(context, resourceId) {
        val bitmap = checkNotNull(
            BitmapFactory.decodeResource(context.resources, resourceId)
        ) {
            "Missing Puppy Clicker pointer bitmap for theme=" +
                (if (darkTheme) "dark" else "light") +
                ", state=$state"
        }

        check(bitmap.width == PUPPY_POINTER_SIZE_PX && bitmap.height == PUPPY_POINTER_SIZE_PX) {
            "Puppy Clicker pointer must be 64x64 px; got " +
                "${bitmap.width}x${bitmap.height} for state=$state"
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
