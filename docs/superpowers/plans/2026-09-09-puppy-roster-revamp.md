# Puppy Clicker Roster Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Shop-hosted puppy collection sheet with a full-screen, dynamic Roster destination that supports status/category filtering, permanent search/sort/favorites, a responsive 3-column grid, selected-puppy inspection, locked-puppy unlock help, and confirmed equipping that returns to Play.

**Architecture:** Keep `DynamicPuppyRoster` as the roster authority and expose a reactive asset list with optional presentation metadata. Put filter/sort logic in a pure Kotlin model, favorites in persisted V6 game preferences, and the new Compose roster UI in its own file. Rewire the app shell so Play/Care/Roster/Shop/Rewards are the five bottom destinations while Settings and Prestige become non-bottom-bar destinations.

**Tech Stack:** Kotlin, Android 14-compatible app stack, Jetpack Compose Material 3, StateFlow, SharedPreferences, JUnit 4, Compose UI instrumentation tests, existing streamed PNG roster pipeline.

**Spec:** `docs/superpowers/specs/2026-09-09-puppy-roster-revamp-design.md`

## Global Constraints

- Base implementation on `main` after commit `6695ec594cbc56e552072345f40aadd636894192` or later; re-read changed files before editing if `main` advances.
- Preserve the existing Puppy Clicker Compose/Material 3 visual language; do not introduce a new theme.
- Preserve streamed puppy artwork; do not bundle roster PNGs as a replacement.
- Preserve `DynamicPuppyRoster` as the source of truth for runtime groups and puppies.
- Do not hardcode V1/V2/Test/Patreon/Event/Special as the UI category source of truth.
- Bottom navigation must be `Play / Care / Roster / Shop / Rewards` with Roster in the center.
- Settings must be reachable from the top-right UI, not the bottom bar.
- Prestige must remain a dedicated screen entered from Rewards.
- Normal phone layout targets three puppy cards per row, with responsive fallback to two on narrow screens and more columns on larger screens.
- Puppy name and source ID are always visible on each card.
- Locked puppies remain visible and cannot be equipped.
- Equipping requires an explicit `Use Puppy` action plus portrait/name confirmation; success returns to Play.
- Favorites must persist locally and may include locked puppies.
- Missing optional metadata and failed streamed art must not break roster rendering.
- Do not modify Puppy Code reward/validation behavior or Danger Zone confirmation behavior as part of this feature.
- No backend, VPS, database, or server-side service is introduced.

---

### Task 1: Expose a reactive, metadata-complete roster asset stream

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/DynamicPuppyRoster.kt`
- Modify/Test: `App/app/src/test/java/com/harleytg/puppyclicker/DynamicPuppyRosterTest.kt`

**Interfaces:**
- Produces: `DynamicPuppyRoster.assets: StateFlow<List<PuppyRosterAsset>>`
- Produces: `PuppyRosterAsset.addedOrder: Long?`
- Produces: `PuppyRosterAsset.unlockSource: String?`
- Preserves: `DynamicPuppyRoster.groups`, `style()`, `asset()`, `assetByAssetId()`, `freeIds()`

- [ ] **Step 1: Write failing tests for reactive asset publication and optional metadata parsing**

Add tests equivalent to:

```kotlin
@Test
fun manifestPublishesOptionalAddedOrderAndUnlockSource() {
    val json = """
        {
          "roster": "v3",
          "puppies": [
            {
              "asset_id": "v3_star",
              "file": "v3_star.png",
              "name": "Star",
              "free": false,
              "redeem_only": true,
              "added_order": 42,
              "unlock_source": "Puppy Code"
            }
          ]
        }
    """.trimIndent()

    val parsed = DynamicPuppyRoster.parseManifest("v3", json)
    assertEquals(42L, parsed.single().addedOrder)
    assertEquals("Puppy Code", parsed.single().unlockSource)
}

@Test
fun missingOptionalMetadataRemainsNull() {
    val json = """
        {
          "roster": "v3",
          "puppies": [
            {"asset_id":"v3_plain","file":"v3_plain.png","name":"Plain","free":true}
          ]
        }
    """.trimIndent()

    val parsed = DynamicPuppyRoster.parseManifest("v3", json)
    assertNull(parsed.single().addedOrder)
    assertNull(parsed.single().unlockSource)
}
```

- [ ] **Step 2: Run the targeted tests and verify failure**

Run:

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.DynamicPuppyRosterTest"
```

