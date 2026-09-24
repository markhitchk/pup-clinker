# PupEye Global Ban System Design

Date: 2026-09-24
Status: Approved design
Repository: `markhitchk/pup-clinker`

## Goal

Add a server-authoritative **PupEye Global Enforcement** system for Puppy Clicker that can place players, linked Discord identities, installations, and recognized device reputations into review, temporary global-ban, or permanent global-ban states.

The enforcement authority is Puppy Clicker's Supabase backend. Android, web, desktop, and future Puppy Clicker clients consume the same enforcement contract and must not independently decide whether a global ban is valid.

Temporary and permanent global bans are both global across all matching Puppy Clicker identities and recognized devices. A confirmed device-wide ban applies to every Puppy Clicker account that the backend can associate with that device reputation.

The system must preserve player data. A ban controls access; it never deletes saves, puppies, currencies, achievements, or account history.

## Existing PupEye Baseline

The current backend already has useful enforcement primitives:

- `pupeye_players.status` supports `active`, `review`, and `blocked`.
- `pupeye_installations.integrity_state` supports `clean`, `review`, and `blocked`.
- Pupeye sessions can be revoked.
- Android maps `PLAYER_BLOCKED` and `INSTALLATION_REVOKED` to a local `BLOCKED` backend state.
- Pupeye signed request envelopes bind requests to the installation's Android Keystore signing key.
- Save generations and protected transaction nonces are already checked server-side.

These fields remain useful as compatibility/cache state, but they are not the source of truth for the new global-ban model.

## Enforcement States

PupEye exposes three user-visible enforcement classes.

### Review Required

A review is not a permanent ban, but it is intentionally close to a full lockout.

While a player or installation is in review:

- gameplay and progression are disabled;
- Casino and Gacha are disabled;
- Exchange/social transactions are disabled;
- Discord reward verification is disabled;
- save import/export is disabled;
- protected sync/economy operations are disabled;
- account switching intended to evade review is not allowed;
- the app may show only the PupEye review screen, Support/appeal information, Terms, Privacy, and limited diagnostics.

Review can be cleared by authorized Support/admin action.

### Temporary Global Ban

A temporary global ban is a full Puppy Clicker lockout for every matching target.

It has a server-side `expires_at`. The client must not clear the ban based on local clock time. Access is restored only after PupEye confirms that no active ban remains.

Temporary bans propagate across matching players, Discord identities, installations, and recognized device reputations.

### Permanent Global Ban

A permanent global ban has no expiry and applies across every matching Puppy Clicker client and linked identity until an authorized admin revokes or changes it.

Permanent bans use the same full-lockout client UX as temporary bans, but display `Permanent` / `Expires: Never`.

## Ban Targets

A global ban may target one or more of the following:

- `PLAYER` — the canonical Puppy Clicker player/account identity.
- `DISCORD` — the linked Discord user ID.
- `DEVICE_REPUTATION` — a server-known native device reputation.
- `INSTALLATION` — one PupEye installation identity.
- `WEB_INSTALLATION` — one browser installation identity.

A single ban can have multiple targets. This allows one Ban ID to represent the enforcement action while attaching additional identities discovered during ban evasion.

Usernames, device model strings, IP addresses, and network names are **not** sufficient ban targets.

## Cross-Platform Identity and Device Reputation

### Android

Android uses:

- Pupeye installation UUID;
- Android Keystore public-key fingerprint;
- Player ID and Friend Code;
- linked Discord ID when present;
- privacy-compliant platform integrity/device-reputation signals when configured and available.

The app must not collect IMEI, hardware serial number, SIM serial, phone number, Wi-Fi MAC address, Bluetooth MAC address, or another restricted hardware identifier for banning.

### Desktop

Windows, macOS, and Linux clients use:

- a PupEye installation UUID;
- a client-generated signing key stored in the strongest available OS secure storage;
- Player ID;
- linked Discord ID;
- server-maintained device/install history.

A desktop implementation must not invent a fingerprint from invasive hardware attributes.

### Web

Web uses:

- Puppy Player/account identity;
- linked Discord identity;
- a browser installation key when supported;
- server-side identity history.

