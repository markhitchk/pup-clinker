package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class PuppyRosterFavoritesTest {
    @Test
    fun knownPuppyCanBeAddedAndRemovedWithoutUnlockState() {
        val known = setOf("v2_flurry", "v3_locked")

        val added = toggleRosterFavorite(
            knownIds = known,
            favoriteIds = setOf("v2_flurry"),
            id = "v3_locked"
        )
        assertEquals(setOf("v2_flurry", "v3_locked"), added)

        val removed = toggleRosterFavorite(
            knownIds = known,
            favoriteIds = added,
            id = "v3_locked"
        )
        assertEquals(setOf("v2_flurry"), removed)
    }

    @Test
    fun unknownPuppyDoesNotPolluteFavorites() {
        assertEquals(
            setOf("v2_flurry"),
            toggleRosterFavorite(
                knownIds = setOf("v2_flurry"),
                favoriteIds = setOf("v2_flurry"),
                id = "not_in_roster"
            )
        )
    }
}
