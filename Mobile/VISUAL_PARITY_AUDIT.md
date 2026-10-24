# Puppy Clicker Kotlin → React Android Visual Parity Audit

**Audit state:** NOT CERTIFIED. A screen being source-ported or unit-tested does NOT imply 100% screenshot or runtime parity.

**Production reference:** `App/` Kotlin Android, actual launcher functions and UI test captures.
**React target:** `Mobile/` on existing `fix/react-android-kotlin-revamp-ui-20261023` (PR #74, targeting `feat/react-native-full-android`), installed side-by-side as `com.harleytg.puppyclicker.preview`.
**Never:** merge the preview into production, rename its package, migrate guest currencies or mark parity 100% without an on-device Kotlin/React comparison.

## Screen-by-screen evidence

| Screen | Source port state | Original artwork | Actual Android screenshot pair | Result |
| --- | --- | --- | --- | --- |
| Launcher Play | React structure ported in Part 1 | V1/V2 art bundled | **Missing** | **Not 100% verified** |
| Care | Kotlin care/need controls ported in Part 2 | Puppy portrait bundled | **Missing** | **Not 100% verified** |
| Daily | gift, goals, park ported; Kotlin hard-coded goal text mismatch documented | Native-ish styles | **Missing** | **Not 100% verified** |
| Shop: normal upgrades | code/price logic ported in Part 3 | Per-upgrade icon reference | **Missing** | **Not 100% verified** |
| Shop: ticket upgrades | ticket inventory, costs ported | Rarity icons | **Missing** | **Not 100% verified** |
| Shop: Pup Coins | accessories and ticket buy ported | Partial | **Missing** | **Not 100% verified** |
| Prestige | Kotlin level/skill UI ported | Partial | **Missing** | **Not 100% verified** |
| Puppy Collection | 29 stable V1/V2 IDs and locked art ported Part 4 | Original PNGs bundled | **Missing** | **Not 100% verified** |
| V3+ browser/favorites | Extra preview browser, not launched Kotlin V6Pups screen | Remote assets | **Missing** | **Not 100% verified** |
| Gacha | Original 5 PNGs, turn/drop/open/reveal ported Part 4 | Byte-exact machine/capsule PNGs | **Missing** | **Not 100% verified** |
| Casino hub | Guest navigation and chip conversion | Original art | **Missing** | **Not 100% verified** |
| Blackjack | Part 5A layout/card gameplay ported | Byte-exact PNGs | **Missing** | **Not 100% verified** |
| Slots | Part 5B layout/reel/lever and saved round ported | Original 10 PNGs (4 main + 6 reel icons) | **Missing** | **Not 100% verified** |
| Roulette | Part 5C source UI/ledger port: 37 pockets, full selectable table, 2.6s reveal and saved round; animations and pixel layout unverified | Four original Kotlin PNGs | **Missing** | **Not 100% verified** |
| Plinko | Part 5D board layer/peg/ball source port and validated saved 8-bounce guest round; actual Android timing and clipping unverified | Seven byte-exact Kotlin PNGs | **Missing** | **Not 100% verified** |
| Scratchers | Part 5E ported 48×20 mask, 68% reveal, weighted coin movement, tier cards and saved guest prize; React overlay still uses tiled Views instead of Kotlin Offscreen Canvas BlendMode.Clear | Original border and byte-exact Pup Coin PNG | **Missing** | **Not 100% verified** |
| Lucky Pup Wheel | Part 5F seven source-weighted slices, segmented labels, independent 18-click pointer, committed spin + once-only payout. 2% protected PUP reward intentionally refunded in guest mode; no trusted entitlement integration | 3 byte-identical Kotlin wheel PNGs | **Missing** | **Not 100% verified** |
| Settings / profile / dialogs / notifications / admin | Incomplete; native auth and settings parity pending | Partial | **Missing** | **Not 100% verified** |

## October 23 user-supplied Android UI mismatch: revamp vs old V6 preview

A screenshot of the installed React preview was compared with a screenshot of the installed Kotlin app on the user's Android phone. **They are not synchronized game-state screenshots**: the guest React app shows Buddy and zero currency, while Kotlin shows an owned Dev Pup and progressed currencies. This is a genuine *structural* mismatch, but these captures cannot establish pixel metrics or save parity.

| Observed difference | Kotlin production screenshot | Corrective React PR |
| --- | --- | --- |
| Main navigation | Play · Care · Roster · Shop · Rewards | Switch five tabs to the same order; Settings/Prestige are internal |
| Top toolbar | Brand logo/title with bell and settings | Shared header with notification/settings routes; preview identity remains visible |
| Play heading | Play + short tagline | Current page header instead of Buddy/mood brand banner |
| Wallet | Horizontal 4 currency summary | Preserve horizontal wallet; compact big values to avoid uncontrolled wrapping |
| Tap area | Centered art, energy and care bonus, full-width cyan tap button | Replace old rounded circle + inset tap badge and move care needs to Care page |
| Next reward | Goal summary, progress bar, gift icon | Add date-driven next unclaimed goal and reward strip |

**Remaining parity blockers:** React still uses a local guest state with different portraits/entitlements than Kotlin. Settings, notifications, roster and all other screens are still incompletely ported; original animation/spacing and responsive behavior have not been visually verified. The current change corrects major structural discrepancies but **does not certify the complete app UI or a successful APK build**.

## Minimum evidence to mark EACH screen 100%

1. Start the same feature state with the **same actual Kotlin original assets** in both APKs. Use identical Android device, density, system fontScale, theme, locale and orientation. Record the native status/navigation bar and safe-area offsets.
2. Capture at least one normal A-series phone portrait, a smaller/narrower Android phone, landscape where supported, and Android large text. Capture states: idle, selected/disabled, winning/losing, split/multi-hand, dialogs and error/empty, returning from background.
3. Compare the screenshot pairs **at pixel level** after aligning density and safe-area. Inspect bounding boxes for overlays, artwork crop, text baseline/font, vertical gap, clipping, tap target and image resolution. Record and fix each difference, then recapture the same state.
4. Confirm **live interactions and animation**: source staging/timing, sequential reels, hover/press equivalents, interrupted round recovery, TalkBack descriptions, contrast, reduced motion, touch target, and navigation/back behavior. Screenshots alone do not verify interactions.
5. Run complete Android CI and install the generated *preview* APK on a physical device; record Build URL, SHA, artifact, test results, screenshot evidence and remaining mismatches. An Android CI build is necessary but never sufficient to claim 100% visual identity.
6. Treat any **deliberate deviation** (extra preview browser, guest-only login, generated source fallback) as non-parity until an explicit scoped exception is agreed upon. Keep Supabase/Discord/PupEye identity and transaction security blocked until the verified production migration stage.

**Current conclusion:** All six Casino source ports are committed, but **none** of the listed screens has the device screenshot/video evidence required for 100% parity. A Preview APK build at commit `76c118ad` ([run 40068347188](https://github.com/markhitchk/pup-clinker/actions/runs/40068347188)) failed 8/147 tests (139 passed), including a Wheel ReferenceError and outdated source-contract assertions. Subsequent Part 6 commits correct those eight detected causes, but require a fresh passing CI run before the tests or APK can be called verified. The next task is **Part 6: green APK build and side-by-side Android screenshot/video captures**; see [PART_6_DEVICE_QA.md](PART_6_DEVICE_QA.md).

## October 24 correct-branch follow-up

The earlier work on `markhitchk/Puppy-Clicker` was in a separate repository and **did not update this app**. Kotlin UI parity changes now target this repository's existing React preview branch (`fix/react-android-kotlin-revamp-ui-20261023`) and PR #74. The React preview uses Kotlin's 48dp toolbar, 40dp action buttons, 80dp bottom navigation, indicator pills, Android safe-area insets, and available-height breakpoints (590/700dp) for Play/Care. The guest disclaimer remains in Settings rather than changing the Kotlin header height. Kotlin production remains unchanged. Full device screenshot matching, protected asset transparency, authoritative Supabase data and remaining UI screens are still unverified.
