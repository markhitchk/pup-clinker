# Puppy Clicker Android 1.0 — Platform & Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the Android-platform pieces of Puppy Clicker 1.0: a read-only home-screen puppy widget, one-time 1.0 badge/celebration, final generated-source integration, and end-to-end release verification across all three 1.0 plans.

**Architecture:** The widget reads a small derived snapshot rather than the protected gameplay save. Release milestones use stable claim/badge IDs persisted in the authoritative main game store so encrypted save import/export preserves them. One deterministic final generated-source patch owns the last-mile shell wiring after the existing Puppy Gacha patch; business logic remains in normal Kotlin source files.

**Tech Stack:** Kotlin 2.x, Android API 26+/SDK 35, AppWidgetProvider/RemoteViews, Jetpack Compose Material 3, SharedPreferences, BuildConfig, JUnit 4, Android resource/manifest tests, Python 3 generated-source patches, Java 17.

**Spec:** `docs/superpowers/specs/2026-09-16-puppy-clicker-android-1-0-completion-design.md`

## Global Constraints

- Android `App/` only.
- No Offline Mode.
- Widget is read-only in 1.0: no tap-to-earn, feed, claim, Gacha, Casino or save mutation from the widget process.
- Widget performs no unrestricted network fetch. Missing selected-puppy art falls back to packaged app artwork/logo.
- 1.0 reward is only the profile badge `Puppy Clicker 1.0`, ID `release_1_0_badge`; it grants no Treats/tickets/Casino value.
- Stable celebration claim ID is `release_1_0_launch_reward`.
- Do not lower or reset Android `versionCode`; current repository value is 27 at plan creation.
- Do not change package name or signing configuration.
- Do not rename/delete existing Android notification channel IDs.
- This plan runs after the Progression/Content and Hardening/Layout plans so final shell integration can consume their completed APIs.

---

### Task 1: Add a pure widget snapshot model/store codec

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyWidgetSnapshot.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyWidgetSnapshotTest.kt`

**Interfaces:**

```kotlin
data class PuppyWidgetSnapshot(
    val puppyName: String,
    val puppyStyle: String,
    val treats: Long,
    val careScore: Int,
    val bond: Int,
    val updatedAtMs: Long
)

internal object PuppyWidgetSnapshotStore {
    const val PREFS_NAME = "puppy_widget_snapshot_v1"
    fun encode(snapshot: PuppyWidgetSnapshot): String
    fun decode(raw: String?): PuppyWidgetSnapshot?
    fun write(context: Context, snapshot: PuppyWidgetSnapshot)
    fun read(context: Context): PuppyWidgetSnapshot?
}
```

- [ ] Write tests for round-trip, malformed JSON, negative Treat normalization, care/Bond `0..100` clamping, blank puppy name/style rejection, and timestamp preservation.
- [ ] Run targeted test and confirm RED:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyWidgetSnapshotTest --stacktrace
```

- [ ] Implement deterministic JSON codec and SharedPreferences store. This store is derived/cache state only and must not be added as an authoritative save source.
- [ ] Re-run targeted test and commit.

### Task 2: Write widget snapshots only after authoritative game saves

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyWidgetSnapshotMappingTest.kt`

**Mapping helper:**

```kotlin
internal fun V6GameState.toWidgetSnapshot(nowMs: Long): PuppyWidgetSnapshot =
    PuppyWidgetSnapshot(
        puppyName = puppyName,
        puppyStyle = puppyStyle,
        treats = treats.coerceAtLeast(0L),
        careScore = careScore,
        bond = bond,
        updatedAtMs = nowMs
    )
