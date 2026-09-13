# Puppy Clicker support reporting relay

The Android app sends optional diagnostics and crash reports only when the player has enabled the matching consent toggle.

## Server configuration

Deploy `puppy-support-relay.php` behind HTTPS and configure these server environment variables:

- `PUPPY_DISCORD_TELEMETRY_WEBHOOK`
- `PUPPY_DISCORD_CRASH_WEBHOOK`

The two webhook URLs may target the same Discord support channel. Keep both values server-side; do not place them in Android resources, BuildConfig, feature flags, or source control.

Configure the Android build with the public relay URL:

- `PUPPY_SUPPORT_RELAY_URL=https://your-domain.example/puppy-support-relay.php`

For GitHub Actions, create the repository variable `PUPPY_SUPPORT_RELAY_URL`. The workflow already passes that variable to Gradle.

## Data sent

Anonymous diagnostics currently send only the event name, app version/code, Android SDK level, package name, and timestamp.

Crash reports additionally send the crashing thread name, exception class, a bounded exception message, and a bounded stack trace.

The client intentionally does not add usernames, birthdays, Discord identities, Treat balances, save contents, or a persistent device identifier.
