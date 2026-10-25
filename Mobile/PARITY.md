## October 25 — local guest recovery corrections (review branch)

- Fixed a reload mismatch in `Mobile/src/engine/game.js`: Kotlin `PuppyClickerV6ViewModel.loadState` calculates CLICK power from owned upgrades and permanent `TAP_TRAINING`. The React guest sanitizer now reconstructs `clickPower` by that rule, rather than trusting a stale save field. The existing AUTO calculation is retained.
- Removed the 400 ms guest-save debounce in `Mobile/src/state/useGame.js`. The existing serialized AsyncStorage writer now starts after each changed game state. This narrows the crash-loss window but does not make an asynchronous storage operation crash-proof.
- Fixed per-puppy rename persistence with `puppyNames` in `Mobile/src/engine/game.js` and `Mobile/src/state/reducer.js`, including schema-1 local guest save compatibility. Kotlin already saves `puppy_name_for_<styleId>` separately.
- Added `Mobile/tests/guestRestoreParity.test.js` and `Mobile/tests/puppyNamesParity.test.js` for those precise Kotlin-derived contracts. Device runtime testing, CI and every unrelated parity item remain outstanding.

## October 24 — remaining screenshot corrections

- Restored the shipped compact Care view from `PuppyRevampedCareScreen`: active portrait, one five-meter panel, live tired/bonus note, and Feed/Play/Clean above Rest/Cuddle, with existing reducer costs/effects. Daily gifts/goals remain in Rewards; Dog Park access is retained there.
- Restored the compact Roster selection strip, current-name/rename/accessory actions, three-column cards with metadata and favorite/retry affordances, native asset-ID search, and sort choices. The existing unlocked-only guest favorite rule is unchanged. Exchange exposes its existing account-service dependency without simulating transactions.
- All three Settings destructive actions now use the shared construction-tape dialog and an uninterrupted ten-second hold. Release, movement outside, multitouch, responder loss, app inactivity, Back and unmount cancel. Confirm dispatches once; the modal describes actual guest save scope.
- Inbox now uses safe insets, a separate unread badge, title-row timestamps and source-sized card typography. Existing real local rewards remain duplicate-safe; uploaded native historical/AFK grants are not recreated.
- Play, its Next Reward card, primary navigation, Shop and casino screens are preserved. Original role-gated Developer Options still requires verified account migration; the screenshots do not grant developer access. Native encrypted import/export remains unported.
- Verification: rendered component/reducer interactions, cancellation/claim-repeat tests, full Node suite and Expo Android export. Installed-device gesture/layout comparison is still required.

## Correction: follow the shipped Kotlin build, preserve approved screens

The source launcher file is an input to a build-time patch pipeline, not the final UI. `App/tools/seasonal.gradle.kts` applies `patch_roster_navigation.py`, `patch_main_ui_revamp.py`, and final viewport patches. The shipped primary destinations are **Play, Care, Roster, Shop, Rewards**, as defined in `PuppyNavigationModel.kt`. Settings and Prestige are internal destinations; the primary bar is hidden while an internal destination is open. **Do not restore the raw V6 Play/Care/Shop/Prestige/Settings tab list.**

The shipped Play surface is `PuppyRevampedPlayScreen` in `PuppyMainScreensRevamp.kt`: height-sensitive 122/150/185dp portrait, transparent unmasked stage, energy/care-bonus note, tap button, and live Next Reward card. The fixed app bar is 48dp high. The older 365dp stage, 258dp circle, care-pill row and duplicate Puppy Clicker header belong to the replaced V6 screen. Historical audit notes below that call this revamped screen unused are superseded by this correction.

Preserve the screens the user has approved when correcting another feature. The preceding correction restored navigation/Play and source settings density. The later screenshot correction above updates Care and Roster; Shop and casino layouts are retained. The original `LegacyPuppyTransparency.kt` removes edge-connected near-black matte only from opaque legacy V1/V2 PNGs. `scripts/prepare-portraits.cjs` applies the same deterministic algorithm into ignored `assets/rendered/`; source artwork is untouched and true-alpha PNGs are copied without alteration. Run `npm run prepare:portraits` after artwork updates; installation and test/start scripts do this automatically.

