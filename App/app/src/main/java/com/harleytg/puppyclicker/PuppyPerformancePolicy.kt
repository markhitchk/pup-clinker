package com.harleytg.puppyclicker

enum class PuppyPerformancePreset {
    AUTOMATIC,
    QUALITY,
    BATTERY_SAVER
}

data class PuppyPerformanceResolution(
    val adaptivePerformance: Boolean,
    val celebrations: Boolean,
    val shimmer: Boolean,
    val nonessentialMotion: Boolean,
    val coreFeedback: Boolean
)

internal object PuppyPerformancePolicy {
    fun resolve(
        preset: PuppyPerformancePreset,
        reducedMotion: Boolean
    ): PuppyPerformanceResolution {
        val base = when (preset) {
            PuppyPerformancePreset.AUTOMATIC -> PuppyPerformanceResolution(
                adaptivePerformance = true,
                celebrations = true,
                shimmer = true,
                nonessentialMotion = true,
                coreFeedback = true
            )
            PuppyPerformancePreset.QUALITY -> PuppyPerformanceResolution(
                adaptivePerformance = false,
                celebrations = true,
                shimmer = true,
                nonessentialMotion = true,
                coreFeedback = true
            )
            PuppyPerformancePreset.BATTERY_SAVER -> PuppyPerformanceResolution(
                adaptivePerformance = false,
                celebrations = false,
                shimmer = false,
                nonessentialMotion = false,
                coreFeedback = true
            )
        }
        return if (!reducedMotion) base else base.copy(nonessentialMotion = false)
    }
}
