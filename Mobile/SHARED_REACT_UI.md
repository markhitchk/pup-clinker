# Website-first React UI for Android

**Canonical UI source**: `markhitchk/Puppy-Clicker/src/shared-ui/` and `src/shared/usePupAuthForm.js`.

`Mobile/App.js` imports the exact website `PupGameChrome.jsx` component source for wallets/resource strips, care pills and quick actions. `Mobile/src/production/BetaAccountEntry.jsx` uses the same React form model as the website. Android-specific native auth, PupEye, local secure storage, signed gameplay and notifications are not replaced.

This is a reviewable **source sync**, not a WebView and not a remotely executed live web bundle. Website changes land in the Android app only after its source sync PR and a rebuilt APK; publishing the Vercel site alone does not silently update installed apps.

## Sync commands

Run from `Mobile/`:
- `node scripts/sync-website-ui.mjs --verify` — offline check of every pinned source blob. CI runs this without contacting the website.
- `node scripts/sync-website-ui.mjs` — fetch the website `main` source at a specific commit and update the allowlisted React UI mirror plus `src/shared-ui/SOURCE.json`.
- `PUPPY_WEBSITE_UI_REF=<reviewed-branch> node scripts/sync-website-ui.mjs` — test a non-main website branch before merge.

The `sync-website-ui.yml` GitHub Action runs every six hours (also manually), runs Android tests, and proposes changes on `sync/website-ui` for review. It must **not** auto-merge broken UI or alter the native backend.

Only React Native-compatible files under the website `src/shared-ui/` are synced automatically. The known form hook is also synced. Plain website HTML, browser CSS, Vercel API code, DOM-only dialogs, account tokens, and server secrets are excluded. To share more screens, gradually extract their React Native-compatible presentation into the canonical website folder and pass native-appropriate props/handlers from each app.
