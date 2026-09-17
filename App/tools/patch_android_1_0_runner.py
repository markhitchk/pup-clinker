#!/usr/bin/env python3
"""Compatibility runner for the final Android 1.0 generated-source integration."""
from pathlib import Path
import sys

import patch_android_1_0_completion as core

PACKAGE = Path("com/harleytg/puppyclicker")


def pre_normalize(root: Path) -> None:
    vm = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    source = vm.read_text(encoding="utf-8")

    # PupEye/ticket cadence uses nextTaps in the current runtime. Normalize only the local tap
    # variable name so the 1.0 insertion point remains stable without changing behavior.
    tap_start = source.find("    fun tapPuppy()")
    tap_end = source.find("    fun buyCookieUpgrade", tap_start)
    if tap_start < 0 or tap_end < 0:
        raise RuntimeError("Android 1.0 runner: tapPuppy section not found")
    tap = source[tap_start:tap_end]
    tap = tap.replace("nextTaps", "nextTotalTaps")
    source = source[:tap_start] + tap + source[tap_end:]
    vm.write_text(source, encoding="utf-8")


def post_fix(root: Path) -> None:
    vm = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    source = vm.read_text(encoding="utf-8")

    # The core patch's compact park/daily compatibility insertion can sit next to the multiline
    # per-puppy Bond insertion. Keep only the multiline authoritative map update.
    source = source.replace(
        "            bondByPuppyId = PuppyProgression.withBondDelta(s.bondByPuppyId, s.puppyStyle, 3),\n            bond = (s.bond + 3).coerceAtMost(100),",
        "            bond = (s.bond + 3).coerceAtMost(100),",
    )
    source = source.replace(
        "            bondByPuppyId = PuppyProgression.withBondDelta(s.bondByPuppyId, s.puppyStyle, 1),\n            bond = (s.bond + 1).coerceAtMost(100)",
        "            bond = (s.bond + 1).coerceAtMost(100)",
    )

    # Casino progression must read the original transaction result before constructing its copy.
    source = source.replace(
        "result.copy(state = awardNewPuppyXp(before, progressedResult.state))",
        "result.copy(state = awardNewPuppyXp(before, result.state))",
    )
    vm.write_text(source, encoding="utf-8")


def main(root: Path) -> None:
    pre_normalize(root)
    core.main(root)
    post_fix(root)
    print("Puppy Clicker Android 1.0 compatibility runner complete")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_android_1_0_runner.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
