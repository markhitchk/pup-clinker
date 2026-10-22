# Casino UI audit and Blackjack correction

## Findings and root causes

The original screenshot combines a tall table with a second illustrated control panel. The table, wallet and panel each allocated height from their source-image aspect ratio. Transparent margins and baked panel details compounded the space. Image buttons were stretched with `FillBounds`. Dealer cards started at 7% from the left and 16% from the top, above the felt. Player split hands were stacked into an unmeasured column inside a fixed-size image, so additional hands could escape the table. Hand totals and wagers appeared both on the table and on the panel. Rules were always expanded.

## Implementation

- Preserve the original table, mascot, wood, chips, deck and both playing-card images. No artwork files changed.
- Separate stateless Compose presentation from persisted round commands. Scoring, settlement, rewards, feature gates and account persistence are unchanged.
- Measure the artwork and both felt regions from one aspect-preserving table geometry. Exclude near-transparent source padding (86px at the top and 128px at the bottom) from layout while drawing the original bitmap at its unchanged aspect ratio. Center both card fans inside these regions. Share card sizes between dealer and player; overlap crowded hands without moving the table or controls.
- Use a focused split-hand view with accessible hand selectors; automatically follow the engine's active hand and restore it before dispatching an action from inspection mode. Keep all hand totals, wagers and statuses together below the table, without repeating them in the buttons.
- Use blue paw HIT, green STAND, gold 2× DOUBLE and purple SPLIT native buttons. Engine eligibility and available chips govern enabled states. Completed rounds have no action panel. Minimum button height is 48dp; text can wrap.
- Keep one compact chip balance. Collapse rules and help. Bound the artwork using the available viewport height; use a two-column arrangement in wide landscape windows. Allow scrolling when large type or a very short window needs it.
- Keep deal/reveal, chip movement, turn color changes, result feedback and press animations short, finite and independent of game commands. Respect the app animation preference and Android's disabled animator setting.
- Consume shell-provided insets before nested safe-drawing padding, preserving cutout/navigation safety without duplicate status-bar padding.
- Prevent a prior round's outcome from labeling a newly accepted hand.

## Other game audit

| Game | Findings and correction |
| --- | --- |
| Slots | Phone artwork could expand with tablet content width. Apply a shared centered 560dp content limit and collapsible rules; preserve reel geometry, lever and persisted outcomes. |
| Roulette | Bound content width, collapse rules and give native number selections a 48dp minimum height. Preserve wheel, table and number-picker behavior. |
| Plinko | Existing board already caps its width at 460dp and maps the ball/bins together. Bound the surrounding screen and collapse help. |
| Scratchers | Preserve scratch-border aspect ratio, coating coordinates and token interaction. Bound the surrounding screen and collapse odds/help. |
| Lucky Wheel | Preserve wheel proportions, counter-rotated labels and saved prize selection. Bound surrounding content and collapse odds/help. |

The shared component controls chrome, width and safe scrolling only. Each board retains its own layout.

## Verification

Commands (Java 17, Gradle 8.13, Android SDK 36):

```sh
cd App
gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
python3 -m unittest tools.test_patch_puppy_exchange tools.test_patch_android_1_0_runner tools.test_png_integrity
```

`PuppyBlackjackLayoutTest` checks 240–600dp geometry, fans of 2–12 cards, matching card sizes and action eligibility/funding. Existing engine and transaction tests cover initial deals, hits, stand, split, doubles, naturals, payouts, serialization and settlement. Legacy source assertions that required the defective image-button layout were replaced by Compose behavior checks.

`PuppyBlackjackResponsiveTest` uses Robolectric Android 14 native graphics to render the actual production composables using deterministic engine fixtures **only in tests**. It captures initial deal, hit, stand, double, two/three split hands, ten-card hands, bust and natural Blackjack at small/large phone, tablet and landscape sizes, plus 1.8× font scaling. It also checks collapsed help, disabled controls, click dispatch and split-hand selection/turn following.

The headless native-graphics runner draws the real Android Compose view into a Canvas. An Android 11 software emulator repeatedly lost system services during package installation, so its instrumentation run is not counted as passing. Screenshots are game-content captures; the unchanged application header is owned by the surrounding app shell.

