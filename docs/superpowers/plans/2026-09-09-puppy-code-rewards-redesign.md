# Puppy Code Rewards Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the V6 basic redeem dialog with a mobile-first Rewards hub and a GitHub-authoritative, exact-match, one-time Puppy Code system with configurable reward bundles, history, version/flag validation, and local anti-abuse cooldowns.

**Architecture:** Keep GitHub `assets/redeem-codes.json` as the only remote authority. Split catalogue fetching/parsing, validation, reward granting/history, anti-abuse state, and Compose UI into focused Kotlin units while leaving `PuppyClickerV6ViewModel` as the owner of game state. Every claim performs a live GitHub validation and fails closed on network/schema/version/flag errors.

**Tech Stack:** Kotlin 2.x, Android SDK 35, Jetpack Compose Material 3, StateFlow/ViewModel, `HttpURLConnection`, `org.json`, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-09-puppy-code-rewards-redesign-design.md`

## Global Constraints

- Repository: `markhitchk/pup-clinker`; edit `main` directly; no PR.
- No VPS, database, custom API, Firebase, or other backend.
- Every new code claim requires a successful live GitHub catalogue validation.
- Schema-2 code matching is exact: do not uppercase, trim, collapse whitespace, or normalize hyphens before hashing.
- Codes are one-time per save; normal Reset Progress preserves redemption records; Erase All Data removes them.
- Unknown flags/reward types fail before granting anything.
- Reward bundles are applied all-or-nothing.
- Existing `redeemedCodeIds` remain authoritative for legacy saves.
- Upgrade Tickets are allowed only when explicitly present in a reward bundle and remain app-side bounded.
- Raw internal flags and plaintext secret codes are not exposed to players.

---

### Task 1: Schema-2 models, parser, exact hashing, and validation

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyCodeModels.kt`
- Replace/refactor: `App/app/src/main/java/com/harleytg/puppyclicker/StreamedRedeemCodes.kt`
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyCodeCatalogTest.kt`

**Interfaces:**
- Produces: `PuppyCodeDefinition`, `PuppyCodeReward`, `PuppyCodeStatus`, `PuppyCodeRarity`, `PuppyCodeValidationResult`, `PuppyCodeCatalog.parse(String)`, `PuppyCodeCatalog.hashExact(String)`, `PuppyCodeCatalog.findExact(String, PuppyCodeCatalogSnapshot)`.
- Consumes: `BuildConfig.VERSION_CODE`, existing puppy IDs, ticket rarities.

- [ ] **Step 1: Write failing parser/exact-match tests**

```kotlin
@Test fun exactHashDoesNotNormalizeInput() {
    assertNotEquals(PuppyCodeCatalog.hashExact("BUDDY-HELLO-2026"), PuppyCodeCatalog.hashExact("buddy-hello-2026"))
    assertNotEquals(PuppyCodeCatalog.hashExact("BUDDY-HELLO-2026"), PuppyCodeCatalog.hashExact("BUDDY-HELLO-2026 "))
}

@Test fun unknownFlagIsRejected() {
    val result = PuppyCodeCatalog.parse(schema2Json(flags = listOf("FUTURE_FLAG")))
    assertTrue(result is PuppyCodeCatalogParseResult.Unsupported)
}
```

- [ ] **Step 2: Run unit tests and verify they fail**

Run: `cd App && ./gradlew testDebugUnitTest --tests 'com.harleytg.puppyclicker.PuppyCodeCatalogTest'`
Expected: FAIL because schema-2 catalogue types do not exist.

- [ ] **Step 3: Implement schema-2 data classes, strict parser, exact salted SHA-256 hashing, status/flag/version checks, and bounded reward parsing**

Use the existing salt value for canonical migrated code hashes, but hash the exact input string. Recognize the approved internal flags and reject unknown/contradictory combinations. Treat `NO_REWARD_PREVIEW` as unsupported in the initial release.

- [ ] **Step 4: Re-run the focused unit tests**

Run: `cd App && ./gradlew testDebugUnitTest --tests 'com.harleytg.puppyclicker.PuppyCodeCatalogTest'`
Expected: PASS.

- [ ] **Step 5: Commit to main**

Commit message: `feat: add schema 2 Puppy Code catalogue`

---

### Task 2: Live GitHub claim authorization

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/StreamedRedeemCodes.kt`
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/StreamedRedeemCodesTest.kt`

**Interfaces:**
- Produces: `suspend fun refreshForClaim(context: Context): PuppyCodeRefreshResult` returning a validated snapshot plus authoritative HTTP response time for 200/304, or a failure that cannot authorize a claim.
- Consumes: schema-2 parser from Task 1.

- [ ] **Step 1: Replace fallback tests with live-authorization semantics**

```kotlin
@Test fun staleCacheCannotAuthorizeClaimByItself() {
    val result = StreamedRedeemCodes.claimAuthorizationForTest(liveCheckSucceeded = false)
    assertFalse(result.authorized)
}

