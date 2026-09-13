#!/usr/bin/env python3
"""Normalize final generated screen fitment while preserving Android system-bar safety."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def rewrite(path: Path, replacements: list[tuple[str, str]]) -> None:
    source = path.read_text(encoding="utf-8")
    updated = source
    for old, new in replacements:
        updated = updated.replace(old, new)
    if updated != source:
        path.write_text(updated, encoding="utf-8")


def main(root: Path) -> None:
    compact_16 = ".padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)"
    legacy_16 = ".padding(horizontal = 16.dp, vertical = 4.dp)"

    for name in (
        "PuppySlotsUi.kt",
        "PuppyRouletteUi.kt",
        "PuppyBlackjackUi.kt",
        "PuppyCasinoHub.kt",
        "PuppySettingsUi.kt",
        "PuppyDeveloperConsole.kt",
        "PuppyExchangeUi.kt",
        "PuppyMainScreensRevamp.kt",
    ):
        rewrite(root / PACKAGE / name, [(legacy_16, compact_16)])

    rewrite(
        root / PACKAGE / "PuppyRosterScreen.kt",
        [
            (
                """.padding(
                horizontal = if (compactRoster) 12.dp else 16.dp,
                vertical = if (compactRoster) 2.dp else 4.dp
            )""",
                """.padding(
                start = if (compactRoster) 12.dp else 16.dp,
                top = 2.dp,
                end = if (compactRoster) 12.dp else 16.dp,
                bottom = 0.dp
            )""",
            ),
            (legacy_16, compact_16),
        ],
    )

    rewrite(
        root / PACKAGE / "PuppyClickerV6Activity.kt",
        [
            (".padding(horizontal = 16.dp, vertical = 10.dp)", compact_16),
            (
                ".padding(horizontal = 14.dp, vertical = 4.dp)",
                ".padding(start = 14.dp, top = 2.dp, end = 14.dp, bottom = 0.dp)",
            ),
            (
                "Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {",
                "Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, top = 2.dp, end = 18.dp, bottom = 0.dp)) {",
            ),
            (
                ".padding(padding)\n                .windowInsetsPadding(WindowInsets.safeDrawing)",
                ".padding(padding)",
            ),
        ],
    )

    rewrite(
        root / PACKAGE / "PuppyOnboardingShell.kt",
        [
            (
                "headerVerticalPadding = if (preferViewportFit) 4.dp else 8.dp",
                "headerVerticalPadding = if (preferViewportFit) 2.dp else 4.dp",
            ),
            (
                "bodyVerticalPadding = if (preferViewportFit) 2.dp else 4.dp",
                "bodyVerticalPadding = if (preferViewportFit) 0.dp else 2.dp",
            ),
            (
                "navigationVerticalPadding = if (preferViewportFit) 4.dp else 6.dp",
                "navigationVerticalPadding = if (preferViewportFit) 2.dp else 4.dp",
            ),
        ],
    )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_compact_viewport.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
