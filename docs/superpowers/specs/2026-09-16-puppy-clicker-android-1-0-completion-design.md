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
- Puppies without stored Bond start at `10`, matching the current V6 default Bond baseline.

Every Bond mutation requires an explicit target puppy ID. Care actions apply to the currently selected puppy.

### Bond milestones

Bond uses lightweight milestone tiers rather than a second complex RPG system:

- 0-24: New Friend
- 25-49: Buddy
- 50-69: Close Pal
- 70-89: Best Friend
- 90-100: Forever Friend

Milestones unlock presentation state only in 1.0: milestone label, milestone celebration when newly crossed, and achievement progress. They do not add Treat multipliers, currencies, or hidden production bonuses.

The existing care-score bonus remains governed by existing care rules.

### UI

Show Bond for the selected puppy in Care and Puppy Viewer. Roster grid cards remain compact and do not need a full Bond meter.

## 2. Player XP and Level Progression

### Current state

V6 currently derives player level from lifetime Treats using a square-root progression curve.

### 1.0 model

Promote progression to an explicit XP model while preserving the existing visible level for established players at migration time.

Persist:

- `playerXp: Long`
- Player level is derived, not separately authoritative.

Migration seeds `playerXp` from non-negative `lifetimeTreats`. The 1.0 level formula remains compatible with the current curve:

`level = 1 + floor(sqrt(playerXp / 100.0))`

This keeps a player's migrated level aligned with the existing lifetime-Treat-derived level while allowing future XP to come from more than Treat accumulation.

### XP sources

All XP awards go through one progression helper. Initial 1.0 award values are:

- Successful manual tap event: `+1 XP`.
- Care action completed: `+5 XP`.
- Daily task completed: `+25 XP`.
- Achievement newly completed after migration: `+50 XP`.
- New puppy ownership acquired: `+100 XP`.
- Event/reward completion explicitly opted into XP: `+50 XP` per stable completion ID.

XP is non-negative and monotonic. No action removes XP. A single action event can settle its XP only once.

Migration may mark existing achievements completed from existing state, but migration itself does not retroactively award achievement XP; this avoids an unpredictable one-time XP spike for established saves.

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

Existing players receive completion credit when their current state already satisfies an achievement. Migration completion credit does not award the new XP completion bonus.

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

Keep exactly the most recent `100` history items. On insertion beyond 100, prune the oldest items by creation timestamp with stable-ID tie-breaking. Pruning must not affect Android system notifications.

### Bell behavior

The top-bar bell opens the history list. Opening the inbox alone does not mark all items read. An item becomes read when its row/detail is opened or its explicit action is invoked. A user-visible `Mark all read` action is allowed.

Unread badge count derives from local inbox state.

### Android notification channels

The implementation preserves current Android notification channel IDs and behavior. Channel consolidation is not part of this 1.0 completion spec. Android notification channel IDs are persistent user-facing OS objects once created, so changing them requires a separately deliberate migration decision.

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

The maximum AFK accrual window is exactly `7 days` (`168 hours`). At the existing `1000 Treats/day` rate, the largest normal capped AFK reward is therefore `7000 Treats`.

Add:

- Persisted settlement identity for each pending AFK interval.
- Persisted last-settled interval metadata sufficient to reject duplicate settlement of the same interval.
- Validation that pending reward interval start/end are sane and ordered.
- Protection against negative clock deltas.
- Forward/backward clock anomalies clamped to the 168-hour maximum rather than producing unbounded rewards.
- Regression tests for onboarding, process death, repeated foregrounding, duplicate activity lifecycle callbacks, and repeated claim attempts.

AFK must continue to use the existing Treat economy; no separate AFK currency is created.

## 8. User-Facing Performance Presets

### Current state

`PuppyMotionPolicy` already supports motion modes/presets, reduced motion, effect categories, and adaptive behavior.

### 1.0 behavior

Expose exactly three simple player-facing presets in Settings:

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

Accessibility changes must not replace the existing visual theme.

## 10. Adaptive Tablet and Large-Screen Layouts

### Goal

Support tablets, foldables, and wide landscape windows without creating a separate tablet app.

### Breakpoints

Use content/window width in density-independent pixels:

- Compact: `< 600dp`
- Medium: `600dp..839dp`
- Expanded: `>= 840dp`

Compact width retains the current phone-first stack.

Medium/expanded widths may use two-pane or denser adaptive composition where it improves usability.

### Priority surfaces

- Roster: filters/grid and Puppy Viewer may become side-by-side on expanded width.
- Care: puppy/status area and actions may use two columns.
- Shop / Rewards: adaptive grid width and constrained readable content columns.
- Settings: constrained centered content or two-column sections on expanded width.
- Casino / Gacha: preserve game aspect and controls without stretching excessively.

Do not duplicate business logic per form factor. If an adaptive branch cannot safely render, fall back to the compact composition.

## 11. Android Home-Screen Puppy Widget

### 1.0 scope

Add one read-only home-screen widget using the platform `AppWidgetProvider`/`RemoteViews` path so the feature does not require introducing a second UI framework dependency.

The widget shows:

- Currently selected puppy artwork when a safe local/bundled/cached representation is available.
- Puppy name.
- Current Treat balance.
- A compact care/status indicator.
- Tap action to open Puppy Clicker.

