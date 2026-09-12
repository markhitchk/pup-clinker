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
    fun playKeepsCareActionsExclusiveToCareTab() {
        val source = generated("PuppyMainScreensRevamp.kt").readText()
        val play = source.substring(
            source.indexOf("internal fun PuppyRevampedPlayScreen"),
            source.indexOf("internal fun PuppyRevampedCareScreen")
        )
        val care = source.substring(
            source.indexOf("internal fun PuppyRevampedCareScreen"),
            source.indexOf("private fun PuppyCompactCareMeter")
        )

        assertTrue(!play.contains("vm::feedPuppy"))
        assertTrue(!play.contains("vm::playWithPuppy"))
        assertTrue(!play.contains("vm::groomPuppy"))
        assertTrue(!play.contains("vm::restPuppy"))
        assertTrue(care.contains("vm::feedPuppy"))
        assertTrue(care.contains("vm::playWithPuppy"))
        assertTrue(care.contains("vm::groomPuppy"))
        assertTrue(care.contains("vm::restPuppy"))
        assertTrue(care.contains("vm::cuddlePuppy"))
    }

    @Test
    fun rewardsFillStreakDotsAndTicketAlertStaysAtPlayHeader() {
        val source = generated("PuppyMainScreensRevamp.kt").readText()

        assertTrue(source.contains("index < state.dailyStreak.coerceIn(0, 7)"))
        assertTrue(source.contains("MaterialTheme.colorScheme.onPrimary"))
        assertTrue(source.contains(".padding(top = 2.dp, start = 24.dp, end = 24.dp)"))
        assertTrue(!source.contains(".padding(top = 105.dp, start = 24.dp, end = 24.dp)"))
    }

    @Test
    fun harleysStudiosBrandingUsesRepositoryLogoAsset() {
        val settings = generated("PuppySettingsUi.kt").readText()
        val onboarding = generated("PuppyOnboardingUi.kt").readText()
        val branding = generated("HarleysStudiosBranding.kt").readText()

        assertTrue(settings.contains("HarleysStudiosBranding("))
        assertTrue(onboarding.contains("HarleysStudiosBranding("))
        assertTrue(!settings.contains("R.drawable.harleys_studios_icon"))
        assertTrue(!onboarding.contains("R.drawable.harleys_studios_icon"))
        assertTrue(branding.contains("streamedRepoLogoPainter("))
        assertTrue(branding.contains("RepoLogoAsset.HARLEYS_STUDIOS"))
        assertTrue(branding.contains("R.drawable.harleys_studios_icon"))
        assertTrue(!branding.contains("HARLEYS_STUDIOS_LOGO_BASE64"))
    }

    @Test
    fun appLogosStreamFromCentralRepositoryFolder() {
        val stream = generated("StreamedRepoLogos.kt").readText()
        val pupEye = generated("StreamedPupEyeBranding.kt").readText()
        val launch = generated("PuppyLaunchUi.kt").readText()

        assertTrue(stream.contains("assets/logos"))
        assertTrue(stream.contains("puppy_clicker.png"))
        assertTrue(stream.contains("harleys_studios.png"))
        assertTrue(pupEye.contains("assets/logos/PupEye.png"))
        assertTrue(launch.contains("streamedRepoLogoPainter(RepoLogoAsset.PUPPY_CLICKER"))
    }

    @Test
    fun rewardsUseStreamedMonthlyScheduleAndClaimedSection() {
        val rewards = generated("PuppyMainScreensRevamp.kt").readText()
        val stream = generated("PuppyMonthlyRewards.kt").readText()
        val viewModel = generated("PuppyClickerV6ViewModel.kt").readText()
        val app = generated("PuppyClickerApplication.kt").readText()

        assertTrue(rewards.contains("PuppyMonthlyRewards.schedule.collectAsState()"))
        assertTrue(rewards.contains("PuppyMonthlyRewards.currentGoals(todayDate)"))
        assertTrue(rewards.contains("Text(\"Claimed\""))
        assertTrue(rewards.contains("PuppyClaimedReward("))
        assertTrue(stream.contains("assets/rewards"))
        assertTrue(stream.contains("MAX_GOALS_PER_DAY = 12"))
        assertTrue(viewModel.contains("PuppyMonthlyRewards.goalToday(id)"))
        assertTrue(app.contains("PuppyMonthlyRewards.initialize(this)"))
    }

    @Test
    fun rosterUpdatesTriggerBackgroundNotifications() {
        val roster = generated("DynamicPuppyRoster.kt").readText()
        val notifications = generated("PuppyNotificationCenter.kt").readText()

        assertTrue(roster.contains("REFRESH_INTERVAL_MS = 60L * 60L * 1_000L"))
        assertTrue(roster.contains("notifyIfRosterChanged("))
        assertTrue(roster.contains("PuppyNotificationCenter.notifyRosterUpdated("))
        assertTrue(notifications.contains("DynamicPuppyRoster.refreshIfDue(app)"))
        assertTrue(notifications.contains("NOTIFY_ROSTER = 42104"))
        assertTrue(notifications.contains("New puppy added to the roster"))
    }

    @Test
    fun rosterKeepsExchangeWhileUsingRevampedVisualHierarchy() {
        val source = generated("PuppyRosterScreen.kt").readText()

        assertTrue(source.contains("style = MaterialTheme.typography.headlineMedium"))
        assertTrue(source.contains("Open Puppy Exchange"))
        assertTrue(source.contains("shape = RoundedCornerShape(18.dp)"))
    }
}
