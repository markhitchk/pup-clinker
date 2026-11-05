# Android ↔ Website parity and Supabase contract (2026-11-04)

## Actual shipping targets
- Android Expo/React Native source: `pup-clinker/Mobile/`.
- Historical Android/Kotlin reference: `pup-clinker/App/`. Do not replace this with a browser bundle.
- Website React companion: `Puppy-Clicker/src/native/`, with browser-specific UI and Supabase Auth adapters.
- Website `Puppy-Clicker/mobile/` is a separate Expo scaffold; do **not** copy its `puppy_web_saves` adapter into the signed production Android client.

## Verified live Supabase layout
- Project: `damcmyqusdxucwozkoeh` (Puppy Clicker DB).
- Canonical physical schemas: `puppy_accounts`, `puppy_pupeye`, `puppy_game`, `puppy_exchange`, `puppy_admin`, `puppy_web`.
- Canonical account table: `puppy_accounts.pup_accounts`.
- Authoritative signed save: `puppy_game.pup_account_saves`.
- Legacy browser-specific store: `puppy_game.puppy_web_saves`; it is **not** an alternative authoritative Pup Account cloud save.
- `public` compatibility views remain present and use `security_invoker=true`. Canonical account and cloud-save tables have RLS enabled.
- Active service endpoints include `pup-account`, `pup-web-auth`, and `pup-exchange`.

The native bridge must use signed, identity-bound PupEye/Edge Function requests to read or mutate protected cloud data. Never embed a Supabase service-role key, bypass RLS, write directly to canonical tables from Expo, or turn a projected Kotlin cloud save into a new authoritative save.

## Website feature incorporated in this branch
- **Inventory**: read-only Puppies, Accessories, Tickets, Upgrades, and Wallet screens; puppy search; owned/current/favorite presentation.
- **Android navigation**: opens from the top bar, Settings, and Pup Account Details.
- **Save behavior**: reads the app's accepted game state; the inventory never writes balance or entitlements. Refresh uses the existing signed native account bootstrap.
- **Tests**: `Mobile/tests/inventoryParity.test.js` asserts categories, entitlement counts, search and zero side effects.

## Remaining parity work — NOT claimed complete
- Native UI styling and layout are not yet visually identical to the current website.
- The website's moderated chat, leaderboards, privacy center, and PupEye status web routes need platform-specific Android views/services.
- Validate signed gameplay, economy, casino, gifts, notifications and reconnect/replay behavior against a real Android install and a linked website session.
- The original package `com.harleytg.puppyclicker` must not replace a Play installation until matching signing identity, cloud save migration, account/device transfer, and complete regression checks pass. Keep the test package `com.harleytg.puppyclicker.beta`.
- APK build and on-device verification are performed by the React Native Android GitHub Actions workflows, not by applying DB migrations.

## Safe release acceptance
1. Authenticate an existing Pup Account in Android Beta and on website.
2. Verify the identical authoritative cloud revision and inventory items on both, including gifted puppies.
3. Test server-accepted purchases/rewards/exchange and ensure the values reappear across sessions without a conflict.
4. Confirm banned/revoked sessions are stopped on both clients and neither uses a guest-save fallback.
5. Run `npm test`, Expo Android export, Kotlin native module compilation, and ARM64 beta APK CI.
