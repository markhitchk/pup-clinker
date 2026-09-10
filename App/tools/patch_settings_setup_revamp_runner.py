#!/usr/bin/env python3
"""Compatibility runner for the Settings/setup final patch.

The final integration runs after the established generated-source transforms. Small compatibility
adjustments live here so actively maintained source helpers do not need to be duplicated by the new
Settings UI.
"""
from pathlib import Path
import sys
import patch_settings_setup_revamp as patch


def robust_pupeye_stream(source: str) -> str:
    return patch.replace_function(
        source,
        "    suspend fun refreshNow(context: Context): Boolean",
        "\n    fun clearCache",
        '''    suspend fun refreshNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("checked")
            .remove("attempted")
            .apply()

        load(appContext)
        // Cached branding keeps the UI functional but does not prove current connectivity.
        status(appContext).lastCheckedAtMs > 0L
    }''',
        "PupEye online verification semantics",
    )


_original_patch_activity = patch.patch_activity


def compatible_activity(source: str) -> str:
    source = _original_patch_activity(source)
    return patch.replace_once(
        source,
        "private fun performV6Haptic(context: Context, strong: Boolean = false)",
        "internal fun performV6Haptic(context: Context, strong: Boolean = false)",
        "V6 haptic helper visibility",
    )


patch.patch_pupeye_stream = robust_pupeye_stream
patch.patch_activity = compatible_activity

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_settings_setup_revamp_runner.py GENERATED_SOURCE_ROOT")
    patch.main(Path(sys.argv[1]))
