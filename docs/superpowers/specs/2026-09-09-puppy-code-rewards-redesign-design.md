# Puppy Clicker — Rewards Hub and Puppy Code System Redesign

Date: 2026-09-09
Status: Approved design
Target: Android app, current V6 flow
Repository: `markhitchk/pup-clinker`
Branch: `main`

## 1. Goal

Replace the current basic redeem-code dialog with a first-class Rewards subsystem while preserving Puppy Clicker's GitHub-only infrastructure. There is no VPS, database, custom API, or other backend. GitHub remains the authoritative remote source for Puppy Code catalogue data.

The redesign must improve the mobile UI, make code behavior extensible, preserve one-time redemption semantics, support configurable reward bundles, add code flags and promotion metadata, provide redemption history, and keep reward application atomic and safe.

## 2. Navigation

The primary bottom navigation becomes:

`🐾 Play · 💖 Care · 🛍️ Shop · 🎁 Rewards · ⚙️ Settings`

The existing Prestige bottom-navigation destination is removed. Prestige moves inside Shop as an advanced progression section alongside upgrades and ticket-related progression.

## 3. Rewards Hub

The Rewards tab is a vertically scrollable, mobile-first hub built from large section cards with comfortable touch targets.

Sections:

1. **Daily Rewards** — existing daily gift, streak, daily goals, and related daily claim UI.
2. **Puppy Codes** — dedicated full-screen code redemption system.
3. **Events** — seasonal and event rewards. Existing event-only puppies remain event rewards rather than being granted through Puppy Codes unless deliberately changed in a later approved design.
4. **Special Rewards** — reserved for future Patreon, bug-bounty, community, developer, or other special reward sources.

The hub must not duplicate reward state. It should route to or reuse existing daily/event logic where practical.

## 4. Puppy Codes screen

The Puppy Codes screen contains:

- Title and short explanatory subtitle.
- Large code-entry field.
- One-tap Paste action.
- Primary Redeem button.
- Inline validation/status area.
- Optional Active Promotions section.
- Redeemed History section.

### 4.1 Exact code entry

Schema 2 codes are exact-match codes. The entered string is not uppercased, whitespace-normalized, or hyphen-normalized before hashing.

Examples:

- `BUDDY-HELLO-2026` is distinct from `buddy-hello-2026`.
- `BUDDY-HELLO-2026` is distinct from `BUDDYHELLO2026`.
- `BUDDY-HELLO-2026` is distinct from `BUDDY-HELLO-2026 `.

Paste inserts clipboard text exactly as copied. The UI must not silently rewrite the pasted value. The implementation may reject leading/trailing clipboard newlines as invalid rather than silently trimming them.

## 5. GitHub-only validation model

Every new Puppy Code claim requires a live network validation against the GitHub catalogue. Offline claims are not permitted.

The current raw GitHub catalogue remains the source of truth, at:

`assets/redeem-codes.json`

The app performs a live refresh/check immediately before authorizing a code claim. A successful live HTTP 200 response authorizes use of that downloaded validated catalogue. A live HTTP 304 response may authorize use of the already validated cached catalogue because GitHub has confirmed it is unchanged. Any other network/HTTP/parse/validation failure must fail closed for new claims.

A local cache may still be used for display resilience, promotion metadata, and history-related presentation, but a stale cache by itself must never authorize a new redemption.

User-facing network failure message:

`Puppy Codes are temporarily unavailable. Connect to the internet and try again.`

## 6. Catalogue schema 2

The existing schema 1 catalogue is migrated to schema 2. The schema is data-only; downloaded catalogue content must never execute code or provide executable logic.

Top-level fields:

- `schema`: integer, value `2`.
- `revision`: human-readable catalogue revision string.
- `rewards`: array of code definitions.

Each code definition supports:

- `id`: permanent unique redemption ID.
- `hash`: salted SHA-256 digest of the exact canonical plaintext code.
- `status`: `active`, `expired`, `disabled`, or `revoked`.
- `rarity`: optional `standard`, `rare`, or `special`.
- `flags`: zero or more internal flags.
- `minVersionCode`: optional minimum Android app versionCode.
- `maxVersionCode`: optional maximum Android app versionCode.
- `requiredRewardSchema`: optional minimum reward-schema version understood by the app.
- `startsAt`: optional ISO-8601 UTC start timestamp.
- `expiresAt`: optional ISO-8601 UTC expiration timestamp.
- `campaign`: optional Active Promotions metadata.
- `rewards`: ordered array of reward objects.
- `message`: user-facing success/reveal message.

### 6.1 Code rarity is not puppy rarity