Regression coverage now renders the app and clicks Roster, Rewards and Settings; it also verifies Back returns to the prior tab, the Next Reward advances after claims, height breakpoints, and matte cleanup preserving enclosed dark details. These tests do not establish pixel-perfect installed Android parity.

## October 24 — settings and hardware pointer restoration

- Restored source accordion categories with functional local appearance controls: Light/Dark/Follow System, eight source accents/custom HEX, high contrast, Compact/Default/Large scale, reduced motion and performance presets. Existing UI style dictionaries update when appearance changes; source casino scenes retain an explicit dark surface for their fixed artwork/text palette.
- Gameplay haptics/test, compact numbers, puppy motion and the original hardware mouse pointer are persisted. `plugins/withPuppyPointer.cjs` registers an Android native root/modal view with Kotlin's exact 64×64 default cursor, theme choice and (16,16) hotspot; all 24 original pointer PNGs are retained byte-for-byte. This is a real OS cursor, not a touch overlay. Physical mouse behavior still requires device QA.
- Launcher `icon` and `roundIcon` use the same unmodified source drawable as Kotlin instead of a differently scaled adaptive foreground. Header logo, notification bell and settings entry restored with safe-area insets.
- Notifications now provide a persistent filtered inbox, unread state, mark-all/clear-read, duplicate-safe guest reward claims, Android permission/channels, test delivery, date-based daily/park reminders and background-only puppy attention. Disabling categories/deleting guest progress cancels pending local reminders. Original historical/AFK reward notifications and remote update notices are not fabricated.
- Guest profile name/birthday, local diagnostics consent, support sharing, roster metadata refresh/cache clearing and confirmed settings/progress resets are available. Settings/inbox actions do not rewrite care timestamps or award achievements.
- Validation: full Node suite, component interaction tests, notification lifecycle tests, Expo Android export and prebuild. Native compilation/installed phone delivery/hardware pointer and matching Android screenshots remain required. This is still a guest preview, not a complete production Kotlin migration.

# React Native Android parity audit

**Target:** Full Kotlin/Compose Puppy Clicker replacement in React Native, with zero WebViews.
**Source:** `../App/` native Android, package `com.harleytg.puppyclicker`.
**Current output:** `Mobile/` React Native **guest-only preview** (`com.harleytg.puppyclicker.preview`).
**Release verdict:** **NOT READY**. No production merges, upload-signing or account-state migration.

Status vocabulary: **Implemented guest** = screens/logic usable in local preview only. **Partial** = demonstrable but incomplete vs Kotlin. **Blocked** = needs original authoritative server or sensitive release prerequisites.

