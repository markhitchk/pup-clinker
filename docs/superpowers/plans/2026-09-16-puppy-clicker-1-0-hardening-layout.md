# Puppy Clicker Android 1.0 — Hardening & Adaptive UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the Android app for 1.0 by making AFK settlement bounded/idempotent, exposing simple performance presets, completing an accessibility pass, and adding adaptive tablet/foldable layouts without creating a second UI implementation.

**Architecture:** Extract timing and layout decisions into pure/testable policies, preserve `PuppyUiPreferences` and `PuppyMotionPolicy` as the existing settings/motion foundation, and adapt existing Compose surfaces with shared width classes. Generated source remains the runtime path; durable behavior stays in normal Kotlin sources wherever possible.

**Tech Stack:** Kotlin 2.x, Android API 26+/SDK 35, Jetpack Compose Material 3, SharedPreferences/StateFlow, Android lifecycle callbacks, JUnit 4, Compose UI tests, Java 17.

**Spec:** `docs/superpowers/specs/2026-09-16-puppy-clicker-android-1-0-completion-design.md`

## Global Constraints

- Android `App/` only; no Offline Mode.
- AFK accrual cap is exactly 168 hours / 7 days / 7000 Treats at the current 1000 Treats/day rate.
- Preserve onboarding gating: no AFK accrual before `setup_complete=true`.
- Preserve Reduced Motion as an accessibility override.
- User-facing performance choices are exactly Automatic, Quality and Battery Saver; do not expose a second matrix of low-level toggles as the primary control.
- Tablet support must share business logic with phone layouts.
- Accessibility fixes must preserve the existing visual theme and game behavior.

---

### Task 1: Extract and test the AFK settlement policy

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyAfkPolicy.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyAfkPolicyTest.kt`

**Interfaces:**

```kotlin
data class PuppyAfkSettlement(
    val settlementId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val creditedAwayMs: Long,
    val earnedTreats: Long
)

internal object PuppyAfkPolicy {
    const val DAY_MS = 86_400_000L
    const val MAX_AWAY_MS = 7L * DAY_MS
    const val TREATS_PER_DAY = 1_000L
    fun prepare(backgroundAtMs: Long, nowMs: Long): PuppyAfkSettlement?
}
```

Settlement ID is deterministic from the effective interval, for example `afk:<startedAtMs>:<endedAtMs>`; no randomness is required.

- [ ] Write tests for zero/negative start, `now <= start`, one hour, one day, exactly seven days, eight days clamped to seven, huge forward clock jump clamped to seven, backward clock jump rejected, and exact `7000` maximum reward.
- [ ] Test settlement ID stability for identical intervals and difference for distinct intervals.
- [ ] Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyAfkPolicyTest --stacktrace
```

Expected RED result: policy does not exist.

- [ ] Implement the pure policy using overflow-safe arithmetic and `coerceAtMost(MAX_AWAY_MS)` before reward calculation.
- [ ] Re-run targeted test and verify GREEN.
- [ ] Commit.

### Task 2: Make application/ViewModel AFK settlement idempotent

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerApplication.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySaveCompatibility.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyAfkSettlementTest.kt`

**New keys:**

```kotlin
const val KEY_AFK_PENDING_SETTLEMENT_ID = "afk_pending_settlement_id_v1"
const val KEY_AFK_PENDING_START = "afk_pending_start_v1"
const val KEY_AFK_PENDING_END = "afk_pending_end_v1"
const val KEY_AFK_LAST_SETTLED_ID = "afk_last_settled_id_v1"
```

- [ ] Write tests around a pure settlement reducer proving: first pending interval accepted; repeated foreground preparation for the same interval does not add Treats twice; a claimed settlement cannot settle again; malformed start/end metadata is rejected; a new later interval can settle normally.
- [ ] In `PuppyClickerApplication.prepareAfkReward`, call `PuppyAfkPolicy.prepare`; clear `KEY_AFK_BACKGROUND_AT` exactly once after successful conversion to pending settlement metadata.
- [ ] Before adding pending reward, reject a settlement whose ID equals the pending or last-settled ID.
- [ ] In the ViewModel AFK claim path, atomically transfer pending Treats into gameplay state, persist `KEY_AFK_LAST_SETTLED_ID`, then clear pending reward/id/start/end keys.
- [ ] Keep onboarding gating before policy calculation and keep `AfkWelcomeActivity` behavior.
- [ ] Add new long/string keys to save compatibility normalization as appropriate.
- [ ] Run targeted tests then full JVM suite.
- [ ] Commit.

### Task 3: Add the three user-facing performance presets

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyMotionPolicy.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyMotionPolicyTest.kt`

**Interfaces:**

```kotlin
enum class PuppyPerformancePreset {
    AUTOMATIC,
    QUALITY,
    BATTERY_SAVER
}
```

Persist as `performance_preset_v1` in `PuppyUiPreferences.PREFS_NAME`.

Mapping requirements:
- Automatic: keeps `adaptivePerformance=true` and normal balanced visual intent.
- Quality: disables adaptive degradation and requests normal/full effects, but `reducedMotion=true` still overrides animation behavior.
- Battery Saver: disables/reduces nonessential card/counter/celebration/loading/shimmer effects while retaining essential feedback.

Migration rule: existing settings with adaptive performance enabled -> Automatic; otherwise existing minimal/low-effect configuration -> Battery Saver; remaining existing configurations -> Quality.

