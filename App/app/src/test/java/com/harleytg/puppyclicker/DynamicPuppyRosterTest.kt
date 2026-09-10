package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun manifestParsesExchangeTransferPolicyAndHiddenState() {
        val json = """
            {
              "roster": "v3",
              "puppies": [
                {
                  "asset_id": "v3_frog_gift",
                  "file": "v3_frog_gift.png",
                  "name": "Frog Gift Puppy",
                  "free": false,
                  "redeem_only": true,
                  "hidden_until_owned": true,
                  "transfer_policy": {
                    "giftable": true,
                    "tradeable": false,
                    "source_copy": true,
                    "limited": true
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = DynamicPuppyRoster.parseManifest("v3", json).single()
        assertTrue(parsed.hiddenUntilOwned)
        assertTrue(parsed.transferPolicy.giftable)
        assertFalse(parsed.transferPolicy.tradeable)
        assertTrue(parsed.transferPolicy.sourceCopy)
        assertTrue(parsed.transferPolicy.limited)
        assertFalse(parsed.transferPolicy.bound)
    }

    @Test
    fun missingExchangePolicyUsesNonTransferableVisibleDefaults() {
        val json = """
            {
              "roster": "v3",
              "puppies": [
                {"asset_id":"v3_plain","file":"v3_plain.png","name":"Plain","free":true}
              ]
            }
        """.trimIndent()

        val parsed = DynamicPuppyRoster.parseManifest("v3", json).single()
        assertFalse(parsed.hiddenUntilOwned)
        assertEquals(PuppyTransferPolicy(), parsed.transferPolicy)
        assertNull(parsed.addedOrder)
        assertNull(parsed.unlockSource)
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundManifestCannotAlsoBeTransferable() {
        val json = """
            {
              "roster": "v3",
              "puppies": [
                {
                  "asset_id":"v3_custom",
                  "file":"v3_custom.png",
                  "free":false,
                  "transfer_policy":{"bound":true,"giftable":true}
                }
              ]
            }
        """.trimIndent()

        DynamicPuppyRoster.parseManifest("v3", json)
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