| Android capability | React Native status | Source / gap |
| --- | --- | --- |
| Real React Native UI, not browser shell | Implemented guest | `Mobile/App.js`; five native React tabs |
| Six onboarding stages | Implemented guest | `PuppyOnboardingModel.kt`; account setup and notification permissions are intentionally simulated/disabled |
| Play, taps, combo, care bonus and base V7 currencies | Partial | `PuppyEconomyV7.kt`; remaining authoritative server/anti-bot parity |
| Per-puppy feeding/play/wash/rest/cuddle, offline decay | Implemented guest | `PuppyCareSystem.kt`; clock capped to 240 min |
| XP, level curve, nine achievement milestones | Implemented guest | `PuppyProgression.kt`, `PuppyAchievementsV6.kt`; guest rewards not official XP |
| Shop, upgrade tickets, Pup Coins, accessories | Partial | Android pricing, rarity tickets and rare items need broader golden-fixture coverage |
| Prestige skill tree, permanent unlocks | Partial | Source levels and reward edge cases need complete fixture coverage |
| V1/V2/Discord portraits | Implemented guest | All 30 source PNGs bundled offline, but locked characters remain unclaimable without authorization |
| Dynamic V3+ streamed art and roster manifests | Partial | `Mobile/src/engine/dynamicRoster.js`, `useRoster.js`: strict data-only GitHub roster discovery, validated manifests, cached offline metadata and category tabs; protected ownership, verified assets/hash and stream telemetry remain |
| Gacha with free-puppy restriction, 2,500 Treats/1 Common Ticket | Implemented guest | `PuppyGacha.kt`; protected/redeem-only skins never randomly granted; source machine animation not 1:1 |
| Slots/Roulette/Scratchers/Plinko/Wheel | Partial | Source-derived outcomes and visual scenes. React Scratchers now uses the original 1426×1077 frame geometry, a drag-to-reveal surface at Kotlin's 68% threshold, deferred once-only guest payout and saved pending-round recovery. True 48×20 native coin physics, exact scene animation, and server-authoritative transactions remain incomplete |
| Blackjack 52-card deck, natural 3:2, dealer stand 17, split/double | Implemented guest | `PuppyBlackjackEngine.kt`; up to 3 hands, no native animation/art yet |
| Blackjack crash-safe guest wager state | Implemented guest | One persisted guest `casinoRound` with deck uniqueness validation; not server-side settlement |
| Daily reward and park activity | Partial | Local daily gift, park, validated monthly schedules, four metric-based fallback goals, per-day counters and duplicate-safe claims now work for **guest** state; remote authoritative settlement and broader event rewards missing |
| Owned roster/favorites/search | Partial | Local V1/V2; source categorization, dynamic groups, reorder, role permissions remain |
| Player profile, username, settings | Partial | Appearance, scale, contrast, motion, haptics, hardware pointer, guest name/birthday, privacy choices, local support share, resets and roster cache controls implemented; protected account/developer actions remain |
| Discord OAuth and linked original Pup Account | Blocked | `DiscordSignupAuth.kt`, production callback flow and device identification; cannot impersonate original account |
| Supabase authoritative cloud saves, one device | Blocked | `PupAccountCloudSave.kt`, revision reconciliation and original data mapping; **never upload guest state** |
| PupEye device binding, bans, alerts, appeals and audit | Blocked | `SupabasePupEyeClient.kt` and server authority; anti-cheat must be authoritative |
| Puppy Codes, code-backed rewards and redemption history | Blocked | `PuppyCodeRewards.kt`; ownership and anti-abuse server validation necessary |
| Player-to-player Exchange/trades/gifts | Blocked | `PuppyExchange*.kt`; server ownership ledger and atomic escrow required |
| Native notification permission, inbox, channels | Partial guest | Local Android permission/channels/test/reminders and persistent categorized inbox implemented; no original Kotlin history, trusted AFK grants or remote update delivery |
| Android home widget, background scheduler, haptics | Not implemented | `PuppyHomeWidgetProvider.kt`, haptics and platform UI |
| Source PNGs, 1:1 casino artwork, adaptive layouts | Partial | 22 original Kotlin casino PNGs copied **byte-for-byte** into Mobile assets, plus six React image-backed game scenes; screenshots, true card coordinates and scene animations still unverified |
| Old Kotlin install migration and compatibility | Blocked | Encrypted/attested Android save and official signing identity; do not uninstall original |
| Google Play signed AAB on release track | Blocked | Upload signing key, `versionCode > 34`, target API 36, Play internal testing, crash-free release |

## Release gates — all must pass before original package cutover

1. **Signed identity:** build `com.harleytg.puppyclicker` from the original verified Play upload key; verify certificates before installation. No unsigned/debug update.
2. **Data migration:** test fresh install, in-place upgrade, existing player restore and replay/rollback across the original Kotlin encrypted save and authoritative Supabase account. Detect partial migrations without silent data loss.
3. **Trust/authorization:** server-confirmed Discord account, same device/PupEye identity, role-gated rewards and bans enforced before the player can access trusted progression.
4. **Feature parity:** original game screens, economics, all six casino games, reward/roster, exchange, code/redemption, onboarding, settings, notifications and widget flows function under Kotlin-driven golden tests.
5. **Visual parity:** screenshots on an Android 16 phone, a narrow handset, landscape and tablet; no blank screens, missing images or clipped controls. Accessibility/large text included.
6. **Play rollout:** build and inspect release AAB, test through Play internal track with crash/ANR telemetry, confirm install and rollback safety.

