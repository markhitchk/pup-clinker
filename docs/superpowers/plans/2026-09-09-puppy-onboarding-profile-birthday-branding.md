# Puppy Onboarding, Birthday, and PupEye Branding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign setup Step 2, make birthdays month/day-only, remove fake Puppy Coins UI, and limit the floating top-right PupEye badge to setup while keeping dedicated PupEye security branding.

**Architecture:** Introduce one pure month/day birthday validator shared by onboarding, Settings, and preference persistence. Keep onboarding as six steps, keep local username as the active profile path, model Discord/Website as disabled future connection cards, remove economic placeholder previews, and move the floating PupEye overlay out of the global theme into the onboarding root.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, SharedPreferences/StateFlow, JUnit 4, existing generated-source Python patch pipeline.

**Spec:** `docs/superpowers/specs/2026-09-09-puppy-clicker-save-onboarding-ui-repair-design.md`

## Global Constraints

- Onboarding remains six steps.
- Step 2 must work with a local username; Discord and Website authentication remain Coming Soon.
- Birthday stores and validates only month/day; no year is required for local or future Discord accounts.
- February 29 is valid as a recurring birthday.
- Existing month/day data must survive upgrades even when an old birthday year exists.
- Setup and Settings must use identical birthday validity rules.
- Puppy Clicker has no Puppy Coins economy; no setup/Settings preview may imply one.
- The top-right PupEye badge may appear during setup but must not float over normal Play/Care/Shop/Prestige/Settings screens after setup.
- Dedicated PupEye protection surfaces and streamed PupEye branding remain available.
- Source edits must survive `generateProtectedPuppySources` and the Python compatibility patches.

## File Structure

- Create `App/app/src/main/java/com/harleytg/puppyclicker/PuppyBirthday.kt` — pure month/day validation.
- Create `App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt` — calendar edge-case tests.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt` — remove active year state/validation and clean legacy year data.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt` — Step 2 layout, Step 3 month/day UI, setup-only top-right PupEye, non-economic appearance preview.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt` — month/day birthday editor and non-economic live preview.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/Theme.kt` — remove global PupEye overlay while preserving theme/density/motion providers.
- Verify `App/tools/patch_settings_setup_revamp.py`, `App/tools/patch_settings_setup_revamp_runner.py`, and `App/tools/patch_puppy_ux.py` against generated-source anchors; modify only if generation fails after canonical source changes.

---

### Task 1: Create one recurring birthday validator

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyBirthday.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt`

**Interfaces:**
- Produces: `internal object PuppyBirthday`.
- Produces: `fun maxDay(month: Int): Int` returning `0` for invalid months and the maximum recurring day otherwise.
- Produces: `fun isValid(month: Int, day: Int): Boolean`.

- [ ] **Step 1: Write the failing calendar tests**

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

- [ ] **Step 2: Run the focused test and verify it fails because `PuppyBirthday` does not exist**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyBirthdayTest --stacktrace
```

Expected: compilation failure for unresolved `PuppyBirthday`.

- [ ] **Step 3: Implement the pure validator**

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

- [ ] **Step 4: Run the focused test and verify it passes**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyBirthdayTest --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit the birthday validator**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyBirthday.kt \
  App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt
git commit -m "test: define recurring birthday validation"
```

---

### Task 2: Remove birthday year from active preference state

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt` in `PuppyUiState`, birthday keys, `ensure`, `setBirthday`, reset/migration/read paths
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyBirthdayTest.kt`

**Interfaces:**
- Consumes: `PuppyBirthday.isValid(month, day)`.
- Produces: `PuppyUiState(birthdayMonth: Int, birthdayDay: Int, ...)` with no active `birthdayYear` property.
- Produces: `PuppyUiPreferences.setBirthday(context: Context, month: Int, day: Int): Boolean`.

- [ ] **Step 1: Change birthday validity in `PuppyUiState`**

Replace the existing year-dependent properties with:

```kotlin
val hasBirthday: Boolean
    get() = PuppyBirthday.isValid(birthdayMonth, birthdayDay)
