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
- `node Mobile/scripts/sync-website-native-ui.mjs`: mirrors reviewed UI source from an immutable website commit; `websiteUiSources.json` pins revision, paths and checksums.
- `.github/workflows/sync-website-ui.yml`: scheduled every six hours and manually dispatched; proposes **Android-only** pull requests after tests when website color tokens or allowlisted native UI components change.

**Scope of automation:** website CSS color tokens plus the allowlisted native account-details UI screen. The website owns the editable implementation, and Android's checked-in output is generated only (not a separately maintained screen). See `Mobile/PORT_MATRIX.md` for ownership and staged migration. Other website HTML elements, CSS layout, animation keyframes, browser router and web-only APIs cannot execute as native React Native JSX. They must be translated into native components screen by screen, preserving behavior. They do not auto-sync to installed APKs. We do not modify website UI as a prerequisite.

## Visual parity acceptance

Android phone: website-like compact Play stage, puppy platform, name badge, care/energy strip, cyan tap pill, Next Reward card, resource strip, bottom navigation and dark translucent panels. Support Android screen cutouts, TalkBack, font scaling and reduced motion.

Android Chromebook / DeX: the Android beta now includes a native translation of the website's desktop sidebar, XP/streak header, two-column Play dashboard, Resources, Today's goals and Pup Care Status panels, and side-by-side Roster/Shop on wide screens. These views read the same signed Pup Account gameplay state as the phone UI. Window width >=960dp enables desktop; smaller windows automatically restore the phone layout. The activity allows both orientations and explicitly opts into resizable multi-window mode via `plugins/withPuppyDesktopMode.cjs`. Desktop inventory and Help are native, read-only/help views.

The **website-only DOM/CSS and its browser interactions** are not bundled in Android. Some secondary screens still use their existing responsive React Native implementations; keyboard/mouse QA on real Chromebook and DeX hardware is needed before claiming exact 1:1 parity.

PupEye, save transactions, auth, queue, notification permissions and signing stay in native adapters. Do not replace them with browser-side code or guest gameplay.

The original website remains unchanged. Work here is an incremental Android visual migration, not a claim that all website screens already have identical native rendering.

## Android desktop mode files

- `Mobile/src/production/desktopLayout.js` — same 960dp breakpoint as the website, recomputed from `useWindowDimensions()`.
- `Mobile/src/components/WebsiteDesktopShell.jsx` — side navigation, player-level/streak top bar, inventory and help.
- `Mobile/src/components/WebsiteDesktopDashboard.jsx` — Play's Resources, Today's Goals and Pup Care Status sidebar panels.
- `Mobile/src/components/WebsitePlayScreen.jsx` — existing native puppy stage expanded for desktop while preserving signed tap commands.
- `Mobile/app.json` and `Mobile/plugins/withPuppyDesktopMode.cjs` — unlocked orientation and resizable freeform window support.

Do not use UA strings or hardcoded ChromeOS/DeX device lists. Viewport size is the responsive source of truth; window resizing can change layout without replacing the Pup Account. Native Android activity restarts, if any, still follow secure startup and cloud verification.

