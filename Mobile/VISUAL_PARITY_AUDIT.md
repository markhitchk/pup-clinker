# Puppy Clicker Kotlin → React Android Visual Parity Audit

**Audit state:** NOT CERTIFIED. A screen being source-ported or unit-tested does NOT imply 100% screenshot or runtime parity.

**Production reference:** `App/` Kotlin Android, actual launcher functions and UI test captures.
**React target:** `Mobile/` on `feat/react-native-full-android`, installed side-by-side as `com.harleytg.puppyclicker.preview`.
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
| Plinko | Rough React preview; Kotlin peg/ball travel pending | Some original art | **Missing** | **Not 100% verified** |
| Scratchers | Guest scratch gesture; Kotlin mask/coin physics pending | Original frame | **Missing** | **Not 100% verified** |
| Lucky Pup Wheel | Rough React preview; Kotlin wheel/pointer sequence pending | Some original art | **Missing** | **Not 100% verified** |
| Settings / profile / dialogs / notifications / admin | Incomplete; native auth and settings parity pending | Partial | **Missing** | **Not 100% verified** |

## Minimum evidence to mark EACH screen 100%

1. Start the same feature state with the **same actual Kotlin original assets** in both APKs. Use identical Android device, density, system fontScale, theme, locale and orientation. Record the native status/navigation bar and safe-area offsets.
2. Capture at least one normal A-series phone portrait, a smaller/narrower Android phone, landscape where supported, and Android large text. Capture states: idle, selected/disabled, winning/losing, split/multi-hand, dialogs and error/empty, returning from background.
3. Compare the screenshot pairs **at pixel level** after aligning density and safe-area. Inspect bounding boxes for overlays, artwork crop, text baseline/font, vertical gap, clipping, tap target and image resolution. Record and fix each difference, then recapture the same state.
4. Confirm **live interactions and animation**: source staging/timing, sequential reels, hover/press equivalents, interrupted round recovery, TalkBack descriptions, contrast, reduced motion, touch target, and navigation/back behavior. Screenshots alone do not verify interactions.
5. Run complete Android CI and install the generated *preview* APK on a physical device; record Build URL, SHA, artifact, test results, screenshot evidence and remaining mismatches. An Android CI build is necessary but never sufficient to claim 100% visual identity.
6. Treat any **deliberate deviation** (extra preview browser, guest-only login, generated source fallback) as non-parity until an explicit scoped exception is agreed upon. Keep Supabase/Discord/PupEye identity and transaction security blocked until the verified production migration stage.

**Current conclusion:** None of these screens has the evidence required for a truthful 100% visual-parity claim. Source ports and deterministic regression tests are progress, not a pixel-certified release. The next source feature is **5C Roulette**.