Browser storage is resettable and therefore a web installation is weaker evidence than a native cryptographic installation. Web must not pretend to provide an immutable hardware ID.

### Cross-Reinstall Limitation

A device-wide ban applies across every installation that PupEye can reliably associate with the same server-side device reputation.

The design does **not** claim that an ordinary third-party app can always recognize the same physical device after every uninstall, app-data wipe, OS reset, browser reset, or hardware change. Cross-reinstall recognition is best-effort and may use only privacy-compliant, platform-supported signals. If the backend cannot prove that a new installation is the same physical device, it must not fabricate that link.

Player and Discord targets remain globally enforceable even when a new installation cannot be linked to prior device reputation.

## Data Model

The global-ban subsystem adds the following Supabase tables. Every table in the exposed `public` schema has RLS enabled and direct `anon`/`authenticated` access revoked, matching the existing PupEye model.

### `pupeye_global_bans`

Canonical enforcement record.

Required fields:

- `id uuid primary key`
- `public_ban_id text unique not null` — human-shareable ID such as `PGB-7F2A-91C4`
- `kind text not null` — `temporary` or `permanent`
- `status text not null` — `active`, `expired`, or `revoked`
- `reason_code text not null`
- `public_reason text not null`
- `internal_reason text`
- `issued_at timestamptz not null`
- `expires_at timestamptz` — required for temporary bans, null for permanent bans
- `issued_by text not null`
- `support_note text`
- `revoked_at timestamptz`
- `revoked_by text`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Database constraints enforce that temporary bans have an expiry and permanent bans do not.

### `pupeye_ban_targets`

Associates a ban with one or more identities.

Required fields:

- `id uuid primary key`
- `ban_uuid uuid references pupeye_global_bans(id)`
- `target_type text not null`
- nullable target columns for `player_uuid`, `discord_user_id`, `device_reputation_uuid`, `installation_uuid`, or `web_installation_id`
- `attached_at timestamptz not null`
- `attached_by text not null`
- `source_event_id bigint`

A check constraint requires exactly one target field appropriate to `target_type`.

The same target cannot be attached to the same ban twice.

### `pupeye_ban_events`

Append-only moderation/audit history.

Fields include:

- `id bigint generated always as identity primary key`
- `ban_uuid uuid`
- `event_code text not null`
- `actor text not null`
- `detail jsonb not null`
- `created_at timestamptz not null`
- `discord_delivery_status text` — `pending`, `sent`, `failed`, or `not_required`
- `discord_attempt_count integer not null default 0`
- `discord_last_attempt_at timestamptz`
- `discord_last_error text`

Old events are never rewritten to disguise prior moderation state. Corrective actions append new events.

### `pupeye_device_reputation`

Represents a server-known native device reputation without storing prohibited hardware identifiers.

Fields include:

- `id uuid primary key`
- `platform text not null`
- `reputation_state text not null` — `clean`, `review`, or `blocked`
- `first_seen_at timestamptz not null`
- `last_seen_at timestamptz not null`
- `created_from_installation_uuid uuid`
- `metadata jsonb not null default '{}'`

Metadata must not contain raw IMEI, serial, MAC, SIM identifiers, phone numbers, session secrets, or other prohibited identifiers.

### `pupeye_identity_links`

Records evidence-backed relationships between identities.

Examples:

- installation -> device reputation;
- player -> Discord identity;
- player -> installation;
- web installation -> player.

Fields include:

- `id bigint generated always as identity primary key`
- `link_type text not null`
- source/target identifiers
- `confidence text not null` — `authoritative`, `strong`, or `weak`
- `evidence_code text not null`
- `created_at timestamptz not null`
- `last_seen_at timestamptz not null`

Only authoritative/strong links are eligible for automatic ban propagation. Weak signals may create review evidence but must not independently cause a device-wide global ban.

### `pupeye_ban_hits`

Records attempts that encounter an active global ban.

Fields include:

- `id bigint generated always as identity primary key`
- `ban_uuid uuid`
- `player_uuid uuid`
- `installation_uuid uuid`
- `device_reputation_uuid uuid`
- `discord_user_id text`
- `client_platform text`
- `request_action text`
- `created_at timestamptz not null`

