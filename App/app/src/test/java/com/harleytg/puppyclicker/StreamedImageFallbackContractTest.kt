package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamedImageFallbackContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/$name").readText()

    @Test
    fun everyStreamedImagePipelineUsesSharedFallbackPng() {
        assertTrue(source("StreamedPuppyArt.kt").contains("streamedImageFallbackPainter()"))
        assertTrue(source("StreamedRepoLogos.kt").contains("streamedImageFallbackPainter()"))
        assertTrue(source("StreamedPupEyeBranding.kt").contains("streamedImageFallbackPainter()"))
        assertTrue(source("StreamedPupCoin.kt").contains("streamedImageFallbackPainter()"))

        assertTrue(source("StreamedImageFallback.kt").contains("R.drawable.fallback"))
        assertTrue(File("src/main/res/drawable-nodpi/fallback.png").isFile)
        assertTrue(
            !source("StreamedPuppyArt.kt").contains("ProtectedPuppyPortrait(style.id")
        )
    }
}
