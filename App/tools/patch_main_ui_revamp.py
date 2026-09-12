#!/usr/bin/env python3
"""Apply the final Puppy Clicker main-screen visual revamp to generated sources.

This runs after the established roster, navigation, Exchange, Settings/onboarding,
and Puppy Code compatibility patches. It deliberately changes only presentation
routing and final roster styling so gameplay/data behavior stays in the existing
models and view models.
"""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(
            f"Main UI revamp patch {label}: expected 1 match, found {count}"
        )
    return text.replace(old, new, 1)


def patch_activity(source: str) -> str:
    source = replace_once(
        source,
        "                    V6Tab.PLAY -> V6Play(state, vm)\n",
        """                    V6Tab.PLAY -> PuppyRevampedPlayScreen(
                        state = state,
                        vm = vm,
                        onOpenRoster = { tab = V6Tab.ROSTER }
                    )
""",
        "Play route",
    )
    source = replace_once(
        source,
        "                    V6Tab.CARE -> V6CareAndDaily(state, vm)\n",
        "                    V6Tab.CARE -> PuppyRevampedCareScreen(state, vm)\n",
        "Care route",
    )
    source = replace_once(
        source,
        "                    V6Tab.SHOP -> V6Shop(state, vm)\n",
        "                    V6Tab.SHOP -> PuppyRevampedShopScreen(state, vm)\n",
        "Shop route",
    )
    # Rewards already routes through PuppyRewardsHub. The source-level hub now
    # delegates to PuppyRevampedRewardsScreen so the established Prestige route
    # stays intact without duplicating navigation logic.
    return source


def patch_roster(source: str) -> str:
    source = replace_once(
        source,
        """                Text(
                    "Puppy Roster",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )""",
        """                Text(
                    "Puppy Roster",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )""",
        "roster title",
    )
    source = replace_once(
        source,
        """                Text(
                    "Browse every streamed puppy in the live roster.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compactRoster) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )""",
        """                Text(
                    "Browse every streamed puppy in the live roster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compactRoster) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )""",
        "roster subtitle",
    )
    source = replace_once(
        source,
        """            IconButton(
                onClick = onOpenExchange,
                modifier = Modifier.semantics { contentDescription = "Open Puppy Exchange" }
            ) {
                Text("🎁", fontSize = 22.sp)
            }""",
        """            OutlinedButton(
                onClick = onOpenExchange,
                modifier = Modifier.semantics { contentDescription = "Open Puppy Exchange" },
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("🎁 Exchange", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }""",
        "roster Exchange action",
    )
    source = replace_once(
        source,
        """            leadingIcon = { Text("🔍", fontSize = if (compactRoster) 14.sp else 16.sp) }
        )""",
        """            leadingIcon = { Text("🔍", fontSize = if (compactRoster) 14.sp else 16.sp) },
            shape = RoundedCornerShape(18.dp)
        )""",
        "roster search shape",
    )
    source = replace_once(
        source,
        "        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)\n",
        "        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)\n",
        "selected puppy card tone",
    )
    return source


def main(root: Path) -> None:
    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    activity.write_text(
        patch_activity(activity.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    roster = root / PACKAGE / "PuppyRosterScreen.kt"
    roster.write_text(
        patch_roster(roster.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    print("Puppy Clicker main UI revamp integrated")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_main_ui_revamp.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