## Next executable work

- P0: Map Supabase/PupEye production schema and design a migration endpoint; no client-authoritative reward uploads.
- P0: Recovery-safe conversion of the original signed Android save, preserving installed players.
- P1: Port remaining original screen UI and reward/roster/casino animations against golden screenshots.
- P1: Production-grade server transaction ledger for casino/Gacha/code/Exchange, duplicate-free payouts.
- P1: Notification channel, home-widget, haptic and accessibility parity.
- P2: Full Play signing and internal QA gate; only then target production release.


## Kotlin-first visual migration status (October 23, 2026)

The previous React preview used a *different* two-column wallet, stacked puppy cards and a permanent developer-style header. Those were not faithful to the shipped Kotlin V6 layout. The following React UI now uses source-derived components from `PuppyClickerV6Activity.kt`, `PuppySettingsUi.kt` and `PuppyRosterScreen.kt`. **This is visual alignment, not yet a verified pixel-perfect 1:1 release.**

| Kotlin screen | React Native work now committed | Still required before 100% parity |
| --- | --- | --- |
| V6 Play | Original five tabs, page title, 4-currency horizontal wallet, 4 care pills, central tappable puppy portrait with tap-reward overlay; portrait scales for shorter screens | Compare Android 16 screenshots, exact motion, real cooldown overlay, ticket-drop animation, theme/contrast settings |
| V6 Care & Daily | Care/Daily chips, per-pup portrait and care meters, 3+2 care actions, daily gift, park, month-scoped data-only goal schedule and one-time **guest** claims | Server authority, monthly event reward settlement and Android notification actions |
| V6 Shop | Three category chips, separate ticket inventory, source pricing/purchases, Pup Coin ticket/accessory store | Real redeem codes, remote tickets/entitlements, complete native shop art and special purchase flows |
| V6 Prestige | Progress toward 50,000 treats, skill-point center, four permanent skills and confirmation | Match all prestige-state reset and migration rules with Kotlin golden fixtures |
| V6 Settings | Native expandable categories, local haptics/motion/compact-wallet toggles persisted in guest save, and source settings order | Real account actions, full theme presets, developer controls, notifications, support and cloud permissions |
| V6 Roster | All/Unlocked/Locked, search, sort, favorites, dynamic category chips, inspected puppy card and source-backed V3+ remote portrait display | Remote accounts/entitlements, every sort and inspect/rename/use dialog, image hash verification, exact grid sizing |
| Casino / Gacha / Rewards | Six navigable React casino scenes use 22 exact source PNGs, existing guest wager engines and Blackjack round persistence; Rewards displays Kotlin-like streaks, live/cache/fallback monthly goals, claim ledger and achievement XP | **Major gaps**: exact scene animation/layout/interaction fidelity, protected rewards, wheel puppy unlock authorization, gacha capsule animation and trusted settlement |
| Account / PupEye / Cloud / Native OS | No trusted migration performed | **Blocking** Discord OAuth, authoritative signed device saves and cloud recovery, one-device enforcement, PupEye, notification channels/widget, official AAB signing |

### 100% completion definition

For *each* Kotlin user-facing screen, identify the exact Compose function and app state, implement matching React Native output and interactions, compare screenshots from the **same original Kotlin build** and React preview at identical viewport, font scale, theme, asset and state. Track spacing, typography, colors, corner radii, artwork sizes, gestures, overlays, orientation, keyboard, TalkBack and screen-reader behavior. Include state-transition and save-compatibility tests; a passing Metro export alone is not visual fidelity.

The original Kotlin `App/` is **read-only authoritative reference** during the migration. Do not delete it, switch the original Play package to an unverified React build, migrate customer saves through the local guest cache, bypass PupEye, or represent the preview as fully identical. Use `Mobile/tests/kotlin-visual-contract.test.js` for static source-alignment checks and require Expo export/Gradle build and manual screenshot regression before declaring any screen pixel-perfect.

