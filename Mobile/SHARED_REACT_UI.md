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
- `.github/workflows/sync-website-ui.yml`: scheduled every six hours and manually dispatched; proposes **Android-only** pull requests after tests when the website's color tokens change.

**Scope of automation:** website CSS color tokens only. Existing website HTML elements, CSS layout, animation keyframes, browser router and web-only APIs cannot execute as native React Native JSX. They must be translated into native components screen by screen, preserving behavior. They do not auto-sync to installed APKs. We do not modify website UI as a prerequisite.

## Visual parity acceptance

Android phone: website-like compact Play stage, puppy platform, name badge, care/energy strip, cyan tap pill, Next Reward card, resource strip, bottom navigation and dark translucent panels. Support Android screen cutouts, TalkBack, font scaling and reduced motion.

Android tablet/DeX: preserve larger layouts without truncation; browser's HTML desktop sidebar must be ported separately before claiming desktop 1:1 parity.

PupEye, save transactions, auth, queue, notification permissions and signing stay in native adapters. Do not replace them with browser-side code or guest gameplay.

The original website remains unchanged. Work here is an incremental Android visual migration, not a claim that all website screens already have identical native rendering.
