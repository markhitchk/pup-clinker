package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyExchangeGeneratedIntegrationTest {
    @Test
    fun rosterExchangeActionIsWiredIntoTheV6Shell() {
        val relative = "generated/protected-puppies/source/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt"
        val generated = listOf(
            File("app/build/$relative"),
            File("build/$relative")
        ).firstOrNull(File::isFile)
            ?: error("Generated PuppyClickerV6Activity.kt was not found")

        val source = generated.readText()
        assertTrue(
            "Roster must route its Puppy Exchange action into the internal Exchange destination",
            source.contains("onOpenExchange = { internalDestination = PuppyInternalDestination.EXCHANGE }")
        )
        assertTrue(
            "The V6 shell must render PuppyExchangeScreen for the Exchange destination",
            source.contains("PuppyInternalDestination.EXCHANGE -> PuppyExchangeScreen(")
        )
    }
}
