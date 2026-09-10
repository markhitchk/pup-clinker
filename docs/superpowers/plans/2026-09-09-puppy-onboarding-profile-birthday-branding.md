# Puppy Onboarding, Birthday, and PupEye Branding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign setup Step 2, make birthdays month/day-only, remove fake Puppy Coins UI, and limit the floating top-right PupEye badge to setup while keeping dedicated PupEye security branding.

**Architecture:** Add one pure recurring-birthday validator shared by preferences, onboarding, and Settings. Migrate the preference API in a build-safe sequence, keep onboarding at six steps, keep the local username path active while Discord/Website remain disabled future connections, remove economic placeholder previews, and move the floating PupEye overlay from the global theme into the onboarding root.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, SharedPreferences/StateFlow, JUnit 4, existing generated-source Python patch pipeline.

**Spec:** `docs/superpowers/specs/2026-09-09-puppy-clicker-save-onboarding-ui-repair-design.md`

## Global Constraints

- Onboarding remains six steps.
- Step 2 must work with a local username; Discord and Website authentication remain Coming Soon.
- Birthday stores and validates only month/day in the finished implementation.
- February 29 is a valid recurring birthday without a year.
- Existing month/day data survives upgrades even when an old birthday year exists.
- Setup and Settings use identical birthday validity rules.
- Puppy Clicker has no Puppy Coins economy; setup and Settings previews must not imply one.
- The top-right PupEye badge may appear during setup but must not float over Play/Care/Shop/Prestige/normal Settings after setup.
- Dedicated PupEye protection surfaces and streamed PupEye branding remain available.
- Canonical source changes must survive `generateProtectedPuppySources` and the current Python patch chain.

## File Structure

- Create `App/app/src/main/java/com/harleytg/puppyclicker/PuppyBirthday.kt` — pure recurring month/day validation.
- Create `App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt` — calendar edge cases.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt` — month/day preference contract and legacy-year cleanup.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt` — profile layout, month/day birthday UI, setup-only PupEye badge, non-economic theme preview.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt` — month/day birthday editor and non-economic live preview.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/Theme.kt` — remove global PupEye overlay.
- Verify `App/tools/patch_settings_setup_revamp.py`, `App/tools/patch_settings_setup_revamp_runner.py`, and `App/tools/patch_puppy_ux.py`; their known anchors are not intentionally changed by this plan.

---

### Task 1: Add recurring month/day birthday validation

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyBirthday.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt`

**Interfaces:**
- Produces: `internal object PuppyBirthday` with `maxDay(month: Int): Int` and `isValid(month: Int, day: Int): Boolean`.

- [ ] **Step 1: Write the failing JVM test**

```kotlin
package com.harleytg.puppyclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyBirthdayTest {
    @Test fun januaryFirstIsValid() = assertTrue(PuppyBirthday.isValid(1, 1))
    @Test fun aprilThirtyFirstIsInvalid() = assertFalse(PuppyBirthday.isValid(4, 31))
    @Test fun februaryTwentyNinthIsValidWithoutYear() = assertTrue(PuppyBirthday.isValid(2, 29))
    @Test fun februaryThirtiethIsInvalid() = assertFalse(PuppyBirthday.isValid(2, 30))
    @Test fun invalidMonthIsRejected() = assertFalse(PuppyBirthday.isValid(0, 1))
}
```

- [ ] **Step 2: Verify the test is red**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyBirthdayTest --stacktrace
```

Expected: unresolved `PuppyBirthday`.

- [ ] **Step 3: Implement the validator**

```kotlin
package com.harleytg.puppyclicker

internal object PuppyBirthday {
    fun maxDay(month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> 29
        else -> 0
    }

    fun isValid(month: Int, day: Int): Boolean {
        val maximum = maxDay(month)
        return maximum > 0 && day in 1..maximum
    }
}
```

- [ ] **Step 4: Verify the test is green**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyBirthdayTest --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyBirthday.kt App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt
git commit -m "test: define recurring birthday validation"
```

---

### Task 2: Add a build-safe month/day preference API

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt`

**Interfaces:**
- Consumes: `PuppyBirthday.isValid(month, day)`.
- Produces: `setBirthday(context: Context, month: Int, day: Int): Boolean`.
- Temporarily retains the existing four-argument birthday setter so current UI callers compile until Task 3.

- [ ] **Step 1: Make `hasBirthday` year-independent**

```kotlin
val hasBirthday: Boolean
    get() = PuppyBirthday.isValid(birthdayMonth, birthdayDay)
```

Keep `birthdayYear` temporarily in `PuppyUiState` for source compatibility in this task.

- [ ] **Step 2: Add the new month/day setter**

```kotlin
fun setBirthday(context: Context, month: Int, day: Int): Boolean {
    if (!PuppyBirthday.isValid(month, day)) return false
    edit(context) {
        putInt(KEY_BIRTHDAY_MONTH, month)
        putInt(KEY_BIRTHDAY_DAY, day)
        remove(KEY_BIRTHDAY_YEAR)
    }
    return true
}
```

