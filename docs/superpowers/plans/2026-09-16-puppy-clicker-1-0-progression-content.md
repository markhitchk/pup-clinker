# Puppy Clicker Android 1.0 — Progression & Content Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish Puppy Clicker Android 1.0 progression/content systems: per-puppy Bond, explicit XP/level progression, V6 achievements, the richer 2D Puppy Viewer, persistent notification history, and the Release Hub.

**Architecture:** Durable business logic lives in focused Kotlin helpers under the existing app package. `PuppyClickerV6ViewModel` remains the authoritative gameplay-state owner, `SharedPreferences` remains the local persistence layer, `.pupsave` stays encrypted v3 because it already serializes the whole main game store, and UI changes extend the current Roster/Rewards/Settings/update surfaces. Do not duplicate navigation or put business logic into Python patch scripts.

**Tech Stack:** Kotlin 2.x, Android API 26+/SDK 35, Jetpack Compose Material 3, SharedPreferences, StateFlow, WorkManager, org.json, JUnit 4, Compose Android tests, Java 17.

**Spec:** `docs/superpowers/specs/2026-09-16-puppy-clicker-android-1-0-completion-design.md`

## Global Constraints

- Android `App/` only.
- No Offline Mode.
- Preserve `com.harleytg.puppyclicker`, signing configuration, Treat/ticket economy, PupEye, Casino, Exchange, Gacha, streamed artwork and current generated-source pipeline.
- Existing saves must migrate without destructive reset.
- Keep `GameSaveTransfer.PAYLOAD_VERSION = 3`; the full main preference store already travels inside encrypted v3 backups.
- New puppy IDs, achievement IDs, settlement IDs and notification IDs must be stable.
- All state-changing paths are test-first and idempotent where rewards/progression can otherwise settle twice.

---

### Task 1: Add pure Bond and XP progression policy

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyProgression.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyProgressionTest.kt`

**Interfaces:**

```kotlin
enum class PuppyBondMilestone(val minimum: Int, val label: String) {
    NEW_FRIEND(0, "New Friend"),
    BUDDY(25, "Buddy"),
    CLOSE_PAL(50, "Close Pal"),
    BEST_FRIEND(70, "Best Friend"),
    FOREVER_FRIEND(90, "Forever Friend")
}

enum class PuppyXpEvent(val amount: Long) {
    MANUAL_TAP(1),
    CARE_ACTION(5),
    DAILY_TASK(25),
    ACHIEVEMENT(50),
    NEW_PUPPY(100),
    EVENT_REWARD(50)
}

internal object PuppyProgression {
    const val DEFAULT_BOND = 10
    fun bondFor(values: Map<String, Int>, puppyId: String): Int
    fun withBondDelta(values: Map<String, Int>, puppyId: String, delta: Int): Map<String, Int>
    fun milestoneFor(bond: Int): PuppyBondMilestone
    fun seedXpFromLifetimeTreats(lifetimeTreats: Long): Long
    fun levelForXp(xp: Long): Int
    fun addXp(current: Long, event: PuppyXpEvent): Long
}
```

- [ ] Write tests for Bond defaults, lower/upper clamping, all five milestone boundaries, negative lifetime Treat migration, XP overflow safety and level boundaries including `0`, `99`, `100`, `399`, `400`, `899`, `900`.
- [ ] Write a migration-equivalence test proving `levelForXp(seedXpFromLifetimeTreats(x))` matches the current lifetime-Treat formula for representative non-negative values.
- [ ] Run from `App/`:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyProgressionTest --stacktrace
```

Expected RED result: `PuppyProgression` and related types do not exist.

- [ ] Implement the minimal pure policy. Clamp Bond to `0..100`, clamp XP to non-negative `Long`, and keep the approved square-root level curve.
- [ ] Re-run the targeted test and verify GREEN.
- [ ] Commit:

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyProgression.kt App/app/src/test/java/com/harleytg/puppyclicker/PuppyProgressionTest.kt
git commit -m "feat: add Puppy Clicker progression policy"
```

### Task 2: Persist and migrate per-puppy Bond and player XP

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyProgressionStore.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyProgressionStoreTest.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySaveCompatibility.kt`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/GameSaveTransferFormatTest.kt`

**Persistence keys:**

```kotlin
const val KEY_BOND_BY_PUPPY = "bond_by_puppy_v1"
const val KEY_PLAYER_XP = "player_xp_v1"
const val KEY_ACHIEVEMENT_REWARDED = "achievement_rewarded_v1"
const val KEY_XP_SETTLEMENTS = "xp_settlements_v1"
```

`bond_by_puppy_v1` is a deterministic JSON object of canonical puppy ID -> integer Bond. Achievement and XP settlement IDs use `StringSet`.

- [ ] Write pure codec tests proving Bond JSON round-trips, malformed entries are ignored, duplicate/corrupt values are clamped, and serialization is deterministic by sorted key.
- [ ] Write migration tests proving a save with only legacy `bond_v5=73` and `puppy_style=v2_frost` becomes `{v2_frost:73}`, while an existing Bond map is never overwritten by legacy migration.
- [ ] Write XP migration tests proving absent `player_xp_v1` seeds from non-negative `lifetime_treats`, while an existing XP value wins.
- [ ] Run targeted tests and confirm RED.
- [ ] Implement `PuppyProgressionStore` codec/migration helpers.
- [ ] Change `V6GameState` to store `bondByPuppyId: Map<String, Int>` and `playerXp: Long`; expose compatibility computed properties:

```kotlin
val bond: Int
    get() = PuppyProgression.bondFor(bondByPuppyId, puppyStyle)

