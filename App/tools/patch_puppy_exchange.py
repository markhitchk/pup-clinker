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


def replace_function(source: str, signature: str, next_signature: str, replacement: str, label: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"Puppy Exchange patch {label}: start signature not found")
    end = source.find(next_signature, start)
    if end < 0:
        raise RuntimeError(f"Puppy Exchange patch {label}: end signature not found")
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


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
        "import androidx.compose.ui.res.painterResource\n",
        "import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.res.painterResource\n",
        "compact roster configuration import",
    )
    source = replace_once(
        source,
        '''    onUseConfirmed: (String) -> Unit,\n    onOpenSettings: () -> Unit\n) {''',
        '''    onUseConfirmed: (String) -> Unit,\n    onOpenSettings: () -> Unit,\n    onOpenExchange: () -> Unit = {}\n) {''',
        "roster exchange callback",
    )
    source = replace_once(
        source,
        '''    var renameOpen by rememberSaveable { mutableStateOf(false) }\n    val retryTokens = remember { mutableStateMapOf<String, Int>() }''',
        '''    var renameOpen by rememberSaveable { mutableStateOf(false) }\n    val retryTokens = remember { mutableStateMapOf<String, Int>() }\n    val compactRoster = LocalConfiguration.current.screenWidthDp < 600''',
        "compact roster breakpoint",
    )
    source = replace_once(
        source,
        '''            .fillMaxSize()\n            .padding(horizontal = 16.dp, vertical = 10.dp)''',
        '''            .fillMaxSize()\n            .padding(\n                horizontal = if (compactRoster) 12.dp else 16.dp,\n                vertical = if (compactRoster) 6.dp else 10.dp\n            )''',
        "compact roster screen padding",
    )
    source = replace_once(
        source,
        '''                Text(\n                    "Browse every streamed puppy in the live roster.",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant\n                )''',
        '''                Text(\n                    "Browse every streamed puppy in the live roster.",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant,\n                    maxLines = if (compactRoster) 1 else 2,\n                    overflow = TextOverflow.Ellipsis\n                )''',
        "compact roster subtitle",
    )
    source = replace_once(
        source,
        '''            IconButton(\n                onClick = onOpenSettings,\n                modifier = Modifier.semantics { contentDescription = "Open settings" }\n            ) {\n                Text("⚙️", fontSize = 22.sp)\n            }''',
        '''            IconButton(\n                onClick = onOpenExchange,\n                modifier = Modifier.semantics { contentDescription = "Open Puppy Exchange" }\n            ) {\n                Text("🎁", fontSize = 22.sp)\n            }\n            IconButton(\n                onClick = onOpenSettings,\n                modifier = Modifier.semantics { contentDescription = "Open settings" }\n            ) {\n                Text("⚙️", fontSize = 22.sp)\n            }''',
        "roster exchange action",
    )
    source = replace_once(
        source,
        '''                onUnlockInfo = { unlockInfoId = asset.style.id },\n                onRename = { renameOpen = true },\n                onAccessory = vm::setAccessory\n            )''',
        '''                onUnlockInfo = { unlockInfoId = asset.style.id },\n                onRename = { renameOpen = true },\n                onAccessory = vm::setAccessory,\n                compact = compactRoster\n            )''',
        "compact selected puppy panel call",
    )

    source = replace_function(
        source,
        "@Composable\nprivate fun SelectedPuppyPanel(",
        "@Composable\nprivate fun PuppyRosterCard(",
        '''@Composable
private fun SelectedPuppyPanel(
    asset: PuppyRosterAsset,
    state: V6GameState,
    isFavorite: Boolean,
    retryToken: Int,
    onToggleFavorite: () -> Unit,
    onRetryArtwork: () -> Unit,
    onUse: () -> Unit,
    onUnlockInfo: () -> Unit,
    onRename: () -> Unit,
    onAccessory: (String) -> Unit,
    compact: Boolean
) {
    val id = asset.style.id
    val unlocked = id in state.unlockedPuppies
    val active = id == state.puppyStyle

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    ) {
        Column(Modifier.padding(if (compact) 8.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    StreamedPuppyPortrait(
                        styleId = id,
                        size = if (compact) 64.dp else 90.dp,
                        accessory = if (active) state.accessory else "None",
                        unlocked = unlocked,
                        background = MaterialTheme.colorScheme.surface,
                        retryToken = retryToken
                    )
                    IconButton(
                        onClick = onRetryArtwork,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(if (compact) 26.dp else 30.dp)
                            .semantics { contentDescription = "Retry artwork for ${asset.style.name}" }
                    ) { Text("↻", fontSize = if (compact) 13.sp else 16.sp) }
                }
                Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        asset.style.name,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (compact) {
                        Text(
                            "${asset.assetId} · ${asset.groupTitle}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(asset.assetId, style = MaterialTheme.typography.labelMedium)
                        Text(asset.groupTitle, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(if (compact) 2.dp else 5.dp))
                    when {
                        active -> Text(
                            "Current Puppy",
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        unlocked -> if (compact) {
                            TextButton(
                                onClick = onUse,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) { Text("Use Puppy") }
                        } else {
                            Button(onClick = onUse) { Text("Use Puppy") }
                        }
                        else -> {
                            Text("Locked", style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                            if (asset.unlockSource != null) {
                                TextButton(
                                    onClick = onUnlockInfo,
                                    contentPadding = PaddingValues(horizontal = if (compact) 4.dp else 8.dp, vertical = 0.dp)
                                ) { Text("How to Unlock") }
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(if (compact) 36.dp else 48.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (isFavorite) R.drawable.ic_puppy_favorite
                            else R.drawable.ic_puppy_favorite_border
                        ),
                        contentDescription = if (isFavorite) "Remove ${asset.style.name} from favorites" else "Add ${asset.style.name} to favorites",
                        tint = if (isFavorite) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (active) {
                Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onRename,
                        modifier = Modifier.height(if (compact) 36.dp else 40.dp),
                        contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 16.dp, vertical = 0.dp)
                    ) { Text("Rename", maxLines = 1) }
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
                    ) {
                        PuppyClickerV6ViewModel.ACCESSORIES.forEach { accessory ->
                            FilterChip(
                                selected = state.accessory == accessory,
                                onClick = { onAccessory(accessory) },
                                label = { Text(accessory, maxLines = 1) },
                                modifier = Modifier.height(if (compact) 34.dp else 40.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}''',
        "compact selected puppy panel",
    )

    source = replace_function(
        source,
        "@Composable\nprivate fun PuppyRosterCard(",
        "@Composable\nprivate fun UsePuppyConfirmation(",
        '''@Composable
private fun PuppyRosterCard(
    card: RosterCardModel,
    inspected: Boolean,
    retryToken: Int,
    onInspect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryArtwork: () -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp < 600
    val shape = RoundedCornerShape(if (compact) 16.dp else 18.dp)
    val borderColor = if (inspected) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, shape)
            .clickable(onClick = onInspect),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (card.active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Box(Modifier.fillMaxWidth().padding(if (compact) 6.dp else 8.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
                StreamedPuppyPortrait(
                    styleId = card.asset.style.id,
                    size = if (compact) 58.dp else 68.dp,
                    unlocked = card.unlocked,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    retryToken = retryToken
                )
                Spacer(Modifier.height(if (compact) 3.dp else 5.dp))
                Text(
                    card.asset.style.name,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    card.asset.assetId,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        card.asset.groupTitle,
                        modifier = Modifier.padding(horizontal = if (compact) 5.dp else 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(if (compact) 1.dp else 3.dp))
                Text(
                    when {
                        card.active -> "Current"
                        card.unlocked -> "Unlocked"
                        else -> "🔒 Locked"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd).size(if (compact) 28.dp else 32.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (card.favorite) R.drawable.ic_puppy_favorite
                        else R.drawable.ic_puppy_favorite_border
                    ),
                    contentDescription = if (card.favorite) {
                        "Remove ${card.asset.style.name} from favorites"
                    } else {
                        "Add ${card.asset.style.name} to favorites"
                    },
                    tint = if (card.favorite) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRetryArtwork,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(if (compact) 24.dp else 28.dp)
                    .semantics { contentDescription = "Retry artwork for ${card.asset.style.name}" }
            ) { Text("↻", fontSize = if (compact) 12.sp else 14.sp) }
        }
    }
}''',
        "compact roster cards",
    )
    return source


def patch_activity(source: str) -> str:
    source = replace_once(
        source,
        '''                    PuppyInternalDestination.SETTINGS -> V6Settings(state, vm)\n                    PuppyInternalDestination.PRESTIGE -> V6Prestige(state, vm)''',
        '''                    PuppyInternalDestination.SETTINGS -> V6Settings(state, vm)\n                    PuppyInternalDestination.PRESTIGE -> V6Prestige(state, vm)\n                    PuppyInternalDestination.EXCHANGE -> PuppyExchangeScreen(\n                        state = state,\n                        vm = vm,\n                        onBack = { internalDestination = null }\n                    )''',
        "app shell exchange destination",
    )
    source = replace_once(
        source,
        '''                        onOpenSettings = { internalDestination = PuppyInternalDestination.SETTINGS }\n                    )''',
        '''                        onOpenSettings = { internalDestination = PuppyInternalDestination.SETTINGS },\n                        onOpenExchange = { internalDestination = PuppyInternalDestination.EXCHANGE }\n                    )''',
        "app shell roster exchange route",
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

    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    activity.write_text(
        patch_activity(activity.read_text(encoding="utf-8")),
        encoding="utf-8",
    )
    print("Puppy Exchange generated-source patches integrated")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_puppy_exchange.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
