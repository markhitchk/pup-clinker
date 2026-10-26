# Account Onboarding Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task.

**Goal:** Restore the Kotlin first-run flow with authenticated Pup Account integration.
**Architecture:** Native Kotlin preference persistence and final account verification; React hook and six-step screen; existing cloud-read activation remains authoritative.
**Tech Stack:** Expo / React Native, Kotlin, node:test / react-test-renderer.
**Spec:** `docs/superpowers/specs/2026-10-25-account-onboarding-design.md`

## Global Constraints
- Six steps: Welcome, Set Up Your Account, Personalize, Permissions & Privacy, Notifications, Ready to Play.
- No guest identity, .pupsave import/export, manufactured cloud save, or device-binding bypass.
- `puppy_ui_preferences_v2` persistence, flow version 5, privacy version 5; privacy defaults off.
- Beta remains `com.harleytg.puppyclicker.beta`, `Puppy Clicker [Beta]`.
- Existing signed-gameplay limitations remain explicit; completion only saves setup preferences.

## Review Focus
- Restart midway must resume the saved step and choices.
- Discord callback device conflict must not advance account setup.
- Cloud activation failure must prevent leaving account setup or finishing.
- Optional privacy/notifications declined must not block completion.
- Invalid birthday and premature finish must be rejected.

### Task 1: Authenticated setup state and persistence
**Files:** `Mobile/src/production/useAccountOnboarding.js`, `Mobile/plugins/puppy-production/PuppyBetaAccountPackage.kt`, `Mobile/tests/accountOnboarding.test.js`.
**Interfaces:** Native `readSetup()`, `saveSetup(json)`, `finishSetup(key)`; hook `useAccountOnboarding({enabled,account,activateAccount})` returns `{setup,ready,busy,problem,update,next,back,finish}`.
- [x] Write behavior tests for resume, cloud-failed account transition, privacy review, invalid date, and premature finish; observe red.
- [x] Implement preference-only native allowlist, server-reverified finish, and serialized hook persistence.
- [x] Run `node --test tests/accountOnboarding.test.js`; expect no failures.
- [x] Commit task.

### Task 2: Kotlin onboarding screens and app entry
**Files:** `Mobile/src/production/AccountOnboarding.jsx`, `Mobile/App.js`, `Mobile/src/state/useGame.js`, account UI tests; original logo assets.
**Interfaces:** `AccountOnboarding({flow,account,notifications,onConnect})`, using Task 1 hook. `refreshAccount()` resolves boolean cloud activation success.
- [x] Write screen tests for six steps, sign-in without nickname, no forward button until verified, birthday, privacy choices, notification skip, and review setup; observe red.
- [x] Implement original shell, content and controls, native identity summary, and account sheet integration.
- [x] Gate beta entry using persisted setup state, apply preferences to the game presentation, and return explicit cloud activation success/failure.
- [x] Run `npm test` and `npx expo export --platform android`; expect passing suite and bundle.
- [ ] Review combined diff, push existing authorized main changes, inspect CI build result.
