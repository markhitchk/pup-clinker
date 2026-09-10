# Puppy Clicker Developer Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a hidden seven-tap Developer Mode and a safe read-only in-app diagnostic console to Puppy Clicker.

**Architecture:** Add focused `PuppyDeveloperPreferences`, `PuppyDebugLog`, and `PuppyDeveloperConsole` units. Integrate them through a final generated-source patch so the existing source-generation pipeline remains authoritative, and route existing Android `Log` calls through the new diagnostic facade without changing call-site semantics.

**Tech Stack:** Kotlin 2.x, Android 14+/API 26 floor, Jetpack Compose Material 3, Kotlin StateFlow, JUnit 4, existing Python generated-source patches.

**Spec:** `docs/superpowers/specs/2026-09-09-developer-console-design.md`

## Global Constraints
- Developer console is read-only and app-scoped; no Android-wide logcat and no command execution.
- Unlock requires exactly seven Build-row taps and persists across launches/updates.
- Console retains at most 500 sanitized in-memory entries and does not persist logs to disk.
- Existing Logcat output remains available.
- Existing Puppy Clicker UI/theme/reduced-motion behavior must remain intact.
- Delete Local Save Data clears Developer Mode; Reset Settings does not.

---

### Task 1: Core diagnostics and unlock state

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyDebugLog.kt`
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyDeveloperPreferences.kt`
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyDeveloperConsoleTest.kt`

**Interfaces:**
- Produces `PuppyLogLevel`, `PuppyLogEntry`, `PuppyDebugLog`, `PuppyDeveloperState`, `PuppyDeveloperPreferences`, `DeveloperUnlockProgress`, and `nextDeveloperUnlockProgress`.

- [ ] **Step 1: Write failing tests** for seven-tap unlock, redaction, 500-entry retention, newest retention, and clear.
- [ ] **Step 2: Run `gradle --no-daemon :app:testDebugUnitTest --stacktrace`** and verify the tests fail because the production types do not exist.
- [ ] **Step 3: Implement minimal core types** with Android Log-compatible `d/i/w/e` overloads, StateFlow snapshot storage, sanitization before storage, and SharedPreferences-backed unlock state.
- [ ] **Step 4: Run `gradle --no-daemon :app:testDebugUnitTest --stacktrace`** and verify all unit tests pass.

### Task 2: Developer Console Compose surface

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyDeveloperConsole.kt`

**Interfaces:**
- Consumes `PuppyDebugLog.observe()` and `PuppyLogEntry`.
- Produces `PuppyDeveloperConsoleScreen(onBack: () -> Unit)` and `PuppyDeveloperOptions(onOpenConsole: () -> Unit)`.

- [ ] **Step 1: Implement the read-only screen** with Back, counts, search, four level filters, newest-first list, Copy Visible Logs, and Clear Console.
- [ ] **Step 2: Ensure no command-entry field exists** and all clipboard content comes from already-sanitized entries.

### Task 3: Generated Settings integration and log routing

**Files:**
- Create: `App/tools/patch_developer_console.py`
- Modify: `App/tools/seasonal.gradle.kts`

**Interfaces:**
- The patch runs after the existing Settings/setup runner.
- It injects Developer state and console navigation into generated `PuppySettingsUi.kt`.
- It makes About/Build tappable with seven-tap progress and persists the unlock.
- It clears developer preferences from the existing local-data delete path.
- It routes `android.util.Log` `d/i/w/e` call sites in generated Puppy Clicker Kotlin files to `PuppyDebugLog` while excluding `PuppyDebugLog.kt` itself.

- [ ] **Step 1: Add deterministic string-anchor transformations** that fail the build when expected Settings anchors are missing.
- [ ] **Step 2: Add the patch as the final `generateProtectedPuppySources` transformation** and include it as a Gradle task input.
- [ ] **Step 3: Run unit tests** and verify generated source compiles.

### Task 4: Full Android verification

**Files:** none beyond prior tasks.

- [ ] **Step 1: Run `gradle --no-daemon :app:testDebugUnitTest --stacktrace`** and require zero test failures.
- [ ] **Step 2: Run `gradle --no-daemon :app:assembleRelease --stacktrace`** and require exit code 0.
- [ ] **Step 3: Restore the temporary branch-only workflow trigger** so the production workflow remains `main`-only.
- [ ] **Step 4: Apply the validated feature commits to `main`** and verify the `main` Android workflow finishes successfully.
