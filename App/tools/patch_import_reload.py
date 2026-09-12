#!/usr/bin/env python3
"""Fix V6 save imports so the live ViewModel reloads restored preferences immediately."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one integration anchor, found {count}")
    return source.replace(old, new, 1)


def patch_view_model(source: str) -> str:
    if "fun reloadImportedSave()" in source:
        return source

    anchor = '''    fun setCompactNumbers(value: Boolean) { _state.update { it.copy(compactNumbers = value) }; saveState() }
'''
    replacement = anchor + '''
    /** Reload state after an authenticated portable-save import without recreating the Activity. */
    fun reloadImportedSave() {
        recentTapTimes.clear()
        automationDetector.reset()
        suspicionHits = 0
        suspicionWindowStartedMs = 0L
        _state.value = loadState()
        syncDynamicFreePuppies()
        refreshSeasonalEvents()
    }
'''
    return replace_once(
        source,
        anchor,
        replacement,
        "V6 imported-save reload method",
    )


def patch_activity(source: str) -> str:
    callback_call = "        SaveTransferSettings(onImportSuccess = vm::reloadImportedSave)\n"
    legacy_call = "        SaveTransferSettings()\n"

    # The modern Settings implementation owns SaveTransferSettings in PuppySettingsUi.kt,
    # where the import-success callback is already wired. Older generated bases still
    # contain the call in the Activity, so only patch that legacy shape when present.
    if callback_call in source or legacy_call not in source:
        return source

    return replace_once(
        source,
        legacy_call,
        callback_call,
        "V6 save import callback",
    )


def patch_transfer(source: str) -> str:
    # Current source already owns the modal save-transfer UX and callback. Keep this
    # patch backward-compatible for older generated bases without re-patching the
    # modern implementation.
    if "internal fun SaveTransferSettings(onImportSuccess: (() -> Unit)? = null) {" in source:
        return source

    source = replace_once(
        source,
        "internal fun SaveTransferSettings() {",
        "internal fun SaveTransferSettings(onImportSuccess: (() -> Unit)? = null) {",
        "save-transfer callback parameter",
    )
    source = replace_once(
        source,
        "            if (result.success) activity?.recreate()",
        '''            if (result.success) {
                onImportSuccess?.invoke() ?: activity?.recreate()
            }''',
        "save-transfer successful import reload",
    )
    source = replace_once(
        source,
        '''SaveTransferResult(true, "Save imported and authenticated for ${PuppyPlayerIdentity.username(context)}. Reloading Puppy Clicker…")''',
        '''SaveTransferResult(true, "Save imported and authenticated for ${PuppyPlayerIdentity.username(context)}. Progress reloaded.")''',
        "save-transfer success message",
    )
    return source


def main(root: Path) -> None:
    view_model = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    transfer = root / PACKAGE / "GameSaveTransfer.kt"

    view_model.write_text(patch_view_model(view_model.read_text(encoding="utf-8")), encoding="utf-8")
    activity.write_text(patch_activity(activity.read_text(encoding="utf-8")), encoding="utf-8")
    transfer.write_text(patch_transfer(transfer.read_text(encoding="utf-8")), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_import_reload.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
