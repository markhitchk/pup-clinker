package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamedImageFallbackContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/$name").readText()

    @Test
    fun everyStreamedImagePipelineUsesSharedThemeAwareFallback() {
        assertTrue(source("StreamedPuppyArt.kt").contains("streamedImageFallbackPainter()"))
        assertTrue(source("StreamedRepoLogos.kt").contains("streamedImageFallbackPainter()"))
        assertTrue(source("StreamedPupEyeBranding.kt").contains("streamedImageFallbackPainter()"))
        assertTrue(source("StreamedPupCoin.kt").contains("streamedImageFallbackPainter()"))

        assertTrue(File("src/main/res/drawable/stream_fallback_light.xml").isFile)
        assertTrue(File("src/main/res/drawable/stream_fallback_dark.xml").isFile)
    }
}
