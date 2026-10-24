# Puppy Clicker — React Native Android Migration

This is a genuine React Native / Expo Android application, **NOT a website wrapper**. Its React components draw Android UI with React Native views and native touch handling.

**Status:** PLAYABLE LOCAL GUEST PREVIEW ONLY — not a feature-complete production replacement. The signed Kotlin/Compose app in [`../App/`](../App/) remains the sole existing Google Play release.

## Versions / Android identity

- Expo SDK 57 + React Native 0.86 + React 19.2 (React Native new architecture).
- Package: `com.harleytg.puppyclicker` (same as existing Android).
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

The package ID matches the Play app. Use `PUPPY_NATIVE_PREVIEW=1 npx expo run:android` to install the separately named `com.harleytg.puppyclicker.preview` test app alongside the existing Play app. **Never** try to install an untrusted debug build over the Play install; original Play signing is different. Do not uninstall production to test without securely backing up and verifying original native cloud data; use a separate emulator/test profile instead.

## Screens implemented in React Native

- Native Play / tap interactions, four-currency wallet, combo and 10-tap bonus.
- Care meters, per-pup care action, active dog park and daily reward.
- Upgrade Shop with Bone/Ticket gating; ticket shop, accessory options.
- Prestige reset and permanent skill tree.
- Roster selection, simple rewards, Settings and guest reset.
- Casino Chips conversion; slots, roulette, scratchers, Plinko, Lucky Wheel, basic Blackjack (Hit/Stand).

`src/engine/` contains a **copied, traceable starting point** of the earlier web Android-derived economy. Those engines are not a full reimplementation of the giant Kotlin ViewModel, nor are they currently verified against every Android state transition.

## SECURITY AND SAVE BOUNDARY — IMPORTANT

- The preview stores a **guest-only, device-scoped** cache via AsyncStorage.
- It is **not** the authoritative Android Supabase save, has no Discord OAuth or device binding, and offers **no** imports from the installed Android app.
- Do not treat this cache as trusted achievements, XP, rare drops, in-game premium currency, or leaderboard proof.
- PupEye bans, appeals and bot admin synchronization are not wired into this preview. **Never release it as production** before original anti-cheat and account enforcement are ported.
- Never put a Supabase service-role key or Discord bot secret inside the shipped JavaScript bundle.

## Completion blockers

Full parity still requires original onboarding, achievements/XP, 100% native character assets, animated casino tables with split/double/insurance, Gacha/Exchange, code redemption, Discord OAuth and role verification, server-authoritative save/device migration, PupEye anti-cheat, notifications and widget parity, and real-device tests. See [migration specification](../docs/superpowers/specs/2026-10-23-react-native-android-migration.md).

## CI

`react-native-android.yml` runs Node economy/state tests and attempts an Android Metro export and native prebuild on GitHub-hosted CI. These checks do not prove Google Play signing or user-data migration. Use the signed original-key AAB and Google Play internal test track for actual replacement, only after migration gates pass.

## Test APK from GitHub Actions

Manually run [React Native Preview APK](../.github/workflows/react-native-preview-apk.yml) via GitHub Actions → Run workflow on the migration branch. It compiles and uploads a **debug-signed installable preview APK** named `puppy-clicker-react-native-preview-debug`. It uses `PUPPY_NATIVE_PREVIEW=1` and package `com.harleytg.puppyclicker.preview`, so it cannot overwrite the current signed app. The artifact is **not** the production Google Play AAB.
