#!/usr/bin/env python3
"""Compatibility runner for the final Android 1.0 generated-source integration."""
from pathlib import Path
import sys

import patch_android_1_0_completion as core

PACKAGE = Path("com/harleytg/puppyclicker")
SCHEMA2_SENTINEL_START = "/* ANDROID_1_0_SCHEMA2_SENTINEL\n"
SCHEMA2_SENTINEL_END = "ANDROID_1_0_SCHEMA2_SENTINEL_END */\n"
GIFT_SENTINEL_START = "/* ANDROID_1_0_GIFT_SENTINEL\n"
GIFT_SENTINEL_END = "ANDROID_1_0_GIFT_SENTINEL_END */\n"

_ORIGINAL_PATCH_SETTINGS = core.patch_settings


def patch_settings_compat(source: str) -> str:
    """Disambiguate compact SettingsHome identity state from ProfileSettings badge state."""
    anchor = "    val officialDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)\n"
    if source.count(anchor) <= 1:
        return _ORIGINAL_PATCH_SETTINGS(source)

    home_start = source.find("private fun SettingsHome(")
    profile_start = source.find("private fun ProfileSettings(", home_start)
    if home_start < 0 or profile_start < 0:
        raise RuntimeError("Android 1.0 runner: SettingsHome/ProfileSettings boundary not found")

    home = source[home_start:profile_start]
    if anchor not in home:
        raise RuntimeError("Android 1.0 runner: duplicate developer anchor is not in SettingsHome")
    home = home.replace("officialDeveloper", "homeOfficialDeveloper")
    normalized = source[:home_start] + home + source[profile_start:]
    return _ORIGINAL_PATCH_SETTINGS(normalized)


# The core patch is intentionally strict. Route Settings through this compatibility shim so the
# already-generated compact account card cannot collide with the ProfileSettings badge anchor.
core.patch_settings = patch_settings_compat


def remove_sentinel(source: str, start_marker: str, end_marker: str, label: str) -> str:
    start = source.find(start_marker)
    if start < 0:
        return source
    end = source.find(end_marker, start)
    if end < 0:
        raise RuntimeError(f"Android 1.0 runner: {label} sentinel end missing")
    return source[:start] + source[end + len(end_marker):]


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

    # The current V6 AFK consumer uses the implicit `it` lambda name; the 1.0 hardening transform
    # intentionally matches a named state variable so the settlement rewrite stays narrow.
    afk_start = source.find("    private fun consumeClaimedAfkReward()")
    afk_end = source.find("    private fun rollDailyDayIfNeeded()", afk_start)
    if afk_start < 0 or afk_end < 0:
        raise RuntimeError("Android 1.0 runner: AFK consume section not found")
    afk = source[afk_start:afk_end]
    afk = afk.replace(
        "        _state.update {\n            it.copy(",
        "        _state.update { s ->\n            s.copy(",
        1,
    )
    afk = afk.replace("safeAdd(it.treats, amount)", "safeAdd(s.treats, amount)", 1)
    afk = afk.replace(
        "safeAdd(it.lifetimeTreats, amount)",
        "safeAdd(s.lifetimeTreats, amount)",
        1,
    )
    source = source[:afk_start] + afk + source[afk_end:]

    # Puppy Code schema-2 replaces the legacy synchronous redeem function before this patch runs.
    # The core transform still has one legacy ownership-XP anchor. Feed that anchor a comment-only
    # sentinel, then attach XP to the real RewardGrantEngine path in post_fix().
    if "fun redeemCode(rawCode: String, onResult: (V6RedeemOutcome) -> Unit)" in source:
        prestige = source.find("    fun prestige() {")
        if prestige < 0:
            raise RuntimeError("Android 1.0 runner: prestige anchor not found for Puppy Code sentinel")
        sentinel = SCHEMA2_SENTINEL_START + '''            redeemedCodeIds = s.redeemedCodeIds + reward.id
        )
        saveState()
        return V6RedeemOutcome(true, reward.message)
''' + SCHEMA2_SENTINEL_END
        source = source[:prestige] + sentinel + source[prestige:]

    # Exchange's final patch uses asset.style.id for gifts, while the core 1.0 transform has a
    # legacy puppyId anchor. Satisfy that anchor in a comment and patch the real gift method later.
    if "fun receiveExchangePuppy(puppyId: String): Boolean" in source:
        trade = source.find("    fun applyExchangeTrade(")
        if trade < 0:
            raise RuntimeError("Android 1.0 runner: Exchange trade anchor not found for gift sentinel")
        sentinel = GIFT_SENTINEL_START + '''        _state.value = current.copy(unlockedPuppies = current.unlockedPuppies + puppyId)
        saveState()
''' + GIFT_SENTINEL_END
        source = source[:trade] + sentinel + source[trade:]

    vm.write_text(source, encoding="utf-8")


def post_fix(root: Path) -> None:
    vm = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    source = vm.read_text(encoding="utf-8")

    source = remove_sentinel(
        source,
        SCHEMA2_SENTINEL_START,
        SCHEMA2_SENTINEL_END,
        "Puppy Code schema-2",
    )
    source = remove_sentinel(
        source,
        GIFT_SENTINEL_START,
        GIFT_SENTINEL_END,
        "Exchange gift",
    )

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

    # Exchange gifts award ownership XP against the real canonical asset ID.
    gift_old = '''        _state.value = current.copy(unlockedPuppies = current.unlockedPuppies + asset.style.id)
        saveState()
        return true'''
    gift_new = '''        _state.value = current.copy(unlockedPuppies = current.unlockedPuppies + asset.style.id)
        _state.value = awardNewPuppyXp(current, _state.value)
        saveState()
        return true'''
    if gift_old in source:
        source = source.replace(gift_old, gift_new, 1)

    # Existing pre-1.0 ownership is migration-settled without awarding XP. This prevents an old
    # puppy from producing +100 XP merely because a trade was cancelled or the puppy was reacquired.
    settlement_load_old = '''            xpSettlementIds = prefs.getStringSet(PuppyProgressionStore.KEY_XP_SETTLEMENTS, emptySet())?.toSet() ?: emptySet(),'''
    settlement_load_new = '''            xpSettlementIds = if (prefs.contains(PuppyProgressionStore.KEY_XP_SETTLEMENTS)) {
                prefs.getStringSet(PuppyProgressionStore.KEY_XP_SETTLEMENTS, emptySet())?.toSet() ?: emptySet()
            } else {
                unlocked.mapTo(linkedSetOf()) { "puppy:$it" }
            },'''
    if settlement_load_old in source:
        source = source.replace(settlement_load_old, settlement_load_new, 1)

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
