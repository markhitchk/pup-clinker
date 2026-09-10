# Puppy Clicker Save, Onboarding, and HUD Repair Design

Date: 2026-09-09
Status: Design approved in chat; awaiting written-spec review before implementation planning
Repository: `markhitchk/pup-clinker`
Scope: Android app under `App/`

## Goals

This change repairs the broken device-bound encrypted save mirror and cleans up the onboarding/game UI issues identified from the current build.

The finished app must:

- successfully create and update the encrypted Android/data mirror without `InvalidAlgorithmParameterException`;
- preserve Android Keystore AES-256-GCM protection and randomized IV enforcement;
- keep the internal SharedPreferences save as the runtime source of truth;
- stop repeated encryption-warning spam in the developer console;
- remove the globally overlaid PupEye logo after setup while retaining PupEye branding in intentional setup/security surfaces;
- redesign setup step 2 around a local profile and future account connections;
- store birthday as month/day only, with no year requirement for local or future Discord accounts;
- remove the fake Puppy Coins preview from setup;
- move ticket-found feedback to a non-layout-shifting transient overlay;
- preserve existing game resources, save compatibility, setup migration, and anti-tamper behavior.

## Root Cause

`PuppySaveCrypto.encryptDevice()` currently creates a 12-byte IV in application code and passes it to:

```kotlin
cipher.init(Cipher.ENCRYPT_MODE, deviceKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
```

The Android Keystore key is created with:

```kotlin
.setRandomizedEncryptionRequired(true)
```

For Android Keystore AES-GCM encryption, that combination is invalid on providers that enforce randomized encryption. The provider requires generating the encryption IV itself and rejects a caller-provided encryption IV with:

`InvalidAlgorithmParameterException: Caller-provided IV not permitted`

The external mirror writer is triggered repeatedly by save-state preference changes, so the same deterministic failure appears many times in the developer console.

The Android/data location itself is not the root cause.

## Save-Crypto Design

### Device-bound encryption

Keep the current Android Keystore AES key alias and key properties unless compatibility testing proves an alias migration is required.

Encryption flow:

1. Obtain the Android Keystore AES key.
2. Create `Cipher.getInstance("AES/GCM/NoPadding")`.
3. Call `cipher.init(Cipher.ENCRYPT_MODE, deviceKey())` with no caller-supplied `GCMParameterSpec`.
4. Read `cipher.iv` after initialization.
5. Require the generated IV to be non-empty and exactly the expected GCM IV length used by the container format.
6. Apply the existing device AAD.
7. Encrypt the plaintext.
8. Persist the existing binary envelope shape: magic header + generated IV + ciphertext/tag.

Decryption flow remains conceptually unchanged:

1. Validate the magic header and minimum length.
2. Extract the stored IV.
3. Initialize AES-GCM in decrypt mode with `GCMParameterSpec` containing that stored IV.
4. Apply the same AAD.
5. Authenticate and decrypt.

Caller-supplied IVs are valid and required for decryption; only encryption initialization changes.

### Transfer-save encryption

Password-protected transfer saves use a software-derived AES key rather than an Android Keystore key. Their random salt/IV envelope is independent and should not be changed as part of this repair unless tests reveal a separate defect.

### Corrupt or incompatible save files

Existing files that cannot authenticate must remain non-fatal. They may be quarantined according to the current PupEye behavior.

A provider/configuration failure must not be misclassified as user tampering. The implementation must distinguish:

- authentication/ciphertext-integrity failures, which are valid PupEye tamper signals;
- local crypto-provider/key initialization failures, which are operational failures and should be logged without incrementing the tamper counter;
- external-storage access failures, which are mirror-availability failures rather than tamper events.

## External Save Write Scheduling and Logging

The SharedPreferences listener currently debounces state changes into mirror writes. That architecture can remain.

The repaired implementation must avoid console flooding if the mirror enters a repeated operational failure state. Logging should retain enough diagnostic detail for development while coalescing identical repeated failures.

Recommended behavior:

- log the first failure immediately;
- suppress identical repeats for a bounded cooldown period or until the failure state changes;
- log successful recovery after a previously failed mirror write;
- do not disable automatic mirror writes permanently after one failure;
- preserve non-fatal fallback to internal save state.

The exact coalescing mechanism may be a small in-memory failure signature/timestamp in `ExternalGameSave` or the application save scheduler. It must not affect persisted game state.

## Onboarding Flow

The onboarding flow remains six steps unless implementation inspection reveals an explicit product reason to change the count.

### Step 1: Welcome

Retain the Puppy Clicker logo, PupEye-protected messaging, Discord community card, legal links, and Get Started action.

PupEye branding on this screen remains streamed from the repository.

