## October 29 — Physical Puppy Clicker schema migration v3 complete

The 36 original Puppy Clicker tables have been physically moved into six schemas. React Native `Mobile/` accesses the same cloud records through the stable signed PupEye native/Edge Function endpoints; no new Android API credentials or different account identity is required. Supabase server RPCs now handle upsert operations against the new physical tables, while `public` compatibility views preserve older clients. This move does not activate new gameplay features or authorize guest save writes. The website's PupEye Web defenses are separate from Android installation attestation.

# Puppy Clicker Android — React Native (current)

**Android production target:** `Mobile/` React Native / Expo. The historic Kotlin `App/` directory is an archived reference and is not the application being migrated or deployed. React Native may compile Kotlin-based native modules internally, but those modules do not make the old Kotlin app the deployment target.

**Database:** React Native continues to use signed Supabase Edge Function and native PupEye service endpoints, not direct writes to grouped private database schemas. Supabase now includes a private PupEye Web rate gate for browser actions; it does not change Android's device attestation or signed cloud economy contract. All existing Android accounts and cloud saves remain authoritative after the completed physical migration. Signed Pup Account status and cloud bootstrap now announce layout v3; older signed responses remain compatible and future incompatible layouts display a safe update-required screen.

---

## Historical v2 plan — Puppy Clicker grouped database layout (obsolete)

> The description below predates the physical migration. See v3 status at the top of this document.

The six grouped Supabase schemas (`puppy_accounts`, `puppy_pupeye`, `puppy_game`, `puppy_exchange`, `puppy_admin`, `puppy_web`) currently contain read-only organizational views. **Canonical records remain in the original `public` tables.** The existing records, device identity, bans, saves and ownership are retained during their atomic table relocation; primary keys and foreign keys stay intact.

The RN Android client continues to call signed Edge Functions via its native PupEye bridge, rather than accessing these schemas directly. `src/production/dbSchemaLayout.js` declares contract v2; `authority.js` accepts older server responses without a version field and rejects explicitly newer incompatible versions. Production player access and signed writes remain governed by backend verification, not a client-selected schema. Never put service-role credentials or direct protected-table access into Android.

See the website repo's `docs/database-layout-v2.md` for the complete 36-table mapping and phased server migration requirements.

## October 25 — Native Pup Account registration and sign-in

Onboarding Step 2 now prioritizes **Pup Account**, with separate `Sign in with Pup Account` and `Create Pup Account` actions. Discord remains an optional alternative or connected identity. Native Pup credentials use a username (3–20 letters/numbers/`_`/`-`), email, and password (minimum 8 characters). Sign-in accepts username **or** email.

A native Android module sends password operations to the `pup-credentials` signed Supabase Edge Function over HTTPS, and Supabase Auth handles password verification and confirmation emails. The game never stores passwords, hashes, or Supabase Auth tokens in the local guest state. Private username reservations and verified Auth-to-Pup Account links live in RLS-protected tables.

The existing `pup_accounts` table now permits a null `discord_user_id` without changing existing Discord accounts. The signed `pup-account` endpoint accepts authenticated native accounts and does not rename them on Discord link. An existing account already attached to a different installation may sign in to the beta as an **expiring read-only companion**; the Play PupEye device, save authority and one-device rule remain unchanged.

Native credential login does **not** mean write-enabled game parity is complete: the beta gameplay UI stays read-only until signed economy and Kotlin cloud-state reconciliation are finished.

## October 25 — Kotlin onboarding with Pup Account integration

The beta now shows the original six-step first-run sequence before the game: Welcome → Set Up Your Account → Personalize → Permissions & Privacy → Notifications → Ready to Play. Setup uses native Discord/PupEye authorization, requires a successful account cloud read to continue, and rechecks the account at completion. It does not offer a guest nickname or `.pupsave` import/export.

Step, birthday (month/day only), appearance, privacy choices and notification categories persist under Kotlin's `puppy_ui_preferences_v2` keys. Optional reporting starts off; notification permission is requested only on its own step. Terms and privacy documents and original brand artwork are bundled offline. The Ready screen uses real public account identifiers.

Discord device conflicts now read React Native's error code and show the actual device-transfer requirement. A separate `.beta` install counts as a different PupEye installation even on the same phone. This change does not transfer existing accounts or complete signed gameplay/cloud writes; the post-setup game remains a read-only account view.

## Earlier October 25 — beta main game hierarchy

