from pathlib import Path

TARGET = Path("App/app/src/main/java/com/harleytg/puppyclicker/DynamicPuppyRoster.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


text = TARGET.read_text(encoding="utf-8")

text = replace_once(
    text,
    '''    val groupId: String,\n    val groupTitle: String,\n    val groupOrder: Int\n)''',
    '''    val groupId: String,\n    val groupTitle: String,\n    val groupOrder: Int,\n    val addedOrder: Long? = null,\n    val unlockSource: String? = null\n)''',
    "PuppyRosterAsset metadata",
)

text = replace_once(
    text,
    '''    private val _groups = MutableStateFlow(groupsFrom(assetsByStyleId.values.toList()))\n    val groups: StateFlow<List<PuppyRosterGroup>> = _groups.asStateFlow()''',
    '''    private val _assets = MutableStateFlow(assetsByStyleId.values.toList())\n    val assets: StateFlow<List<PuppyRosterAsset>> = _assets.asStateFlow()\n\n    private val _groups = MutableStateFlow(groupsFrom(assetsByStyleId.values.toList()))\n    val groups: StateFlow<List<PuppyRosterGroup>> = _groups.asStateFlow()''',
    "asset StateFlow",
)

text = replace_once(
    text,
    '''    private fun publishRemote(remoteDynamic: List<PuppyRosterAsset>) {\n        val next = buildAssetMap(remoteDynamic)\n        assetsByStyleId = next\n        assetsByAssetId = next.values.associateBy { it.assetId }\n        _groups.value = groupsFrom(next.values.toList())\n    }''',
    '''    private fun publishRemote(remoteDynamic: List<PuppyRosterAsset>) {\n        val next = buildAssetMap(remoteDynamic)\n        val nextAssets = next.values.toList()\n        assetsByStyleId = next\n        assetsByAssetId = nextAssets.associateBy { it.assetId }\n        _assets.value = nextAssets\n        _groups.value = groupsFrom(nextAssets)\n    }''',
    "publishRemote",
)

text = replace_once(
    text,
    '''                if (description.length > 240) throw IOException("Dynamic puppy description is too long")\n\n                add(''',
    '''                if (description.length > 240) throw IOException("Dynamic puppy description is too long")\n                val addedOrder = if (item.has("added_order")) item.getLong("added_order") else null\n                val unlockSource = item.optString("unlock_source").trim().ifBlank { null }\n\n                add(''',
    "optional manifest metadata",
)

text = replace_once(
    text,
    '''                        groupId = folder,\n                        groupTitle = groupTitle,\n                        groupOrder = groupOrder\n                    )''',
    '''                        groupId = folder,\n                        groupTitle = groupTitle,\n                        groupOrder = groupOrder,\n                        addedOrder = addedOrder,\n                        unlockSource = unlockSource\n                    )''',
    "manifest asset constructor",
)

old_serialize = '''        assets.forEach { asset ->\n            array.put(\n                JSONObject()\n                    .put("styleId", asset.style.id)\n                    .put("name", asset.style.name)\n                    .put("emoji", asset.style.emoji)\n                    .put("description", asset.style.description)\n                    .put("redeemOnly", asset.style.redeemOnly)\n                    .put("assetId", asset.assetId)\n                    .put("folder", asset.folder)\n                    .put("fileName", asset.fileName)\n                    .put("free", asset.free)\n                    .put("groupId", asset.groupId)\n                    .put("groupTitle", asset.groupTitle)\n                    .put("groupOrder", asset.groupOrder)\n            )\n        }'''
new_serialize = '''        assets.forEach { asset ->\n            val item = JSONObject()\n                .put("styleId", asset.style.id)\n                .put("name", asset.style.name)\n                .put("emoji", asset.style.emoji)\n                .put("description", asset.style.description)\n                .put("redeemOnly", asset.style.redeemOnly)\n                .put("assetId", asset.assetId)\n                .put("folder", asset.folder)\n                .put("fileName", asset.fileName)\n                .put("free", asset.free)\n                .put("groupId", asset.groupId)\n                .put("groupTitle", asset.groupTitle)\n                .put("groupOrder", asset.groupOrder)\n            asset.addedOrder?.let { item.put("addedOrder", it) }\n            asset.unlockSource?.let { item.put("unlockSource", it) }\n            array.put(item)\n        }'''
text = replace_once(text, old_serialize, new_serialize, "cache serialization")

text = replace_once(
    text,
    '''                groupId = item.getString("groupId"),\n                groupTitle = item.getString("groupTitle"),\n                groupOrder = item.getInt("groupOrder")\n            )''',
    '''                groupId = item.getString("groupId"),\n                groupTitle = item.getString("groupTitle"),\n                groupOrder = item.getInt("groupOrder"),\n                addedOrder = if (item.has("addedOrder")) item.getLong("addedOrder") else null,\n                unlockSource = item.optString("unlockSource").trim().ifBlank { null }\n            )''',
    "cache parsing",
)

TARGET.write_text(text, encoding="utf-8")
print("patched", TARGET)
