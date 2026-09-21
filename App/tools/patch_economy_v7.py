#!/usr/bin/env python3
"""Final Economy V7 generated-source guard.

Business logic remains in canonical Kotlin. This pass only normalizes the one roster selector that
legacy layout patches may rewrite and then asserts that the finished generated source still carries
the Economy V7 invariants.
"""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def require_contains(source: str, needle: str, label: str) -> None:
    if needle not in source:
        raise RuntimeError(f"Economy V7 generated-source guard missing {label}: {needle}")


def require_absent(source: str, needle: str, label: str) -> None:
    if needle in source:
        raise RuntimeError(f"Economy V7 generated-source guard found stale {label}: {needle}")


def main(root: Path) -> None:
    package = root / PACKAGE

    roster = package / "PuppyRosterScreen.kt"
    roster_source = roster.read_text(encoding="utf-8")
    roster_source = roster_source.replace(
        "PuppyClickerV6ViewModel.ACCESSORIES.forEach { accessory ->",
        "PuppyClickerV6ViewModel.ACCESSORIES.filter { it in state.ownedAccessories }.forEach { accessory ->",
    )
    roster.write_text(roster_source, encoding="utf-8")

    vm = (package / "PuppyClickerV6ViewModel.kt").read_text(encoding="utf-8")
    transactions = (package / "PuppyCasinoTransactions.kt").read_text(encoding="utf-8")
    casino_hub = (package / "PuppyCasinoHub.kt").read_text(encoding="utf-8")
    main_ui = (package / "PuppyMainScreensRevamp.kt").read_text(encoding="utf-8")
    notifications = (package / "PuppyNotificationHistory.kt").read_text(encoding="utf-8")
    inbox = (package / "PuppyNotificationInboxUi.kt").read_text(encoding="utf-8")

    require_contains(vm, "val bones: Long = 0", "Bones state")
    require_contains(vm, "val pupCoins: Long = 0", "Pup Coins state")
    require_contains(vm, "val casinoChips: Long = 0", "Casino Chips state")
    require_contains(vm, "val activeBonus: Int = 0", "active bonus state")
    require_contains(vm, "PuppyEconomyV7.TICKET_DROP_DENOMINATOR", "rare tap ticket roll")
    require_contains(vm, "fun convertTreatsToCasinoChips", "one-way chip conversion")
    require_contains(vm, "fun claimSystemReward", "idempotent reward claim")
    require_absent(
        vm,
        "PuppyAppRuntime.isForeground && current.autoPerSecond > 0",
        "passive V6 Treat production",
    )
    require_absent(vm, "nextTotalTaps % 5L == 0L", "five-tap ticket cadence")

    require_contains(
        transactions,
        "before.copy(casinoChips = before.casinoChips - wagerTreats)",
        "Casino Chip initial wager",
    )
    require_contains(
        transactions,
        "state = before.copy(casinoChips = nextChips)",
        "Casino Chip settlement/refund",
    )
    require_absent(
        transactions,
        "lifetimeTreats = nextLifetime",
        "casino lifetime-Treat mutation",
    )

    require_contains(casino_hub, '"Casino Chip Wallet"', "Casino Chip wallet label")
    require_contains(casino_hub, "convertTreatsToCasinoChips", "Treat-to-Chip exchange")
    require_absent(casino_hub, '"Treat Wallet"', "Treat Wallet casino label")
    require_absent(casino_hub, "No chips or second wallet", "old casino wallet copy")

    require_contains(main_ui, '"🪙 Pup Coin Shop"', "Pup Coin Shop")
    require_contains(main_ui, "state.bones", "wallet Bones")
    require_contains(main_ui, "state.pupCoins", "wallet Pup Coins")
    require_contains(main_ui, "state.casinoChips", "wallet Casino Chips")
    require_absent(main_ui, '"Per sec"', "passive wallet stat")
    require_absent(main_ui, "Every 5th accepted tap", "old ticket cadence copy")

    require_contains(notifications, "SYSTEM_REWARD", "system reward type")
    require_contains(notifications, "rewardCurrency", "system reward currency")
    require_contains(notifications, "markRewardClaimed", "system reward claimed state")
    require_contains(inbox, "item.hasClaimableReward", "inbox claim button")

    for name in (
        "PuppySlotsUi.kt",
        "PuppyRouletteUi.kt",
        "PuppyBlackjackUi.kt",
        "PuppyPlinkoUi.kt",
        "PuppyScratchersUi.kt",
        "PuppyLuckyWheelUi.kt",
    ):
        game = (package / name).read_text(encoding="utf-8")
        require_contains(game, "state.casinoChips", f"{name} Casino Chip balance")
        require_absent(game, '"Treat Wallet"', f"{name} Treat Wallet label")

    print("Puppy Clicker Economy V7 generated-source guard complete")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_economy_v7.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
