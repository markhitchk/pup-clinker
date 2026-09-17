package com.harleytg.puppyclicker

enum class PuppyWindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

internal object PuppyAdaptiveLayout {
    fun widthClass(widthDp: Float): PuppyWindowWidthClass = when {
        widthDp < 600f -> PuppyWindowWidthClass.COMPACT
        widthDp < 840f -> PuppyWindowWidthClass.MEDIUM
        else -> PuppyWindowWidthClass.EXPANDED
    }

    fun useRosterTwoPane(widthClass: PuppyWindowWidthClass): Boolean =
        widthClass == PuppyWindowWidthClass.EXPANDED
}
