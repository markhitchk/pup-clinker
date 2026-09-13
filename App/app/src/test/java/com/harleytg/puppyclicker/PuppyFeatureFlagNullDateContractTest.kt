package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyFeatureFlagNullDateContractTest {
    @Test
    fun jsonNullReleaseDateStaysNullInsteadOfLiteralNullString() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyFeatureFlags.kt"
        ).readText()

        assertTrue(source.contains("item.isNull(\"releaseDate\")"))
        assertTrue(
            source.contains(
                "item.optString(\"releaseDate\").trim().ifBlank { null }"
            )
        )
    }

    @Test
    fun discordFallbackRemainsReleasedAndEnabled() {
        val source = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyFeatureFlags.kt"
        ).readText()

        assertTrue(
            source.contains(
                "\"discord_linking\", true, true, \"released\", null"
            )
        )
    }
}
