#!/usr/bin/env python3
"""Apply Puppy Exchange integration to the final generated Android sources."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Puppy Exchange patch {label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def patch_dynamic_roster(source: str) -> str:
    source = replace_once(
        source,
        '''    val groupOrder: Int,\n    val addedOrder: Long? = null,\n    val unlockSource: String? = null\n)''',
        '''    val groupOrder: Int,\n    val addedOrder: Long? = null,\n    val unlockSource: String? = null,\n    val transferPolicy: PuppyTransferPolicy = PuppyTransferPolicy(),\n    val hiddenUntilOwned: Boolean = false\n)''',
        "roster transfer fields",
    )

    source = replace_once(
        source,
        '''                val addedOrder = if (item.has("added_order")) item.getLong("added_order") else null\n                val unlockSource = item.optString("unlock_source").trim().ifBlank { null }\n\n                add(''',
        '''                val addedOrder = if (item.has("added_order")) item.getLong("added_order") else null\n                val unlockSource = item.optString("unlock_source").trim().ifBlank { null }\n                val transfer = item.optJSONObject("transfer_policy")\n                val transferPolicy = PuppyTransferPolicy(\n                    giftable = transfer?.optBoolean("giftable", false) ?: false,\n                    tradeable = transfer?.optBoolean("tradeable", false) ?: false,\n                    bound = transfer?.optBoolean("bound", false) ?: false,\n                    sourceCopy = transfer?.optBoolean("source_copy", false) ?: false,\n                    limited = transfer?.optBoolean("limited", false) ?: false\n                )\n                val hiddenUntilOwned = item.optBoolean("hidden_until_owned", false)\n\n                add(''',
        "manifest transfer policy parsing",
    )

    source = replace_once(
        source,
        '''                        groupOrder = groupOrder,\n                        addedOrder = addedOrder,\n                        unlockSource = unlockSource\n                    )''',
        '''                        groupOrder = groupOrder,\n                        addedOrder = addedOrder,\n                        unlockSource = unlockSource,\n                        transferPolicy = transferPolicy,\n                        hiddenUntilOwned = hiddenUntilOwned\n                    )''',
        "manifest transfer constructor",
    )

    source = replace_once(
        source,
        '''            asset.addedOrder?.let { item.put("addedOrder", it) }\n            asset.unlockSource?.let { item.put("unlockSource", it) }\n            array.put(item)''',
        '''            asset.addedOrder?.let { item.put("addedOrder", it) }\n            asset.unlockSource?.let { item.put("unlockSource", it) }\n            item.put("transferPolicy", JSONObject().apply {\n                put("giftable", asset.transferPolicy.giftable)\n                put("tradeable", asset.transferPolicy.tradeable)\n                put("bound", asset.transferPolicy.bound)\n                put("sourceCopy", asset.transferPolicy.sourceCopy)\n                put("limited", asset.transferPolicy.limited)\n            })\n            item.put("hiddenUntilOwned", asset.hiddenUntilOwned)\n            array.put(item)''',
        "cache transfer serialization",
    )

    source = replace_once(
        source,
        '''                groupOrder = item.getInt("groupOrder"),\n                addedOrder = if (item.has("addedOrder")) item.getLong("addedOrder") else null,\n                unlockSource = item.optString("unlockSource").trim().ifBlank { null }\n            )''',
        '''                groupOrder = item.getInt("groupOrder"),\n                addedOrder = if (item.has("addedOrder")) item.getLong("addedOrder") else null,\n                unlockSource = item.optString("unlockSource").trim().ifBlank { null },\n                transferPolicy = item.optJSONObject("transferPolicy")?.let { policy ->\n                    PuppyTransferPolicy(\n                        giftable = policy.optBoolean("giftable", false),\n                        tradeable = policy.optBoolean("tradeable", false),\n                        bound = policy.optBoolean("bound", false),\n                        sourceCopy = policy.optBoolean("sourceCopy", false),\n                        limited = policy.optBoolean("limited", false)\n                    )\n                } ?: PuppyTransferPolicy(),\n                hiddenUntilOwned = item.optBoolean("hiddenUntilOwned", false)\n            )''',
        "cache transfer parsing",
    )

    return source


def patch_roster_screen(source: str) -> str:
    source = replace_once(
        source,
        '''    onUseConfirmed: (String) -> Unit,\n    onOpenSettings: () -> Unit\n) {''',
        '''    onUseConfirmed: (String) -> Unit,\n    onOpenSettings: () -> Unit,\n    onOpenExchange: () -> Unit = {}\n) {''',
        "roster exchange callback",
    )
    source = replace_once(
        source,
        '''            IconButton(\n                onClick = onOpenSettings,\n                modifier = Modifier.semantics { contentDescription = "Open settings" }\n            ) {\n                Text("⚙️", fontSize = 22.sp)\n            }''',
        '''            IconButton(\n                onClick = onOpenExchange,\n                modifier = Modifier.semantics { contentDescription = "Open Puppy Exchange" }\n            ) {\n                Text("🎁", fontSize = 22.sp)\n            }\n            IconButton(\n                onClick = onOpenSettings,\n                modifier = Modifier.semantics { contentDescription = "Open settings" }\n            ) {\n                Text("⚙️", fontSize = 22.sp)\n            }''',
        "roster exchange action",
    )
    return source


def main(root: Path) -> None:
    dynamic_roster = root / PACKAGE / "DynamicPuppyRoster.kt"
    dynamic_roster.write_text(
        patch_dynamic_roster(dynamic_roster.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    roster_screen = root / PACKAGE / "PuppyRosterScreen.kt"
    roster_screen.write_text(
        patch_roster_screen(roster_screen.read_text(encoding="utf-8")),
        encoding="utf-8",
    )
    print("Puppy Exchange generated-source patches integrated")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_puppy_exchange.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
