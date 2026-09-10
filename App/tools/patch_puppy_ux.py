#!/usr/bin/env python3
"""Apply Puppy Clicker V6 welcome, signup, notification, save-transfer and overlay UX."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one integration anchor, found {count}")
    return source.replace(old, new, 1)


def patch_activity(source: str) -> str:
    source = replace_once(
        source,
        '''    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }
    SeasonalWelcomeGate(vm)''',
        '''    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }
    PuppyAttentionLifecycle()
    var titleVisible by rememberSaveable { mutableStateOf(true) }
    if (titleVisible) {
        PuppyWelcomeTitleScreen(onContinue = { titleVisible = false })
        return
    }
    SeasonalWelcomeGate(vm)''',
        "title and attention lifecycle",
    )

    source = replace_once(
        source,
        '''        V6Header("Settings", "Game preferences, fair play and app information.")''',
        '''        V6Header("Settings", "Game preferences, notifications, saves, fair play and app information.")''',
        "settings subtitle",
    )

    source = replace_once(
        source,
        '''        V6Switch("🔢", "Compact numbers", "Use K/M/B abbreviations.", state.compactNumbers, vm::setCompactNumbers)
        Spacer(Modifier.height(14.dp))
        SeasonalBirthdaySettings(vm)''',
        '''        V6Switch("🔢", "Compact numbers", "Use K/M/B abbreviations.", state.compactNumbers, vm::setCompactNumbers)
        Spacer(Modifier.height(8.dp))
        PuppyAttentionSettings()
        Spacer(Modifier.height(8.dp))
        SaveTransferSettings()
        Spacer(Modifier.height(14.dp))
        SeasonalBirthdaySettings(vm)''',
        "notification and save settings",
    )
    return source


def patch_seasonal_ui(source: str) -> str:
    source = replace_once(
        source,
        '''    if (!settings.introSeen) {
        SeasonalBirthdayDialog(
            initial = settings.birthday,
            welcome = true,
            onSave = { vm.setSeasonalBirthday(it.monthValue, it.dayOfMonth) },
            onClose = vm::dismissSeasonalIntro
        )
        return
    }''',
        '''    if (!settings.introSeen) {
        PuppySignupScreen(onContinueAsGuest = vm::dismissSeasonalIntro)
        return
    }''',
        "signup intro",
    )
    return source


def main(root: Path) -> None:
    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    seasonal = root / PACKAGE / "SeasonalPuppyUI.kt"

    activity.write_text(patch_activity(activity.read_text(encoding="utf-8")), encoding="utf-8")
    seasonal.write_text(patch_seasonal_ui(seasonal.read_text(encoding="utf-8")), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_puppy_ux.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
