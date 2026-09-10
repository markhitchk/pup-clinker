package com.harleytg.puppyclicker

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyArtRetryKeyTest {
    @Test
    fun retryTokenChangesRequestIdentityWithoutChangingAssetIdentity() {
        val first = streamedPuppyRequestKey("v2_flurry", 0)
        val retry = streamedPuppyRequestKey("v2_flurry", 1)

        assertNotEquals(first, retry)
        assertTrue(first.startsWith("v2_flurry#retry="))
        assertTrue(retry.startsWith("v2_flurry#retry="))
    }
}
