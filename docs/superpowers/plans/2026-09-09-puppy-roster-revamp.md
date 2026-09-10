# Puppy Clicker Roster Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Shop-hosted puppy collection sheet with a full-screen, dynamic Roster destination with status/category filtering, permanent search/sort/favorites, a responsive grid, selected-puppy inspection, locked-puppy unlock help, and confirmed equipping that returns to Play.

**Architecture:** Keep `DynamicPuppyRoster` as the single roster authority. Expose a reactive asset stream, put filter/sort behavior in a pure Kotlin presentation model, persist favorites in the existing V6 preferences/state, isolate the new Compose roster in its own file, and rewire the existing enum/state navigation so Play/Care/Roster/Shop/Rewards are bottom destinations while Settings and Prestige remain full screens outside the bottom bar.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, StateFlow, SharedPreferences, JUnit 4, Compose UI instrumentation tests, existing streamed PNG puppy-art pipeline.

**Spec:** `docs/superpowers/specs/2026-09-09-puppy-roster-revamp-design.md`

## Global Constraints

- Start from `main` at commit `6695ec594cbc56e552072345f40aadd636894192` or later; re-read any file changed after that commit before editing it.
- Preserve the current Puppy Clicker Material 3 visual language.
- Preserve streamed puppy art; do not replace it with bundled roster PNGs.
- Preserve `DynamicPuppyRoster` as the runtime source of truth.
- Do not hardcode V1/V2/Test/Patreon/Event/Special as the category source of truth.
- Bottom navigation must be `Play / Care / Roster / Shop / Rewards`, with Roster centered.
- Settings moves to the top-right UI and leaves the bottom bar.
- Prestige remains a dedicated full screen entered from Rewards.
- Normal phone width targets three cards across; narrow widths fall back to two; tablets may show more.
- Every card always shows puppy portrait area, name, and source ID.
- Locked puppies remain visible and cannot be equipped.
- Equipping requires `Use Puppy` plus a portrait/name confirmation modal; confirmation returns to Play.
- Favorites persist locally and may include locked puppies.
- Missing optional metadata and failed streamed art must not break roster rendering.
- Do not change Puppy Code reward/validation behavior or Danger Zone confirmation behavior.
- Do not introduce a backend, VPS dependency, database, or server-side roster service.

---

### Task 1: Expose reactive roster assets and optional roster metadata

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/DynamicPuppyRoster.kt`
- Modify/Test: `App/app/src/test/java/com/harleytg/puppyclicker/DynamicPuppyRosterTest.kt`

**Interfaces:**
- Produces: `DynamicPuppyRoster.assets: StateFlow<List<PuppyRosterAsset>>`
- Produces: `PuppyRosterAsset.addedOrder: Long?`
- Produces: `PuppyRosterAsset.unlockSource: String?`
- Preserves: `groups`, `style()`, `asset()`, `assetByAssetId()`, `isKnown()`, `freeIds()`

- [ ] **Step 1: Add failing tests for the new metadata and asset stream**

Add these concrete cases to `DynamicPuppyRosterTest.kt`:

```kotlin
@Test
fun manifestParsesOptionalAddedOrderAndUnlockSource() {
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

    val parsed = DynamicPuppyRoster.parseManifest("v3", json).single()
    assertEquals(42L, parsed.addedOrder)
    assertEquals("Puppy Code", parsed.unlockSource)
}

@Test
fun missingOptionalMetadataStaysNull() {
    val json = """
        {
          "roster": "v3",
          "puppies": [
            {"asset_id":"v3_plain","file":"v3_plain.png","name":"Plain","free":true}
          ]
        }
    """.trimIndent()

    val parsed = DynamicPuppyRoster.parseManifest("v3", json).single()
    assertNull(parsed.addedOrder)
    assertNull(parsed.unlockSource)
}

@Test
fun assetStreamAndGroupStreamDescribeTheSameStartupRoster() {
    val assetIds = DynamicPuppyRoster.assets.value.map { it.style.id }.toSet()
    val groupedIds = DynamicPuppyRoster.groups.value
        .flatMap { it.puppies }
        .map { it.id }
        .toSet()

    assertTrue(assetIds.isNotEmpty())
    assertEquals(assetIds, groupedIds)
}
```

- [ ] **Step 2: Run the targeted test and verify failure**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.DynamicPuppyRosterTest"
```

