package com.harleytg.puppyclicker

import android.content.Context

internal object PuppyPerformancePreferences {
    const val KEY_PERFORMANCE_PRESET = "performance_preset_v1"

    fun inferPreset(
        adaptive: Boolean,
        celebrations: Boolean,
        shimmer: Boolean
    ): PuppyPerformancePreset = when {
        adaptive && celebrations && shimmer -> PuppyPerformancePreset.AUTOMATIC
        !adaptive && celebrations && shimmer -> PuppyPerformancePreset.QUALITY
        !adaptive && !celebrations && !shimmer -> PuppyPerformancePreset.BATTERY_SAVER
        else -> PuppyPerformancePreset.AUTOMATIC
    }

    fun current(context: Context): PuppyPerformancePreset {
        val game = context.applicationContext.getSharedPreferences(
            PuppyClickerV6ViewModel.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val saved = game.getString(KEY_PERFORMANCE_PRESET, null)
        if (!saved.isNullOrBlank()) {
            return runCatching { PuppyPerformancePreset.valueOf(saved) }
                .getOrDefault(PuppyPerformancePreset.AUTOMATIC)
        }

        val ui = PuppyUiPreferences.current(context)
        return inferPreset(
            adaptive = ui.adaptivePerformance,
            celebrations = ui.celebrationAnimations,
            shimmer = ui.shimmerEffects
        )
    }

    fun set(context: Context, preset: PuppyPerformancePreset) {
        val app = context.applicationContext
        app.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PERFORMANCE_PRESET, preset.name)
            .apply()

        val resolution = PuppyPerformancePolicy.resolve(
            preset = preset,
            reducedMotion = PuppyUiPreferences.current(app).reducedMotion
        )
        app.getSharedPreferences(PuppyUiPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("adaptive_performance", resolution.adaptivePerformance)
            .putBoolean("celebration_animations", resolution.celebrations)
            .putBoolean("shimmer_effects", resolution.shimmer)
            .apply()
    }
}
