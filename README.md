# Puppy Clicker

Puppy Clicker is organized as a multi-platform project. The existing native Android game and shared artwork are preserved; the website and desktop areas are reserved for their own implementations.

## Project folders

| Folder | Purpose |
| --- | --- |
| [`App/`](App/) | Existing signed Kotlin/Compose Android production app and its Play release pipeline. |
| [`Mobile/`](Mobile/) | **Primary React Native Android development source** (Expo SDK 57), currently a separate preview; no production account or Play cutover yet. |
| [`Website/`](Website/) | Website and browser edition. No website implementation is included yet. |
| [`assets/`](assets/) | Existing shared artwork and versioned puppy assets. Keep this path stable for all platforms. |
| [`Desktop/`](Desktop/) | Future desktop edition, installers, and platform-specific packaging. No desktop implementation is included yet. |

The root `.github/` directory contains repository automation. The root `.gitignore` and this README apply to the entire repository and are not separate products.

## Android

**Development direction:** React Native in [`Mobile/`](Mobile/) is the main-branch Android migration target. The original Kotlin/Compose source remains in `App/` as the shipping reference until Play migration gates are verified. The immutable Kotlin snapshot is in [`archive/kotlin-android-2026-10-24`](https://github.com/markhitchk/pup-clinker/tree/archive/kotlin-android-2026-10-24). Merging React source into `main` does not change the Google Play package, identity, signing, or existing players' save data. React preview builds use `com.harleytg.puppyclicker.preview` and MUST NOT be uploaded to Google Play as production.

React local Android notifications now follow the original two-hour recurring AFK attention cadence, using the selected puppy's latest known care state. The in-app notification inbox and Android channels are local guest features, not authenticated system broadcasts. Kotlin server messages, PupEye enforcement, Supabase save authority, device binding, Discord OAuth, and secure existing-user migration remain required before production.

The existing Android project is now in `App/`. Open that directory in Android Studio, or build from the repository root:

```bash
cd App
gradle :app:assembleRelease
```

The Android application ID, existing source, game data compatibility, version configuration, and permanent signing setup are unchanged. Build output is under `App/app/build/outputs/apk/`. See [`App/README.md`](App/README.md) and [`App/SIGNING.md`](App/SIGNING.md).

## Shared assets

Use the existing [`assets/`](assets/) directory as the single source of shared artwork. Preserve its existing file names, character IDs, and V1/V2 organization. Platform implementations may generate or package their own optimized copies without altering the source assets. Do not create a second root-level assets folder or replace the original character art with placeholders.

## Website and desktop

The website and desktop folders have README files so Git tracks them. They are not claims that a playable browser or desktop version already exists. Their implementations can be added independently while reusing the shared assets and established Puppy Clicker game design.

## Repository history

This organization preserves the prior Android files and assets in Git history. Older commits and releases remain available. Paths in scripts or external documentation that referenced the old Android root must use `App/` for the current branch.

## Rights

Puppy Clicker is based on the original HarleyTG-O Puppy Clicker project. All rights reserved by the project owner; this repository does not declare the game open source.
