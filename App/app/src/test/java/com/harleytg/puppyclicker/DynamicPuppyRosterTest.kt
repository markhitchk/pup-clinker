package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicPuppyRosterTest {
    @Test
    fun manifestParsesOptionalAddedOrderAndUnlockSource() {
        val json = """
            {
              "roster": "v3",
              "puppies": [
                {
                  "asset_id": "v3_star",
                  "file": "v3_star.png",
                  "name": "Star",
                  "free": false,
                  "redeem_only": true,
                  "added_order": 42,
                  "unlock_source": "Puppy Code"
                }
              ]
            }
        """.trimIndent()

        val parsed = DynamicPuppyRoster.parseManifest("v3", json).single()
        assertEquals(42L, parsed.addedOrder)
        assertEquals("Puppy Code", parsed.unlockSource)
    }

    @Test
    fun missingOptionalMetadataStaysNull() {
        val json = """
            {
              "roster": "v3",
              "puppies": [
                {"asset_id":"v3_plain","file":"v3_plain.png","name":"Plain","free":true}
              ]
            }
        """.trimIndent()

        val parsed = DynamicPuppyRoster.parseManifest("v3", json).single()
        assertNull(parsed.addedOrder)
        assertNull(parsed.unlockSource)
    }

    @Test
    fun assetStreamAndGroupStreamDescribeTheSameStartupRoster() {
        val assetIds = DynamicPuppyRoster.assets.value.map { it.style.id }.toSet()
        val groupedIds = DynamicPuppyRoster.groups.value
            .flatMap { it.puppies }
            .map { it.id }
            .toSet()

        assertTrue(assetIds.isNotEmpty())
        assertEquals(assetIds, groupedIds)
    }
}
