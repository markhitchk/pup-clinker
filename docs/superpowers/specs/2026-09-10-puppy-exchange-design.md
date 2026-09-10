# Puppy Exchange Design

Date: 2026-09-10
Status: Approved design
Repository: `markhitchk/pup-clinker`

## Goal

Add a dedicated, backend-free **Puppy Exchange** system to Puppy Clicker for live friend requests, gifting, trading, local history, and trade recovery. It must integrate with the existing GitHub-backed dynamic puppy roster and encrypted device-bound save architecture without adding a central account, friend, inventory, or trade API.

## Core Constraints

- GitHub remains the source for puppy assets, roster manifests, redeem definitions, and transfer-policy metadata.
- Puppy Clicker does not use a central player, inventory, friend, or trade database.
- Social actions require both players to be online at the same time.
- Peer communication uses WebRTC DataChannels.
- WebRTC signaling uses manual copy/paste Offer and Answer codes.
- No QR-code workflow is used.
- Player identity is device-bound.
- Username is display metadata, not the authoritative ownership identity.
- The system must preserve existing gameplay, dynamic roster, redeem, save-encryption, and PupEye behavior.

## Player Identity

Each Puppy Clicker installation gets three identity fields:

- Editable username.
- Permanent Player ID.
- Permanent Friend Code.

The **Player ID** is a cryptographically random internal identifier generated once for the device identity.

The **Friend Code** is a separate cryptographically random public identifier tied to that Player ID. It is permanent for the lifetime of the Player ID and cannot be regenerated from Settings.

The username remains editable and follows the app's existing normalization rules. Variants such as `HarleyTG`, `HARLEYTG`, and `harleytg` therefore normalize consistently, but username is not used as the long-term ownership key.

Because there is no central registry, Friend Code collisions cannot be proven impossible. The Friend Code space must be large enough to make collisions negligible, and a connected peer must verify the full Player ID before social actions are enabled.

### Device-bound identity rule

Player ID and Friend Code belong to the local device identity. They are not portable account credentials.

If Puppy Clicker supports a gameplay save import/export path, importing gameplay data onto a different device must **not silently replace that device's Player ID or Friend Code**. The destination device retains its own identity unless a future, separately designed identity-migration feature is explicitly added.

Bound/custom puppy ownership tied to a Player ID is therefore also device-identity-specific under this design. Trade Recovery does not act as device migration.

## Settings Account Card

The top of Settings gains a dedicated **Account** card showing:

- Username.
- Player ID.
- Friend Code.
- Device-bound save status.
- Puppy Exchange readiness/status.
- Copy Player ID action.
- Copy Friend Code action.

Friend Code is the primary shareable identity. No QR-code actions or camera permissions are introduced.

## Puppy Exchange Entry Point

Puppy Exchange is a dedicated screen, not a Settings subsection and not a bottom-navigation tab.

The primary entry point is from the **Roster / Profile area**. Roster remains responsible for browsing/selecting puppies; transactions and social state remain inside Puppy Exchange.

## Puppy Exchange Navigation

The Puppy Exchange screen contains:

- **Friends**
- **Connect**
- **Gifts**
- **Trade**
- **History**

Friends is the default landing tab. The last-used tab may be remembered only for the current app session.

A compact header shows local username, Friend Code, and connection state. Full Player ID details remain in Settings.

## Friend Records

Accepted friends are stored locally in the encrypted save. A record contains at least:

- Friend Player ID.
- Friend Code.
- Last known username.
- Date added.
- Blocked state where applicable.

Player ID is authoritative. Username may refresh when the same Player ID reconnects.

## Friend Requests

Friend requests are **live only**. There are no offline/pending server-side requests.

Flow:

1. Both players are online.
2. Player A enters Player B's permanent Friend Code as the expected remote identity.
3. Player A starts the manual WebRTC connection flow.
4. Player A creates an Offer Code.
5. Player B pastes the Offer Code.
6. Player B creates an Answer Code.
7. Player A pastes the Answer Code.
8. The WebRTC DataChannel establishes.
9. Both peers exchange Player ID, Friend Code, username, protocol version, and app version.
10. Puppy Clicker verifies the connected peer against the expected identity.
11. Player A sends a live Friend Request.
12. Player B chooses Accept, Decline, or Block.
13. On acceptance, both devices persist the friendship locally.

A Friend Code does **not** resolve or locate another player by itself because no lookup API exists. It acts as the permanent human-shareable identity and expected-peer check; the manual WebRTC Offer/Answer exchange performs signaling.

## WebRTC Connection Codes

Offer and Answer codes are temporary signaling envelopes containing enough data to establish and validate the direct session. They contain at least:

- Protocol version.
- Session ID.
- Random nonce.
- Creation time.
- Expiry time.
- Sender Player ID.
- Sender Friend Code.
- Expected Friend Code where applicable.
- SDP.
- ICE candidate data.
- Integrity/authentication metadata supported by the protocol.

Connection codes expire at the earlier of:

- First successful use on the relevant local identity.
- Ten minutes after creation.

Expired, malformed, or already-consumed codes are rejected with a clear status.

### Manual-signaling requirement

Because signaling is copy/paste rather than trickle signaling through a service, the app must gather the required ICE candidates before producing the final Offer/Answer code. The encoded envelope should be compressed and text-safe so it can be copied between apps without changing its bytes.

The implementation must enforce sane envelope-size limits and fail clearly rather than accepting truncated signaling data.

## WebRTC Network Limitation

STUN may be used to improve direct connectivity.

No TURN relay is required by this design. Direct WebRTC may therefore fail on restrictive NAT, carrier-grade NAT, or some mobile networks. Puppy Clicker must report this as **Direct Connection Failed** and recommend another network/Wi-Fi and a fresh connection code rather than incorrectly claiming that the friend is offline or the Friend Code is invalid.

## Blocking

Blocking is local.

A blocked Player ID cannot be accepted as a friend and cannot perform gift/trade actions with the local player once remote identity is known.

Blocked players are managed from **Puppy Exchange -> Friends -> Blocked**.

With no server, blocking cannot prevent another device from constructing an Offer Code. It prevents Puppy Clicker from accepting social actions after identity verification.

## Ownership Ledger

Puppy Exchange adds a protected local ownership ledger for special, gifted, traded, bound, source-copy, and transferred puppies.

Normal roster discovery remains driven by the dynamic roster. The ledger provides transaction provenance and transfer-policy enforcement.

An ownership record contains at least:

- Ownership record ID.
- Puppy asset/style ID.
- Current owner Player ID.
- Acquisition type.
- Original/source owner where applicable.
- Previous ownership record ID where applicable.
- Source transaction ID where applicable.
- Acquisition timestamp.
- Ownership status.
- Transfer-policy snapshot or manifest revision reference.

The encrypted device-bound save remains the local persistence layer.

## Per-Puppy Transfer Policy

Each streamed puppy may declare its own transfer behavior. The UI and transaction engine must not hard-code puppy names as policy exceptions.

Supported behavior includes:

- Giftable.
- Tradeable.
- Giftable + tradeable.
- Bound / custom.
- Source copy.
- Limited plus configurable transfer rules.
- Neither giftable nor tradeable.

The same policy rules are enforced in Roster selection, Gift, Trade, commit validation, and recovery.

## Bound / Custom Puppies

A custom puppy made specifically for one person is bound to that person's **Player ID**.

Bound/custom puppies:

- Cannot be gifted.
- Cannot be traded.
- Cannot be copied through Puppy Exchange.
- Are excluded or disabled in gift/trade selectors with a visible reason.
- Remain normally visible and usable by the designated owner.

Username may be stored as provenance/display metadata, but Player ID is the ownership key.

## Source Copies

A **Source Copy** is a protected original entitlement capable of creating legitimate gift copies while remaining permanently owned by its designated source owner.

Source Copies:

- Stay in the source owner's inventory.
- Can create recipient copies only when the puppy policy allows gifting.
- Preserve source provenance.
- Cannot be consumed by a normal trade unless a future manifest policy explicitly changes that rule.

## Initial HarleyTG Special Puppy

The frog puppy is the first Owner Gift / Source Copy use case.

Initial redemption requirements:

- Normalized username must equal `harleytg`.
- Redemption is one-time for the local entitlement.
- On successful redemption, the Source Copy is bound to that installation's generated Player ID.

After claim:

- The Source Copy remains permanently owned by that Player ID.
- It cannot be accidentally consumed by a trade.
- It may create legitimate gift copies according to manifest policy.
- Changing the visible username later does not change Source Copy ownership.

Recipients receive normal legitimate ownership records, not additional Source Copies. Their ability to gift or trade the puppy is controlled by that puppy's configured policy.

## Limited Puppies

`Limited` is an attribute rather than a universal transfer rule.

A limited puppy may also be giftable, tradeable, both, bound, or non-transferable.

Without an authoritative backend, Puppy Clicker must not claim to enforce a trustworthy worldwide quantity such as exactly 50 copies globally. It can enforce local ownership, transaction rules, and provenance, but authoritative global scarcity is outside this design.

## Gifts

Gift transactions require an active verified WebRTC session.

Flow:

1. Sender opens Send Gift.
2. The app shows eligible puppies and clearly explains why ineligible puppies cannot be selected.
3. Sender selects the puppy and reviews the recipient.
4. Recipient receives a live gift review.
5. Recipient chooses **Accept Gift** or **Decline**.
6. Only acceptance creates the recipient ownership record.