Expected: compilation/test failure because `addedOrder` and `unlockSource` do not exist yet.

- [ ] **Step 3: Extend `PuppyRosterAsset` and publish an asset `StateFlow`**

Implement the data shape:

```kotlin
data class PuppyRosterAsset(
    val style: PuppyStyle,
    val assetId: String,
    val folder: String,
    val fileName: String,
    val free: Boolean,
    val groupId: String,
    val groupTitle: String,
    val groupOrder: Int,
    val addedOrder: Long? = null,
    val unlockSource: String? = null
)
```

Add a reactive asset list beside `groups`:

```kotlin
private val _assets = MutableStateFlow(assetsByStyleId.values.toList())
val assets: StateFlow<List<PuppyRosterAsset>> = _assets.asStateFlow()
```

Update `publishRemote()` so `_assets.value` and `_groups.value` are derived from the same `next` map in the same publication pass:

```kotlin
private fun publishRemote(remoteDynamic: List<PuppyRosterAsset>) {
    val next = buildAssetMap(remoteDynamic)
    assetsByStyleId = next
    assetsByAssetId = next.values.associateBy { it.assetId }
    _assets.value = next.values.toList()
    _groups.value = groupsFrom(next.values.toList())
}
```

- [ ] **Step 4: Parse/cache optional metadata without making it required**

In `parseManifest()`:

```kotlin
val addedOrder = if (item.has("added_order")) item.getLong("added_order") else null
val unlockSource = item.optString("unlock_source").trim().ifBlank { null }
```

Pass those values into `PuppyRosterAsset`. Add them to cache serialization only when non-null, and read them with nullable fallbacks when parsing cache. Keep the cache parser backward-compatible with cache files that predate these keys.

Legacy V1/V2 entries remain valid with `addedOrder = null`; deterministic source order is handled by Task 2.

- [ ] **Step 5: Run the roster tests**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.DynamicPuppyRosterTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/DynamicPuppyRoster.kt \
        App/app/src/test/java/com/harleytg/puppyclicker/DynamicPuppyRosterTest.kt
git commit -m "feat: expose dynamic roster asset metadata"
```

---

### Task 2: Add the pure roster presentation/filter/sort model

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterModel.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyRosterModelTest.kt`

**Interfaces:**
- Consumes: `List<PuppyRosterAsset>`, unlocked IDs, favorite IDs, active puppy ID
- Produces: `RosterStatusFilter`, `RosterSort`, `RosterQuery`, `RosterCardModel`
- Produces: `buildRosterCards(...) : List<RosterCardModel>`
- Produces: `dynamicRosterCategories(...) : List<RosterCategory>`

- [ ] **Step 1: Write failing model tests covering every filter and sort mode**

Use a compact fixture with unlocked/locked puppies across at least two groups. Include tests equivalent to:

```kotlin
@Test fun allDefaultsToUnlockedFirst() { /* unlocked cards precede locked cards */ }
@Test fun unlockedFilterRemovesLocked() { /* only unlocked */ }
@Test fun lockedFilterRemovesUnlocked() { /* only locked */ }
@Test fun categoryFilterUsesGroupId() { /* only selected group */ }
@Test fun searchMatchesNameCaseInsensitively() { /* "flur" matches Flurry */ }
@Test fun searchMatchesPartialAssetId() { /* "v2_fl" matches v2_flurry */ }
@Test fun favoritesOnlyCanIncludeLockedPuppies() { /* favorite locked card remains */ }
@Test fun nameAscendingSortsByDisplayName() { /* A-Z */ }
@Test fun nameDescendingSortsByDisplayName() { /* Z-A */ }
@Test fun newestUsesAddedOrderThenStableSourceIndex() { /* explicit order first */ }
@Test fun oldestUsesAddedOrderThenStableSourceIndex() { /* inverse */ }
@Test fun categorySortUsesGroupOrderThenName() { /* deterministic */ }
@Test fun categoriesAreGeneratedFromAssets() { /* no hardcoded generation names */ }
```

- [ ] **Step 2: Run the model test and verify failure**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.PuppyRosterModelTest"
```

Expected: compilation failure because the model types/functions do not exist.

- [ ] **Step 3: Implement the model with explicit, stable interfaces**

Use these shapes:

```kotlin
enum class RosterStatusFilter { ALL, UNLOCKED, LOCKED }

