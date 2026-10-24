# Puppy Clicker — React Native Android Migration

This is a genuine React Native / Expo Android application, **NOT a website wrapper**. Its React components draw Android UI with React Native views and native touch handling.

**Status:** PLAYABLE LOCAL GUEST PREVIEW ONLY — not a feature-complete production replacement. The signed Kotlin/Compose app in [`../App/`](../App/) remains the sole existing Google Play release.

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
- Casino Chips conversion; slots, roulette, fixed-wager scratchers, Plinko, Lucky Wheel, Blackjack with physical 52-card deck, Hit/Stand/Double/Split and persisted local round.

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

## Test APK from GitHub Actions

Manually run [React Native Preview APK](../.github/workflows/react-native-preview-apk.yml) via GitHub Actions → Run workflow on the migration branch. It compiles and uploads a **debug-signed installable preview APK** named `puppy-clicker-react-native-preview-debug`. It uses `PUPPY_NATIVE_PREVIEW=1` and package `com.harleytg.puppyclicker.preview`, so it cannot overwrite the current signed app. The artifact is **not** the production Google Play AAB.

## Artwork

The original logo and all **30 V1/V2/Discord puppy PNG portraits** are bundled directly from the canonical `assets/` Git blobs for immediate offline display in React Native. Unlocking restricted or role-gated puppies still requires verified official entitlements; including a picture does not grant ownership. Future V3+ streamed art must be validated before native rendering.

## Detailed migration audit

See [PARITY.md](PARITY.md) for a feature-by-feature status against the Kotlin source and the mandatory Play cutover checklist. The list is intentionally explicit about missing 1:1 behavior.

## Production release guard

`Mobile/app.config.js` always chooses `com.harleytg.puppyclicker.preview` unless **both** `PUPPY_NATIVE_PRODUCTION=1` and `PUPPY_NATIVE_RELEASE_APPROVED=1` are set, and the preview flag is absent. This is a build-time safeguard, **not proof** of parity or permission to publish. Release candidates require manual confirmation of original signing, verified Supabase/PupEye authority and Kotlin-to-RN data migration, plus signed Play internal testing. Do not set the release approval flag just to generate an APK.
