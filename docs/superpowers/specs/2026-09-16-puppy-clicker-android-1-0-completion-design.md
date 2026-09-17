# Puppy Clicker Android 1.0 Completion Design

Date: 2026-09-16
Status: Approved design
Repository: `markhitchk/pup-clinker`
Scope: Android app only (`App/`)

## Goal

Finish the remaining Puppy Clicker Android work required for a cohesive 1.0 release without replacing the existing app architecture, package identity, signing configuration, streamed-asset system, save/export system, casino economy, or generated-source patch pipeline.

This is a single coordinated 1.0 completion program composed of isolated subsystems. The work ships together, but each subsystem keeps a clear persistence boundary, UI boundary, and test surface so the release is not implemented as one monolithic patch.

## Explicit Scope

The 1.0 completion program finishes the following areas:

1. Per-puppy Bond progression.
2. Player XP and level progression.
3. Current V6 achievement integration.
4. Expanded 2D Puppy Viewer inside the current Roster flow.
5. Persistent in-app notification history.
6. Release Hub / What's New using the existing GitHub Releases update source.
7. AFK reward hardening and duplicate-claim protection.
8. User-facing performance presets built on the existing motion/performance policy.
9. Accessibility hardening across current Compose surfaces.
10. Adaptive tablet / large-screen layouts.
11. Android home-screen puppy widget.
12. One-time 1.0 celebration and commemorative reward.

The work must preserve already implemented 1.0 systems including Daily Tasks, Gacha, Care, GitHub Releases update checks, streamed assets, encrypted save transfer, PupEye, Casino transaction handling, Android pointer support, Tier-1 support reporting, onboarding, and event infrastructure.

## Explicit Exclusions

The following are not part of this work:

- No Offline Mode or user-facing offline-mode toggle.
- No new backend, VPS, central account service, inventory API, or cloud-save API.
- No new currency.
- No new Casino games.
- No 3D puppy models.
- No replacement of the current Roster with a second roster implementation.
- No package-name change.
- No signing-key or signing-configuration change.
- No reset of existing version codes.
- No removal of the existing GitHub-backed streamed asset cache/fallback behavior; it remains resilience infrastructure rather than an Offline Mode feature.

## Architectural Constraints

### Generated-source pipeline

The Android build already copies Kotlin into generated protected source and applies ordered Python/Gradle patches before compilation. The 1.0 work must preserve that mechanism.

Changes should prefer normal Kotlin source files for durable subsystem logic and use the existing patch layer only where generated V6 shell integration is already the established pattern. New patch steps must be deterministic, idempotent, testable, and ordered explicitly in `App/tools/seasonal.gradle.kts`.

The implementation must not create a second navigation shell or bypass the current generated `PuppyClickerV6Activity` flow.

### Persistence

Existing local state, encrypted save transfer, PupEye save sealing, and save compatibility must remain valid.

New 1.0 state must use explicit schema keys / versioning and migration logic. A pre-1.0 save must load without destructive reset. New state defaults must be deterministic.

Where state participates in import/export, it must be included in the encrypted save payload and covered by compatibility tests.

### Economy

Treats remain the primary currency. Existing Upgrade Tickets / Common Tickets remain the only additional currencies already present.

The 1.0 completion work must not mint hidden replacement currencies or create duplicate balances.

## 1. Per-Puppy Bond Progression

### Current state

The current V6 game state already contains a `bond` value and Care can increase it, but the value behaves as global/current-game state rather than persistent progression for each puppy.

### 1.0 model

Bond becomes puppy-specific.

Persist a map keyed by canonical puppy asset/style ID:

- `bondByPuppyId: Map<String, Int>`
- Valid range: `0..100`
- Existing active-puppy Bond migrates to the currently selected puppy.
- Puppies without stored Bond start from the current default Bond baseline used by the app.

Every Bond mutation requires an explicit target puppy ID. Care actions apply to the currently selected puppy.

### Bond milestones

Bond uses lightweight milestone tiers rather than a second complex RPG system:

- 0-24: New Friend
- 25-49: Buddy
- 50-69: Close Pal
- 70-89: Best Friend
- 90-100: Forever Friend

Milestones can unlock presentation rewards such as profile labels, small celebratory effects, and achievement progress. They must not create a second currency or destabilize Treat production.

The existing care-score bonus remains governed by existing care rules. Bond milestones must not silently stack major economy multipliers.

### UI

Show Bond for the selected puppy in Care and Puppy Viewer. Roster grid cards remain compact and do not need a full Bond meter.

## 2. Player XP and Level Progression

### Current state

V6 currently derives player level from lifetime Treats.

### 1.0 model

Promote progression to an explicit XP model while maintaining compatibility with existing players.

Persist:

- `playerXp: Long`
- `playerLevel: Int` may remain derived from XP and does not need duplicate persistence if derivation is deterministic.

