# Puppy Ticket Overlay and Release Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ticket-found feedback compact, correctly anchored over the Play screen without layout shifts, then perform end-to-end generated-source/build/signing verification for the full repair.

**Architecture:** Replace the window-level `Popup`/fixed top offset with an overlapping child of the Play screen root `Box`. Keep the scrolling Play `Column` unchanged underneath the overlay, key the visibility timer to `ticketDropSerial` so a new drop restarts one banner, and use the existing reduced-motion composition local for near-instant transitions when motion is reduced.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI tests, JUnit 4, existing generated-source Python patch pipeline, Gradle/Android APK signing.

**Spec:** `docs/superpowers/specs/2026-09-09-puppy-clicker-save-onboarding-ui-repair-design.md`

## Global Constraints

- Ticket feedback must never change the measured position of wallet, needs row, puppy card, or bottom navigation.
- Banner copy is compact: `<Rarity> Ticket +1` with `Ticket Upgrades` secondary text.
- Visibility target is approximately 2.5-3 seconds.
- Repeated drops restart/update one banner rather than stacking multiple banners.
- Reduced-motion preferences remain respected.
- No window-level fixed offset determines placement.
- Canonical V6 source and `patch_puppy_ux.py` must agree.
- Final verification covers JVM tests, generated source, debug/release assembly, and v4 signing output when permanent signing secrets are configured.

## File Structure

- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppyTicketOverlay.kt` — compact parent-anchored overlay; no `Popup`.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt` — root `Box` host in `V6Play` and 2.75-second timer.
- Modify `App/tools/patch_puppy_ux.py` — remove the now-obsolete transform that replaces the old in-flow ticket block.
- Modify `App/app/build.gradle.kts` — add Compose UI instrumentation dependency if absent.
- Create `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyTicketOverlayTest.kt` — compact copy and sibling-layout stability.
- Verify `.github/workflows/android.yml` and release signing after all three repair plans land.

---

### Task 1: Make `V6TicketDropOverlay` compact, parent-anchored, and reduced-motion aware

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyTicketOverlay.kt`
- Modify: `App/app/build.gradle.kts` dependency block if needed
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyTicketOverlayTest.kt`

**Interfaces:**
- Produces: `@Composable internal fun V6TicketDropOverlay(visible: Boolean, rarity: TicketRarity?, modifier: Modifier = Modifier)`.
- Removes: `Popup`, `PopupProperties`, `LocalDensity`, and `IntOffset` from ticket rendering.

- [ ] **Step 1: Add Compose UI test dependency if absent**

```kotlin
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
```

The existing Compose BOM supplies the version.

- [ ] **Step 2: Write the Compose regression test before changing the overlay**

```kotlin
package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PuppyTicketOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun overlayDoesNotMoveUnderlyingContentAndUsesCompactCopy() {
        val visible = mutableStateOf(false)
        compose.setContent {
            Box {
                Column {
                    Box(Modifier.width(200.dp).height(40.dp).testTag("wallet"))
                    Box(Modifier.width(200.dp).height(100.dp).testTag("play-card"))
                }
                V6TicketDropOverlay(
                    visible = visible.value,
                    rarity = TicketRarity.COMMON,
                    modifier = Modifier.testTag("ticket-overlay")
                )
            }
        }

        val before = compose.onNodeWithTag("play-card").getUnclippedBoundsInRoot()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("play-card").getUnclippedBoundsInRoot()

        assertEquals(before, after)
        compose.onNodeWithTag("ticket-overlay").assertIsDisplayed()
        compose.onNodeWithText("Common Ticket +1").assertIsDisplayed()
        compose.onNodeWithText("Ticket Upgrades").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Compile the instrumentation test before implementation**

```bash
gradle --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

Expected before the overlay signature is changed: the new `modifier` argument is unresolved, proving the test is red for the intended interface change.

- [ ] **Step 4: Rewrite `V6TicketDropOverlay` without `Popup`**

```kotlin
@Composable
internal fun V6TicketDropOverlay(
    visible: Boolean,
    rarity: TicketRarity?,
    modifier: Modifier = Modifier
) {
    if (rarity == null) return
    val reducedMotion = com.harleytg.puppyclicker.ui.theme.LocalPuppyReducedMotion.current
    val duration = if (reducedMotion) 1 else 160

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.widthIn(max = 286.dp),
        enter = fadeIn(tween(duration)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(duration)),
        exit = fadeOut(tween(duration)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(duration))
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(rarity.emoji, fontSize = 22.sp)
                Spacer(Modifier.size(8.dp))
                Column {
                    Text("${rarity.displayName} Ticket +1", fontWeight = FontWeight.Black)
                    Text("Ticket Upgrades", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
```

Import `androidx.compose.animation.core.tween`. Delete imports for `LocalDensity`, `IntOffset`, `Popup`, and `PopupProperties`.

- [ ] **Step 5: Compile instrumentation source after implementation**

```bash
gradle --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Run the test on a connected emulator/device when available**

```bash
gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

Expected: `PuppyTicketOverlayTest` PASS.

- [ ] **Step 7: Commit**

```bash
git add App/app/build.gradle.kts \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppyTicketOverlay.kt \
  App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyTicketOverlayTest.kt
git commit -m "test: anchor ticket feedback inside play screen"
```

---

