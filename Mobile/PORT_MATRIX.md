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
| 4 | `RosterScreen`, profile, `PrestigeScreen`, redeem | `Mobile/App.js`, `AccountDetailsScreen.jsx`, `PuppyExchangeScreen.jsx` | **Partial** | Owner/metadata, layout, empty/error states, animations, accessibility |
| 5 | `src/InventoryScreen.jsx` and account inventory | `WebsiteInventoryScreen.jsx` | **Partial** | Inventory works read-only; current website DOM layout and Android RN code remain separate |
| 6 | `web/AccountGate.jsx`, web registration | `BetaAccountEntry.jsx`, `AccountOnboarding.jsx` | **Native + partial UI** | Visually match web; keep PKCE/session, Keystore and signed PupEye identity native |
| 7 | `WebSettings.jsx`, notifications/inbox, responsive onboarding | `SettingsScreen.jsx`, `NotificationInbox.jsx`, onboarding | **Partial** | Exact UI parity with native notifications and preferences |
| 8 | Named Casino and Puppy Gotcha entrypoints | `Mobile/App.js` plus existing casino games/Gacha | **Native, retained** | Preserve existing game logic; add explicit UI slots and verify all games |
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
- **Goal state:** generated `websiteGoalState.js` mirrors the website `src/native/web/webGoalState.js`; only its engine import paths change. Incomplete goals navigate to Play/Care/Shop; eligible goals show Claim Reward. Maxed Shop Visit goals use website semantics, but server authority may still reject claims.
- **English/Spanish Rewards strings:** website `src/communityI18n.js` is copied byte-for-byte to `websiteI18n.js`; Android uses device locale while the native language selector remains a future UI task.
- **Native calendar exception:** website uses a UTC server-day adapter and Android retains its existing local-day signed progression until its cloud protocol can be migrated safely. The native adapter explicitly supplies the local-day date/epoch functions to the website-owned Rewards component. This may differ around midnight/time-zone boundaries; validate with server-owned test accounts.
- **Security:** no browser auth, Vercel APIs, guest save state or Supabase secrets are copied. Existing Android `act` callbacks remain the only mutation boundary.
- **Verification:** the source-sync script pins all generated files with source/target Git blob SHAs, runs offline drift checks, and checks the native JSX structure. The GitHub Action proposes updates rather than auto-merging/releasing. CI and physical devices must still pass before claiming pixel-exact parity.