The installable Android test APK uses `com.harleytg.puppyclicker.beta` and launcher name **Puppy Clicker [Beta]**. After account onboarding, the beta mounts the real `PuppyApp` hierarchy: Play, Care, Roster, Shop, Rewards, Casino and Settings. The Discord/PupEye sheet is available from account setup and Settings.

**This is a UI parity/test shell, not completed playable parity.** The beta ignores `GUEST_SAVE_KEY` and blocks all unverified or unsigned economy mutations; it never writes a guest save or replaces a Kotlin Pup Account cloud save. After device-bound Discord authorization, a strictly read-only projector can show the existing `puppy-clicker-cloud-save` format from the server. Signed gameplay/casino transactions, comprehensive Kotlin save mapping, revision-aware signed cloud writes, device transfer of existing accounts, account entitlements, and remaining platform notifications must be completed and tested before releasing playable beta gameplay. The server's one-device rule remains enforced.

The authoritative Kotlin reference remains archived for parity. The `com.harleytg.puppyclicker` Play replacement needs complete feature and security validation and the original signing identity; the isolated `.beta` APK is not an in-place update.

## October 25 — beta native Discord/PupEye account bootstrap

- **Separate beta launcher no longer runs `classifyOriginalInstall()`:** `ProductionEntry` selects `BetaAccountEntry` for the `com.harleytg.puppyclicker.beta` package. The old `SIGNED_UPGRADE_REQUIRED` screen applies only to the reserved original production package handoff.
- The beta registers the original Discord OAuth callback URI using Android intent filters and launches a PKCE S256 flow from a native module. If Android offers multiple callback handlers because the Kotlin Play app uses the same redirect, users must choose **Puppy Clicker [Beta]**.
- **No guest gameplay:** after Discord authorization, the native bridge generates a *beta-only* Android Keystore signing/encryption identity and enrolls it with the existing `pupeye-register` endpoint, authorizes through `pupeye-auth-discord`, and reads `pup-account` status / cloud-save data. Device sessions are AES-GCM sealed in the beta sandbox and are never returned to JavaScript.
- **Original-account limitation:** PupEye enforces one authorized installation. An existing Play app account can return `DISCORD_ALREADY_LINKED` or `PUP_ACCOUNT_ON_OTHER_DEVICE`. The beta does not override enforcement or silently transfer/revoke the original account. No production cloud writes or React guest fallback are enabled.
- **Still not playable:** signed production economy/casino operations, Kotlin-format authoritative cloud save hydration, post-transfer reconciliation, and full visual parity must be implemented and validated. This beta account foundation is not a finished replacement.

## October 25 — separately installable Puppy Clicker [Beta] APK

- Android beta identity: `com.harleytg.puppyclicker.beta`; launcher name: **Puppy Clicker [Beta]**. This is an isolated, debug-key-signed standalone release APK with bundled JavaScript, not a Google Play update or the production signing certificate.
- The GitHub Actions **Puppy Clicker Beta APK** workflow verifies the package, launcher label, JS bundle and Android signature, then uploads `puppy-clicker-react-native-beta-apk`. An EAS `beta` profile uses the same package.
- **Known blocker:** a separate beta package cannot access Kotlin's Android Keystore or save sandbox, and the verified Discord OAuth/device-register/device-transfer flow has not been ported. The beta is not an authenticated playable replacement; gameplay remains fail-closed. No guest save is used, and existing signed Kotlin installations must not be uninstalled.
- The original `com.harleytg.puppyclicker` remains reserved for a later fully validated, original-key-signed, data-preserving Play upgrade. Historical `.preview` references in older porting notes describe former builds, not the current APK.

## October 25 production migration directive — supersedes the old guest-preview instructions

The React Native app's default root now opens **Pup Account migration verification**. It does **not** run the legacy guest preview or permit unverified gameplay. The Kotlin-derived gameplay components remain in the repository as fixture/reference code only until signed, authoritative transactions are connected.

The Android native `PuppyProductionAuthority` module uses the original Kotlin Keystore aliases, AES-GCM encrypted PupEye files and server-signed `account-status` request. It can confirm an existing account/cloud revision **read-only** on a valid signed in-place upgrade. A separate Migration QA APK cannot access the production app's data sandbox. The device session is never surfaced to JavaScript; the original saved progression is never replaced.

To enable live account verification for a correctly signed build, configure `EXPO_PUBLIC_PUPPY_SUPABASE_KEY` with the **publishable** key for the existing Puppy Clicker Supabase project. Never embed a secret/service-role key. Missing configuration, incomplete original state or failed server verification intentionally blocks access.