The widget must not directly mutate the game economy in 1.0. No widget tap-to-earn, feed, claim, or gacha actions are included because direct cross-process game mutations increase save-race and integrity risk.

### Data source

Expose a small widget-safe snapshot written by the main app only after a successful authoritative game save/state settlement. The widget reads this snapshot and does not parse or mutate the full protected save.

When streamed artwork is unavailable to the widget process, use the app's safe packaged fallback artwork/logo rather than performing unrestricted network work from the widget provider.

## 12. One-Time 1.0 Celebration

### Trigger

Show once when a player first runs a build designated as the 1.0 stable celebration release.

### Behavior

Display a Puppy Clicker 1.0 celebration surface with:

- 1.0 title.
- Compact What's New summary.
- A commemorative profile badge named `Puppy Clicker 1.0` with stable ID `release_1_0_badge`.
- Continue action.
- Link/action to Release Hub for full notes.

The 1.0 celebration grants no Treats, tickets, Casino value, or other economy currency.

### Reward integrity

Persist stable claim ID `release_1_0_launch_reward` together with the badge entitlement.

The reward is idempotent. App restart, activity recreation, save import, or repeated upgrade checks must not grant duplicate badge entitlements or replay claim side effects.

If the player imports a save that has already claimed the 1.0 reward, the claim remains honored.

## Navigation and UI Integration

The generated main shell remains:

- Play
- Care
- Roster
- Shop
- Rewards

Internal destinations continue to include existing Settings, Prestige, Exchange, Casino, and Gacha routes.

New 1.0 surfaces integrate as nested/current destinations rather than new bottom-nav tabs:

- Puppy Viewer: from Roster.
- Achievements: under Rewards.
- Notification History: from top-bar bell.
- Release Hub: from Settings/About and update notifications.
- 1.0 Celebration: one-time startup overlay/screen gated by persisted claim/display state.

The home-screen widget exists outside in-app navigation and launches the existing app entry point.

## Save and Migration Design

### New persisted concepts

The save layer gains explicit state for:

- Per-puppy Bond map.
- Player XP / progression state.
- Achievement claim/completion identifiers where not derivable.
- Notification history/read state.
- Performance preset.
- AFK claim-settlement metadata.
- 1.0 celebration/reward claim and badge entitlement state.

Widget snapshot storage is derived/cache state and is not authoritative save data.

### Migration rules

On loading an older save:

1. Preserve all existing balances, roster ownership, selected puppy, event data, Casino state, account/player identity, and support/privacy settings.
2. Migrate current global Bond to the selected puppy; other puppies lazily default to Bond 10.
3. Seed `playerXp` from non-negative lifetime Treats so the existing visible level is preserved by the compatible square-root curve.
4. Re-evaluate achievements from current state and mark satisfied progress/completion; migration completion does not award new achievement XP.
5. Default notification history to empty without affecting existing Android notifications.
6. Map existing motion preferences to the nearest 1.0 performance preset when deterministic; otherwise use Automatic.
7. Initialize AFK settlement metadata without invalidating any legitimate pending reward already created before migration; any migrated pending interval is still clamped to the 168-hour cap before settlement.
8. Preserve any imported 1.0 celebration claim/badge state when present.

Migration must be covered by unit/instrumentation tests using representative legacy payloads.

## Error Handling

- Unknown puppy ID in Bond map: ignore the orphan for active UI but preserve it through save round trips when possible; never crash roster loading.
- Corrupt/invalid XP: clamp to a safe non-negative value and let existing save-integrity tooling record/recover according to current policy.
- Notification history decode failure: drop only invalid history entries rather than resetting the game save.
- Release API unavailable: show cached data and a clear refresh failure state; do not break Settings.
- AFK invalid interval: reject/clamp the interval and never produce negative or more than 7000 Treats from one AFK settlement under the current rate.
- Widget snapshot missing/corrupt: render packaged fallback content and open the app normally on tap.
- Large-screen adaptive layout failure: fall back to compact composition rather than hiding controls.

## Testing Strategy

### Unit tests

Add focused tests for:

- Bond per-puppy isolation, clamping, milestone boundaries, and migration.
- XP awards, exactly-once event settlement, level boundaries, migration seeding, and no level regression for representative saves.
- Achievement evaluation and idempotent claims.
- Notification history insertion, read state, 100-item pruning, tie-breaking, and serialization.
- AFK 168-hour maximum accrual, 7000-Treat cap at the current rate, duplicate-claim protection, clock anomaly handling, and onboarding gate preservation.
- Performance preset mapping and persistence.
- 1.0 celebration/badge idempotency.
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
- Expanded-width Roster adaptive/two-pane behavior.
- Achievement visibility/claim path.
- Notification inbox read/unread behavior.
- Performance preset selection.
- 1.0 celebration shown once.
- Accessibility semantics for key icon-only controls and selected states.
- Widget provider renders fallback snapshot safely.

### Regression tests

Existing tests for save transfer, streamed assets, Gacha, Casino, navigation, PupEye, and onboarding must continue passing.

## Implementation Boundaries

Prefer small focused classes/files for new responsibilities, including:

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
