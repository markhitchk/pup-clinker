#!/usr/bin/env python3
"""Compatibility runner for the final Android 1.0 generated-source integration."""
from pathlib import Path
import sys

import patch_android_1_0_completion as core

PACKAGE = Path("com/harleytg/puppyclicker")
SENTINEL_START = "/* ANDROID_1_0_SCHEMA2_SENTINEL\n"
SENTINEL_END = "ANDROID_1_0_SCHEMA2_SENTINEL_END */\n"


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

    # Puppy Code schema-2 replaces the legacy synchronous redeem function before this patch runs.
    # The core transform still has one legacy ownership-XP anchor. Feed that anchor a comment-only
    # sentinel, then attach XP to the real RewardGrantEngine path in post_fix().
    if "fun redeemCode(rawCode: String, onResult: (V6RedeemOutcome) -> Unit)" in source:
        prestige = source.find("    fun prestige() {")
        if prestige < 0:
            raise RuntimeError("Android 1.0 runner: prestige anchor not found for Puppy Code sentinel")
        sentinel = SENTINEL_START + '''            redeemedCodeIds = s.redeemedCodeIds + reward.id
        )
        saveState()
        return V6RedeemOutcome(true, reward.message)
''' + SENTINEL_END
        source = source[:prestige] + sentinel + source[prestige:]

    vm.write_text(source, encoding="utf-8")


def post_fix(root: Path) -> None:
    vm = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    source = vm.read_text(encoding="utf-8")

    # Remove the comment-only schema-2 compatibility sentinel after the core anchor is consumed.
    start = source.find(SENTINEL_START)
    if start >= 0:
        end = source.find(SENTINEL_END, start)
        if end < 0:
            raise RuntimeError("Android 1.0 runner: Puppy Code sentinel end missing")
        source = source[:start] + source[end + len(SENTINEL_END):]

    # Attach new-puppy XP to the actual live, validated Puppy Code grant result.
    schema2_old = '''                        val next = grant.state.copy(
                            redeemedCodeIds = current.redeemedCodeIds + definition.id
                        )'''
    schema2_new = '''                        val next = awardNewPuppyXp(
                            current,
                            grant.state.copy(
                                redeemedCodeIds = current.redeemedCodeIds + definition.id
                            )
                        )'''
    if schema2_old in source:
        source = source.replace(schema2_old, schema2_new, 1)

    # Persist progression atomically with the schema-2 reward grant itself.
    commit_anchor = '''        putStringSet(KEY_REDEEMED_CODES, next.redeemedCodeIds)
        putString(KEY_PUPPY_CODE_HISTORY, PuppyCodeHistory.encode(history))'''
    if commit_anchor in source:
        source = source.replace(
            commit_anchor,
            '''        putStringSet(KEY_REDEEMED_CODES, next.redeemedCodeIds)
        putLong(PuppyProgressionStore.KEY_PLAYER_XP, next.playerXp)
        putStringSet(PuppyProgressionStore.KEY_XP_SETTLEMENTS, next.xpSettlementIds)
        putString(PuppyProgressionStore.KEY_BOND_BY_PUPPY, PuppyProgressionStore.encodeBondMap(next.bondByPuppyId))
        putString(KEY_PUPPY_CODE_HISTORY, PuppyCodeHistory.encode(history))''',
            1,
        )

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
