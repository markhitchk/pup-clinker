# React Native Android parity audit

**Target:** Full Kotlin/Compose Puppy Clicker replacement in React Native, with zero WebViews.
**Source:** `../App/` native Android, package `com.harleytg.puppyclicker`.
**Current output:** `Mobile/` React Native **guest-only preview** (`com.harleytg.puppyclicker.preview`).
**Release verdict:** **NOT READY**. No production merges, upload-signing or account-state migration.

Status vocabulary: **Implemented guest** = screens/logic usable in local preview only. **Partial** = demonstrable but incomplete vs Kotlin. **Blocked** = needs original authoritative server or sensitive release prerequisites.

| Android capability | React Native status | Source / gap |
| --- | --- | --- |
| Real React Native UI, not browser shell | Implemented guest | `Mobile/App.js`; five native React tabs |
| Six onboarding stages | Implemented guest | `PuppyOnboardingModel.kt`; account setup and notification permissions are intentionally simulated/disabled |
| Play, taps, combo, care bonus and base V7 currencies | Partial | `PuppyEconomyV7.kt`; remaining authoritative server/anti-bot parity |
| Per-puppy feeding/play/wash/rest/cuddle, offline decay | Implemented guest | `PuppyCareSystem.kt`; clock capped to 240 min |
| XP, level curve, nine achievement milestones | Implemented guest | `PuppyProgression.kt`, `PuppyAchievementsV6.kt`; guest rewards not official XP |
| Shop, upgrade tickets, Pup Coins, accessories | Partial | Android pricing, rarity tickets and rare items need broader golden-fixture coverage |
| Prestige skill tree, permanent unlocks | Partial | Source levels and reward edge cases need complete fixture coverage |
| V1/V2/Discord portraits | Implemented guest | All 30 source PNGs bundled offline, but locked characters remain unclaimable without authorization |
| Dynamic V3+ streamed art and roster manifests | Not implemented | `DynamicPuppyRoster.kt`; must validate/cap manifests and asset hashes |
| Gacha with free-puppy restriction, 2,500 Treats/1 Common Ticket | Implemented guest | `PuppyGacha.kt`; protected/redeem-only skins never randomly granted; source machine animation not 1:1 |
| Slots/Roulette/Scratchers/Plinko/Wheel | Partial | Source-derived outcomes, visual game scenes and transaction authority incomplete |
| Blackjack 52-card deck, natural 3:2, dealer stand 17, split/double | Implemented guest | `PuppyBlackjackEngine.kt`; up to 3 hands, no native animation/art yet |
| Blackjack crash-safe guest wager state | Implemented guest | One persisted guest `casinoRound` with deck uniqueness validation; not server-side settlement |
| Daily reward and park activity | Partial | Source calendar/time policy, daily tasks, monthly/event rewards still incomplete |
| Owned roster/favorites/search | Partial | Local V1/V2; source categorization, dynamic groups, reorder, role permissions remain |
| Player profile, username, settings | Partial | Guest profile exists; native owner/operator/developer/account menus and preferences missing |
| Discord OAuth and linked original Pup Account | Blocked | `DiscordSignupAuth.kt`, production callback flow and device identification; cannot impersonate original account |
| Supabase authoritative cloud saves, one device | Blocked | `PupAccountCloudSave.kt`, revision reconciliation and original data mapping; **never upload guest state** |
| PupEye device binding, bans, alerts, appeals and audit | Blocked | `SupabasePupEyeClient.kt` and server authority; anti-cheat must be authoritative |
| Puppy Codes, code-backed rewards and redemption history | Blocked | `PuppyCodeRewards.kt`; ownership and anti-abuse server validation necessary |
| Player-to-player Exchange/trades/gifts | Blocked | `PuppyExchange*.kt`; server ownership ledger and atomic escrow required |
| Native notification permission, inbox, channels | Not implemented | `PuppyAttentionNotifications.kt`, onboarding permissions; guest preview only saves preference |
| Android home widget, background scheduler, haptics | Not implemented | `PuppyHomeWidgetProvider.kt`, haptics and platform UI |
| Source PNGs, 1:1 casino artwork, adaptive layouts | Partial | Core sprites bundled; screen-by-screen screenshot regression not performed |
| Old Kotlin install migration and compatibility | Blocked | Encrypted/attested Android save and official signing identity; do not uninstall original |
| Google Play signed AAB on release track | Blocked | Upload signing key, `versionCode > 34`, target API 36, Play internal testing, crash-free release |

## Release gates — all must pass before original package cutover

1. **Signed identity:** build `com.harleytg.puppyclicker` from the original verified Play upload key; verify certificates before installation. No unsigned/debug update.
2. **Data migration:** test fresh install, in-place upgrade, existing player restore and replay/rollback across the original Kotlin encrypted save and authoritative Supabase account. Detect partial migrations without silent data loss.
3. **Trust/authorization:** server-confirmed Discord account, same device/PupEye identity, role-gated rewards and bans enforced before the player can access trusted progression.
4. **Feature parity:** original game screens, economics, all six casino games, reward/roster, exchange, code/redemption, onboarding, settings, notifications and widget flows function under Kotlin-driven golden tests.
5. **Visual parity:** screenshots on an Android 16 phone, a narrow handset, landscape and tablet; no blank screens, missing images or clipped controls. Accessibility/large text included.
6. **Play rollout:** build and inspect release AAB, test through Play internal track with crash/ANR telemetry, confirm install and rollback safety.

## Next executable work

- P0: Map Supabase/PupEye production schema and design a migration endpoint; no client-authoritative reward uploads.
- P0: Recovery-safe conversion of the original signed Android save, preserving installed players.
- P1: Port remaining original screen UI and reward/roster/casino animations against golden screenshots.
- P1: Production-grade server transaction ledger for casino/Gacha/code/Exchange, duplicate-free payouts.
- P1: Notification channel, home-widget, haptic and accessibility parity.
- P2: Full Play signing and internal QA gate; only then target production release.
