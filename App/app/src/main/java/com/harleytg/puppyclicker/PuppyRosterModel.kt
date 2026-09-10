package com.harleytg.puppyclicker

import java.util.Locale

enum class RosterStatusFilter {
    ALL,
    UNLOCKED,
    LOCKED
}

enum class RosterSort {
    UNLOCKED_FIRST,
    NAME_ASC,
    NAME_DESC,
    NEWEST_ADDED,
    OLDEST_ADDED,
    CATEGORY
}

data class RosterCategory(
    val id: String,
    val title: String,
    val order: Int
)

data class RosterQuery(
    val status: RosterStatusFilter = RosterStatusFilter.ALL,
    val categoryId: String? = null,
    val search: String = "",
    val favoritesOnly: Boolean = false,
    val sort: RosterSort = RosterSort.UNLOCKED_FIRST
)

data class RosterCardModel(
    val asset: PuppyRosterAsset,
    val unlocked: Boolean,
    val favorite: Boolean,
    val active: Boolean,
    val sourceIndex: Int
)

internal fun toggleRosterFavorite(
    knownIds: Set<String>,
    favoriteIds: Set<String>,
    id: String
): Set<String> {
    if (id !in knownIds) return favoriteIds
    return favoriteIds.toMutableSet().apply {
        if (!add(id)) remove(id)
    }.toSet()
}

internal fun buildRosterCards(
    assets: List<PuppyRosterAsset>,
    unlockedIds: Set<String>,
    favoriteIds: Set<String>,
    activePuppyId: String,
    query: RosterQuery
): List<RosterCardModel> {
    val normalizedSearch = query.search.trim().lowercase(Locale.ROOT)

    val cards = assets.mapIndexed { index, asset ->
        RosterCardModel(
            asset = asset,
            unlocked = asset.style.id in unlockedIds,
            favorite = asset.style.id in favoriteIds,
            active = asset.style.id == activePuppyId,
            sourceIndex = index
        )
    }.asSequence()
        .filter { card ->
            when (query.status) {
                RosterStatusFilter.ALL -> true
                RosterStatusFilter.UNLOCKED -> card.unlocked
                RosterStatusFilter.LOCKED -> !card.unlocked
            }
        }
        .filter { card -> query.categoryId == null || card.asset.groupId == query.categoryId }
        .filter { card ->
            normalizedSearch.isEmpty() ||
                card.asset.style.name.lowercase(Locale.ROOT).contains(normalizedSearch) ||
                card.asset.assetId.lowercase(Locale.ROOT).contains(normalizedSearch)
        }
        .filter { card -> !query.favoritesOnly || card.favorite }
        .toList()

    return when (query.sort) {
        RosterSort.UNLOCKED_FIRST -> cards.sortedWith(
            compareByDescending<RosterCardModel> { it.unlocked }
                .thenBy { it.sourceIndex }
        )
        RosterSort.NAME_ASC -> cards.sortedWith(
            compareBy<RosterCardModel> { it.asset.style.name.lowercase(Locale.ROOT) }
                .thenBy { it.asset.assetId }
                .thenBy { it.sourceIndex }
        )
        RosterSort.NAME_DESC -> cards.sortedWith(
            compareByDescending<RosterCardModel> { it.asset.style.name.lowercase(Locale.ROOT) }
                .thenBy { it.asset.assetId }
                .thenBy { it.sourceIndex }
        )
        RosterSort.NEWEST_ADDED -> cards.sortedWith(
            compareBy<RosterCardModel> { it.asset.addedOrder == null }
                .thenByDescending { it.asset.addedOrder ?: Long.MIN_VALUE }
                .thenBy { it.sourceIndex }
        )
        RosterSort.OLDEST_ADDED -> cards.sortedWith(
            compareBy<RosterCardModel> { it.asset.addedOrder == null }
                .thenBy { it.asset.addedOrder ?: Long.MAX_VALUE }
                .thenBy { it.sourceIndex }
        )
        RosterSort.CATEGORY -> cards.sortedWith(
            compareBy<RosterCardModel> { it.asset.groupOrder }
                .thenBy { it.asset.groupTitle.lowercase(Locale.ROOT) }
                .thenBy { it.asset.style.name.lowercase(Locale.ROOT) }
                .thenBy { it.sourceIndex }
        )
    }
}

internal fun dynamicRosterCategories(
    assets: List<PuppyRosterAsset>
): List<RosterCategory> = assets
    .groupBy { it.groupId }
    .map { (id, groupedAssets) ->
        val first = groupedAssets.first()
        RosterCategory(
            id = id,
            title = first.groupTitle,
            order = first.groupOrder
        )
    }
    .sortedWith(compareBy<RosterCategory> { it.order }.thenBy { it.id })