@Test fun http304CanAuthorizePreviouslyValidatedCache() {
    val result = StreamedRedeemCodes.claimAuthorizationForTest(liveCheckSucceeded = true, notModified = true)
    assertTrue(result.authorized)
}
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run: `cd App && ./gradlew testDebugUnitTest --tests 'com.harleytg.puppyclicker.StreamedRedeemCodesTest'`
Expected: FAIL under the old fallback behavior.

- [ ] **Step 3: Implement claim-time refresh**

Use `HttpURLConnection`, conditional ETag requests, bounded download size, `Date` header capture, validated persistent cache, and fail-closed semantics. Background refresh may continue for display metadata, but `find()` alone must no longer authorize a new claim.

- [ ] **Step 4: Re-run focused tests**

Run: `cd App && ./gradlew testDebugUnitTest --tests 'com.harleytg.puppyclicker.StreamedRedeemCodesTest'`
Expected: PASS.

- [ ] **Step 5: Commit to main**

Commit message: `feat: require live GitHub validation for Puppy Codes`

---

### Task 3: Redemption history, atomic reward engine, and anti-abuse state

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyCodeRewards.kt`
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyCodeAntiAbuse.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt`
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyCodeRewardsTest.kt`
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyCodeAntiAbuseTest.kt`

**Interfaces:**
- Produces: `PuppyCodeHistoryEntry`, `PuppyCodePreview`, `RewardGrantEngine.preflight(...)`, `RewardGrantEngine.applyTo(...)`, `PuppyCodeAntiAbuse.recordInvalid(...)`, `PuppyCodeAntiAbuse.recordAttempt(...)`.
- ViewModel produces async `validatePuppyCode(rawCode)` and `claimPendingPuppyCode()` state transitions.

- [ ] **Step 1: Write failing reward/history/cooldown tests**