For copy-style gifting, the sender retains their active ownership record and the recipient receives a new provenance-linked active ownership record.

A Source Copy never leaves the source owner's save.

Decline, validation failure, or disconnect before acceptance leaves both inventories unchanged.

## Trade UI

Trades support:

- One puppy for one puppy.
- Bundle trades.
- Maximum five puppies per side.

Each player builds an offer independently. Any offer edit resets both Ready states.

When both players are Ready, offers lock and both devices display the same final review.

Final commitment uses a deliberate **hold-to-confirm** interaction rather than a single accidental tap.

## Hybrid Trade Architecture

The player experience is a simple live swap, while the transaction engine uses a journaled two-phase model plus provenance-linked ownership transfer.

Transaction states:

- `PENDING`
- `READY`
- `COMMITTING`
- `COMPLETED`
- `RECOVERY_REQUIRED`
- `CANCELLED`

Before any ownership mutation, both devices persist a transaction journal containing at least:

- Transaction ID.
- Transaction type.
- Player A ID.
- Player B ID.
- Offer A hash.
- Offer B hash.
- Ownership record IDs involved.
- Protocol version.
- Creation timestamp.
- Current state.
- Enough pre-commit ownership information to validate or safely restore local state during an agreed cancellation/recovery.

Both peers exchange and validate matching transaction data before commit.

## Ownership Transfer Semantics

A traded ownership record is not deleted.

The sender's previous active ownership record becomes transferred/retired and records at least:

- Destination Player ID.
- Transaction ID.
- Transfer timestamp.

The recipient receives a new active ownership record linked to the previous record.

This produces a local provenance chain such as:

`Claim -> Gift -> Trade -> Trade -> Current Owner`

Roster logic treats only the current active ownership record as transferable ownership.

## Interrupted Transactions

If WebRTC disconnects before entering the commit window, the trade can be cancelled without ownership changes.

If the session fails during or after commit negotiation, the transaction becomes `RECOVERY_REQUIRED`.

Affected puppies become **transaction locked**. They remain visible and usable for normal gameplay but cannot be gifted or traded again until recovery resolves the transaction.

## Trade Recovery

Puppy Exchange -> History contains a dedicated **Recover Trade** flow.

A player can generate a Recovery Code only for an unresolved transaction already present in that local save. The other player pastes the Recovery Code and generates a response.

Recovery envelopes contain at least:

- Existing transaction ID.
- Both Player IDs.
- Offer hashes.
- Each recorded transaction state.
- Recovery protocol version.
- Random recovery nonce.
- Creation timestamp.
- Expiry timestamp.

Recovery codes are one-use and expire after ten minutes, matching the connection-code safety window.

A Recovery Code can never create a new transaction or mint puppy ownership from scratch. The referenced unresolved transaction must already exist in the local journal.

## Manual Recovery Review

If both transaction journals agree, Puppy Clicker may perform the valid deterministic recovery action.

If journals disagree, the app opens a manual review showing:

- Both players.
- Both recorded offers.
- Each device's recorded transaction state.
- The detected mismatch.

Both players independently choose the same outcome:

- **Complete Trade**
- **Cancel Trade**

No resolution is applied until both devices agree.

If one selects Complete and the other selects Cancel, the transaction remains `RECOVERY_REQUIRED` and the affected puppies stay transaction-locked.

For an agreed cancellation after one side has locally entered a later commit stage, that device uses the pre-commit journal state to restore its own local ownership consistently before clearing the lock.

Without an authoritative backend, neither device may unilaterally declare itself the winner.

## Local History

Puppy Exchange -> History stores local records of social transactions including:

- Gifts sent and received.
- Completed trades.
- Declined actions where useful.
- Cancelled trades.
- Recovery-required transactions.
- Recovered transactions.

Detailed puppy provenance belongs in Puppy Details / ownership information rather than cluttering normal roster cards.

## Policy Validation

Before a puppy enters a Gift or Trade transaction, Puppy Clicker validates both the current streamed manifest policy and local ownership record.

Reject or disable transfer when the puppy is:

- Bound/custom.
- Source-protected from trading.
- Already transferred.
- Transaction-locked.
- Unknown to the current roster.
- Not giftable for a gift.
- Not tradeable for a trade.
- Otherwise inconsistent with the local ownership ledger.

The UI must explain the reason instead of silently hiding every unavailable item.

## Global Subject-to-Change Notice

Puppy Clicker gains one reusable global disclaimer component so wording is controlled from one place.

Full wording on title/setup surfaces:

> Items, rewards, values, availability, and features are subject to change.

Compact wording on standard in-app surfaces:

> Items and features are subject to change.

