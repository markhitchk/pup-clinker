package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyMainUiRevampGeneratedIntegrationTest {
    private fun generated(name: String): File {
        val relative = "generated/protected-puppies/source/com/harleytg/puppyclicker/$name"
        return listOf(
            File("app/build/$relative"),
            File("build/$relative")
        ).firstOrNull(File::isFile)
            ?: error("Generated $name was not found")
    }

    @Test
    fun mainNavigationUsesRevampedGameScreens() {
        val source = generated("PuppyClickerV6Activity.kt").readText()

        assertTrue(source.contains("V6Tab.PLAY -> PuppyRevampedPlayScreen("))
        assertTrue(source.contains("V6Tab.CARE -> PuppyRevampedCareScreen(state, vm)"))
        assertTrue(source.contains("V6Tab.SHOP -> PuppyRevampedShopScreen(state, vm)"))
        assertTrue(source.contains("V6Tab.REWARDS -> PuppyRewardsHub("))

        // Exchange and Settings remain internal destinations rather than stealing a bottom tab.
        assertTrue(source.contains("PuppyInternalDestination.SETTINGS -> V6Settings(state, vm)"))
        assertTrue(source.contains("PuppyInternalDestination.EXCHANGE -> PuppyExchangeScreen("))
    }

    @Test
    fun revampedScreensKeepGameplayFeedbackAndLiveCodeValidation() {
        val source = generated("PuppyMainScreensRevamp.kt").readText()

        assertTrue(source.contains("V6TicketDropOverlay("))
        assertTrue(source.contains("performV6Haptic(context"))
        assertTrue(source.contains("vm.redeemCode(code) { result ->"))
        assertTrue(source.contains("PuppyRevampedRewardsScreen"))
        assertTrue(source.contains("PuppyRevampedCareScreen"))
    }

    @Test
    fun rosterKeepsExchangeWhileUsingRevampedVisualHierarchy() {
        val source = generated("PuppyRosterScreen.kt").readText()

        assertTrue(source.contains("style = MaterialTheme.typography.headlineMedium"))
        assertTrue(source.contains("Open Puppy Exchange"))
        assertTrue(source.contains("shape = RoundedCornerShape(18.dp)"))
    }
}
