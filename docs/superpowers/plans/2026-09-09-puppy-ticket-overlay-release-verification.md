# Puppy Ticket Overlay and Release Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ticket-found feedback compact, correctly anchored over the Play screen without layout shifts, then perform end-to-end generated-source/build/signing verification for the full repair.

**Architecture:** Stop using a window-level `Popup` with a fixed top offset. Render the ticket banner as an overlapping child of the Play screen's root `Box`, keep the normal scrolling `Column` untouched underneath it, and let repeated ticket drops restart the existing visibility timer rather than stacking banners.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI tests, JUnit 4, existing generated-source Python patch pipeline, Gradle/Android APK signing.

**Spec:** `docs/superpowers/specs/2026-09-09-puppy-clicker-save-onboarding-ui-repair-design.md`

## Global Constraints

- Ticket feedback must never change the measured position of the wallet, needs row, puppy card, or bottom navigation.
- Banner copy is compact: `<Rarity> Ticket +1` with `Ticket Upgrades` as secondary text.
- Visibility target is approximately 2.5-3 seconds.
- Repeated drops restart/update one banner rather than stacking multiple banners.
- Reduced-motion preferences remain respected.
- No window-level fixed pixel/dp offset should determine ticket placement.
- Canonical source and generated-source patch logic must agree.
- Final verification must cover JVM tests, generated source, debug/release assembly, and v4 signing output when permanent signing secrets are configured.

## File Structure

- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppyTicketOverlay.kt` — compact, parent-anchored overlay composable; remove `Popup`.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt` — root `Box` host in `V6Play`, 2.75-second timer, overlay placement after normal content.
- Modify `App/tools/patch_puppy_ux.py` — remove the obsolete transform that converts the old in-flow banner to `V6TicketDropOverlay`, because canonical V6 source will already contain the final host.
- Modify `App/app/build.gradle.kts` — add Compose UI instrumentation test dependency if not already added by an earlier plan.
- Create `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyTicketOverlayTest.kt` — verify compact copy and sibling layout stability.
- Verify `.github/workflows/android.yml` and release artifact signing behavior after all repair plans land.

---

### Task 1: Make the overlay composable parent-anchored and compact

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyTicketOverlay.kt`
- Modify: `App/app/build.gradle.kts` dependency block if `ui-test-junit4` is absent
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyTicketOverlayTest.kt`

**Interfaces:**
- Produces: `@Composable internal fun V6TicketDropOverlay(visible: Boolean, rarity: TicketRarity?, modifier: Modifier = Modifier)`.
- Removes: window-level `Popup`, `PopupProperties`, `LocalDensity`, and `IntOffset` dependencies from the overlay.

- [ ] **Step 1: Add Compose UI test dependency if missing**

In `App/app/build.gradle.kts`:

```kotlin
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
```

The existing Compose BOM supplies the version.

- [ ] **Step 2: Write the Compose instrumentation test before changing the overlay**

Create:

```kotlin
package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.testTag
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PuppyTicketOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun overlayDoesNotMoveUnderlyingContent() {
        compose.setContent {
            Box {
                Column {
                    Box(Modifier.width(200.dp).height(40.dp).testTag("wallet"))
                    Box(Modifier.width(200.dp).height(100.dp).testTag("play-card"))
                }
                V6TicketDropOverlay(
                    visible = true,
                    rarity = TicketRarity.COMMON,
                    modifier = Modifier.testTag("ticket-overlay")
                )
            }
        }

        val walletTop = compose.onNodeWithTag("wallet").getUnclippedBoundsInRoot().top
        val playTop = compose.onNodeWithTag("play-card").getUnclippedBoundsInRoot().top
        compose.onNodeWithTag("ticket-overlay").assertIsDisplayed()
        assertEquals(40.dp, playTop - walletTop)
    }
}
```

Use `androidx.compose.ui.platform.testTag` only; remove any accidental `foundation.layout.testTag` import if the IDE adds it.

- [ ] **Step 3: Compile the test against the current Popup implementation**

```bash
gradle --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

Expected: compilation succeeds after the correct `testTag` import is used. The test establishes the parent-overlay contract that the rewritten composable must satisfy.

- [ ] **Step 4: Rewrite `V6TicketDropOverlay` without `Popup`**

Use this shape:

```kotlin
@Composable
internal fun V6TicketDropOverlay(
    visible: Boolean,
    rarity: TicketRarity?,
    modifier: Modifier = Modifier
) {
    if (rarity == null) return

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.widthIn(max = 286.dp),
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f)
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

Delete imports for `LocalDensity`, `IntOffset`, `Popup`, and `PopupProperties`.

- [ ] **Step 5: Run instrumentation compilation**

```bash
gradle --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Commit the overlay component and test**

```bash
git add App/app/build.gradle.kts \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppyTicketOverlay.kt \
  App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppyTicketOverlayTest.kt
git commit -m "test: anchor ticket feedback inside play screen"
```

---

### Task 2: Host ticket feedback over the Play content without layout movement

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt` in `V6Play`
- Modify: `App/tools/patch_puppy_ux.py` in `patch_activity`

