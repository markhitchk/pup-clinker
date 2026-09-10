# Puppy Exchange Design

Date: 2026-09-10
Status: Approved design
Repository: `markhitchk/pup-clinker`

## Goal

Add a dedicated, backend-free Puppy Exchange system to Puppy Clicker for live friend requests, gifting, trading, transaction history, and recovery. The system must integrate with the existing dynamic GitHub-backed puppy roster and encrypted device-bound save architecture without introducing a player inventory API or cloud database.

## Core Constraints

- GitHub remains the source for puppy assets, roster manifests, redeem definitions, and transfer policy metadata.
- Puppy Clicker does not use a central account, inventory, friend, or trade database.
- Social actions require both players to be online at the same time.
- Peer communication uses WebRTC DataChannels.
- WebRTC signaling uses manual copy/paste offer and answer codes rather than a signaling API.
- No QR codes are used.
- Player saves remain device-bound.
- Username is not the authoritative identity for ownership or trading.

## Player Identity

Each Puppy Clicker installation gets a locally generated identity containing:

- Editable username.
- Permanent Player ID.
- Permanent Friend Code.

The Player ID is a cryptographically random internal identifier created once for the device identity and persisted with the protected local game state.

The Friend Code is a separate randomly generated public identifier tied to the Player ID. It is permanent for the lifetime of that Player ID and cannot be regenerated from Settings.

The username remains editable and is only presentation metadata. Existing username normalization rules continue to apply, so variants such as `HarleyTG`, `HARLEYTG`, and `harleytg` normalize consistently.

Because there is no authoritative registry, Friend Code collisions cannot be proven impossible. The code space must therefore be large enough to make collisions negligible, and WebRTC peers must verify the full Player ID after connection.

## Settings Account Card

The top of Settings gains a dedicated Account card showing:

- Username.
- Player ID.
- Friend Code.
- Device-bound save status.
- Puppy Exchange readiness/status.
- Copy Player ID action.
- Copy Friend Code action.

The Friend Code is the primary shareable identity. No QR-code actions or camera permissions are introduced.

## Puppy Exchange Entry Point

Puppy Exchange is a dedicated UI, not a Settings subsection and not a bottom-navigation tab.

Primary entry point:

- Roster / Profile area.

The Roster launches Puppy Exchange as a separate screen. The roster itself remains responsible for puppy browsing and selection; social transactions remain inside Puppy Exchange.

## Puppy Exchange Navigation

The dedicated Puppy Exchange UI contains:

- Friends.
- Connect.
- Gifts.
- Trade.
- History.

Friends is the default landing tab. The last-used tab may be retained only for the current app session.

A compact identity/status header shows the local username, Friend Code, and connection state. Full Player ID details remain available in Settings.

## Friend Records

Accepted friends are stored locally in the encrypted save and contain at least:

- Friend Player ID.
- Friend Code.
- Last known username.
- Date added.
- Blocked state where applicable.

Player ID is authoritative. Username may update the next time the same Player ID reconnects.

## Friend Request Flow

Friend requests are live only. There are no offline or pending server-side requests.

Flow:

1. Both players are online.
2. Player A enters Player B's permanent Friend Code.
3. Player A starts a manual WebRTC connection.
4. Player A creates an Offer Code.
5. Player B pastes the Offer Code.
6. Player B creates an Answer Code.
7. Player A pastes the Answer Code.
8. A WebRTC DataChannel is established.
9. Both peers exchange identity and protocol metadata.
10. The remote Player ID and Friend Code are verified.
11. Player A sends a live friend request.
12. Player B chooses Accept, Decline, or Block.
13. On acceptance, both devices persist the friendship locally.

## WebRTC Connection Codes

Manual Offer and Answer codes are temporary connection envelopes containing enough data to establish and validate a direct session. They include at least:

- Protocol version.
- Session ID.
- Random nonce.
- Creation time.
- Expiry time.
- Sender Player ID.
- Sender Friend Code.
- Expected Friend Code where applicable.
- SDP data.
- ICE candidate data.
- Integrity/authentication data supported by the local protocol.

Connection codes expire at the earlier of:

- First successful connection.
- Ten minutes after creation.

Expired or already-consumed codes must be rejected with a clear user-facing status.