- [ ] Extend `PuppyMotionPolicyTest` with mapping and Reduced Motion precedence tests; confirm RED before implementation.
- [ ] Add preset to `PuppyUiState`, read/write migration, setter and reset behavior.
- [ ] Implement one resolver from the public preset to the existing internal motion config; do not delete the existing internal `PuppyMotionPreset`, intensity or mode types used elsewhere.
- [ ] In Settings, present exactly three primary options with short descriptions and current runtime mode. Keep Reduce Motion visible as a separate accessibility option.
- [ ] Ensure resetting interface settings produces Automatic unless the approved retained accessibility state requires Reduced Motion.
- [ ] Run targeted tests and compile Android tests.
- [ ] Commit.

### Task 4: Add shared adaptive width classification

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyAdaptiveLayout.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyAdaptiveLayoutTest.kt`

**Interfaces:**

```kotlin
enum class PuppyWindowClass { COMPACT, MEDIUM, EXPANDED }

internal object PuppyAdaptiveLayout {
    fun classify(widthDp: Float): PuppyWindowClass
}
```

Exact breakpoints: `<600` Compact, `600..839.999` Medium, `>=840` Expanded.

- [ ] Write boundary tests for 0, 599.9, 600, 839.9, 840 and very wide values.
- [ ] Implement the pure classifier; no AndroidX WindowSizeClass dependency is required for this 1.0 design.
- [ ] Add a small Compose helper that reads `BoxWithConstraints.maxWidth` and calls the pure classifier.
- [ ] Run targeted test and commit.

### Task 5: Apply adaptive layout to Roster and Care

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyViewer.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyMainScreensRevamp.kt`
- Modify: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyRosterScreenTest.kt`
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyAdaptiveScreensTest.kt`

- [ ] Add Compose test cases using compact and expanded test widths. Assert Roster remains usable and expanded width exposes a side-by-side viewer/grid composition without duplicating the roster data source.
- [ ] On Compact, preserve current phone stack.
- [ ] On Medium, increase grid capacity/readable widths without forcing two-pane when vertical room is insufficient.
- [ ] On Expanded, render Roster browser/grid and `PuppyViewer` side-by-side with independent scroll behavior where needed.
- [ ] Adapt Care so Compact keeps the stack and Expanded uses puppy/status plus action panel columns. Care actions still call the same ViewModel methods.
- [ ] Keep the existing streamed-art retry and selected-puppy behavior.
- [ ] Run `assembleDebugAndroidTest` and commit.

### Task 6: Apply adaptive constraints to Shop, Rewards, Settings, Casino and Gacha

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyMainScreensRevamp.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyCasinoHub.kt`
- Modify: existing game UI files only where a width stretch regression is present (`PuppyBlackjackUi.kt`, Roulette/Slots/Plinko/Lucky Wheel UI files as applicable)
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyGacha.kt`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyCompactFitmentContractTest.kt`

- [ ] Add contract assertions for a shared max-readable-width/adaptive wrapper rather than hardcoded phone-only full-width assumptions.
- [ ] Shop/Rewards: use centered/constrained content and adaptive columns where cards benefit; no business logic forks.
- [ ] Settings: constrain reading width on Medium and use safe two-column section composition only on Expanded where sections remain logically independent.
- [ ] Casino/Gacha: center game machines/boards with bounded maximum width and preserve aspect/controls instead of stretching across tablet width.
- [ ] Verify compact fitment tests still pass so tablet work does not restore top/bottom padding regressions.
- [ ] Commit.

### Task 7: Accessibility audit — navigation, primary screens and new 1.0 surfaces

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyAccessibility.kt` only if shared semantics/minimum-target helpers reduce repetition
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUpdateUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyViewer.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyMainScreensRevamp.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyGacha.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyCasinoHub.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt`
- Modify: relevant Compose Android tests

**Requirements:** icon-only controls get descriptions; decorative art gets `contentDescription=null`; selected/checked/expanded state is exposed semantically; controls target at least 48dp; critical status is not color-only; dialogs/sheets have logical traversal; new effects respect Reduced Motion; large font scale does not make primary controls unreachable.

- [ ] Start with Compose tests for top-bar notification/settings controls, Roster favorite/select/retry, performance preset selection and notification rows. Confirm missing semantics tests RED where applicable.
- [ ] Fix semantics and touch targets on those surfaces first.
- [ ] Audit Play/Care/Shop/Rewards, Settings, Release Hub/inbox, Gacha, Casino hub/game controls and onboarding for icon-only controls and color-only states.
- [ ] For emoji used as button art, put the meaningful accessible label on the button and avoid duplicate reading from the emoji text where Compose semantics would otherwise repeat it.
- [ ] Test at increased font scale in instrumentation where the runner supports it; at minimum compile and source-contract-test any fallback wrapping/scroll changes.
- [ ] Run Android-test compilation and commit.

### Task 8: Verify hardening and layout integration

**Files:** no new production files unless verification finds a defect.

- [ ] Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
./gradlew --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

- [ ] Verify AFK cannot exceed 7000 Treats for one background interval and the same settlement cannot be claimed twice.
- [ ] Verify onboarding-incomplete state still writes/keeps `afk_background_at_v6=0` and produces no welcome reward.
- [ ] Verify Automatic/Quality/Battery Saver persist across restart and Reduced Motion overrides each without deleting the chosen preset.
- [ ] Verify Compact/Medium/Expanded thresholds are exact and phone layout still uses the compact path.
- [ ] Verify no new CAMERA/MIC permission or Offline Mode entry was introduced.
- [ ] Commit only fixes required by the verification run.

## Completion Criteria

This plan is complete when AFK settlement is capped/idempotent, the three performance presets are persisted and mapped through the existing motion engine, the primary app surfaces pass the accessibility audit, and tablets/foldables get adaptive layouts without changing gameplay logic or breaking compact Android layouts.