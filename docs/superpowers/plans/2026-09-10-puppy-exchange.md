# Puppy Exchange Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add device-bound Player IDs and Friend Codes, manifest-driven transfer policy, backend-free manual-signaling WebRTC sessions, local gifting/trading/recovery journals, a dedicated Puppy Exchange UI, and the approved subject-to-change notices.

**Architecture:** Keep GitHub as the puppy/catalog source and the encrypted local save as player state. New exchange modules isolate identity, manifest policy, ownership/transactions, signaling envelopes, WebRTC transport, and Compose UI so the existing roster/gameplay code only receives narrow integration hooks.

**Tech Stack:** Kotlin 2.x, Android 8+/API 26+, Jetpack Compose Material3, Android SharedPreferences/Keystore save protection, `org.json`, WebRTC Android SDK (`io.github.webrtc-sdk:android:150.7871.01`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-10-puppy-exchange-design.md`

## Global Constraints

- No central player, friend, inventory, gifting, or trade API.
- Manual copy/paste WebRTC Offer/Answer codes only; no QR codes.
- Both players must be online for friend requests, gifts, and trades.
- Player ID and Friend Code are permanent for the local device identity.
- Friend Code cannot be regenerated.
- Trade bundles are capped at five puppies per side.
- Connection and recovery codes expire after one successful use or ten minutes, whichever happens first.
- Per-puppy policy controls giftable, tradeable, bound/custom, source-copy, and limited behavior.
- The HarleyTG special puppy initial claim is restricted to normalized username `harleytg`; its source copy binds to that device Player ID.
- Existing roster, redeem, save crypto, PupEye, gameplay, and V1/V2 compatibility behavior must remain intact.
- Full title/setup notice: `Items, rewards, values, availability, and features are subject to change.`
- Compact app notice: `Items and features are subject to change.`

---

### Task 1: Device Identity

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyPlayerIdentity.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyExchangeIdentityTest.kt`

**Interfaces:**
- Produces: `PuppyPlayerIdentity.playerId(context): String`, `friendCode(context): String`, `isValidPlayerId(String): Boolean`, `isValidFriendCode(String): Boolean`.
- Player ID format: `PC-` + 32 uppercase hex characters.
- Friend Code format: `PUP-XXXX-XXXX-XXXX` using uppercase unambiguous alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`.

- [ ] **Step 1: Write failing identity-format tests.** Test pure validators against valid values, malformed prefixes, lowercase/short values, and confirm username normalization remains lowercase.
- [ ] **Step 2: Run `cd App && ./gradlew testDebugUnitTest --tests '*PuppyExchangeIdentityTest'` and verify failure because the new identity functions do not exist.**
- [ ] **Step 3: Implement secure one-time generation.** Use `SecureRandom`; persist Player ID and Friend Code in `puppy_player_identity_v1`; never change them from `setUsername`; include both in `metadata()`.
- [ ] **Step 4: Run the focused identity tests and then `./gradlew testDebugUnitTest`.**
- [ ] **Step 5: Commit identity changes.**

### Task 2: Manifest Transfer Policy

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/DynamicPuppyRoster.kt`
- Modify: `App/app/src/test/java/com/harleytg/puppyclicker/DynamicPuppyRosterTest.kt`

**Interfaces:**
- Produce `PuppyTransferPolicy(giftable:Boolean, tradeable:Boolean, bound:Boolean, sourceCopy:Boolean, limited:Boolean)`.
- Add `transferPolicy: PuppyTransferPolicy` and `hiddenUntilOwned: Boolean` to `PuppyRosterAsset` with backward-compatible defaults.
- Dynamic manifest accepts an optional `transfer_policy` object and optional `hidden_until_owned` boolean.

- [ ] **Step 1: Add failing manifest tests** for gift+trade, bound/custom, source-copy, limited, hidden, and missing-policy defaults.
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*DynamicPuppyRosterTest'` and verify failure.**
- [ ] **Step 3: Parse and cache policy metadata** while preserving legacy V1/V2 defaults and existing manifest fields.
- [ ] **Step 4: Re-run focused and full unit tests.**
- [ ] **Step 5: Commit manifest-policy support.**

### Task 3: Ownership Ledger and Journal

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyExchangeLedger.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyExchangeLedgerTest.kt`

**Interfaces:**
- `enum class OwnershipStatus { ACTIVE, TRANSFERRED, LOCKED }`
- `enum class AcquisitionType { CLAIM, GIFT, TRADE, SOURCE }`
- `data class PuppyOwnershipRecord(...)`
- `enum class ExchangeTransactionState { PENDING, READY, COMMITTING, COMPLETED, RECOVERY_REQUIRED, CANCELLED }`
- `enum class ExchangeTransactionType { GIFT, TRADE }`
- `data class ExchangeTransactionRecord(...)`
- `PuppyExchangeLedger` serializes records to canonical JSON and persists them under the protected game preference store so existing save sealing covers the ledger.
- Policy validation returns a typed result with a user-visible reason.

- [ ] **Step 1: Write failing ledger tests** for policy eligibility, source-copy protection, bound rejection, transfer retirement, transaction locking, bundle limit, canonical offer hashing, and provenance links.
- [ ] **Step 2: Run focused tests and verify failure.**
- [ ] **Step 3: Implement immutable ownership/journal models and pure validation/hash functions.**
- [ ] **Step 4: Implement SharedPreferences persistence with canonical JSON string sets/records and transaction-lock lookup.**
- [ ] **Step 5: Re-run focused and full unit tests.**
- [ ] **Step 6: Commit ledger implementation.**

### Task 4: Manual Signaling and Recovery Envelopes

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyExchangeProtocol.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyExchangeProtocolTest.kt`

**Interfaces:**
- `ExchangeSignalEnvelope` contains protocol version, session ID, nonce, created/expiry times, sender Player ID/Friend Code, optional expected Friend Code, SDP type/body, ICE candidates.
- `ExchangeRecoveryEnvelope` references only an existing transaction ID and both transaction states/hashes.
- Encode as UTF-8 JSON -> DEFLATE -> Base64 URL-safe no-wrap with prefixes `PUP-O1-`, `PUP-A1-`, `PUP-R1-`.
- Reject decoded payloads above 256 KiB, malformed timestamps, wrong prefix/version, expired envelopes, and consumed nonces.

- [ ] **Step 1: Write failing round-trip, expiry, size, malformed, and replay tests.**
- [ ] **Step 2: Run focused tests and verify failure.**
- [ ] **Step 3: Implement deterministic encoding/decoding and `ConsumedNonceStore`.**
- [ ] **Step 4: Re-run focused and full tests.**
- [ ] **Step 5: Commit protocol code.**

### Task 5: WebRTC DataChannel Transport

**Files:**
- Modify: `App/app/build.gradle.kts`
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyExchangeWebRtc.kt`
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyExchangeSession.kt`

**Interfaces:**
- Add `implementation("io.github.webrtc-sdk:android:150.7871.01")`.
- Use DataChannels only; no camera/microphone permissions.
- ICE servers: STUN only; no TURN.
- `PuppyExchangeSession` exposes `StateFlow<ExchangeConnectionState>` and actions to create offer, accept offer/create answer, apply answer, send typed exchange message, and disconnect.
- Manual signaling waits for ICE gathering completion before generating the code.
- After DataChannel open, exchange an identity hello containing Player ID, Friend Code, normalized username, protocol version, and app version; reject mismatch against expected Friend Code or blocked Player ID.

- [ ] **Step 1: Add the dependency and transport/session source with narrow WebRTC wrapper interfaces.**
- [ ] **Step 2: Compile with `./gradlew compileDebugKotlin`; fix API mismatches without weakening protocol checks.**
- [ ] **Step 3: Add session-state unit tests around pure identity/message validation where JVM-testable.**
- [ ] **Step 4: Run unit tests and `assembleDebug`.**
- [ ] **Step 5: Commit WebRTC transport.**

### Task 6: Gift/Trade/Recovery Engine

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyExchangeEngine.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/PuppyExchangeEngineTest.kt`

**Interfaces:**
- Engine owns live Friend Request, Gift, Trade, and Recovery messages over `PuppyExchangeSession`.
- Gifts create recipient copies only after recipient acceptance; sender keeps copy-style/source ownership.
- Trades use matching PENDING -> READY -> COMMITTING -> COMPLETED journals; edits reset both ready flags.
- Disconnect before COMMITTING cancels without mutations; failure during/after commit marks RECOVERY_REQUIRED and locks involved records.
- Recovery can only reference an unresolved local transaction. Matching journals resolve deterministically; disagreement requires both peers to choose the same Complete/Cancel result.

- [ ] **Step 1: Write failing engine tests** for gift decline/accept, 1:1 trade, 5-item bundle, sixth-item rejection, ready reset, commit mismatch, interrupted commit, fabricated recovery ID, and conflicting recovery decisions.
- [ ] **Step 2: Run focused tests and verify failure.**
- [ ] **Step 3: Implement the state machine with pure reducers and ledger mutation only at defined commit points.**
- [ ] **Step 4: Run focused and full unit tests.**
- [ ] **Step 5: Commit exchange engine.**

### Task 7: Puppy Exchange UI and Account Card

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyExchangeUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt`
- Add/modify Compose instrumentation tests under `App/app/src/androidTest/java/com/harleytg/puppyclicker/`.

**Interfaces:**
- Dedicated screen tabs: Friends, Connect, Gifts, Trade, History; Friends default.
- Roster gets an `onOpenExchange` callback and a visible `Puppy Exchange` entry button.
- Exchange is not added to bottom navigation. V6 shell uses an overlay/dedicated screen state and Back returns to Roster.
- Settings top area displays Username, Player ID, Friend Code, `Device Bound`, copy actions, and exchange readiness.
- No QR UI.

- [ ] **Step 1: Add failing Compose tests** for roster entry point, Exchange tabs/default, no QR content, identity card text/actions, 5-slot trade selector, recovery-required action, and policy reason text.
- [ ] **Step 2: Implement `PuppyExchangeUi.kt` using existing Material3 visual language and streamed puppy portraits.**
- [ ] **Step 3: Wire Roster and V6 navigation without adding another bottom-nav item.**
- [ ] **Step 4: Move Account information to the top of Settings while preserving existing profile/birthday controls.**
- [ ] **Step 5: Run unit tests, compile, and available instrumentation tests.**
- [ ] **Step 6: Commit UI integration.**

### Task 8: Global Subject-to-Change Notice

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyChangeNotice.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyLaunchUi.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt`
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt` only where the global shell cannot cover a modal/panel.
- Modify Exchange transaction dialogs where needed.

**Interfaces:**
- `PuppyChangeNotice(full: Boolean = false, modifier: Modifier = Modifier)` is the single wording source.

- [ ] **Step 1: Add a small JVM constant test or Compose test asserting exact approved wording.**
- [ ] **Step 2: Implement the reusable composable/constants.**
- [ ] **Step 3: Render the full notice on title/setup and compact notice in the V6 app shell plus constrained Exchange confirmation/recovery surfaces where the shell is not visible.**
- [ ] **Step 4: Run tests and `assembleDebug`.**
- [ ] **Step 5: Commit disclaimer integration.**

### Task 9: Final Regression and Documentation

**Files:**
- Modify README/docs only if build/user-facing feature documentation requires it.

- [ ] **Step 1: Run `cd App && ./gradlew testDebugUnitTest assembleDebug`.** Expected: BUILD SUCCESSFUL.
- [ ] **Step 2: Review generated APK dependency/manifest to confirm no camera or microphone permissions and no TURN/backend endpoint was introduced.**
- [ ] **Step 3: Verify existing redeem, roster, save crypto, PupEye, and onboarding unit tests remain green.**
- [ ] **Step 4: Inspect `git diff` for hard-coded puppy exceptions, QR code references, accidental identity portability, or plaintext inventory persistence; correct any findings.**
- [ ] **Step 5: Commit final regression fixes/documentation if any.**

## Plan Self-Review

- Spec coverage: identity, permanent friend code, manual WebRTC signaling, no API/QR, policy metadata, source/bound/limited rules, gifting, 1-5 item trades, two-phase journals, provenance, recovery, dedicated UI, Settings account card, and global notices are mapped to tasks.
- Placeholder scan: no TBD/TODO/implement-later steps remain.
- Type consistency: Player ID/Friend Code flow from Task 1 into protocol/session/ledger/UI; transaction states and ownership records originate in Task 3 and are consumed by Tasks 6-7.
- Scope: tasks are independently reviewable but together form one cohesive Puppy Exchange subsystem.