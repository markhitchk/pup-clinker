package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyEconomyV7SourceContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun v6HasNoPassiveTreatProductionLoop() {
        val vm = source("PuppyClickerV6ViewModel.kt")
        assertFalse(vm.contains("if (current.autoPerSecond > 0)"))
        assertFalse(vm.contains("addTreats((current.autoPerSecond"))
        assertTrue(vm.contains("PuppyEconomyV7.ACTIVE_BONUS_TAP_INTERVAL"))
        assertTrue(vm.contains("activeBonus = nextActiveBonus"))
    }

    @Test
    fun casinoTransactionsAreIsolatedToCasinoChips() {
        val transactions = source("PuppyCasinoTransactions.kt")
        assertTrue(transactions.contains("before.casinoChips < wagerTreats"))
        assertTrue(transactions.contains("before.copy(casinoChips = before.casinoChips - wagerTreats)"))
        assertTrue(transactions.contains("before.copy(casinoChips = nextChips)"))
        assertFalse(transactions.contains("before.treats < wagerTreats"))
        assertFalse(transactions.contains("before.copy(treats = before.treats - wagerTreats)"))
        assertFalse(transactions.contains("checkedAdd(before.lifetimeTreats"))
    }

    @Test
    fun activeUiNoLongerAdvertisesPassiveIncomeOrTreatCasinoWallet() {
        val main = source("PuppyMainScreensRevamp.kt")
        val casino = source("PuppyCasinoHub.kt")
        assertFalse(main.contains("Per sec"))
        assertTrue(main.contains("Bones"))
        assertTrue(main.contains("Pup Coins"))
        assertTrue(main.contains("Casino Chips"))
        assertTrue(casino.contains("Casino Chip Wallet"))
        assertFalse(casino.contains("Treat Wallet"))
        assertFalse(casino.contains("No chips or second wallet"))
    }

    @Test
    fun afkPreparationCreatesSystemRewardInsteadOfWalletDeposit() {
        val app = source("PuppyClickerApplication.kt")
        val notifications = source("PuppyNotificationHistory.kt")
        assertTrue(app.contains("PuppyNotificationHistory.recordSystemReward("))
        assertTrue(notifications.contains("SYSTEM_REWARD"))
        assertTrue(notifications.contains("hasClaimableReward"))
        assertTrue(notifications.contains("markRewardClaimed"))
    }
}
