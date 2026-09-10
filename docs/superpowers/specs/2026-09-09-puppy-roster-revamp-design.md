# Puppy Clicker Roster Revamp Design

Date: 2026-09-09
Status: Approved design, pending implementation plan
Repository: `markhitchk/pup-clinker`

## 1. Goal

Replace the current Shop-hosted puppy collection bottom sheet with a first-class, full-screen Roster destination while preserving the existing Puppy Clicker Compose/Material 3 visual language and the existing streamed, dynamic puppy roster architecture.

The roster must remain data-driven. Adding supported roster groups or puppies through the existing dynamic roster source must not require hardcoding new generation/category tabs into the app UI.

## 2. Existing UI constraints

The redesign must reuse the current app's UI system rather than introduce a new visual theme. Reuse existing Material 3 surfaces, cards, typography, theme colors, rounded shapes, headers, wallet styling, filter chips, and `V6PuppyPortrait` streamed-art rendering.

Do not replace the streamed puppy-art pipeline or duplicate roster data in a second hardcoded list.

## 3. Main navigation

The main bottom navigation becomes:

`🐾 Play · 💖 Care · 🐶 Roster · 🛍️ Shop · 🎁 Rewards`

Rules:

- `🐶 Roster` is the center destination and receives modest visual emphasis compared with the other bottom-nav items.
- `⚙️ Settings` moves out of the bottom navigation and becomes a top-right action in the main screen/header pattern.
- The current `⭐ Prestige` bottom-navigation destination is removed from the bottom bar.
- Prestige remains a dedicated full-screen progression screen, but is entered from `🎁 Rewards`.
- Confirming a new active puppy from the roster returns the user directly to `🐾 Play`.

## 4. Roster screen structure

The Roster is a full-screen destination, not a modal sheet.

Screen order:

1. Header: `Puppy Roster` with `⚙️ Settings` at upper-right.
2. Persistent status tabs: `All | Unlocked | Locked`.
3. Permanent search, sort, and favorites controls.
4. Dynamic category row.
5. Selected Puppy panel.
6. Responsive lazy puppy grid.

The search/filter control area should remain reachable while scrolling. The Selected Puppy panel may scroll normally with content so it does not permanently consume excessive vertical space.

## 5. Dynamic roster source of truth

`DynamicPuppyRoster` remains the roster source of truth for dynamically discovered puppies/groups and their metadata.

The UI must not hardcode categories such as V1, V2, Test, Patreon, Event, or future groups. Instead:

- `All Categories` is always present.
- Other category chips/tabs are generated from the roster groups actually available at runtime.
- Empty categories are not shown.
- Newly supported groups appear automatically once exposed by the existing dynamic roster layer.
- Category order should follow explicit roster metadata/order when available; otherwise use stable source order.
- Missing optional metadata must not break rendering.

Existing static/legacy roster compatibility must be preserved where required by current game state, but the presentation layer should consume a unified roster view instead of duplicating V1/V2 UI logic.

## 6. Status tabs

Top-level status tabs are fixed:

- `All`
- `Unlocked`
- `Locked`

Behavior:

- `All`: show every matching puppy with unlocked puppies first by default.
- `Unlocked`: show only owned/unlocked puppies.
- `Locked`: show only not-yet-unlocked puppies.
- Status filters combine with category, search, favorites, and sort controls.

## 7. Search

Search is permanently visible.

Requirements:

- Search by puppy display name.
- Search by exact/partial puppy asset ID.
- Search is case-insensitive.
- Search applies within the currently selected status/category/favorites filters.
- Search state should survive normal navigation away from and back to the roster when practical through saveable UI state.

## 8. Sort

The sort control is permanently visible.

Supported options:

- Unlocked First — default
- Name A–Z
- Name Z–A
- Newest Added
- Oldest Added
- Category

For newest/oldest sorting:

- Prefer an explicit added timestamp/order field if provided by roster metadata.
- Otherwise fall back to deterministic manifest/source order.
- Never derive chronology from display names or asset IDs.

## 9. Favorites

Favorites are a filter/action, not a fourth status tab.

Global favorites control:

- Show a heart button beside search/sort/filter controls.
- When enabled, show only puppies marked as favorites within the other active filters.

Per-card favorite control:

- Place the heart in the upper-right corner of every puppy card.
- Unfavorited: outlined heart.
- Favorited: filled red heart.
- Tapping the heart toggles favorite state only and must not select/equip the puppy.
- Locked puppies may also be favorited.
- Favorites persist locally across app restarts.

Implementation should use an Android-native icon approach that visually matches the requested Font Awesome heart behavior rather than introducing a web-only Font Awesome dependency into Compose.

## 10. Dynamic category controls

Under the status/search/filter controls, show a horizontally scrollable dynamic category row:

`All Categories | <runtime roster groups...>`