This table is operational/audit data. It must not contain request secrets or full save payloads.

### Save Attestation History

To support high-confidence tamper evidence, add server-side authenticated save attestations rather than relying only on the current save head.

A `pupeye_save_attestations` table stores at least:

- `save_id uuid`
- `player_uuid uuid`
- `installation_uuid uuid`
- `generation bigint`
- `payload_hash_sha256 text`
- `device_key_id text`
- `observed_at timestamptz`

The hash is calculated over the canonical authenticated plaintext save payload, not merely over randomized encrypted ciphertext.

The existing `pupeye_save_heads` record may additionally cache the latest `save_id` and `payload_hash_sha256`.

This supports detection of cases such as the same authenticated save ID appearing with different payload hashes.

## Reason Codes

Initial reason codes:

- `SAVE_TAMPERING`
- `SAVE_ROLLBACK`
- `SIGNATURE_FORGERY`
- `TRANSACTION_REPLAY`
- `ECONOMY_MANIPULATION`
- `BAN_EVASION`
- `DEVICE_BAN_EVASION`
- `ACCOUNT_ABUSE`
- `DISCORD_IDENTITY_ABUSE`
- `UNAUTHORIZED_CLIENT`
- `SECURITY_BYPASS`
- `SUPPORT_ACTION`
- `OTHER`

Reason codes are stable machine-readable values. `public_reason` is safe to display to the user. Detailed anti-cheat evidence stays in internal/audit fields and is not returned to clients.

## Detection and Escalation Policy

Detection and punishment are separate stages.

A weak or ambiguous signal must not create a permanent global ban by itself.

Default escalation model:

1. Weak anomaly -> `REVIEW_REQUIRED`.
2. Repeated or high-confidence anomaly -> temporary global ban.
3. Confirmed severe abuse or repeated ban evasion -> permanent global ban.
4. Authorized Support/admin action can issue, modify, revoke, or escalate bans directly.

Examples:

- Save generation rollback -> review.
- Same authenticated `save_id` with conflicting canonical payload hashes -> review or temporary global ban depending on evidence confidence.
- Cryptographically invalid/forged device request presented as a known installation -> high-confidence security event and eligible for temporary global ban.
- Repeated confirmed signature forgery or confirmed ban evasion -> eligible for permanent global ban.
- New player account on a recognized device with an active temporary or permanent device ban -> denied under the same active ban.
- Client-reported local hard flags alone -> never sufficient for a permanent global ban.

Server-side validation is required before automatic escalation.

## Central Enforcement Resolver

All client-facing PupEye Edge Functions use one shared resolver, conceptually:

`resolveGlobalEnforcement(admin, identityContext)`

It resolves:

- player;
- Discord identity;
- installation;
- device reputation;
- web installation where applicable;
- all active ban targets;
- review state;
- expiry using server time.

The resolver returns one of:

- `ALLOWED`
- `REVIEW_REQUIRED`
- `GLOBAL_BANNED`

No individual Edge Function maintains its own separate interpretation of a global ban.

## Request Enforcement Flow

For an authenticated request:

1. Validate the public API key.
2. Verify the signed PupEye envelope.
3. Authenticate the PupEye session when the operation requires a session.
4. Resolve player/install/device/Discord identity.
5. Run the central global-enforcement resolver.
6. If review is active, stop with `REVIEW_REQUIRED`.
7. If an active global ban is found:
   - record a `pupeye_ban_hits` row;
   - revoke the current session when one exists;
   - return `GLOBAL_BANNED`;
   - do not execute the requested mutation.
8. Otherwise continue the requested operation.

The ban check occurs before protected state changes.

## Registration and Ban Evasion

`pupeye-register` performs enforcement resolution before issuing a new session.

If a known player, Discord identity, installation, or recognized device reputation is actively banned:

- registration does not issue a usable session;
- the client receives the canonical ban envelope;
- any newly proven identity relationship may be recorded;
- a strongly linked new identity may be attached to the existing ban;
- the system appends `BAN_EVASION_DETECTED`, `NEW_IDENTITY_LINKED`, and/or `BAN_TARGET_ATTACHED` events as appropriate.