enum class RosterSort {
    UNLOCKED_FIRST,
    NAME_ASC,
    NAME_DESC,
    NEWEST_ADDED,
    OLDEST_ADDED,
    CATEGORY
}

data class RosterCategory(
    val id: String,
    val title: String,
    val order: Int
)

data class RosterQuery(
    val status: RosterStatusFilter = RosterStatusFilter.ALL,
    val categoryId: String? = null,
    val search: String = "",
    val favoritesOnly: Boolean = false,
    val sort: RosterSort = RosterSort.UNLOCKED_FIRST
)

data class RosterCardModel(
    val asset: PuppyRosterAsset,
    val unlocked: Boolean,
    val favorite: Boolean,
    val active: Boolean,
    val sourceIndex: Int
)
```

Implement:

```kotlin
fun buildRosterCards(
    assets: List<PuppyRosterAsset>,
    unlockedIds: Set<String>,
    favoriteIds: Set<String>,
    activePuppyId: String,
    query: RosterQuery
): List<RosterCardModel>
```

Filtering order must be status -> category -> search -> favorites. Sorting must be deterministic. For newest/oldest, use `asset.addedOrder` when present and stable `sourceIndex` as fallback; never infer chronology from ID/name.

Implement:

```kotlin
fun dynamicRosterCategories(assets: List<PuppyRosterAsset>): List<RosterCategory>
```

by grouping on runtime `groupId`, taking title/order from the asset metadata, and sorting by `order` then `id`.

- [ ] **Step 4: Run the model tests**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.PuppyRosterModelTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterModel.kt \
        App/app/src/test/java/com/harleytg/puppyclicker/PuppyRosterModelTest.kt
git commit -m "feat: add roster filtering and sorting model"
```

---

### Task 3: Persist puppy favorites in V6 state without changing unlock/equip rules

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt`
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyFavoritesPersistenceTest.kt`

**Interfaces:**
- Produces: `V6GameState.favoritePuppies: Set<String>`
- Produces: `PuppyClickerV6ViewModel.toggleFavoritePuppy(id: String)`
- Preserves: `setPuppyStyle(id: String)` as the only actual equip mutation used by roster confirmation

- [ ] **Step 1: Write the persistence test first**

Use an Android instrumentation test with a fresh app preferences namespace. Verify that toggling a known dynamic roster ID writes the favorite set and that recreating state reads it back. Also verify a locked puppy may be favorited without becoming unlocked or active.

Core assertions:

```kotlin
assertTrue(after.favoritePuppies.contains(targetId))
assertEquals(before.puppyStyle, after.puppyStyle)
assertEquals(before.unlockedPuppies, after.unlockedPuppies)
```

- [ ] **Step 2: Run the targeted instrumentation test and verify failure**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyFavoritesPersistenceTest
```

Expected: compile/test failure because favorite state and toggle API do not exist.

- [ ] **Step 3: Add favorites to V6 state and load/save path**

Add:

```kotlin
val favoritePuppies: Set<String> = emptySet()
```

to `V6GameState`.

Add a dedicated preference key:

```kotlin
private const val KEY_FAVORITE_PUPPIES = "favorite_puppies"
```

Read with a defensive copy:

```kotlin
val favoritePuppies = prefs.getStringSet(KEY_FAVORITE_PUPPIES, emptySet())?.toSet().orEmpty()
```

Ensure normal state persistence keeps the set. Do not tie favorites to unlock state.

- [ ] **Step 4: Implement safe favorite toggling**

Add:

```kotlin
fun toggleFavoritePuppy(id: String) {
    if (!DynamicPuppyRoster.isKnown(id)) return
    val current = _state.value
    val next = current.favoritePuppies.toMutableSet().apply {
        if (!add(id)) remove(id)
    }.toSet()
    _state.value = current.copy(favoritePuppies = next)
    prefs.edit().putStringSet(KEY_FAVORITE_PUPPIES, next).apply()
}
```

The method must not call `setPuppyStyle()`, change `unlockedPuppies`, or grant rewards.

- [ ] **Step 5: Run favorites instrumentation and V6 unit regression tests**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyFavoritesPersistenceTest
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt \
        App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyFavoritesPersistenceTest.kt
git commit -m "feat: persist puppy roster favorites"
```