Examples such as V1, V2, Test, Patreon, Event, and Special are illustrative only and must not be hardcoded as the source of truth.

The selected category combines with `All / Unlocked / Locked`, search, favorites, and sort.

## 11. Selected Puppy panel

Place a Selected Puppy panel directly under the filter UI.

Keep the panel intentionally minimal. Show:

- Larger streamed puppy portrait.
- Puppy display name.
- Puppy ID.
- Dynamic category/group.
- Favorite toggle.
- Active/current-puppy state.
- Primary action appropriate to lock/active state.

Do not require rarity, rating, long description, or other optional metadata in this panel.

### Unlocked inspected puppy

If the puppy is unlocked and is not currently active:

- Show `Use Puppy`.
- Tapping `Use Puppy` opens a confirmation dialog.

### Current active puppy

If the inspected puppy is already active:

- Replace `Use Puppy` with a `Current Puppy` state.
- Do not open a redundant confirmation dialog.

### Locked inspected puppy

If locked:

- Show `🔒 Locked`.
- Do not expose `Use Puppy`.
- Show `How to Unlock` when unlock metadata is available.

## 12. Use Puppy confirmation dialog

Selecting an unlocked puppy in the grid is inspection only; it must not immediately change the active gameplay puppy.

Flow:

1. Tap unlocked grid card.
2. Selected Puppy panel updates.
3. Tap `Use Puppy`.
4. Show Material 3 confirmation dialog.
5. Dialog includes the streamed puppy portrait and puppy name.
6. Confirm equips the puppy.
7. Cancel/Deny closes the dialog without changing the active puppy.
8. Successful confirmation navigates directly back to `🐾 Play` with the selected puppy active.

Suggested copy structure:

- Title: `Use Puppy?`
- Portrait
- Puppy name
- Body: `Make <name> your active puppy?`
- Actions: `Cancel` and `Use Puppy`

## 13. Locked puppy interaction

Locked cards stay visible.

Every locked card must show a clear `🔒` lock indicator directly on the card while continuing to show identity information.

Tap behavior:

- Tapping a locked puppy updates the Selected Puppy panel.
- It does not unlock or equip the puppy.
- The Selected Puppy panel shows `Locked` and, when supported, a `How to Unlock` action.

### How to Unlock dialog

When unlock metadata exists, `How to Unlock` opens a Material 3 dialog/sheet showing:

- Puppy portrait.
- Puppy name.
- Puppy ID.
- Category/group.
- Available unlock method/source.

Possible unlock sources include Puppy Code, Event, Patreon, Reward, Free, or future values supplied by roster metadata. The UI must not assume every puppy has an unlock source.

## 14. Puppy grid

Use a lazy responsive grid.

Default phone behavior:

- 3 cards across under normal phone widths.
- Fall back to 2 across when the available width would make three cards unreadable or untappable.
- Larger devices/tablets may expand to 4 or more columns based on measured available width and minimum card size.

Each card always shows:

- Streamed puppy portrait.
- Puppy name.
- Puppy ID.
- Dynamic category/group indicator where space permits.
- Upper-right favorite heart.
- Lock indicator/status when locked.
- Active-puppy indicator when applicable.
- Inspected/selected visual state distinct from active-puppy state.

The puppy name and ID are always visible. The ID must come from roster metadata/source data and must not be regenerated from the name.

## 15. Card selection states

Differentiate these states:

- Normal.
- Inspected/selected in the roster.
- Active/current gameplay puppy.
- Locked.
- Favorited.

These states may overlap, so visuals must remain understandable without relying on color alone.

Use the existing Material 3 selected/container styling patterns where possible.

## 16. Loading and streamed-art errors

The roster must remain usable while artwork streams.

Requirements:

- Reserve stable portrait space so the grid does not jump as images load.
- Show a lightweight loading placeholder consistent with the current UI.
- If an image fails, keep name/ID/card controls visible.
- Offer retry for failed streamed art without forcing a full roster reload.
- A single failed asset must not block the rest of the roster.

## 17. Empty states and reset

If filters return zero puppies, show a clear empty state such as:

`No locked puppies in this category.`

Provide a compact reset action that restores:

- Status: `All`
- Category: `All Categories`
- Search: empty
- Favorites-only: off
- Sort: `Unlocked First`

## 18. State restoration

Preserve practical roster browsing state across navigation:

- Status tab.
- Search text.
- Category.
- Favorites-only toggle.
- Sort option.
- Inspected puppy where still valid.
- Grid scroll position where practical.

After a successful `Use Puppy` confirmation, the app intentionally navigates to Play. Returning to Roster should restore the prior filters/browsing state unless the underlying roster data changed enough to invalidate it.

## 19. Back behavior

Android Back from the Roster should follow the app's main-navigation behavior and return to the previous main destination when a previous destination exists, rather than unexpectedly exiting the activity.

Dialogs/sheets close first before leaving the roster.

