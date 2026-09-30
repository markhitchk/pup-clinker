# Puppy Clicker — Privacy Policy

> **Effective date:** September 24, 2026

This Privacy Policy explains how **Puppy Clicker**, provided by **Harley's Studios**, handles information in the Android app.

---

## 1. Game Data and Local Cache

Puppy Clicker stores authoritative game progression in the Puppy Clicker **Supabase database**. The Android app also keeps a temporary local working cache and local-only preferences so gameplay and the interface remain responsive.

Cloud progression may include upgrades, puppy ownership and care statistics, balances, inventory, achievements, reward state, and related gameplay information. Local-only data may include interface settings, cached assets, transient state, and PupEye device integrity state.

## 2. Player Username

A player may choose a local Puppy Clicker username.

The Puppy Clicker display name may be derived from the verified Discord-backed Pup Account, normalized where required, cached locally, and stored with the account/cloud progression where needed.

## 3. Device and Installation Security Metadata

PupEye installation metadata may contain a coarse manufacturer/model label for the Android device authorized to access the account.

PupEye also creates security metadata that is specific to the Puppy Clicker installation, including:

- A random Puppy Clicker installation ID
- A public signing-key fingerprint
- A monotonic save generation number
- Random save and economy transaction identifiers
- Integrity-event and protected economy-ledger information

The corresponding private signing key is generated in Android Keystore and is not exported. The installation ID and signing-key fingerprint are app security identifiers, not hardware serial numbers.

Puppy Clicker does **not** use the following for save ownership:

- IMEI
- Hardware serial number
- Android ID
- Phone number
- Advertising ID
- Other persistent hardware identifiers

PupEye global enforcement also does not use SIM serial, Wi-Fi MAC, or Bluetooth MAC addresses as ban identifiers. A coarse device model, username, or IP address alone does not establish that two players are the same person or device.

## 4. Cloud Save Integrity

Supabase is the authoritative store for Puppy Clicker progression. Cloud saves use account/device authorization, monotonic generations, revision checks, and content hashes. The app may keep a local working cache protected by PupEye integrity checks, but Puppy Clicker does not provide a portable backup file or Android/data save mirror.

Authentication failures, rollback attempts, ownership mismatches, duplicate protected transactions, cloud revision conflicts, or unauthorized local-cache modifications may be recorded by **PupEye** and may cause a save or protected gameplay operation to be rejected, restored from the authoritative cloud state, or blocked pending Support review.

## 5. PupEye Fair-Play Information

PupEye may process local security information such as:

- Recent tap timing
- Fair-play strike counts
- Save-integrity results
- Related local security state

These checks are designed to detect likely automated clicking or unauthorized save modification. PupEye now also uses Puppy Clicker's Supabase backend to register the app installation, compare save generations, reject replayed protected transactions, and support authorized device migration. Local timing samples used for click-pattern detection are not uploaded as part of the current Supabase integration.

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
- Discord identity and derived Puppy Clicker server role after Discord authorization

The corresponding Android Keystore private signing key is not uploaded. Discord OAuth access tokens are used transiently for server verification and are not intentionally stored by Puppy Clicker or in the PupEye database.

For global enforcement, PupEye may maintain a public Ban ID, Player/Discord/installation links, device reputation records, enforcement audit events, cloud-save revision/generation metadata, and save-content hashes. These records help detect replay or tampering, require Support review, and enforce temporary or permanent bans. Authoritative game progression itself is stored in the Puppy Clicker Supabase database; moderation notifications exclude save contents.

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

The Discord account does not replace Puppy Clicker's device-bound **Player ID** or **Friend Code**. Those local identifiers continue to be used for Puppy Clicker trading, gifting, save ownership, and related local game features.

## 9. Data Sharing

Puppy Clicker does **not** sell local game-save data.

Local usernames, encrypted save contents, and local PupEye state are not intentionally shared with advertisers.

Third-party hosting providers may process normal network metadata when streamed resources are requested. Supabase processes the account/security records described above to provide Puppy Clicker's backend account and PupEye services.

## 10. Retention and Deletion

Local cache and preferences remain on the device until removed by the user, cleared through Android app storage controls, replaced by a cloud restore/sync, or removed when the app is uninstalled, subject to Android behavior.

Authoritative Pup Account progression and PupEye/account records are stored server-side and may remain while needed to provide the account, preserve progression, prevent save or transaction replay, resolve device recovery, or handle Support review. A future account-deletion workflow should remove or anonymize server records where they are no longer required for security or legal purposes.

## 11. Children and Age-Sensitive Features

Puppy Clicker is designed as a casual game.

Online account, social, or cloud features that may be added later will require an updated privacy review before launch.

## 12. Security

Harley's Studios uses measures including:

- Supabase-backed authoritative progression
- Revision, generation, and content-hash conflict protection
- Android Keystore-backed device signing keys
- PupEye integrity checks

No security system can guarantee protection against every form of device compromise or data loss.

## 13. Changes to This Policy

This policy may be updated when Puppy Clicker features, data practices, or connected services change.

The current version displayed in the app is streamed from the official Puppy Clicker repository when available.

## 14. Contact

Questions about Puppy Clicker privacy may be directed to **Harley's Studios** through the official Puppy Clicker community or support channels made available in the app or official website.

---

**Puppy Clicker** · **Harley's Studios**