Code rarity describes the promotion/code, not the puppy. Puppy unlock rewards may have their own rating or may have no rating at all. If a puppy has no rating, the reward UI omits a puppy-rating field rather than inventing one.

## 7. Status behavior

Status is separate from flags.

- `active`: eligible for normal validation.
- `expired`: show `This Puppy Code has expired.`
- `disabled`: show `This Puppy Code is currently unavailable.`
- `revoked`: behave as a generic invalid code and do not expose revocation details.
- Unknown/missing code: show `Invalid Puppy Code.`

A code outside `startsAt` / `expiresAt` is treated as unavailable according to its time window even if its static status remains `active`.

## 8. Authoritative time for limited-time codes

Because device time can be changed, limited-time validation must use a timestamp obtained from the successful live GitHub HTTP response when available, such as the HTTP `Date` header.

If a code uses `startsAt` or `expiresAt` and the live response does not provide a usable authoritative timestamp, redemption fails closed for that limited-time code instead of trusting the device clock.

Permanent codes do not depend on authoritative server time beyond the required live catalogue check.

## 9. Flags

A code may contain multiple flags simultaneously. Flags are additive and primarily internal.

The initial implementation supports a registry of known flags. Candidate supported flags include:

- `LIMITED_TIME`
- `HIDDEN_PROMO`
- `SPECIAL_REVEAL`
- `REQUIRES_ONLINE`
- `REQUIRES_NEWER_VERSION`
- `DEV_ONLY`
- `STABLE_ONLY`
- `EVENT_CODE`
- `PATREON_CODE`
- `BUG_BOUNTY`
- `STAFF_CODE`
- `NO_REWARD_PREVIEW`
- `NO_HISTORY_DETAILS`

However, this design explicitly requires full reward preview before claiming major/special bundles, so `NO_REWARD_PREVIEW` must not be enabled in the initial schema-2 catalogue. It is reserved for future design work and should be rejected if encountered until explicitly supported.

`REQUIRES_ONLINE` is redundant under the current all-codes-online policy but may remain a known reserved flag for future compatibility.

Contradictory combinations, such as `DEV_ONLY` plus `STABLE_ONLY`, are catalogue validation errors unless a future approved design defines their meaning.

Unknown flags are not ignored. The code fails before any reward is granted and shows a version/incompatibility message such as:

`This Puppy Code requires a different or newer version of Puppy Clicker. Update the app and try again.`

Raw internal flag names are not shown to players. The UI may show clean consequences such as `Limited Time`, `Special Reward`, or `Update Required`.

## 10. App/version compatibility

Version compatibility is validated before preview or claim.

Failure cases include:

- App version below `minVersionCode`.
- App version above `maxVersionCode`.
- Reward schema newer than the app understands.
- Unknown reward type.
- Unknown flag.

Suggested messages:

- `This Puppy Code requires a newer version of Puppy Clicker. Please update the app and try again.`
- `This Puppy Code is not compatible with your version of Puppy Clicker.`
- `This Puppy Code contains rewards that this version of Puppy Clicker cannot process.`

No reward and no redemption marker may be written in these cases.

Release-channel restrictions such as Stable-only or Dev-only remain supported by the flag model but are not required to be used until a later product decision is made.

## 11. Reward model

Puppy Codes support configurable reward bundles composed from reward types the installed app explicitly knows how to process.

Initial registered reward types should include:

- `treats`
- `puppy`
- `upgrade_ticket`
- `cosmetic`
- `badge`
- `boost`
- future registered game reward types as the app evolves

The catalogue configures data only. Adding a completely new reward behavior still requires a new app version that registers and safely implements that reward type.

### 11.1 Upgrade Tickets

Upgrade Tickets may be granted by selected codes. They are no longer globally forbidden from Puppy Codes.

Ticket rewards must be explicitly represented in the reward bundle and validated against hard app-side limits. Catalogue data must not be able to overflow inventory or grant unreasonable amounts because of an accidental JSON edit.

The initial hard limit is **100 Upgrade Tickets per rarity per code claim**, with inventory still capped by the app's existing inventory limits. A future app version may revise this bound deliberately.

### 11.2 No progression eligibility gates

A valid code is not blocked by player level, prestige count, owned puppy, completed event, or setup progression. Code eligibility depends only on code validity, status/time, flags, app compatibility, live GitHub validation, cooldown state, and prior redemption.

### 11.3 No global redemption count

There is no fake or simulated global claim limit. Without a shared backend/database, Puppy Clicker cannot reliably enforce a worldwide maximum number of claims.

