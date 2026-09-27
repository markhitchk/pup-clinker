package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamedImageFallbackContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/$name").readText()

    @Test
    fun everyStreamedImagePipelineUsesSharedFallbackPng() {
        val puppy = source("StreamedPuppyArt.kt")
        val logos = source("StreamedRepoLogos.kt")
        val pupEye = source("StreamedPupEyeBranding.kt")
        val coin = source("StreamedPupCoin.kt")
        val fallback = source("StreamedImageFallback.kt")

        assertTrue(puppy.contains("streamedImageFallbackPainter(style.id, background)"))
        assertTrue(logos.contains("streamedImageFallbackPainter(\"repo_logo:\" + asset.name.lowercase())"))
        assertTrue(pupEye.contains("streamedImageFallbackPainter(\"pupeye_branding\")"))
        assertTrue(coin.contains("streamedImageFallbackPainter(\"pup_coin\")"))

        assertTrue(fallback.contains("R.drawable.fallback"))
        assertTrue(fallback.contains("streamedFallbackBackgroundColor(assetKey)"))
        assertTrue(fallback.contains("canvas.drawColor(background.toArgb())"))
        assertTrue(fallback.contains("BitmapPainter(composed.asImageBitmap())"))
        assertTrue(File("src/main/res/drawable-nodpi/fallback.png").isFile)

        // Streamed image failures must stay in the shared PNG fallback path.
        assertFalse(puppy.contains("ProtectedPuppyPortrait(style.id"))
        assertFalse(fallback.contains("stream_fallback_light"))
        assertFalse(fallback.contains("stream_fallback_dark"))
    }
}
