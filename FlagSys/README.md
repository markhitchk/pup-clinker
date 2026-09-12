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

A feature is available only when:

1. `visible` is `true`
2. `enabled` is `true`
3. `status` is `released` or `beta`
4. `releaseDate` is empty/null or the date has arrived

A future `releaseDate` automatically keeps the feature unavailable until that day.

## Example

```json
"example_feature": {
  "visible": true,
  "enabled": true,
  "status": "released",
  "releaseDate": "2026-10-01",
  "label": "Example Feature",
  "description": "Example remotely controlled feature."
}
```

## Important limitation

Feature flags can only control feature code already included in the installed app. Adding a brand-new JSON key does not download or execute new Android code. The app must already reference that key before it can control a feature.

The app caches the last valid flag document and uses bundled fallback values if GitHub is temporarily unavailable.
