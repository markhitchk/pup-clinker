package com.harleytg.puppyclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamedRedeemCodesTest {
    @Test
    fun staleCacheCannotAuthorizeClaimByItself() {
        val result = StreamedRedeemCodes.claimAuthorizationForTest(
            liveCheckSucceeded = false,
            notModified = false,
            hasValidatedCache = true
        )
        assertFalse(result.authorized)
    }

    @Test
    fun http304CanAuthorizePreviouslyValidatedCache() {
        val result = StreamedRedeemCodes.claimAuthorizationForTest(
            liveCheckSucceeded = true,
            notModified = true,
            hasValidatedCache = true
        )
        assertTrue(result.authorized)
    }

    @Test
    fun http304CannotAuthorizeWithoutValidatedCache() {
        val result = StreamedRedeemCodes.claimAuthorizationForTest(
            liveCheckSucceeded = true,
            notModified = true,
            hasValidatedCache = false
        )
        assertFalse(result.authorized)
    }

    @Test
    fun successfulFreshDownloadCanAuthorizeClaim() {
        val result = StreamedRedeemCodes.claimAuthorizationForTest(
            liveCheckSucceeded = true,
            notModified = false,
            hasValidatedCache = false
        )
        assertTrue(result.authorized)
    }
}
