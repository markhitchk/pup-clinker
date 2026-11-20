# Website to Android parity

Source baseline: markhitchk/Puppy-Clicker at `64e4ccd32580c5fcfbf4038a3de328845aaa4fe2`. Android baseline: `4b6e2fc0fb35446d7fea6239672441ddd57238da`. Entry points inspected: website `src/main.jsx → src/App.jsx → src/native/App.jsx` for Play, Android `Mobile/index.js → Mobile/App.js` after queue admission. The website's separate `mobile/App.jsx` launches UniversalApp, which production Web does not mount.

| Website route or subsystem | Android entry or gap at baseline | Verification required for release |
| --- | --- | --- |
| Home, demo, footer and brand dialogs | Android starts at the native account gate | Native home, guest demo, navigation and visual comparison |
| /play, desktop layout, care, shop, prestige, gacha | Android has independent Play shell, optional compact layout and native controls | Match both viewport layouts, pointer, back navigation and signed economy |
| /play, daily goals and rewards | Android uses local-day schedule and signed server actions | UTC boundaries and simultaneous website/device claims |
| Casino, including blackjack, slots, plinko, roulette, scratchers and wheel | Native scenes and signed settlement exist | Cross-platform pending rounds and interrupted transactions |
| Puppy Exchange, friends, gifts, trades | Native exchange adapter | Two-account approval, restrictions and receipts |
| Roster, /account/inventory, /pupdex and /puppies alias | Baseline has native roster and older legacy collection | Published catalog, rarity effects, ownership, asset details, links and read-only modes |
| /account, /register, profiles, Discord login/link, /auth/discord/callback | Native credential and PKCE sign-in, secure session and Android callback | Same-account auth, profile edits, notifications and device transfer |
| /recovery, account deletion and session management | Native account UI does not cover all website actions | Recovery, revocation, deletion and enforcement |
| /pupeye, /pupeye/status, bans and suspensions | Native signed authority, startup status and enrollment | Standing and restriction screens, original signing migration |
| /chat, mentions, moderation; /leaderboard, opt-in and ranking | Website UI uses browser account session | Native session adapter, authorization and interaction tests |
| /26xmas, Advent rewards, snow and seasonal theme | Baseline has no native event page | UTC event window, duplicate claims and shared progression |
| /privacy, /account/privacy, /legal/privacy and /legal/terms | Native consent/onboarding and local legal content | Current legal display, export, preferences and deletion |
| /settings, /browser/settings and in-game settings | Native settings and developer flags | Accessibility, locale, visual and persistence comparisons |
| /account/admin, users, audit, bans, gifts, activity, pupeye-web, chat, rarity, privacy, /account/bansys/user | No corresponding complete native admin portal | Verify role enforcement using authorized accounts |
| /error/{status}, numeric error routes and fallback | Native startup error screens | Native route recovery and error states |
| General services | Android uses signed native cloud actions, protected local session, notification channels, and local preferences | Cross-device save conflicts, process death, offline taps, denied permissions |
| Rendering, media and input | Native artwork, haptics, safe areas and Android pointer plugin | Full-resolution art, reduced motion, keyboard, cutouts, DeX, foldable, landscape and screenshots |

For a full source inventory at the audited revision, run `git ls-tree -rl 64e4ccd32580c5fcfbf4038a3de328845aaa4fe2 -- src public assets api supabase mobile package.json package-lock.json vite.config.js vercel.json` in the website repository. This enumerates source, assets, service implementations and lockfiles with their object IDs. Backend files represent server deployments and are not bundled solely because they appear in the inventory.

## Implemented in the Android target

The default phone Play screen and the dashboard at 960 logical pixels use the website's active Play and DesktopPlayScreen components. Care, Shop, Prestige and the daily goal card also come from the active website module. The importer reads named declarations with Babel, preserves the component bodies and binds browser host elements to native views, buttons, progress indicators and dialogs. Android supplies artwork, safe areas, navigation, feedback and the signed action dispatcher. The optional saved Android compact-layout flag remains available.

Roster, Inventory and PupDex use the production PuppyCollectionCard JSX and collectionCapabilities. Inventory includes owned puppies, accessories, tickets, upgrades and the wallet. The public rarity RPC supplies rarity and transfer metadata. Collection actions still require the signed game service. The native viewer displays the original artwork. The public web route and share/deep-link behavior, 3D model parity and foil animation still require work.

The shared-file manifest includes gameplay rules, native-compatible casino screens, cloud-save projection, palette, exchange, inbox and bundled assets. Metro resolves casino art locally. Android uses the existing native account and signing modules. The production package retains its original migration checks.

## Reproducing a build

From Mobile, run `npm ci`, `npm run verify:website`, `npm run typecheck`, `npm test` and `npx expo export --platform android`. To update snapshots, fetch the source repository and run `node scripts/sync-website.mjs /path/to/Puppy-Clicker FULL_COMMIT_SHA`. The importer reads committed Git blobs. The manifest records hashes of the output files. Expo embeds the pinned website SHA under `extra.websiteSourceCommit`.

The Website source sync workflow checks upstream hourly. Its summary lists changed website files, including changes outside the current mapping. The candidate builds and verifies an ARM64 beta before the workflow proposes the revision for review. The APK artifact includes the source manifest. Review newly discovered routes and platform dependencies before merging a synchronization PR.

## Verification status

On the baseline website, 360 of 363 Node tests passed. Three tests inspected collection markup and imports that had moved into shared components. The production Vite build succeeded. No website files were changed. On the Android candidate, the TypeScript check, all 307 Node tests and the Metro/Hermes export passed. This includes rendered navigation, read-only collection modes, gameplay rules and native bridge contracts.

Equivalent viewport screenshots, real sign-in, two-way cloud progression, Discord callbacks, moderation and event reward transactions require installed-device verification. The production signed beta endpoint inspected for this audit does not accept the website's claimAdvent or claimFreeTest intents. Feature-complete parity and an upgrade of the signed production application are pending.
