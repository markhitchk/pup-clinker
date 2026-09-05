# Puppy Clicker — Local Puppy Codes

Owner reference for the offline Puppy Code system used by the Android game.

The APK does **not** store these plain-text values. It stores salted SHA-256 digests in `LocalRedeemCodes.kt` and marks each reward ID as redeemed in local save data after a successful claim.

| Code | Reward |
| --- | --- |
| `BUDDY-HELLO-2026` | 750 treats |
| `PAW-PASS-2026` | 2 Upgrade Tickets |
| `PUP-SHOP-BOOST` | 1,500 treats + 1 Upgrade Ticket |
| `MIDNIGHT-MOON` | Unlock Midnight + 500 treats + 1 Upgrade Ticket |
| `CLOUD-CUDDLES` | Unlock Cloud + 500 treats + 1 Upgrade Ticket |
| `HTG-PUPPY-2026` | 5,000 treats + 3 Upgrade Tickets |
| `TREAT-TIME-26` | 2,500 treats |
| `SHOP-TICKETS-3` | 3 Upgrade Tickets |

## Rules

- Codes are case-insensitive and ignore whitespace.
- Each reward ID can be redeemed once per local app data set.
- Reset Game intentionally keeps redeem history so reset cannot be used to repeat codes.
- Codes can grant treats, tickets, and puppy cosmetics/unlocks.
- Codes **cannot** change tap power. Tap power is only changed by CLICK upgrades purchased in the Shop.
- This is an offline/local system, not a server-authoritative global redemption database.
