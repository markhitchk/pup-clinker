# File Wiper Android APK build branch

This separate branch is an isolated File Wiper native Java Android project; it does not alter Puppy Clicker's main branch.

Choose local files through Android's document picker. The app makes 1 or 3 best-effort logical overwrite passes, verifies each pass with SHA-256 readback, then attempts deletion only when all checks succeed. It skips cloud/unsupported providers and files without known size, seekable read/write access or deletion support.

**Warning:** Due to flash wear-leveling, copy-on-write, backups and storage-controller behavior, successful logical overwrite does not guarantee physical forensic erasure. An interruption may corrupt a file without deleting it.

No all-files permission, no Internet permission, no analytics.
The workflow builds a debug-signed APK for testing.