val level: Int
    get() = PuppyProgression.levelForXp(playerXp)
```

- [ ] Update V6 load/save so new state persists to the exact keys above. Existing Care code may continue reading `state.bond`, but Bond mutations must update `bondByPuppyId[state.puppyStyle]` rather than a global integer.
- [ ] Add the new numeric/string keys to `PuppySaveCompatibility` only where typed normalization is necessary; do not delete legacy `bond_v5` because it is the migration source.
- [ ] Extend `GameSaveTransferFormatTest` with a contract assertion/comment that encrypted payload format remains v3; do not change `PAYLOAD_VERSION`.
- [ ] Run:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyProgressionStoreTest --tests com.harleytg.puppyclicker.GameSaveTransferFormatTest --stacktrace
```

- [ ] Commit the migration as a standalone commit.

### Task 3: Centralize XP awards and new-puppy progression

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt`
- Modify: `App/tools/patch_puppy_gacha.py`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyGachaEngineTest.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyProgressionSettlementTest.kt`

**ViewModel contracts:**

```kotlin
private fun awardXp(event: PuppyXpEvent, settlementId: String? = null): Boolean
internal fun unlockPuppyWithProgression(puppyId: String): Boolean
```

Rules: successful manual taps award `+1`; completed Care actions `+5`; a newly completed daily task `+25`; a new puppy ownership acquisition `+100`; opted-in event completion `+50`. Stable settlement IDs prevent replay where an event can be invoked twice. New-puppy XP uses the pre-mutation ownership set, so an already-owned puppy never awards another `+100`.

- [ ] Write failing settlement tests for first award, duplicate settlement rejection, already-owned puppy rejection and monotonic XP.
- [ ] Implement the two ViewModel helpers and wire successful tap/Care/daily-task paths through them only after the underlying gameplay action succeeds.
- [ ] Modify `patch_puppy_gacha.py` so a successful first-time Gacha unlock calls the centralized progression unlock path instead of directly making progression assumptions. Preserve Treat/Common Ticket cost and existing Gacha animation behavior.
- [ ] Run Python patch tests plus unit tests:

```bash
python3 tools/test_patch_puppy_gacha.py
./gradlew --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyProgressionSettlementTest --tests com.harleytg.puppyclicker.PuppyGachaEngineTest --stacktrace
```

- [ ] Commit.

### Task 4: Promote legacy achievement IDs into current V6 Rewards

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyAchievementsV6.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyAchievementsV6Test.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyMainScreensRevamp.kt`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyMainUiRevampGeneratedIntegrationTest.kt`

**Stable IDs to reuse:** `first_treat`, `snack_stash`, `puppy_pro`, `combo_hero`, `ticket_hunter`, `big_taps`, `auto_pup`, `happy_home`, `level_ten`.

**Interfaces:**

```kotlin
data class PuppyAchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val target: Long,
    val rewardDescription: String,
    val progress: (V6GameState) -> Long
)

data class PuppyAchievementStatus(
    val definition: PuppyAchievementDefinition,
    val progress: Long,
    val completed: Boolean,
    val xpRewarded: Boolean
)
```

- [ ] Write tests for all nine stable IDs, progress boundaries, V6 cleanliness-inclusive `happy_home`, XP-level-driven `level_ten`, and migration behavior.
- [ ] On first V6 load after migration, evaluate current state and mark already-satisfied achievements as rewarded/completed **without granting migration XP**.
- [ ] On later state transitions, detect newly completed achievements, persist the ID in `achievement_rewarded_v1`, and award exactly `+50 XP` once.
- [ ] Add a dedicated Achievements section under the existing Rewards screen. Show title, description, progress, completed status and `+50 XP` reward description; do not create a separate currency or legacy screen.
- [ ] Add generated integration assertions that Rewards contains the V6 Achievements section and legacy `GameViewModel` is not required by the current V6 screen.
- [ ] Run targeted and generated tests.
- [ ] Commit.

### Task 5: Add the richer 2D Puppy Viewer without replacing Roster

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyViewer.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt`
- Modify: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyRosterScreenTest.kt`