**This is not yet a releasable production replacement.** Existing-player conversion, device transfer, Discord PKCE login, PupEye transaction enforcement, signed cloud-save restore, original Play upload key verification, and installed-device tests remain mandatory. Do not uninstall the Kotlin app or flip release approval flags to bypass this process. Historical guest-preview implementation notes below are retained for audit but do not describe the current runtime behavior.

---

# Puppy Clicker — React Native Android Migration

This is a genuine React Native / Expo Android application, **NOT a website wrapper**. Its React components draw Android UI with React Native views and native touch handling.

**Status:** LOCAL GUEST PREVIEW SOURCE PORT ONLY — build and device parity not yet certified; not a feature-complete production replacement. The signed Kotlin/Compose app in [`../App/`](../App/) remains the sole existing Google Play release.

## Versions / Android identity

- Expo SDK 57 + React Native 0.86 + React 19.2 (React Native new architecture).
- Reserved Google Play package: `com.harleytg.puppyclicker` (unchanged original identity); all normal builds default to the **separate** `com.harleytg.puppyclicker.beta`.
- Version code: `35` (current Kotlin source code: `34`). Google Play requires monotonic increment and original upload signing key.
- Android target SDK 36 via SDK 57 defaults.
- No `WebView` package or browser-embedded UI.

## Run locally

Use Node.js 22+, Android Studio + SDK and a test device/emulator (not the device with your signed production app installed):

```bash
cd Mobile
npm install
npx expo install --fix
npm test
npx expo start --android
# To generate Android source and build a native test APK:
npx expo run:android
# To prepare a generated Android Gradle project:
npx expo prebuild --platform android
```

The `app.json` reserves the future Play package ID, but **runtime configuration defaults to the safe beta package**. Use `npx expo run:android` (or `PUPPY_NATIVE_BETA=1 npx expo run:android`) to install the separately named `com.harleytg.puppyclicker.beta` test app alongside the existing Play app. **Never** try to install an untrusted debug build over the Play install; original Play signing is different. Do not uninstall production to test without securely backing up and verifying original native cloud data; use a separate emulator/test profile instead.

## Screens implemented in React Native

- Native Play / tap interactions, four-currency wallet, combo and 10-tap bonus.
- Care meters, per-pup care action, active dog park and daily reward.
- Upgrade Shop with Bone/Ticket gating; ticket shop, accessory options.
- Prestige reset and permanent skill tree.
- Roster selection, local favorites/search, nine source achievements, guest XP, Settings and guest reset.
- Six-step guest onboarding: welcome, player setup, personalization, privacy, notifications, ready.
- Casino Chips conversion; six dedicated Kotlin-sourced game screens: Slots, Roulette, Scratchers, Plinko, Lucky Wheel and Blackjack with physical 52-card deck, Hit/Stand/Double/Split and persisted local round.

`src/engine/` contains a **copied, traceable starting point** of the earlier web Android-derived economy. Those engines are not a full reimplementation of the giant Kotlin ViewModel, nor are they currently verified against every Android state transition.

## SECURITY AND SAVE BOUNDARY — IMPORTANT

- The preview stores a **guest-only, device-scoped** cache via AsyncStorage.
- It is **not** the authoritative Android Supabase save, has no Discord OAuth or device binding, and offers **no** imports from the installed Android app.
- Do not treat this cache as trusted achievements, XP, rare drops, in-game premium currency, or leaderboard proof.
- PupEye bans, appeals and bot admin synchronization are not wired into this preview. **Never release it as production** before original anti-cheat and account enforcement are ported.
- Never put a Supabase service-role key or Discord bot secret inside the shipped JavaScript bundle.

## Completion blockers

Full parity still requires verified real-account onboarding, protected/streamed V3+ character entitlements, pixel-level casino art and animations, Exchange, Puppy Codes, Discord OAuth/role verification, server-authoritative cloud/device migration, PupEye anti-cheat, native notifications/widgets, and real-device tests. See [the detailed parity audit](PARITY.md). See [migration specification](../docs/superpowers/specs/2026-10-23-react-native-android-migration.md).

## CI

`react-native-android.yml` runs Node economy/state tests and attempts an Android Metro export and native prebuild on GitHub-hosted CI. These checks do not prove Google Play signing or user-data migration. Use the signed original-key AAB and Google Play internal test track for actual replacement, only after migration gates pass.

## Installable standalone preview APK from GitHub Actions

