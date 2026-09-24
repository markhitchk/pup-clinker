package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCodeGeneratedIntegrationTest {
    @Test
    fun v6ClaimsRequireLiveSchema2Authorization() {
        val relative = "generated/protected-puppies/source/com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt"
        val generated = listOf(
            File("app/build/$relative"),
            File("build/$relative")
        ).firstOrNull(File::isFile)
            ?: error("Generated PuppyClickerV6ViewModel.kt was not found")

        val source = generated.readText()
        assertTrue(
            "V6 Puppy Code claims must perform a live GitHub authorization",
            source.contains("StreamedRedeemCodes.refreshForClaim")
        )
        assertFalse(
            "V6 claims must not use the deprecated local StreamedRedeemCodes.find fallback",
            source.contains("StreamedRedeemCodes.find(rawCode)")
        )
    }

    @Test
    fun liveCatalogueIsSchema2AndParsableByTheInstalledClient() {
        val catalogue = listOf(
            File("../../assets/redeem-codes.json"),
            File("../assets/redeem-codes.json"),
            File("assets/redeem-codes.json")
        ).firstOrNull(File::isFile)
            ?: error("assets/redeem-codes.json was not found")

        val parsed = PuppyCodeCatalog.parse(catalogue.readText())
        assertTrue(parsed.codesByHash.isNotEmpty())
    }

    @Test
    fun discordRolePuppiesStayOutOfRedeemCatalogue() {
        val catalogue = listOf(
            File("../../assets/redeem-codes.json"),
            File("../assets/redeem-codes.json"),
            File("assets/redeem-codes.json")
        ).firstOrNull(File::isFile)
            ?: error("assets/redeem-codes.json was not found")

        val parsed = PuppyCodeCatalog.parse(catalogue.readText())
        val puppyIds = parsed.codesByHash.values
            .flatMap { definition -> definition.rewards }
            .filterIsInstance<PuppyCodeReward.Puppy>()
            .mapTo(mutableSetOf()) { it.puppyId }

        assertFalse("Discord Pup must remain auth-only", "v2_discord_pup" in puppyIds)
        assertFalse("V2 Dev Pup must remain Discord Developer-role only", "v2_dev_pup" in puppyIds)
        assertFalse("HarleyTG V2 puppy must remain on its special unlock path", "v2_harleytg" in puppyIds)

        listOf(
            "v2_frost",
            "v2_honey",
            "v2_biscuit",
            "v2_onyx",
            "v2_domino",
            "v2_chestnut",
            "v2_prism",
            "v2_flurry"
        ).forEach { puppyId ->
            assertTrue("Expected live Puppy Code reward for $puppyId", puppyId in puppyIds)
        }
    }
}