---

### Task 4: Add roster-safe streamed-art retry without replacing the existing pipeline

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/StreamedPuppyArt.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt` only for the existing `V6PuppyPortrait` wrapper signature if that wrapper remains there
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyArtRetryKeyTest.kt`

**Interfaces:**
- Produces: `streamedPuppyRequestKey(assetId: String, retryToken: Int): String`
- Extends: streamed portrait composable with `retryToken: Int = 0`
- Preserves: existing callers through the default value

- [ ] **Step 1: Write a failing retry-key test**

```kotlin
@Test fun retryTokenChangesRequestKeyWithoutChangingAssetId() {
    assertNotEquals(
        streamedPuppyRequestKey("v2_flurry", 0),
        streamedPuppyRequestKey("v2_flurry", 1)
    )
    assertTrue(streamedPuppyRequestKey("v2_flurry", 1).startsWith("v2_flurry"))
}
```

- [ ] **Step 2: Run and verify failure**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.PuppyArtRetryKeyTest"
```

Expected: compilation failure because the retry key/helper does not exist.

- [ ] **Step 3: Add retry-token support to the existing streamed loader**

Implement a deterministic request key:

```kotlin
internal fun streamedPuppyRequestKey(assetId: String, retryToken: Int): String =
    "$assetId#retry=$retryToken"
```

Add `retryToken: Int = 0` to the streamed portrait path and key the `produceState`/loading effect with both asset identity and retry token. Do not change the canonical URL, cache filename, transparency handling, or fallback rules. A retry must trigger a new load attempt while still allowing the last-good cached image to win when available.

If `V6PuppyPortrait` is a wrapper, pass the parameter through with a default so existing Play/Care/Shop callers do not need changes.

- [ ] **Step 4: Run retry and existing transparency/streaming tests**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.PuppyArtRetryKeyTest"
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/StreamedPuppyArt.kt \
        App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt \
        App/app/src/test/java/com/harleytg/puppyclicker/PuppyArtRetryKeyTest.kt
git commit -m "feat: add streamed puppy art retry support"
```

---

### Task 5: Build the full-screen dynamic Roster Compose UI

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt`
- Create: `App/app/src/main/res/drawable/ic_puppy_favorite.xml`
- Create: `App/app/src/main/res/drawable/ic_puppy_favorite_border.xml`
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyRosterScreenTest.kt`

**Interfaces:**
- Consumes: `DynamicPuppyRoster.assets`, `V6GameState`, `PuppyClickerV6ViewModel`, `buildRosterCards()`
- Produces composable:

```kotlin
@Composable
internal fun PuppyRosterScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onUseConfirmed: (String) -> Unit,
    onOpenSettings: () -> Unit
)
```

- [ ] **Step 1: Write Compose UI tests for the required screen structure**

Cover these semantics/text assertions:

```kotlin
composeRule.onNodeWithText("Puppy Roster").assertExists()
composeRule.onNodeWithText("All").assertExists()
composeRule.onNodeWithText("Unlocked").assertExists()
composeRule.onNodeWithText("Locked").assertExists()
composeRule.onNodeWithText("Search puppies or ID").assertExists()
composeRule.onNodeWithContentDescription("Sort puppies").assertExists()
composeRule.onNodeWithContentDescription("Show favorites only").assertExists()
```

Seed enough cards to assert three cards can coexist in a normal-width test layout and verify every card exposes its puppy name and asset ID.

Add interaction tests for:

- heart tap toggles favorite but does not call `onUseConfirmed`
- locked card shows `Locked` and `How to Unlock` when metadata exists
- unlocked card tap only changes inspected selection
- `Use Puppy` opens a confirmation dialog containing portrait semantics/name
- cancel leaves the current puppy unchanged
- confirm calls `onUseConfirmed(id)` once
- current active puppy shows `Current Puppy` instead of `Use Puppy`
- zero-result filters show an empty state and Reset Filters

