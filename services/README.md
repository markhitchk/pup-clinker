# Puppy Clicker support reporting relay

The Android app sends optional diagnostics and crash reports only when the player has enabled the matching consent toggle.

## Server configuration

Deploy `puppy-support-relay.php` behind HTTPS and configure these server environment variables:

- `PUPPY_DISCORD_TELEMETRY_WEBHOOK`
- `PUPPY_DISCORD_CRASH_WEBHOOK`
- `PUPPY_DISCORD_USER_REPORT_WEBHOOK_ENC`
- `PUPPY_SUPPORT_WEBHOOK_KEY_B64`

Tier 1 user reports use an AES-256-GCM encrypted webhook value. The encrypted value format is:

```
v1:<base64 12-byte IV>:<base64 16-byte GCM tag>:<base64 ciphertext>
```

Generate a fresh encrypted value with:

```bash
php services/encrypt-support-webhook.php
```

Store the encrypted blob and 32-byte key only as server secrets/environment variables. Never place the plaintext webhook, encrypted blob, or key in Android resources, BuildConfig, feature flags, or source control. A dedicated Tier 1 user-report Discord channel is recommended.

Configure the Android build with the public relay URL:

- `PUPPY_SUPPORT_RELAY_URL=https://your-domain.example/puppy-support-relay.php`

For GitHub Actions, create the repository variable `PUPPY_SUPPORT_RELAY_URL`. The workflow already passes that variable to Gradle.

## Data sent

Anonymous diagnostics currently send only the event name, app version/code, Android SDK level, package name, and timestamp.

Crash reports additionally send the crashing thread name, exception class, a bounded exception message, and a bounded stack trace.

Manual Tier 1 user reports use the same Android support subsystem and relay. The app sends a local report ID, category, subject, bounded report body, app version/code, Android SDK, and package name. Optional identity and sanitized diagnostics are included only when the existing Privacy & Data consent settings allow them.

A manual report is shown as **Submitted** only after the relay returns a successful Discord delivery response. If the relay is unavailable, the report stays local and Copy/Android Share remain available as fallback delivery.

A separate **Include account identity in support reports** toggle is OFF by default. When the player explicitly enables it, reports may additionally include the Puppy Clicker username, public Player ID, Friend Code, linked Discord display name/username, and Discord user ID.

OAuth tokens, birthdays, Treat balances, save contents, Android ID, IMEI, advertising ID, phone number, and other device identifiers are not included.

Discord messages are branded as **Puppy Clicker Support** and use the public `assets/logos/puppy_clicker.png` logo as the webhook avatar/embed icon.
