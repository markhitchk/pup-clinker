# Puppy Clicker — React Native Android Migration

This is a genuine React Native / Expo Android application, **NOT a website wrapper**. Its React components draw Android UI with React Native views and native touch handling.

**Status:** LOCAL GUEST PREVIEW SOURCE PORT ONLY — build and device parity not yet certified; not a feature-complete production replacement. The signed Kotlin/Compose app in [`../App/`](../App/) remains the sole existing Google Play release.

## Versions / Android identity

- Expo SDK 57 + React Native 0.86 + React 19.2 (React Native new architecture).
- Reserved Google Play package: `com.harleytg.puppyclicker` (unchanged original identity); all normal builds default to the **separate** `com.harleytg.puppyclicker.preview`.
- Version code: `35` (current Kotlin source code: `34`). Google Play requires monotonic increment and original upload signing key.
- Android target SDK 36 via SDK 57 defaults.
- No `WebView` package or browser-embedded UI.

## Run locally

Use Node.js 22+, Android Studio + SDK and a test device/emulator (not the device with your signed production app installed):

```bash
cd Mobile
npm install
npx expo install --fix
npm test
npx expo start --android
# To generate Android source and build a native test APK:
npx expo run:android
# To prepare a generated Android Gradle project:
npx expo prebuild --platform android
```

The `app.json` reserves the future Play package ID, but **runtime configuration defaults to the safe preview package**. Use `npx expo run:android` (or `PUPPY_NATIVE_PREVIEW=1 npx expo run:android`) to install the separately named `com.harleytg.puppyclicker.preview` test app alongside the existing Play app. **Never** try to install an untrusted debug build over the Play install; original Play signing is different. Do not uninstall production to test without securely backing up and verifying original native cloud data; use a separate emulator/test profile instead.

## Screens implemented in React Native

- Native Play / tap interactions, four-currency wallet, combo and 10-tap bonus.
- Care meters, per-pup care action, active dog park and daily reward.
- Upgrade Shop with Bone/Ticket gating; ticket shop, accessory options.
- Prestige reset and permanent skill tree.
- Roster selection, local favorites/search, nine source achievements, guest XP, Settings and guest reset.
- Six-step guest onboarding: welcome, player setup, personalization, privacy, notifications, ready.
- Casino Chips conversion; six dedicated Kotlin-sourced game screens: Slots, Roulette, Scratchers, Plinko, Lucky Wheel and Blackjack with physical 52-card deck, Hit/Stand/Double/Split and persisted local round.

`src/engine/` contains a **copied, traceable starting point** of the earlier web Android-derived economy. Those engines are not a full reimplementation of the giant Kotlin ViewModel, nor are they currently verified against every Android state transition.

## SECURITY AND SAVE BOUNDARY — IMPORTANT

- The preview stores a **guest-only, device-scoped** cache via AsyncStorage.
- It is **not** the authoritative Android Supabase save, has no Discord OAuth or device binding, and offers **no** imports from the installed Android app.
- Do not treat this cache as trusted achievements, XP, rare drops, in-game premium currency, or leaderboard proof.
- PupEye bans, appeals and bot admin synchronization are not wired into this preview. **Never release it as production** before original anti-cheat and account enforcement are ported.
- Never put a Supabase service-role key or Discord bot secret inside the shipped JavaScript bundle.

## Completion blockers

Full parity still requires verified real-account onboarding, protected/streamed V3+ character entitlements, pixel-level casino art and animations, Exchange, Puppy Codes, Discord OAuth/role verification, server-authoritative cloud/device migration, PupEye anti-cheat, native notifications/widgets, and real-device tests. See [the detailed parity audit](PARITY.md). See [migration specification](../docs/superpowers/specs/2026-10-23-react-native-android-migration.md).

## CI

`react-native-android.yml` runs Node economy/state tests and attempts an Android Metro export and native prebuild on GitHub-hosted CI. These checks do not prove Google Play signing or user-data migration. Use the signed original-key AAB and Google Play internal test track for actual replacement, only after migration gates pass.

## Installable standalone preview APK from GitHub Actions