Expected: compilation/test failure because `assets`, `addedOrder`, and `unlockSource` do not exist.

- [ ] **Step 3: Extend `PuppyRosterAsset` and publish assets with groups**

Change the model to:

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

Add:

```kotlin
private val _assets = MutableStateFlow(assetsByStyleId.values.toList())
val assets: StateFlow<List<PuppyRosterAsset>> = _assets.asStateFlow()
```

Update `publishRemote()` atomically from the same `next` map:

```kotlin
private fun publishRemote(remoteDynamic: List<PuppyRosterAsset>) {
    val next = buildAssetMap(remoteDynamic)
    val nextAssets = next.values.toList()
    assetsByStyleId = next
    assetsByAssetId = nextAssets.associateBy { it.assetId }
    _assets.value = nextAssets
    _groups.value = groupsFrom(nextAssets)
}
```

- [ ] **Step 4: Parse and cache optional metadata backward-compatibly**

In `parseManifest()`:

```kotlin
val addedOrder = if (item.has("added_order")) item.getLong("added_order") else null
val unlockSource = item.optString("unlock_source").trim().ifBlank { null }
```

Pass them into `PuppyRosterAsset`. In cache serialization, write them only when non-null. In cache parsing, use `has()` checks so old cache files without those keys continue to load.

Legacy V1/V2 assets keep the default null values; Task 2 supplies deterministic fallback ordering.

- [ ] **Step 5: Run tests and commit**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.DynamicPuppyRosterTest"
cd ..
git add App/app/src/main/java/com/harleytg/puppyclicker/DynamicPuppyRoster.kt \
        App/app/src/test/java/com/harleytg/puppyclicker/DynamicPuppyRosterTest.kt
git commit -m "feat: expose dynamic roster asset metadata"
```

Expected: targeted test PASS.

---

### Task 2: Add pure roster filtering, searching, categories, and sorting

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterModel.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyRosterModelTest.kt`

**Interfaces:**
- Produces: `RosterStatusFilter`
- Produces: `RosterSort`
- Produces: `RosterCategory`
- Produces: `RosterQuery`
- Produces: `RosterCardModel`
- Produces: `buildRosterCards(...)`
- Produces: `dynamicRosterCategories(...)`

- [ ] **Step 1: Create failing pure-model tests**

Create fixtures with three puppies across two runtime groups: one active/unlocked, one unlocked/favorite, one locked/favorite. Test these exact behaviors:

```kotlin
@Test fun allDefaultsToUnlockedFirst()
@Test fun unlockedFilterRemovesLocked()
@Test fun lockedFilterRemovesUnlocked()
@Test fun categoryFilterMatchesRuntimeGroupId()
@Test fun searchMatchesDisplayNameCaseInsensitively()
@Test fun searchMatchesPartialAssetIdCaseInsensitively()
@Test fun favoritesOnlyCanReturnLockedPuppies()
@Test fun nameAscendingSortsByDisplayName()
@Test fun nameDescendingSortsByDisplayName()
@Test fun newestUsesAddedOrderThenStableSourceIndex()
@Test fun oldestUsesAddedOrderThenStableSourceIndex()
@Test fun categorySortUsesGroupOrderThenName()
@Test fun categoriesComeFromRuntimeAssetGroups()
@Test fun missingAddedOrderStillSortsDeterministically()
```

Use assertions on returned IDs, not only list size.

- [ ] **Step 2: Run the new test and verify failure**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.PuppyRosterModelTest"
```

Expected: compilation failure because the model does not exist.

- [ ] **Step 3: Implement the presentation model**

Create these types:

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
internal fun buildRosterCards(
    assets: List<PuppyRosterAsset>,
    unlockedIds: Set<String>,
    favoriteIds: Set<String>,
    activePuppyId: String,
    query: RosterQuery
): List<RosterCardModel>
```

Filtering order is status -> category -> search -> favorites. Search checks `style.name` and `assetId` using lowercase comparison. Every comparator ends with stable `sourceIndex` or ID tie-breaking.

Implement:

```kotlin
internal fun dynamicRosterCategories(
    assets: List<PuppyRosterAsset>
): List<RosterCategory>
```

Group by `groupId`; derive title/order from the runtime asset; sort by `order` then `id`. `All Categories` is a UI sentinel and is not inserted into the data list.

- [ ] **Step 4: Run tests and commit**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.PuppyRosterModelTest"
cd ..
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterModel.kt \
        App/app/src/test/java/com/harleytg/puppyclicker/PuppyRosterModelTest.kt