- [ ] **Step 3: Mark the old setter transitional but keep its current behavior**

```kotlin
@Deprecated("Use month/day birthday storage")
fun setBirthday(context: Context, month: Int, day: Int, year: Int): Boolean {
    val today = LocalDate.now()
    val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return false
    if (date.isAfter(today) || year < today.year - 120) return false
    edit(context) {
        putInt(KEY_BIRTHDAY_MONTH, month)
        putInt(KEY_BIRTHDAY_DAY, day)
        putInt(KEY_BIRTHDAY_YEAR, year)
    }
    return true
}
```

- [ ] **Step 4: Test and compile**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt
git commit -m "feat: add month day birthday preference API"
```

---

### Task 3: Migrate all birthday UI and remove active year state

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt` — `BirthdayStep`.
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt` — birthday editor call and `BirthdayEditorDialog`.
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt` — final state/read/reset/migration cleanup.

**Interfaces:**
- Consumes: `PuppyBirthday.maxDay/isValid` and two-argument birthday payload `(month, day)`.
- Produces: no active `birthdayYear` property and `BirthdayEditorDialog(initialMonth: Int, initialDay: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit)`.

- [ ] **Step 1: Convert onboarding Step 3 to Month + Day only**

```kotlin
var month by rememberSaveable { mutableIntStateOf(ui.birthdayMonth.coerceIn(0, 12)) }
var day by rememberSaveable { mutableIntStateOf(ui.birthdayDay.coerceIn(0, 31)) }
val maxDay = PuppyBirthday.maxDay(month).takeIf { it > 0 } ?: 31
if (day > maxDay) day = 0
val valid = PuppyBirthday.isValid(month, day)
```

Render Month and Day pickers only. Save with:

```kotlin
if (PuppyUiPreferences.setBirthday(context, month, day)) {
    vm.setSeasonalBirthday(month, day)
    onNext()
}
```

Use privacy copy that says month/day stays local and is not publicly displayed.

- [ ] **Step 2: Change the Settings birthday editor call**

```kotlin
BirthdayEditorDialog(
    initialMonth = ui.birthdayMonth,
    initialDay = ui.birthdayDay,
    onDismiss = { birthdayEditor = false },
    onSave = { month, day ->
        if (PuppyUiPreferences.setBirthday(context, month, day)) {
            vm.setSeasonalBirthday(month, day)
            birthdayEditor = false
        }
    }
)
```

- [ ] **Step 3: Rewrite `BirthdayEditorDialog` with the existing Settings `BirthdayPicker` helper**

```kotlin
@Composable
internal fun BirthdayEditorDialog(
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var month by rememberSaveable { mutableIntStateOf(initialMonth.coerceIn(0, 12)) }
    var day by rememberSaveable { mutableIntStateOf(initialDay.coerceIn(0, 31)) }
    val maxDay = PuppyBirthday.maxDay(month).takeIf { it > 0 } ?: 31
    if (day > maxDay) day = 0
    val valid = PuppyBirthday.isValid(month, day)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Birthday", fontWeight = FontWeight.Black) },
        text = {
            Column {
                BirthdayPicker(
                    if (month == 0) "Month" else setupMonthName(month),
                    (1..12).map { it to setupMonthName(it) }
                ) { selected ->
                    month = selected
                    if (day > PuppyBirthday.maxDay(selected)) day = 0
                }
                Spacer(Modifier.height(8.dp))
                BirthdayPicker(
                    if (day == 0) "Day" else day.toString(),
                    (1..maxDay).map { it to it.toString() }
                ) { selected -> day = selected }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(month, day) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

If `setupMonthName` is private to onboarding, use the Settings file's existing month-label expression based on `Month.of(value).getDisplayName(TextStyle.FULL, Locale.getDefault())`; do not call a private function across files.

- [ ] **Step 4: Remove final active year state from preferences**

Remove `birthdayYear`, `birthdayDate`, and the transitional four-argument setter. Keep `KEY_BIRTHDAY_YEAR` only for cleanup.

After `migrateOnce(app, store)` in `ensure`:

```kotlin
if (store.contains(KEY_BIRTHDAY_YEAR)) {
    store.edit().remove(KEY_BIRTHDAY_YEAR).apply()
}
```

Use:

```kotlin
fun clearBirthday(context: Context) = edit(context) {
    remove(KEY_BIRTHDAY_MONTH)
    remove(KEY_BIRTHDAY_DAY)
    remove(KEY_BIRTHDAY_YEAR)
}
```

`resetInterfaceSettings` preserves only month/day; `migrateOnce` copies legacy month/day without writing year `0`; `read` populates only month/day.

- [ ] **Step 5: Remove unused year/date imports and verify**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
! grep -R "birthdayYear\|initialYear" app/src/main/java app/build/generated/protected-puppies/source
```

