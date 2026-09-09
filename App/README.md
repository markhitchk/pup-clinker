# Puppy Clicker — Android

This is the existing native Android edition of Puppy Clicker, moved from the repository root without changing its game source or application identity. It uses Kotlin, Jetpack Compose, Java 17, and Android SDK 35.

## Open and build

Open this `App/` directory as the Gradle project in Android Studio. From a terminal at the repository root:

```bash
cd App
gradle :app:assembleDebug
gradle :app:assembleRelease
```

Gradle 8.9, Java 17, Android SDK 35, and Python 3 are required by the existing build configuration. Release APKs are written to `App/app/build/outputs/apk/release/`. The existing GitHub Actions workflow uses the permanent signing secrets when configured and otherwise produces an unsigned release for external signing. See [`SIGNING.md`](SIGNING.md); never regenerate the permanent signing identity to fix a build.

## Existing project layout

- `app/` — Android application module, Kotlin source, resources, and existing packaged SVG artwork.
- `tools/` — SVG compiler, artwork checks, and original project utilities.
- `admin/` — Existing administration documentation.
- `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties` — Android project configuration.
- `SIGNING.md` — Permanent update-signing documentation.

The application ID remains `com.harleytg.puppyclicker`. The current Gradle configuration determines the effective version, rather than historical README version notes. No game mechanics, character IDs, Android package names, or signing keys were changed during this folder organization.

## Shared artwork

The canonical cross-platform artwork lives at [`../assets/`](../assets/). Existing Android source artwork remains under `app/src/main/puppy-svg/` so the current renderer and encryption build tasks continue to work. A future asset synchronization step can use the shared folder without changing the current game.

## Historical scripts

Repository-wide workflow files remain under `../.github/workflows/`. Historical one-off patch scripts under `../.github/scripts/` were preserved unchanged. They use paths relative to the old Android project root, so run them from `App/` only after reviewing their historical version assumptions. They are not part of the normal build.
