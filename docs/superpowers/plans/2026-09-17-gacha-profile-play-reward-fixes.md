# Gacha, Profile Progression, and Play Reward Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent duplicate Gacha pulls, move XP/Achievements to Profile Settings, and hide the Play next-reward strip when no unclaimed daily goal exists.

**Architecture:** Keep reward eligibility in pure domain helpers that JVM tests can exercise, and keep the generated Android 1.0 UI patch responsible only for relocating existing progression cards. Gacha currency mutation remains in the ViewModel and is guarded by an empty unowned pool before any charge.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 4, Python generated-source patch tooling, Gradle/GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-17-gacha-profile-play-reward-fixes-design.md`

## Global Constraints

- Do not alter permanent signing identity or package ID.
- Gacha may only return unowned, non-redeem-only puppies.
- Profile progression must not remain on Rewards.
- Play reward UI must use current daily goal data and disappear when all goals are claimed.
- Preserve existing 1.0 build compatibility fixes from PR #10.

---

### Task 1: Gacha duplicate prevention

**Files:**
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyGachaEngineTest.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyGacha.kt`

**Interfaces:**
- Consumes: `PuppyGachaEngine.eligiblePuppies(styles, unlocked)`.
- Produces: `PuppyGachaEngine.pullPool(styles, unlocked)` returning only unowned eligible puppies.

- [ ] **Step 1: Write the failing test**

Replace the completed-collection fallback assertion with:

```kotlin
@Test
fun pullPoolReturnsEmptyWhenEveryEligiblePuppyIsAlreadyOwned() {
    val styles = listOf(buddy, sunny, special)
    assertTrue(
        PuppyGachaEngine.pullPool(styles, setOf(buddy.id, sunny.id)).isEmpty()
    )
}
```

- [ ] **Step 2: Run the JVM test and verify RED**

Run: `gradle --no-daemon :app:testDebugUnitTest --tests '*PuppyGachaEngineTest*'`
Expected: FAIL because `pullPool()` currently falls back to owned puppies.

- [ ] **Step 3: Implement the minimal fix**

```kotlin
fun pullPool(styles: List<PuppyStyle>, unlocked: Set<String>): List<PuppyStyle> =
    eligiblePuppies(styles, unlocked)
```

Update the screen's completed-collection copy and button enablement to use the remaining/unowned pool.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same Gradle command; expected PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: prevent duplicate puppy gacha pulls`

### Task 2: Dynamic Play next-reward selection

**Files:**
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyMonthlyRewardsTest.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyMonthlyRewards.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyMainScreensRevamp.kt`

**Interfaces:**
- Produces: `PuppyMonthlyRewards.nextUnclaimedGoal(goals, claimedIds): PuppyRewardGoal?`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test
fun nextUnclaimedGoalSkipsClaimedGoals() {
    val next = PuppyMonthlyRewards.nextUnclaimedGoal(listOf(first, second), setOf(first.id))
    assertEquals(second.id, next?.id)
}

@Test
fun nextUnclaimedGoalReturnsNullWhenAllGoalsAreClaimed() {
    val next = PuppyMonthlyRewards.nextUnclaimedGoal(listOf(first, second), setOf(first.id, second.id))
    assertNull(next)
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `gradle --no-daemon :app:testDebugUnitTest --tests '*PuppyMonthlyRewardsTest*'`
Expected: compile/test failure because helper does not exist.

- [ ] **Step 3: Implement minimal helper and UI**

```kotlin
fun nextUnclaimedGoal(goals: List<PuppyRewardGoal>, claimedIds: Set<String>): PuppyRewardGoal? =
    goals.firstOrNull { it.id !in claimedIds }
```

In `PuppyRevampedPlayScreen`, collect the reward schedule, resolve today's goals, obtain the next unclaimed goal, and render the strip only inside `nextGoal?.let { ... }` using the goal's own target/reward/title/emoji.

- [ ] **Step 4: Re-run focused tests and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: hide exhausted play reward strip`

### Task 3: Move XP and Achievements to Profile Settings

**Files:**
- Modify: `App/tools/test_patch_android_1_0_runner.py`
- Modify: `App/tools/patch_android_1_0_completion.py`

**Interfaces:**
- `patch_main_screens(source)` must no longer inject `PuppyPlayerProgressCard` or `PuppyAchievementsSection` into Rewards.
- `patch_settings(source)` must inject the current game-state observer and progression cards into `ProfileSettings` and append the two composables there.

- [ ] **Step 1: Add a failing patch regression**

The test fixture must assert:

```python
self.assertNotIn("PuppyPlayerProgressCard(state)", patched_rewards)
self.assertNotIn("PuppyAchievementsSection(state)", patched_rewards)
self.assertIn("PuppyPlayerProgressCard(gameState)", patched_settings)
self.assertIn("PuppyAchievementsSection(gameState)", patched_settings)
```

- [ ] **Step 2: Run Python patch tests and verify RED**

Run: `python3 -m unittest tools.test_patch_puppy_exchange tools.test_patch_android_1_0_runner`
Expected: FAIL because progression currently patches Rewards.

- [ ] **Step 3: Implement minimal patch relocation**

Make `patch_main_screens` return source unchanged for progression. In `patch_settings`, collect `vm.state`, insert both cards after the local-profile card, and append the existing card composables to the settings generated source.

- [ ] **Step 4: Re-run Python tests and verify GREEN**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: move progression UI into profile settings`

### Task 4: Full verification and signed APK

**Files:**
- No production source changes unless verification exposes a regression.

- [ ] **Step 1:** Run Python patch regression tests.
- [ ] **Step 2:** Run all JVM regression tests.
- [ ] **Step 3:** Compile Android instrumentation tests.
- [ ] **Step 4:** Build the unsigned release APK.
- [ ] **Step 5:** Sign with the existing permanent PKCS12 identity and generate the detached v4 `.idsig`.
- [ ] **Step 6:** Verify APK signer certificate SHA-256 and publish APK + `.idsig` bundle.
