from pathlib import Path
import unittest

from tools.patch_puppy_exchange import patch_roster_screen


ROSTER_SOURCE = Path("app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt")
SETTINGS_SOURCE = Path("app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt")


class CompactRosterPatchTest(unittest.TestCase):
    def test_generated_roster_uses_compact_phone_layout(self) -> None:
        source = ROSTER_SOURCE.read_text(encoding="utf-8")
        patched = patch_roster_screen(source)

        self.assertIn(
            "val compactRoster = LocalConfiguration.current.screenWidthDp < 600",
            patched,
        )
        self.assertIn(
            ".padding(\n                horizontal = if (compactRoster) 12.dp else 16.dp,\n                vertical = if (compactRoster) 6.dp else 10.dp\n            )",
            patched,
        )
        self.assertIn("compact = compactRoster", patched)

    def test_generated_roster_compacts_only_the_requested_top_controls(self) -> None:
        source = ROSTER_SOURCE.read_text(encoding="utf-8")
        patched = patch_roster_screen(source)

        self.assertIn("modifier = Modifier.height(if (compactRoster) 34.dp else 48.dp)", patched)
        self.assertIn("modifier = Modifier.fillMaxWidth().height(if (compactRoster) 48.dp else 56.dp)", patched)
        self.assertIn("CompactRosterSortFavorites(", patched)
        self.assertIn("CompactRosterCategories(", patched)
        self.assertIn("height = if (compactRoster) 34.dp else 40.dp", patched)

    def test_selected_puppy_becomes_a_slim_horizontal_strip_on_phone(self) -> None:
        source = ROSTER_SOURCE.read_text(encoding="utf-8")
        patched = patch_roster_screen(source)

        self.assertIn("size = if (compact) 52.dp else 90.dp", patched)
        self.assertIn("CompactSelectedPuppyActions(", patched)
        self.assertIn("modifier = Modifier.heightIn(min = if (compact) 68.dp else 0.dp)", patched)
        self.assertIn("size = if (compact) 58.dp else 68.dp", patched)


class SettingsAccountCardTest(unittest.TestCase):
    def test_complete_account_profile_moves_to_top_card(self) -> None:
        source = SETTINGS_SOURCE.read_text(encoding="utf-8")

        account = source.index("AccountProfileCard(")
        appearance = source.index('SettingsSectionCard("appearance"')
        self.assertLess(account, appearance)
        self.assertNotIn('SettingsSectionCard("account", "👤", "Account & Profile"', source)
        self.assertIn("private fun AccountProfileCard(", source)
        self.assertIn("AccountProfileSettings(vm, ui)", source)

    def test_top_account_card_shows_identity_summary(self) -> None:
        source = SETTINGS_SOURCE.read_text(encoding="utf-8")

        self.assertIn("PuppyPlayerIdentity.playerId(context)", source)
        self.assertIn("PuppyPlayerIdentity.friendCode(context)", source)
        self.assertIn('Text("Device Bound"', source)
        self.assertIn('Text("Account & Profile"', source)
        self.assertIn('Text("Copy"', source)


if __name__ == "__main__":
    unittest.main()
