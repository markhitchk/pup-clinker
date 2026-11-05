# Website → Android port matrix — incremental parts

**Visual source of truth:** [Puppy-Clicker](https://github.com/markhitchk/Puppy-Clicker) `main`; **target:** [pup-clinker/Mobile](https://github.com/markhitchk/pup-clinker/tree/main/Mobile). The website's appearance and deployment are not changed by this work.

## Status meanings
- **Shared:** website-owned React Native-compatible component, generated downstream with a pinned provenance hash and offline drift test; deterministic asset substitutions are permitted and recorded.
- **Partial:** native implementation exists but a side-by-side, screen-by-screen 1:1 review or true shared-source extraction is outstanding.
- **Native:** kept in Android by design, with browser UI only adapted through a narrow interface.
- **Deferred:** not yet proven equivalent; do not claim completion.

## Inclusion matrix

| Part | Website source / route | Android entry / ownership | Status | Next acceptance requirement |
| --- | --- | --- | --- | --- |
| 1 | `src/native/src/components/AccountDetailsScreen.jsx` | `Mobile/src/components/AccountDetailsScreen.jsx` | **Shared** | Website-owned upstream, pinned SHA, automated source sync and offline hash check |
| 1 | `src/native/src/components/NotificationInbox.jsx` | `Mobile/src/components/NotificationInbox.jsx` | **Shared with asset adapter** | Identical website component body; generated local Image require for logo |
| 1 | `src/native/src/production/AccountOnboarding.jsx` | `Mobile/src/production/AccountOnboarding.jsx` | **Shared with asset adapter** | Identical website component body; local logo/Discord images only |
| 1 | `src/native/web/kawaii.css` | `websiteDesignTokens.js` and `websiteTheme.js` | **Partial** | Tokens synced; typography, radii, spacing, interactions and animation parity remain |
| 2 | `src/native/src/engine/playRevamp.js` five-tab source and `web/DesktopNavigation.jsx` | `Mobile/src/production/websiteNavigation.js`, `Mobile/App.js`, desktop shell | **Shared tabs; partial screen UI** | Tab order/icons/labels are generated from website source; actual sidebar layout and routes still require real-device QA |
| 2 | Website `/play` PlayScreen + desktop Play | `WebsitePlayScreen.jsx`, `WebsiteDesktopDashboard.jsx` | **Partial — updated** | Native night-yard, headings, identity, controls and responsive CSS-grid proportions are closer to website; full source-sharing and screenshot/device parity still outstanding |
| 3 | `src/native/App.jsx` CareScreen, ShopScreen, RewardsGroup, DailyGoalCard and RewardsScreen | `WebsiteCareShopScreens.jsx`, `WebsiteRewardsScreens.jsx`, `Mobile/App.js` native adapters | **Shared JSX with native adapters** | Generated from canonical website source, no handwritten Android screen duplicates; native date/security, translations and real-device visual QA remain |
| 4 | Website `src/native/App.jsx` Roster + Prestige; `PuppyDetailsDialog.jsx`, existing Pup Account details | `WebsiteRosterPrestigeScreens.jsx`, `PuppyDetailsDialog.jsx`, `AccountDetailsScreen.jsx` | **Website-owned RN JSX; Redeem deferred** | Generated native screens and puppy dialog from pinned website SHA; code redemption requires signed entitlement endpoint; validate visual/interaction parity on devices |
| 4 | `src/InventoryScreen.jsx` and account inventory | `websiteInventoryContract.js`, `inventoryModel.js`, `WebsiteInventoryScreen.jsx` | **Shared category/data contract; native presentation partial** | Web DOM and Android RN layout remain separate; source-owned tabs, item icons/currencies, read-only inventory and Pup Account navigation; screenshot parity pending |
| 5 | `web/AccountGate.jsx`, `WebRegister.jsx`, `JoinSourceFields.jsx` | `BetaAccountEntry.jsx`, `websiteAccountContract.js`, native PupEye APIs | **Shared copy/validation/referral contract; native authentication** | Website titles, password rules, referral options and native benefits; browser markup cannot run on Android, signed login and hardware Keystore remain native; device visual QA pending |
| 5 | `WebSettings.jsx`, `communityI18n.js`, notification inbox, account details and onboarding | `websiteSettingsContract.js`, `websiteLanguage.js`, `SettingsScreen.jsx`, generated native inbox/details/onboarding | **Shared settings headings and translated strings; native presentation/notification permissions** | Website section titles, cloud refresh and persisted English/Spanish setting; existing source-shared inbox and onboarding; browser-only Settings interactions and native sign-out still need parity work |
| 6 | Website `src/native/App.jsx` Casino and Gacha, `web/WebsiteDesign.jsx` consent | `WebsiteCasinoGachaScreens.jsx`, native Casino/Gacha game components and signed `useGame.js` | **Website-owned RN screen JSX with native consent adapter** | Six casino subgames and Gacha use the website routing/animations/disclosures; payout, signed receipts, desktop sizing and APK interaction checks still require on-device QA |
| 7 | Website `src/pupeyeStatusPresentation.js`, `/pupeye` and `/pupeye/status` | `websitePupEyePresentation.js`, Android Settings PupEye panel | **Shared status copy; server-only standing** | Account standing is from native verified session; fair-play clearance is never inferred without server status; official status-page links work only with signed website sessions |
| 7 | Signed cloud save and Supabase migration test fixtures | `useGame.js`, `supabase/migrations/20261030074000_pupeye_retired_identity_guard.sql`, GH Actions | **Cross-platform contract checks; CI pending** | Signed gameplay state and queued actions remain server-owned; retired identity tombstones guarded on actual physical table; integration and device tests needed before release |
| 9 | Web public/admin infrastructure and browser-only routes | **Not ported** | **Excluded** | Never bundle Vercel API, web admin, DOM, web routing, browser storage or guest/demo paths |

## Source-sharing mechanism (Part 1)
- `Mobile/scripts/sync-website-native-ui.mjs` synchronizes **only** reviewed React Native-compatible UI modules from a pinned website commit. Website source is authored once; Android copies are **generated downstream**, never edited independently.
- `Mobile/src/production/websiteUiSources.json` records canonical GitHub paths and Git blob checksums. `--verify` is offline and fails on local drift.
- The existing color-token sync stays separate; the scheduled workflow proposes an Android-only PR, runs tests, and does **not** silently deploy or update any installed APK.
- Before adding a module to the allowlist, review all imports and ensure the website code contains no DOM, browser Supabase session, privileged API calls, guest access, or gameplay mutation. Extract shared presentation and inject callbacks for Android-specific behavior.
- Existing `PupGameChrome.jsx` is an Android-only native translation; it is **not** yet website-owned. Do not treat it as 1:1 source sharing.

## Preserved native and server boundaries
Android remains authoritative for secure identity, PupEye/device security, signed gameplay/save mutations, push/local notifications, private persisted storage, deep links, and Android back/system UI. Cloud save and RLS remain enforced on the server. No browser-only session, admin credential, elevated key, or local guest balance may replace these paths. Keep the production application/package and its data unchanged until an authenticated beta migration has passed device QA.

## Required release gates before merge / Play Store replacement
1. Every included screen has a single website-owned UI source with adapter contracts or a documented unavoidable platform exception; neither handwritten duplicate UI nor the color sync alone qualifies.
2. Snapshot/behavioral checks for small phones, 960dp+ landscape/DeX, resizing, keyboard/mouse, safe areas, motion reduction and accessibility.
3. Signed native auth, startup, PupEye restrictions, save revision conflicts, rewards, shop, notifications, inventory and the Casino/Gacha slots validated.
4. Node tests, Metro production bundle, Android native compile, ARM64 APK, and physical phone/Chromebook/DeX smoke tests pass; no guest/demo route is reachable.
5. PR approvals and all required CI checks are green. An APK artifact in Actions is **not** a verified device release.

## Part-by-part decisions
- Favor safe incremental native React UI extraction over directly executing website DOM/CSS inside Android.
- Website visual/content authority is unchanged; shared modules are upstream-owned and mirrored only by automated provenance-checked generation.
- Do not merge PR #102 or release beta solely because the JavaScript build succeeds; the current Supabase integration fixture failure and real-device verification still require attention.

## Part 2 changes committed in PR #102

1. Generated `websiteNavigation.js` directly from the website `MAIN_TABS` declaration with pinned upstream and downstream SHA checks. The app uses this module for both desktop and phone navigation.
2. Matched website CSS desktop breakpoint, sidebar clamp and the `1.366fr : 1fr` Play/right-rail ratio; responsive to width changes and Android freeform windows.
3. Reworked the desktop Play stage around the website's published night-yard scene URL, with an offline dark fallback; adjusted player identity, background, mobile stage scaling, XP/streak distribution and minimum available button visibility.
4. Added regression checks for navigation ownership, layout thresholds, stage structure and compilation. **CI and physical-device verification are separate release gates.**

## Part 3 — shared Care, Shop and Rewards (PR #102)

- **Care and Shop:** functions extracted verbatim from the reviewed `Puppy-Clicker/src/native/App.jsx` into the generated `Mobile/src/components/WebsiteCareShopScreens.jsx`. `Mobile/App.js` imports a screen factory with existing RN styles, portraits, pricing helpers and signed gameplay callbacks, not a separately written copy.
- **Rewards:** extracted website-native `DailyGoalCard`, `RewardsGroup`, `RewardsScreen` into generated `WebsiteRewardsScreens.jsx`. The website-only *compact HTML goal renderer* is omitted, while its React Native branch remains identical. The website's expandable Overview, Daily Rewards, Claimed Today, Adventures, Progression and Achievements sections are preserved.
- **Goal state:** generated `websiteGoalState.js` mirrors the website `src/native/web/webGoalState.js`; only its engine import paths change. Incomplete goals navigate to Play/Care/Shop; eligible goals show Claim Reward. Maxed Shop Visit goals use website semantics, but server authority may still reject claims. Pending tap intents are displayed separately as awaiting cloud confirmation; they never grant a reward before the signed backend acknowledges them.
- **English/Spanish Rewards strings:** website `src/communityI18n.js` is copied byte-for-byte to `websiteI18n.js`; Android uses device locale while the native language selector remains a future UI task.
- **Native calendar exception:** website uses a UTC server-day adapter and Android retains its existing local-day signed progression until its cloud protocol can be migrated safely. The native adapter explicitly supplies the local-day date/epoch functions to the website-owned Rewards component. This may differ around midnight/time-zone boundaries; validate with server-owned test accounts.
- **Security:** no browser auth, Vercel APIs, guest save state or Supabase secrets are copied. Existing Android `act` callbacks remain the only mutation boundary.
- **Verification:** the source-sync script pins all generated files with source/target Git blob SHAs, runs offline drift checks, and checks the native JSX structure. The GitHub Action proposes updates rather than auto-merging/releasing. CI and physical devices must still pass before claiming pixel-exact parity.

## Part 4 — Roster, Profile, Prestige, Redeem and Inventory (PR #102)

- **Roster + Prestige:** their complete React Native-compatible screen JSX bodies now come from `Puppy-Clicker/src/native/App.jsx` through `WebsiteRosterPrestigeScreens.jsx`. The Android `App.js` no longer has duplicate screen function implementations; layout, controls, V1/V2 collections, sort, filters, favorites, current-puppy hero, awards and prestige confirmation are website-owned. Android still injects native game action callbacks.
- **Puppy profile:** `PuppyDetailsDialog.jsx` now mirrors the website source with one reviewed import adapter for cosmetic `suggestPuppyName`, including its original-name restore, artwork retry, favorite, unlock instructions, change-active and rename-confirmation flows. `AccountDetailsScreen.jsx` already shares website-owned source from Part 1.
- **Random puppy names:** a generated lightweight `websiteNameSuggestions.js` uses the *website's own puppy name list*. On Android, name ideas use non-cryptographic randomness solely for display; account passwords, IDs, friend codes and game outcomes cannot use this helper. Renaming remains a signed gameplay action.
- **Inventory:** its five tabs, wallet types, rarities and accessory icons now derive from generated `websiteInventoryContract.js`, extracted from the website's existing `InventoryScreen.jsx`. Native `inventoryModel.js` references those values and remains a read-only projection of verified Pup Account data; UI adds a functional link to Pup Account details. The website's browser DOM and its native counterpart are **not** pixel-identical code and need visual QA.
- **Redeem:** the canonical website Shop currently shows a signed-entitlement-service-required alert rather than a functioning redemption path. Android mirrors that limitation; **no fabricated or local-only code grants** have been added. Real redemption is still blocked on a verified signed server entitlement API.
- **Sync and tests:** the scheduled website-to-Android workflow includes the new generated sources, SHA pinning, JSX checks and drift detection. Legacy Kotlin parity tests now inspect website-owned files. Phone/Chromebook/DeX device testing, actual entitlement redemption, and Supabase integration CI remain required before merge or release.

## Part 5 — Pup Account, Settings, notifications and language

- **Canonical website sign-in/registration contract:** `websiteAccountContract.js` is deterministically generated from `Puppy-Clicker/src/WebRegister.jsx`, `src/native/web/AccountGate.jsx` and `src/JoinSourceFields.jsx` with pinned Git blob SHAs. It contains the website's current username/password policy, referral choices, exact welcome headings and benefits. The existing `usePupAuthForm.js` consumes the website-derived password rules and join-source choices rather than separately maintaining them.
- **Native login/registration presentation:** `BetaAccountEntry.jsx` displays the website's Pup Account introduction, full benefits and sign-in/create-account tabs with accessibility state and Android password-manager autofill hints. Legal acceptance, optional support email, signed Supabase/Discord/PupEye credentials and Android callback validation still run **exclusively** through the existing native module. Browser `<form>`/`<div>` are not embedded or represented as fully shared executable JSX.
- **Website Settings:** `websiteSettingsContract.js` extracts the website `WebSettings.jsx` section headings and explanations. Android uses those labels where shared, provides a real verified-cloud refresh action, and keeps the Android-only permissions, notifications and diagnostics panels.
- **Language:** `websiteLanguage.js` uses the previously generated website English/Spanish `websiteI18n.js` catalogue. A Settings selector stores `LANGUAGE_KEY` locally in AsyncStorage; Rewards screens re-render when selected language changes. Neither selecting language nor the local key alters game currency, profile, cloud revisions or signed actions.
- **Existing website-owned native components remain unchanged:** Account Details, `AccountOnboarding.jsx` and `NotificationInbox.jsx` were already generated from the website in Part 1. Inbox preserves pending-AFK and claim-navigation logic while Android retains real notification permission prompts.
- **Honest exceptions:** the website's browser login/Settings DOM is not 1:1 source code on Android, and there is no verified native sign-out or server-side session-revocation API exposed by the beta bridge; do not implement fake sign-out. Full screenshot parity, real Android password-manager autofill and physical notification delivery still require device tests.

## Part 6 — Casino and Gacha source parity

- **Website-owned complete screen bodies:** `WebsiteCasinoGachaScreens.jsx` is generated from actual website `src/native/App.jsx` `GachaScreen` and `CasinoScreen`. It reuses the same six Casino entrypoints (Slots, Roulette, Blackjack, Plinko, Scratchers, Lucky Wheel), saved-round resumptions, resource balances, and Gacha's machine phase animations, reveal card, eligibility and cost copy. The Android app no longer maintains duplicated Casino/Gacha screen functions.
- **Consent:** the website's `src/native/web/WebsiteDesign.jsx` CasinoIntroDialog uses DOM `<dialog>`, so Android renders a proper native Modal with its exact website-owned simulated-gambling notice and equivalent two-button consent. Website disclosure updates are read by the sync script; unexpected changes fail code generation.
- **Signed Gacha outcome:** removed the Android UI's former locally generated `Math.random()` roll argument. The website's `act({type:'gacha',payment})` now reaches the native signed PupEye action queue. Only a confirmed server return can alter the cloud save, unlock puppies or display the `betaLastGachaResult`. Non-tap protected actions mark `gameplayBusy` while queued; Gacha purchase buttons stay disabled until confirmation.
- **Legacy Casino recovery safety:** the website offers a special server recovery action for verified old-round refunds. The Android native bridge does **not** expose that capability. The generated Android adapter displays the saved-round notice but does not offer an actionable refund button unless `canRecoverLegacyCasino` becomes explicitly server-verified. This prevents a misleading or unsigned refund.
- **Not within this PR:** no new casino payout algorithm, client RNG, direct Supabase writes or user-editable server outcome fields. Individual game engines and their native artwork remain unchanged.

## Part 7 — PupEye status and release readiness

- `websitePupEyePresentation.js` is byte-identical to the website's `src/pupeyeStatusPresentation.js`, SHA pinned and generated by the recurring source-sync workflow. The native Settings PupEye section shows the official bundled logo, server-verified account standing when available, explanatory language and safe links to `/pupeye` and `/pupeye/status`.
- This Android panel **never reports clear fair-play standing without a signed fair-play response**. Until a native fair-play-status endpoint is implemented, it deliberately displays `Status unavailable` even for verified accounts. The website checks the player's own signed browser session; web fairness signals are not proof of Android device attestation.
- Supabase CI's retired-identity migration is now compatible with both the legacy `public.pupeye_players` physical table in local testing and the grouped `puppy_pupeye.pupeye_players` table in production. It creates no new player identities, restores no retired IDs, and installs the protective triggers on the physical table only. Docker rate limits can independently prevent startup.
- **Acceptance still required:** green final head JS/Expo/native Android migration, successful ARM64 beta artifact, green local Supabase integration tests, manual phone/Chromebook/DeX screenshot comparisons, signed Gacha and each of six casino games tested end to end against a safe test account, verified round recovery, offline artwork, no regression to notification/identity behavior. **Do not merge or promote to production before these gates.**
