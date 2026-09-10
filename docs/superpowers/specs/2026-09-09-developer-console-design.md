# Puppy Clicker Developer Console Design

## Goal
Add a hidden Developer Mode to Puppy Clicker Settings that unlocks after seven taps on the Build row in About and exposes a read-only, app-scoped diagnostic console.

## Scope
Developer Mode is diagnostics-only. It does not expose Android-wide logcat, execute commands, modify save data, bypass PupEye, unlock content, or provide privileged device access.

## Unlock and persistence
- About Puppy Clicker keeps Version, Build, Developer, Discord, and legal information.
- Build becomes tappable.
- Seven taps unlock Developer Mode. Before the seventh tap, Settings displays the number of taps remaining.
- The unlocked flag persists across launches and application updates in `puppy_developer_preferences_v1`.
- Reset Settings does not disable Developer Mode.
- Delete Local Save Data clears the Developer Mode preference, matching a local-data reset.
- When unlocked, Settings shows a collapsible `Developer Options` card with an `Open Developer Console` action.

## Diagnostic pipeline
`PuppyDebugLog` is the single app-owned diagnostic facade. It forwards diagnostics to Android `Log` while retaining a sanitized in-memory copy for the console.

Each entry contains:
- timestamp in milliseconds
- level: DEBUG, INFO, WARN, or ERROR
- tag
- sanitized message

The console retains at most 500 entries and drops the oldest entries first. It does not persist diagnostic history to disk.

The generated-source patch routes Puppy Clicker calls to `Log.d`, `Log.i`, `Log.w`, and `Log.e` through `PuppyDebugLog` so existing diagnostics from PupEye, streamed assets, roster loading, save compatibility, notifications, application lifecycle, and other app-owned code appear in the console while continuing to reach Logcat.

## Redaction
Console-bound text is sanitized before storage. Redaction covers common credential/value assignments such as token, authorization, password, secret, API key, session, keystore/encryption key, save payload, and birthday values. Email addresses and obvious Android data-storage paths are also masked. Throwable class names may be shown; throwable messages pass through the same sanitizer. Full stack traces remain Logcat-only and are not retained by the in-app console.

## Console UI
The Developer Console opens inside the Settings surface rather than launching another Activity. It provides:
- Back
- current total, warning, and error counts
- search by tag/message
- independent DEBUG / INFO / WARN / ERROR filters
- newest-first read-only log list
- Copy Visible Logs
- Clear Console

The console has no text-command input.

## Performance and safety
- Maximum retained entries: 500.
- No disk-backed console history.
- No network work performed by the console itself.
- Logging must never block normal app behavior or throw back into callers.
- Redaction occurs before an entry is added to the in-memory buffer.
- The feature must build through Puppy Clicker's generated-source pipeline and preserve existing reduced-motion/theme behavior.

## Verification
Pure JVM tests cover the seven-tap unlock state machine, redaction, 500-entry retention, ordering, and clearing. The Android release build must complete after the tests pass.