A new username alone never establishes identity linkage.

An IP match alone never establishes identity linkage.

## Enforcement Status Endpoint

Add a client-facing `pupeye-enforcement-status` Edge Function.

Purpose:

- allow a banned/reviewed installation to refresh enforcement state without an active session;
- confirm temporary-ban expiry using server time;
- detect admin revocation/unban;
- return the current public ban/review information.

The endpoint requires a valid signed PupEye installation envelope and public API key. It does not grant gameplay access or create a session.

After a temporary ban expires or an admin revokes a ban, the client calls registration/authentication again to obtain a usable session.

## Standard Client Contract

A global ban response follows a shared schema:

```json
{
  "ok": false,
  "code": "GLOBAL_BANNED",
  "ban": {
    "id": "PGB-7F2A-91C4",
    "kind": "temporary",
    "reasonCode": "BAN_EVASION",
    "publicReason": "PupEye detected activity that violates Puppy Clicker security rules.",
    "issuedAt": "2026-09-24T20:52:00Z",
    "expiresAt": "2026-09-27T20:52:00Z",
    "scope": "GLOBAL",
    "deviceWide": true
  }
}
```

Permanent bans return `expiresAt: null`.

Review responses use `REVIEW_REQUIRED` and contain only information safe for the user.

Clients must not receive internal evidence, webhook credentials, database identifiers that are not needed by the UI, session-token hashes, full public-key material, IP metadata, or Discord email addresses.

## Android Client Behavior

Android adds a persistent PupEye enforcement model to `SupabasePupEyeClient`.

A known active ban is stored inside protected PupEye state with:

- Ban ID;
- kind;
- public reason;
- issued time;
- server-confirmed expiry when present;
- last server verification time.

Rules:

- known active global ban + network unavailable -> remain locked;
- local device clock passing `expires_at` -> remain locked until server confirms expiry;
- local data must not be able to convert `GLOBAL_BANNED` to `CONNECTED`;
- a verified server unban/expiry clears the cached ban and allows normal registration;
- clearing a local review flag does not override server review/ban state.

## Banned / Review UI

Puppy Clicker starts normally enough to initialize PupEye, then routes to an app-level enforcement screen instead of the game when required.

### Global Ban Screen

Display:

- `PupEye Global Ban`
- `Temporary Global Ban` or `Permanent Global Ban`
- public Ban ID
- safe public reason
- issued date
- expiry date or `Never`
- scope: `Puppy Clicker Global`
- Support Installation Code when available

Allowed actions:

- Contact Support
- Copy Ban ID
- View appeal information
- Privacy
- Terms
- limited diagnostics safe for Support

Not available:

- tapping/progression
- Casino
- Gacha
- Exchange
- rewards/redeem mutations
- save import/export
- roster/economy mutation
- account switching for bypass
- protected backend mutations

### Review Screen

The review screen uses the same restricted shell but says `PupEye Review Required` rather than presenting the user as permanently banned.

## Temporary Ban Expiry

Temporary-ban expiry uses Supabase/server time only.

An active temporary ban is considered expired when `expires_at <= now()`. Expiration is reflected by the enforcement resolver and should append `BAN_EXPIRED` once, either lazily on first post-expiry resolution or by scheduled maintenance.

The client must receive server confirmation before removing its cached lockout.

If another active ban target still applies, access remains blocked.

## Unban and Appeal Behavior

Revoking a ban:

- sets the canonical ban status to `revoked`;
- records `revoked_at` and `revoked_by`;
- appends `BAN_REVOKED`;
- preserves all prior audit history;
- does not delete evidence;
- does not delete the player's save/inventory;
- allows clients to recover access after server verification.

Appeal events may include:

- `APPEAL_SUBMITTED`
- `APPEAL_APPROVED`
- `APPEAL_DENIED`

An appeal is a moderation workflow; it does not automatically change enforcement state until an authorized action is recorded.

## Support/Admin API

Add a server-secret-only `pupeye-support-ban` Edge Function or equivalent trusted backend action set.

Supported operations:

- search ban by Ban ID;
- search player by Player ID/Friend Code;
- search linked Discord ID;
- search Support Installation Code;
- view active/recent bans for a target;
- issue temporary ban;
- issue permanent ban;
- attach an additional target;
- extend/shorten a temporary ban;
- convert temporary -> permanent or permanent -> temporary; every conversion must append an audit event and preserve the original issue history;
- revoke a ban;
- clear review;
- revoke an individual installation;
- record appeal status;
- add private Support notes.

Every mutation appends an audit event with:

- actor;
- action;
- timestamp;
- prior state;
- resulting state;
- reason;
- support note when supplied.

No admin credential is shipped in Android, web, or desktop clients.

## Discord Webhook Notifications

PupEye sends Discord **embed messages** for moderation events.

The webhook credential is server-side only and configured as a Supabase secret such as:

`PUPPY_GLOBAL_BAN_DISCORD_WEBHOOK`

The raw webhook URL must not be committed to GitHub, embedded in Android/web/desktop clients, stored in a public database column, or returned to clients.

Because a webhook credential has already been shared during development, production deployment should use a rotated webhook token.

### Events Requiring Discord Embeds

- `GLOBAL_BAN_CREATED`
- `GLOBAL_BAN_EXTENDED`
- `GLOBAL_BAN_EXPIRED`
- `GLOBAL_BAN_REVOKED`
- `GLOBAL_BAN_ESCALATED`
- `BAN_EVASION_DETECTED`
- `NEW_ACCOUNT_BLOCKED_ON_BANNED_DEVICE`
- `APPEAL_SUBMITTED`
- `APPEAL_APPROVED`
- `APPEAL_DENIED`
- `ADMIN_OVERRIDE`

### Embed Presentation

Use visually distinct embed types:

- 🔴 Permanent Global Ban
- 🟠 Temporary Global Ban
- 🟡 Review Required
- 🚫 Ban Evasion Detected
- 🔄 Ban Extended
- ✅ Ban Revoked / Appeal Approved
- ⌛ Temporary Ban Expired
- ⚠️ New Account Blocked on Banned Device

Embed fields may include:

- Ban ID
- status/kind
- reason code
- player username
- Player ID
- scope
- device-wide yes/no
- Discord-linked yes/no
- source platform
- issued/expiry timestamp

Discord notifications must **not** include:

- webhook secrets;
- Pupeye session tokens or token hashes;
- IP addresses;
- email addresses;
- full anti-cheat evidence;
- save contents;
- raw cryptographic keys;
- private device-reputation metadata.

### Delivery Semantics

The database enforcement mutation commits first.

Discord delivery is secondary and must never determine whether a ban succeeds.

Each relevant `pupeye_ban_events` row records delivery state. Failed delivery may be retried, but retrying the webhook must not duplicate the moderation mutation.

## Relationship to Existing PupEye Fields

`pupeye_players.status` remains a compatibility/cache field:

- `active` when no player-level review/global enforcement applies;
- `review` when review is active;
- `blocked` when a global ban is active.

It is not the canonical historical ban record.

`pupeye_installations.integrity_state` continues to represent anti-cheat/integrity condition independently of global bans.

Therefore this is valid:

```text
integrity_state = clean
global ban = active
```

and this is also valid:

```text
integrity_state = review
global ban = none
```

## Security Rules

- Supabase is authoritative for global enforcement.
- RLS is enabled on every new public table.
- Direct public table access is revoked.
- Server/service-role credentials remain server-side.
- Client requests use the existing publishable key plus PupEye signatures/session authorization as appropriate.
- Admin/support actions require a server-only credential.
- Ban IDs are public identifiers and contain no secret material.
- Client-provided usernames, device models, reason strings, or local hard flags are untrusted data.
- A permanent ban must not be created solely from one weak client-reported signal.
- No banned client can clear its own ban with a local preference edit or clock change.
- IP/network data is not an automatic ban target.
- Restricted Android hardware identifiers are not collected to implement device bans.

## Privacy and Terms

Before production rollout, update Puppy Clicker's Privacy Policy and Terms to disclose:

- server-side global enforcement;
- device/install reputation records;
- linked Player/Discord/install identities;
- ban/review audit events;
- save-content hash/attestation metadata;
- retention of enforcement history;
- Discord moderation notifications;
- Support/appeal process.