```

- [ ] Write a pure mapping test first.
- [ ] After successful V6 `saveState()` persistence/sealing, write the widget snapshot from the same settled state. Do not allow the widget snapshot write to make a failed gameplay save appear successful.
- [ ] Trigger widget refresh only after snapshot persistence. Widget refresh failure must not fail gameplay saving.
- [ ] Ensure imported/reloaded save state causes the next authoritative save/reload settlement to refresh the snapshot.
- [ ] Run targeted tests and commit.

### Task 3: Implement the read-only AppWidgetProvider and resources

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyHomeWidgetProvider.kt`
- Create: `App/app/src/main/res/layout/puppy_home_widget.xml`
- Create: `App/app/src/main/res/xml/puppy_home_widget_info.xml`
- Modify: `App/app/src/main/AndroidManifest.xml`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyWidgetContractTest.kt`

**Provider behavior:**
- Read `PuppyWidgetSnapshotStore` only.
- Show puppy name, Treat balance, compact `Wellness N% · Bond N%` status.
- Show packaged fallback `R.drawable.source_logo` if a safe local selected-puppy bitmap is not immediately available.
- Root tap opens `PuppyClickerV6Activity` through an immutable/update-current `PendingIntent`.
- `onUpdate`/explicit refresh updates all requested widget IDs.
- No network, no gameplay writes, no services beyond the normal provider update path.

- [ ] Write a contract test that reads manifest/resource files and asserts the receiver, `android.appwidget.action.APPWIDGET_UPDATE`, metadata resource and read-only launch intent integration are present.
- [ ] Add the `AppWidgetProvider` receiver with `android:exported="true"` as required for launcher-host delivery, guarded only by the app-widget intent filter/metadata.
- [ ] Add a compact resizable widget info XML with minimum dimensions and normal home-screen category.
- [ ] Implement `RemoteViews` update and launch behavior.
- [ ] Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyWidgetContractTest --stacktrace
./gradlew --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

- [ ] Commit.

### Task 4: Add idempotent 1.0 milestone entitlement logic

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyReleaseMilestones.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyReleaseMilestonesTest.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySaveCompatibility.kt`

**Stable identifiers:**

```kotlin
const val RELEASE_1_0_BADGE_ID = "release_1_0_badge"
const val RELEASE_1_0_CLAIM_ID = "release_1_0_launch_reward"
```

Persist authoritative string sets in the main save:
- `release_claim_ids_v1`
- `profile_badge_ids_v1`

**Interfaces:**

```kotlin
data class PuppyReleaseEntitlements(
    val claimIds: Set<String>,
    val badgeIds: Set<String>
)

data class PuppyReleaseClaimResult(
    val entitlements: PuppyReleaseEntitlements,
    val newlyClaimed: Boolean
)

internal object PuppyReleaseMilestones {
    fun claimOnePointZero(current: PuppyReleaseEntitlements): PuppyReleaseClaimResult
}
```

- [ ] Write tests proving the first claim adds exactly one claim ID and badge ID, a second claim is a no-op, an imported already-claimed entitlement remains a no-op, unrelated badge/claim IDs are preserved, and no currency values exist in this API.
- [ ] Implement the pure entitlement reducer.
- [ ] Add entitlement fields/accessors to V6 state/load/save as string sets; `GameSaveTransfer` needs no payload-version bump because these keys are in the main store.
- [ ] Add `PuppySaveCompatibility` string-set handling only if legacy imported representations need normalization.
- [ ] Re-run tests and commit.

### Task 5: Add explicit 1.0 celebration build gating and UI

**Files:**
- Modify: `App/app/build.gradle.kts`
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyReleaseCelebrationUi.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyReleaseCelebrationPolicyTest.kt`

Add an explicit build-time field without changing current version naming:

```kotlin
buildConfigField("boolean", "PUPPY_RELEASE_1_0_CELEBRATION", "false")
```

The final stable 1.0 packaging commit may flip this field to `true`; implementation/testing can exercise the pure policy with an injected boolean. Do **not** infer eligibility from the current human-readable `versionName` because the repository already uses `1.7.14`.

**UI behavior:** one-time Compose dialog/surface titled `Puppy Clicker 1.0`, compact What's New summary, badge display, `Continue`, and `What's New`/Release Hub action. It grants no currency.

- [ ] Write policy tests for disabled build, enabled/unclaimed, enabled/already-claimed.
- [ ] Implement the policy and UI separately from entitlement mutation.
- [ ] On first eligible display/claim, call the ViewModel entitlement method once. Activity recreation must redisplay only if claim/display settlement has not completed.
- [ ] Keep the flag `false` in ordinary development builds until the explicit stable-release step.
- [ ] Run targeted tests and commit.

### Task 6: Own final 1.0 generated-source shell integration in one deterministic patch

