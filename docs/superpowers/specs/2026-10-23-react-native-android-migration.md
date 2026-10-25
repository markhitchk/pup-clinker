# Puppy Clicker — React Native Android replacement design

## Intent
Rebuild the original Kotlin/Jetpack Compose Puppy Clicker Android application as a true React Native Android game, with all game screens written in React components rather than a WebView. Preserve the exact Google Play package identity `com.harleytg.puppyclicker`, its players, game economics, artwork, and eventually its account/cloud/PupEye controls.

## Current-state constraints
- Android source of truth: `App/`, `PuppyClickerV6Activity.kt`, `PuppyClickerV6ViewModel.kt` and the V7 economy.
- Browser React foundation: `markhitchk/Puppy-Clicker/src/game.js` and `src/casinoV7.js`, a **partial** browser port, not production-authoritative Android logic.
- App upgrades must not break Google Play signing identity, incrementing version codes, original save formats or account ownership.
- DO NOT replace `App/`'s Play Store launcher until a separately signed production candidate has passed migration and real-device testing.

## Considered approaches
1. WebView wrapper around Vercel: fastest; rejected because not a React Native interface, no native offline interaction and weaker parity.
2. Immediate rewrite of `App/` into React Native: unsafe; destroys known-working production build and stored data without a migration path.
3. **Chosen: adjacent `Mobile/` Expo React Native project**, React UI and game logic, retain `App/` unchanged as reference and emergency rollback. Later cut over the launcher only after tests and signed AAB rollout readiness.

## Architecture
- Expo SDK 57 / React Native 0.86 with React 19.2; React components implement Play, Care, Shop, Prestige, Settings, Roster, Rewards, Casino.
- `Mobile/src/engine/game.js` and `casinoV7.js` are initially vendored from a specific verified web revision, with platform-independent Node tests and explicit parity gaps. Do not mislabel these as full Android parity.
- `Mobile/src/state/useGame.js` handles transitions and crash-safe guest persistence. Guest progress must **never** silently become a trusted cloud save. No direct write to Android production save tables or official player progression.
- `Mobile/App.js` uses React Native primitives, local visuals and clear disabled states for not-yet-supported native account features.
- Android artwork: original `assets/` source. Only use public art in RN; protected art, owner rewards and paid/role unlocks require secure entitlement paths.
- Google Play identity `com.harleytg.puppyclicker` and SDK target 36 are declared; release versionCode/Play signing must be verified before replacing Android.

## Migration/acceptance gates
1. Native React UI and five core tabs launch through Metro on an Android physical device, no WebView.
2. Reproduce Kotlin V7 economy from golden fixtures; red/green tests for tap, care, shop, prestige, casino rules and overflow/invalid actions.
3. Full roster, unlocks, onboarding, achievements, notifications, all six casino engines (including blackjack split/double) and animation fidelity.
4. OAuth PKCE deep link; server-authoritative save and sync; device binding and PupEye enforcement before authenticated production play.
5. Source-encrypted artwork and in-app assets validated; permissions, notifications/widgets, offline policy and Android lifecycle handled.
6. Test migration from installed Kotlin production app, signing identity, Google Play upload key and `versionCode` > 34, using internal testing first.
7. Build a signed AAB, exercise an Android 16 device, compare pixel screenshots and gameplay state, check R8 and release crash logs.

## Explicit boundaries for the first implementation
The first iteration delivers a REAL React Native Android project and working local single-device gameplay **only**, not a 100% completed feature-by-feature migration. Until account/PupEye and existing save migration are proven, production Kotlin app remains untouched. Guest progress is provisional and not portable.
