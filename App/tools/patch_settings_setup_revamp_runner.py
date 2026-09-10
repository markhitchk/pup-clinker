#!/usr/bin/env python3
"""Compatibility runner for the Settings/setup final patch.

The PupEye streamer is actively maintained and its refresh method may receive implementation-only
changes. Replace that function by signature rather than requiring one historical body string, then
run the rest of the final integration unchanged.
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


patch.patch_pupeye_stream = robust_pupeye_stream

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_settings_setup_revamp_runner.py GENERATED_SOURCE_ROOT")
    patch.main(Path(sys.argv[1]))