Migration seeds XP from the player's existing lifetime-Treat progression so established players do not return to level 1.

### XP sources

Award XP for ordinary play actions that already exist:

- Manual tapping.
- Care actions.
- Daily task completion.
- Achievement claims / completion.
- New-puppy acquisition.
- Selected event/reward completions where appropriate.

XP awards must be bounded and centralized in one progression service/helper rather than scattered magic numbers across UI files.

### Level curve

Use a deterministic monotonic curve with no level loss. The exact formula is owned by the progression helper and covered by tests for boundary levels and migration values.

No prestige reset of XP is introduced by this design.

## 3. V6 Achievement Integration

### Current state

Achievement definitions already exist in the repository but are not fully exposed as a first-class current V6 feature.

### 1.0 behavior

Reuse existing achievement IDs wherever practical. Do not create duplicate versions of an achievement that already exists.

Achievements become visible from the current Rewards experience through a dedicated Achievements section.

Each achievement has:

- Stable ID.
- Title.
- Description.
- Progress target.
- Current progress.
- Completed state.
- Claimed state when a claim action is applicable.
- Reward description.

Achievement evaluation must be pure/deterministic from persisted game state where possible. One-time reward claims must use persisted claim IDs so they are idempotent across app restart/import.

Existing players receive completion credit when their current state already satisfies an achievement.

## 4. Expanded 2D Puppy Viewer

### Current state

The Roster already supports filtering, favorites, selected-puppy information, preview dialogs, streamed art retry, ownership states, and selection confirmation.

### 1.0 behavior

Keep the current Roster and add a richer Puppy Viewer rather than replacing the roster.

The viewer is a larger static 2D presentation using the same streamed art pipeline and fallback behavior.

Show, when data exists:

- Large puppy artwork.
- Puppy display name.
- Canonical asset ID.
- V1 / V2 or roster group/category.
- Ownership state.
- Unlock/acquisition source.
- Favorite state.
- Selected/current state.
- Bond value and Bond milestone.
- Relevant rarity/event/birthday metadata already available from roster data.
- Retry-artwork action on stream failure.

Actions:

- Use / Select puppy.
- Favorite / Unfavorite.
- Existing rename action where supported.
- Existing accessory controls where supported.

The viewer must not invent metadata not provided by current roster/manifest data.

## 5. Persistent Notification History

### Current state

The app already has Android notification infrastructure, scheduled notifications, update notifications, a top-bar bell, unread state, and update UI.

### 1.0 model

Add a local in-app notification inbox.

Each history item contains:

- Stable local notification ID.
- Type/category.
- Title.
- Body/summary.
- Created timestamp.
- Read/unread state.
- Optional deep-link/action target represented by a safe internal route enum or payload.
- Optional external release URL only for existing release/update behavior.

History is local only. No notification server is introduced.

### Retention

Keep a bounded history, for example the most recent 100 items. Pruning is deterministic and must not affect Android system notifications.

### Bell behavior

The top-bar bell opens the history list. Opening the inbox does not have to mark every item read automatically; items should become read through explicit viewing/interaction rules.

Unread badge count derives from local inbox state.

### Android notification channels

The implementation should preserve current notification behavior first. Channel consolidation is not part of this 1.0 completion spec unless a compatibility review proves a safe migration path. Android notification channel IDs are persistent user-facing OS objects once created, so changing them requires a separately deliberate migration decision.

## 6. Release Hub / What's New

### Current state

The app already checks the latest published GitHub Release and caches release metadata, notes, APK URL, release URL, and unread state.

### 1.0 behavior

Add a Release Hub reachable from Settings/About and from update-related notification history.

Show:

- Installed app version and version code.
- Latest known release name/version/build.
- Release notes already returned by the existing update source.
- Update availability state.
- Refresh action.
- Existing open-release / update action.
- A compact local record for the currently installed 1.0 What's New entry.

The Release Hub must use the existing GitHub Releases source and must not introduce another update API.

## 7. AFK Reward Hardening

### Current state

AFK accrual is correctly gated behind completed onboarding and process/background timestamps are already converted into pending rewards.

### 1.0 hardening

Add:

- A defined maximum AFK accrual duration.
- Persisted claim identity / claim timestamp sufficient to prevent duplicate settlement of the same background interval.
- Validation that pending reward interval start/end are sane and ordered.
- Protection against negative clock deltas.
- Protection against large forward/backward clock anomalies by clamping to the supported maximum interval.
- Regression tests for onboarding, process death, repeated foregrounding, duplicate activity lifecycle callbacks, and repeated claim attempts.

Recommended maximum accrual window: 7 days. At the existing 1000 Treats/day rate this remains understandable and bounded.

AFK must continue to use the existing Treat economy; no separate AFK currency is created.