## Modified files

Production: `PuppyBlackjackUi.kt`, new `PuppyBlackjackLayout.kt` and `PuppyCasinoLayout.kt`; `PuppySlotsUi.kt`, `PuppyRouletteUi.kt`, `PuppyPlinkoUi.kt`, `PuppyScratchersUi.kt`, `PuppyLuckyWheelUi.kt`; `PuppyClickerV6Activity.kt` and its build-time shell generator `App/tools/patch_roster_navigation.py`.

Build configuration: `App/app/build.gradle.kts` enables Android resources and adds test-only Robolectric / Compose UI test dependencies; no production dependencies added.

Tests: new `PuppyBlackjackLayoutTest.kt` and `PuppyBlackjackResponsiveTest.kt`; updated `PuppyBlackjackIntegrationContractTest.kt`, `PuppyCasinoAnimationContractTest.kt`, `PuppyCasinoScreenshotUiContractTest.kt` and `PuppyCompactFitmentContractTest.kt`.

## Limits

No physical Samsung Galaxy A-series device, Android 16 cutout device, TalkBack session, live Supabase account or battery/performance profiler is attached here. Those integration/hardware checks cannot be inferred from geometry or engine tests. Very short windows and large accessibility text deliberately retain scrolling rather than reducing native control/type size.

## Recorded results — October 22, 2026

- **485 JVM / Robolectric tests passed, 0 failures.** Includes four Compose behavior/render tests and three card-layout/control tests.
- **12 Python regression tests passed.**
- **Debug APK, Android test APK and release APK assembled successfully.** Release vital lint passed. Release output is unsigned; no store release was submitted.
- **14 production-Compose screenshots generated**, with phone, split-hand, crowded-hand, landscape and large-text captures visually reviewed. Fixtures exist only in the test source set.
- Independent read-only review found and verified corrections for split-hand inspection/action targeting and large-type rules labels. Existing Kotlin condition/deprecation warnings remain.
- No engine, transaction, account, Supabase schema, signing or production version changes.

| Capture | Size in dp | Coverage |
| --- | --- | --- |
| [Small phone](screenshots/blackjack-phone-small.png) | 320 × 640 | Initial deal and native controls |
| [A-series-sized viewport](screenshots/blackjack-phone-a-series.png) | 360 × 740 | Initial deal; geometry target, not a physical Samsung test |
| [Large phone](screenshots/blackjack-phone-large.png) | 430 × 900 | Table width cap and centered controls |
| [Landscape](screenshots/blackjack-landscape.png) | 800 × 480 | Two-column board and controls |
| [Tablet](screenshots/blackjack-tablet.png) | 800 × 1000 | Centered content and bounded artwork |
| [Two split hands](screenshots/blackjack-split-two.png) | 360 × 740 | Hand selector, totals and active turn |
| [Three split hands](screenshots/blackjack-split-three.png) | 430 × 900 | Maximum hand count and SPLIT disabled |
| [Ten-card hand](screenshots/blackjack-ten-card-hand.png) | 360 × 740 | Controlled overlap within the felt |
| [Large text](screenshots/blackjack-large-text.png) | 360 × 740, 1.8× | Native text wrapping and scroll fallback |
| [Hit](screenshots/blackjack-hit.png) | 360 × 740 | Additional card and action eligibility |
| [Stand](screenshots/blackjack-stand.png) | 360 × 740 | Dealer reveal and completed controls removed |
| [Double](screenshots/blackjack-double.png) | 360 × 740 | Doubled wager and final card |
| [Natural Blackjack](screenshots/blackjack-blackjack.png) | 360 × 740 | Result and engine-calculated return |
| [Bust](screenshots/blackjack-bust.png) | 360 × 740 | Bust feedback and completion |

Compared with the supplied original, dealer cards now sit inside the felt rather than on top of the left chip stack; the lower hand stays within the gold border; the separate illustrated button panel and repeated hand/bet line are gone; and controls follow the table/status without the original transparent-artwork gap. The full mascot, wood trim, chips, deck and paw-card artwork remain.

Other games received a source/asset geometry audit and shared layout corrections. Their existing engine/transaction regressions passed; full six-game device interaction and performance profiling remain unverified.
