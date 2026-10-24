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
| **2** | **Care & Daily, five care actions, per-puppy care, Park, daily claims** | **React screens rebuilt; automated Kotlin contract tests added; Android-device visual and lifecycle verification outstanding.** Source: `V6CareAndDaily`, `V6CarePanel`, `V6DailyPanel`, `PuppyCareSystem.kt`, `PuppyClickerV6ViewModel.kt`, `PuppyMonthlyRewards.kt`; code: `Mobile/src/engine/careV6.js`, `Mobile/App.js`; tests: `Mobile/tests/careV6.test.js`. |
| **3** | **Shop, tickets, accessories, Puppy Codes and Prestige** | **Kotlin Shop and Prestige views rebuilt in React; economy transaction guards and regression tests added; CI/device visual verification pending.** Reference: `V6Shop`, `V6CookieUpgradeCard`, `V6TicketUpgradeCard`, `V6TicketInventory`, `PuppyPupCoinShop`, `V6PrestigeCenter`, `PuppyEconomyV7`, `PuppyClickerV6ViewModel`; implementation: `Mobile/src/engine/shopV6.js`, `Mobile/src/engine/game.js`, `Mobile/App.js`; tests: `Mobile/tests/shopV6.test.js`. Puppy Code redemption is disabled in this guest preview. |
| **4** | **Puppy Collection V1/V2, source artwork, mechanical Gacha, guest unlocks, favorites** | **Implemented in code; automated tests added; device screenshots / APK verification pending.** 29 Kotlin legacy IDs, descriptions and locks, original five PNG machine pieces, staged turn/drop/open/reveal, two free TEST-manifest Gacha puppies with original PNGs; advanced V3+ browser and favorites retained. Code: `Mobile/src/engine/rosterV6.js`, `Mobile/src/engine/gachaV6.js`, `Mobile/src/assets/gacha.js`, `Mobile/App.js`; tests: `Mobile/tests/rosterV6.test.js` and `Mobile/tests/gacha.test.js`. Restricted ownership, role unlocks, and free *future* V3+ Gacha remain unported. |
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

## Part 2 — Care & Daily evidence and remaining differences

- The launched Kotlin screen is `V6CareAndDaily` (not a reconstructed generic Care menu). React uses the original 50dp logo header, Care/Daily selector, 125dp active portrait, five 6dp meters, action layout **Feed + Play / Rest + Groom / Cuddle**, plus a care-information panel.
- Action guards and values follow `PuppyClickerV6ViewModel`: Feed costs 20 Treats, Play costs 1 Bone and 12 energy, Groom costs 10 Treats, Rest and Cuddle are free. The **Kotlin UI text claims every care action awards one Bone, but Kotlin's actual care methods do not credit Bones**. React follows transaction behavior and does not advertise a false Bone payout; this textual difference requires an explicit upstream Kotlin UI reconciliation before declaring strict pixel-level parity.
- React daily screen has the source-inspired streak banner, next daily gift amount using `level` and streak formula, validated monthly goals with claim safety, and a 60-second / 20-energy Dog Park adventure with exactly-once guest claim. Kotlin's `V6DailyPanel` currently shows four hard-coded daily task descriptions while its ViewModel settles against `PuppyMonthlyRewards`; React renders the **validated schedule actually used for settlement** instead of showing potentially wrong tasks.
- Daily gift day is resolved inside the reducer from the action timestamp, like Kotlin resolving `LocalDate.now()`, rather than accepting a UI-supplied epoch day. This prevents simple repeated-claim spoofing by supplying a different `epochDay` parameter. It is not cryptographically secure; the preview is guest-only.
- Per-puppy local care decay/restore already exists in `Mobile/src/engine/careDecay.js`; it remains the same isolated guest approximation of `PuppyCareSystem` and is covered by `Mobile/tests/careDecay.test.js`. It does not make the original encrypted Kotlin save accessible.
- Android native Dog Park push notification, AFK inbox, Supabase/PupEye secure accounts, and real-device visual/interaction comparison are **not completed**. The temporary `Preview · Rewards Hub` entry under Daily retains access to other guest preview content absent from the launched Kotlin Care tab.

**Part 3 next:** Shop, Tickets, Pup Coins, and Prestige. Never grant protected Puppy Codes from client-side guest logic.

## Part 3 — Shop & Prestige evidence