## 8. User-Facing Performance Presets

### Current state

`PuppyMotionPolicy` already supports motion modes/presets, reduced motion, effect categories, and adaptive behavior.

### 1.0 behavior

Expose three simple player-facing presets in Settings:

- Automatic
- Quality
- Battery Saver

Automatic maps to existing adaptive performance behavior.

Quality enables the normal intended visual experience while still respecting explicit reduced-motion accessibility preferences.

Battery Saver reduces nonessential motion/effects, loading shimmer where appropriate, celebration intensity, and other existing effect toggles without disabling core game feedback.

Do not expose every internal motion flag as a separate player setting.

Persist the chosen preset. Migration defaults existing players to Automatic unless an existing motion preference can be mapped deterministically.

## 9. Accessibility Hardening

The 1.0 pass must audit current primary Android Compose surfaces, including:

- Top app bar.
- Bottom navigation.
- Play.
- Care.
- Roster and Puppy Viewer.
- Shop.
- Rewards / Achievements.
- Settings.
- Release Hub.
- Notification history.
- Gacha.
- Casino hub and game controls.
- Onboarding.

Requirements:

- Interactive icon-only controls have meaningful content descriptions.
- Decorative images do not pollute TalkBack traversal.
- State controls expose selected/checked/expanded state semantically.
- Touch targets remain at least Material accessibility minimums unless a platform exception is unavoidable.
- Text does not rely solely on color to convey critical state.
- Focus order is logical for major dialogs and sheets.
- Reduced-motion policy is respected by new 1.0 animations.
- Layout remains usable at increased Android font scale; critical controls must not be clipped or made unreachable.

Accessibility changes should not replace the existing visual theme.

## 10. Adaptive Tablet and Large-Screen Layouts

### Goal

Support tablets, foldables, and wide landscape windows without creating a separate tablet app.

### Implementation pattern

Use window/content-width breakpoints in shared Compose layout helpers.

Compact width retains the current phone-first stack.

Medium/expanded widths may use two-pane or denser adaptive composition where it improves usability.

Priority surfaces:

- Roster: filters/list/grid + Puppy Viewer can become side-by-side.
- Care: puppy/status area + actions can use two columns.
- Shop / Rewards: adaptive grid width and constrained readable content columns.
- Settings: constrained centered content or two-column sections on expanded width.
- Casino / Gacha: preserve game aspect and controls without stretching excessively.

Do not duplicate business logic per form factor.

## 11. Android Home-Screen Puppy Widget

### 1.0 scope

Add a read-only home-screen widget.

The widget shows:

- Currently selected puppy artwork when a safe local/bundled/cached representation is available.
- Puppy name.
- Current Treat balance.
- A compact care/status indicator where practical.
- Tap action to open Puppy Clicker.

The widget must not directly mutate the game economy in 1.0. No widget tap-to-earn, feed, claim, or gacha actions are included because direct cross-process game mutations increase save-race and integrity risk.

### Data source

Expose a small widget-safe snapshot written by the main app after successful game saves/state changes. The widget reads this snapshot and does not parse or mutate the full protected save.

When streamed artwork is unavailable to the widget process, use a safe fallback/logo/placeholder rather than performing unrestricted network work from the widget provider.

## 12. One-Time 1.0 Celebration

### Trigger

Show once when a player first runs a build designated as the 1.0 stable celebration release.

### Behavior

Display a Puppy Clicker 1.0 celebration surface with:

- 1.0 title.
- Compact What's New summary.
- A commemorative 1.0 badge/reward.
- Continue action.
- Link/action to Release Hub for full notes.

### Reward integrity

Persist a stable reward/claim ID such as `release_1_0_launch_reward`.

The reward is idempotent. App restart, activity recreation, save import, or repeated upgrade checks must not grant it more than once for the same player save.

The reward should be cosmetic/profile-oriented or a modest bounded existing-currency reward. It must not introduce a new currency.

If the player imports a save that has already claimed the 1.0 reward, the claim remains honored.

## Navigation and UI Integration

The generated main shell remains:

- Play
- Care
- Roster
- Shop
- Rewards

Internal destinations continue to include existing Settings, Prestige, Exchange, Casino, and Gacha routes.

New 1.0 surfaces should integrate as nested/current destinations rather than new bottom-nav tabs:

- Puppy Viewer: from Roster.
- Achievements: under Rewards.
- Notification History: from top-bar bell.
- Release Hub: from Settings/About and update notifications.
- 1.0 Celebration: one-time startup overlay/screen gated by persisted claim/display state.

The home-screen widget exists outside in-app navigation and launches the existing app entry point.

## Save and Migration Design

### New persisted concepts

The save layer will gain explicit state for:

- Per-puppy Bond map.
- Player XP / progression state.
- Achievement claim/completion identifiers where not derivable.
- Notification history/read state.
- Performance preset.
- AFK claim-settlement metadata.
- 1.0 celebration/reward claim state.

Widget snapshot storage is derived/cache state and should not be treated as authoritative save data.

### Migration rules

On loading an older save:

1. Preserve all existing balances, roster ownership, selected puppy, event data, Casino state, account/player identity, and support/privacy settings.
2. Migrate current global Bond to the selected puppy.
3. Initialize other puppy Bond entries lazily/defaulted.
4. Seed XP from the existing lifetime-Treat/level progression so player progression does not visibly reset.
5. Re-evaluate achievements from current state and mark satisfied non-claim progress appropriately; one-time rewards remain unclaimed until their claim rules are satisfied.
6. Default notification history to empty without affecting existing Android notifications.
7. Map existing motion preferences to the nearest 1.0 performance preset when deterministic; otherwise use Automatic.
8. Initialize AFK settlement metadata without invalidating any legitimate pending reward already created before migration.
9. Preserve any imported 1.0 celebration claim state when present.

Migration must be covered by unit/instrumentation tests using representative legacy payloads.

## Error Handling

- Unknown puppy ID in Bond map: ignore orphan entry safely and preserve it through save migration when possible; never crash roster loading.
- Corrupt/invalid XP: clamp to a safe non-negative value and let save integrity tooling record/recover according to existing policy.
- Notification history decode failure: drop only invalid history entries rather than resetting the game save.
- Release API unavailable: show cached data and a clear refresh failure state; do not break Settings.
- AFK invalid interval: reject/clamp the interval and never produce negative or unbounded rewards.
- Widget snapshot missing/corrupt: render fallback content and open app normally on tap.
- Large-screen adaptive layout failure: fall back to compact composition rather than hiding controls.

## Testing Strategy

### Unit tests

Add focused tests for:

- Bond per-puppy isolation, clamping, milestones, and migration.
- XP awards, level boundaries, migration seeding, and no level regression for representative saves.
- Achievement evaluation and idempotent claims.
- Notification history insertion, read state, pruning, and serialization.
- AFK maximum accrual, duplicate-claim protection, clock anomaly handling, and onboarding gate preservation.
- Performance preset mapping and persistence.
- 1.0 celebration/reward idempotency.
- Widget snapshot serialization.

### Generated integration tests

Verify the generated V6 source still wires:

- Existing main/internal destinations.
- Puppy Viewer access from Roster.
- Achievements in Rewards.
- Bell -> Notification History.
- Settings/About -> Release Hub.
- Existing Gacha/Casino/Exchange wiring.

Patch application must remain deterministic in a clean build.

### Compose / instrumentation tests

Cover at minimum:

- Phone compact Roster -> Puppy Viewer flow.
- Expanded-width Roster two-pane/adaptive behavior.
- Achievement visibility/claim path.
- Notification inbox read/unread behavior.
- Performance preset selection.
- 1.0 celebration shown once.
- Accessibility semantics for key icon-only controls and selected states.
- Widget provider renders fallback snapshot safely.

### Regression tests

Existing tests for save transfer, streamed assets, Gacha, Casino, navigation, PupEye, and onboarding must continue passing.

## Implementation Boundaries

Prefer small focused classes/files for new responsibilities, for example:

- Progression/Bond helper or repository.
- Achievement evaluator/state adapter.
- Notification history repository.
- Release Hub view model/state adapter using the existing update client.
- AFK settlement helper.
- Performance preset adapter around `PuppyMotionPolicy`.
- Adaptive layout helper.
- Widget snapshot writer/provider.
- 1.0 launch reward coordinator.

Do not put all 1.0 logic into `PuppyClickerV6Activity.kt` or one generated patch file.

## Release and Versioning Constraint

The current repository uses a monotonically increasing Android `versionCode`; that must continue.

This design does not force a specific public `versionName` migration because existing public builds already use the `1.7.x` naming family. Choosing the final 1.0 marketing/version string is a release-management decision separate from feature implementation. Under no circumstances may the Android `versionCode` decrease.

## Completion Criteria

The Android 1.0 completion program is complete when:

- All twelve scoped areas above are implemented and wired into the current Android app.
- Offline Mode is absent as a user-facing feature.
- Existing saves migrate without losing balances, ownership, identity, Casino state, or selected puppy.
- Existing generated-source build architecture remains operational.
- New one-time rewards/claims cannot be duplicated by normal restart/import/lifecycle paths.
- Phone layouts remain usable and expanded layouts adapt rather than merely stretching.
- The home-screen widget is read-only and cannot race the protected game economy.
- Accessibility regressions identified by the 1.0 audit are resolved for primary surfaces.
- Existing relevant unit/instrumentation tests pass and new tests cover the 1.0 state/migration paths.
- No package/signing identity change is introduced.
