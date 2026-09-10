# Puppy Clicker Haptics + Motion System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair unreliable Android haptics and implement the approved system-wide Puppy Clicker motion system with all controls exposed in Settings.

**Architecture:** Haptics are centralized behind a semantic event API that prefers Android UI haptic feedback and falls back to direct `Vibrator` effects for unsupported or strong events. Motion is centralized behind persisted `PuppyUiPreferences`, theme-provided composition locals, semantic motion tokens/presets, and contextual screen/component wrappers; Reduce Motion is the global override and Adaptive Performance is user-configurable.

**Tech Stack:** Kotlin, Android 14-compatible APIs, Jetpack Compose Material 3, Android `HapticFeedbackConstants`, `VibratorManager`/`Vibrator`/`VibrationEffect`, SharedPreferences/StateFlow, JUnit, Gradle 8.9 / Java 17.

**Spec:** `docs/superpowers/specs/2026-09-10-puppy-motion-system-design.md`

## Global Constraints

- Motion style: Hybrid / Medium intensity by default.
- Puppy artwork remains static; animate containers/overlays only.
- Reduce Motion applies to every motion category and overrides other motion settings without deleting their persisted values.
- Adaptive Performance is visible and configurable in Settings.
- Haptics are controlled by the existing Haptic Feedback preference.
- Normal UI haptics prefer semantic Android feedback; stronger/special events may use direct vibration.
- Haptic failures must never crash gameplay and must no longer fail silently.
- Existing `android.permission.VIBRATE` remains the only vibration permission requirement.
- All new behavior follows test-first red/green verification.

---

### Task 1: Central haptic policy and Android engine

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyHaptics.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyHapticsPolicyTest.kt`
- Modify: `App/tools/patch_settings_setup_revamp_runner.py`

**Interfaces:**
- Produces: `enum class PuppyHapticEvent`, `data class PuppyHapticPlan`, `object PuppyHapticPolicy`, `object PuppyHaptics`.
- `PuppyHaptics.perform(view, context, event, enabled)` returns a result describing semantic/direct/unavailable/disabled/error execution.

- [ ] Write tests proving light UI events prefer semantic feedback with direct fallback, strong events route directly, disabled state emits nothing, and fallback strength is deterministic.
- [ ] Run `gradle --no-daemon :app:testDebugUnitTest --stacktrace` and verify the new test fails because the haptic policy does not exist.
- [ ] Implement the minimal policy and Android engine.
- [ ] Update the generated-source compatibility patch so existing `performV6Haptic` callers delegate to the new engine and are visible to Settings.
- [ ] Re-run unit tests and compile Android instrumentation tests.

### Task 2: Motion preferences and presets

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt`
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/PuppyMotion.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyMotionPolicyTest.kt`

**Interfaces:**
- Produces: `PuppyMotionPreset { MINIMAL, BALANCED, PLAYFUL, CUSTOM }`, `PuppyMotionIntensity { LOW, MEDIUM, HIGH }`, persisted per-category toggles, `PuppyMotionMode { FULL, PERFORMANCE, REDUCED, STATIC }`, and semantic motion tokens.

- [ ] Write failing tests for preset resolution, Custom transition after manual overrides, Reduced Motion precedence, Animated UI precedence, and Adaptive Performance resolution.
- [ ] Add persisted fields and setters while preserving existing UI/profile settings migration behavior.
- [ ] Add the central motion policy/tokens and composition local.
- [ ] Re-run unit tests.

### Task 3: Settings → Motion & Animations UI

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt`

**Interfaces:**
- Consumes the Task 2 preferences and motion state.
- Exposes preset, intensity, Animated UI, Button Animations, Screen Transitions, Card & Selection Animations, Counter Animations, Celebrations, Error/Warning Animations, Loading Animations, Skeleton/Shimmer, Adaptive Performance, Reduce Motion, live preview, and reset controls.

- [ ] Add UI contract tests/compile assertions before implementation.
- [ ] Move existing motion-related controls from Appearance into the dedicated collapsible section.
- [ ] Keep controls visible under Reduce Motion and mark them as limited rather than resetting them.
- [ ] Add live preview and runtime-mode status.

### Task 4: Contextual screen/component motion

**Files:**
- Modify generated V6 integration through the established `App/tools` patch pipeline.
- Modify focused roster/rewards/loading components where they already exist as separate source files.

**Interfaces:**
- Bottom navigation uses destination-specific transitions.
- Cards, counters, rewards, errors, loading states, tabs, filters, collapsibles, and Danger Zone request semantic motion rather than arbitrary tween values.

- [ ] Add failing behavior/contract tests for reduced variants and semantic token selection.
- [ ] Implement interruptible bottom-navigation transitions.
- [ ] Implement contextual roster, reward, counter, loading, error/warning, and control motion.
- [ ] Ensure puppy art is never directly transformed.

### Task 5: Haptic coverage across interactions

**Files:**
- Modify generated V6 integration through the established patch pipeline.
- Modify focused Settings/Danger Zone/roster/rewards components as needed.

**Interfaces:**
- Normal tap/navigation/selection/toggle events use semantic haptics.
- Reward/success/error/Danger Zone confirmation use stronger/special haptic plans.

- [ ] Wire existing puppy tap, ticket drop, Settings test, and Danger Zone confirmation first.
- [ ] Add contextual haptics to navigation, roster selection/favorites, rewards, code results, errors, and destructive confirmation where corresponding UI exists.
- [ ] Ensure all paths obey `hapticsEnabled` and expose diagnostic status from Test Haptic.

### Task 6: Verification and integration

**Files:** no new production files unless verification exposes a regression.

- [ ] Run `gradle --no-daemon :app:testDebugUnitTest --stacktrace`.
- [ ] Run `gradle --no-daemon :app:assembleDebugAndroidTest --stacktrace`.
- [ ] Run `gradle --no-daemon :app:assembleRelease --stacktrace` in CI signing mode available to the repository.
- [ ] Verify haptic Settings UI, Reduce Motion precedence, static puppy art, and motion preferences via source/contract checks.
- [ ] Review CI results before merging to `main`.
