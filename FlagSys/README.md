# Puppy Clicker Remote Feature Flags

The Android app reads `FlagSys/flags.json` from the repository's `main` branch.

## Flag fields

Each entry under `flags` supports:

- `visible`: show or hide the feature entry in UI that knows this flag key.
- `enabled`: allow the shipped feature path to run.
- `status`: `released`, `beta`, `coming_soon`, or `disabled`.
- `releaseDate`: optional ISO date in `YYYY-MM-DD` format.
- `label`: human-readable feature name.
- `description`: short explanation shown in Feature Availability.

A feature is available only when `visible` and `enabled` are true, `status` is `released` or `beta`, and any configured `releaseDate` has arrived.

Feature flags can only control code already included in the installed app. Adding a new JSON key does not download or execute new Android code.

The app caches the last valid flag document and uses bundled fallbacks if GitHub is unavailable.
