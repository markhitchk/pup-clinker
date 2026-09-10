#!/usr/bin/env python3
"""Apply the Puppy Roster revamp model changes to generated Android sources."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Roster revamp patch {label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def patch_dynamic_roster(source: str) -> str:
    source = replace_once(
        source,
        '''    val groupId: String,\n    val groupTitle: String,\n    val groupOrder: Int\n)''',
        '''    val groupId: String,\n    val groupTitle: String,\n    val groupOrder: Int,\n    val addedOrder: Long? = null,\n    val unlockSource: String? = null\n)''',
        "PuppyRosterAsset metadata",
    )

    source = replace_once(
        source,
        '''    private val _groups = MutableStateFlow(groupsFrom(assetsByStyleId.values.toList()))\n    val groups: StateFlow<List<PuppyRosterGroup>> = _groups.asStateFlow()''',
        '''    private val _assets = MutableStateFlow(assetsByStyleId.values.toList())\n    val assets: StateFlow<List<PuppyRosterAsset>> = _assets.asStateFlow()\n\n    private val _groups = MutableStateFlow(groupsFrom(assetsByStyleId.values.toList()))\n    val groups: StateFlow<List<PuppyRosterGroup>> = _groups.asStateFlow()''',
        "asset StateFlow",
    )

    source = replace_once(
        source,
        '''    private fun publishRemote(remoteDynamic: List<PuppyRosterAsset>) {\n        val next = buildAssetMap(remoteDynamic)\n        assetsByStyleId = next\n        assetsByAssetId = next.values.associateBy { it.assetId }\n        _groups.value = groupsFrom(next.values.toList())\n    }''',
        '''    private fun publishRemote(remoteDynamic: List<PuppyRosterAsset>) {\n        val next = buildAssetMap(remoteDynamic)\n        val nextAssets = next.values.toList()\n        assetsByStyleId = next\n        assetsByAssetId = nextAssets.associateBy { it.assetId }\n        _assets.value = nextAssets\n        _groups.value = groupsFrom(nextAssets)\n    }''',
        "publishRemote",
    )

    source = replace_once(
        source,
        '''                if (description.length > 240) throw IOException("Dynamic puppy description is too long")\n\n                add(''',
        '''                if (description.length > 240) throw IOException("Dynamic puppy description is too long")\n                val addedOrder = if (item.has("added_order")) item.getLong("added_order") else null\n                val unlockSource = item.optString("unlock_source").trim().ifBlank { null }\n\n                add(''',
        "optional manifest metadata",
    )

    source = replace_once(
        source,
        '''                        groupId = folder,\n                        groupTitle = groupTitle,\n                        groupOrder = groupOrder\n                    )''',
        '''                        groupId = folder,\n                        groupTitle = groupTitle,\n                        groupOrder = groupOrder,\n                        addedOrder = addedOrder,\n                        unlockSource = unlockSource\n                    )''',
        "manifest asset constructor",
    )

    source = replace_once(
        source,
        '''        assets.forEach { asset ->\n            array.put(\n                JSONObject()\n                    .put("styleId", asset.style.id)\n                    .put("name", asset.style.name)\n                    .put("emoji", asset.style.emoji)\n                    .put("description", asset.style.description)\n                    .put("redeemOnly", asset.style.redeemOnly)\n                    .put("assetId", asset.assetId)\n                    .put("folder", asset.folder)\n                    .put("fileName", asset.fileName)\n                    .put("free", asset.free)\n                    .put("groupId", asset.groupId)\n                    .put("groupTitle", asset.groupTitle)\n                    .put("groupOrder", asset.groupOrder)\n            )\n        }''',
        '''        assets.forEach { asset ->\n            val item = JSONObject()\n                .put("styleId", asset.style.id)\n                .put("name", asset.style.name)\n                .put("emoji", asset.style.emoji)\n                .put("description", asset.style.description)\n                .put("redeemOnly", asset.style.redeemOnly)\n                .put("assetId", asset.assetId)\n                .put("folder", asset.folder)\n                .put("fileName", asset.fileName)\n                .put("free", asset.free)\n                .put("groupId", asset.groupId)\n                .put("groupTitle", asset.groupTitle)\n                .put("groupOrder", asset.groupOrder)\n            asset.addedOrder?.let { item.put("addedOrder", it) }\n            asset.unlockSource?.let { item.put("unlockSource", it) }\n            array.put(item)\n        }''',
        "cache serialization",
    )

    source = replace_once(
        source,
        '''                groupId = item.getString("groupId"),\n                groupTitle = item.getString("groupTitle"),\n                groupOrder = item.getInt("groupOrder")\n            )''',
        '''                groupId = item.getString("groupId"),\n                groupTitle = item.getString("groupTitle"),\n                groupOrder = item.getInt("groupOrder"),\n                addedOrder = if (item.has("addedOrder")) item.getLong("addedOrder") else null,\n                unlockSource = item.optString("unlockSource").trim().ifBlank { null }\n            )''',
        "cache parsing",
    )

    return source


def main(root: Path) -> None:
    target = root / PACKAGE / "DynamicPuppyRoster.kt"
    source = target.read_text(encoding="utf-8")
    target.write_text(patch_dynamic_roster(source), encoding="utf-8")
    print("Puppy roster revamp model patch integrated")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_roster_revamp.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
