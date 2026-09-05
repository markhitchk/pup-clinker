# Puppy Clicker — Android

Native Android edition of **Puppy Clicker**, based on the original game concept and artwork from [`HarleyTG-O/Puppy-Clicker`](https://github.com/HarleyTG-O/Puppy-Clicker).

## What is included

- 🐶 Large tap-to-click puppy gameplay using the original `Images/pup.png`
- 🍪 Treat currency with lifetime progression and levels
- 🛒 Six upgrades for tap power and automatic treat generation
- ⏱️ Offline earnings, capped at 8 hours
- ✏️ Custom puppy name
- 🎀 Bandana, bow, and crown accessories
- 🏆 Six achievements
- 👁️ **Pup Eye** local anti-auto-click protection with short cooldowns
- 💾 Automatic offline save using Android SharedPreferences
- 🌗 Material 3 light/dark theme
- 📱 Native Kotlin + Jetpack Compose UI
- ⚙️ GitHub Actions build that uploads a debug APK artifact

## Original Puppy Clicker artwork

The build imports the original `pup.png` and `logo.png` directly from `HarleyTG-O/Puppy-Clicker`, pinned to source commit:

```text
ab844c9f5fd09f5f17b9bf577e75524f0b6edcd1
```

The images are downloaded only while building and are packaged into the APK as Android resources. The installed game itself does **not** need internet access.

## Android configuration

- Package: `com.harleytg.puppyclicker`
- Minimum Android: 8.0 / API 26
- Target Android API: 35
- Compile API: 35
- Kotlin: 2.0.21
- Java: 17
- Version: `1.0.0` (`versionCode 1`)

## Build

### Android Studio

Open this repository as a Gradle project, allow Gradle sync to finish, then run the `app` configuration. Internet access is required during the build to resolve normal Gradle dependencies and retrieve the two pinned Puppy Clicker source images.

### Command line

With Java 17, Android SDK 35, and Gradle 8.9 installed:

```bash
gradle :app:assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions also builds the same APK automatically after pushes to `main` and uploads it as the **puppy-clicker-debug** artifact.

## Pup Eye

The Android edition keeps Pup Eye intentionally local and privacy-preserving. It watches only tap timing inside the app. Extremely fast tap bursts trigger a short cooldown and increment the local cooldown counter. No webhook, account identifier, device identifier, or personal information is sent anywhere.

## Rights

Puppy Clicker is based on the original HarleyTG-O Puppy Clicker project. All rights reserved by the project owner; this repository does not declare the game open source.
