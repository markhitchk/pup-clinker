# Puppy Clicker — Kotlin Android Archive

Archived on: 2026-10-24
Original source repository: https://github.com/markhitchk/pup-clinker
Original source branch: main
Original source commit: 4d01e469aecf62e927aa7674ebf7a75c258f66e1

This branch is a preservation snapshot of Puppy Clicker's original native **Kotlin / Jetpack Compose Android application**, not the React Native replacement.

Included:
- `App/` — unchanged Kotlin Android Gradle project, source, UI, resources, minigames, artwork, documentation, and tools
- `assets/` — shared artwork and resources referenced by the native build
- `.env` — the already-public app configuration from the original source commit (not Android signing credentials)
- `.gitignore` — original project ignore rules
- `.github/` — original CI workflows and helper scripts

Build locally:
1. Open the `App/` folder in Android Studio.
2. Use Java 17, Android SDK 36 and a compatible Gradle installation.
3. Run `gradle :app:assembleDebug` from the `App/` folder.
4. For Google Play release signing, supply the existing upload key through your secure CI environment; **private signing keys are not in this archive**.

The application ID is `com.harleytg.puppyclicker`.
The archive is intended as a backup/reference for verifying one-to-one parity while migrating to React Native.

This archive does not remove or change source files on `main` or `feat/react-native-full-android`.