The policy should distinguish between data used as authoritative identity evidence and weak diagnostic metadata.

## Migration and Rollout

Rollout must be non-destructive.

1. Add schema/tables/indexes/RLS.
2. Deploy central enforcement resolver.
3. Deploy `pupeye-enforcement-status`.
4. Deploy trusted Support/admin ban actions.
5. Add Discord embed sender using the server-side secret.
6. Update existing client-facing Pupeye functions to call the resolver.
7. Update Android state mapping and app-level enforcement UI.
8. Add save attestation/hash support.
9. Update legal documents.
10. Verify current existing player remains `active` with no accidental ban.
11. Only then enable automatic escalation rules.

Existing players receive no ban row by default.

Existing `review`/`blocked` compatibility states should not be silently converted into permanent global bans. They require explicit migration logic or Support review.

## Testing Strategy

Implementation follows test-driven development.

### Database Tests

Verify:

- temporary ban requires expiry;
- permanent ban rejects expiry;
- one ban can have multiple targets;
- duplicate target attachment is rejected;
- RLS/direct client table access remains denied;
- expired/revoked bans are not active;
- append-only audit history is preserved;
- save attestation detects conflicting hashes for the same authenticated save ID.

### Edge Function Tests

Verify:

- active player ban returns `GLOBAL_BANNED`;
- Discord-target ban applies to a linked player;
- device-reputation ban blocks a different account on that recognized device;
- installation-only ban does not invent unrelated device/player links;
- weak identity evidence does not automatically propagate a ban;
- temporary ban remains active before server expiry;
- temporary ban becomes inactive after server expiry;
- revoked ban no longer blocks;
- active ban revokes session before protected mutation;
- registration does not create a usable session for an active banned target;
- enforcement-status works without a session but requires a valid installation signature;
- failed Discord webhook delivery does not undo enforcement;
- webhook retries do not duplicate ban mutations.

### Android Tests

Verify:

- `GLOBAL_BANNED` maps to the persistent ban state;
- review maps to restricted review state;
- app-level routing prevents game access;
- temporary/permanent ban screens render correct information;
- local clock changes cannot clear a temporary ban;
- offline mode cannot bypass a known active ban;
- server-confirmed expiry/unban restores registration flow;
- ban does not erase save/inventory;
- existing clean players continue normal startup.

### Regression Tests

Existing PupEye, save transfer, Casino, Gacha, Discord auth, roster, Exchange, onboarding, navigation, and signing/build tests must continue passing.

## Acceptance Criteria

The feature is complete when all of the following are true:

1. Supabase stores canonical temporary/permanent global bans with public Ban IDs and immutable audit history.
2. A ban can target player, Discord identity, device reputation, installation, or web installation.
3. Temporary and permanent device-wide bans block every account strongly linked to the recognized banned device.
4. Android cannot bypass a known active ban by going offline or changing local time.
5. Every protected PupEye endpoint uses the shared enforcement resolver.
6. A banned registration attempt receives a ban envelope and no usable session.
7. Temporary bans automatically become non-active by server time; clients require server confirmation to unlock.
8. Authorized Support can issue, modify, revoke, and inspect bans without shipping admin credentials to clients.
9. Rich Discord webhook embeds are emitted for the approved moderation events without exposing sensitive evidence.
10. Webhook failure never rolls back or blocks the actual enforcement action.
11. Ban/review screens expose Ban ID, reason, timing, Support/appeal, Terms, Privacy, and diagnostics while blocking gameplay/progression.
12. A ban never deletes player data.
13. Existing clean users remain unaffected after migration.
14. Privacy/Terms are updated before production enforcement is enabled.

## Non-Goals

This design does not:

- collect IMEI/serial/MAC/SIM identifiers;
- guarantee impossible-to-evade physical hardware identity after every OS/app reset;
- use IP address as a ban identity;
- delete saves or inventory when enforcing a ban;
- expose detailed anti-cheat evidence to banned clients;
- place admin credentials in public clients;
- rely on Discord webhook delivery for enforcement correctness;
- automatically convert every anomaly into a permanent ban.