```

Remove `birthdayYear` and `birthdayDate`; recurring birthday behavior does not need a concrete `LocalDate`.

- [ ] **Step 2: Change persistence API to month/day only**

Replace `setBirthday(context, month, day, year)` with:

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

Keep `KEY_BIRTHDAY_YEAR` temporarily as a private legacy-key constant so upgrades can remove old values safely.

- [ ] **Step 3: Clean legacy year data without replaying onboarding**

Inside `ensure(context)` after `migrateOnce(app, store)` and before publishing state, add:

```kotlin
if (store.contains(KEY_BIRTHDAY_YEAR)) {
    store.edit().remove(KEY_BIRTHDAY_YEAR).apply()
}
```

This retains stored month/day while dropping the obsolete year.

- [ ] **Step 4: Update clear/reset/migration/read paths**

Use exactly these rules:

```kotlin
fun clearBirthday(context: Context) = edit(context) {
    remove(KEY_BIRTHDAY_MONTH)
    remove(KEY_BIRTHDAY_DAY)
    remove(KEY_BIRTHDAY_YEAR)
}
```

In `resetInterfaceSettings`, preserve only month/day:

```kotlin
if (keep.birthdayMonth > 0) putInt(KEY_BIRTHDAY_MONTH, keep.birthdayMonth)
if (keep.birthdayDay > 0) putInt(KEY_BIRTHDAY_DAY, keep.birthdayDay)
```

In `migrateOnce`, retain existing legacy month/day and do not write year `0`.

In `read`, populate only `birthdayMonth` and `birthdayDay`.

- [ ] **Step 5: Remove now-unused `LocalDate` import from `PuppyUiPreferences.kt`**

The file must compile without year/date-based validation.

- [ ] **Step 6: Run JVM tests and generated-source compilation**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:generateProtectedPuppySources --stacktrace
```

Expected at this intermediate point: if onboarding/Settings still call the old three-argument API, generation succeeds but app compilation may fail; do not commit a release build until Tasks 3-4 update all callers.

- [ ] **Step 7: Commit preference-model changes together with caller updates in Task 3 if compilation cannot remain green independently**

Do not leave `main` with unresolved `birthdayYear` references. If the repository policy requires every commit to compile, perform Task 3 before this commit and combine both file sets in the same commit.

---

### Task 3: Redesign onboarding Steps 2-4 and keep PupEye badge setup-only

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt` in root flow, `ProfileStep`, `BirthdayStep`, `SetupAppearanceStep`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/Theme.kt` in `PuppyClickerTheme`
- Modify with Task 2 if needed: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt`

**Interfaces:**
- Consumes: `PuppyUiPreferences.setBirthday(context, month, day)` and `PuppyBirthday.maxDay/isValid`.
- Produces: six-step onboarding with a local-profile path, disabled future account cards, Month/Day birthday only, no fake currency preview, and an onboarding-owned PupEye top-right badge.

- [ ] **Step 1: Move the floating PupEye badge from global theme to onboarding**

In `Theme.kt`, replace the `Box { content(); StreamedPupEyeBranding(...) }` wrapper with direct themed content:

```kotlin
MaterialTheme(
    colorScheme = colors,
    typography = Typography(),
    content = content
)
```

Remove no-longer-used global-overlay imports such as `Box`, `WindowInsets`, `safeDrawing`, `windowInsetsPadding`, `Alignment`, `alpha`, `dp`, and `StreamedPupEyeBranding` when the compiler confirms they are unused.

In `PuppyOnboardingFlow`, make the `Surface` contain a `Box` and put the existing scrolling setup `Column` plus this badge inside it:

```kotlin
Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // existing progress + AnimatedContent
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

This keeps the badge during setup only. Do not remove the `Protected by PupEye` welcome card or the dedicated Settings security logo.

- [ ] **Step 2: Replace flat Step 2 status rows with Local Profile and Connect an account sections**

Keep the existing username normalization. The Step 2 body should use this structure:

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

Add this helper in the same file:

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

- [ ] **Step 3: Convert Step 3 to Month + Day only**

Replace year state/validation with:

```kotlin
var month by rememberSaveable { mutableIntStateOf(ui.birthdayMonth.coerceIn(0, 12)) }
var day by rememberSaveable { mutableIntStateOf(ui.birthdayDay.coerceIn(0, 31)) }
val maxDay = PuppyBirthday.maxDay(month).takeIf { it > 0 } ?: 31
if (day > maxDay) day = 0
val valid = PuppyBirthday.isValid(month, day)
```

Render only Month and Day pickers. Remove the Year picker and age/year copy.

Use this save call:

```kotlin
if (PuppyUiPreferences.setBirthday(context, month, day)) {
    vm.setSeasonalBirthday(month, day)
    onNext()
}
```

Update privacy copy to state that month/day remains local and is not displayed publicly.

- [ ] **Step 4: Replace the fake setup economy preview**

Replace the hard-coded `Puppy Coins: 15,250` and `BUY` card with:

```kotlin
Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier.fillMaxWidth()
) {
    Column(Modifier.padding(12.dp)) {
        Text("Puppy Clicker", fontWeight = FontWeight.Black)
        Text("Theme preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(7.dp))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text("Accent Preview")
        }
    }
}
```

No currency, store balance, or purchase copy may appear in this preview.

- [ ] **Step 5: Build canonical and generated source**

```bash
gradle --no-daemon :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
```

Expected: no patch-anchor error and no unresolved `birthdayYear`/three-argument `setBirthday` call.

- [ ] **Step 6: Verify generated source does not reintroduce removed UI**

From `App/`:

```bash
! grep -R "Puppy Coins" app/build/generated/protected-puppies/source
! grep -R "PuppyUiState.*birthdayYear\|initialYear = ui.birthdayYear" app/build/generated/protected-puppies/source
```

Expected: both commands succeed by finding no matches.

- [ ] **Step 7: Commit onboarding/theme changes**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt \
  App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/Theme.kt
git commit -m "feat: simplify Puppy Clicker setup profile"
```

---

### Task 4: Convert Settings birthday editor and live preview

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt` in `AppearancePreview`, `AccountProfileSettings`, `BirthdayEditorDialog`

**Interfaces:**
- Consumes: `PuppyBirthday.maxDay/isValid` and `PuppyUiPreferences.setBirthday(context, month, day)`.
- Produces: Settings birthday editor with `(month, day)` callbacks only and a non-economic appearance preview.

- [ ] **Step 1: Remove Puppy Coins from `AppearancePreview`**

Use:

```kotlin
Text("Puppy Clicker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
Text("Theme preview", style = MaterialTheme.typography.bodyMedium)
Spacer(Modifier.height(9.dp))
Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Accent Preview") }
```

Keep the existing theme/accent label below it.

- [ ] **Step 2: Change the Settings birthday editor call**

Replace the three-value call with:

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

- [ ] **Step 3: Change `BirthdayEditorDialog` signature and body**

Use:

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

    // Existing AlertDialog shell stays; render Month and Day selectors only.
    // Confirm action calls onSave(month, day) and is enabled only when valid.
}
```

Delete the Year selector and remove unused `LocalDate`/`YearMonth` imports if no other Settings code uses them. Keep `Month` only if still used for month labels.

- [ ] **Step 4: Compile and search both canonical and generated source**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
! grep -R "Puppy Coins" app/src/main/java app/build/generated/protected-puppies/source
! grep -R "birthdayYear\|initialYear" app/src/main/java app/build/generated/protected-puppies/source
```

Expected: build PASS; no fake currency; no active birthday-year references.

- [ ] **Step 5: Commit Settings cleanup**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt
git commit -m "feat: use month day birthdays in settings"
```

---

### Task 5: Verify generated-source patch compatibility and branding boundaries

**Files:**
- Verify: `App/tools/patch_settings_setup_revamp.py`
- Verify: `App/tools/patch_settings_setup_revamp_runner.py`
- Verify: `App/tools/patch_puppy_ux.py`
- Modify only the exact broken anchor if `generateProtectedPuppySources` proves one is stale.

**Interfaces:**
- Produces: generated Kotlin that matches canonical setup/birthday/theme behavior.

- [ ] **Step 1: Force a clean generated-source rebuild**

```bash
gradle --no-daemon clean :app:generateProtectedPuppySources --stacktrace
```

Expected: every Python patch exits successfully.

- [ ] **Step 2: Verify PupEye appears only in intended generated surfaces**

```bash
grep -R "StreamedPupEyeBranding" app/build/generated/protected-puppies/source/com/harleytg/puppyclicker
```

Expected: matches include onboarding/welcome and dedicated PupEye security/settings components; `ui/theme/Theme.kt` must not contain `StreamedPupEyeBranding`.

- [ ] **Step 3: Verify final app compilation**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --stacktrace
```

Expected: PASS.

- [ ] **Step 4: If a patch anchor fails, change only that anchor and re-run Step 1**

For example, if the onboarding motion patch still expects the old local variable name, update its exact source anchor while preserving the behavior:

```python
source = replace_once(
    source,
    "    val reducedMotion = LocalPuppyReducedMotion.current",
    "    val animateUi = com.harleytg.puppyclicker.ui.theme.LocalPuppyAnimatedUi.current and not LocalPuppyReducedMotion.current",
    "onboarding motion state",
)
```

Use valid Kotlin text in the actual replacement (`&&`, not Python `and not`); the point of this step is to update only a proven stale anchor, not rewrite unrelated patch logic.

- [ ] **Step 5: Commit a patch-script change only if Step 1 required it**

```bash
git add App/tools/patch_settings_setup_revamp.py App/tools/patch_settings_setup_revamp_runner.py App/tools/patch_puppy_ux.py
git commit -m "fix: keep setup generator aligned with source"
```

Skip this commit if no patch script changed.

---

## Plan Self-Review

- Spec coverage: local profile redesign, future account cards, month/day-only birthday, February 29, migration, removal of Puppy Coins, setup-only top-right PupEye, and dedicated PupEye branding preservation are covered.
- Placeholder scan: implementation behavior is specified; no future authentication or new economy work is introduced.
- Type consistency: every caller uses `setBirthday(context, month, day)` and `BirthdayEditorDialog(..., onSave: (Int, Int) -> Unit)` after the migration.
- Generated-source check: every canonical UI edit is explicitly verified after the existing patch pipeline runs.
