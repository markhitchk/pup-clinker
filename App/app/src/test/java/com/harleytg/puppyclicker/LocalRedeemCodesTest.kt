package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRedeemCodesTest {
    @Suppress("UNCHECKED_CAST")
    private fun catalog(): Map<String, LocalRedeemReward> {
        val field = LocalRedeemCodes::class.java.getDeclaredField("rewards")
        field.isAccessible = true
        return field.get(LocalRedeemCodes) as Map<String, LocalRedeemReward>
    }

    @Test fun seasonalPuppiesHaveNoRedeemCodes() {
        val rewards = catalog()
        val retiredHashes = setOf(
            "701ef2213a91025138e788960e06de5982be75e3515c3ba5c6bc6e71cc8e885c",
            "54c783e30226e902ad806048a15e71311528cde67be2d6253a1cb6581737f5cd",
            "62d6bd02529b36e5ad99632aca7a7ea339a273203ec20e973e159c15238de8a1"
        )
        retiredHashes.forEach { assertFalse("Retired seasonal code is still registered", rewards.containsKey(it)) }
        assertTrue("Event-only puppies must not be redeem rewards",
            rewards.values.none { it.puppyId?.let(SeasonalPuppyEvents::isSeasonal) == true })
    }

    @Test fun unrelatedCodesAndRewardsArePreserved() {
        val rewards = catalog()
        assertEquals(35, rewards.size)
        assertEquals(2_500L, rewards.getValue("3c7f2f76a3310dbcbc787ca9be87a6530d7020a66df7614ea1b9bf052944f74c").treats)
        assertEquals("snowball", rewards.getValue("f8c145053f3623125b58b65c5f17a5af55fab6ac3dfc5f67322ddbc2dc33db02").puppyId)
        assertEquals("Okay... ONE more treat. +250 treats.",
            rewards.getValue("6ee7c0b55cb72470dd834f94035f191088d51719875abb2e0e8e6bc928fcbcd6").message)
    }
}
