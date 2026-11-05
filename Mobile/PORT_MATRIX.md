# Website → Android port matrix — incremental parts

**Visual source of truth:** [Puppy-Clicker](https://github.com/markhitchk/Puppy-Clicker) `main`; **target:** [pup-clinker/Mobile](https://github.com/markhitchk/pup-clinker/tree/main/Mobile). The website's appearance and deployment are not changed by this work.

## Status meanings
- **Shared:** byte-identical, generated from one website-owned React Native-compatible component, with provenance and an offline drift test.
- **Partial:** native implementation exists but a side-by-side, screen-by-screen 1:1 review or true shared-source extraction is outstanding.
- **Native:** kept in Android by design, with browser UI only adapted through a narrow interface.
- **Deferred:** not yet proven equivalent; do not claim completion.

## Inclusion matrix

| Part | Website source / route | Android entry / ownership | Status | Next acceptance requirement |
| --- | --- | --- | --- | --- |
| 1 | `src/native/src/components/AccountDetailsScreen.jsx` | `Mobile/src/components/AccountDetailsScreen.jsx` | **Shared** | Website-owned upstream, pinned SHA, automated source sync and offline hash check |
| 1 | `src/native/web/kawaii.css` | `websiteDesignTokens.js` and `websiteTheme.js` | **Partial** | Tokens synced; typography, radii, spacing, interactions and animation parity remain |
| 2 | `src/native/App.jsx` navigation and `web/DesktopNavigation.jsx` | `Mobile/App.js`, `WebsiteDesktopShell.jsx` | **Partial** | Exact five tabs, deep-navigation/back behavior, phone/desktop and resize QA |
| 2 | Website `/play` PlayScreen + desktop Play | `WebsitePlayScreen.jsx`, `WebsiteDesktopDashboard.jsx` | **Partial** | Reuse the screen hierarchy/copy/animation contract; compare on real phone/Chromebook/DeX |
| 3 | `CareScreen`, `ShopScreen`, `RewardsScreen` | `Mobile/App.js` native functions | **Partial** | One website-owned UI implementation or generated adapter-driven contract per screen |
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