git commit -m "feat: add roster filtering and sorting model"
```

Expected: PASS.

---

### Task 3: Persist favorite puppies in V6 state

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt`
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyFavoritesPersistenceTest.kt`

**Interfaces:**
- Produces: `V6GameState.favoritePuppies: Set<String>`
- Produces: `PuppyClickerV6ViewModel.toggleFavoritePuppy(id: String)`
- Preserves: `setPuppyStyle(id: String)` as the actual equip mutation

- [ ] **Step 1: Write the failing instrumentation test**

Use the target app context and clear `PuppyClickerV6ViewModel.PREFS_NAME` before/after the test. Instantiate the V6 ViewModel, choose a known roster ID, toggle it, and assert:

```kotlin
assertTrue(vm.state.value.favoritePuppies.contains(targetId))
assertEquals(originalStyle, vm.state.value.puppyStyle)
assertEquals(originalUnlocked, vm.state.value.unlockedPuppies)
```

Create a second ViewModel after clearing the first reference and assert the favorite still exists, proving SharedPreferences persistence. Add a case that favorites a locked known puppy and verifies it remains locked.

- [ ] **Step 2: Run and verify failure**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyFavoritesPersistenceTest
```

Expected: compile/test failure because favorite state/API do not exist.

- [ ] **Step 3: Add favorite state and preference handling**

Add to `V6GameState`:

```kotlin
val favoritePuppies: Set<String> = emptySet()
```

Add:

```kotlin
private const val KEY_FAVORITE_PUPPIES = "favorite_puppies"
```

Load with:

```kotlin
val favorites = prefs.getStringSet(KEY_FAVORITE_PUPPIES, emptySet())?.toSet().orEmpty()
```

Include `favoritePuppies = favorites` when constructing loaded V6 state. Do not couple favorites to unlock state.

- [ ] **Step 4: Implement toggling without changing gameplay state**

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

This method must not call `setPuppyStyle()`, mutate `unlockedPuppies`, grant treats/tickets, or touch Puppy Code history.

- [ ] **Step 5: Run regression tests and commit**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyFavoritesPersistenceTest
./gradlew :app:testDebugUnitTest
cd ..
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt \
        App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyFavoritesPersistenceTest.kt
git commit -m "feat: persist puppy roster favorites"
```

Expected: tests PASS.

---

### Task 4: Add per-card retry support to the existing streamed-art path

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/StreamedPuppyArt.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt` for the existing `V6PuppyPortrait` wrapper parameter
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyArtRetryKeyTest.kt`

**Interfaces:**
- Produces: `streamedPuppyRequestKey(assetId: String, retryToken: Int): String`
- Extends the streamed portrait path with `retryToken: Int = 0`
- Existing callers remain source-compatible through the default value

- [ ] **Step 1: Write the failing retry-key test**

```kotlin
@Test
fun retryTokenChangesRequestIdentityWithoutChangingAssetIdentity() {
    val first = streamedPuppyRequestKey("v2_flurry", 0)
    val retry = streamedPuppyRequestKey("v2_flurry", 1)
    assertNotEquals(first, retry)
    assertTrue(retry.startsWith("v2_flurry#retry="))
}
```

- [ ] **Step 2: Run and verify failure**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.PuppyArtRetryKeyTest"
```

Expected: compilation failure because the helper does not exist.

- [ ] **Step 3: Implement retry identity without changing URLs/cache rules**

Add:

```kotlin
internal fun streamedPuppyRequestKey(assetId: String, retryToken: Int): String =
    "$assetId#retry=$retryToken"
```

Add `retryToken: Int = 0` to the streamed puppy composable and to `V6PuppyPortrait`. Key the load effect/state with the request key. Keep canonical GitHub URL generation, cached last-good image behavior, transparency handling, and asset validation unchanged.

- [ ] **Step 4: Run unit regressions and commit**

```bash
cd App
./gradlew :app:testDebugUnitTest --tests "com.harleytg.puppyclicker.PuppyArtRetryKeyTest"
./gradlew :app:testDebugUnitTest
cd ..
git add App/app/src/main/java/com/harleytg/puppyclicker/StreamedPuppyArt.kt \
        App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt \
        App/app/src/test/java/com/harleytg/puppyclicker/PuppyArtRetryKeyTest.kt
git commit -m "feat: add streamed puppy art retry support"
```

