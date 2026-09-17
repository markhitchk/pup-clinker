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
    private val legacyRedeemOnlyNormal = PuppyStyle(
        id = "aurora",
        name = "Aurora",
        emoji = "🌌",
        description = "Normal roster puppy using the legacy redeem-only flag.",
        redeemOnly = true
    )
    private val developerExclusive = PuppyStyle(
        id = "dev_pup",
        name = "Dev Pup",
        emoji = "🛠️",
        description = "Developer-only puppy.",
        redeemOnly = true
    )
    private val seasonalExclusive = PuppyStyle(
        id = "halloween",
        name = "Pumpkin Pup",
        emoji = "🎃",
        description = "Seasonal event puppy.",
        redeemOnly = true
    )
    private val codeExclusive = PuppyStyle(
        id = "secret_snoot",
        name = "Secret Snoot",
        emoji = "🤫",
        description = "Code-only puppy.",
        redeemOnly = true
    )

    @Test
    fun normalLegacyRedeemOnlyPuppiesRemainGachaEligible() {
        val eligible = PuppyGachaEngine.allEligiblePuppies(
            styles = listOf(buddy, legacyRedeemOnlyNormal, sunny)
        )

        assertEquals(
            listOf(legacyRedeemOnlyNormal.id, buddy.id, sunny.id),
            eligible.map { it.id }
        )
    }

    @Test
    fun trueSpecialExclusivesStayOutOfGacha() {
        val eligible = PuppyGachaEngine.allEligiblePuppies(
            styles = listOf(
                buddy,
                developerExclusive,
                seasonalExclusive,
                codeExclusive,
                sunny
            )
        )

        assertEquals(listOf(buddy.id, sunny.id), eligible.map { it.id })
    }

    @Test
    fun eligiblePoolExcludesOwnedButKeepsUnownedNormalRedeemOnlyPuppies() {
        val eligible = PuppyGachaEngine.eligiblePuppies(
            styles = listOf(buddy, legacyRedeemOnlyNormal, sunny, sunny),
            unlocked = setOf(buddy.id)
        )

        assertEquals(
            listOf(legacyRedeemOnlyNormal.id, sunny.id),
            eligible.map { it.id }
        )
    }

    @Test
    fun pullPoolReturnsEmptyOnlyWhenEveryNormalEligiblePuppyIsOwned() {
        val styles = listOf(
            buddy,
            legacyRedeemOnlyNormal,
            sunny,
            developerExclusive,
            seasonalExclusive,
            codeExclusive
        )

        assertTrue(
            PuppyGachaEngine.pullPool(
                styles,
                setOf(buddy.id, legacyRedeemOnlyNormal.id, sunny.id)
            ).isEmpty()
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
