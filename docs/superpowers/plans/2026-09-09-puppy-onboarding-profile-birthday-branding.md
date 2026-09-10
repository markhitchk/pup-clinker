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
- Existing month/day data must survive upgrades even when an old birthday year exists.
- Setup and Settings must use identical birthday validity rules.
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
- Verify `App/tools/patch_settings_setup_revamp.py`, `App/tools/patch_settings_setup_revamp_runner.py`, and `App/tools/patch_puppy_ux.py`; current anchors touched by this plan are expected to remain valid, so no patch-script edit is planned unless verification disproves that assumption.

---

### Task 1: Add recurring month/day birthday validation

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyBirthday.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt`

**Interfaces:**
- Produces: `internal object PuppyBirthday`.
- Produces: `fun maxDay(month: Int): Int`.
- Produces: `fun isValid(month: Int, day: Int): Boolean`.

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

- [ ] **Step 2: Run the focused test and confirm unresolved `PuppyBirthday`**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyBirthdayTest --stacktrace
```

Expected: compile failure because `PuppyBirthday` does not exist.

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

- [ ] **Step 4: Run the focused test**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyBirthdayTest --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyBirthday.kt \
  App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt
git commit -m "test: define recurring birthday validation"
```

---

### Task 2: Add a build-safe month/day preference API

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt`
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt`

**Interfaces:**
- Consumes: `PuppyBirthday.isValid(month, day)`.
- Produces now: `PuppyUiPreferences.setBirthday(context: Context, month: Int, day: Int): Boolean`.
- Temporarily retains: existing four-argument `setBirthday(context, month, day, year)` so the current onboarding/Settings callers still compile until Task 3.

- [ ] **Step 1: Change `PuppyUiState.hasBirthday` to recurring month/day validity**

```kotlin
val hasBirthday: Boolean
    get() = PuppyBirthday.isValid(birthdayMonth, birthdayDay)
```

Keep the current `birthdayYear` field temporarily in this task only so all existing UI callers remain source-compatible.

- [ ] **Step 2: Add the new month/day persistence method**

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

- [ ] **Step 3: Keep the old four-argument method only as a transitional compatibility path**

Keep its current validation/write behavior until Task 3 rather than delegating to the new method. Mark it deprecated so no new caller is added:

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

- [ ] **Step 4: Run unit tests and compile the app**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Expected: PASS. This task is intentionally build-safe before any UI caller migration.

- [ ] **Step 5: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt
git commit -m "feat: add month day birthday preference API"
```

---

### Task 3: Migrate onboarding and Settings to month/day, then remove active year state

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt` in `BirthdayStep`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt` in `AccountProfileSettings` and `BirthdayEditorDialog`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt` in state/read/reset/migration/cleanup

**Interfaces:**
- Consumes: `PuppyBirthday.maxDay/isValid` and `setBirthday(context, month, day)`.
- Produces: no active `birthdayYear` property and no four-argument `setBirthday` method.
- Produces: `BirthdayEditorDialog(initialMonth: Int, initialDay: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit)`.

- [ ] **Step 1: Convert onboarding Step 3 to Month + Day only**

Use:

```kotlin
var month by rememberSaveable { mutableIntStateOf(ui.birthdayMonth.coerceIn(0, 12)) }
var day by rememberSaveable { mutableIntStateOf(ui.birthdayDay.coerceIn(0, 31)) }
val maxDay = PuppyBirthday.maxDay(month).takeIf { it > 0 } ?: 31
if (day > maxDay) day = 0
val valid = PuppyBirthday.isValid(month, day)
```

Render only Month and Day pickers. Save with:

```kotlin
if (PuppyUiPreferences.setBirthday(context, month, day)) {
    vm.setSeasonalBirthday(month, day)
    onNext()
}
```

Replace the existing year-oriented privacy copy with text that says the birthday month/day is local and not publicly displayed.

- [ ] **Step 2: Convert Settings birthday editor call and callback**

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

- [ ] **Step 3: Convert `BirthdayEditorDialog` to month/day only**

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
                SetupPicker(
                    if (month == 0) "Month" else setupMonthName(month),
                    (1..12).map { it to setupMonthName(it) }
                ) { selected ->
                    month = selected
                    if (day > PuppyBirthday.maxDay(selected)) day = 0
                }
                Spacer(Modifier.height(8.dp))
                SetupPicker(
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

If `SetupPicker` is private to onboarding and unavailable from Settings, retain the Settings file's existing picker helper and apply the same month/day values and validity rules; do not duplicate year logic.

- [ ] **Step 4: Remove active year state and clean legacy storage in `PuppyUiPreferences`**

Remove `birthdayYear` and `birthdayDate` from `PuppyUiState`. Remove the transitional four-argument `setBirthday` method.

Keep `KEY_BIRTHDAY_YEAR` only as a legacy cleanup constant and remove it after migration in `ensure`:

```kotlin
migrateOnce(app, store)
if (store.contains(KEY_BIRTHDAY_YEAR)) {
    store.edit().remove(KEY_BIRTHDAY_YEAR).apply()
}
```

Update `clearBirthday`:

```kotlin
fun clearBirthday(context: Context) = edit(context) {
    remove(KEY_BIRTHDAY_MONTH)
    remove(KEY_BIRTHDAY_DAY)
    remove(KEY_BIRTHDAY_YEAR)
}
```

In `resetInterfaceSettings`, preserve only month/day. In `migrateOnce`, copy legacy month/day but do not write year `0`. In `read`, populate only month/day.

- [ ] **Step 5: Remove unused date/year imports**

Remove `LocalDate` from `PuppyUiPreferences.kt` after the transitional overload is gone. Remove `YearMonth`/year-specific imports from onboarding and Settings when unused.

- [ ] **Step 6: Run unit tests, generated-source build, and grep for active year usage**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
! grep -R "birthdayYear\|initialYear" app/src/main/java app/build/generated/protected-puppies/source
```

