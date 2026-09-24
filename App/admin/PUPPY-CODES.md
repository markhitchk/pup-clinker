# Puppy Clicker — Puppy Codes

Puppy Codes are validated against the live schema-2 catalogue at `assets/redeem-codes.json`.

## Current behavior

- New claims require a live GitHub validation.
- Codes are exact-match: case, spaces, and hyphens are significant.
- The public catalogue stores salted SHA-256 hashes, not plaintext codes.
- Each redemption ID can be claimed once per local save history.
- Puppy-code unlocks use a full reward preview / special reveal.
- Seasonal/event-only puppies remain outside the normal Puppy Code catalogue unless deliberately added.

## Current V1 puppy-code rewards

The live catalogue currently contains code rewards for:

- Midnight
- Cloud
- Aurora
- Cocoa
- Snowball
- Galaxy
- Neon Buddy
- Golden Night
- V1 Dev Pup
- Secret Snoot
- Classic Forever

## Current V2 puppy-code rewards

The live catalogue currently contains code rewards for:

- Frost
- Honey
- Biscuit
- Onyx
- Domino
- Chestnut
- Prism
- Flurry

## V2 special unlocks that are not Puppy Codes

These puppies intentionally use separate unlock paths and must not be added to `assets/redeem-codes.json` without an explicit design change:

- `v2_harleytg` — HarleyTG special reward
- `v2_dev_pup` — Discord Developer role
- `v2_discord_pup` — Discord Pup Members role

The Discord Pup remains tied to Discord authentication and server-role verification; entering a Puppy Code must not bypass that requirement.

## Maintenance

When adding a new Puppy Code:

1. Choose a permanent redemption `id`.
2. Hash the exact canonical plaintext using `PUPPY_CLICKER_LOCAL_2026_V1|` + the code and SHA-256.
3. Add the hash-only definition to `assets/redeem-codes.json`.
4. For puppy rewards, use `rarity: "special"` and the `SPECIAL_REVEAL` flag.
5. Bump the catalogue `revision`.
6. Keep role-auth, event-only, developer-only, and other special-path puppies out of the catalogue unless their unlock policy is intentionally changed.
7. Run the Android/JVM regression suite before release.


## Date-locked automatic puppy unlocks

These puppies are not Puppy Code rewards:

- `halloween` / Pumpkin Pup — automatically unlocks on **October 31** in the player's local time zone.
- `santa` / Santa Paws — automatically unlocks on **December 25** in the player's local time zone.
- `birthday` / Birthday Buddy — automatically unlocks on the locally saved birthday month/day.

The app records an in-app notification when the unlock is applied. When Game Event notifications are enabled, the background notification sweep also alerts the player on the eligible date. Once unlocked, the puppy remains permanently owned.