## WebRTC Network Limitation

STUN may be used to improve direct connectivity.

No TURN relay is required by this design. As a result, direct WebRTC connectivity may fail on restrictive NAT, carrier-grade NAT, or certain mobile networks. Puppy Clicker must report this as a direct connection failure and recommend trying another network or Wi-Fi rather than incorrectly claiming the remote player is offline or the Friend Code is invalid.

## Blocking

Blocking is local to the device.

A blocked Player ID cannot be accepted as a friend and cannot perform gift or trade actions with the local player once the remote identity is known.

Blocked players are manageable from Puppy Exchange -> Friends -> Blocked.

Without a central server, blocking cannot stop another device from creating a WebRTC offer; it prevents Puppy Clicker from accepting social actions after peer identity verification.

## Ownership Model

Puppy Exchange adds a protected local ownership ledger for special, gifted, traded, bound, source-copy, and transferred puppies.

Normal roster discovery remains driven by the existing dynamic roster. The ownership ledger provides transaction provenance and policy enforcement for Exchange features.

Each active special ownership record should include enough information to identify:

- Ownership record ID.
- Puppy asset/style ID.
- Current owner Player ID.
- Acquisition type.
- Original/source owner where applicable.
- Previous ownership record where applicable.
- Source transaction ID.
- Acquisition timestamp.
- Current ownership status.
- Transfer-policy snapshot or manifest revision reference.

The encrypted save remains the local persistence layer.

## Per-Puppy Transfer Policy

Each puppy can declare transfer behavior in its streamed manifest. The UI must not hard-code individual puppy names as policy exceptions.

Supported policy behavior includes:

- Giftable.
- Tradeable.
- Giftable + tradeable.
- Bound / custom.
- Source copy.
- Limited plus configurable transfer rules.
- Neither giftable nor tradeable.

The same policy checks must be enforced by Roster, Gift, and Trade logic rather than only by hiding UI controls.

## Bound / Custom Puppies

A custom puppy made for one person is bound to a specific Player ID.

Bound/custom puppies:

- Cannot be gifted.
- Cannot be traded.
- Cannot be copied through Puppy Exchange.
- Are excluded from selectable gift/trade inventory.
- Remain normally visible and usable by their designated owner.

Username may be retained for provenance/display but is not the ownership key.

## Source Copies

A Source Copy is a protected original entitlement capable of generating legitimate gift copies while remaining permanently owned by its designated source owner.

Source Copies:

- Cannot be consumed by a trade unless a future manifest policy explicitly changes this behavior.
- Remain in the source owner's inventory.
- May create legitimate recipient ownership records when gift policy allows.
- Preserve source provenance.

## Initial HarleyTG Special Puppy

The special frog puppy is the first Owner Gift / Source Copy use case.

Initial redemption requirements:

- Normalized username must equal `harleytg`.
- Redemption is one-time for the local entitlement.
- On successful claim, the Source Copy becomes bound to that installation's generated Player ID.

After claim:

- The source copy remains permanently owned by that Player ID.
- It cannot be accidentally removed by a trade.
- It may create legitimate gift copies according to its configured manifest policy.
- The source entitlement remains valid if the visible username later changes because ownership is bound to Player ID after redemption.

Recipients receive normal legitimate ownership records, not additional Source Copies. Their ability to gift or trade is controlled by the puppy's manifest policy.

## Limited Puppies

`Limited` is an attribute, not a universal transfer rule.

A limited puppy may also be:

- Tradeable.
- Giftable.
- Giftable + tradeable.
- Bound.

Without an authoritative server, Puppy Clicker must not claim to enforce a trustworthy worldwide supply such as exactly 50 copies globally. It can enforce local ownership, transaction rules, and provenance, but global scarcity requires an authoritative online service outside this design.

## Gifts

Gift transactions occur only during an active verified WebRTC session.

Flow:

1. Sender opens Send Gift.
2. The app shows only eligible puppies or clearly disables ineligible puppies with a reason.
3. Sender selects the puppy and recipient.
4. Recipient receives a live gift review.
5. Recipient chooses Accept or Decline.
6. Only acceptance creates the new recipient ownership record.

For copy-style gifting, the sender retains the existing ownership record and the recipient receives a new provenance-linked active ownership record.

