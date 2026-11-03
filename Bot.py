#!/usr/bin/env python3
"""Puppy Clicker Discord launcher (live bot or explicit forum maintenance mode)."""

import os
import runpy
import sys
from pathlib import Path

mode = os.environ.get("PUPPY_MODE", "serve").strip().lower()
if mode not in {"serve", "verify", "post"}:
    raise SystemExit("PUPPY_MODE must be serve, verify, or post")
script = "puppy_bot.py" if mode == "serve" else "discord_puppy_forum_poster.py"
path = Path(__file__).resolve().parent / "Bot" / script
if not path.is_file():
    raise SystemExit(f"Bot script not found: {path}")
sys.path.insert(0, str(path.parent))
runpy.run_path(str(path), run_name="__main__")