### Task 2: Host the banner over Play content and remove the obsolete generator transform

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt` in `V6Play`
- Modify: `App/tools/patch_puppy_ux.py` in `patch_activity`

**Interfaces:**
- Consumes: `V6TicketDropOverlay(visible, rarity, modifier)`.
- Produces: one Play-root overlay whose appearance/disappearance cannot consume `Column` height.

- [ ] **Step 1: Change the existing drop timer to 2.75 seconds**

```kotlin
LaunchedEffect(state.ticketDropSerial) {
    if (state.ticketDropSerial > 0 && state.lastTicketDrop != null) {
        ticketVisible = true
        if (state.hapticsEnabled) performV6Haptic(context, true)
        delay(2_750)
        ticketVisible = false
    }
}
```

A new serial cancels/restarts this effect, so repeated drops update one banner rather than stack.

- [ ] **Step 2: Wrap the existing Play `Column` in a root `Box` and delete the old in-flow banner**

Use this structure while leaving the existing wallet, needs row, puppy card, click handling, cooldown text, and bottom spacing unchanged inside the `Column`:

```kotlin
Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        V6Header("Puppy Clicker", "${state.puppyName} · ${state.mood}")
        Spacer(Modifier.height(10.dp))
        V6Wallet(state)
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            V6NeedPill("💖", state.happiness, Modifier.weight(1f))
            V6NeedPill("🍖", state.fullness, Modifier.weight(1f))
            V6NeedPill("⚡", state.energy, Modifier.weight(1f))
            V6NeedPill("🫧", state.cleanliness, Modifier.weight(1f))
        }

        // Existing puppy Surface and remaining Play content follow unchanged.
    }

    V6TicketDropOverlay(
        visible = ticketVisible,
        rarity = state.lastTicketDrop,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 142.dp, start = 24.dp, end = 24.dp)
    )
}
```

The only removed canonical block is the old `AnimatedVisibility(ticketVisible)` banner and its conditional spacer.

- [ ] **Step 3: Delete only the obsolete `"ticket overlay"` replacement from `patch_puppy_ux.py`**

Remove the `replace_once(...)` call that searches for the old in-flow ticket `AnimatedVisibility` block and replaces it with `V6TicketDropOverlay(ticketVisible, state.lastTicketDrop)`.

Keep all other transforms in `patch_activity` unchanged.

- [ ] **Step 4: Clean-build generated source and app**

```bash
gradle --no-daemon clean :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
```

Expected: PASS with no `ticket overlay: expected one integration anchor` error.

- [ ] **Step 5: Verify generated V6 source has one host call and no old copy**

```bash
grep -n "V6TicketDropOverlay" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt
! grep -n "Added to Ticket Upgrades" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt
```

Expected: one overlay host call; no old banner text.

- [ ] **Step 6: Run connected instrumentation tests**

```bash
gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

Expected: ticket overlay and save-crypto instrumentation tests PASS on the device/emulator.

- [ ] **Step 7: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt \
  App/tools/patch_puppy_ux.py
git commit -m "fix: keep ticket drops from shifting play layout"
```

---

### Task 3: End-to-end repair and signing verification

**Files:**
- Verify: all source/tests changed by the three repair plans
- Verify: `.github/workflows/android.yml`
- Verify: `App/app/build.gradle.kts`

**Interfaces:**
- Consumes: save-crypto plan, onboarding/profile/birthday/branding plan, and ticket-overlay plan.
- Produces: build/test/signing evidence; no empty verification commit.

- [ ] **Step 1: Run all JVM tests**

```bash
gradle --no-daemon :app:testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 2: Rebuild generated sources from clean state**

```bash
gradle --no-daemon clean :app:generateProtectedPuppySources --stacktrace
```

Expected: every Python transform completes successfully.

- [ ] **Step 3: Verify removed UI does not reappear in generated source**

```bash
! grep -R "Puppy Coins\|15,250\|birthdayYear\|initialYear" app/build/generated/protected-puppies/source
! grep -n "StreamedPupEyeBranding" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker/ui/theme/Theme.kt
```

Expected: no matches.

- [ ] **Step 4: Compile debug, Android test, and release artifacts**

```bash
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Verify configured signing still requests all schemes including v4**

`App/app/build.gradle.kts` must retain:

```kotlin
enableV1Signing = true
enableV2Signing = true
enableV3Signing = true
enableV4Signing = true
```

When permanent signing secrets are configured, verify:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/*.apk
find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.idsig' -size +0c
```

Expected: APK verification succeeds and a non-empty v4 `.idsig` sidecar is found.

- [ ] **Step 6: Perform the manual device acceptance pass**

```text
Fresh setup
- Step 2: Local Profile plus disabled Discord and Website cards.
- Step 3: Month and Day only; February 29 can be selected.
- Step 4: no Puppy Coins, 15,250, or BUY preview.
- PupEye top-right badge exists during setup.

Completed setup
- No floating PupEye badge on Play, Care, Shop, Prestige, or normal Settings.
- Dedicated PupEye Settings/security branding still displays.
- Ticket drop shows one compact <Rarity> Ticket +1 overlay for about 2.75 seconds.
- Needs row and puppy card do not move when the ticket overlay appears/disappears.
- Developer Console does not repeat Caller-provided IV not permitted.
- Android/data encrypted `.pup` mirror exists and verifies after relaunch.
```

- [ ] **Step 7: Do not claim completion until all evidence is green**

If any verification fails, return to systematic debugging for that failure, implement one root-cause fix, and rerun Steps 1-6. Do not create an empty commit when all checks pass unchanged.

---

## Plan Self-Review

- Spec coverage: compact ticket copy, parent-relative placement, no layout shift, 2.75-second lifetime, repeat-drop behavior, reduced motion, generated-source integrity, build coverage, and v4 signing verification are covered.
- Placeholder scan: all intended source edits and commands are concrete; retained Play content is explicitly constrained to remain unchanged rather than being reimplemented.
- Type consistency: the three-argument overlay signature introduced in Task 1 is used unchanged in Task 2.
- Architecture check: no window-level `Popup` or fixed window offset remains.
