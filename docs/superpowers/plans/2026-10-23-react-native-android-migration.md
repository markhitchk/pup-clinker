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
- [ ] Write failing golden economy/casino test.
- [ ] Run `cd Mobile && npm test`: confirm expected missing import failure.
- [ ] Vendor Android-derived web logic with source/pin comment.
- [ ] Run `cd Mobile && npm test`: verify green.
- [ ] Commit.

### Task 2: Expo project identity and React Native screen shell
**Files:** Create `Mobile/package.json`, `app.json`, `index.js`, `App.js`, `src/theme.js`, `Mobile/tests/config.test.js`.
**Interfaces:** React Native rendering, 5 tabs, roster and game handlers, package identity.
- [ ] Write failing packaging/config tests (no WebView, package ID, five tabs).
- [ ] Run to observe red.
- [ ] Add Expo 57 config and the native React UI (not DOM tags) for core tabs and secondary pages.
- [ ] Run Node tests and `npx expo export --platform android` once dependencies install in CI.
- [ ] Commit.

### Task 3: Local save adapter and feature guards
**Files:** Create `Mobile/src/state/storage.js`, `Mobile/src/state/useGame.js`, tests.
**Interfaces:** `parseGuestSave(raw)`, `serializeGuestSave(g)`, `useGame()`.
- [ ] Write malformed-save + data boundary tests.
- [ ] Run to observe red.
- [ ] Implement local device-scoped persistence, no automatic official cloud import.
- [ ] Verify tests; commit.

### Task 4: Build pipeline and source-of-truth disclosure
**Files:** Add `.github/workflows/react-native-android.yml`, `Mobile/README.md`, root README link.
**Interfaces:** On PR, Node tests + static bundle on Expo Android; separate/manual signed-release gate.
- [ ] Write tests for Android identity and no published release claim.
- [ ] Test PR CI bundle; diagnose failures.
- [ ] Document exact local setup, signing/upload migration, features not yet ported.
- [ ] Commit; PR to main; do NOT auto-merge.

### Follow-on migration tasks (NOT done in first iteration)
- [ ] Implement per-pup care decay, milestones, daily tasks, full gacha/exchange and roster.
- [ ] Implement complete canvas/animated Blackjack and other casinos to parity.
- [ ] Implement Discord OAuth linked original account; Supabase server-authoritative state and PupEye.
- [ ] Implement Android notification channels/widgets, native share targets and device checks.
- [ ] Build full signed AAB with original upload key, test in Play internal track and migrate on-device state.
