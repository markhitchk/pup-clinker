package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class PuppyRosterModelTest {
    private fun asset(
        id: String,
        name: String,
        groupId: String,
        groupTitle: String,
        groupOrder: Int,
        addedOrder: Long? = null
    ) = PuppyRosterAsset(
        style = PuppyStyle(id, name, "🐾", "$name description", redeemOnly = false),
        assetId = id,
        folder = groupId,
        fileName = "$id.png",
        free = true,
        groupId = groupId,
        groupTitle = groupTitle,
        groupOrder = groupOrder,
        addedOrder = addedOrder
    )

    private val assets = listOf(
        asset("v1_alpha", "Alpha", "v1", "V1 Puppies", 1, addedOrder = 10),
        asset("v2_flurry", "Flurry", "v2", "V2 Puppies", 2, addedOrder = 30),
        asset("v2_beta", "Beta", "v2", "V2 Puppies", 2, addedOrder = null)
    )

    private val unlocked = setOf("v1_alpha", "v2_flurry")
    private val favorites = setOf("v2_flurry", "v2_beta")

    private fun ids(query: RosterQuery) = buildRosterCards(
        assets = assets,
        unlockedIds = unlocked,
        favoriteIds = favorites,
        activePuppyId = "v1_alpha",
        query = query
    ).map { it.asset.assetId }

    @Test
    fun allDefaultsToUnlockedFirst() {
        assertEquals(
            listOf("v1_alpha", "v2_flurry", "v2_beta"),
            ids(RosterQuery())
        )
    }

    @Test
    fun unlockedFilterRemovesLocked() {
        assertEquals(
            listOf("v1_alpha", "v2_flurry"),
            ids(RosterQuery(status = RosterStatusFilter.UNLOCKED))
        )
    }

    @Test
    fun lockedFilterRemovesUnlocked() {
        assertEquals(
            listOf("v2_beta"),
            ids(RosterQuery(status = RosterStatusFilter.LOCKED))
        )
    }

    @Test
    fun categoryFilterMatchesRuntimeGroupId() {
        assertEquals(
            listOf("v2_flurry", "v2_beta"),
            ids(RosterQuery(categoryId = "v2"))
        )
    }

    @Test
    fun searchMatchesDisplayNameCaseInsensitively() {
        assertEquals(
            listOf("v2_flurry"),
            ids(RosterQuery(search = "FLUR"))
        )
    }

    @Test
    fun searchMatchesPartialAssetIdCaseInsensitively() {
        assertEquals(
            listOf("v2_flurry"),
            ids(RosterQuery(search = "V2_FL"))
        )
    }

    @Test
    fun favoritesOnlyCanReturnLockedPuppies() {
        assertEquals(
            listOf("v2_flurry", "v2_beta"),
            ids(RosterQuery(favoritesOnly = true))
        )
    }

    @Test
    fun nameAscendingSortsByDisplayName() {
        assertEquals(
            listOf("v1_alpha", "v2_beta", "v2_flurry"),
            ids(RosterQuery(sort = RosterSort.NAME_ASC))
        )
    }

    @Test
    fun nameDescendingSortsByDisplayName() {
        assertEquals(
            listOf("v2_flurry", "v2_beta", "v1_alpha"),
            ids(RosterQuery(sort = RosterSort.NAME_DESC))
        )
    }

    @Test
    fun newestUsesAddedOrderThenStableSourceIndex() {
        assertEquals(
            listOf("v2_flurry", "v1_alpha", "v2_beta"),
            ids(RosterQuery(sort = RosterSort.NEWEST_ADDED))
        )
    }

    @Test
    fun oldestUsesAddedOrderThenStableSourceIndex() {
        assertEquals(
            listOf("v1_alpha", "v2_flurry", "v2_beta"),
            ids(RosterQuery(sort = RosterSort.OLDEST_ADDED))
        )
    }

    @Test
    fun categorySortUsesGroupOrderThenName() {
        assertEquals(
            listOf("v1_alpha", "v2_beta", "v2_flurry"),
            ids(RosterQuery(sort = RosterSort.CATEGORY))
        )
    }

    @Test
    fun categoriesComeFromRuntimeAssetGroups() {
        assertEquals(
            listOf(
                RosterCategory("v1", "V1 Puppies", 1),
                RosterCategory("v2", "V2 Puppies", 2)
            ),
            dynamicRosterCategories(assets)
        )
    }

    @Test
    fun missingAddedOrderStillSortsDeterministically() {
        val noDates = listOf(
            asset("zeta", "Zeta", "future", "Future Puppies", 7),
            asset("alpha", "Alpha", "future", "Future Puppies", 7)
        )
        val result = buildRosterCards(
            assets = noDates,
            unlockedIds = emptySet(),
            favoriteIds = emptySet(),
            activePuppyId = "none",
            query = RosterQuery(sort = RosterSort.NEWEST_ADDED)
        )
        assertEquals(listOf("zeta", "alpha"), result.map { it.asset.assetId })
    }
}
