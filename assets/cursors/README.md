# Puppy Clicker Mouse Cursor Assets

These cursor sources are designed to match Puppy Clicker's current UI accent and mascot styling.

## Files

- `paw_default.svg` — normal pointer
- `paw_hover.svg` — hover / emphasized pointer
- `paw_click.svg` — pressed click state
- `paw_drag.svg` — drag / move state
- `paw_text.svg` — text-entry pointer
- `paw_casino.svg` — Pup Coin cursor for casino scratch interactions
- `paw_link.svg` — clickable link state
- `paw_disabled.svg` — unavailable / disabled state

## Rendering

Source canvas: **64 × 64**

Recommended Android pointer hotspot for paw cursors: **(16, 16)**.

The casino coin can use a centered interaction hotspot when used specifically as a scratch tool.

## Theme mapping

Primary cyan: `#00B8F0`  
Dark app background: `#0C0F12`  
Dark app surface: `#151A1F`

The cursor artwork is transparent and is intended to remain readable on both Puppy Clicker light and dark themes.

## Android

Keep these SVG files as the source-of-truth artwork.

The Android app now uses a native pointer bridge:

- `App/app/src/main/res/drawable/puppy_pointer_paw.xml` is the packaged runtime derivative of `paw_default.svg`.
- `PuppyPointer.kt` rasterizes that drawable to a 64 × 64 bitmap and creates `android.view.PointerIcon` with hotspot `(16, 16)`.
- `PuppyClickerTheme` applies the custom pointer with Compose `Modifier.pointerHoverIcon`, so every activity using the shared theme receives the paw cursor automatically.
- Touchscreen input remains unaffected; the custom cursor is only visible for mouse/trackpad hover input.

Additional SVG states remain available for future per-control hover, click, drag, text, disabled, and casino cursor overrides.
