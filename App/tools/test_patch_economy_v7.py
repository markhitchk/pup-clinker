import tempfile
import unittest
from pathlib import Path

import patch_economy_v7 as patch


class EconomyV7PatchGuardTest(unittest.TestCase):
    def make_root(self, stale_passive: bool = False) -> Path:
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        package = root / patch.PACKAGE
        package.mkdir(parents=True)

        vm = """
val bones: Long = 0
val pupCoins: Long = 0
val casinoChips: Long = 0
val activeBonus: Int = 0
PuppyEconomyV7.TICKET_DROP_DENOMINATOR
fun convertTreatsToCasinoChips() = Unit
fun claimSystemReward() = Unit
"""
        if stale_passive:
            vm += "PuppyAppRuntime.isForeground && current.autoPerSecond > 0\n"
        (package / "PuppyClickerV6ViewModel.kt").write_text(vm, encoding="utf-8")

        (package / "PuppyCasinoTransactions.kt").write_text(
            """
before.copy(casinoChips = before.casinoChips - wagerTreats)
state = before.copy(casinoChips = nextChips)
""",
            encoding="utf-8",
        )
        (package / "PuppyCasinoHub.kt").write_text(
            """
"Casino Chip Wallet"
convertTreatsToCasinoChips
""",
            encoding="utf-8",
        )
        (package / "PuppyMainScreensRevamp.kt").write_text(
            """
"🪙 Pup Coin Shop"
state.bones
state.pupCoins
state.casinoChips
""",
            encoding="utf-8",
        )
        (package / "PuppyNotificationHistory.kt").write_text(
            """
SYSTEM_REWARD
rewardCurrency
markRewardClaimed
""",
            encoding="utf-8",
        )
        (package / "PuppyNotificationInboxUi.kt").write_text(
            "item.hasClaimableReward\n",
            encoding="utf-8",
        )
        (package / "PuppyRosterScreen.kt").write_text(
            "PuppyClickerV6ViewModel.ACCESSORIES.forEach { accessory ->\n",
            encoding="utf-8",
        )
        for name in (
            "PuppySlotsUi.kt",
            "PuppyRouletteUi.kt",
            "PuppyBlackjackUi.kt",
            "PuppyPlinkoUi.kt",
            "PuppyScratchersUi.kt",
            "PuppyLuckyWheelUi.kt",
        ):
            (package / name).write_text("state.casinoChips\n", encoding="utf-8")
        return root

    def test_guard_accepts_v7_and_filters_roster_accessories(self):
        root = self.make_root()
        patch.main(root)

        roster = (root / patch.PACKAGE / "PuppyRosterScreen.kt").read_text(encoding="utf-8")
        self.assertIn(
            "PuppyClickerV6ViewModel.ACCESSORIES.filter { it in state.ownedAccessories }",
            roster,
        )

    def test_guard_rejects_restored_passive_income(self):
        root = self.make_root(stale_passive=True)
        with self.assertRaisesRegex(RuntimeError, "passive V6 Treat production"):
            patch.main(root)


if __name__ == "__main__":
    unittest.main()