Use [Puppy Clicker React Native Preview APK](../.github/workflows/react-native-preview-apk.yml) in GitHub Actions → **Run workflow** and select the `main` branch (or the migration feature branch while it remains available), or take the latest successful automatic run after a change to `Mobile/**`. Download the `puppy-clicker-react-native-preview-standalone-apk` artifact and install the APK on an Android device.

The workflow runs Gradle **`assembleRelease`** (signed with the template's **debug key**, **not** the owner's Google Play signing key), so **`assets/index.android.bundle` is embedded** in the APK. No Metro server, computer, or USB connection is required to open the standalone preview. CI verifies that the bundle exists inside the APK, that the package ID is `com.harleytg.puppyclicker.preview`, and that the APK signature is valid. **Do not use `assembleDebug` APKs for standalone installation**: those expect Metro and show the red "Unable to load script" screen when it is unavailable.

The Kotlin AFK reminder behavior is now mirrored in the preview: enabling notifications during onboarding enables attention reminders by default; Android schedules a repeating two-hour local alert only while away and cancels it on return. The alert text describes the last known care state when scheduled, not a live background Supabase refresh. Android may defer local delivery under battery policies. Developer/system server broadcasts, original inbox history, cloud account identity and signed Kotlin-save migration are **not** ported yet.

The preview remains separately installed from the Kotlin Google Play app (`com.harleytg.puppyclicker`). **Never uninstall the production game to troubleshoot the preview**, as the native game and its authoritative cloud data must be preserved. If Android reports "App not installed" due to a different signing certificate, remove **only** the `Puppy Clicker Preview` test app before installing the latest preview APK. Removing the preview deletes its guest-only local test data. It is **not** a production-ready Play AAB or a verified feature-complete migration.

## Artwork

The original logo and all **32 V1/V2/Discord and Kotlin test puppy PNG portraits** are bundled directly from the canonical `assets/` Git blobs for immediate offline display in React Native. Unlocking restricted or role-gated puppies still requires verified official entitlements; including a picture does not grant ownership. Future V3+ streamed art must be validated before native rendering.

## Detailed migration audit

See [PARITY.md](PARITY.md) for a feature-by-feature status against the Kotlin source and the mandatory Play cutover checklist. The list is intentionally explicit about missing 1:1 behavior.

## Production release guard

`Mobile/app.config.js` always chooses `com.harleytg.puppyclicker.preview` unless **both** `PUPPY_NATIVE_PRODUCTION=1` and `PUPPY_NATIVE_RELEASE_APPROVED=1` are set, and the preview flag is absent. This is a build-time safeguard, **not proof** of parity or permission to publish. Release candidates require manual confirmation of original signing, verified Supabase/PupEye authority and Kotlin-to-RN data migration, plus signed Play internal testing. Do not set the release approval flag just to generate an APK.

## Part 6 Android build and screenshot verification

GitHub workflow [React Native Preview APK](../.github/workflows/react-native-preview-apk.yml) must finish green before an APK artifact is advertised as working. On October 24, 2026, workflow run [40068347188](https://github.com/markhitchk/pup-clinker/actions/runs/40068347188) failed **8 of 147** Node tests and did **not** produce a verified APK. Part 6 commits fix the Wheel width crash and stale asset/navigation/daily-goal assertions; verify a **new completed** workflow run before claiming that those fixes pass CI or that an APK exists.

For actual image parity rather than source assumptions, follow [PART_6_DEVICE_QA.md](PART_6_DEVICE_QA.md) and [VISUAL_PARITY_AUDIT.md](VISUAL_PARITY_AUDIT.md). `scripts/android_visual_audit.py` compares original Kotlin and React screenshots at identical size, exports changed-pixel percentages and heatmaps, and refuses missing screenshots. Run on the same physical Android device with matching states. **No comparison images have been collected in this repository; 100% visual parity is not verified.**


### Restored settings and local notifications (October 24)

Open Settings from the header gear or Settings tab. Appearance and Gameplay choices persist in the existing guest save. The header bell opens the notification inbox. Android alerts require permission from Settings → Notifications; no push token or Firebase project is requested. Daily and park reminders are recalculated when opening the app or changing the relevant game/preferences; puppy attention is scheduled only while away. These reminders do not import the original Kotlin history or issue trusted AFK rewards.

The custom hardware cursor requires the standalone Android build (`expo prebuild` applies the config plugin); Expo Go does not contain this native view. Keep `App/` as the original Kotlin reference. The preview launcher uses the original source drawable and preserves the separate preview package.
