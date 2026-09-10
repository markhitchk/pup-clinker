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
            File("../assets/redeem-codes.json"),
            File("assets/redeem-codes.json")
        ).firstOrNull(File::isFile)
            ?: error("assets/redeem-codes.json was not found")

        val parsed = PuppyCodeCatalog.parse(catalogue.readText())
        assertTrue(parsed.codesByHash.isNotEmpty())
    }
}
