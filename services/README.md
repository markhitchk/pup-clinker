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

A separate **Include account identity in support reports** toggle is OFF by default. When the player explicitly enables it, reports may additionally include the Puppy Clicker username, public Player ID, Friend Code, linked Discord display name/username, and Discord user ID.

OAuth tokens, birthdays, Treat balances, save contents, Android ID, IMEI, advertising ID, phone number, and other device identifiers are not included.

Discord messages are branded as **Puppy Clicker Support** and use the public `assets/logos/puppy_clicker.png` logo as the webhook avatar/embed icon.