- Ported the launched `V6Shop` three-tab structure; **Pup Coins** now contains only the actual Kotlin Pup Coin balance, owned/equippable accessories and purchases of rarity-specific Upgrade Tickets. It no longer mistakenly shows Ticket Upgrades from another tab. **Upgrades** contains only normal Cookie upgrades; **Tickets** contains ticket inventory, rarity and owned-ticket requirements, Treat/Bone fees and the separate Upgrade purchase action.
- Ported the exact `V6PrestigeCenter` source text hierarchy, original PrestigeSkill names/descriptions, 50,000-lifetime-Treat threshold, 7dp progress track, level 5 cap and two-stage confirmation dialog. Active Blackjack and unfinished Scratcher rounds must finish first.
- Price calculations share the source-derived `PuppyEconomyV7` rules (155% or 135% next-cost growth, Smart Shopper 5%-per-level discount, ticket rarity purchase steps of +25% of the base price until 6×, and ticket discount at Ticket Sense levels 2/4). React guest purchase requests are resolved against the canonical upgrade allowlist instead of accepting mutable action prices/effects.
- Kotlin has a hard **9,999 Upgrade Tickets per rarity** cap. React now applies it to purchases and guest-save normalization; stock-cap purchase attempts cannot charge Pup Coins.
- Prestige resets this run's Treats, Bones, upgrade levels and Upgrade Tickets, active care meters, daily streak/tasks and current Casino run stats; keeps Pup Coins, Casino Chips, roster, accessories, redeemed code IDs, bond, purchased Shop ticket price history and permanent skill levels. Current local calendar day is used for the reset baseline, matching the Kotlin ViewModel.
- **Puppy Codes:** the source Kotlin app has on-device redemption. The React guest build does not pretend to redeem codes or grant protected puppies. Code redemptions require separate migration of original code history/entitlements and save-integrity safety before activation.
- Automated contracts in `Mobile/tests/shopV6.test.js` cover exact sections, rarity symbols, pricing, inventory bounds, forged upgrade requests, accessory permanence, the prestige reset and blocked prestige mid-Casino. **Not yet phone-screenshot-verified** and not a release approval.

**Part 4 next:** Puppy Roster, real bundled V1/V2/V3 artworks, Gacha, favorites and verified ownership. Production Supabase/PupEye accounts are still a separate dependency.

## Part 4 — Collection and Gacha source evidence

- **Launched roster:** Kotlin `V6Pups` uses `V6PuppyRosterSection` for 18 V1 and 11 V2 stable IDs, 86dp portraits, a title/copy row, a lock scrim, per-puppy selection, 18-character rename, and owned accessories. React's default Collection follows that order and logic, using metadata directly generated from `PuppyCatalogV6.kt` and `GameViewModel.kt` (`rosterV6.js`).
- The original 29 legacy PNG assets already existed in `Mobile/assets/v1`, `Mobile/assets/v2` and `Mobile/assets/discord_rewards`; React now renders the locked scrim, puppy emoji badge and selected accessory. Dynamic manifest-backed V3+ art remains in a **separate read-only advanced browser** with favorites and grouping; those extra browse controls are not part of Kotlin's simple `V6Pups` and are not called pixel-identical.
- **Gacha machine:** Five byte-identical Kotlin PNG files were re-referenced from `App/app/src/main/res/drawable` into `Mobile/assets/gacha`: machine, knob, closed capsule, opening tilt and opened capsule. React's `gachaV6.js` uses Kotlin's 1024:1536 scene, dial at 1002/1536, capsule at 1246/1536, 620ms turn, 640ms open, plus 300ms reveal delay. Game settlement occurs at the start of the pull, with **Open Capsule** as a separate step; the overlay shows the actual won puppy artwork and Use/Another/Close actions.
- **Kotlin Gacha eligibility:** The four built-in free V1 pups start owned in guest saves. `assets/test/manifest.json` additionally contains two explicitly free, non-redeem-only TEST pups (`test_v1_crash_dummy` and `test_v2_crash_dummy`). Those two original images are now packaged and can be won locally; after both are unlocked, the all-owned duplicate fallback applies. Other V1/V2 restrictions remain excluded; any **future** v3+ free manifest entries are browse-only until roster/game-save and backend entitlement reconciliation exists. Source Kotlin dynamically consumes validated manifests; full dynamic Gacha parity is not yet done.
- **Ownership security:** Existing Discord reward pups, special code pups, staff/developer pups and online entitlement-only pups cannot be awarded by these local guest Gacha rolls. Guest saves are not authoritative or secure and cannot be imported into production Supabase/PupEye. Role/Discord verification, save migration and permanent cloud ownership require a later secure part.
- **Native verification still pending:** Compare Kotlin/React screenshot pixel geometry, original animated stage art, large fonts, narrow devices, loaded portrait assets, correct reset/reentry, saved unlock persistence and accessibility. GitHub Actions pending is not a passing APK or signed Play build.

**Part 5 next:** rebuild the six Casino games in individual subparts, starting with Blackjack. Keep Kotlin `App/` and production data intact.
