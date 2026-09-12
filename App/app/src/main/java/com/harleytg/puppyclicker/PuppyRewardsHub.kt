package com.harleytg.puppyclicker

import androidx.compose.runtime.Composable

/**
 * Compatibility entry point for older navigation and generated builds.
 * The visual implementation lives in PuppyMainScreensRevamp.kt so Rewards
 * uses the same card hierarchy, spacing and accent treatment as onboarding
 * and Settings.
 */
@Composable
internal fun PuppyRewardsHub(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onOpenPrestige: () -> Unit
) {
    PuppyRevampedRewardsScreen(
        state = state,
        vm = vm,
        onOpenPrestige = onOpenPrestige
    )
}