**Viewer contract:** consumes the existing `PuppyRosterAsset`, current `V6GameState`, favorite/unlock/active state and existing callbacks. It displays large streamed 2D art, name, canonical asset ID, group/category, ownership, unlock source when available, Bond value/milestone, available rarity/event/birthday metadata, favorite/current state and retry-artwork control. Actions reuse existing select/favorite/rename/accessory behavior.

- [ ] Add Compose tests for opened viewer, locked/unlocked states, Bond milestone text, favorite toggle semantics and artwork retry action.
- [ ] Implement `PuppyViewer` by extracting/reusing existing preview/panel behavior; do not create a second roster data source.
- [ ] Change roster preview activation to open the richer viewer while keeping compact selected strip/grid behavior.
- [ ] Keep all unknown metadata absent rather than inventing labels.
- [ ] Run:

```bash
./gradlew --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

- [ ] Commit.

### Task 6: Implement 100-item local notification history

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyNotificationHistory.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyNotificationHistoryTest.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyNotificationCenter.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUpdateUi.kt`

**Interfaces:**

```kotlin
enum class PuppyNotificationType { DAILY_REWARD, PARK_READY, APP_UPDATE, ROSTER_UPDATE }
enum class PuppyNotificationRoute { NONE, REWARDS, ROSTER, RELEASE_HUB }

data class PuppyNotificationItem(
    val id: String,
    val type: PuppyNotificationType,
    val title: String,
    val body: String,
    val createdAtMs: Long,
    val read: Boolean,
    val route: PuppyNotificationRoute,
    val externalUrl: String? = null
)
```

Storage: `puppy_notification_history_v1`; retain exactly the newest 100 by `(createdAtMs, id)` and dedupe by stable item ID.

- [ ] Write tests for JSON round-trip, deterministic sort, 100-item pruning, equal-timestamp ID tie-break, duplicate insertion, mark-one-read and mark-all-read.
- [ ] Implement a local StateFlow-backed store with `initialize`, `record`, `markRead`, `markAllRead`, `items`, and `unreadCount`.
- [ ] Record logical events when discovered using stable IDs: `daily:<epochDay>`, `park:<readyAtMs>`, `update:<versionCode>`, and a deterministic roster-change ID. Record before the OS permission gate so the in-app history is useful even when Android notification permission is denied, but do not create history for events that did not actually become eligible.
- [ ] Preserve existing Android channel IDs `puppy_rewards_v1`, `puppy_events_v1`, `puppy_updates_v1` unchanged.
- [ ] Replace the current update-only bell dialog with a scrollable inbox showing unread state and `Mark all read`; opening the inbox alone must not mark everything read.
- [ ] Run targeted tests and compile Android tests.
- [ ] Commit.

### Task 7: Add Release Hub / What's New using existing GitHub Release data

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyReleaseHub.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyReleaseHubTest.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUpdateUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyNotificationCenter.kt`

- [ ] Write a pure presentation-model test covering installed build, no update, update available and notes truncation/empty cases.
- [ ] Implement Release Hub using `BuildConfig.VERSION_NAME`, `BuildConfig.VERSION_CODE` and `PuppyNotificationCenter.refreshUpdateStatus()`; do not add another network endpoint.
- [ ] Add a Settings/About action to open Release Hub and allow update-history rows with `RELEASE_HUB` route to open it.
- [ ] Show installed version/build, cached/latest release data, refresh, notes and existing release/APK action. Add the local Puppy Clicker 1.0 What's New summary from the approved spec.
- [ ] Reuse `openPuppyUpdateUrl`; validate URI presence before showing an external action.
- [ ] Run unit tests and `assembleDebugAndroidTest`.
- [ ] Commit.

### Task 8: Verify progression/content integration

**Files:** no new production files unless a failure exposes a regression.

- [ ] Run all JVM tests:

```bash
./gradlew --no-daemon :app:testDebugUnitTest --stacktrace
```

- [ ] Run Android-test compilation:

```bash
./gradlew --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

- [ ] Verify generated source still contains Gacha, Exchange, Casino, Settings, Roster and Rewards routes.
- [ ] Verify legacy saves retain selected puppy Bond on migration and XP-derived level matches the old visible level.
- [ ] Verify `.pupsave` v3 is unchanged and the new main-store keys are included automatically through `SecurePreferenceCodec.encode(mainPrefs)`.
- [ ] Verify the notification channel constants are unchanged.
- [ ] Commit only test/regression fixes discovered during this verification.

## Completion Criteria

This plan is complete when per-puppy Bond, explicit XP, nine V6 achievements, the richer viewer, the 100-item inbox and Release Hub all work with migrated saves and no existing Gacha/Exchange/Casino/update behavior regresses. The release is not yet considered 1.0-complete until the companion Hardening/Layout and Platform/Release plans are also complete.