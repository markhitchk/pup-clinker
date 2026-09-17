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
    """Keep 1.0 profile-badge anchors scoped to ProfileSettings.

    Earlier generated-source layers may introduce another `officialDeveloper`
    declaration outside SettingsHome. The 1.0 patch intentionally augments the
    ProfileSettings declaration, so temporarily mask identical anchors outside
    that function instead of assuming where an earlier duplicate lives.
    """
    anchor = "    val officialDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)\n"
    if source.count(anchor) <= 1:
        return _ORIGINAL_PATCH_SETTINGS(source)

    profile_start = source.find("private fun ProfileSettings(")
    if profile_start < 0:
        raise RuntimeError("Android 1.0 runner: ProfileSettings boundary not found")

    next_composable = source.find("\n@Composable\nprivate fun ", profile_start + len("private fun ProfileSettings("))
    profile_end = len(source) if next_composable < 0 else next_composable

    prefix = source[:profile_start]
    profile = source[profile_start:profile_end]
    suffix = source[profile_end:]
    if anchor not in profile:
        raise RuntimeError("Android 1.0 runner: developer anchor is not in ProfileSettings")

    masked_anchor = (
        "    val officialDeveloper = /* ANDROID_1_0_SETTINGS_COMPAT */ "
        "PuppyPlayerIdentity.isHarleyTgDeveloper(context)\n"
    )
    profile_badge_anchor = '''            if (officialDeveloper) {
                StatusLine("Account", "HarleyTG Developer / Owner")
                StatusLine("Studio", "Harley's Studios")
            }
'''
    masked_profile_badge_anchor = '''            if (/* ANDROID_1_0_SETTINGS_COMPAT */ officialDeveloper) {
                StatusLine("Account", "HarleyTG Developer / Owner")
                StatusLine("Studio", "Harley's Studios")
            }
'''

    prefix = prefix.replace(anchor, masked_anchor)
    suffix = suffix.replace(anchor, masked_anchor)
    prefix = prefix.replace(profile_badge_anchor, masked_profile_badge_anchor)
    suffix = suffix.replace(profile_badge_anchor, masked_profile_badge_anchor)

    normalized = prefix + profile + suffix
    patched = _ORIGINAL_PATCH_SETTINGS(normalized)
    return (
        patched
        .replace(masked_anchor, anchor)
        .replace(masked_profile_badge_anchor, profile_badge_anchor)
    )


# The core patch is intentionally strict. Route Settings through this compatibility shim so the
# profile-release badge transform cannot collide with developer identity state added by older
# generated-source layers.
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
    # The core transform still has one legacy ownership-XP anchor. Feed that exact anchor through
    # a comment-only sentinel, then attach XP to the real RewardGrantEngine path in post_fix().
    if "fun redeemCode(rawCode: String, onResult: (V6RedeemOutcome) -> Unit)" in source:
        prestige = source.find("    fun prestige() {")
        if prestige < 0:
            raise RuntimeError("Android 1.0 runner: prestige insertion point not found")
        sentinel = SCHEMA2_SENTINEL_START + '''    fun redeemCodeCompatibility(): V6RedeemOutcome {
        val s = _state.value
        val reward = LocalRedeemCodes.find("")!!
        _state.value = s.copy(
            redeemedCodeIds = s.redeemedCodeIds + reward.id
        )
        saveState()
        return V6RedeemOutcome(true, reward.message)
    }
''' + SCHEMA2_SENTINEL_END
        source = source[:prestige] + sentinel + source[prestige:]

    # Exchange's current generated source uses asset.style.id for gifts while the core 1.0
    # transform still has the older puppyId anchor. Feed that exact legacy anchor through a
    # comment-only sentinel; post_fix() applies XP to the real canonical asset path afterward.
    current_exchange_gift = "fun receiveExchangePuppy(puppyId: String): Boolean" in source
    reward_engine_gift = (
        "fun claimIncomingGift(" in source
        and "RewardGrantEngine.grantIncomingGift" in source
    )
    if current_exchange_gift or reward_engine_gift:
        if current_exchange_gift:
            insertion = source.find("    fun applyExchangeTrade(")
            if insertion < 0:
                raise RuntimeError("Android 1.0 runner: Exchange trade insertion point not found")
        else:
            insertion = source.find("    fun parkVisit()")
            if insertion < 0:
                raise RuntimeError("Android 1.0 runner: parkVisit insertion point not found")
        gift_sentinel = GIFT_SENTINEL_START + '''        _state.value = current.copy(unlockedPuppies = current.unlockedPuppies + puppyId)
        saveState()
''' + GIFT_SENTINEL_END
        source = source[:insertion] + gift_sentinel + source[insertion:]

    vm.write_text(source, encoding="utf-8")


def post_fix(root: Path) -> None:
    vm = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    source = vm.read_text(encoding="utf-8")

    source = remove_sentinel(source, SCHEMA2_SENTINEL_START, SCHEMA2_SENTINEL_END, "schema-2")
    source = remove_sentinel(source, GIFT_SENTINEL_START, GIFT_SENTINEL_END, "gift")

    # The schema-2 real grant path owns XP and profile-badge persistence after the core patch.
    schema2_old = '''            val next = grant.nextState.copy(
                redeemedCodeIds = grant.nextState.redeemedCodeIds + grant.claimRecord.codeId,
                puppyCodeHistory = history
            )'''
    schema2_new = '''            val rewarded = awardNewPuppyXp(current, grant.nextState)
            val next = rewarded.copy(
                redeemedCodeIds = rewarded.redeemedCodeIds + grant.claimRecord.codeId,
                puppyCodeHistory = history
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

    # The source reset currently names its protected state snapshot `keep`, while the core 1.0
    # transform still targets an older `current` shape. Preserve all non-run 1.0 progression here.
    reset_old = '''            redeemedCodeIds = keep.redeemedCodeIds,
            hapticsEnabled = keep.hapticsEnabled,'''
    reset_new = '''            redeemedCodeIds = keep.redeemedCodeIds,
            bond = keep.bond,
            bondByPuppyId = keep.bondByPuppyId,
            playerXp = keep.playerXp,
            achievementRewardedIds = keep.achievementRewardedIds,
            xpSettlementIds = keep.xpSettlementIds,
            releaseClaimIds = keep.releaseClaimIds,
            profileBadgeIds = keep.profileBadgeIds,
            hapticsEnabled = keep.hapticsEnabled,'''
    if reset_old in source:
        source = source.replace(reset_old, reset_new, 1)

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
