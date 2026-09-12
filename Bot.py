#!/usr/bin/env python3
"""Compatibility launcher for hosts configured to run /app/Bot.py."""

from pathlib import Path
import runpy

SCRIPT = Path(__file__).resolve().parent / "Bot" / "discord_puppy_forum_poster.py"

if not SCRIPT.is_file():
    raise SystemExit(f"Bot script not found: {SCRIPT}")

runpy.run_path(str(SCRIPT), run_name="__main__")
