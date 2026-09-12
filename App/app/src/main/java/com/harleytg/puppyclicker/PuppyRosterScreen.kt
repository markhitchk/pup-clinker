package com.harleytg.puppyclicker

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val FavoriteRed = Color(0xFFD32F2F)

@Composable
internal fun PuppyRosterScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onUseConfirmed: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val assets by DynamicPuppyRoster.assets.collectAsStateWithLifecycle()
    var status by rememberSaveable { mutableStateOf(RosterStatusFilter.ALL) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(RosterSort.UNLOCKED_FIRST) }
    var sortOpen by remember { mutableStateOf(false) }
    var inspectedId by rememberSaveable { mutableStateOf<String?>(state.puppyStyle) }
    var confirmUseId by rememberSaveable { mutableStateOf<String?>(null) }
    var unlockInfoId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    val retryTokens = remember { mutableStateMapOf<String, Int>() }

    val categories = remember(assets) { dynamicRosterCategories(assets) }
    val categoryIds = remember(categories) { categories.mapTo(linkedSetOf()) { it.id } }

    LaunchedEffect(categoryId, categoryIds) {
        if (categoryId != null && categoryId !in categoryIds) categoryId = null
    }
    LaunchedEffect(assets, inspectedId, state.puppyStyle) {
        if (inspectedId == null || assets.none { it.style.id == inspectedId }) {
            inspectedId = assets.firstOrNull { it.style.id == state.puppyStyle }?.style?.id
                ?: assets.firstOrNull()?.style?.id
        }
    }

    val query = remember(status, categoryId, search, favoritesOnly, sort) {
        RosterQuery(
            status = status,
            categoryId = categoryId,
            search = search,
            favoritesOnly = favoritesOnly,
            sort = sort
        )
    }
    val cards = remember(assets, state.unlockedPuppies, state.favoritePuppies, state.puppyStyle, query) {
        buildRosterCards(
            assets = assets,
            unlockedIds = state.unlockedPuppies,
            favoriteIds = state.favoritePuppies,
            activePuppyId = state.puppyStyle,
            query = query
        )
    }
    val inspected = assets.firstOrNull { it.style.id == inspectedId }
        ?: assets.firstOrNull { it.style.id == state.puppyStyle }
        ?: assets.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Puppy Roster",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Browse every streamed puppy in the live roster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
            ) {
                Text(
                    "🐾 " + assets.count { it.style.id in state.unlockedPuppies } + " / " + assets.size + " Unlocked",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RosterStatusFilter.entries.forEach { option ->
                FilterChip(
                    selected = status == option,
                    onClick = { status = option },
                    label = {
                        Text(
                            when (option) {
                                RosterStatusFilter.ALL -> "All"
                                RosterStatusFilter.UNLOCKED -> "Unlocked"
                                RosterStatusFilter.LOCKED -> "Locked"
                            },
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(64) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search puppies or ID") },
            leadingIcon = { Text("🔍") },
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { sortOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Sort puppies" }
                ) {
                    Text("Sort: ${rosterSortLabel(sort)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(
                    expanded = sortOpen,
                    onDismissRequest = { sortOpen = false }
                ) {
                    RosterSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(rosterSortLabel(option)) },
                            onClick = {
                                sort = option
                                sortOpen = false
                            }
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { favoritesOnly = !favoritesOnly },
                modifier = Modifier.semantics {
                    contentDescription = if (favoritesOnly) "Show all puppies" else "Show favorites only"
                }
            ) {
                Icon(
                    painter = painterResource(
                        if (favoritesOnly) R.drawable.ic_puppy_favorite
                        else R.drawable.ic_puppy_favorite_border
                    ),
                    contentDescription = null,
                    tint = if (favoritesOnly) FavoriteRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(5.dp))
                Text("Favorites")
            }
        }

        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FilterChip(
                selected = categoryId == null,
                onClick = { categoryId = null },
                label = { Text("All Categories") }
            )
            categories.forEach { category ->
                FilterChip(
                    selected = categoryId == category.id,
                    onClick = { categoryId = category.id },
                    label = { Text(category.title) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "${cards.size} shown · ${assets.count { it.style.id in state.unlockedPuppies }} unlocked",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        inspected?.let { asset ->
            Spacer(Modifier.height(7.dp))
            SelectedPuppyPanel(
                asset = asset,
                state = state,
                isFavorite = asset.style.id in state.favoritePuppies,
                retryToken = retryTokens[asset.style.id] ?: 0,
                onToggleFavorite = { vm.toggleFavoritePuppy(asset.style.id) },
                onRetryArtwork = {
                    retryTokens[asset.style.id] = (retryTokens[asset.style.id] ?: 0) + 1
                },
                onUse = { confirmUseId = asset.style.id },
                onUnlockInfo = { unlockInfoId = asset.style.id },
                onRename = { renameOpen = true },
                onAccessory = vm::setAccessory
            )
        }

        Spacer(Modifier.height(8.dp))
        if (cards.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No puppies match these filters.", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            status = RosterStatusFilter.ALL
                            categoryId = null
                            search = ""
                            favoritesOnly = false
                            sort = RosterSort.UNLOCKED_FIRST
                        }) {
                            Text("Reset filters")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cards, key = { it.asset.style.id }) { card ->
                    PuppyRosterCard(
                        card = card,
                        inspected = card.asset.style.id == inspectedId,
                        retryToken = retryTokens[card.asset.style.id] ?: 0,
                        onInspect = { inspectedId = card.asset.style.id },
                        onToggleFavorite = { vm.toggleFavoritePuppy(card.asset.style.id) },
                        onRetryArtwork = {
                            val id = card.asset.style.id
                            retryTokens[id] = (retryTokens[id] ?: 0) + 1
                        }
                    )
                }
            }
        }
    }

    confirmUseId?.let { id ->
        assets.firstOrNull { it.style.id == id }?.let { asset ->
            UsePuppyConfirmation(
                asset = asset,
                retryToken = retryTokens[id] ?: 0,
                onCancel = { confirmUseId = null },
                onConfirm = {
                    confirmUseId = null
                    onUseConfirmed(id)
                }
            )
        }
    }

    unlockInfoId?.let { id ->
        assets.firstOrNull { it.style.id == id }?.let { asset ->
            asset.unlockSource?.let { source ->
                AlertDialog(
                    onDismissRequest = { unlockInfoId = null },
                    title = { Text("How to Unlock") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            StreamedPuppyPortrait(
                                styleId = asset.style.id,
                                size = 96.dp,
                                unlocked = false,
                                background = MaterialTheme.colorScheme.surfaceVariant,
                                retryToken = retryTokens[id] ?: 0
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(asset.style.name, fontWeight = FontWeight.Black)
                            Text(asset.assetId, style = MaterialTheme.typography.labelMedium)
                            Text(asset.groupTitle, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(10.dp))
                            Text(source)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { unlockInfoId = null }) { Text("Close") }
                    }
                )
            }
        }
    }

    if (renameOpen) {
        var name by rememberSaveable { mutableStateOf(state.puppyName) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename puppy") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(18) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renamePuppy(name)
                    renameOpen = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
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
    onAccessory: (String) -> Unit
) {
    val id = asset.style.id
    val unlocked = id in state.unlockedPuppies
    val active = id == state.puppyStyle

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    StreamedPuppyPortrait(
                        styleId = id,
                        size = 90.dp,
                        accessory = if (active) state.accessory else "None",
                        unlocked = unlocked,
                        background = MaterialTheme.colorScheme.surface,
                        retryToken = retryToken
                    )
                    IconButton(
                        onClick = onRetryArtwork,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(30.dp)
                            .semantics { contentDescription = "Retry artwork for ${asset.style.name}" }
                    ) { Text("↻", fontSize = 16.sp) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(asset.style.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(asset.assetId, style = MaterialTheme.typography.labelMedium)
                    Text(asset.groupTitle, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(5.dp))
                    when {
                        active -> Text("Current Puppy", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        unlocked -> Button(onClick = onUse) { Text("Use Puppy") }
                        else -> {
                            Text("Locked", fontWeight = FontWeight.Black)
                            if (asset.unlockSource != null) {
                                TextButton(onClick = onUnlockInfo) { Text("How to Unlock") }
                            }
                        }
                    }
                }
                IconButton(onClick = onToggleFavorite) {
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

@Composable
private fun PuppyRosterCard(
    card: RosterCardModel,
    inspected: Boolean,
    retryToken: Int,
    onInspect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryArtwork: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
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
        Box(Modifier.fillMaxWidth().padding(8.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(4.dp))
                StreamedPuppyPortrait(
                    styleId = card.asset.style.id,
                    size = 68.dp,
                    unlocked = card.unlocked,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    retryToken = retryToken
                )
                Spacer(Modifier.height(5.dp))
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
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(3.dp))
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
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
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
                    .size(28.dp)
                    .semantics { contentDescription = "Retry artwork for ${card.asset.style.name}" }
            ) { Text("↻", fontSize = 14.sp) }
        }
    }
}

@Composable
private fun UsePuppyConfirmation(
    asset: PuppyRosterAsset,
    retryToken: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
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
    )
}

private fun rosterSortLabel(sort: RosterSort): String = when (sort) {
    RosterSort.UNLOCKED_FIRST -> "Unlocked First"
    RosterSort.NAME_ASC -> "Name A–Z"
    RosterSort.NAME_DESC -> "Name Z–A"
    RosterSort.NEWEST_ADDED -> "Newest Added"
    RosterSort.OLDEST_ADDED -> "Oldest Added"
    RosterSort.CATEGORY -> "Category"
}
