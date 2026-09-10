from pathlib import Path
import unittest

from tools.patch_puppy_exchange import patch_roster_screen


ROSTER_SOURCE = Path("app/src/main/java/com/harleytg/puppyclicker/PuppyRosterScreen.kt")


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
        self.assertIn("size = if (compact) 64.dp else 90.dp", patched)
        self.assertIn("size = if (compact) 58.dp else 68.dp", patched)
        self.assertIn("if (compact) 8.dp else 12.dp", patched)


if __name__ == "__main__":
    unittest.main()
