# Puppy Clicker — Android

Native Android edition of **Puppy Clicker**, based on the original game concept and artwork from [`HarleyTG-O/Puppy-Clicker`](https://github.com/HarleyTG-O/Puppy-Clicker).

## Current game

- 🐶 Original `Images/pup.png`, `logo.png`, and `htg.png` preserved in the Android build
- 🍪 Tap-to-earn treat gameplay with levels, care, missions, daily rewards, and dog-park adventures
- 🛒 Shop upgrades for tap power and automatic treat production
- 🔒 **Tap power rule:** treats-per-tap can only increase through Shop CLICK upgrades
- 🔥 Combos remain visual/progression achievements and do **not** multiply tap rewards
- 🐕 Puppy Collection with Classic, Sunny, Mochi, Pepper, Midnight, and Cloud puppy styles
- 🎟️ RSA-signed `PC1` redeem codes for treats and special puppy unlocks
- ⚙️ Dedicated Settings screen for haptics, animations/reduced motion, compact numbers, redeem codes, app info, and reset controls
- 🎀 Bandana, bow, and crown accessories
- ⏱️ Offline earnings capped at 8 hours
- 👁️ Pup Eye local rapid-tap protection
- 💾 SharedPreferences save compatibility with earlier Android builds
- 🌗 Material 3 light/dark UI
- 📱 Native Kotlin + Jetpack Compose

## Original Puppy Clicker artwork

The build imports the original images from `HarleyTG-O/Puppy-Clicker`, pinned to source commit:

```text
ab844c9f5fd09f5f17b9bf577e75524f0b6edcd1
```

The artwork is downloaded at build time and packaged into the APK. The installed game itself does not need network access for the original assets.

## Android configuration

- Package: `com.harleytg.puppyclicker`
- Minimum Android: 8.0 / API 26
- Target Android API: 35
- Compile API: 35
- Kotlin: 2.0.21
- Java: 17
- Current version: `1.3.0` (`versionCode 4`)

## Update signing

Release/update builds use one permanent signing identity. Never regenerate or replace that key if existing installs must continue updating normally.

The repository does not contain the private Android signing key. GitHub Actions can consume it from these repository secrets:

- `PUPPY_KEYSTORE_B64`
- `PUPPY_SIGNING_STORE_PASSWORD`
- `PUPPY_SIGNING_KEY_ALIAS`
- `PUPPY_SIGNING_KEY_PASSWORD`

When those secrets are absent, CI produces an unsigned release artifact for external permanent signing rather than publishing a random debug-key build as an update.

## Redeem-code security

Redeem codes use this format:

```text
PC1.<base64url payload>.<RSA-SHA256 signature>
```

The Android app contains only the RSA **public verification key**. The private redeem-code key must remain outside the APK and outside GitHub.

Supported v1.3 reward types:

- `TREATS` — grants signed treat rewards
- `PUPPY` — unlocks special puppy styles such as `midnight` or `cloud`

Redeem codes intentionally cannot change `clickPower` or treats-per-tap.

A command-line generator is included at:

```text
tools/redeem-code-generator.py
```

Example:

```bash
python3 tools/redeem-code-generator.py \
  --private-key /secure/path/redeem-private.pem \
  --id event-001 \
  --type TREATS \
  --amount 500
```

Code IDs are recorded locally after redemption. Resetting game progress preserves that redeemed-code history. A server-side service would be required for globally one-time codes across multiple devices or a full app-data wipe.

## Build

With Java 17, Android SDK 35, and Gradle 8.9 installed:

```bash
gradle :app:assembleRelease
```

If the permanent signing environment variables are configured, the release is signed using that update identity. Otherwise the release is unsigned and must be signed externally before distribution.

## Pup Eye

Pup Eye remains local and privacy-preserving. It watches tap timing inside the app. Extremely fast tap bursts trigger a short cooldown and increment the local cooldown counter. It does not send account IDs, device IDs, tap logs, or personal information anywhere.

## Rights

Puppy Clicker is based on the original HarleyTG-O Puppy Clicker project. All rights reserved by the project owner; this repository does not declare the game open source.