### Latest Kotlin-source asset and rewards invariants

- `Mobile/tests/casinoArtwork.test.js` checks **all 22** React casino PNGs are byte-identical to corresponding `App/app/src/main/res/drawable/` source artwork. No synthetic placeholder or externally sourced casino art was introduced.
- `Mobile/src/engine/monthlyRewards.js` validates `schemaVersion=1`, exact local month, goal ID uniqueness, supported metric, target/reward bounds, day limits and 512 KiB maximum; unavailable or invalid remote schedules revert to deterministic Kotlin fallback goals. `Mobile/tests/monthlyRewards.test.js` verifies duplicate claim rejection, day rollover, and local guest payouts.
- `Mobile/src/state/useGame.js` loads cached monthly goals from AsyncStorage, refreshes at 15-minute intervals and rolls daily counters on local date changes or app resume. Missing `YYYY-MM.json` months are handled by fallback, not a broken Rewards screen.
- Kotlin's `hapticsEnabled`, `animationsEnabled` and `compactNumbers` are now represented in the React guest preferences; tap haptics use Android's built-in React Native `Vibration` API (not yet gesture-perfect compared with Compose `performV6Haptic`).
- **Full 100% parity remains not verified**. Completing GUI sketches or passing CI does not establish account, server, casino, notification, Play identity or game-state parity. Keep `App/` authoritative and production intact.

## Kotlin DynamicPuppyRoster read-only port

The migration preview now loads the same `assets` folder index and v2/test/discord_rewards/v3+ manifests the Kotlin app uses, validates path/ID/size constraints, and caches trusted data separately from guest progression. **This discovery grants no puppy ownership**: newly streamed images appear locked unless the authoritative user account separately verifies an entitlement. `Mobile/tests/dynamicRoster.test.js` checks known Kotlin manifests and malicious path cases. The original cloud-save and account-grant endpoints are not called by this feature.

### Blackjack Kotlin scene geometry

The React preview now uses the **exact** `PuppyBlackjackLayout.kt` bitmap dimensions (1122×1402), alpha-transparent crop (86/1188) and dealer/player card zones, with Kotlin-style bounded fan spacing. `Mobile/tests/blackjackLayout.test.js` verifies numerical layout equations against the Kotlin source. Android screenshot validation, card flip animations, split-hand inspection and signed casino settlement remain to be completed.

## October 23 branch correction — Android source of truth

- React **Android** development takes place in this repository on `feat/react-native-full-android` under `Mobile/`. The separately hosted `Puppy-Clicker` React web repository is not the Android branch, and its PRs do not update this Android app.
- Scratchers parity fix implemented locally in `Mobile/src/engine/scratch.js`, `Mobile/src/components/ScratchStage.jsx`, the React casino screen, and guest reducer/save validation; tests in `Mobile/tests/scratch.test.js`.
- The wager is debited when a guest round starts but the outcome is settled **only once** upon 68% scratch coverage, not immediately on card purchase. Pending rounds block other wagers and survive local guest-save rehydration; invalid pending records are discarded. Guest balance has no trusted server standing.
- The view-tile scratch mask is deliberately not claimed to match Kotlin's 48×20 native Canvas/coin-motion implementation pixel for pixel. All other missing systems, including Discord/PupEye, Exchange, push notifications, full source animations and signing, remain migration blockers. Keep Kotlin `App/` unchanged and do not publish an unverified React replacement.

## October 24 — Kotlin attention notification behavior

- React local Android attention reminders now follow the Kotlin two-hour recurring AFK cadence, cancel when active, and use the selected puppy's known care status for the alert text.
- Notification onboarding enables the attention category when the user opts in; Settings still allows turning it off.
- Android schedule + in-app reminders are **not** equivalent to the Kotlin authenticated developer/system inbox. Server-authenticated announcements, cloud-synced read state, existing native notification history, and account/device/PupEye integration are still blocked; no live production release is implied.
