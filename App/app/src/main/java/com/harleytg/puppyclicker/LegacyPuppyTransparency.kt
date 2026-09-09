package com.harleytg.puppyclicker

import android.graphics.Bitmap

/**
 * Restores transparency for the original V1/V2 PNGs without erasing dark puppy details.
 *
 * Those legacy files were exported as opaque RGB PNGs on a near-black matte. We only
 * remove near-black pixels that are connected to an outer image edge, so enclosed dark
 * eyes, noses, outlines, clothing, and markings remain untouched.
 */
internal object LegacyPuppyTransparency {
    private const val MATTE_MAX_CHANNEL = 20

    fun apply(assetId: String, source: Bitmap): Bitmap {
        val folder = DynamicPuppyRoster.assetByAssetId(assetId)?.folder ?: return source
        if (folder != "v1" && folder != "v2") return source
        if (source.hasAlpha()) return source
        if (source.width <= 0 || source.height <= 0) return source

        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // The queue also acts as our visited set: a pixel is made alpha=0 when queued.
        val queue = IntArray(pixels.size)
        var head = 0
        var tail = 0

        fun enqueue(index: Int) {
            val pixel = pixels[index]
            if ((pixel ushr 24) == 0 || !isMatte(pixel)) return
            pixels[index] = pixel and 0x00FFFFFF
            queue[tail++] = index
        }

        for (x in 0 until width) {
            enqueue(x)
            if (height > 1) enqueue((height - 1) * width + x)
        }
        for (y in 1 until height - 1) {
            enqueue(y * width)
            if (width > 1) enqueue(y * width + width - 1)
        }

        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            if (x > 0) enqueue(index - 1)
            if (x + 1 < width) enqueue(index + 1)
            if (y > 0) enqueue(index - width)
            if (y + 1 < height) enqueue(index + width)
        }

        if (tail == 0) return source

        val transparent = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        transparent.setHasAlpha(true)
        transparent.setPixels(pixels, 0, width, 0, 0, width, height)
        return transparent
    }

    private fun isMatte(pixel: Int): Boolean {
        val red = (pixel ushr 16) and 0xFF
        val green = (pixel ushr 8) and 0xFF
        val blue = pixel and 0xFF
        return red <= MATTE_MAX_CHANNEL &&
            green <= MATTE_MAX_CHANNEL &&
            blue <= MATTE_MAX_CHANNEL
    }
}