**Files:**
- Create: `App/tools/patch_1_0_completion.py`
- Create: `App/tools/test_patch_1_0_completion.py`
- Modify: `App/tools/seasonal.gradle.kts`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyMainUiRevampGeneratedIntegrationTest.kt`

**Patch ownership:** this script performs only final shell wiring that cannot live safely in the raw source because earlier generated patches replace navigation/UI blocks. It must not duplicate progression, notification, AFK, widget or milestone business logic.

Required final wiring after `patch_puppy_gacha.py`:
- bell opens the new notification-history surface;
- history action can open Release Hub;
- Settings/About can open Release Hub;
- eligible one-time 1.0 celebration is mounted over the finished shell;
- existing Gacha, Casino, Exchange, Settings, Prestige, Roster and Rewards routes remain intact.

- [ ] Build a minimal fixture in `test_patch_1_0_completion.py` that contains the exact finished-shell anchors expected after Gacha patching.
- [ ] Write RED tests proving the patch inserts required calls, is idempotent when run twice, and throws a clear error when a required single-match anchor is missing.
- [ ] Implement `replace_once`-style deterministic patching; never silently accept multiple ambiguous anchors.
- [ ] Add `completionPatch` to `seasonal.gradle.kts` inputs and execute it **after** `puppyGachaPatch`.
- [ ] Extend generated integration tests to assert all old internal destinations plus the new inbox/release/celebration wiring survive.
- [ ] Run:

```bash
python3 tools/test_patch_1_0_completion.py
./gradlew --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyMainUiRevampGeneratedIntegrationTest --stacktrace
```

- [ ] Commit.

### Task 7: Cross-plan save/import and feature regression verification

**Files:**
- Modify tests only unless a defect is found.
- Relevant tests: `GameSaveTransferFormatTest.kt`, progression store tests, AFK settlement tests, milestone tests, notification history tests, generated integration tests.

- [ ] Create a representative old-state fixture containing legacy global Bond, lifetime Treats, existing selected puppy, daily progress, Casino state and no 1.0 keys.
- [ ] Verify migration produces selected-puppy Bond, XP seed, no duplicate achievement XP, no launch-badge duplication and unchanged Casino state.
- [ ] Verify an exported/imported v3 save carries `bond_by_puppy_v1`, `player_xp_v1`, achievement settlement IDs, AFK settlement metadata and 1.0 badge/claim IDs while device-bound Player ID/Friend Code behavior remains unchanged.
- [ ] Verify widget snapshot is derived after import and is not treated as authoritative backup data.
- [ ] Run full JVM suite.
- [ ] Commit any regression-test additions.

### Task 8: Full Android 1.0 verification gate

**Files:** no production changes unless verification identifies a defect.

- [ ] Run all JVM tests:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
```

- [ ] Compile Android instrumentation tests:

```bash
./gradlew --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

- [ ] Build debug APK to exercise resource/manifest/generated-source integration:

```bash
./gradlew --no-daemon :app:assembleDebug --stacktrace
```

- [ ] If the repository/CI environment has the permanent signing variables, run:

```bash
./gradlew --no-daemon :app:assembleRelease --stacktrace
```

Do not claim a signed release succeeded unless this command actually runs with the permanent signing environment and succeeds.

- [ ] Inspect generated final sources and confirm main tabs remain Play/Care/Roster/Shop/Rewards and internal destinations still include Settings, Prestige, Exchange, Casino and Gacha.
- [ ] Confirm no `Offline Mode` menu/toggle/screen was introduced.
- [ ] Confirm manifest package/application identity and signing configuration are unchanged.
- [ ] Confirm notification channel IDs remain unchanged.
- [ ] Confirm no new currency or Casino game was introduced.
- [ ] Confirm AFK max is 168 hours/7000 Treats and duplicate claims fail safely.
- [ ] Confirm widget has no economy-mutating actions and no network fetch.
- [ ] Confirm the 1.0 badge claim is idempotent and grants no currency.
- [ ] Confirm `versionCode` is still monotonic; do not reduce it as part of the 1.0 naming decision.

### Task 9: Stable 1.0 packaging switch — only when release naming is explicitly chosen

**Files:**
- Modify: `App/app/build.gradle.kts`

This task is the only release-time switch. It is intentionally separate from feature completion because the repository currently reports `versionName = "1.7.14"` and `versionCode = 27`.

- [ ] Choose the public store-facing stable version name separately from this implementation plan; never lower `versionCode`.
- [ ] Increment `versionCode` to a value greater than every previously distributed build.
- [ ] Set `PUPPY_RELEASE_1_0_CELEBRATION` to `true` only in the build intended to trigger the one-time 1.0 celebration.
- [ ] Run the full verification gate again and verify the GitHub Release notes include `Build: <new-code>` or `Version code: <new-code>` so the existing update checker can detect it.
- [ ] Commit the packaging-only change separately from feature code.

## Completion Criteria

The full Puppy Clicker Android 1.0 completion program is ready for release when this plan plus the Progression/Content and Hardening/Layout plans are green, the widget and 1.0 badge are idempotent/read-only as designed, final generated-source wiring preserves all existing destinations, and a release build has been verified under the actual signing environment. Public version naming remains a release-packaging choice; Android versionCode must remain monotonic.