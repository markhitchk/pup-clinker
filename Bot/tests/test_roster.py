"""Offline unit tests; no Discord token or network access required."""

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from roster import load_roster, lookup_puppy  # noqa: E402


class RosterTests(unittest.TestCase):
    def test_real_manifests_and_website_links(self):
        root = Path(__file__).resolve().parents[2] / "assets"
        puppies = load_roster(root)
        self.assertGreaterEqual(len(puppies), 30)
        self.assertEqual(lookup_puppy(puppies, "v2_rory").number, 29)
        self.assertEqual(lookup_puppy(puppies, "Dev Pup"), None)  # ambiguous: require ID
        self.assertIsNone(lookup_puppy(puppies, "invalid"))
        discord_pup = lookup_puppy(puppies, "v2_discord_pup")
        self.assertIsNone(discord_pup.number)
        self.assertIn("discord_rewards/v2_discord_pup.png", discord_pup.image_url)
        self.assertEqual(discord_pup.website_url,
                         "https://puppy-clicker.vercel.app/puppies?pup=v2_discord_pup")

    def test_reject_path_traversal_and_duplicate_ids(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for folder in ("v1", "v2", "discord_rewards"):
                (root / folder).mkdir()
                (root / folder / "manifest.json").write_text(json.dumps({"puppies": []}))
            bad = [{"number": 1, "name": "Test", "asset_id": "v1_test", "filename": "../secret.png"}]
            (root / "v1" / "manifest.json").write_text(json.dumps(bad))
            with self.assertRaises(ValueError):
                load_roster(root)
            bad[0]["filename"] = "v1_test.png"
            (root / "v1" / "manifest.json").write_text(json.dumps(bad + bad))
            with self.assertRaises(ValueError):
                load_roster(root)


if __name__ == "__main__":
    unittest.main()
