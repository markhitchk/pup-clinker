# Android must match the existing Puppy Clicker website

The **website is already approved**. Its repository, CSS, layouts, account pages and Vercel deployment are not changed by this Android work.

## Android visual source of truth

- Website: `markhitchk/Puppy-Clicker/src/native/App.jsx` and `src/native/web/kawaii.css`.
- Android native Play-screen adaptation: `Mobile/src/components/WebsitePlayScreen.jsx` integrated into `Mobile/App.js`. It keeps the real signed PupEye game actions. This is not a WebView.
- Android theme: `Mobile/src/production/websiteTheme.js` and generated `websiteDesignTokens.js`. The tokens are copied from the website CSS root, not invented Android styling.
- Login/registration: `Mobile/src/production/BetaAccountEntry.jsx`; signed Android native authentication remains separate from the website's browser Supabase session. New UI design must use the website `/account` as reference.

## Changes and syncing

Run from the Android repository:
- `node Mobile/scripts/sync-website-ui.mjs --verify`: checks Android-native source/style integration offline.
- `node Mobile/scripts/sync-website-ui.mjs`: reads the *unchanged* website `main` stylesheet and regenerates native design tokens.
- `node Mobile/scripts/sync-website-native-ui.mjs --verify`: checks exact Git blob hashes for allowlisted website-owned, React Native-compatible components without network access.
- `node Mobile/scripts/sync-website-native-ui.mjs`: mirrors reviewed UI source from an immutable website commit **and** generates `websiteNavigation.js` from the website's `MAIN_TABS` definition; `websiteUiSources.json` pins source, generated paths and checksums.
- `Mobile/src/components/WebsiteCareShopScreens.jsx` and `WebsiteRewardsScreens.jsx`: generated from the React Native JSX functions in `Puppy-Clicker/src/native/App.jsx` with native style/security callbacks injected by `Mobile/App.js`. Do not edit the generated copies; any future website UI changes must flow through the source sync.
- `.github/workflows/sync-website-ui.yml`: scheduled every six hours and manually dispatched; proposes **Android-only** pull requests after tests when website color tokens or allowlisted native UI components change.

**Scope of automation:** website CSS color tokens, the canonical five primary tabs, account details, inbox, onboarding, website-owned Care/Shop/Rewards/Roster/Prestige/Casino/Gacha screen functions, the puppy profile dialog, and the Inventory category contract (including daily goal cards and accordions), plus the site's bilingual Rewards catalog, eligibility rules, source-derived username/password and referral contract, and website Settings headings. Reviewed Image source substitutions adapt website asset URLs to bundled Android `require()` calls, without changing UI logic. The website owns the editable implementation, and Android's checked-in output is generated only (not a separately maintained screen). See `Mobile/PORT_MATRIX.md` for ownership and staged migration. Other website HTML elements, CSS layout, animation keyframes, browser router and web-only APIs cannot execute as native React Native JSX. They must be translated into native components screen by screen, preserving behavior. They do not auto-sync to installed APKs. We do not modify website UI as a prerequisite.

## Visual parity acceptance

Android phone: website-like compact Play stage, puppy platform, name badge, care/energy strip, cyan tap pill, Next Reward card, resource strip, bottom navigation and dark translucent panels. Support Android screen cutouts, TalkBack, font scaling and reduced motion.

Android Chromebook / DeX: the Android beta now includes a native translation of the website's desktop sidebar, XP/streak header, two-column Play dashboard, Resources, Today's goals and Pup Care Status panels, and side-by-side Roster/Shop on wide screens. These views read the same signed Pup Account gameplay state as the phone UI. Window width >=960dp enables desktop; smaller windows automatically restore the phone layout. The activity allows both orientations and explicitly opts into resizable multi-window mode via `plugins/withPuppyDesktopMode.cjs`. Desktop inventory and Help are native, read-only/help views.

The desktop Play night-yard image uses the actual published website scene URL (`/assets/scenes/night-yard-v1.webp`), with a native dark background as a fallback when that remote image is unavailable; a bundled offline artwork copy is still pending. The **website-only DOM/CSS and its browser interactions** are not bundled in Android. Some secondary screens still use their existing responsive React Native implementations; keyboard/mouse QA on real Chromebook and DeX hardware is needed before claiming exact 1:1 parity.

PupEye, save transactions, auth, queue, notification permissions and signing stay in native adapters. Do not replace them with browser-side code or guest gameplay.

The original website remains unchanged. Work here is an incremental Android visual migration, not a claim that all website screens already have identical native rendering.

## Android desktop mode files

- `Mobile/src/production/desktopLayout.js` — same 960dp breakpoint as the website, recomputed from `useWindowDimensions()`.
- `Mobile/src/components/WebsiteDesktopShell.jsx` — side navigation, player-level/streak top bar, inventory and help.
- `Mobile/src/components/WebsiteDesktopDashboard.jsx` — Play's Resources, Today's Goals and Pup Care Status sidebar panels.
- `Mobile/src/components/WebsitePlayScreen.jsx` — existing native puppy stage expanded for desktop while preserving signed tap commands.
- `Mobile/app.json` and `Mobile/plugins/withPuppyDesktopMode.cjs` — unlocked orientation and resizable freeform window support.

Do not use UA strings or hardcoded ChromeOS/DeX device lists. Viewport size is the responsive source of truth; window resizing can change layout without replacing the Pup Account. Native Android activity restarts, if any, still follow secure startup and cloud verification.

 
## Part 2 — native navigation and responsive Play

