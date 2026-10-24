# Kotlin → React Native Android: one-part-at-a-time rebuild

**Authoritative Android source:** `App/app/src/main/java/com/harleytg/puppyclicker/` in this repository.
**React Android target:** `Mobile/` on `feat/react-native-full-android`.
**Release gate:** Kotlin app (`App/`) is unchanged. React uses `com.harleytg.puppyclicker.preview`, guest-only data. No original signed Play package or PupEye cloud save cutover.

## Rules for every part

1. Use the actual launched Kotlin Compose function, not an unused old screen or an earlier React/web mock.
2. Compare and port the exact navigation, copy, layout dimensions, artwork, gestures, animation behavior and state transitions; do not substitute placeholder UI.
3. Keep authoritative Kotlin game economy and account behavior as acceptance references. Explicitly mark React guest-only simulations when native Supabase/PupEye enforcement has not been migrated.
4. Add automated deterministic contracts and a real-device screenshot checklist before considering a part visually verified.
5. Keep this single branch for Android React, do not use the separate `Puppy-Clicker` web repository as the Android implementation.

## Parts and status

| Part | Target | Status / source |
| --- | --- | --- |
| **1** | **Launcher Play: V6Header, V6Wallet, V6NeedPill, V6Play** | **Code rebuilt; automated tests added; phone screenshots and Android device interaction review still required.** Source: `PuppyClickerV6Activity.kt`. JS contract: `Mobile/src/engine/playV6.js`; view: `Mobile/App.js`; tests: `Mobile/tests/playV6.test.js`. |
| 2 | Care & Daily, five care actions, per-puppy care, Park, daily claims | Next. `V6CareAndDaily`, `PuppyCareSystem.kt`, `PuppyMonthlyRewards.kt`. |
| 3 | Shop, tickets, accessories, Puppy Codes and Prestige | Pending. `V6Shop`, `PuppyEconomyV7.kt`, V6 Prestige. Codes cannot grant protected rewards without server verification. |
| 4 | Puppy Roster, art V1/V2/V3+, Gacha and official role unlocks | Pending. Original art, `PuppyRosterScreen.kt`, `PuppyGacha.kt`. Secure entitlements must remain server-only. |
| 5 | Six casino games as **separate subparts**: Blackjack, Slots, Roulette, Plinko, Scratchers, Wheel | Pending pixel/gesture/round-recovery validation. Scratchers guest gesture implementation exists but is not Kotlin Canvas-equivalent. |
| 6 | Settings, profile, onboarding, themes, accessibility, support, developer/admin menus | Pending. Native flows and role visibility must be verified. |
| 7 | Discord OAuth, original Pup Account cloud save, one-device/PupEye, Exchange, notifications/widgets | Blocked on backend authority/security design and migration verification. No guest-currency imports. |
| 8 | Final cross-feature regression, screenshot diff, signed release migration | Not started. Requires original signing key, Play internal test, original-data recovery and approval. |

## Part 1 exact contract

The **launched** tab path is `PuppyClickerV6Activity.kt → V6TabContent → V6Play`. The separate `PuppyRevampedPlayScreen` in `PuppyMainScreensRevamp.kt` is **not** wired as the launcher Play screen; copying it would create a false visual comparison.

- 50dp original logo; native title/subtitle and horizontal four-currency wallet.
- Four care need pills (happiness, fullness, energy, cleanliness) with 3dp progress tracks.
- 365dp tap stage with 30dp radius; 258dp circular portrait holder and 238dp original puppy art; safe-width clamping on narrow displays.
- **Only the portrait** accepts taps, matching Kotlin, not the entire stage.
- Kotlin `formatV6` exact K/M/B/T decimal thresholds. Label displays native base `clickPower`; game accounting still includes earned care and 10-tap bonus.
- Kotlin idle bob and 0.89 tap press scale, vibration toggle, 2.75-second ticket overlay. Animation tuning is source-based but not yet screenshot-verified.
- Display existing cooldown state when present and refuse taps during it; **PupEye detection itself is not ported**. Guest mode does not manufacture trusted enforcement events.
- Remove extra Rewards card/shortcut grid from the Play screen; temporary Rewards Hub access moved to Care → Daily, with Gacha and Casino reachable there.

### Part 1 acceptance evidence still needed
- Android phone captures at native 360×800 (and Galaxy A17 5G size), large text, shorter/narrow displays, both themes where supported. Pixel-align Kotlin and React screenshots with the same save, artwork and density.
- Verify hardware back navigation, portrait tap target, screen-reader labels, 120Hz/animation reduced motion, ticket animation and Android lifecycle resume.
- Verify final APK's bundled JS and actual device launch. Passing source or unit tests does **not** imply pixel-perfect 1:1 parity.

**Next step:** Rebuild and test **Part 2 — Care & Daily** from `V6CareAndDaily`. No automatic production merge.
