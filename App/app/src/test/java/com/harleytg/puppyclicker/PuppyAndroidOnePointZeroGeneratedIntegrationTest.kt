package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyAndroidOnePointZeroGeneratedIntegrationTest {
    private fun generated(name: String): String {
        val relative = "generated/protected-puppies/source/com/harleytg/puppyclicker/$name"
        val file = listOf(File("app/build/$relative"), File("build/$relative"))
            .firstOrNull(File::isFile)
            ?: error("Generated $name was not found")
        return file.readText()
    }

    @Test
    fun v6StateUsesPerPuppyBondAndExplicitXp() {
        val vm = generated("PuppyClickerV6ViewModel.kt")
        assertTrue(vm.contains("val bondByPuppyId: Map<String, Int>"))
        assertTrue(vm.contains("val playerXp: Long"))
        assertTrue(vm.contains("PuppyProgression.bondFor("))
        assertTrue(vm.contains("PuppyProgression.levelForXp(playerXp)"))
        assertTrue(vm.contains("PuppyProgressionStore.KEY_BOND_BY_PUPPY"))
        assertTrue(vm.contains("PuppyProgressionStore.KEY_PLAYER_XP"))
    }

    @Test
    fun v6ProgressionAwardsAreWiredToRealActions() {
        val vm = generated("PuppyClickerV6ViewModel.kt")
        assertTrue(vm.contains("PuppyXpEvent.MANUAL_TAP"))
        assertTrue(vm.contains("PuppyXpEvent.CARE_ACTION"))
        assertTrue(vm.contains("PuppyXpEvent.DAILY_TASK"))
        assertTrue(vm.contains("PuppyXpEvent.NEW_PUPPY"))
        assertTrue(vm.contains("PuppyXpEvent.ACHIEVEMENT"))
        assertTrue(vm.contains("awardNewPuppyXp"))
    }

    @Test
    fun legacyOwnershipIsMarkedSettledWithoutRetroactiveXp() {
        val vm = generated("PuppyClickerV6ViewModel.kt")
        assertTrue(vm.contains("unlocked.mapTo(linkedSetOf()) { \"puppy:${'$'}it\" }"))
    }

    @Test
    fun nonPrestigeResetPreservesOnePointZeroProgression() {
        val vm = generated("PuppyClickerV6ViewModel.kt")
        val start = vm.indexOf("fun resetRunWithoutPrestige()")
        val end = vm.indexOf("private fun consumeClaimedAfkReward()", start)
        assertTrue(start >= 0 && end > start)
        val reset = vm.substring(start, end)
        assertTrue(reset.contains("bondByPuppyId = keep.bondByPuppyId"))
        assertTrue(reset.contains("playerXp = keep.playerXp"))
        assertTrue(reset.contains("achievementRewardedIds = keep.achievementRewardedIds"))
        assertTrue(reset.contains("xpSettlementIds = keep.xpSettlementIds"))
        assertTrue(reset.contains("releaseClaimIds = keep.releaseClaimIds"))
        assertTrue(reset.contains("profileBadgeIds = keep.profileBadgeIds"))
    }

    @Test
    fun currentUiIncludesAchievementsViewerInboxAndReleaseHub() {
        val main = generated("PuppyMainScreensRevamp.kt")
        val roster = generated("PuppyRosterScreen.kt")
        val activity = generated("PuppyClickerV6Activity.kt")
        val settings = generated("PuppySettingsUi.kt")
        assertTrue(main.contains("PuppyAchievementsV6.statuses"))
        assertTrue(roster.contains("PuppyViewerDialog("))
        assertTrue(activity.contains("PuppyNotificationInboxDialog("))
        assertTrue(activity.contains("PuppyNotificationHistory.unreadCount"))
        assertTrue(settings.contains("PuppyReleaseHubScreen("))
        assertTrue(settings.contains("PuppyPerformancePreset"))
    }

    @Test
    fun existingInternalRoutesRemainPresent() {
        val activity = generated("PuppyClickerV6Activity.kt")
        assertTrue(activity.contains("PuppyInternalDestination.GACHA -> PuppyGachaScreen("))
        assertTrue(activity.contains("PuppyInternalDestination.CASINO -> PuppyCasinoHub("))
        assertTrue(activity.contains("PuppyInternalDestination.EXCHANGE -> PuppyExchangeScreen("))
    }

    @Test
    fun offlineModeIsNotIntroduced() {
        val activity = generated("PuppyClickerV6Activity.kt")
        val settings = generated("PuppySettingsUi.kt")
        assertFalse(activity.contains("Offline Mode"))
        assertFalse(settings.contains("Offline Mode"))
    }
    @Test
    fun generatedSourcesRetainEconomyV7AfterAllPatchLayers() {
        val vm = generated("PuppyClickerV6ViewModel.kt")
        val transactions = generated("PuppyCasinoTransactions.kt")
        val hub = generated("PuppyCasinoHub.kt")
        val main = generated("PuppyMainScreensRevamp.kt")
        val inbox = generated("PuppyNotificationInboxUi.kt")

        assertTrue(vm.contains("val bones: Long = 0"))
        assertTrue(vm.contains("val pupCoins: Long = 0"))
        assertTrue(vm.contains("val casinoChips: Long = 0"))
        assertTrue(vm.contains("val activeBonus: Int = 0"))
        assertTrue(vm.contains("PuppyEconomyV7.TICKET_DROP_DENOMINATOR"))
        assertFalse(vm.contains("current.autoPerSecond > 0"))

        assertTrue(transactions.contains("before.copy(casinoChips = before.casinoChips - wagerTreats)"))
        assertTrue(transactions.contains("state = before.copy(casinoChips = nextChips)"))
        assertFalse(transactions.contains("lifetimeTreats = nextLifetime"))

        assertTrue(hub.contains("Casino Chip Wallet"))
        assertTrue(hub.contains("10 Treats = 1 Casino Chip"))
        assertFalse(hub.contains("Treat Wallet"))

        assertTrue(main.contains("🪙 Pup Coin Shop"))
        assertTrue(main.contains("state.bones"))
        assertTrue(main.contains("state.pupCoins"))
        assertTrue(main.contains("state.casinoChips"))
        assertFalse(main.contains(""Per sec""))

        assertTrue(inbox.contains("item.hasClaimableReward"))
    }

}
