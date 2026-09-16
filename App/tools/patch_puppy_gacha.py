#!/usr/bin/env python3
"""Integrate Puppy Gacha into the final generated V6 Kotlin sources."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Puppy Gacha patch {label}: expected 1 match, found {count}")
    return source.replace(old, new, 1)


def patch_view_model(source: str) -> str:
    if "internal fun pullPuppyGacha(payment: PuppyGachaPayment)" in source:
        return source

    anchor = "    fun setPuppyStyle(id: String) {"
    if anchor not in source:
        raise RuntimeError("Puppy Gacha patch ViewModel anchor not found")

    method = """    @Synchronized
    internal fun pullPuppyGacha(payment: PuppyGachaPayment): PuppyGachaPullResult {
        val current = _state.value
        val styles = DynamicPuppyRoster.groups.value.flatMap { it.puppies }
        val candidates = PuppyGachaEngine.pullPool(
            styles = styles,
            unlocked = current.unlockedPuppies
        )
        if (candidates.isEmpty()) {
            return PuppyGachaPullResult(
                success = false,
                payment = payment,
                failure = PuppyGachaFailure.NO_ELIGIBLE_PUPPIES
            )
        }

        val commonTickets = current.ticketInventory[TicketRarity.COMMON] ?: 0
        when (payment) {
            PuppyGachaPayment.TREATS -> {
                if (current.treats < PuppyGachaEngine.COST_TREATS) {
                    return PuppyGachaPullResult(
                        success = false,
                        costTreats = PuppyGachaEngine.COST_TREATS,
                        payment = payment,
                        failure = PuppyGachaFailure.NOT_ENOUGH_TREATS
                    )
                }
            }
            PuppyGachaPayment.COMMON_TICKET -> {
                if (commonTickets < PuppyGachaEngine.COST_COMMON_TICKETS) {
                    return PuppyGachaPullResult(
                        success = false,
                        costTickets = PuppyGachaEngine.COST_COMMON_TICKETS,
                        payment = payment,
                        failure = PuppyGachaFailure.NOT_ENOUGH_TICKETS
                    )
                }
            }
        }

        val selected = PuppyGachaEngine.select(
            candidates = candidates,
            roll = Random.nextInt(candidates.size)
        ) ?: return PuppyGachaPullResult(
            success = false,
            payment = payment,
            failure = PuppyGachaFailure.NO_ELIGIBLE_PUPPIES
        )
        val isNewUnlock = selected.id !in current.unlockedPuppies

        val nextInventory = if (payment == PuppyGachaPayment.COMMON_TICKET) {
            current.ticketInventory + (
                TicketRarity.COMMON to
                    (commonTickets - PuppyGachaEngine.COST_COMMON_TICKETS).coerceAtLeast(0)
            )
        } else {
            current.ticketInventory
        }

        _state.value = current.copy(
            treats = if (payment == PuppyGachaPayment.TREATS) {
                current.treats - PuppyGachaEngine.COST_TREATS
            } else {
                current.treats
            },
            ticketInventory = nextInventory,
            unlockedPuppies = current.unlockedPuppies + selected.id
        )
        saveState()

        return PuppyGachaPullResult(
            success = true,
            puppyId = selected.id,
            puppyName = selected.name,
            puppyEmoji = selected.emoji,
            costTreats = if (payment == PuppyGachaPayment.TREATS) {
                PuppyGachaEngine.COST_TREATS
            } else {
                0L
            },
            costTickets = if (payment == PuppyGachaPayment.COMMON_TICKET) {
                PuppyGachaEngine.COST_COMMON_TICKETS
            } else {
                0
            },
            payment = payment,
            isNewUnlock = isNewUnlock
        )
    }

"""
    return source.replace(anchor, method + anchor, 1)


def patch_activity(source: str) -> str:
    if "PuppyInternalDestination.GACHA -> PuppyGachaScreen(" not in source:
        casino = """                    PuppyInternalDestination.CASINO -> PuppyCasinoHub(
                        state = state,
                        vm = vm,
                        onBack = { internalDestination = null }
                    )"""
        gacha = casino + """
                    PuppyInternalDestination.GACHA -> PuppyGachaScreen(
                        state = state,
                        vm = vm,
                        onBack = { internalDestination = null }
                    )"""
        source = replace_once(source, casino, gacha, "Gacha internal destination")

    if "onOpenGacha = { internalDestination = PuppyInternalDestination.GACHA }" not in source:
        old = """                        onOpenCasino = { internalDestination = PuppyInternalDestination.CASINO }
                    )"""
        new = """                        onOpenCasino = { internalDestination = PuppyInternalDestination.CASINO },
                        onOpenGacha = { internalDestination = PuppyInternalDestination.GACHA }
                    )"""
        source = replace_once(source, old, new, "Rewards Gacha route")

    return source


def main(root: Path) -> None:
    view_model = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    view_model.write_text(
        patch_view_model(view_model.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    activity.write_text(
        patch_activity(activity.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    print("Puppy Gacha generated-source integration complete")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_puppy_gacha.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