## 20. Rewards and Prestige

`🎁 Rewards` becomes the progression/reward hub.

Prestige placement:

- Prestige is accessed from Rewards.
- Prestige remains its own dedicated full-screen screen.
- Existing Prestige game logic and progression are not merged into the roster.

The exact visual reorganization of the broader Rewards screen is outside this roster implementation except for providing a clear Prestige entry point required by the new bottom-navigation layout.

## 21. Settings placement

`⚙️ Settings` moves to a top-right app/header action so the bottom bar can remain focused on the five primary gameplay destinations.

The existing Settings screen and recently redesigned Settings/onboarding behavior should remain intact. Only its navigation entry point changes as required.

## 22. Performance

The roster is expected to continue growing, so implementation must avoid eager rendering of the entire collection.

Use lazy grid rendering and stable keys based on puppy IDs. Filtering/sorting should operate on roster metadata, while image loading remains delegated to the existing streamed-art path.

Do not trigger redundant full-roster image downloads or recreate image state unnecessarily during simple filter changes.

## 23. Accessibility

- Heart controls need content descriptions such as `Add <name> to favorites` / `Remove <name> from favorites`.
- Locked state must have a semantic/text representation, not just a visual lock/color.
- Current-puppy state must be distinguishable without color alone.
- Tap targets should meet normal Android touch-size expectations.
- Search, sort, tabs, and category controls should remain keyboard/screen-reader navigable through standard Compose semantics.

## 24. Data/model implications

The implementation may require a thin unified presentation model around `DynamicPuppyRoster` and existing legacy/static puppy definitions so the UI can consume one list of roster entries.

That model should expose, at minimum:

- ID.
- Display name.
- Group/category.
- Unlock state.
- Active state.
- Favorite state.
- Sort/source order.
- Optional added-order/date metadata.
- Optional unlock source.
- Optional asset metadata required by streamed portrait rendering.

Optional fields must remain optional.

Favorites should be stored locally with the rest of user-owned app preferences/save state in a backward-compatible way.

## 25. Files/components expected to change

Primary implementation is expected around:

- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/DynamicPuppyRoster.kt`
- Existing game state/ViewModel/save code only where needed for favorites, unified roster state, and navigation.
- Existing streamed-art components only if required for retry/loading behavior; do not replace the pipeline.
- Tests covering the new roster behavior.

The implementation plan should prefer extracting focused roster composables/model helpers rather than making `PuppyClickerV6Activity.kt` substantially more monolithic.

## 26. Testing requirements

At minimum, cover:

- Dynamic group discovery becomes visible without hardcoded category additions.
- `All`, `Unlocked`, and `Locked` filtering.
- Dynamic category filtering.
- Search by name and ID.
- Every sort mode.
- Unlocked-first default behavior.
- Favorite persistence and favorites-only filtering.
- Favorite tap does not equip/select unintentionally.
- Locked cards cannot equip.
- Locked card → inspect → How to Unlock flow.
- Unlocked card → inspect → Use Puppy → cancel preserves active puppy.
- Unlocked card → inspect → Use Puppy → confirm changes active puppy and navigates to Play.
- Current Puppy state does not present a redundant equip action.
- Missing optional metadata renders safely.
- Failed streamed art does not break card/grid rendering.
- Responsive grid remains usable at narrow phone widths.
- Main navigation contains Play, Care, Roster, Shop, Rewards; Settings is top-right; Prestige remains reachable through Rewards.

## 27. Non-goals

This project does not:

- Redesign Puppy Clicker's overall visual theme.
- Replace streamed puppy assets with bundled assets.
- Hardcode all current/future roster groups.
- Rebuild Prestige progression logic.
- Change puppy ownership/unlock rules beyond exposing existing unlock information.
- Introduce a backend requirement.
- Require a VPS or server-side roster service.

## 28. Acceptance criteria

The redesign is complete when:

1. Roster is a full-screen primary destination reached from the center bottom-nav button.
2. The bottom bar is `Play / Care / Roster / Shop / Rewards`.
3. Settings is accessible from the upper-right UI.
4. Prestige is reachable from Rewards as a dedicated screen.
5. Roster categories are generated dynamically from runtime roster data.
6. Status filtering supports All, Unlocked, and Locked.
7. Search, sort, and favorites are permanently accessible.
8. Grid is 3-across on normal phones and responsive at other widths.
9. Every card always shows portrait, puppy name, puppy ID, heart, and relevant lock/current state.
10. Selected Puppy panel appears under filters and remains minimal.
11. Equipping requires `Use Puppy` plus portrait/name confirmation.
12. Confirming an equip returns to Play.
13. Locked puppies expose a lock state and optional How to Unlock flow but cannot equip.
14. Existing dynamic/streamed roster behavior remains intact.
15. The screen remains usable with missing metadata, failed artwork, and large future rosters.