- [ ] **Step 2: Run roster UI test and verify failure**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyRosterScreenTest
```

Expected: compile failure because `PuppyRosterScreen` does not exist.

- [ ] **Step 3: Implement persistent filter controls and dynamic categories**

Inside `PuppyRosterScreen`, collect runtime assets:

```kotlin
val assets by DynamicPuppyRoster.assets.collectAsStateWithLifecycle()
```

Hold saveable UI state for:

```kotlin
var status by rememberSaveable { mutableStateOf(RosterStatusFilter.ALL) }
var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
var search by rememberSaveable { mutableStateOf("") }
var favoritesOnly by rememberSaveable { mutableStateOf(false) }
var sort by rememberSaveable { mutableStateOf(RosterSort.UNLOCKED_FIRST) }
var inspectedId by rememberSaveable { mutableStateOf<String?>(state.puppyStyle) }
```

Render in this order:

1. `V6Header("Puppy Roster", ...)` plus top-right Settings action.
2. Fixed status row: All / Unlocked / Locked.
3. Permanently visible `OutlinedTextField` search.
4. Permanently visible sort action/menu.
5. Favorites-only heart action.
6. Horizontally scrollable runtime category chips beginning with `All Categories`.
7. Selected Puppy panel.
8. Responsive lazy grid.

When a selected dynamic category disappears after roster refresh, reset `categoryId` to null rather than leaving a dead filter.

- [ ] **Step 4: Implement the responsive lazy grid and card semantics**

Use `LazyVerticalGrid` with adaptive sizing chosen to produce three columns on normal phone width:

```kotlin
LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 108.dp),
    contentPadding = PaddingValues(bottom = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(cards, key = { it.asset.style.id }) { card ->
        PuppyRosterCard(...)
    }
}
```

Tune the minimum card width during device verification so common phone width yields three columns, narrow width yields two, and tablets naturally expand.

Every card must always render:

- streamed portrait
- display name
- `assetId`
- upper-right outlined/filled heart using the two vector drawables
- lock badge/text if locked
- current-puppy badge if active
- inspected visual state distinct from active state

Use red tint for the filled favorite heart. Also provide `contentDescription` so state is not color-only.

- [ ] **Step 5: Implement the minimal Selected Puppy panel**

The panel displays:

```text
portrait
name
asset ID
runtime category/group
favorite control
Current Puppy OR Use Puppy OR Locked
```

Behavior:

- unlocked + not active -> `Use Puppy`
- active -> `Current Puppy`
- locked -> `Locked`; if `unlockSource != null`, show `How to Unlock`

Do not require rarity, rating, or long description.

- [ ] **Step 6: Implement confirmation and unlock-information dialogs**

Use Material 3 `AlertDialog`.

Equip dialog must include the same streamed puppy portrait plus name and text:

```text
Use Puppy?
[portrait]
<name>
Make <name> your active puppy?
Cancel | Use Puppy
```

Confirm calls `onUseConfirmed(inspectedId)` and does not directly manipulate navigation inside the roster composable.

Unlock dialog must include portrait, name, asset ID, category, and `unlockSource`. If `unlockSource == null`, omit `How to Unlock` entirely.

- [ ] **Step 7: Implement loading/error stability and per-card retry**

Reserve fixed portrait bounds so cards do not resize while images stream. Keep text/controls visible when image loading fails. Maintain a `mutableStateMapOf<String, Int>()` retry counter keyed by style ID and increment only the failed card's token when Retry is tapped.

- [ ] **Step 8: Implement Reset Filters and state consistency**

Reset action sets exactly:

```kotlin
status = RosterStatusFilter.ALL
categoryId = null
search = ""
favoritesOnly = false
sort = RosterSort.UNLOCKED_FIRST
```

Keep inspected puppy if it remains in the source roster; otherwise fall back to the active puppy.

- [ ] **Step 9: Run roster UI tests**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyRosterScreenTest
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt \
        App/app/src/main/res/drawable/ic_puppy_favorite.xml \
        App/app/src/main/res/drawable/ic_puppy_favorite_border.xml \
        App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyRosterScreenTest.kt
git commit -m "feat: add full screen dynamic puppy roster"
```

---

