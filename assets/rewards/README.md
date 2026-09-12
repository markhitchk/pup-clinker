# Puppy Clicker monthly rewards

The Android app streams the current month's file from this folder every 15 minutes and caches the last valid copy for offline use.

## File naming

Use one file per month:

- `2026-09.json`
- `2026-10.json`
- `2026-11.json`

The filename and top-level `month` value must match `YYYY-MM`.

## Goal fields

Each day can contain up to 12 goals.

```json
{
  "id": "tap_time",
  "type": "taps",
  "emoji": "🐾",
  "title": "Tap Time",
  "description": "Tap your puppy 75 times",
  "target": 75,
  "rewardTreats": 300,
  "enabled": true
}
```

Supported `type` values are:

- `taps` — accepted taps completed today
- `care` — care actions completed today
- `shop` — upgrades bought today
- `wellness` — current wellness percentage
- `bond` — current bond percentage
- `tickets` — current total tickets in inventory

Only these data-only goal types are accepted. The JSON cannot download or execute Android code.

## Editing behavior

Changes committed to `main` are picked up by installed apps on the next refresh. If GitHub is unavailable or a file is invalid, Puppy Clicker keeps the last valid cached schedule. If no cache exists, built-in fallback goals are used.

Claimed goal IDs are tracked for the current local day. Keep each goal `id` unique within a day.