Each eligible save may claim a code once, subject to the local redemption record.

## 12. Claim presentation

Puppy Clicker uses two claim presentations.

### 12.1 Immediate compact claim

A simple reward may redeem immediately after validation and show a compact confirmation.

Initial rule: a bundle containing only a standard Treats reward, with no special-presentation flag and no additional reward objects, qualifies for immediate compact claim.

Example:

`✅ Code redeemed · +2,500 Treats`

### 12.2 Preview then claim

All other bundles use:

`Enter code → Redeem → live GitHub validation → full reward preview → Claim Reward → atomic grant → reward reveal`

The preview must show every reward and exact amount/item before the user claims. There are no mystery rewards.

If the user exits before `Claim Reward`, nothing is granted and the code remains redeemable.

## 13. Atomic reward transaction

Reward application is all-or-nothing.

Required sequence:

1. Complete live GitHub validation.
2. Locate exact-match code hash.
3. Validate status, time window, flags, version, and prior redemption.
4. Parse and validate every reward object.
5. Calculate a complete next-state copy without mutating persistent state.
6. Verify every grant can be represented safely.
7. Add the redemption-history record to that same next state.
8. Persist the encrypted save.
9. Publish the new in-memory state only after persistence succeeds, or restore the previous state if persistence reports failure.
10. Show compact success or full reward reveal.

If any step fails, no partial rewards are retained and the code is not marked redeemed.

## 14. Redemption history

History is part of persisted save data and contains no plaintext code.

Each history record contains at minimum:

- redemption ID
- redeemed timestamp
- reward summary suitable for display
- reward type summary
- claimed status
- optional puppy/reward artwork reference or stable asset ID

When an already-redeemed code is entered, the UI shows `Already redeemed` plus the reward name/summary and original redemption date when available.

History UI displays reward, redemption date, and claimed status. It must never reconstruct or expose the original plaintext code.

Legacy saves that contain only `redeemedCodeIds` remain valid. During migration, those IDs are treated as previously claimed even when a timestamp is unavailable; history may display `Previously redeemed` without a date for those legacy entries.

## 15. Reset semantics

Two distinct reset levels are required.

### Reset Progress

Clears normal gameplay progression but preserves Puppy Code redemption records/history. This prevents reset-based code farming.

### Erase All Data

A deliberately destructive full reset clears:

- gameplay progression
- settings covered by full app erasure
- redemption history/IDs
- cached catalogue and promotion metadata
- related Puppy Code cooldown state

This behaves like a true fresh local installation from the app's data perspective.

## 16. Invalid-attempt protection and PupEye

Normal users may make several invalid attempts without penalty.

Initial local policy:

- First 4 invalid submissions in a rolling 2-minute window: no cooldown.
- 5th invalid submission in that window: 30-second cooldown.
- Additional repeated invalid bursts within the next 10 minutes may increase the normal cooldown to 60 seconds.
- A successful redemption clears the normal invalid-attempt counter.

PupEye may escalate when submission cadence looks automated. Initial escalation trigger: 5 or more redeem submissions within 10 seconds, or equivalent existing PupEye automation heuristics. Escalated redeem cooldown is capped at 5 minutes.

Cooldown state is persisted locally so closing/reopening the screen does not immediately bypass it.

User-facing text should be simple, for example:

`Too many invalid attempts. Try again in 42 seconds.`

Raw PupEye telemetry or heuristic details are not exposed in normal UI. No permanent local redemption ban is created by this feature.

## 17. Active Promotions

The Puppy Codes screen may show Active Promotions only when the catalogue contains campaign metadata.

Campaign metadata may include:

- stable campaign ID
- title
- short description
- start/end date
- reward teaser
- optional streamed artwork URL/asset reference
- clean user-facing badge such as `Limited Time` or `Special Reward`

Campaign cards never need to reveal the actual secret code. The code is distributed separately through Discord, Patreon, events, social posts, or other channels chosen by the developer.

Cached promotion metadata may remain visible if temporarily offline, but the UI must clearly avoid implying that a new code can be redeemed offline.

## 18. Public plaintext-code cleanup

The current public `App/admin/PUPPY-CODES.md` plaintext code list is removed or rewritten so unpublished/secret Puppy Codes are not stored in plaintext in the public repository.

The replacement documentation may describe:

- how hashing works
- schema 2 fields
- status semantics
- supported reward types
- maintenance rules

It must not list secret/unpublished plaintext Puppy Codes.

The schema-2 catalogue retains only hashes. Existing live catalogue entries are migrated using the canonical plaintext spellings currently documented, then the public plaintext list is sanitized.