Expected: PASS.

---

### Task 5: Build the full-screen dynamic Roster UI

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt`
- Create: `App/app/src/main/res/drawable/ic_puppy_favorite.xml`
- Create: `App/app/src/main/res/drawable/ic_puppy_favorite_border.xml`
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyRosterScreenTest.kt`

**Interfaces:**
- Consumes: `DynamicPuppyRoster.assets`, `V6GameState`, `buildRosterCards()`, `dynamicRosterCategories()`
- Produces:

```kotlin
@Composable
internal fun PuppyRosterScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onUseConfirmed: (String) -> Unit,
    onOpenSettings: () -> Unit
)
```

- [ ] **Step 1: Write failing Compose tests for structure and core interactions**

Test these required nodes:

```kotlin
composeRule.onNodeWithText("Puppy Roster").assertExists()
composeRule.onNodeWithText("All").assertExists()
composeRule.onNodeWithText("Unlocked").assertExists()
composeRule.onNodeWithText("Locked").assertExists()
composeRule.onNodeWithText("Search puppies or ID").assertExists()
composeRule.onNodeWithContentDescription("Sort puppies").assertExists()
composeRule.onNodeWithContentDescription("Show favorites only").assertExists()
composeRule.onNodeWithContentDescription("Open settings").assertExists()
```

Add interaction cases proving:

- card always exposes name and source ID
- heart toggles favorite without equipping
- locked card shows lock state
- locked inspected puppy shows `How to Unlock` only when metadata exists
- unlocked card tap changes inspection only
- current puppy shows `Current Puppy`
- non-current unlocked puppy shows `Use Puppy`
- `Use Puppy` opens a dialog with puppy name and portrait semantics
- Cancel leaves active puppy unchanged
- confirm invokes `onUseConfirmed(id)` once
- empty result shows a reset action

- [ ] **Step 2: Run and verify failure**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyRosterScreenTest
```

Expected: compile failure because `PuppyRosterScreen` does not exist.

- [ ] **Step 3: Implement permanent top filter UI and runtime categories**

Collect:

```kotlin
val assets by DynamicPuppyRoster.assets.collectAsStateWithLifecycle()
```

Keep saveable UI state:

```kotlin
var status by rememberSaveable { mutableStateOf(RosterStatusFilter.ALL) }
var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
var search by rememberSaveable { mutableStateOf("") }
var favoritesOnly by rememberSaveable { mutableStateOf(false) }
var sort by rememberSaveable { mutableStateOf(RosterSort.UNLOCKED_FIRST) }
var inspectedId by rememberSaveable { mutableStateOf<String?>(state.puppyStyle) }
```

Render in order:

1. `Puppy Roster` header with top-right Settings action
2. `All | Unlocked | Locked`
3. permanently visible search field
4. permanently visible sort control
5. favorites-only heart control
6. horizontal `All Categories | <runtime groups>` chips
7. Selected Puppy panel
8. responsive lazy grid

If the selected runtime group disappears after a roster refresh, set `categoryId = null`.

- [ ] **Step 4: Implement responsive grid cards**

Use:

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

Tune `minSize` only if device verification shows normal phone width does not produce three columns. Each card must show portrait area, name, `assetId`, upper-right favorite heart, lock state when locked, and current-puppy state when active. The inspected state must be visually distinct from the active state.

Use `ic_puppy_favorite_border.xml` for off and `ic_puppy_favorite.xml` tinted red for on. Provide content descriptions `Add <name> to favorites` / `Remove <name> from favorites`.

- [ ] **Step 5: Implement the minimal Selected Puppy panel**

Show only:

```text
portrait
name
source ID
runtime category/group
favorite control
Current Puppy OR Use Puppy OR Locked
```

Rules:

- active puppy -> `Current Puppy`, no equip button
- unlocked non-active -> `Use Puppy`
- locked -> `Locked`; show `How to Unlock` only when `unlockSource != null`

Do not require rarity, rating, or description.

- [ ] **Step 6: Implement portrait/name confirmation and unlock-info dialogs**

Equip dialog content:

```text
Use Puppy?
[streamed portrait]
<puppy name>
Make <puppy name> your active puppy?
Cancel | Use Puppy
```

Confirm calls `onUseConfirmed(inspectedId)`; navigation is owned by the app shell, not this composable.

Unlock dialog shows streamed portrait, name, source ID, group title, and `unlockSource`.

- [ ] **Step 7: Add per-card art retry and filter reset**

Keep retry counts in:

```kotlin
val retryTokens = remember { mutableStateMapOf<String, Int>() }
```

Retry increments only the failed puppy's token. Preserve fixed portrait bounds so layout does not jump while art loads.

Reset sets exactly:

```kotlin
status = RosterStatusFilter.ALL
categoryId = null
search = ""
favoritesOnly = false
sort = RosterSort.UNLOCKED_FIRST
```

- [ ] **Step 8: Run UI tests and commit**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyRosterScreenTest
cd ..
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt \
        App/app/src/main/res/drawable/ic_puppy_favorite.xml \
        App/app/src/main/res/drawable/ic_puppy_favorite_border.xml \
        App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyRosterScreenTest.kt
git commit -m "feat: add full screen dynamic puppy roster"
```

