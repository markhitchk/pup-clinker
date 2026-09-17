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