For Source Copies, the source ownership record never leaves the owner's save.

If the recipient declines, disconnects, or validation fails before acceptance, no inventory changes occur.

## Trade UI

Trades support:

- One-to-one trades.
- Bundle trades.
- Maximum five puppies per side.

Each side builds its offer independently.

When either player changes their offer, both Ready states reset.

Once both players are Ready, offers lock and both users see the same final review.

Final commitment uses a deliberate hold-to-confirm interaction rather than a single accidental tap.

## Hybrid Trade Architecture

The user experience is a simple live swap, while the underlying transaction engine uses a journaled two-phase model and provenance-linked ownership transfer.

Transaction lifecycle:

- PENDING.
- READY.
- COMMITTING.
- COMPLETED.
- RECOVERY_REQUIRED when necessary.
- CANCELLED where appropriate.

Before ownership changes, both devices persist a matching transaction journal containing at least:

- Transaction ID.
- Transaction type.
- Player A ID.
- Player B ID.
- Offer A hash.
- Offer B hash.
- Ownership record IDs involved.
- Protocol version.
- Creation timestamp.
- Current transaction state.

Both sides exchange and validate matching transaction data before commit.

## Ownership Transfer Semantics

A traded ownership record is not simply deleted.

The sender's previous active ownership record becomes transferred/retired and records:

- Destination Player ID.
- Transaction ID.
- Transfer timestamp.

The recipient receives a new active ownership record linked to the previous ownership record.

This creates a local provenance chain such as:

Claim -> Gift -> Trade -> Trade -> Current Owner

Roster logic only treats the current active ownership record as usable ownership.

## Interrupted Transactions

If a WebRTC session disconnects before the transaction reaches the commit window, the trade can be cancelled safely.

If the session fails during or after commit negotiation, the transaction becomes RECOVERY_REQUIRED.

Puppies involved in a recovery-required transaction become transaction-locked. They remain visible and usable for normal gameplay but cannot be gifted or traded again until the transaction is resolved.

## Trade Recovery System

Puppy Exchange includes a dedicated recovery flow under History.

A player can generate a temporary Recovery Code for an existing locally recorded pending/recovery transaction. The other player pastes it and generates a response.

Recovery envelopes contain at least:

- Existing transaction ID.
- Both Player IDs.
- Offer hashes.
- Recorded transaction states.
- Protocol version.
- Recovery nonce.
- Creation timestamp.
- Expiry timestamp.

Recovery codes are one-use and short-lived.

A recovery code cannot create a new transaction or mint ownership from scratch. The local save must already contain the referenced unresolved transaction.

## Manual Recovery Review

If both transaction journals agree, Puppy Clicker may proceed with the valid deterministic recovery action.

If the journals disagree, the app opens a manual recovery screen showing:

- Both players.
- Both recorded offers.
- Each device's transaction state.
- What fields do not match.

Both players must independently choose the same resolution:

- Complete Trade.
- Cancel Trade.

No resolution is applied until both devices agree.

If one chooses Complete and the other chooses Cancel, the trade remains RECOVERY_REQUIRED and affected puppies remain transaction-locked.

Without an authoritative backend, neither device may unilaterally declare itself the winner.

## Local History

Puppy Exchange -> History stores local records of social transactions including:

- Gifts sent and received.
- Completed trades.
- Declined actions where useful.
- Cancelled trades.
- Recovery-required transactions.
- Recovered transactions.

Detailed ownership provenance is available from Puppy Details / ownership information rather than cluttering the normal Roster cards.

## Policy Validation

Before a puppy can enter a gift or trade transaction, the app validates both the streamed manifest policy and current local ownership record.

Reject or disable a puppy when it is:

- Bound/custom for another purpose.
- Source-protected from trading.
- Already transferred.
- Transaction-locked.
- Unknown to the current roster.
- Not giftable for a gift.
- Not tradeable for a trade.
- Otherwise inconsistent with local ownership state.

The UI should state why an item is unavailable rather than silently hiding every invalid option.

## Global Subject-to-Change Notice

Puppy Clicker gains a reusable global disclaimer component so wording is managed from one place.

Full wording for title/setup surfaces:

> Items, rewards, values, availability, and features are subject to change.

Compact wording for normal in-app surfaces:

> Items and features are subject to change.

The compact notice appears in relevant locations across:

- Play.
- Roster.
- Shop.
- Rewards / Redeem.
- Settings.
- Puppy Exchange.
- Gift confirmation.
- Trade confirmation.
- Trade Recovery.
- Puppy details.
- Future inventory/event screens where the statement applies.

The notice is visible but non-blocking and must not require dismissal.

## Security Boundaries

This design improves consistency and abuse resistance but does not pretend to provide server-grade authority.

The system should:

- Use cryptographically strong random Player IDs, Friend Codes, session IDs, transaction IDs, and nonces.
- Protect local state with the existing encrypted device-bound save architecture.
- Validate peer Player ID and Friend Code after WebRTC connection.
- Validate transaction hashes before commit.
- Reject replayed, expired, or already-consumed connection/recovery envelopes.
- Keep transaction-locked puppies unavailable for additional transfers.
- Keep source and bound ownership rules enforced in transaction logic, not only UI.

Because there is no authoritative backend, a sufficiently modified application or compromised local device cannot be made equivalent to a centrally validated economy. The UI and documentation must not imply otherwise.

## Failure Handling

Expected failures must produce explicit, recoverable states rather than corrupt inventory.

Examples:

- Invalid Friend Code format -> reject before connection flow.
- Invalid/expired Offer or Answer Code -> require a new connection code.
- Direct WebRTC failure -> report direct connection failure and recommend another network.
- Peer identity mismatch -> terminate the session before social actions.
- Gift disconnect before acceptance -> no inventory change.
- Trade disconnect before commit -> cancel safely.
- Trade disconnect during commit -> mark RECOVERY_REQUIRED.
- Mismatched recovery journals -> manual recovery review.
- Manifest policy changes while an item is selected -> revalidate before Ready/Confirm.

## Testing Requirements

Implementation must include tests for the isolated subsystems.

Identity tests:

- Player ID is generated once and remains stable on the same device identity.
- Friend Code is generated once and remains stable.
- Username changes do not alter Player ID or Friend Code.
- Friend Code and Player ID validation reject malformed values.

Connection tests:

- Offer/Answer envelope encode/decode round trip.
- Ten-minute expiry.
- One-use consumption.
- Replay rejection.
- Peer Friend Code and Player ID mismatch rejection.

Ownership tests:

- Bound puppies cannot be gifted or traded.
- Source copies cannot be consumed by normal trades.
- Giftable/tradeable flags are enforced independently.
- Transferred ownership is no longer active for the sender.
- Recipient ownership links to prior provenance.
- Transaction-locked puppies cannot enter another transfer.

Gift tests:

- Decline leaves both inventories unchanged.
- Disconnect before acceptance leaves both inventories unchanged.
- Accepted copy-style gift retains sender ownership and creates recipient ownership.

Trade tests:

- 1:1 trade.
- Bundle trade up to five items per side.
- Sixth item is rejected.
- Editing an offer resets both Ready states.
- Commit requires matching transaction data on both peers.
- Interrupted commit enters recovery state.

Recovery tests:

- Recovery only references an existing unresolved local transaction.
- Fabricated recovery transaction IDs are rejected.
- Matching journals resolve safely.
- Mismatched journals require manual review.
- Conflicting Complete/Cancel decisions leave the transaction unresolved.

UI tests:

- Puppy Exchange launches from Roster/Profile.
- Friends is the default tab.
- Settings Account card shows Player ID and Friend Code.
- No QR-code UI exists.
- Ineligible puppies show policy reasons.
- Subject-to-change notices render on required surfaces.

## Non-Goals

This design does not add:

- Central accounts.
- Cloud inventory storage.
- Offline friend requests.
- Server-hosted pending trades.
- Global authoritative item scarcity.
- QR-code friend or signaling workflows.
- TURN relay infrastructure.
- A backend API for Puppy Exchange.

## Implementation Boundary

The implementation should preserve existing Puppy Clicker gameplay, dynamic roster behavior, redeem-code behavior, save encryption, and PupEye integrity protections while extending them with narrowly scoped Player Identity, Puppy Exchange, ownership-ledger, WebRTC-session, transaction, and recovery components.

No implementation should begin until this design is reviewed and approved as the committed specification.
