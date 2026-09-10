#!/usr/bin/env python3
"""Apply the Puppy Roster revamp changes to generated Android sources."""
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


def patch_view_model(source: str) -> str:
    source = replace_once(
        source,
        '''    val puppyStyle: String = "classic",\n    val unlockedPuppies: Set<String> = DEFAULT_V6_PUPPIES,\n    val accessory: String = "None",''',
        '''    val puppyStyle: String = "classic",\n    val unlockedPuppies: Set<String> = DEFAULT_V6_PUPPIES,\n    val favoritePuppies: Set<String> = emptySet(),\n    val accessory: String = "None",''',
        "favorite state field",
    )

    source = replace_once(
        source,
        '''    fun setPuppyStyle(id: String) {\n        val s = _state.value\n        if (id !in s.unlockedPuppies || id !in V6_PUPPY_IDS) return\n        _state.value = s.copy(puppyStyle = id)\n        saveState()\n    }\n\n    fun setAccessory(value: String) {''',
        '''    fun setPuppyStyle(id: String) {\n        val s = _state.value\n        if (id !in s.unlockedPuppies || id !in V6_PUPPY_IDS) return\n        _state.value = s.copy(puppyStyle = id)\n        saveState()\n    }\n\n    fun toggleFavoritePuppy(id: String) {\n        val knownIds = DynamicPuppyRoster.assets.value.mapTo(linkedSetOf()) { it.style.id }\n        val current = _state.value\n        val next = toggleRosterFavorite(knownIds, current.favoritePuppies, id)\n        if (next == current.favoritePuppies) return\n        _state.value = current.copy(favoritePuppies = next)\n        prefs.edit().putStringSet(KEY_FAVORITE_PUPPIES, next).apply()\n    }\n\n    fun setAccessory(value: String) {''',
        "favorite toggle method",
    )

    source = replace_once(
        source,
        '''            puppyStyle = keep.puppyStyle,\n            unlockedPuppies = keep.unlockedPuppies,\n            accessory = keep.accessory,''',
        '''            puppyStyle = keep.puppyStyle,\n            unlockedPuppies = keep.unlockedPuppies,\n            favoritePuppies = keep.favoritePuppies,\n            accessory = keep.accessory,''',
        "reset favorites preservation",
    )

    source = replace_once(
        source,
        '''            ?: "classic"\n        val inventory = TicketRarity.entries.associateWith { rarity ->''',
        '''            ?: "classic"\n        val favorites = prefs.getStringSet(KEY_FAVORITE_PUPPIES, emptySet())?.toSet().orEmpty()\n        val inventory = TicketRarity.entries.associateWith { rarity ->''',
        "favorite state load",
    )

    source = replace_once(
        source,
        '''            puppyStyle = style,\n            unlockedPuppies = unlocked,\n            accessory = prefs.getString(KEY_ACCESSORY, "None")?.takeIf { it in ACCESSORIES } ?: "None",''',
        '''            puppyStyle = style,\n            unlockedPuppies = unlocked,\n            favoritePuppies = favorites,\n            accessory = prefs.getString(KEY_ACCESSORY, "None")?.takeIf { it in ACCESSORIES } ?: "None",''',
        "favorite state construction",
    )

    source = replace_once(
        source,
        '''            putString(KEY_PUPPY_STYLE, s.puppyStyle)\n            putStringSet(KEY_UNLOCKED_PUPPIES, s.unlockedPuppies)\n            putString(KEY_ACCESSORY, s.accessory)''',
        '''            putString(KEY_PUPPY_STYLE, s.puppyStyle)\n            putStringSet(KEY_UNLOCKED_PUPPIES, s.unlockedPuppies)\n            putStringSet(KEY_FAVORITE_PUPPIES, s.favoritePuppies)\n            putString(KEY_ACCESSORY, s.accessory)''',
        "favorite state save",
    )

    source = replace_once(
        source,
        '''        private const val KEY_PUPPY_STYLE = "puppy_style"\n        private const val KEY_UNLOCKED_PUPPIES = "unlocked_puppies"\n        private const val KEY_ACCESSORY = "accessory"''',
        '''        private const val KEY_PUPPY_STYLE = "puppy_style"\n        private const val KEY_UNLOCKED_PUPPIES = "unlocked_puppies"\n        private const val KEY_FAVORITE_PUPPIES = "favorite_puppies"\n        private const val KEY_ACCESSORY = "accessory"''',
        "favorite preference key",
    )

    return source


def patch_streamed_art(source: str) -> str:
    source = replace_once(
        source,
        '''/** V6 portrait renderer backed by the dynamic roster's exact manifest asset path. */''',
        '''internal fun streamedPuppyRequestKey(assetId: String, retryToken: Int): String =\n    "$assetId#retry=$retryToken"\n\n/** V6 portrait renderer backed by the dynamic roster's exact manifest asset path. */''',
        "streamed retry helper",
    )
    source = replace_once(
        source,
        '''    background: Color,\n    @Suppress("UNUSED_PARAMETER") furFilter: ColorFilter? = null\n) {''',
        '''    background: Color,\n    @Suppress("UNUSED_PARAMETER") furFilter: ColorFilter? = null,\n    retryToken: Int = 0\n) {''',
        "streamed retry parameter",
    )
    source = replace_once(
        source,
        '''    val assetId = RemotePuppyAssets.assetIdFor(style.id)\n    val portrait = produceState<Bitmap?>(\n        initialValue = RemotePuppyAssets.peek(assetId),\n        key1 = context,\n        key2 = assetId''',
        '''    val assetId = RemotePuppyAssets.assetIdFor(style.id)\n    val requestKey = streamedPuppyRequestKey(assetId, retryToken)\n    val portrait = produceState<Bitmap?>(\n        initialValue = RemotePuppyAssets.peek(assetId),\n        key1 = context,\n        key2 = requestKey''',
        "streamed retry request key",
    )
    return source


def patch_activity(source: str) -> str:
    source = replace_once(
        source,
        '''private fun V6PuppyPortrait(styleId: String, size: Dp, accessory: String = "None", unlocked: Boolean = true) {\n    StreamedPuppyPortrait(\n        styleId = styleId,\n        size = size,\n        accessory = accessory,\n        unlocked = unlocked,\n        background = v6PuppyBackground(styleId),\n        furFilter = null\n    )''',
        '''private fun V6PuppyPortrait(\n    styleId: String,\n    size: Dp,\n    accessory: String = "None",\n    unlocked: Boolean = true,\n    retryToken: Int = 0\n) {\n    StreamedPuppyPortrait(\n        styleId = styleId,\n        size = size,\n        accessory = accessory,\n        unlocked = unlocked,\n        background = v6PuppyBackground(styleId),\n        furFilter = null,\n        retryToken = retryToken\n    )''',
        "V6 portrait retry forwarding",
    )
    return source


def main(root: Path) -> None:
    dynamic_roster = root / PACKAGE / "DynamicPuppyRoster.kt"
    dynamic_roster.write_text(
        patch_dynamic_roster(dynamic_roster.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    view_model = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    view_model.write_text(
        patch_view_model(view_model.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    streamed_art = root / PACKAGE / "StreamedPuppyArt.kt"
    streamed_art.write_text(
        patch_streamed_art(streamed_art.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    activity.write_text(
        patch_activity(activity.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    print("Puppy roster revamp generated-source patches integrated")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_roster_revamp.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
