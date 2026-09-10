#!/usr/bin/env python3
"""Apply the Settings/onboarding revamp after all existing Puppy Clicker V6 patches.

This intentionally runs last. Earlier patch scripts retain their established source anchors for
seasonal events, PupEye, dynamic rosters, save import and transparency handling.
"""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one integration anchor, found {count}")
    return source.replace(old, new, 1)


def replace_function(source: str, signature: str, next_signature: str, replacement: str, label: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"{label}: start signature not found")
    end = source.find(next_signature, start)
    if end < 0:
        raise RuntimeError(f"{label}: end signature not found")
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


def patch_activity(source: str) -> str:
    source = replace_once(
        source,
        '''    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }
    PuppyAttentionLifecycle()
    var titleVisible by rememberSaveable { mutableStateOf(true) }
    if (titleVisible) {
        PuppyWelcomeTitleScreen(onContinue = { titleVisible = false })
        return
    }
    SeasonalWelcomeGate(vm)''',
        '''    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }
    PuppyAttentionLifecycle()
    val uiPreferences by PuppyUiPreferences.observe(LocalContext.current).collectAsStateWithLifecycle()
    if (!uiPreferences.setupComplete) {
        PuppyOnboardingFlow(vm)
        return
    }
    SeasonalWelcomeGate(vm)''',
        "first-run onboarding gate",
    )

    source = replace_function(
        source,
        "@Composable\nprivate fun V6Settings(state: V6GameState, vm: PuppyClickerV6ViewModel)",
        "@Composable\nprivate fun V6PrestigeCenter",
        '''@Composable
private fun V6Settings(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    PuppySettingsScreen(state, vm)
}''',
        "V6 Settings screen",
    )

    play_start = source.find("@Composable\nprivate fun V6Play")
    play_end = source.find("\n@Composable\nprivate fun V6CareAndDaily", play_start)
    if play_start < 0 or play_end < 0:
        raise RuntimeError("V6 Play motion block not found")
    play = source[play_start:play_end]
    play = replace_once(
        play,
        '''    val tapScale = remember { Animatable(1f) }''',
        '''    val tapScale = remember { Animatable(1f) }
    val motionEnabled = state.animationsEnabled && !com.harleytg.puppyclicker.ui.theme.LocalPuppyReducedMotion.current''',
        "V6 reduced motion state",
    )
    play = play.replace("if (state.animationsEnabled) -3f else 0f", "if (motionEnabled) -3f else 0f")
    play = play.replace("if (state.animationsEnabled) 5f else 0f", "if (motionEnabled) 5f else 0f")
    play = play.replace("if (state.animationsEnabled) {", "if (motionEnabled) {")
    source = source[:play_start] + play + source[play_end:]
    return source


def patch_dynamic_roster(source: str) -> str:
    source = replace_once(
        source,
        "import kotlinx.coroutines.launch\n",
        "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n",
        "dynamic roster coroutine import",
    )
    source = replace_once(
        source,
        '''    fun freeIds(): Set<String> = assetsByStyleId.values
        .asSequence()
        .filter { it.free }
        .map { it.style.id }
        .toCollection(linkedSetOf())

    private fun buildAssetMap''',
        '''    fun freeIds(): Set<String> = assetsByStyleId.values
        .asSequence()
        .filter { it.free }
        .map { it.style.id }
        .toCollection(linkedSetOf())

    fun lastCheckedAt(context: Context): Long =
        prefs(context.applicationContext).getLong("checked", 0L).coerceAtLeast(0L)

    /** Force a user-requested roster verification without blocking the main UI thread. */
    suspend fun refreshNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        prefs(app).edit().remove("checked").remove("attempted").apply()
        refresh(app)
        lastCheckedAt(app) > 0L
    }

    private fun buildAssetMap''',
        "dynamic roster manual refresh API",
    )
    return source


def patch_streamed_art(source: str) -> str:
    return replace_once(
        source,
        '''    private fun lock(assetId: String): Mutex = locks.computeIfAbsent(checkedId(assetId)) { Mutex() }

    suspend fun cached''',
        '''    private fun lock(assetId: String): Mutex = locks.computeIfAbsent(checkedId(assetId)) { Mutex() }

    /** Clear only streamed puppy image caches; roster metadata and game saves are untouched. */
    fun clearCache(context: Context) {
        memory.evictAll()
        File(context.cacheDir, "puppy-stream-v3").deleteRecursively()
        prefs(context).edit().clear().apply()
    }

    suspend fun cached''',
        "streamed puppy cache control",
    )


def main(root: Path) -> None:
    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    roster = root / PACKAGE / "DynamicPuppyRoster.kt"
    art = root / PACKAGE / "StreamedPuppyArt.kt"

    activity.write_text(patch_activity(activity.read_text(encoding="utf-8")), encoding="utf-8")
    roster.write_text(patch_dynamic_roster(roster.read_text(encoding="utf-8")), encoding="utf-8")
    art.write_text(patch_streamed_art(art.read_text(encoding="utf-8")), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_settings_setup_revamp.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