```kotlin
@Test fun unsupportedRewardDoesNotMutateState() {
    val before = V6GameState(treats = 100)
    val result = RewardGrantEngine.applyTo(before, listOf(PuppyCodeReward.Unknown("future")))
    assertFalse(result.success)
    assertEquals(before, result.state)
}

@Test fun fifthInvalidAttemptStartsThirtySecondCooldown() {
    var state = PuppyCodeAntiAbuse.State()
    repeat(5) { state = PuppyCodeAntiAbuse.recordInvalid(state, nowMs = 1_000L + it) }
    assertEquals(30_000L, state.cooldownUntilMs - 1_004L)
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `cd App && ./gradlew testDebugUnitTest --tests 'com.harleytg.puppyclicker.PuppyCodeRewardsTest' --tests 'com.harleytg.puppyclicker.PuppyCodeAntiAbuseTest'`
Expected: FAIL because the new engine/state types do not exist.

- [ ] **Step 3: Implement all-or-nothing next-state calculation and history migration**

Support current-state rewards first (`treats`, `puppy`, `upgrade_ticket`) and represent recognized future-facing `cosmetic`, `badge`, and `boost` reward types only where the current save model can safely persist them. If a recognized type cannot be persisted by this app version, preflight returns an update/incompatibility failure instead of partially granting.

- [ ] **Step 4: Integrate ViewModel validation/preview/claim and reset semantics**

Preserve `redeemedCodeIds` on Reset Progress. Clear redemption IDs/history, catalogue cache, and redeem cooldown data only on Erase All Data. Successful simple Treats-only codes may validate and claim in one ViewModel operation; all other bundles create a pending preview and require `claimPendingPuppyCode()`.

- [ ] **Step 5: Re-run focused tests**

Run: `cd App && ./gradlew testDebugUnitTest --tests 'com.harleytg.puppyclicker.PuppyCodeRewardsTest' --tests 'com.harleytg.puppyclicker.PuppyCodeAntiAbuseTest'`
Expected: PASS.

- [ ] **Step 6: Commit to main**

Commit message: `feat: add Puppy Code rewards history and anti abuse`

---

### Task 4: Rewards hub UI and navigation

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt`
- Optional create if Activity becomes unwieldy: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRewardsUi.kt`

**Interfaces:**
- Consumes: ViewModel claim/preview/history state from Task 3.
- Produces: V6 bottom nav `PLAY, CARE, SHOP, REWARDS, SETTINGS`; Shop internal Prestige section; Rewards Hub section cards and Puppy Codes screen.

- [ ] **Step 1: Replace V6 bottom navigation**

Change the V6 nav model to `Play · Care · Shop · Rewards · Settings` and remove the standalone Prestige destination.

- [ ] **Step 2: Move Prestige UI inside Shop**

Reuse the existing Prestige composable/state/actions rather than duplicating progression logic.

- [ ] **Step 3: Build mobile Rewards Hub**

Create large cards for Daily Rewards, Puppy Codes, Events, and Special Rewards. Reuse existing daily/event logic where available.

- [ ] **Step 4: Build Puppy Codes screen**

Add exact-entry field, Paste action, Redeem button, inline status, Active Promotions, Redeemed History, full reward preview, Claim Reward, full reveal for non-simple bundles, and compact success for Treats-only codes.

- [ ] **Step 5: Compile UI**

Run: `cd App && ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit to main**

Commit message: `feat: add Rewards hub and Puppy Codes UI`

---

### Task 5: Migrate live catalogue and sanitize public code documentation

**Files:**
- Modify: `assets/redeem-codes.json`
- Modify: `App/admin/PUPPY-CODES.md`
- Modify/delete obsolete compiled fallback behavior in: `App/app/src/main/java/com/harleytg/puppyclicker/LocalRedeemCodes.kt`

**Interfaces:**
- Consumes: schema-2 parser from Task 1.
- Produces: schema-2 live catalogue containing every current live reward ID with canonical exact-code hashes and no plaintext secret-code list in public documentation.

- [ ] **Step 1: Regenerate exact hashes for every currently live schema-1 entry**

Use the canonical plaintext spellings that were already publicly documented, preserve each reward `id`, migrate Treats/Puppy rewards into the `rewards` array, set status to `active`, and default rarity to omitted or `standard` unless intentionally special.

- [ ] **Step 2: Rewrite `PUPPY-CODES.md` as maintenance documentation**

Document hashing, schema/status/flags/reward semantics and the exact-match rule, but remove the plaintext code table.

- [ ] **Step 3: Remove claim authorization through `LocalRedeemCodes`**

Legacy local data may remain only if required for migration/tests, but new claims must not succeed from the APK without a live GitHub authorization.

- [ ] **Step 4: Run catalogue/unit tests**

Run: `cd App && ./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit to main**

Commit message: `feat: migrate Puppy Codes to schema 2`

---

### Task 6: Full verification

**Files:**
- Verify all files changed in Tasks 1-5.

**Interfaces:**
- Consumes the completed implementation.
- Produces a verified main-branch implementation ready for APK packaging.

- [ ] **Step 1: Run all JVM tests**

Run: `cd App && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build debug APK**

Run: `cd App && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL and a debug APK under `App/app/build/outputs/apk/debug/`.

- [ ] **Step 3: Inspect final main diff/commit history**

Confirm the standalone V6 Prestige tab is gone, Rewards is present, exact-match tests pass, no plaintext code table remains, schema is 2, and no network failure path grants a reward.

- [ ] **Step 4: Final verification commit only if verification required source/doc corrections**

Commit message: `fix: finalize Puppy Code rewards verification`
