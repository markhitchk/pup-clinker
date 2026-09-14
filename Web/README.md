# Puppy Clicker Web

Browser/PWA port of Puppy Clicker. This branch is intentionally isolated from Android production work on `main`.

## Run locally

Serve the repository's generated Pages layout so `Web/` and `assets/` share one web root. The GitHub Pages workflow does this automatically.

## Current parity

- Click/treat loop with V6-compatible core fields
- Automatic treats and up-to-4-hour offline accrual
- V1/V2 roster manifests and real asset IDs
- Cookie + Upgrade Ticket shop using the Android V5/V6 upgrade definitions and growth rules
- Basic care state
- Simulated Pup Scratchers, Slots, and Roulette using Treats only
- Responsive phone/desktop layout
- PWA manifest + service worker
- Versioned `puppy-clicker-cloud-save` JSON import/export
- Cross-save client with revision/conflict protection hooks

Android-only settings such as notification permissions, haptics, Keystore keys, and local PupEye tamper history are intentionally not synchronized.

## Cross-save API contract

GitHub Pages is static, so account cross-save requires a separate HTTPS endpoint.

### GET

`GET <endpoint>`

Return either a cloud-save object or:

```json
{"save":{"format":"puppy-clicker-cloud-save","schemaVersion":1,"revision":4,"playerId":"...","updatedAt":"...","game":{}}}
```

Return HTTP 404 when the player has no cloud save.

### PUT

The web client sends:

```json
{
  "baseRevision": 4,
  "save": {
    "format": "puppy-clicker-cloud-save",
    "schemaVersion": 1,
    "revision": 4,
    "playerId": "...",
    "updatedAt": "...",
    "game": {}
  }
}
```

The server must atomically accept only when `baseRevision` matches the current server revision, increment the revision, and return the accepted save. Return HTTP 409 on a revision conflict.

Authentication should be handled by the API using secure cookies/session credentials. Do not put backend secrets in this branch.

## GitHub Pages

`.github/workflows/deploy-web.yml` copies `Web/` to the Pages root and then adds the repository `assets/` tree. All runtime paths are relative so project Pages hosting under `/pup-clinker/` works correctly.
