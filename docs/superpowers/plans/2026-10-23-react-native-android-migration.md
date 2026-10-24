# React Native Android Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A real React Native Puppy Clicker Android build with meaningful gameplay while protecting the existing signed Kotlin release.

**Architecture:** Adjacent Expo-managed `Mobile/` project. Reuse verified isolated web engine functions, React Native screens and local-only data boundary. Incrementally replace missing native features, never wholesale cut over unverified Android production.

**Tech Stack:** Expo SDK 57, React 19.2, RN 0.86, Metro, node:test, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-23-react-native-android-migration.md`

## Global constraints
- Preserve Android package `com.harleytg.puppyclicker` and `App/` untouched.
- No WebView. No release claim until device testing and Play signing proven.
- No guest-save escalation to live Supabase production or PupEye.
- Asset IDs/currency/ticket and chip mechanics must match the Kotlin source.
- No secrets checked into Git.

## Review Focus
- Stale/background storage callback must not overwrite newer session state.
- Broken/invalid local JSON must reset without crash and must not be treated as authenticated data.
- Racing taps must not drop input when React state is batching events.
- Wager failures must not debit chips; payouts must settle exactly once.
- A new APK signed with different key must never be claimed upgrade-compatible.

### Task 1: Shared engine + golden smoke tests
**Files:** Create `Mobile/src/engine/{game,casinoV7}.js`, `Mobile/tests/engine.test.js`.
**Interfaces:** `freshGame()`, `tap(g,now,rng)`, `purchase(g,u)`, `care(g,kind)`, `prestige(g)`, casino helpers.
- [x] Write failing golden economy/casino test.
- [x] Run `cd Mobile && npm test`: confirm expected missing import failure.
- [x] Vendor Android-derived web logic with source/pin comment.
- [x] Run `cd Mobile && npm test`: verify green.
- [x] Commit.

### Task 2: Expo project identity and React Native screen shell
**Files:** Create `Mobile/package.json`, `app.json`, `index.js`, `App.js`, `src/theme.js`, `Mobile/tests/config.test.js`.
**Interfaces:** React Native rendering, 5 tabs, roster and game handlers, package identity.
- [x] Write failing packaging/config tests (no WebView, package ID, five tabs).
- [x] Run to observe red.
- [x] Add Expo 57 config and the native React UI (not DOM tags) for core tabs and secondary pages.
- [x] Run Node tests and `npx expo export --platform android` once dependencies install in CI.
- [x] Commit.

### Task 3: Local save adapter and feature guards
**Files:** Create `Mobile/src/state/storage.js`, `Mobile/src/state/useGame.js`, tests.
**Interfaces:** `parseGuestSave(raw)`, `serializeGuestSave(g)`, `useGame()`.
- [x] Write malformed-save + data boundary tests.
- [x] Run to observe red.
- [x] Implement local device-scoped persistence, no automatic official cloud import.
- [x] Verify tests; commit.

### Task 4: Build pipeline and source-of-truth disclosure
**Files:** Add `.github/workflows/react-native-android.yml`, `Mobile/README.md`, root README link.
**Interfaces:** On PR, Node tests + static bundle on Expo Android; separate/manual signed-release gate.
- [x] Write tests for Android identity and no published release claim.
- [x] Test PR CI bundle; diagnose failures.
- [x] Document exact local setup, signing/upload migration, features not yet ported.
- [x] Commit; PR to main; do NOT auto-merge.

### Follow-on migration tasks — current checkpoint

- [x] Kotlin-derived care offline decay and per-puppy clocks (guest mode)
- [x] Source-derived Gacha restrictions and capsule UI (guest mode)
- [x] Nine source achievement definitions and deduplicated local guest XP
- [x] Source-derived 52-card Blackjack deck, split/double and persisted guest wagers
- [x] 30 source V1/V2/Discord PNG portraits statically bundled offline
- [x] Six-step guest React Native onboarding and preview package release guard
- [ ] Complete source-level economics, official account and device/PupEye authority
- [ ] Complete code redemption, player-to-player Exchange and protected reward settlement
- [ ] Complete casino scenes/animation, monthly rewards, notifications, widgets, UI parity
- [ ] Signed Google Play AAB and real-device in-place save migration verification

See `Mobile/PARITY.md` for precise missing features and release gates.