Expected: PASS.

---

### Task 6: Rewire main navigation, Settings, Rewards, and Prestige

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt`
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRewardsHub.kt`
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyMainNavigationTest.kt`

**Interfaces:**
- Consumes: `PuppyRosterScreen(...)`
- Produces bottom destinations: PLAY, CARE, ROSTER, SHOP, REWARDS
- Produces internal destinations: SETTINGS, PRESTIGE
- Produces: `PuppyRewardsHub(state, vm, onOpenPrestige)`

- [ ] **Step 1: Write failing navigation tests**

Assert the bottom bar contains exactly:

```text
Play
Care
Roster
Shop
Rewards
```

Assert `Settings` and `Prestige` are not bottom-bar labels.

Test flows:

```text
Roster -> Settings -> Back -> Roster
Rewards -> Prestige -> Back -> Rewards
Play -> Roster -> inspect unlocked puppy -> Use Puppy -> confirm -> Play
```

Also assert Shop no longer presents the old `Pups` collection sheet button as the primary roster entry.

- [ ] **Step 2: Run and verify failure**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyMainNavigationTest
```

Expected: FAIL because the current bottom bar is Play/Care/Shop/Prestige/Settings.

- [ ] **Step 3: Replace the tab enum with destination metadata**

Use:

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

Render only entries with `showInBottomBar` in the `NavigationBar`.

- [ ] **Step 4: Add lightweight Back history within the existing state router**

Track current destination plus the previous bottom destination with `rememberSaveable`. Opening Settings or Prestige stores the originating main destination. Android Back first lets dialogs/sheets close, then returns hidden destinations to their origin; Back from Roster returns to the previous main destination when one exists.

Do not add Navigation Compose solely for this feature.

- [ ] **Step 5: Wire Roster and equip-confirm return-to-Play**

```kotlin
V6Destination.ROSTER -> PuppyRosterScreen(
    state = state,
    vm = vm,
    onUseConfirmed = { id ->
        vm.setPuppyStyle(id)
        destination = V6Destination.PLAY
    },
    onOpenSettings = {
        returnDestination = V6Destination.ROSTER
        destination = V6Destination.SETTINGS
    }
)
```

`setPuppyStyle()` remains the authoritative equip validator.

- [ ] **Step 6: Remove the Shop-hosted Pups sheet launcher**

Delete `pupsOpen`, the `🐶 Pups` Shop button, and its `ModalBottomSheet`. Leave Puppy Code redeem and upgrade UI/logic unchanged.

Remove the old private `V6Pups`/`V6PuppyRosterSection` implementations only after the new screen has replaced every V6 caller.

- [ ] **Step 7: Add the Rewards hub and Prestige entry**

Create:

```kotlin
@Composable
internal fun PuppyRewardsHub(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onOpenPrestige: () -> Unit
)
```

Use the existing Material 3 card/header language. Provide a prominent `⭐ Prestige` entry that opens the existing dedicated Prestige screen. Do not rewrite prestige progression logic or duplicate reward-claim mutations.

- [ ] **Step 8: Route Settings outside the bottom bar**

Reuse the existing `PuppySettingsScreen` through `V6Destination.SETTINGS`. Add `Open settings` top-right semantics to the Roster header and any shared app-shell top-right action introduced for other main screens. Do not alter Danger Zone internals.

- [ ] **Step 9: Run navigation tests/build and commit**