- Five primary tab labels/icons/order are generated from `Puppy-Clicker/src/native/src/engine/playRevamp.js`, imported directly into Android `App.js` and enforced by an offline hash test. No Android-handwritten duplicate navigation array is used for the shell.
- `desktopLayoutForWindow` matches the website `desktop-reference.css` sidebar `clamp(180px,16.15vw,248px)` and dashboard `1.366fr 1fr` columns, including the page gutters and 18dp gap. It recalculates when the freeform window changes.
- The native desktop Play stage mirrors the website night-yard background, large heading, puppy identity and rename action, transparent stage, cyan tap pill and translucent Next Reward card. Physical device screenshot comparison and full DOM-to-RN UI extraction remain outstanding.
- The Player Level/Streak topbar proportions and desktop sidebar sizing now follow the existing website reference.
- Smaller phone viewports use compact puppy artwork and allow vertical scrolling when necessary to avoid clipping controls; ordinary height continues with a fixed five-tab bottom bar.
- Account, gameplay commands, PupEye, notifications and backend writes remain native; this does not add a browser login or WebView.

## Part 3 — Care, Shop and Rewards ownership

Care, Shop and Rewards screen body ownership resides in the website's `src/native/App.jsx`; Android uses generated component factories. The Rewards source uses React Native Views and Buttons already; only the browser-specific compact HTML goal layout is omitted from the Android output. Source checksums and deterministic generation prohibit drift.

Android's existing local-day reward authority intentionally differs from the browser's UTC server-day display. This is an explicit platform exception rather than a silently changed save contract. Android native actions continue to handle Care, Shop, daily gifts and goal claims. The website's Spanish/English text and goal availability rules are shared; Android currently derives the Rewards language from system locale, not an in-app language picker. Verify around date boundaries and live cloud saves before production release.

## Part 4 — Roster, Prestige, puppy profiles and Inventory

- `WebsiteRosterPrestigeScreens.jsx` is generated from the exact website `src/native/App.jsx` `PrestigeScreen` and `RosterScreen` functions. It receives immutable native helper modules, current game state and signed `act` callbacks through the Android shell; the UI body must never be edited independently.
- `PuppyDetailsDialog.jsx` now mirrors the website modal except its cosmetic `suggestPuppyName` import is rewritten to the generated native name-list adapter. `AccountDetailsScreen.jsx` remains website-owned from Part 1.
- `websiteInventoryContract.js` is generated from the website's `src/InventoryScreen.jsx`; `inventoryModel.js` maps those exact website category/wallet labels/icons into native immutable objects. The native Inventory view remains separate because the website Inventory is DOM and CSS; it never writes the save and links to the Pup Account page.
- **Do not misrepresent redemption:** the website's Redeem Code action explicitly states that signed entitlement redemption is not live. Android preserves this status; no player entitlement is granted from a typed code.
- CI enforces source checksums; direct app builds and on-device visual checks remain required. A beta APK is not a production rollout.

## Part 5 — canonical Pup Account and Settings contracts

The web account gate and Settings screens are **browser DOM** components. Copying them as-is would break Android and bypass its signed identity integration. Instead, the sync script now generates `websiteAccountContract.js` from three website sources (`WebRegister.jsx`, `AccountGate.jsx`, `JoinSourceFields.jsx`) and `websiteSettingsContract.js` from `WebSettings.jsx`. Each output has exact source and generated-content Git blob hashes; CI fails on drift. Android's `BetaAccountEntry.jsx` consumes the website's password/referral rules and intro copy and provides native-style tabs, password manager fields, policy acceptance and secure native callbacks. Browser Supabase Auth, sessions and route handlers are never imported.

Android Settings uses website section titles for the shared account, profile, appearance, notifications and play/performance destinations, with native device-specific controls retained. The source-owned `communityI18n.js` catalogue is reused through `websiteLanguage.js`: English/Spanish selection persists locally using the website language key and updates Rewards text without modifying Pup Account save fields. The native notification inbox, account details and onboarding were already exact website-owned React Native modules (with documented image adapters). Full browser-vs-native UI screenshot parity and verified native session revocation remain outstanding.

## Parts 6–7 — website Casino/Gacha and PupEye owner-only status

`WebsiteCasinoGachaScreens.jsx` now derives the complete Casino and Gacha screen bodies from `Puppy-Clicker/src/native/App.jsx` as generated source, with a narrow native `<Modal>` adaptation for the website's browser-only CasinoIntroDialog. It preserves the actual website disclaimer and all six casino routes. The `useGame` signed queue supplies `gameplayBusy` while protected actions await server confirmation, so the canonical Gacha buttons cannot be tapped twice while an outcome is uncertain. A website-only legacy refund button remains hidden unless an explicit server/native recovery capability is implemented; never manufacture refunds locally.

`websitePupEyePresentation.js` is copied byte-for-byte from the website's `pupeyeStatusPresentation.js` and verified by SHA. The Android Settings PupEye panel displays the official icon and verified account standing, but **must not** infer fair-play clearance from account verification. It offers `/pupeye/status` for the player's server-confirmed website state. Internal risk scores, rule thresholds, case evidence and other users' records are not available to the Android screen.

See `PORT_MATRIX.md` for detailed exceptions and signed gameplay/CI release gates. This work remains on PR #102 only. The original website deployment remains unchanged, and no protected Supabase table has been directly edited.
