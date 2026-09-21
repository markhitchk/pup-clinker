package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyNotificationHistoryTest {
    private fun item(id: String, at: Long, read: Boolean = false) = PuppyNotificationItem(
        id = id,
        type = PuppyNotificationType.APP_UPDATE,
        title = id,
        body = "body-$id",
        createdAtMs = at,
        read = read,
        route = PuppyNotificationRoute.RELEASE_HUB
    )

    @Test
    fun jsonRoundTripPreservesItems() {
        val items = listOf(item("b", 2), item("a", 1, read = true))
        val decoded = PuppyNotificationHistoryCodec.decode(PuppyNotificationHistoryCodec.encode(items))
        assertEquals(items.sortedWith(PuppyNotificationHistoryCodec.NEWEST_FIRST), decoded)
    }

    @Test
    fun systemRewardJsonRoundTripPreservesClaimStateAndCurrency() {
        val reward = PuppyNotificationItem(
            id = "afk:100:200",
            type = PuppyNotificationType.SYSTEM_REWARD,
            title = "Your puppies saved some Treats!",
            body = "Claim your reward.",
            createdAtMs = 10L,
            read = false,
            route = PuppyNotificationRoute.NONE,
            rewardCurrency = PuppyRewardCurrency.TREATS,
            rewardAmount = 500L,
            claimed = false
        )
        val decoded = PuppyNotificationHistoryCodec.decode(
            PuppyNotificationHistoryCodec.encode(listOf(reward))
        ).single()

        assertEquals(PuppyRewardCurrency.TREATS, decoded.rewardCurrency)
        assertEquals(500L, decoded.rewardAmount)
        assertTrue(decoded.hasClaimableReward)

        val claimed = PuppyNotificationHistoryCodec.markRewardClaimed(listOf(decoded), decoded.id).single()
        assertTrue(claimed.claimed)
        assertTrue(claimed.read)
        assertFalse(claimed.hasClaimableReward)
    }

    @Test
    fun insertionDeduplicatesAndKeepsNewestHundred() {
        var values = emptyList<PuppyNotificationItem>()
        repeat(105) { index ->
            values = PuppyNotificationHistoryCodec.insert(values, item("id-$index", index.toLong()))
        }
        assertEquals(100, values.size)
        assertEquals("id-104", values.first().id)
        assertEquals("id-5", values.last().id)
        val same = PuppyNotificationHistoryCodec.insert(values, item("id-104", 104L))
        assertEquals(100, same.size)
    }

    @Test
    fun unclaimedSystemRewardSurvivesHistoryTrimming() {
        val reward = PuppyNotificationItem(
            id = "afk:protected",
            type = PuppyNotificationType.SYSTEM_REWARD,
            title = "AFK reward",
            body = "claim",
            createdAtMs = 1L,
            read = false,
            route = PuppyNotificationRoute.NONE,
            rewardCurrency = PuppyRewardCurrency.TREATS,
            rewardAmount = 500L
        )
        val noisy = (0 until 105).map { index ->
            item("regular-$index", 1_000L + index)
        }
        val normalized = PuppyNotificationHistoryCodec.normalize(noisy + reward)

        assertEquals(PuppyNotificationHistoryCodec.MAX_ITEMS, normalized.size)
        assertTrue(normalized.any { it.id == reward.id && it.hasClaimableReward })
    }

    @Test
    fun equalTimestampUsesStableIdTieBreak() {
        val values = PuppyNotificationHistoryCodec.normalize(
            listOf(item("a", 10), item("c", 10), item("b", 10))
        )
        assertEquals(listOf("c", "b", "a"), values.map { it.id })
    }

    @Test
    fun readOperationsAreExplicit() {
        val source = listOf(item("a", 3), item("b", 2))
        val one = PuppyNotificationHistoryCodec.markRead(source, "a")
        assertTrue(one.first { it.id == "a" }.read)
        assertFalse(one.first { it.id == "b" }.read)
        assertTrue(PuppyNotificationHistoryCodec.markAllRead(source).all { it.read })
    }
}
