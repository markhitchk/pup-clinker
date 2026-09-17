package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyGachaEngineTest {
    private val buddy = PuppyStyle(
        id = "buddy_test",
        name = "Buddy Test",
        emoji = "🐶",
        description = "Eligible test puppy."
    )
    private val sunny = PuppyStyle(
        id = "sunny_test",
        name = "Sunny Test",
        emoji = "☀️",
        description = "Another eligible test puppy."
    )
    private val special = PuppyStyle(
        id = "special_test",
        name = "Special Test",
        emoji = "⭐",
        description = "Redeem-only test puppy.",
        redeemOnly = true
    )

    @Test
    fun eligiblePoolExcludesOwnedAndRedeemOnlyPuppies() {
        val eligible = PuppyGachaEngine.eligiblePuppies(
            styles = listOf(buddy, sunny, special, sunny),
            unlocked = setOf(buddy.id)
        )

        assertEquals(listOf(sunny.id), eligible.map { it.id })
    }

    @Test
    fun allEligiblePoolStillListsEveryNonRedeemOnlyPuppy() {
        val eligible = PuppyGachaEngine.allEligiblePuppies(
            styles = listOf(buddy, sunny, special, sunny)
        )

        assertEquals(listOf(buddy.id, sunny.id), eligible.map { it.id })
    }

    @Test
    fun pullPoolContainsOnlyUnownedEligiblePuppies() {
        val styles = listOf(buddy, sunny, special)

        assertEquals(
            listOf(sunny.id),
            PuppyGachaEngine.pullPool(styles, setOf(buddy.id)).map { it.id }
        )
    }

    @Test
    fun pullPoolReturnsEmptyWhenEveryEligiblePuppyIsAlreadyOwned() {
        val styles = listOf(buddy, sunny, special)

        assertTrue(
            PuppyGachaEngine.pullPool(styles, setOf(buddy.id, sunny.id)).isEmpty()
        )
    }

    @Test
    fun selectionAlwaysComesFromEligiblePool() {
        val candidates = listOf(buddy, sunny)

        assertEquals(buddy, PuppyGachaEngine.select(candidates, 0))
        assertEquals(sunny, PuppyGachaEngine.select(candidates, 1))
        assertEquals(buddy, PuppyGachaEngine.select(candidates, 2))
    }

    @Test
    fun emptyCollectionReturnsNoSelection() {
        assertNull(PuppyGachaEngine.select(emptyList(), 0))
    }

    @Test
    fun capsuleCostIsPositiveAndFixed() {
        assertTrue(PuppyGachaEngine.COST_TREATS > 0)
        assertEquals(2_500L, PuppyGachaEngine.COST_TREATS)
        assertEquals(1, PuppyGachaEngine.COST_COMMON_TICKETS)
    }
}