**Interfaces:**
- Consumes: `V6TicketDropOverlay(visible, rarity, modifier)` from Task 1.
- Produces: Play root `Box` containing the scrolling game `Column` and independently aligned overlay.

- [ ] **Step 1: Increase the existing display timer to 2.75 seconds**

Inside `LaunchedEffect(state.ticketDropSerial)`:

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

Because `LaunchedEffect` is keyed by `ticketDropSerial`, a new drop cancels/restarts the previous timer instead of stacking another banner.

- [ ] **Step 2: Replace the in-flow ticket block with a root overlay host**

Change the `V6Play` root from a single `Column` to:

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

        // Keep the remainder of the existing puppy card/content unchanged.
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

The banner overlaps the area immediately below the wallet/header region; it does not occupy `Column` height.

- [ ] **Step 3: Remove the obsolete ticket replacement from `patch_puppy_ux.py`**

Delete only the `replace_once(...)` call labeled `"ticket overlay"` that searches for the old in-flow `AnimatedVisibility` block and replaces it with `V6TicketDropOverlay(...)`.

Do not remove the title/attention, Settings subtitle, notification/save Settings, or seasonal signup transforms.

- [ ] **Step 4: Force generated-source reconstruction to prove the patch pipeline accepts the new canonical V6Play**

```bash
gradle --no-daemon clean :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
```

Expected: no `ticket overlay: expected one integration anchor` failure.

- [ ] **Step 5: Verify generated V6 source contains one overlay call and no old in-flow ticket banner**

```bash
grep -n "V6TicketDropOverlay" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt
! grep -n "Added to Ticket Upgrades" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt
```

Expected: exactly one overlay host call; no old banner copy.

- [ ] **Step 6: Run Compose instrumentation tests on a connected device/emulator when available**

```bash
gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

Expected: `PuppyTicketOverlayTest` PASS along with crypto instrumentation tests from the save-repair plan.

- [ ] **Step 7: Commit Play host and patch-pipeline synchronization**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt \
  App/tools/patch_puppy_ux.py
git commit -m "fix: keep ticket drops from shifting play layout"
```

---

### Task 3: End-to-end repair verification and signed release checks

**Files:**
- Verify: all files changed by the three repair plans
- Verify: `.github/workflows/android.yml`
- No source change unless a failing verification identifies a specific regression.

**Interfaces:**
- Consumes: save crypto plan, onboarding/profile/birthday/branding plan, and ticket overlay plan.
- Produces: evidence that the repaired repository builds and the release signing configuration still requests v1/v2/v3/v4 signatures.

- [ ] **Step 1: Run all JVM regression tests**

```bash
gradle --no-daemon :app:testDebugUnitTest --stacktrace
```

Expected: PASS.

- [ ] **Step 2: Rebuild generated sources from a clean tree**

```bash
gradle --no-daemon clean :app:generateProtectedPuppySources --stacktrace
```

Expected: all Python transforms complete successfully.

- [ ] **Step 3: Verify removed UI does not return in generated source**

```bash
! grep -R "Puppy Coins" app/build/generated/protected-puppies/source
! grep -R "birthdayYear\|initialYear" app/build/generated/protected-puppies/source
! grep -n "StreamedPupEyeBranding" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker/ui/theme/Theme.kt
```

Expected: all three commands succeed with no matches.

- [ ] **Step 4: Compile debug, Android test, and release artifacts**

```bash
gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Verify signing configuration remains v4-enabled**

Confirm `App/app/build.gradle.kts` still contains all four flags in the configured signing block:

```kotlin
enableV1Signing = true
enableV2Signing = true
enableV3Signing = true
enableV4Signing = true
```

When permanent signing secrets are available, verify the produced APK and `.idsig` using the workflow's existing command:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/*.apk
```

Expected: signature verification passes and a non-empty `.idsig` v4 sidecar exists.

- [ ] **Step 6: Manual device acceptance pass**

Verify in one fresh-setup run and one completed-setup run:

```text
Fresh setup:
- Step 2 shows Local Profile + disabled Discord/Website account cards.
- Step 3 shows Month + Day only.
- Step 4 contains no Puppy Coins/BUY preview.
- PupEye top-right badge is visible only during setup.

Completed setup:
- No floating top-right PupEye badge on Play/Care/Shop/Prestige/normal Settings.
- Dedicated PupEye Settings section still shows streamed branding.
- Ticket drops display one compact overlay without moving the needs row or puppy card.
- Developer Console does not repeat Caller-provided IV not permitted warnings.
- Android/data encrypted save mirror exists and survives relaunch.
```

- [ ] **Step 7: Record final verification commit only if verification itself required source fixes**

For a specific failing regression, fix only that root cause, rerun Steps 1-6, then commit the exact affected files with a scoped message. If all verification passes without source changes, do not create an empty commit.

---

## Plan Self-Review

- Spec coverage: ticket placement, compact copy, 2.5-3 second lifetime, repeat-drop behavior, layout stability, generated-source integrity, full build, and v4 release signing verification are covered.
- Placeholder scan: every source change and verification command is explicit.
- Type consistency: the overlay signature is defined once in Task 1 and used unchanged in Task 2.
- Architecture check: no fixed window-level Popup offset remains; overlay positioning is relative to the Play root.
