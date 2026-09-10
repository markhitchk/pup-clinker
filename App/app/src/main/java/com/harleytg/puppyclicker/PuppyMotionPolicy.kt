package com.harleytg.puppyclicker

enum class PuppyMotionPreset {
    MINIMAL,
    BALANCED,
    PLAYFUL,
    CUSTOM
}

enum class PuppyMotionIntensity {
    LOW,
    MEDIUM,
    HIGH
}

enum class PuppyMotionMode {
    FULL,
    PERFORMANCE,
    REDUCED,
    STATIC
}

data class PuppyMotionConfig(
    val preset: PuppyMotionPreset,
    val intensity: PuppyMotionIntensity,
    val animatedUi: Boolean,
    val buttonAnimations: Boolean,
    val screenTransitions: Boolean,
    val cardAnimations: Boolean,
    val counterAnimations: Boolean,
    val celebrations: Boolean,
    val warningAnimations: Boolean,
    val loadingAnimations: Boolean,
    val shimmerEffects: Boolean,
    val adaptivePerformance: Boolean,
    val reducedMotion: Boolean = false
)

object PuppyMotionPresets {
    fun config(preset: PuppyMotionPreset): PuppyMotionConfig = when (preset) {
        PuppyMotionPreset.MINIMAL -> PuppyMotionConfig(
            preset = PuppyMotionPreset.MINIMAL,
            intensity = PuppyMotionIntensity.LOW,
            animatedUi = true,
            buttonAnimations = false,
            screenTransitions = true,
            cardAnimations = false,
            counterAnimations = false,
            celebrations = false,
            warningAnimations = true,
            loadingAnimations = true,
            shimmerEffects = false,
            adaptivePerformance = true
        )

        PuppyMotionPreset.BALANCED -> PuppyMotionConfig(
            preset = PuppyMotionPreset.BALANCED,
            intensity = PuppyMotionIntensity.MEDIUM,
            animatedUi = true,
            buttonAnimations = true,
            screenTransitions = true,
            cardAnimations = true,
            counterAnimations = true,
            celebrations = true,
            warningAnimations = true,
            loadingAnimations = true,
            shimmerEffects = true,
            adaptivePerformance = true
        )

        PuppyMotionPreset.PLAYFUL -> PuppyMotionConfig(
            preset = PuppyMotionPreset.PLAYFUL,
            intensity = PuppyMotionIntensity.HIGH,
            animatedUi = true,
            buttonAnimations = true,
            screenTransitions = true,
            cardAnimations = true,
            counterAnimations = true,
            celebrations = true,
            warningAnimations = true,
            loadingAnimations = true,
            shimmerEffects = true,
            adaptivePerformance = true
        )

        PuppyMotionPreset.CUSTOM -> config(PuppyMotionPreset.BALANCED).copy(preset = PuppyMotionPreset.CUSTOM)
    }
}

object PuppyMotionPolicy {
    fun mode(config: PuppyMotionConfig, performanceConstrained: Boolean): PuppyMotionMode = when {
        config.reducedMotion -> PuppyMotionMode.REDUCED
        !config.animatedUi -> PuppyMotionMode.STATIC
        config.adaptivePerformance && performanceConstrained -> PuppyMotionMode.PERFORMANCE
        else -> PuppyMotionMode.FULL
    }

    fun afterManualOverride(current: PuppyMotionPreset): PuppyMotionPreset = PuppyMotionPreset.CUSTOM
}
