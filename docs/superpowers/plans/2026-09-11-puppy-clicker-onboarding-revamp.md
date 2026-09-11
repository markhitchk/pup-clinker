# Puppy Clicker Minimal Onboarding Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Puppy Clicker's six-step onboarding with the approved minimal five-step flow while preserving local profiles, Discord PKCE signup, encrypted save import, device-bound identity, optional birthday, notification preferences, AFK protections, and permanent v4 signing.

**Architecture:** Add a small pure onboarding model for step migration and transient setup decisions, then split the existing monolithic onboarding Compose file into focused step files behind one shared shell. Existing identity, Discord, save-transfer, notification, and preference services remain authoritative; the new UI orchestrates them rather than duplicating their logic.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Activity Result APIs, SharedPreferences/StateFlow, JUnit 4, Gradle, GitHub Actions, Android `apksigner`.

**Spec:** `docs/superpowers/specs/2026-09-11-puppy-clicker-onboarding-revamp-design.md`

## Global Constraints

- Five steps exactly: Welcome, Player Setup, Personalize, Notifications, Ready.
- Local Profile remains available and is the default Player Setup method.
- Discord remains optional and uses the existing PKCE `identify`-only flow.
- Save import reuses `GameSaveTransfer`; do not change the `.pupsave` format or crypto.
- Player ID and Friend Code remain device-bound.
- Save passwords and Discord access tokens are never persisted.
- Birthday is month/day only and may be skipped.
- Notification permission denial must never block onboarding.
- Existing completed users must not be forced back through onboarding.
- No AFK reward may accrue before setup is complete.
- Final release must be signed with the existing permanent Puppy Clicker key and verified with its v4 `.idsig`.

---

## File Structure

### Create

- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingModel.kt` — pure step model, migration, setup method and notification decisions.
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingShell.kt` — responsive scaffold, progress and bottom navigation.
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingPlayerSetup.kt` — Local / Discord / Import UI.
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingPersonalize.kt` — birthday + appearance + accessibility.
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingNotifications.kt` — notification preferences and permission.
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingReady.kt` — summary/review/final completion.
- `App/app/src/test/java/com/harleytg/puppyclicker/PuppyOnboardingModelTest.kt`
- `App/app/src/test/java/com/harleytg/puppyclicker/PuppyOnboardingPreferencesContractTest.kt`
- `App/app/src/test/java/com/harleytg/puppyclicker/PuppyOnboardingAfkContractTest.kt`

### Modify

- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/GameSaveTransfer.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerApplication.kt` only if the AFK regression test exposes a real gap.

---

## Task 1: Pure Onboarding Model and Six-to-Five Migration

**Files:**
- Create: `PuppyOnboardingModel.kt`
- Create: `PuppyOnboardingModelTest.kt`
- Modify: `PuppyUiPreferences.kt`

**Interfaces:**
- Produces `PuppyOnboardingStep`, `PuppyPlayerSetupMethod`, `PuppyOnboardingSessionState`, `migrateLegacyOnboardingStep()`, `localUsernameEligible()`, and `notificationPermissionDecision()`.

- [ ] **Step 1: Write the failing model tests**

```kotlin
class PuppyOnboardingModelTest {
    @Test
    fun legacySixStepIndexesMapDeterministicallyToFiveSteps() {
        assertEquals(0, migrateLegacyOnboardingStep(0))
        assertEquals(1, migrateLegacyOnboardingStep(1))
        assertEquals(2, migrateLegacyOnboardingStep(2))
        assertEquals(2, migrateLegacyOnboardingStep(3))
        assertEquals(3, migrateLegacyOnboardingStep(4))
        assertEquals(4, migrateLegacyOnboardingStep(5))
    }

    @Test
    fun freshSessionDefaultsToLocalProfile() {
        val state = PuppyOnboardingSessionState()
        assertEquals(PuppyPlayerSetupMethod.LOCAL, state.playerSetupMethod)
        assertFalse(state.importedSave)
        assertFalse(state.birthdaySkipped)
    }

    @Test
    fun validLocalUsernameCanContinue() {
        assertTrue(localUsernameEligible("puppy_player"))
        assertFalse(localUsernameEligible("   "))
    }