### Task 6: Rewire main navigation, Settings, Rewards, and Prestige

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt`
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRewardsHub.kt`
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyMainNavigationTest.kt`

**Interfaces:**
- Consumes: `PuppyRosterScreen(..., onUseConfirmed, onOpenSettings)`
- Produces bottom destinations: PLAY, CARE, ROSTER, SHOP, REWARDS
- Produces internal destinations: SETTINGS, PRESTIGE
- Produces: `PuppyRewardsHub(state, vm, onOpenPrestige)`

- [ ] **Step 1: Write failing navigation tests**

Assert the bottom bar exposes exactly these labels:

```text
Play
Care
Roster
Shop
Rewards
```

Assert no bottom item labeled `Settings` or `Prestige` exists.

Add flows:

1. open Roster -> tap top-right Settings -> Settings screen exists -> Back returns to Roster
2. open Rewards -> open Prestige -> Prestige screen exists -> Back returns to Rewards
3. start on Play -> open Roster -> inspect unlocked puppy -> Use Puppy -> confirm -> Play becomes selected and active puppy changed
4. open Shop -> verify the old `Pups` bottom-sheet launcher is no longer the primary collection entry

- [ ] **Step 2: Run the navigation test and verify failure**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyMainNavigationTest
```

Expected: FAIL because the existing bottom nav is Play/Care/Shop/Prestige/Settings and Roster/Rewards do not exist.

- [ ] **Step 3: Replace the bottom-tab enum with destination metadata**

Use an enum that separates visibility from screen identity:

```kotlin
private enum class V6Destination(
    val label: String,
    val emoji: String,
    val showInBottomBar: Boolean
) {
    PLAY("Play", "🐾", true),
    CARE("Care", "💖", true),
    ROSTER("Roster", "🐶", true),
    SHOP("Shop", "🛍️", true),
    REWARDS("Rewards", "🎁", true),
    SETTINGS("Settings", "⚙️", false),
    PRESTIGE("Prestige", "⭐", false)
}
```

Render only `showInBottomBar` entries in `NavigationBar` so the center Roster position is guaranteed by enum order.

- [ ] **Step 4: Add lightweight navigation history for Back behavior**

Keep the current destination and previous bottom destination in saveable state. Dialogs/sheets consume Back first through normal Compose behavior. When Settings/Prestige is open, Back returns to its originating main destination. When Roster is reached from another main destination, Back returns to the previous main destination instead of exiting immediately.

Do not introduce Navigation Compose unless the existing app already uses it; this feature can remain within the current enum/state routing architecture.

- [ ] **Step 5: Wire the full-screen Roster and equip-return-to-Play callback**

Route:

```kotlin
V6Destination.ROSTER -> PuppyRosterScreen(
    state = state,
    vm = vm,
    onUseConfirmed = { id ->
        vm.setPuppyStyle(id)
        destination = V6Destination.PLAY
    },
    onOpenSettings = { destination = V6Destination.SETTINGS }
)
```

Because `setPuppyStyle()` already validates unlock/known roster rules, do not duplicate ownership mutation inside the UI.

- [ ] **Step 6: Remove the old Shop-hosted Pups sheet entry**

Delete `pupsOpen` and the `🐶 Pups` Shop button/sheet from `V6Shop`. Leave Puppy Code/redeem and upgrade behavior unchanged.

Do not delete roster functionality still needed by the new screen until all callers have migrated.

- [ ] **Step 7: Create the Rewards hub with a clear Prestige entry**

Create:

```kotlin
@Composable
internal fun PuppyRewardsHub(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onOpenPrestige: () -> Unit
)
```

Keep this intentionally small to stay within roster scope. Reuse existing Material 3 card/header patterns and provide a prominent `⭐ Prestige` entry that opens the existing dedicated Prestige screen. Do not rewrite prestige progression logic.

If an existing reward summary/daily component can be reused without moving ownership of Care features, it may be shown as a summary only; do not duplicate reward-claim mutations in a second implementation.

- [ ] **Step 8: Move Settings entry to top-right UI**

Expose the existing `PuppySettingsScreen` through `V6Destination.SETTINGS`. Add the Settings action to the roster header and, where the app shell supports it cleanly, the shared top-right main-screen action. Avoid changing `PuppySettingsUi.kt` internals or Danger Zone behavior.

- [ ] **Step 9: Run navigation tests and compile all debug sources**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyMainNavigationTest
./gradlew :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt \
        App/app/src/main/java/com/harleytg/puppyclicker/PuppyRewardsHub.kt \
        App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyMainNavigationTest.kt