Only codes present in the current live `assets/redeem-codes.json` are considered active migration inputs. Stale documentation-only seasonal codes are not automatically reintroduced into the redeem catalogue.

## 19. Existing-code migration

All current live schema-1 entries are migrated to schema 2 without changing their redemption IDs or intended rewards.

Canonical existing code spellings are hashed exactly as currently published before the plaintext list is sanitized. After migration, variants that previously worked because of uppercase/whitespace normalization no longer match.

Existing saved `redeemedCodeIds` remain authoritative so already-claimed codes cannot be reclaimed merely because the catalogue schema changed.

Existing seasonal/event restrictions remain intact unless separately approved.

## 20. Component boundaries

The implementation should isolate responsibilities rather than leave the feature embedded inside the large V6 activity/view model.

### `PuppyCodeCatalog`

Responsibilities:

- live GitHub fetch
- conditional request / ETag handling
- validated persistent cache
- schema parsing
- response-time capture
- catalogue snapshot for UI

### `PuppyCodeValidator`

Responsibilities:

- exact input hashing
- code lookup
- status/time checks
- known-flag checks
- version/reward-schema checks
- already-redeemed check
- structured validation result

### `RewardGrantEngine`

Responsibilities:

- registered reward-type parsing
- preflight validation
- safe next-state construction
- all-or-nothing grant result

### `PuppyCodeHistory`

Responsibilities:

- legacy ID migration
- history-record construction
- display summaries
- reset-preservation rules

### `PuppyCodeAntiAbuse`

Responsibilities:

- invalid-attempt rolling window
- cooldown calculation
- persisted redeem cooldown state
- PupEye escalation integration

### UI components

- Rewards hub
- Daily Rewards destination
- Puppy Codes screen
- Active Promotions list
- Reward Preview
- Reward Reveal
- Redeemed History

The existing V6 ViewModel remains the owner of game state but delegates catalogue/validation/reward responsibilities to these focused units.

## 21. Error handling

All catalogue and claim failures are represented as structured outcomes rather than free-form string-only logic.

Required user-facing states include:

- invalid
- already redeemed
- expired
- disabled/unavailable
- wrong/newer app version required
- unsupported flag
- unsupported reward type/schema
- network/GitHub unavailable
- cooldown active
- reward preflight failure
- save/persistence failure

A failed claim never consumes the code.

## 22. Testing requirements

At minimum, implementation tests must cover:

- exact-case matching
- exact whitespace/hyphen matching
- canonical migrated codes
- wrong-case rejection
- live 200 authorization
- live 304 authorization with valid cache
- offline/network failure rejection
- malformed catalogue rejection
- unknown flag rejection
- contradictory flag rejection
- min/max version rejection
- unknown reward type rejection
- expired/disabled/revoked behavior
- server-time handling for limited-time codes
- already-redeemed behavior
- legacy `redeemedCodeIds` migration
- Upgrade Ticket bounds
- multi-reward atomic success
- multi-reward preflight failure with zero partial grant
- persistence failure with zero consumed redemption
- simple reward immediate confirmation
- major reward preview then claim
- backing out of preview leaves code unused
- invalid-attempt cooldown
- PupEye escalation cap
- Reset Progress preserves redemption history
- Erase All Data clears redemption history and catalogue cache
- Rewards navigation replaces Prestige tab and Prestige remains reachable through Shop

## 23. Out of scope

This redesign does not add:

- VPS/backend service
- custom database/API
- global redemption counters
- QR-code redemption
- progression-based eligibility rules
- repeatable codes
- mystery rewards
- a separate admin code generator/validator tool
- executable behavior downloaded from GitHub

## 24. Success criteria

The redesign is complete when:

1. Bottom navigation is `Play / Care / Shop / Rewards / Settings` and Prestige is accessible within Shop.
2. Rewards Hub exposes Daily Rewards, Puppy Codes, Events, and Special Rewards as mobile-first section cards.
3. Puppy Codes require live GitHub validation for every claim.
4. Schema-2 exact-match codes, statuses, flags, version checks, campaigns, and configurable reward bundles are implemented.
5. All current live codes are migrated without losing existing redemption IDs.
6. Major rewards preview every item before claim; simple Treats-only codes can claim immediately.
7. Reward grants are atomic and cannot partially consume a one-time code.
8. Redeemed History persists across Reset Progress but clears under Erase All Data.
9. Invalid-attempt throttling and PupEye escalation work without permanent local bans.
10. Public plaintext secret-code storage is removed from the repository.
11. Tests cover the validation, migration, reset, atomicity, UI flow, and anti-abuse rules above.