The compact notice is shown on every normal full-screen Puppy Clicker surface where the global app shell can render it, including Play, Roster, Shop, Rewards/Redeem, Settings, Puppy Exchange, gift/trade confirmations, Trade Recovery, Puppy Details, and future inventory/event screens. Modal or constrained transaction views may render the same compact notice within the panel/footer instead of duplicating a second global footer.

The notice is visible but non-blocking and never requires dismissal.

## Security Boundaries

This design improves consistency and abuse resistance but does not pretend to provide server-grade authority.

The implementation should:

- Use cryptographically strong randomness for Player IDs, Friend Codes, session IDs, transaction IDs, and nonces.
- Protect local state with the existing encrypted device-bound save architecture.
- Validate peer Player ID and Friend Code after connection.
- Validate transaction hashes before commit.
- Reject replayed, expired, malformed, truncated, or consumed connection/recovery envelopes.
- Keep transaction-locked puppies unavailable for additional transfers.
- Enforce bound/source/transfer policy in transaction logic, not only UI.
- Keep sufficient local journal data to recover from interrupted commits.

Because there is no authoritative backend, a sufficiently modified client or compromised local device cannot be made equivalent to a centrally validated economy. UI/documentation must not claim otherwise.

## Failure Handling

Expected failures produce explicit recoverable states instead of corrupting inventory.

Examples:

- Invalid Friend Code format -> reject before connection flow.
- Invalid/expired Offer or Answer Code -> require a fresh connection code.
- Truncated signaling envelope -> reject before WebRTC setup.
- Direct WebRTC failure -> report direct connection failure and recommend another network.
- Peer identity mismatch -> terminate session before social actions.
- Gift disconnect before acceptance -> no inventory change.
- Trade disconnect before commit -> cancel safely.
- Trade disconnect during commit -> mark `RECOVERY_REQUIRED`.
- Mismatched recovery journals -> manual recovery review.
- Manifest policy changes while selected -> revalidate before Ready and again before final commit.

## Testing Requirements

### Identity tests

- Player ID is generated once and remains stable on the same device identity.
- Friend Code is generated once and remains stable.
- Username changes do not alter Player ID or Friend Code.
- Gameplay save import does not silently replace the destination device's Player ID/Friend Code.
- Malformed Player IDs and Friend Codes are rejected.

### Connection tests

- Offer/Answer envelope encode/decode round trip.
- ICE gathering is complete before manual signaling code generation.
- Text-safe compression/encoding round trip.
- Ten-minute expiry.
- One-use local consumption.
- Replay rejection.
- Truncated/oversized envelope rejection.
- Expected Friend Code mismatch rejection.
- Remote Player ID mismatch rejection where an established friend identity is expected.

### Ownership tests

- Bound puppies cannot be gifted or traded.
- Source Copies cannot be consumed by normal trades.
- Giftable and tradeable flags are enforced independently.
- Transferred ownership is no longer active for the sender.
- Recipient ownership links to prior provenance.
- Transaction-locked puppies cannot enter another transfer.

### Gift tests

- Decline leaves both inventories unchanged.
- Disconnect before acceptance leaves both inventories unchanged.
- Accepted copy-style gift retains sender ownership and creates recipient ownership.
- Source Copy remains with the source owner after gifting.

### Trade tests

- One-to-one trade.
- Bundle trade up to five puppies per side.
- Sixth puppy is rejected.
- Editing an offer resets both Ready states.
- Commit requires matching transaction data on both peers.
- Interrupted commit enters recovery state.

### Recovery tests

- Recovery references only an existing unresolved local transaction.
- Fabricated transaction IDs are rejected.
- Recovery code expires after ten minutes and is one-use locally.
- Matching journals resolve safely.
- Mismatched journals require manual review.
- Conflicting Complete/Cancel choices remain unresolved.
- Agreed cancellation restores valid local pre-commit state before unlocking puppies.

### UI tests

- Puppy Exchange launches from Roster/Profile.
- Friends is the default tab.
- Settings Account card shows Player ID and Friend Code.
- No QR-code UI exists.
- Ineligible puppies show policy reasons.
- Gift acceptance is explicit.
- Final trade uses deliberate confirmation.
- Global subject-to-change notice appears on required surfaces.

## Non-Goals

This design does not add:

- Central accounts.
- Cloud inventory storage.
- Offline friend requests.
- Server-hosted pending trades.
- Friend Code lookup/discovery API.
- Global authoritative item scarcity.
- QR-code friend/signaling workflows.
- TURN relay infrastructure.
- Puppy Exchange backend API.
- Cross-device Player ID migration/recovery.

## Implementation Boundary

Implementation should introduce focused components for Player Identity, Puppy Exchange UI, local friend storage, WebRTC session/signaling envelopes, ownership ledger, gift/trade transaction engine, history, recovery, and the global disclaimer while preserving existing Puppy Clicker behavior.

No implementation begins until this committed specification is reviewed and approved.