    @Test
    fun notificationPermissionRequestedOnlyWhenNeeded() {
        assertEquals(
            PuppyNotificationPermissionDecision.REQUEST,
            notificationPermissionDecision(35, true, false)
        )
        assertEquals(
            PuppyNotificationPermissionDecision.NONE,
            notificationPermissionDecision(32, true, false)
        )
        assertEquals(
            PuppyNotificationPermissionDecision.NONE,
            notificationPermissionDecision(35, false, false)
        )
        assertEquals(
            PuppyNotificationPermissionDecision.NONE,
            notificationPermissionDecision(35, true, true)
        )
    }
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :app:testDebugUnitTest --tests com.harleytg.puppyclicker.PuppyOnboardingModelTest --stacktrace
```

Expected: compilation failure because the model does not exist.

- [ ] **Step 3: Implement the minimal model**

```kotlin
enum class PuppyOnboardingStep(val persistedIndex: Int) {
    WELCOME(0), PLAYER_SETUP(1), PERSONALIZE(2), NOTIFICATIONS(3), READY(4);

    companion object {
        fun fromPersisted(index: Int): PuppyOnboardingStep =
            entries.firstOrNull { it.persistedIndex == index } ?: WELCOME
    }
}

enum class PuppyPlayerSetupMethod { LOCAL, DISCORD, IMPORT_SAVE }

data class PuppyOnboardingSessionState(
    val playerSetupMethod: PuppyPlayerSetupMethod = PuppyPlayerSetupMethod.LOCAL,
    val importedSave: Boolean = false,
    val birthdaySkipped: Boolean = false
)

enum class PuppyNotificationPermissionDecision { NONE, REQUEST }

fun migrateLegacyOnboardingStep(oldStep: Int): Int = when (oldStep.coerceIn(0, 5)) {
    0 -> 0
    1 -> 1
    2, 3 -> 2
    4 -> 3
    else -> 4
}

fun localUsernameEligible(raw: String): Boolean {
    val normalized = PuppyPlayerIdentity.normalizeUsername(raw)
    return normalized.isNotBlank() &&
        PuppyPlayerIdentity.usernameModerationIssue(normalized) == null
}

fun notificationPermissionDecision(
    sdkInt: Int,
    notificationsEnabled: Boolean,
    permissionGranted: Boolean
): PuppyNotificationPermissionDecision =
    if (sdkInt >= 33 && notificationsEnabled && !permissionGranted) {
        PuppyNotificationPermissionDecision.REQUEST
    } else {
        PuppyNotificationPermissionDecision.NONE
    }
```

- [ ] **Step 4: Verify GREEN**

Run the focused model test again and require `BUILD SUCCESSFUL`.

- [ ] **Step 5: Version the persisted flow**

In `PuppyUiPreferences.kt` add:

```kotlin
private const val KEY_SETUP_FLOW_VERSION = "setup_flow_version"
private const val SETUP_FLOW_VERSION = 2
```

Add one-time migration:

```kotlin
private fun migrateSetupFlowIfNeeded(store: SharedPreferences) {
    if (store.getInt(KEY_SETUP_FLOW_VERSION, 1) >= SETUP_FLOW_VERSION) return

    val complete = store.getBoolean(KEY_SETUP_COMPLETE, false)
    val migrated = if (complete) 4
    else migrateLegacyOnboardingStep(store.getInt(KEY_SETUP_STEP, 0))

    store.edit()
        .putInt(KEY_SETUP_STEP, migrated)
        .putInt(KEY_SETUP_FLOW_VERSION, SETUP_FLOW_VERSION)
        .apply()
}
```

Call it from `ensure()` after the existing install migration. Change all onboarding bounds from `0..5` to `0..4`. `finishSetup()` writes step 4 + flow version 2. `prepareFreshSetupAfterDelete()` writes flow version 2.

- [ ] **Step 6: Run full JVM tests**

```bash
./gradlew :app:testDebugUnitTest --stacktrace
```

- [ ] **Step 7: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingModel.kt         App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt         App/app/src/test/java/com/harleytg/puppyclicker/PuppyOnboardingModelTest.kt
git commit -m "Refactor onboarding state to five steps"
```

---

## Task 2: Shared Minimal Shell and Welcome

**Files:**
- Create: `PuppyOnboardingShell.kt`
- Modify: `PuppyOnboardingUi.kt`
- Create: `PuppyOnboardingPreferencesContractTest.kt`

- [ ] **Step 1: Add five-step persistence contract test**

```kotlin
@Test
fun preferencesPersistFiveStepFlowVersion() {
    val source = File(
        "src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt"
    ).readText()
    assertTrue(source.contains("SETUP_FLOW_VERSION = 2"))
    assertTrue(source.contains("coerceIn(0, 4)"))
    assertTrue(source.contains("putInt(KEY_SETUP_STEP, 4)"))
}
```

- [ ] **Step 2: Implement `PuppyOnboardingShell`**

Signature:

```kotlin
@Composable
internal fun PuppyOnboardingShell(
    step: PuppyOnboardingStep,
    title: String,
    canGoBack: Boolean,
    primaryLabel: String?,
    primaryEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    onPrimary: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

Requirements:
- `widthIn(max = 560.dp)`;
- `WindowInsets.safeDrawing`;
- `verticalScroll` + `imePadding()`;
- `navigationBarsPadding()`;
- header text `Step ${step.persistedIndex + 1} of 5`;
- `LinearProgressIndicator`;
- consistent Back + primary action area;
- no overlay covering fields.

- [ ] **Step 3: Convert `PuppyOnboardingUi.kt` to orchestration**

Store a 0..4 step index, map through `PuppyOnboardingStep.fromPersisted()`, preserve transient session state, and route to five focused steps.

- [ ] **Step 4: Simplify Welcome**

Use a 120–136dp logo, one headline, one concise sentence, Get Started, compact Discord/Terms/Privacy links, lightweight PupEye status and development notice. Remove the large PupEye and Discord cards.

- [ ] **Step 5: Verify**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest --stacktrace
```

- [ ] **Step 6: Commit**

```bash
git commit -am "Add minimal onboarding shell"
```

---

## Task 3: Player Setup — Local, Discord, Import

**Files:**
- Create: `PuppyOnboardingPlayerSetup.kt`
- Modify: `GameSaveTransfer.kt`
- Modify: `PuppyOnboardingUi.kt`
- Modify: `PuppyOnboardingModelTest.kt`

- [ ] **Step 1: Add state preservation test**

```kotlin
@Test
fun changingPlayerSetupMethodPreservesSessionFlags() {
    val imported = PuppyOnboardingSessionState(
        playerSetupMethod = PuppyPlayerSetupMethod.IMPORT_SAVE,
        importedSave = true
    )
    val switched = imported.copy(playerSetupMethod = PuppyPlayerSetupMethod.LOCAL)
    assertTrue(switched.importedSave)
    assertEquals(PuppyPlayerSetupMethod.LOCAL, switched.playerSetupMethod)
}
```

- [ ] **Step 2: Add save password requirement detection**

In `GameSaveTransfer` add:

```kotlin
internal enum class PuppySavePasswordRequirement {
    REQUIRED, NOT_REQUIRED, UNKNOWN
}

internal fun passwordRequirement(
    context: Context,
    uri: Uri
): PuppySavePasswordRequirement = runCatching {
    val root = JSONObject(readBounded(context, uri).toString(Charsets.UTF_8))
    if (root.optString("format") == LEGACY_FORMAT) {
        PuppySavePasswordRequirement.NOT_REQUIRED
    } else {
        PuppySavePasswordRequirement.REQUIRED
    }
}.getOrDefault(PuppySavePasswordRequirement.UNKNOWN)
```

Do not alter save encryption or payload format.

- [ ] **Step 3: Implement Player Setup selector**

Signature:

```kotlin
@Composable
internal fun PuppyOnboardingPlayerSetup(
    vm: PuppyClickerV6ViewModel,
    session: PuppyOnboardingSessionState,
    onSessionChange: (PuppyOnboardingSessionState) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit
)
```

Render Local / Discord / Import Save selectors. Only one branch is composed:

```kotlin
when (session.playerSetupMethod) {
    PuppyPlayerSetupMethod.LOCAL -> LocalPlayerSetup(...)
    PuppyPlayerSetupMethod.DISCORD -> DiscordPlayerSetup(...)
    PuppyPlayerSetupMethod.IMPORT_SAVE -> ImportPlayerSetup(...)
}
```

- [ ] **Step 4: Local branch**

One username field + one Create Local Profile button. Preserve text across tab switching. Button uses `localUsernameEligible()`; click re-checks moderation, shows the existing Username Not Allowed dialog on failure, and calls `PuppyPlayerIdentity.setUsername()` on success.

- [ ] **Step 5: Discord branch**

Use existing `DiscordSignupAuth.observe()`, `startSignup()`, PKCE and callback code. Busy state disables duplicate taps. Connected state shows display name, `@username`, Continue with Discord, and Use a Different Discord Account. Keep the existing username fallback logic.

- [ ] **Step 6: Import branch**

Use `OpenDocument`. After selection determine password requirement. Show password only for REQUIRED/UNKNOWN. Import via `GameSaveTransfer.import()`. On success:

```kotlin
vm.reloadImportedSave()
onSessionChange(session.copy(importedSave = true))
onComplete()
```

On failure remain on Step 2 with the returned message. Never persist the password.

- [ ] **Step 7: Remove old stacked Step 2 UI**

Delete old `ProfileStep` from `PuppyOnboardingUi.kt`. Remove onboarding-only `OnboardingSaveImport` once unused; retain `SaveTransferSettings` for Settings.

- [ ] **Step 8: Verify and commit**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest --stacktrace
git commit -am "Redesign onboarding player setup"
```

---

## Task 4: Personalize — Birthday + Appearance + Accessibility

**Files:**
- Create: `PuppyOnboardingPersonalize.kt`
- Modify: `PuppyOnboardingUi.kt`
- Modify: `PuppyOnboardingModelTest.kt`

- [ ] **Step 1: Add explicit birthday-skip test**

```kotlin
@Test
fun birthdaySkipIsExplicitSessionState() {
    assertTrue(
        PuppyOnboardingSessionState()
            .copy(birthdaySkipped = true)
            .birthdaySkipped
    )
}
```

- [ ] **Step 2: Implement Personalize**

Signature:

```kotlin
@Composable
internal fun PuppyOnboardingPersonalize(
    vm: PuppyClickerV6ViewModel,
    ui: PuppyUiState,
    session: PuppyOnboardingSessionState,
    onSessionChange: (PuppyOnboardingSessionState) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit
)
```

Birthday is optional:
- valid month/day saves through `PuppyUiPreferences.setBirthday()` and `vm.setSeasonalBirthday()`;
- Skip birthday calls `PuppyUiPreferences.clearBirthday()` and marks `birthdaySkipped = true`;
- Continue requires valid birthday OR explicit skip.

Appearance:
- System / Light / Dark;
- accent selector;
- Compact / Default / Large;
- Reduced Motion;
- one small preview surface.

Use existing reactive preference setters so changes preview immediately.

- [ ] **Step 3: Remove old Birthday + Appearance steps**

Delete old `BirthdayStep` and `SetupAppearanceStep`.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest --stacktrace
git commit -am "Merge onboarding personalization steps"
```

---

## Task 5: Notifications

**Files:**
- Create: `PuppyOnboardingNotifications.kt`
- Modify: `PuppyOnboardingUi.kt`

- [ ] **Step 1: Red-green the notification decision helper**

Temporarily make the production threshold wrong, run `PuppyOnboardingModelTest`, confirm failure, restore `sdkInt >= 33`, and confirm GREEN.

- [ ] **Step 2: Implement Notifications**

Three compact switch rows:
- Daily rewards;
- Game events;
- App updates.

Use the existing `PuppyUiPreferences` setters.

If API 33+ and any category is enabled and permission is missing, primary action requests `POST_NOTIFICATIONS`. Otherwise it is Continue. Always expose Not Now / Continue so denial never blocks setup.

- [ ] **Step 3: Remove old notification step**

Delete `SetupNotificationsStep`.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest --stacktrace
git commit -am "Simplify onboarding notifications"
```

---

## Task 6: Ready Summary, Review Setup, Completion and AFK Guard

**Files:**
- Create: `PuppyOnboardingReady.kt`
- Modify: `PuppyOnboardingUi.kt`
- Create: `PuppyOnboardingAfkContractTest.kt`

- [ ] **Step 1: Add AFK contract regression test**

```kotlin
@Test
fun afkRewardPreparationRemainsBlockedUntilSetupComplete() {
    val source = File(
        "src/main/java/com/harleytg/puppyclicker/PuppyClickerApplication.kt"
    ).readText()
    assertTrue(source.contains("private fun prepareAfkReward"))
    assertTrue(source.contains("PuppyUiPreferences.PREFS_NAME"))
    assertTrue(source.contains("setup_complete"))
    assertTrue(source.contains("if (!setupComplete)"))
}
```

Run it before changing app lifecycle behavior. If the existing names differ, rewrite the assertion to match the actual guard rather than modifying production code to satisfy a string.

- [ ] **Step 2: Implement Ready**

Show username, Player ID, Friend Code, Discord only when connected, Save restored only when `session.importedSave`, birthday only when present, and theme/UI scale summary.

Review Setup returns to Player Setup (step 1) without clearing session state.

Start Playing is the only onboarding action that calls:

```kotlin
vm.dismissSeasonalIntro()
PuppyUiPreferences.finishSetup(context)
```

- [ ] **Step 3: Remove old FinishStep**

No earlier step may call `finishSetup()`.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest --stacktrace
git commit -am "Add minimal onboarding ready summary"
```

---

## Task 7: Integration and Responsive Review

**Files:** all onboarding production/test files.

- [ ] **Step 1: Search for stale six-step behavior**

```bash
grep -R "Step .* of 6\|repeat(6)\|coerceIn(0, 5)\|putInt(KEY_SETUP_STEP, 5)"   app/src/main/java/com/harleytg/puppyclicker
```

Expected: no onboarding-flow matches.

- [ ] **Step 2: Responsive checklist**

Confirm:
- max content width 560dp;
- vertical scroll;
- IME padding on input screens;
- navigation bar padding;
- no fixed-width action rows;
- no overlay covering fields;
- selected method is indicated by more than color;
- large text does not clip critical actions.

- [ ] **Step 3: Full build verification**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleRelease --stacktrace
```

If local Android build tools are unavailable, push and require GitHub Actions to show success for JVM tests, instrumentation-test compilation and unsigned release build.

- [ ] **Step 4: Commit integration-only cleanup if needed**

Use `Polish minimal onboarding flow` only if this task changes code.

---

## Task 8: Permanent v4 Release APK

**Inputs:**
- Final successful unsigned release artifact from the exact final main commit.
- User-provided `Puppy-Clicker-NEW-Permanent-Signing-Pack-Trade-Recovery.zip`.

**Outputs:**
- `Puppy-Clicker-Minimal-Onboarding-<sha>-Permanent-v4.apk`
- matching `.idsig`
- `APK-VERIFICATION-Minimal-Onboarding.txt`
- ZIP bundle.

- [ ] **Step 1: Confirm final GitHub Actions run**

Require:
- JVM regression tests: success;
- Android instrumentation compile: success;
- unsigned release build: success.

- [ ] **Step 2: Download that exact unsigned artifact**

Never sign an artifact from an older commit.

- [ ] **Step 3: Extract signing pack privately**

Read keystore, alias and passwords without exposing secrets in chat.

- [ ] **Step 4: Verify certificate continuity**

Compare the permanent signing key fingerprint with a previously permanent-signed Puppy Clicker APK. Abort if mismatched.

- [ ] **Step 5: Sign**

```bash
java -jar apksigner.jar sign   --ks "<keystore>"   --ks-key-alias "<alias>"   --ks-pass "pass:<store-password>"   --key-pass "pass:<key-password>"   --v1-signing-enabled true   --v2-signing-enabled true   --v3-signing-enabled true   --v4-signing-enabled true   --out "Puppy-Clicker-Minimal-Onboarding-<sha>-Permanent-v4.apk"   "<unsigned-release.apk>"
```

- [ ] **Step 6: Verify APK + v4 sidecar**

```bash
java -jar apksigner.jar verify --verbose --print-certs   --v4-signature-file "Puppy-Clicker-Minimal-Onboarding-<sha>-Permanent-v4.apk.idsig"   "Puppy-Clicker-Minimal-Onboarding-<sha>-Permanent-v4.apk"
```

Record v2/v3/v4 results, certificate SHA-256, final source SHA and workflow run ID.

- [ ] **Step 7: Package**

Bundle APK, `.idsig`, and verification report into `Puppy-Clicker-Minimal-Onboarding-Permanent-v4.zip`.

- [ ] **Step 8: Final evidence gate**

Before claiming completion, confirm final repo HEAD, workflow SHA, unsigned artifact SHA lineage, permanent certificate match, v4 sidecar verification, and output file existence.
