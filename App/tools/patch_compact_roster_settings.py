#!/usr/bin/env python3
"""Apply compact Puppy Roster controls and move Account & Profile to the top of Settings."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Compact UI patch {label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def replace_function(source: str, signature: str, next_signature: str, replacement: str, label: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"Compact UI patch {label}: start signature not found")
    end = source.find(next_signature, start)
    if end < 0:
        raise RuntimeError(f"Compact UI patch {label}: end signature not found")
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


def patch_roster_screen(source: str) -> str:
    source = replace_once(
        source,
        "import androidx.compose.foundation.layout.height\n",
        "import androidx.compose.foundation.layout.height\nimport androidx.compose.foundation.layout.heightIn\n",
        "roster heightIn import",
    )
    source = replace_once(
        source,
        "import androidx.compose.ui.unit.dp\n",
        "import androidx.compose.ui.unit.Dp\nimport androidx.compose.ui.unit.dp\n",
        "roster Dp import",
    )

    source = replace_once(
        source,
        "import androidx.compose.ui.text.style.TextOverflow\n",
        "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow\n",
        "roster TextAlign import",
    )

    source = replace_once(
        source,
        '''                    modifier = Modifier.weight(1f)\n                )\n            }\n        }\n\n        Spacer(Modifier.height(8.dp))\n        OutlinedTextField(''',
        '''                    modifier = Modifier.height(if (compactRoster) 34.dp else 48.dp).weight(1f)\n                )\n            }\n        }\n\n        Spacer(Modifier.height(if (compactRoster) 5.dp else 8.dp))\n        OutlinedTextField(''',
        "shorter status filters",
    )

    source = replace_once(
        source,
        '''        OutlinedTextField(\n            value = search,\n            onValueChange = { search = it.take(64) },\n            modifier = Modifier.fillMaxWidth(),\n            singleLine = true,\n            label = { Text("Search puppies or ID") },\n            leadingIcon = { Text("🔍") }\n        )''',
        '''        OutlinedTextField(\n            value = search,\n            onValueChange = { search = it.take(64) },\n            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),\n            singleLine = true,\n            placeholder = { Text("Search puppies or ID", maxLines = 1) },\n            leadingIcon = { Text("🔍", fontSize = if (compactRoster) 14.sp else 16.sp) }\n        )''',
        "shorter search field",
    )

    source = replace_once(
        source,
        '''        Spacer(Modifier.height(7.dp))\n        Row(\n            modifier = Modifier.fillMaxWidth(),\n            horizontalArrangement = Arrangement.spacedBy(7.dp),\n            verticalAlignment = Alignment.CenterVertically\n        ) {\n            Box(Modifier.weight(1f)) {\n                OutlinedButton(\n                    onClick = { sortOpen = true },\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .semantics { contentDescription = "Sort puppies" }\n                ) {\n                    Text("Sort: ${rosterSortLabel(sort)}", maxLines = 1, overflow = TextOverflow.Ellipsis)\n                }\n                DropdownMenu(\n                    expanded = sortOpen,\n                    onDismissRequest = { sortOpen = false }\n                ) {\n                    RosterSort.entries.forEach { option ->\n                        DropdownMenuItem(\n                            text = { Text(rosterSortLabel(option)) },\n                            onClick = {\n                                sort = option\n                                sortOpen = false\n                            }\n                        )\n                    }\n                }\n            }\n            OutlinedButton(\n                onClick = { favoritesOnly = !favoritesOnly },\n                modifier = Modifier.semantics {\n                    contentDescription = if (favoritesOnly) "Show all puppies" else "Show favorites only"\n                }\n            ) {\n                Icon(\n                    painter = painterResource(\n                        if (favoritesOnly) R.drawable.ic_puppy_favorite\n                        else R.drawable.ic_puppy_favorite_border\n                    ),\n                    contentDescription = null,\n                    tint = if (favoritesOnly) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant\n                )\n                Spacer(Modifier.width(5.dp))\n                Text("Favorites")\n            }\n        }''',
        '''        Spacer(Modifier.height(if (compactRoster) 4.dp else 7.dp))\n        CompactRosterSortFavorites(\n            sort = sort,\n            sortOpen = sortOpen,\n            onOpenSort = { sortOpen = true },\n            onDismissSort = { sortOpen = false },\n            onSelectSort = { selected ->\n                sort = selected\n                sortOpen = false\n            },\n            favoritesOnly = favoritesOnly,\n            onToggleFavorites = { favoritesOnly = !favoritesOnly },\n            height = if (compactRoster) 34.dp else 40.dp\n        )''',
        "compact sort and favorites",
    )

    source = replace_once(
        source,
        '''        Spacer(Modifier.height(7.dp))\n        Row(\n            modifier = Modifier\n                .fillMaxWidth()\n                .horizontalScroll(rememberScrollState()),\n            horizontalArrangement = Arrangement.spacedBy(7.dp)\n        ) {\n            FilterChip(\n                selected = categoryId == null,\n                onClick = { categoryId = null },\n                label = { Text("All Categories") }\n            )\n            categories.forEach { category ->\n                FilterChip(\n                    selected = categoryId == category.id,\n                    onClick = { categoryId = category.id },\n                    label = { Text(category.title) }\n                )\n            }\n        }''',
        '''        Spacer(Modifier.height(if (compactRoster) 4.dp else 7.dp))\n        CompactRosterCategories(\n            categories = categories,\n            categoryId = categoryId,\n            onCategory = { categoryId = it },\n            height = if (compactRoster) 34.dp else 40.dp\n        )''',
        "compact category chips",
    )

    helper_marker = "@Composable\nprivate fun SelectedPuppyPanel("
    helper_block = '''@Composable
private fun CompactRosterSortFavorites(
    sort: RosterSort,
    sortOpen: Boolean,
    onOpenSort: () -> Unit,
    onDismissSort: () -> Unit,
    onSelectSort: (RosterSort) -> Unit,
    favoritesOnly: Boolean,
    onToggleFavorites: () -> Unit,
    height: Dp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            FilterChip(
                selected = false,
                onClick = onOpenSort,
                label = {
                    Text(
                        "Sort: ${rosterSortLabel(sort)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .semantics { contentDescription = "Sort puppies" }
            )
            DropdownMenu(expanded = sortOpen, onDismissRequest = onDismissSort) {
                RosterSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(rosterSortLabel(option)) },
                        onClick = { onSelectSort(option) }
                    )
                }
            }
        }
        FilterChip(
            selected = favoritesOnly,
            onClick = onToggleFavorites,
            label = { Text("Favorites", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    painter = painterResource(
                        if (favoritesOnly) R.drawable.ic_puppy_favorite
                        else R.drawable.ic_puppy_favorite_border
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (favoritesOnly) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .height(height)
                .semantics {
                    contentDescription = if (favoritesOnly) "Show all puppies" else "Show favorites only"
                }
        )
    }
}

@Composable
private fun CompactRosterCategories(
    categories: List<RosterCategory>,
    categoryId: String?,
    onCategory: (String?) -> Unit,
    height: Dp
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        FilterChip(
            selected = categoryId == null,
            onClick = { onCategory(null) },
            label = { Text("All Categories", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.height(height)
        )
        categories.forEach { category ->
            FilterChip(
                selected = categoryId == category.id,
                onClick = { onCategory(category.id) },
                label = { Text(category.title, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.height(height)
            )
        }
    }
}

'''
    source = replace_once(source, helper_marker, helper_block + helper_marker, "roster compact helper insertion")

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
        modifier = Modifier.heightIn(min = if (compact) 68.dp else 0.dp).fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 14.dp else 20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    ) {
        Column(Modifier.padding(if (compact) 6.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    StreamedPuppyPortrait(
                        styleId = id,
                        size = if (compact) 52.dp else 90.dp,
                        accessory = if (active) state.accessory else "None",
                        unlocked = unlocked,
                        background = MaterialTheme.colorScheme.surface,
                        retryToken = retryToken
                    )
                    IconButton(
                        onClick = onRetryArtwork,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(if (compact) 22.dp else 30.dp)
                            .semantics { contentDescription = "Retry artwork for ${asset.style.name}" }
                    ) { Text("↻", fontSize = if (compact) 11.sp else 16.sp) }
                }
                Spacer(Modifier.width(if (compact) 7.dp else 10.dp))
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
                    Spacer(Modifier.height(if (compact) 1.dp else 5.dp))
                    when {
                        active -> Text(
                            "Current Puppy",
                            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        unlocked -> if (compact) {
                            TextButton(
                                onClick = onUse,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) { Text("Use Puppy", style = MaterialTheme.typography.labelSmall) }
                        } else {
                            Button(onClick = onUse) { Text("Use Puppy") }
                        }
                        else -> {
                            Text("Locked", style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                            if (asset.unlockSource != null) {
                                TextButton(
                                    onClick = onUnlockInfo,
                                    contentPadding = PaddingValues(horizontal = if (compact) 3.dp else 8.dp, vertical = 0.dp),
                                    modifier = if (compact) Modifier.height(24.dp) else Modifier
                                ) { Text("How to Unlock", style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge) }
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(if (compact) 32.dp else 48.dp)
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
                if (compact) {
                    Spacer(Modifier.height(3.dp))
                    CompactSelectedPuppyActions(state, onRename, onAccessory)
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onRename) { Text("Rename") }
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PuppyClickerV6ViewModel.ACCESSORIES.forEach { accessory ->
                                FilterChip(
                                    selected = state.accessory == accessory,
                                    onClick = { onAccessory(accessory) },
                                    label = { Text(accessory) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSelectedPuppyActions(
    state: V6GameState,
    onRename: () -> Unit,
    onAccessory: (String) -> Unit
) {
    var accessoryOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onRename,
            modifier = Modifier.height(30.dp),
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
        ) { Text("Rename", style = MaterialTheme.typography.labelSmall) }
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { accessoryOpen = true },
                modifier = Modifier.fillMaxWidth().height(30.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    "Accessory: ${state.accessory}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(expanded = accessoryOpen, onDismissRequest = { accessoryOpen = false }) {
                PuppyClickerV6ViewModel.ACCESSORIES.forEach { accessory ->
                    DropdownMenuItem(
                        text = { Text(accessory) },
                        onClick = {
                            onAccessory(accessory)
                            accessoryOpen = false
                        }
                    )
                }
            }
        }
    }
}''',
        "slim selected puppy strip",
    )

    source = replace_once(
        source,
        '''    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Use Puppy?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StreamedPuppyPortrait(
                    styleId = asset.style.id,
                    size = 112.dp,
                    unlocked = true,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    retryToken = retryToken
                )
                Spacer(Modifier.height(8.dp))
                Text(asset.style.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text("Make ${asset.style.name} your active puppy?")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Use Puppy") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )''',
        '''    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "Use Puppy?",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StreamedPuppyPortrait(
                    styleId = asset.style.id,
                    size = 108.dp,
                    unlocked = true,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    retryToken = retryToken
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    asset.style.name,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Make ${asset.style.name} your active puppy?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(10.dp))
                Button(onClick = onConfirm) { Text("Use Puppy") }
            }
        },
        dismissButton = {}
    )''',
        "centered use puppy confirmation",
    )

    return source


def patch_settings_screen(source: str) -> str:
    source = replace_once(
        source,
        '''    var expandedKeys by rememberSaveable { mutableStateOf("appearance") }''',
        '''    var expandedKeys by rememberSaveable { mutableStateOf("appearance") }\n    var accountExpanded by rememberSaveable { mutableStateOf(false) }''',
        "settings account expansion state",
    )

    source = replace_once(
        source,
        '''        Spacer(Modifier.height(14.dp))\n\n        SettingsSectionCard("appearance", "🎨", "Appearance", isExpanded("appearance"), { toggle("appearance") }) {''',
        '''        Spacer(Modifier.height(10.dp))\n\n        AccountProfileCard(\n            state = state,\n            vm = vm,\n            ui = ui,\n            expanded = accountExpanded,\n            onToggle = { accountExpanded = !accountExpanded }\n        )\n\n        SettingsSectionCard("appearance", "🎨", "Appearance", isExpanded("appearance"), { toggle("appearance") }) {''',
        "top account profile card",
    )

    source = replace_once(
        source,
        '''        SettingsSectionCard("account", "👤", "Account & Profile", isExpanded("account"), { toggle("account") }) {\n            AccountProfileSettings(vm, ui)\n        }\n''',
        "",
        "remove duplicate account accordion",
    )

    marker = "@Composable\nprivate fun SettingsSectionCard("
    account_card = '''@Composable
private fun AccountProfileCard(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    ui: PuppyUiState,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val username = PuppyPlayerIdentity.username(context)
    val playerId = PuppyPlayerIdentity.playerId(context)
    val friendCode = PuppyPlayerIdentity.friendCode(context)
    val shortPlayerId = if (playerId.length > 13) "${playerId.take(7)}…${playerId.takeLast(4)}" else playerId
    val reduceMotion = LocalPuppyReducedMotion.current
    val duration = if (reduceMotion) 1 else 190
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(duration),
        label = "settings-account-profile-chevron"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreamedPuppyPortrait(
                    styleId = state.puppyStyle,
                    size = 48.dp,
                    accessory = state.accessory,
                    unlocked = true,
                    background = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Account & Profile", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                    Text(username, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(friendCode, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text("Player ID: $shortPlayerId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "Device Bound",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "›",
                    modifier = Modifier.rotate(arrowRotation),
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { copyAccountValue(context, "Puppy Clicker Friend Code", friendCode) },
                    modifier = Modifier.height(30.dp)
                ) { Text("Copy", style = MaterialTheme.typography.labelSmall) }
                Text("Friend Code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { copyAccountValue(context, "Puppy Clicker Player ID", playerId) },
                    modifier = Modifier.height(30.dp)
                ) { Text("Copy", style = MaterialTheme.typography.labelSmall) }
                Text("Player ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    AccountProfileSettings(vm, ui)
                }
            }
        }
    }
}

private fun copyAccountValue(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
}

'''
    source = replace_once(source, marker, account_card + marker, "account profile card helper")
    return source


def main(root: Path) -> None:
    roster = root / PACKAGE / "PuppyRosterScreen.kt"
    roster.write_text(patch_roster_screen(roster.read_text(encoding="utf-8")), encoding="utf-8")

    settings = root / PACKAGE / "PuppySettingsUi.kt"
    settings.write_text(patch_settings_screen(settings.read_text(encoding="utf-8")), encoding="utf-8")

    print("Compact roster controls and top Account & Profile card integrated")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_compact_roster_settings.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
