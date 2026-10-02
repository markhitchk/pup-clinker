from pathlib import Path
import unittest

from tools.patch_puppy_exchange import patch_roster_screen as patch_exchange_roster_screen
from tools.patch_compact_roster_settings import (
    patch_roster_screen as patch_compact_roster_screen,
    patch_settings_screen,
)


ROSTER_SOURCE = Path("app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt")
SETTINGS_SOURCE = Path("app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt")


def final_roster_source() -> str:
    source = ROSTER_SOURCE.read_text(encoding="utf-8")
    return patch_compact_roster_screen(patch_exchange_roster_screen(source))


def final_settings_source() -> str:
    source = SETTINGS_SOURCE.read_text(encoding="utf-8")
    return patch_settings_screen(source)


class CompactRosterPatchTest(unittest.TestCase):
    def test_generated_roster_uses_compact_phone_layout(self) -> None:
        patched = final_roster_source()

        self.assertIn(
            "val compactRoster = LocalPuppyViewport.current.isCompact",
            patched,
        )
        self.assertIn(
            ".padding(\n                start = if (compactRoster) 12.dp else 16.dp,\n                top = 2.dp,\n                end = if (compactRoster) 12.dp else 16.dp,\n                bottom = 0.dp\n            )",
            patched,
        )
        self.assertIn("compact = compactRoster", patched)

    def test_generated_roster_compacts_only_the_requested_top_controls(self) -> None:
        patched = final_roster_source()

        self.assertIn("modifier = Modifier.height(if (compactRoster) 34.dp else 48.dp)", patched)
        self.assertIn("modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)", patched)
        self.assertIn("CompactRosterSortFavorites(", patched)
        self.assertIn("CompactRosterCategories(", patched)
        self.assertIn("height = if (compactRoster) 34.dp else 40.dp", patched)

    def test_selected_puppy_becomes_a_slim_horizontal_strip_on_phone(self) -> None:
        patched = final_roster_source()

        self.assertIn("size = if (compact) 52.dp else 90.dp", patched)
        self.assertIn("CompactSelectedPuppyActions(", patched)
        self.assertIn("modifier = Modifier.heightIn(min = if (compact) 68.dp else 0.dp)", patched)
        self.assertIn("size = if (compact) 58.dp else 68.dp", patched)


class SettingsOneScreenCardsTest(unittest.TestCase):
    def test_settings_use_one_screen_expandable_cards(self) -> None:
        source = final_settings_source()

        self.assertNotIn("SettingsDestination", source)
        self.assertNotIn("private fun SettingsHome(", source)
        self.assertIn('var expandedSection by rememberSaveable', source)
        self.assertIn('Everything in one place. Tap a card to expand its settings.', source)
        self.assertIn('SettingsLabel("ACCOUNT")', source)
        self.assertIn('SettingsLabel("APP & EXPERIENCE")', source)
        self.assertIn('SettingsLabel("GAME")', source)
        self.assertIn('SettingsLabel("SUPPORT")', source)
        self.assertIn('SettingsLabel("ADVANCED")', source)

        expected_cards = (
            "Profile",
            "Discord",
            "Appearance",
            "Notifications",
            "Privacy & Data",
            "Gameplay",
            "Data Management",
            "Support & Reports",
            "Puppy Roster",
            "PupEye Protection",
            "Online Assets",
            "About Puppy Clicker",
        )
        for title in expected_cards:
            self.assertIn(f'title = "{title}"', source)
        self.assertNotIn('title = "Import / Export"', source)
        self.assertNotIn(".pupsave", source)

        self.assertIn("ProfileSettings(vm, ui)", source)
        self.assertIn("DiscordSettings(state, vm)", source)
        self.assertIn("AppearanceSettings(ui)", source)
        self.assertIn("NotificationSettings()", source)
        self.assertIn("PuppyPrivacyDataSettings(ui)", source)
        self.assertIn("GameplaySettings(state, vm)", source)
        self.assertIn("SaveDataSettings(vm)", source)
        self.assertIn("PuppySupportReportSettings(ui)", source)
        self.assertIn("PuppyRosterSettings()", source)
        self.assertIn("PupEyeSettings(state)", source)
        self.assertIn("OnlineAssetSettings()", source)

    def test_profile_and_identity_remain_card_based_inside_settings(self) -> None:
        source = final_settings_source()

        self.assertIn("private fun ProfileDropdownCard(", source)
        self.assertIn('title = "Account & Identity"', source)
        self.assertIn("PuppyPlayerIdentity.publicPlayerId(context)", source)
        self.assertIn("PuppyPlayerIdentity.publicFriendCode(context)", source)
        self.assertIn('title = "Progress & XP"', source)
        self.assertIn('title = "Achievements"', source)
        self.assertIn('title = "Birthday"', source)

    def test_settings_cards_show_summary_and_expand_inline(self) -> None:
        source = final_settings_source()

        self.assertIn("private fun SettingsSectionCard(", source)
        self.assertIn("subtitle: String", source)
        self.assertIn("AnimatedVisibility(", source)
        self.assertIn("targetValue = if (expanded) 90f else 0f", source)
        self.assertIn("expandedSection = if (expandedSection == section) null else section", source)


if __name__ == "__main__":
    unittest.main()