git commit -m "feat: make roster a primary navigation destination"
```

---

### Task 7: Add accessibility, large-roster, and regression coverage

**Files:**
- Modify: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyRosterScreenTest.kt`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyRosterModelTest.kt`
- Modify only if a regression is found: roster-related implementation files from Tasks 1-6

**Interfaces:**
- Verifies all public behavior from the spec without changing external APIs.

- [ ] **Step 1: Add semantics/accessibility assertions**

Verify content descriptions include meaningful state, for example:

```text
Add Flurry to favorites
Remove Flurry from favorites
Flurry locked
Flurry current puppy
Sort puppies
Open settings
```

Ensure lock/current state has text or semantics and is not communicated only by color.

- [ ] **Step 2: Add a large-roster pure-model test**

Generate at least 500 `PuppyRosterAsset` fixtures and assert filtering/sorting returns deterministic results without duplicates:

```kotlin
@Test fun fiveHundredPuppiesFilterAndSortDeterministically() {
    val result = buildRosterCards(...)
    assertEquals(result.map { it.asset.style.id }.distinct(), result.map { it.asset.style.id })
}
```

This test does not benchmark wall-clock time; it protects against accidental quadratic/duplicate-producing transformations.

- [ ] **Step 3: Add missing-metadata and art-failure UI cases**

Render a puppy with `addedOrder = null` and `unlockSource = null`; assert the card and Selected Puppy panel still render. Simulate failed art state through the loader/test seam; assert name, ID, heart, and lock/current status stay visible and Retry is available.

- [ ] **Step 4: Run the complete unit suite**

```bash
cd App
./gradlew :app:testDebugUnitTest
```

Expected: PASS, including Puppy Code and Danger Zone unit tests.

- [ ] **Step 5: Run the complete connected Compose test suite**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest
```

Expected: PASS.

- [ ] **Step 6: Build debug APK to catch resource/Compose integration errors**

```bash
cd App
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Inspect the final diff for forbidden scope creep**

Run:

```bash
git diff --stat HEAD~6..HEAD
git diff HEAD~6..HEAD -- \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppyCodeCatalog.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppyCodeRewards.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/DangerZoneConfirmation.kt
```

Expected: no intentional changes to Puppy Code validation/reward behavior or Danger Zone confirmation model.

- [ ] **Step 8: Commit final regression adjustments only if needed**

If Steps 4-7 required roster-specific fixes, commit them as:

```bash
git add App/app/src/main App/app/src/test App/app/src/androidTest
git commit -m "test: complete puppy roster regression coverage"
```

If no fixes were required, do not create an empty commit.

---

## Final Verification Checklist

Before declaring the implementation complete, verify all of the following on the current `main`-based worktree:

- [ ] Bottom bar is exactly Play / Care / Roster / Shop / Rewards.
- [ ] Roster occupies the center bottom-nav position.
- [ ] Settings opens from the top-right UI and is absent from the bottom bar.
- [ ] Prestige opens from Rewards and remains a dedicated screen.
- [ ] Shop no longer owns the primary puppy collection sheet.
- [ ] Runtime roster groups generate category controls dynamically.
- [ ] All / Unlocked / Locked filters combine correctly with category, search, favorites, and sort.
- [ ] Search matches display names and IDs.
- [ ] All six sort modes behave deterministically.
- [ ] Unlocked First is the default.
- [ ] Favorites persist and may include locked puppies.
- [ ] Favorite heart is upper-right, outlined when off, filled red when on.
- [ ] Normal phones show approximately three cards across; narrow phones fall back to two; tablets expand.
- [ ] Every puppy card always shows portrait area, name, and source ID.
- [ ] Locked cards display a lock and cannot equip.
- [ ] Selected Puppy panel is directly under filters and stays minimal.
- [ ] `How to Unlock` appears only when unlock metadata exists.
- [ ] `Use Puppy` requires portrait/name confirmation.
- [ ] Cancel preserves the existing active puppy.
- [ ] Confirm changes active puppy and returns to Play.
- [ ] Current active puppy does not show a redundant equip action.
- [ ] Missing optional metadata renders safely.
- [ ] Failed streamed art leaves the roster usable and provides per-card retry.
- [ ] Roster filter/selection state survives normal destination switching where valid.
- [ ] Unit tests, connected UI tests, and `assembleDebug` pass.
- [ ] Puppy Code and Danger Zone behavior remain unchanged.