### Step 2: Your Profile

Replace the current flat username plus two status rows with two clear sections.

#### Local Profile

- Heading: `Your Profile` or equivalent existing setup heading style.
- Username field using existing username normalization and length constraints.
- Small local-device status such as `Stored on this device`.
- Supporting copy explaining that the normalized username is associated with the protected Puppy Clicker save.
- Continue is enabled only when the normalized username is valid/non-empty.

#### Connect an account

Show future account providers as disabled or unavailable connection cards/buttons rather than ordinary text rows:

- Discord — Coming Soon
- Website — Coming Soon

The UI should make the local profile the active path and connected accounts an optional future path.

The design must not introduce working Discord or website authentication in this change.

## Birthday Model

Birthday is month/day only.

### Persistence

`PuppyUiState` and `PuppyUiPreferences` should stop requiring `birthdayYear` for birthday validity.

The preferred target model is:

- `birthdayMonth: Int`
- `birthdayDay: Int`
- no active year field in the public UI state contract

If removing the persisted legacy year key immediately would make migration unnecessarily risky, the implementation may continue reading/ignoring the legacy key for compatibility while no longer exposing or writing it in normal flows.

### Validation

A valid birthday requires:

- month in 1..12;
- day valid for that month;
- February 29 must be accepted as a valid recurring birthday without needing a specific leap year.

Do not use `LocalDate.of(year, month, day)` for normal birthday validity because there is no year.

### Setup UI

Step 3 contains exactly:

- Month picker
- Day picker

Remove the Year picker and all age/year-related validation/copy.

### Settings UI

The birthday editor in Settings must use the same month/day-only model and validation.

### Existing installs

For an existing saved birthday with month/day/year:

- preserve month/day;
- ignore or remove the stored year during migration/next write;
- do not force onboarding to replay solely because the year field disappears.

For legacy installs that already contain month/day with year `0`, preserve the birthday as valid.

### Seasonal behavior

Birthday-related rewards remain keyed to month/day and continue through the existing seasonal birthday path.

## Appearance Setup

Keep:

- Light/Dark/System choices;
- accent presets;
- custom-color note pointing users to Settings.

Remove the hard-coded preview containing:

- `Puppy Coins: 15,250`
- `BUY`

Puppy Clicker does not use Puppy Coins.

Replace it with a non-economic visual preview that demonstrates the selected theme and accent. It may show:

- app/card title;
- short preview label;
- sample accent button or chip;
- surface/background contrast.

The preview must not imply a currency, purchase, store balance, or monetization system that does not exist.

## Notification Setup

The current notification step is not a primary redesign target. Preserve the existing optional-notification behavior unless a code dependency must change to support the other work.

No new notification subsystem should be added in this repair.

## PupEye Branding Placement

### Remove global overlay

`PuppyClickerTheme()` must stop rendering `StreamedPupEyeBranding` as a global top-right overlay.

The theme layer should provide theme/density/motion configuration only and must not inject security branding over every screen.

### Keep PupEye where intentional

Retain streamed PupEye branding in intentional surfaces such as:

- onboarding/welcome protection card;
- onboarding top-right badge if retained by the onboarding container;
- PupEye Protection/Security section in Settings;
- other dedicated PupEye status/security pages already intended to show the brand.

### After setup

After setup completion, ordinary game screens must have no floating top-right PupEye watermark:

- Play
- Care
- Shop
- Prestige
- normal Settings navigation/content outside dedicated PupEye sections

The underlying PupEye security functionality remains active; only the global visual overlay is removed.

## Ticket-Found Feedback

The current ticket-found surface participates in normal column layout immediately after the wallet, causing content to move when the notification appears/disappears.

Convert ticket-found feedback into a transient overlay.

### Placement

Preferred placement:

- horizontally centered;
- below the wallet/stat area;
- above the puppy play card/content;
- rendered in an overlay/Box layer so it does not consume normal layout height.

### Content

Use compact copy, for example:

- primary: `Common Ticket +1`
- secondary: `Ticket Upgrades`

Retain rarity-specific container styling if practical.

### Behavior

- show on a new ticket-drop event;
- remain visible approximately 2.5-3 seconds;
- animate with the existing reduced-motion rules;
- disappear without shifting any persistent game UI;
- repeated drops should update/restart the banner rather than stack multiple overlapping banners.

## Files Expected to Change

Primary expected files:

- `App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/ExternalGameSave.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerApplication.kt` if failure coalescing belongs in scheduling
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyUiPreferences.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyOnboardingUi.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/ui/theme/Theme.kt`
- `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt`

Test files may be added or updated under the existing Android unit/instrumentation test layout.

Patch-generation helper scripts under `App/tools/` must only be changed if the build actually regenerates affected source from those scripts. Source and generator must not be allowed to diverge.

## Data Flow After Repair

### Runtime save

Gameplay writes internal SharedPreferences -> debounced listener schedules protection/mirror work -> PupEye seal writes encrypted last-known-good private copy -> external mirror serializes metadata + preferences -> Android Keystore AES-GCM encrypts with provider-generated IV -> encrypted `.pup` file replaces previous mirror atomically.

### Startup

Application starts -> PupEye verifies internal seal -> compatibility normalization runs -> dynamic data initializes -> external mirror is verified/written non-fatally -> app proceeds to onboarding or game UI according to setup state.

### Onboarding completion

User creates local profile -> selects month/day birthday -> chooses appearance -> notification preference step -> finish step verifies protection status -> setup is marked complete -> ordinary app UI renders without global PupEye overlay.

## Error Handling

- Crypto provider/key initialization failures: operational log, no tamper increment.
- GCM authentication failures on an existing encrypted file: PupEye tamper event + quarantine according to existing policy.
- External storage unavailable: mirror reports unavailable; app continues with internal save.
- Atomic replacement failure: keep internal save authoritative and report mirror failure.
- Invalid onboarding username: Continue disabled.
- Invalid month/day combination: Continue disabled or day choice constrained to valid range.
- Streamed PupEye asset unavailable: branding fails independently and never blocks app/setup.

## Testing Strategy

### Crypto unit/instrumentation coverage

Add a failing regression test first for the current caller-provided-IV behavior or an equivalent test that proves device encryption can initialize successfully with the Android Keystore provider.

Required verification:

- device encrypt/decrypt round trip succeeds;
- two encryptions of identical plaintext produce different IV/ciphertext;
- stored IV is used successfully for decryption;
- ciphertext/tag tampering fails authentication;
- AAD mismatch fails authentication;
- transfer-save encryption/decryption remains unchanged and passing.

Because AndroidKeyStore is not fully available in plain JVM tests, use instrumentation where necessary rather than mocking away the provider behavior that caused the bug.

### External-save tests

Verify:

- mirror write creates the expected `.pup` file;
- mirror can be verified/decrypted after write;
- a malformed/tampered encrypted file is handled non-fatally;
- provider/storage operational failures do not increment tamper count;
- repeated identical operational failures are coalesced in logging behavior where testable.

### Birthday tests

Verify:

- January 1 valid;
- April 31 invalid;
- February 29 valid as a recurring birthday;
- month/day survives legacy migration with year `0` or a prior real year;
- setup and Settings use the same validation semantics.

### UI/Compose tests

Verify:

- Step 2 shows Local Profile and disabled future account connection surfaces;
- username validity controls Continue;
- Step 3 has Month and Day but no Year;
- appearance step contains no `Puppy Coins` or `BUY` economic preview;
- completing setup removes the top-right global PupEye overlay;
- dedicated PupEye setup/settings surfaces still show streamed branding;
- ticket notification overlay does not change the measured position of the puppy/play content;
- reduced-motion behavior remains respected.

### Build/release verification

Before completion claims:

- run relevant unit tests;
- run Android instrumentation/Compose tests available in the project;
- run Gradle assemble/build for the target app variant;
- inspect build output for compiler/lint regressions introduced by these changes;
- if producing a release APK later, verify signing separately, including requested v4 signing where applicable.

## Non-Goals

This repair does not include:

- working Discord authentication;
- working website authentication;
- cloud saves;
- a Puppy Coins economy;
- new monetization;
- a replacement save architecture;
- removing PupEye security itself;
- redesigning every Settings screen;
- changing the existing resource model beyond the ticket-found presentation.

## Acceptance Criteria

The change is complete only when all of the following are true:

1. No normal device-save write produces `Caller-provided IV not permitted`.
2. Android/data encrypted mirror writes and verifies successfully on a supported Android device/emulator.
3. Internal saves remain authoritative and the app launches if the external mirror is unavailable.
4. Operational crypto/storage failures are not falsely counted as tampering.
5. Developer Console is not flooded by identical external-save failures.
6. Setup Step 2 presents a clear local-profile path plus disabled Discord/Website future connections.
7. Birthday requires only month/day everywhere.
8. February 29 can be stored without requiring a year.
9. Appearance setup contains no Puppy Coins or BUY preview.
10. Ticket-found feedback overlays the game without moving the persistent layout.
11. PupEye is not globally overlaid after setup.
12. PupEye remains visible in intentional setup/security surfaces.
13. Existing users retain profile/game progress and valid month/day birthday data through migration.
14. Regression tests and the Android build pass before the change is declared complete.