```bash
cd App
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.harleytg.puppyclicker.PuppyMainNavigationTest
./gradlew :app:assembleDebug
cd ..
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt \
        App/app/src/main/java/com/harleytg/puppyclicker/PuppyRewardsHub.kt \
        App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyMainNavigationTest.kt
git commit -m "feat: make roster a primary navigation destination"
```

Expected: tests PASS and `BUILD SUCCESSFUL`.

---

### Task 7: Complete accessibility, large-roster, and regression verification

**Files:**
- Modify: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyRosterScreenTest.kt`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyRosterModelTest.kt`
- Modify roster implementation files only when a failing regression test proves a roster bug

**Interfaces:**
- Verifies behavior from Tasks 1-6 without introducing new public APIs.

- [ ] **Step 1: Add accessibility semantics assertions**

Assert these semantics exist in the relevant states:

```text
Add Flurry to favorites
Remove Flurry from favorites
Flurry locked
Flurry current puppy
Sort puppies
Open settings
```

Lock/current/favorite state must remain understandable without color alone.

- [ ] **Step 2: Add a 500-entry deterministic model test**

Generate 500 unique `PuppyRosterAsset` fixtures across several runtime group IDs, call `buildRosterCards()`, and assert:

```kotlin
assertEquals(500, result.size)
assertEquals(500, result.map { it.asset.style.id }.distinct().size)
assertEquals(result.map { it.asset.style.id }, repeatedRun.map { it.asset.style.id })
```

This guards against duplicate-producing or unstable transformations without using a flaky wall-clock benchmark.

- [ ] **Step 3: Add missing-metadata and failed-art UI cases**

Render a card with `addedOrder = null` and `unlockSource = null`; assert card and Selected Puppy panel still render. Drive the existing streamed-art failure test seam so the image fails; assert name, ID, heart, and lock/current status remain visible and Retry is offered.

- [ ] **Step 4: Run full unit, connected UI, and debug build verification**

```bash
cd App
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
```

Expected: all PASS / `BUILD SUCCESSFUL`, including Puppy Code and Danger Zone regressions.

- [ ] **Step 5: Inspect final scope before completion**

```bash
git diff --stat 6695ec594cbc56e552072345f40aadd636894192..HEAD
git diff 6695ec594cbc56e552072345f40aadd636894192..HEAD -- \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppyCodeCatalog.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppyCodeRewards.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/DangerZoneConfirmation.kt
```

Expected: no intentional Puppy Code validation/reward changes and no Danger Zone confirmation-model changes.

- [ ] **Step 6: Commit only real regression fixes**

If Steps 1-5 required source/test adjustments:

```bash
git add App/app/src/main App/app/src/test App/app/src/androidTest
git commit -m "test: complete puppy roster regression coverage"
```

If no adjustments were required, do not create an empty commit.

---

## Final Verification Checklist

- [ ] Bottom bar is exactly Play / Care / Roster / Shop / Rewards.
- [ ] Roster is the center bottom destination.
- [ ] Settings is top-right and absent from the bottom bar.
- [ ] Prestige is reachable from Rewards and remains a dedicated screen.
- [ ] Shop no longer owns the primary puppy collection sheet.
- [ ] Category controls come from runtime roster groups.
- [ ] All / Unlocked / Locked combine correctly with category, search, favorites, and sort.
- [ ] Search matches display names and source IDs.
- [ ] Sort supports Unlocked First, Name A-Z, Name Z-A, Newest Added, Oldest Added, and Category.
- [ ] Unlocked First is the default.
- [ ] Favorites persist and may include locked puppies.
- [ ] Favorite heart is upper-right, outlined when off, filled red when on.
- [ ] Normal phone width targets three cards; narrow width falls back to two; tablets expand.
- [ ] Every card always shows portrait area, name, and source ID.
- [ ] Locked cards show a lock and cannot equip.
- [ ] Selected Puppy panel appears directly under filters and remains minimal.
- [ ] `How to Unlock` appears only when unlock metadata exists.
- [ ] `Use Puppy` requires portrait/name confirmation.
- [ ] Cancel preserves the active puppy.
- [ ] Confirm equips and returns to Play.
- [ ] Current active puppy has no redundant equip action.
- [ ] Missing optional metadata renders safely.
- [ ] Failed streamed art leaves the card usable and provides per-card Retry.
- [ ] Roster browsing/filter state survives normal destination switching where still valid.
- [ ] Unit tests, connected UI tests, and `assembleDebug` pass.
- [ ] Puppy Code and Danger Zone behavior remain unchanged.