Expected: PASS and no active birthday-year references.

- [ ] **Step 7: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt
git commit -m "feat: use month day birthdays everywhere"
```

---

### Task 4: Redesign Step 2, remove fake currency previews, and scope PupEye to onboarding

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt` in root flow, `ProfileStep`, `SetupAppearanceStep`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt` in `AppearancePreview`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/Theme.kt` in `PuppyClickerTheme`

**Interfaces:**
- Produces: local-profile Step 2 plus disabled Discord/Website cards.
- Produces: theme previews without currency/purchase language.
- Produces: setup-owned top-right `StreamedPupEyeBranding`; global theme no longer draws it.

- [ ] **Step 1: Replace Step 2 with Local Profile + Connect an account sections**

Keep the current username normalization/24-character limit and use this body:

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
    SetupNavigation(
        onBack = onBack,
        nextLabel = "Continue",
        nextEnabled = normalized.isNotBlank(),
        onNext = {
            PuppyPlayerIdentity.setUsername(context, username)
            onNext()
        }
    )
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

In onboarding `SetupAppearanceStep` and Settings `AppearancePreview`, use non-economic preview content:

```kotlin
Text("Puppy Clicker", fontWeight = FontWeight.Black)
Text("Theme preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
Spacer(Modifier.height(7.dp))
Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
    Text("Accent Preview")
}
```

Keep existing theme/accent metadata where useful. Remove `Puppy Coins`, `15,250`, and `BUY`.

- [ ] **Step 3: Remove the global PupEye overlay from `Theme.kt`**

Replace the themed `Box { content(); StreamedPupEyeBranding(...) }` with:

```kotlin
MaterialTheme(
    colorScheme = colors,
    typography = Typography(),
    content = content
)
```

Remove imports used only by the global overlay.

- [ ] **Step 4: Add the top-right streamed badge to the onboarding root only**

Inside the onboarding `Surface`, host the scrolling setup content and badge in one `Box`:

```kotlin
Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // existing OnboardingProgress + AnimatedContent block
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

Keep the Step 1 `Protected by PupEye` card and the dedicated Settings PupEye logo.

- [ ] **Step 5: Clean-build generated source and verify removed UI stays removed**

```bash
gradle --no-daemon clean :app:testDebugUnitTest :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
! grep -R "Puppy Coins\|15,250\|Text(\"BUY\")" app/src/main/java app/build/generated/protected-puppies/source
! grep -n "StreamedPupEyeBranding" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker/ui/theme/Theme.kt
```

Expected: build PASS; no fake currency/purchase copy; no PupEye branding call in generated `Theme.kt`.

- [ ] **Step 6: Verify intended PupEye surfaces remain**

```bash
grep -R "StreamedPupEyeBranding" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker | grep -v "/ui/theme/Theme.kt"
```

Expected: onboarding and dedicated PupEye protection/settings surfaces still contain streamed branding calls.

- [ ] **Step 7: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/Theme.kt
git commit -m "feat: refine onboarding and PupEye branding"
```

---

### Task 5: Verify the existing generated-source patch chain

**Files:**
- Verify only: `App/tools/patch_settings_setup_revamp.py`
- Verify only: `App/tools/patch_settings_setup_revamp_runner.py`
- Verify only: `App/tools/patch_puppy_ux.py`

**Interfaces:**
- Produces: proof that canonical onboarding/preferences/theme edits survive the current generation chain unchanged.

- [ ] **Step 1: Rebuild generated sources from clean state**

```bash
gradle --no-daemon clean :app:generateProtectedPuppySources --stacktrace
```

Expected: all current Python transforms exit successfully. The planned canonical edits do not alter the known motion, first-run gate, migration-signal, or Settings-routing anchors used by these scripts.

- [ ] **Step 2: Compile the generated app**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --stacktrace
```

Expected: PASS.

- [ ] **Step 3: Stop instead of guessing if a patch anchor unexpectedly fails**

If Step 1 reports an anchor-count/signature error, return to systematic debugging: identify the exact failed transform and compare the generated pre-patch source to that transform before making any patch-script change. Do not bundle speculative patch-script rewrites into this plan.

No commit is created for this task when verification passes unchanged.

---

## Plan Self-Review

- Spec coverage: local profile redesign, future account cards, recurring month/day birthday, February 29, old-year cleanup, removal of Puppy Coins, setup-only top-right PupEye, and dedicated PupEye preservation are covered.
- Placeholder scan: no implementation field is left undefined; the one conditional failure path explicitly stops for root-cause investigation rather than prescribing an unknown edit.
- Type consistency: the new two-argument birthday payload `(month, day)` is introduced before callers migrate, then the old year API/state is removed after all callers change.
- Task independence: Tasks 1, 2, 3, and 4 each end in a compilable/testable state; Task 5 is verification-only.