Expected: PASS and no active year references.

- [ ] **Step 6: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt
git commit -m "feat: use month day birthdays everywhere"
```

---

### Task 4: Redesign Step 2, remove fake currency previews, and scope PupEye to onboarding

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt` — root flow, `ProfileStep`, `SetupAppearanceStep`.
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt` — `AppearancePreview`.
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/Theme.kt` — `PuppyClickerTheme`.

**Interfaces:**
- Produces: local-profile Step 2 plus disabled Discord/Website cards.
- Produces: non-economic appearance previews.
- Produces: onboarding-owned top-right PupEye badge; global theme draws none.

- [ ] **Step 1: Replace Step 2 content**

```kotlin
SetupCard("YOUR PROFILE", "👤") {
    Text("Local Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    Text(
        "Your local profile is stored on this device and linked to your protected Puppy Clicker save.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = username,
        onValueChange = { username = it.take(24) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Player username") },
        supportingText = { Text("Stored on this device") },
        singleLine = true
    )
    Spacer(Modifier.height(16.dp))
    Text("Connect an account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    FutureAccountCard("Discord", "Coming Soon")
    Spacer(Modifier.height(8.dp))
    FutureAccountCard("Website", "Coming Soon")
    Spacer(Modifier.height(14.dp))
    SetupNavigation(onBack, "Continue", normalized.isNotBlank()) {
        PuppyPlayerIdentity.setUsername(context, username)
        onNext()
    }
}
```

Add:

```kotlin
@Composable
private fun FutureAccountCard(provider: String, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(provider, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}
```

- [ ] **Step 2: Replace both fake Puppy Coins previews**

In onboarding and Settings preview cards, use:

```kotlin
Text("Puppy Clicker", fontWeight = FontWeight.Black)
Text("Theme preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
Spacer(Modifier.height(7.dp))
Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Accent Preview") }
```

Remove `Puppy Coins`, `15,250`, and `BUY`.

- [ ] **Step 3: Remove global PupEye rendering from `Theme.kt`**

```kotlin
MaterialTheme(
    colorScheme = colors,
    typography = Typography(),
    content = content
)
```

Remove imports used only by the old global overlay.

- [ ] **Step 4: Host the top-right PupEye badge inside onboarding only**

```kotlin
Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OnboardingProgress(step)
        Spacer(Modifier.height(20.dp))
        // Existing AnimatedContent step switch remains here.
    }

    StreamedPupEyeBranding(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 8.dp, end = 8.dp)
            .size(38.dp),
        contentDescription = "PupEye fair-play protection"
    )
}
```

Keep the Step 1 `Protected by PupEye` card and dedicated Settings PupEye branding.

- [ ] **Step 5: Clean-build and verify generated source**

```bash
gradle --no-daemon clean :app:testDebugUnitTest :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
! grep -R "Puppy Coins\|15,250\|Text(\"BUY\")" app/src/main/java app/build/generated/protected-puppies/source
! grep -n "StreamedPupEyeBranding" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker/ui/theme/Theme.kt
grep -R "StreamedPupEyeBranding" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker | grep -v "/ui/theme/Theme.kt"
```

Expected: build PASS; first two checks find no removed global/fake UI; final grep still finds onboarding/dedicated PupEye surfaces.

- [ ] **Step 6: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/Theme.kt
git commit -m "feat: refine onboarding and PupEye branding"
```

---

### Task 5: Verify generated-source patch compatibility

**Files:**
- Verify: `App/tools/patch_settings_setup_revamp.py`
- Verify: `App/tools/patch_settings_setup_revamp_runner.py`
- Verify: `App/tools/patch_puppy_ux.py`

- [ ] **Step 1: Force a clean generation pass**

```bash
gradle --no-daemon clean :app:generateProtectedPuppySources --stacktrace
```

Expected: all current Python transforms exit successfully because this plan leaves their motion, first-run-gate, migration-signal, and Settings-routing anchors intact.

- [ ] **Step 2: Compile full app**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --stacktrace
```

Expected: PASS.

- [ ] **Step 3: If an anchor unexpectedly fails, stop for systematic debugging**

Identify the exact transform named in the failure and compare its expected anchor with the generated pre-transform source before editing a patch script. This is a diagnostic gate, not permission to make speculative generator changes.

No commit is created when verification passes unchanged.

---

## Plan Self-Review

- Spec coverage: local profile redesign, future account cards, recurring month/day birthday, February 29, legacy-year cleanup, removal of Puppy Coins, setup-only top-right PupEye, and dedicated PupEye preservation are covered.
- Placeholder scan: all intended code changes are concrete; the final generation failure branch deliberately stops for root-cause investigation rather than inventing an unknown patch.
- Type consistency: the new month/day setter exists before UI migration; after Task 3 all callers and state use month/day only.
- Task independence: Tasks 1-4 each end in a compilable/testable state; Task 5 is verification-only.
