package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCodeCatalogTest {
    @Test
    fun exactHashDoesNotNormalizeCaseOrWhitespace() {
        val canonical = PuppyCodeCatalog.hashExact("BUDDY-HELLO-2026")
        assertNotEquals(canonical, PuppyCodeCatalog.hashExact("buddy-hello-2026"))
        assertNotEquals(canonical, PuppyCodeCatalog.hashExact("BUDDY-HELLO-2026 "))
        assertNotEquals(canonical, PuppyCodeCatalog.hashExact("BUDDYHELLO2026"))
    }

    @Test
    fun parserBuildsSchema2RewardBundle() {
        val snapshot = PuppyCodeCatalog.parse(
            """{
              "schema":2,
              "revision":"test-1",
              "rewards":[{
                "id":"bundle_test",
                "hash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "status":"active",
                "rarity":"special",
                "flags":["SPECIAL_REVEAL"],
                "message":"Bundle ready.",
                "rewards":[
                  {"type":"treats","amount":2500},
                  {"type":"upgrade_ticket","rarity":"RARE","amount":2},
                  {"type":"puppy","puppyId":"v2_flurry"}
                ]
              }]
            }""".trimIndent()
        )

        val code = snapshot.codesByHash.values.single()
        assertEquals("bundle_test", code.id)
        assertEquals(PuppyCodeRarity.SPECIAL, code.rarity)
        assertEquals(3, code.rewards.size)
        assertTrue(code.rewards[0] is PuppyCodeReward.Treats)
        assertTrue(code.rewards[1] is PuppyCodeReward.UpgradeTickets)
        assertTrue(code.rewards[2] is PuppyCodeReward.Puppy)
    }

    @Test
    fun unknownFlagFailsCodeValidation() {
        val snapshot = PuppyCodeCatalog.parse(
            """{
              "schema":2,
              "revision":"test-2",
              "rewards":[{
                "id":"future_flag",
                "hash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "status":"active",
                "flags":["FUTURE_FLAG"],
                "message":"Future.",
                "rewards":[{"type":"treats","amount":10}]
              }]
            }""".trimIndent()
        )
        val code = snapshot.codesByHash.values.single()
        val result = PuppyCodeValidator.validate(
            definition = code,
            versionCode = 22,
            redeemedIds = emptySet(),
            authoritativeTimeMs = 1_800_000_000_000L,
            channel = "stable"
        )
        assertTrue(result is PuppyCodeValidationResult.Rejected)
        assertEquals(
            PuppyCodeRejectReason.UNSUPPORTED_FLAG,
            (result as PuppyCodeValidationResult.Rejected).reason
        )
    }

    @Test
    fun alreadyRedeemedIsReportedBeforeGrant() {
        val snapshot = PuppyCodeCatalog.parse(
            """{
              "schema":2,
              "revision":"test-3",
              "rewards":[{
                "id":"used_code",
                "hash":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "status":"active",
                "message":"Used.",
                "rewards":[{"type":"treats","amount":10}]
              }]
            }""".trimIndent()
        )
        val code = snapshot.codesByHash.values.single()
        val result = PuppyCodeValidator.validate(
            definition = code,
            versionCode = 22,
            redeemedIds = setOf("used_code"),
            authoritativeTimeMs = 1_800_000_000_000L,
            channel = "stable"
        )
        assertEquals(
            PuppyCodeRejectReason.ALREADY_REDEEMED,
            (result as PuppyCodeValidationResult.Rejected).reason
        )
    }
}
