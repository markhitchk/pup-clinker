# React Android settings restoration

Goal: Restore missing Kotlin settings controls, themed hardware cursor and usable notification inbox on feat/react-native-full-android.
Source: PuppySettingsUi.kt, PuppyUiPreferences.kt, ui/theme/Theme.kt and PuppyPointer.kt, plus the four supplied screenshots.
Constraints: App/ remains the reference; preserve preview package and existing guest save; do not claim cloud/account or Play release parity.

1. Add failing preference persistence, validation, reset, theme, motion and notification lifecycle tests. Implement validated preferences in the existing guest save/reducer.
2. Replace placeholder appearance/gameplay/notification controls; retain source accordion order, add profile editing, birthday, cache refresh and reset controls. Apply palette/contrast/scale and effective motion across existing screens.
3. Copy all original 64px cursor assets; register an Android native view through a repeatable Expo config plugin. Use native pointer icons with the original 16px hotspot; wrap root and modal surfaces.
4. Replace notification placeholder with filtered, grouped persistent inbox and duplicate-safe reward navigation. Integrate Android local permission/channel/test/reminders, honoring category preferences. No invented historical Kotlin notifications or AFK currency grants.
5. Run full Node suite, Expo export/prebuild, inspect native generated registration, and review the diff. Commit and push the requested existing branch and inspect preview APK workflow.

Review focus: malformed preferences; older save compatibility; inaccessible low-contrast accents; reduced-motion precedence; duplicate notification/reward actions; denied OS notification permission; modal hardware pointer; offline roster refresh; Back closing expanded settings.
