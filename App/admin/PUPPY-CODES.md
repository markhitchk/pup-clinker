# Puppy Clicker — Local Puppy Codes

Owner reference for the offline Puppy Code system used by the Android game.

The APK does **not** store these plain-text values. It stores salted SHA-256 digests in `LocalRedeemCodes.kt` and marks each reward ID as redeemed in local save data after a successful claim.

## General treat codes

| Code | Reward |
| --- | --- |
| `BUDDY-HELLO-2026` | 750 treats |
| `PAW-PASS-2026` | 1,000 treats |
| `PUP-SHOP-BOOST` | 2,500 treats |
| `MIDNIGHT-MOON` | Unlock Midnight + 500 treats |
| `CLOUD-CUDDLES` | Unlock Cloud + 500 treats |
| `HTG-PUPPY-2026` | 10,000 treats |
| `TREAT-TIME-26` | 2,500 treats |
| `SHOP-TICKETS-3` | Legacy code converted to 4,000 treats |
| `GOOD-BOY-26` | 1,000 treats |
| `BOOP-THE-PUP` | 750 treats |
| `DOG-PARK-DAY` | 2,000 treats |
| `PUPPY-PARTY-26` | 2,500 treats |
| `BIG-TREAT-BAG` | 5,000 treats |
| `OG-PUPPY` | 7,500 treats |
| `THANK-YOU-PUPS` | 10,000 treats |
| `ONE-MORE-TREAT` | 250 treats |
| `WHO-ATE-THE-TREATS` | 1 treat |
| `VERY-GOOD-PUP` | 4,000 treats |

## V1 puppy unlocks

| Code | Puppy | Extra reward |
| --- | --- | ---: |
| `AURORA-PUP` | Aurora | 500 treats |
| `COCOA-CUDDLES` | Cocoa | 500 treats |
| `SNOWBALL-26` | Snowball | 750 treats |
| `GALAXY-PUP` | Galaxy | 1,000 treats |
| `NEON-BUDDY` | Neon Buddy | — |
| `GOLDEN-NIGHT` | Golden Night | — |
| `HALLOWEEN-PUP` | Pumpkin Pup | 500 treats |
| `SANTA-PAWS` | Santa Paws | 1,000 treats |
| `BIRTHDAY-BUDDY` | Birthday Buddy | 2,500 treats |
| `DEV-PUP-26` | Dev Pup | — |
| `SECRET-SNOOT` | Secret Snoot | — |
| `CLASSIC-FOREVER` | Classic Forever | — |

## V2 puppy unlocks

V2 puppies use separate `v2_` save IDs and do not reuse V1 display names.

| Code | Puppy | Extra reward |
| --- | --- | ---: |
| `FROSTY-PAWS-26` | Frost | 500 treats |
| `HONEY-BOOP-26` | Honey | 500 treats |
| `BISCUIT-CRUMBS` | Biscuit | 500 treats |
| `ONYX-NIGHT-26` | Onyx | 750 treats |
| `DOMINO-DOTS` | Domino | 750 treats |
| `CHESTNUT-CUDDLES` | Chestnut | 500 treats |
| `PRISM-PAWS` | Prism | 1,000 treats |
| `FLURRY-FRIEND` | Flurry | 750 treats |

## Rules

- Codes are case-insensitive and ignore whitespace.
- Each reward ID can be redeemed once per local app data set.
- Reset Game intentionally keeps redeem history so reset cannot be used to repeat codes.
- Puppy Codes can grant treats and puppy cosmetics/unlocks.
- Puppy Codes do **not** grant Upgrade Tickets, prestige points, or free tap power.
- Upgrade Tickets remain gameplay drops and are spent only on matching-rarity upgrades.
- This is an offline/local system, not a server-authoritative global redemption database.
