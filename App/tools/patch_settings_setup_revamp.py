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
    val motionEnabled = state.animationsEnabled && com.harleytg.puppyclicker.ui.theme.LocalPuppyAnimatedUi.current''',
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


def patch_pupeye_stream(source: str) -> str:
    return replace_once(
        source,
        '''    suspend fun refreshNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("checked")
            .remove("attempted")
            .apply()
        load(context) != null
    }''',
        '''    suspend fun refreshNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("checked")
            .remove("attempted")
            .apply()
        load(context)
        // A cached bitmap alone does not prove current connectivity. Only a successful network
        // validation writes the checked timestamp after the forced refresh above.
        status(context).lastCheckedAtMs > 0L
    }''',
        "PupEye online verification semantics",
    )


def patch_ui_preference_migration(source: str) -> str:
    return replace_once(
        source,
        '''        val hasConfiguredUsername = PuppyPlayerIdentity.username(context) != "localplayer"
        val existingInstall = looksUpdated || legacyIntroSeen || hasConfiguredUsername''',
        '''        val hasConfiguredUsername = PuppyPlayerIdentity.username(context) != "localplayer"
        val hasExistingGame = context
            .getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
            .all
            .isNotEmpty()
        val existingInstall = looksUpdated || legacyIntroSeen || hasConfiguredUsername || hasExistingGame''',
        "existing-player migration signal",
    )


def patch_save_transfer(source: str) -> str:
    source = replace_once(
        source,
        '''    private const val MAIN_PREFS = PuppyClickerV6ViewModel.PREFS_NAME
    private const val SEASONAL_PREFS = "puppy_seasonal_v1"''',
        '''    private const val MAIN_PREFS = PuppyClickerV6ViewModel.PREFS_NAME
    private const val SEASONAL_PREFS = "puppy_seasonal_v1"
    private const val UI_PREFS = PuppyUiPreferences.PREFS_NAME''',
        "UI preferences backup constant",
    )
    source = replace_once(
        source,
        '''            put(MAIN_PREFS, SecurePreferenceCodec.encode(context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)))
            put(SEASONAL_PREFS, SecurePreferenceCodec.encode(context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE)))''',
        '''            put(MAIN_PREFS, SecurePreferenceCodec.encode(context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)))
            put(SEASONAL_PREFS, SecurePreferenceCodec.encode(context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE)))
            put(UI_PREFS, SecurePreferenceCodec.encode(context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)))''',
        "UI preferences encrypted export",
    )
    source = replace_once(
        source,
        '''        stores.optJSONObject(SEASONAL_PREFS)?.let { seasonalStore ->
            SecurePreferenceCodec.restore(
                context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE),
                seasonalStore
            )
        }
    }''',
        '''        stores.optJSONObject(SEASONAL_PREFS)?.let { seasonalStore ->
            SecurePreferenceCodec.restore(
                context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE),
                seasonalStore
            )
        }
        // Optional for backward compatibility with encrypted v3 saves created before the UI revamp.
        stores.optJSONObject(UI_PREFS)?.let { uiStore ->
            SecurePreferenceCodec.restore(
                context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE),
                uiStore
            )
        }
    }''',
        "UI preferences encrypted import",
    )
    return source


def add_graphics_imports(source: str, needs_android_color: bool, label: str) -> str:
    if needs_android_color and "import android.graphics.Color as AndroidColor\n" not in source:
        source = replace_once(
            source,
            "import android.content.Context\n",
            "import android.content.Context\nimport android.graphics.Color as AndroidColor\n",
            f"{label} AndroidColor import",
        )
    if "import androidx.compose.ui.graphics.luminance\n" not in source:
        source = replace_once(
            source,
            "import androidx.compose.ui.graphics.Color\n",
            "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.luminance\n",
            f"{label} luminance import",
        )
    return source


def patch_settings_motion(source: str) -> str:
    source = replace_once(
        source,
        "import com.harleytg.puppyclicker.ui.theme.LocalPuppyReducedMotion\n",
        "import com.harleytg.puppyclicker.ui.theme.LocalPuppyAnimatedUi\nimport com.harleytg.puppyclicker.ui.theme.LocalPuppyReducedMotion\n",
        "Settings UI animation local import",
    )
    source = replace_once(
        source,
        '''    val reduceMotion = LocalPuppyReducedMotion.current
    val duration = if (reduceMotion) 1 else 190''',
        '''    val animateUi = LocalPuppyAnimatedUi.current && !LocalPuppyReducedMotion.current
    val duration = if (animateUi) 190 else 1''',
        "Settings expansion motion preference",
    )
    return source


def patch_onboarding_motion(source: str) -> str:
    return replace_once(
        source,
        '''    val reducedMotion = LocalPuppyReducedMotion.current''',
        '''    val animateUi = com.harleytg.puppyclicker.ui.theme.LocalPuppyAnimatedUi.current &&
        !LocalPuppyReducedMotion.current''',
        "onboarding motion state",
    ).replace(
        "val duration = if (reducedMotion) 1 else 180",
        "val duration = if (animateUi) 180 else 1",
        1,
    )


def patch_theme_accessibility(source: str) -> str:
    if "import android.animation.ValueAnimator\n" not in source:
        source = replace_once(
            source,
            "import android.graphics.Color as AndroidColor\n",
            "import android.animation.ValueAnimator\nimport android.graphics.Color as AndroidColor\n",
            "theme system animation import",
        )
    source = replace_once(
        source,
        '''    val scaledDensity = Density(
        density = baseDensity.density * densityScale,
        fontScale = baseDensity.fontScale
    )

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalPuppyReducedMotion provides ui.reducedMotion,
        LocalPuppyAnimatedUi provides (ui.animatedUi && !ui.reducedMotion),
        LocalPuppyButtonAnimations provides (ui.buttonAnimations && !ui.reducedMotion),''',
        '''    val scaledDensity = Density(
        density = baseDensity.density * densityScale,
        fontScale = baseDensity.fontScale
    )
    val effectiveReducedMotion = ui.reducedMotion || !ValueAnimator.areAnimatorsEnabled()

    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalPuppyReducedMotion provides effectiveReducedMotion,
        LocalPuppyAnimatedUi provides (ui.animatedUi && !effectiveReducedMotion),
        LocalPuppyButtonAnimations provides (ui.buttonAnimations && !effectiveReducedMotion),''',
        "system reduced-motion behavior",
    )
    return source


def main(root: Path) -> None:
    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    roster = root / PACKAGE / "DynamicPuppyRoster.kt"
    art = root / PACKAGE / "StreamedPuppyArt.kt"
    pupeye = root / PACKAGE / "StreamedPupEyeBranding.kt"
    preferences = root / PACKAGE / "PuppyUiPreferences.kt"
    settings = root / PACKAGE / "PuppySettingsUi.kt"
    onboarding = root / PACKAGE / "PuppyOnboardingUi.kt"
    transfer = root / PACKAGE / "GameSaveTransfer.kt"
    theme = root / PACKAGE / "ui/theme/Theme.kt"

    activity.write_text(patch_activity(activity.read_text(encoding="utf-8")), encoding="utf-8")
    roster.write_text(patch_dynamic_roster(roster.read_text(encoding="utf-8")), encoding="utf-8")
    art.write_text(patch_streamed_art(art.read_text(encoding="utf-8")), encoding="utf-8")
    pupeye.write_text(patch_pupeye_stream(pupeye.read_text(encoding="utf-8")), encoding="utf-8")
    preferences.write_text(
        patch_ui_preference_migration(preferences.read_text(encoding="utf-8")),
        encoding="utf-8",
    )
    transfer.write_text(patch_save_transfer(transfer.read_text(encoding="utf-8")), encoding="utf-8")

    settings_source = add_graphics_imports(settings.read_text(encoding="utf-8"), False, "Settings UI")
    settings.write_text(patch_settings_motion(settings_source), encoding="utf-8")

    onboarding_source = add_graphics_imports(onboarding.read_text(encoding="utf-8"), True, "Onboarding UI")
    onboarding.write_text(patch_onboarding_motion(onboarding_source), encoding="utf-8")

    theme_source = add_graphics_imports(theme.read_text(encoding="utf-8"), False, "theme")
    theme.write_text(patch_theme_accessibility(theme_source), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_settings_setup_revamp.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
