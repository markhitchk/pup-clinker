package com.harleytg.puppyclicker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamedRedeemCodesTest {
    @After
    fun resetStreamedCatalog() {
        StreamedRedeemCodes.replaceForTest(null)
    }

    @Test
    fun compiledCatalogIsFallbackBeforeAnyStreamedCatalogLoads() {
        StreamedRedeemCodes.replaceForTest(null)
        assertEquals("good_boy_26", StreamedRedeemCodes.find("GOOD-BOY-26")?.id)
    }

    @Test
    fun streamedCatalogIsAuthoritativeAndCanRevokeCompiledCodes() {
        StreamedRedeemCodes.replaceForTest(emptyMap())
        assertNull(StreamedRedeemCodes.find("GOOD-BOY-26"))
    }

    @Test
    fun streamedCatalogCanAddARewardWithoutAnApkUpdate() {
        val hash = "67cda329535c8dc7348c72a276c1b8d6e88c29997d608151710eeaff5ee74d47"
        val reward = LocalRedeemReward(
            id = "stream_test",
            treats = 1234,
            message = "Streamed reward loaded."
        )
        StreamedRedeemCodes.replaceForTest(mapOf(hash to reward))
        assertEquals(reward, StreamedRedeemCodes.find("TESTCODE"))
    }
}
