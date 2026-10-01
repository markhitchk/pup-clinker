# Puppy Clicker — Privacy Policy

> **Effective date:** October 1, 2026

This Privacy Policy explains how **Puppy Clicker**, provided by **Harley's Studios**, handles information in the Android app.

---

## 1. Local Game Data

Puppy Clicker stores the following information on the user's device:

- Game progress
- Settings
- Upgrade data
- Care statistics
- Achievements
- PupEye fair-play state
- Related gameplay information

## 2. Player Username

A player uses a Pup Account backed by Discord passwordless sign-in and may choose a Puppy Clicker display name.

The username:

- Is normalized to lowercase
- Is cached locally for gameplay and offline tolerance
- Is synchronized as part of the player's Pup Account identity where applicable

## 3. Device and Installation Security Metadata

PupEye security metadata may contain a coarse manufacturer/model label for the active Android device.

PupEye also creates security metadata that is specific to the Puppy Clicker installation, including:

- A random Puppy Clicker installation ID
- A public signing-key fingerprint
- A monotonic save generation number
- Random save and economy transaction identifiers
- Integrity-event and protected economy-ledger information

The corresponding private signing key is generated in Android Keystore and never leaves the device. The installation ID and signing-key fingerprint are app security identifiers, not hardware serial numbers.

Puppy Clicker does **not** use the following for save ownership:

- IMEI
- Hardware serial number
- Android ID
- Phone number
- Advertising ID
- Other persistent hardware identifiers

PupEye global enforcement also does not use SIM serial, Wi-Fi MAC, or Bluetooth MAC addresses as ban identifiers. A coarse device model, username, or IP address alone does not establish that two players are the same person or device.

## 4. Pup Account and Local Cache Security

Pup Account is the player progress system. Supabase-backed account data is authoritative after authenticated sign-in. Local gameplay data is an encrypted/integrity-protected cache used for responsive play and temporary offline tolerance.

Authentication failures, save rollback attempts, ownership mismatches, duplicate protected transactions, or unauthorized modifications may be recorded locally by **PupEye** and may cause a save or protected gameplay operation to be:

- Rejected
- Quarantined
- Restored from a last-known-good state
- Blocked pending Support review

Device transfer and account recovery use the authenticated Pup Account and PupEye device-authorization flow. No save file or backup password is required.

## 5. PupEye Fair-Play Information

PupEye may process local security information such as:

- Recent tap timing
- Fair-play strike counts
- Save-integrity results
- Related local security state

These checks are designed to detect likely automated clicking or unauthorized save modification. PupEye also uses Puppy Clicker's backend to register the app installation, compare save generations, reject replayed protected transactions, and authorize account-based device migration. Local timing samples used for click-pattern detection are not uploaded as part of the current backend integration.

## 6. Supabase Account and PupEye Service

Puppy Clicker may send the following limited information to the Puppy Clicker Supabase project for account security and PupEye enforcement:

- Puppy Clicker Player ID and Friend Code
- Local username
- Random installation ID and Support Installation Code
- Public device signing key and its fingerprint
- Coarse device model, Android platform label, and app version
- Highest authenticated save generation
- Protected Casino/Gacha transaction IDs, source labels, and bounded transaction details
- PupEye integrity/review event codes
- Pup Account progression and cloud revision metadata
- Discord identity and derived Puppy Clicker server role after Discord authorization

The corresponding Android Keystore private signing key is not uploaded. Discord OAuth access tokens are used transiently for server verification and are not intentionally stored by Puppy Clicker or in the PupEye database.

For global enforcement, PupEye may maintain a public Ban ID, Player/Discord/installation links, device reputation records, enforcement audit events, and protected save-generation metadata. These records help detect replay or tampering, require Support review, and enforce temporary or permanent bans. Game progression is stored in the Pup Account save; moderation alerts do not include the save contents.

Puppy Clicker does not use IMEI, hardware serial number, Android ID, SIM serial, phone number, advertising ID, Wi-Fi MAC, or Bluetooth MAC as global-ban identifiers. IP and network metadata may be considered as evidence, but do not independently trigger a device or account ban.

Operational events may be sent to Harley's Studios through a Discord moderation notification as a webhook embed. These notifications exclude session tokens, IP addresses, email addresses, save contents, and raw cryptographic key material.

Supabase may process ordinary network information, including IP address and request metadata, under Supabase's own privacy and security practices.

## 7. Streamed Content and Network Requests

Puppy Clicker may contact **GitHub-hosted resources** to retrieve:

- Streamed branding
- Legal documents
- Configuration
- Other app assets

When the app makes those requests, the hosting provider may receive ordinary network information such as:

- IP address
- Request time
- Technical connection details

That information is handled under the hosting provider's own policies.

## 8. Discord Signup

Puppy Clicker uses **Discord OAuth** for player signup and community features. The authorization flow requests the following scopes:

- `identify`
- `email`
- `guilds`
- `guilds.join`
- `guilds.members.read`

After successful authorization, Puppy Clicker may read account identity information including:

- Discord user ID
- Username
- Display name, when available
- Avatar identifier, when available
- Email address, when Discord returns it for the authorized account

The account identity metadata used by Puppy Clicker is stored locally and may also be stored in the Puppy Clicker Supabase project. Discord authorization is verified by a Puppy Clicker Supabase Edge Function before the account or configured Puppy Clicker server role is trusted. The app stores only the derived verified role tier, configured server ID, and verification time for this feature; it does not persist the Discord OAuth access token or the full server member response.

The Discord account is the passwordless Pup Account identity. PupEye-authorized device transfer carries the account's registered Player ID and Friend Code to the newly authorized device so trading, gifting, and ownership remain consistent.

## 9. Data Sharing

Puppy Clicker does **not** sell Pup Account progression or local gameplay data.

Pup Account progression, local cache contents, usernames, and local PupEye state are not intentionally shared with advertisers.

Third-party hosting providers may process normal network metadata when streamed resources are requested. Supabase processes the account/security records described above to provide Puppy Clicker's backend account and PupEye services.

## 10. Retention and Deletion

Local Puppy Clicker cache information remains on the device until it is removed by the user, cleared through Android app storage controls, or removed when the app is uninstalled, subject to Android storage behavior. Clearing local cache does not by itself delete Pup Account progression.

Server-side PupEye/account records may remain while needed to protect the player's account, prevent save or transaction replay, resolve device migrations, or handle Support review. A future account-deletion workflow should remove or anonymize server records where they are no longer required for security or legal purposes.

## 11. Children and Age-Sensitive Features

Puppy Clicker is designed as a casual game.

Online account, social, or cloud features that may be added later will require an updated privacy review before launch.

## 12. Security

Harley's Studios uses measures including:

- Authenticated encryption
- Android Keystore-backed keys for device-bound saves
- PupEye integrity checks

No security system can guarantee protection against every form of device compromise or data loss.

## 13. Changes to This Policy

This policy may be updated when Puppy Clicker features, data practices, or connected services change.

The current version displayed in the app is streamed from the official Puppy Clicker repository when available.

## 14. Contact

Questions about Puppy Clicker privacy may be directed to **Harley's Studios** through the official Puppy Clicker community or support channels made available in the app or official website.

---

**Puppy Clicker** · **Harley's Studios**