Use [Puppy Clicker React Native Preview APK](../.github/workflows/react-native-preview-apk.yml) in GitHub Actions → **Run workflow** and select the `feat/react-native-full-android` branch, or take the latest successful automatic run after a change to `Mobile/**`. Download the `puppy-clicker-react-native-preview-standalone-apk` artifact and install the APK on an Android device.

The workflow runs Gradle **`assembleRelease`** (signed with the template's **debug key**, **not** the owner's Google Play signing key), so **`assets/index.android.bundle` is embedded** in the APK. No Metro server, computer, or USB connection is required to open the standalone preview. CI verifies that the bundle exists inside the APK, that the package ID is `com.harleytg.puppyclicker.preview`, and that the APK signature is valid. **Do not use `assembleDebug` APKs for standalone installation**: those expect Metro and show the red "Unable to load script" screen when it is unavailable.

The preview remains separately installed from the Kotlin Google Play app (`com.harleytg.puppyclicker`). **Never uninstall the production game to troubleshoot the preview**, as the native game and its authoritative cloud data must be preserved. If Android reports "App not installed" due to a different signing certificate, remove **only** the `Puppy Clicker Preview` test app before installing the latest preview APK. Removing the preview deletes its guest-only local test data. It is **not** a production-ready Play AAB or a verified feature-complete migration.

## Artwork

The original logo and all **32 V1/V2/Discord and Kotlin test puppy PNG portraits** are bundled directly from the canonical `assets/` Git blobs for immediate offline display in React Native. Unlocking restricted or role-gated puppies still requires verified official entitlements; including a picture does not grant ownership. Future V3+ streamed art must be validated before native rendering.

## Detailed migration audit

See [PARITY.md](PARITY.md) for a feature-by-feature status against the Kotlin source and the mandatory Play cutover checklist. The list is intentionally explicit about missing 1:1 behavior.

## Production release guard

`Mobile/app.config.js` always chooses `com.harleytg.puppyclicker.preview` unless **both** `PUPPY_NATIVE_PRODUCTION=1` and `PUPPY_NATIVE_RELEASE_APPROVED=1` are set, and the preview flag is absent. This is a build-time safeguard, **not proof** of parity or permission to publish. Release candidates require manual confirmation of original signing, verified Supabase/PupEye authority and Kotlin-to-RN data migration, plus signed Play internal testing. Do not set the release approval flag just to generate an APK.

## Part 6 Android build and screenshot verification

GitHub workflow [React Native Preview APK](../.github/workflows/react-native-preview-apk.yml) must finish green before an APK artifact is advertised as working. On October 24, 2026, workflow run [40068347188](https://github.com/markhitchk/pup-clinker/actions/runs/40068347188) failed **8 of 147** Node tests and did **not** produce a verified APK. Part 6 commits fix the Wheel width crash and stale asset/navigation/daily-goal assertions; verify a **new completed** workflow run before claiming that those fixes pass CI or that an APK exists.

For actual image parity rather than source assumptions, follow [PART_6_DEVICE_QA.md](PART_6_DEVICE_QA.md) and [VISUAL_PARITY_AUDIT.md](VISUAL_PARITY_AUDIT.md). `scripts/android_visual_audit.py` compares original Kotlin and React screenshots at identical size, exports changed-pixel percentages and heatmaps, and refuses missing screenshots. Run on the same physical Android device with matching states. **No comparison images have been collected in this repository; 100% visual parity is not verified.**


### Restored settings and local notifications (October 24)

Open Settings from the header gear or Settings tab. Appearance and Gameplay choices persist in the existing guest save. The header bell opens the notification inbox. Android alerts require permission from Settings → Notifications; no push token or Firebase project is requested. Daily and park reminders are recalculated when opening the app or changing the relevant game/preferences; puppy attention is scheduled only while away. These reminders do not import the original Kotlin history or issue trusted AFK rewards.

The custom hardware cursor requires the standalone Android build (`expo prebuild` applies the config plugin); Expo Go does not contain this native view. Keep `App/` as the original Kotlin reference. The preview launcher uses the original source drawable and preserves the separate preview package.
