package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoPuppyOwnershipContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun casinoUnlockWritesTheExistingUnlockedPuppiesSaveKey() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")
        val rewards = source("PuppyCasinoPuppyRewards.kt")

        assertTrue(
            rewards.contains(
                "unlockedPuppies = before.unlockedPuppies + styleId"
            )
        )
        assertTrue(
            viewModel.contains(
                ".putStringSet(KEY_UNLOCKED_PUPPIES, result.state.unlockedPuppies)"
            )
        )
        assertFalse(rewards.contains("casinoPuppyInventory"))
        assertFalse(rewards.contains("casino_puppy_inventory"))
    }

    @Test
    fun normalResetAndPrestigePreserveExistingPuppyOwnership() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(
            viewModel.contains(
                "unlockedPuppies = keep.unlockedPuppies"
            )
        )

        val prestigeStart = viewModel.indexOf("fun prestige()")
        val prestigeEnd = viewModel.indexOf("fun buyPrestigeSkill", prestigeStart)
        val prestige = viewModel.substring(prestigeStart, prestigeEnd)
        assertTrue(prestige.contains("_state.value = current.copy("))
        assertFalse(prestige.contains("unlockedPuppies = emptySet()"))
        assertFalse(prestige.contains("unlockedPuppies = DEFAULT_V6_PUPPIES"))
    }

    @Test
    fun portableSaveReloadRestoresOwnershipAndPuppyRewardLedger() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(viewModel.contains("_state.value = loadState()"))
        assertTrue(
            viewModel.contains(
                "_casinoPuppyRewardLedger.value = PuppyCasinoPuppyRewardPersistence.load(prefs)"
            )
        )
        assertTrue(
            viewModel.contains(
                "prefs.getStringSet(KEY_UNLOCKED_PUPPIES, DEFAULT_V6_PUPPIES)"
            )
        )
    }

    @Test
    fun fullLocalDataEraseClearsCasinoPuppyRewardRuntimeState() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(
            viewModel.contains(
                "_casinoPuppyRewardLedger.value = PuppyCasinoPuppyRewardLedger()"
            )
        )
        assertTrue(viewModel.contains("_lastCasinoPuppyReward.value = null"))
        assertTrue(viewModel.contains("_state.value = V6GameState()"))
    }

    @Test
    fun reservedSpecialPuppiesAreNotInCasinoPool() {
        val rewards = source("PuppyCasinoPuppyRewards.kt")

        assertTrue(rewards.contains("default/free puppies"))
        assertTrue(rewards.contains("seasonal/event puppies"))
        assertTrue(rewards.contains("developer/secret/tribute puppies"))
        assertTrue(rewards.contains("branded/special V2 puppies"))
    }
}
