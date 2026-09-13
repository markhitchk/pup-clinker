package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoSaveTransferContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun portableV3BackupExportsTheWholeMainPreferenceStore() {
        val transfer = source("GameSaveTransfer.kt")

        assertTrue(transfer.contains("private const val PAYLOAD_VERSION = 3"))
        assertTrue(
            transfer.contains(
                "SecurePreferenceCodec.encode(context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE))"
            )
        )
    }

    @Test
    fun validActiveRoundMustFinishBeforeImportingAnotherSave() {
        val transfer = source("GameSaveTransfer.kt")

        assertTrue(
            transfer.contains(
                "require(currentCasino.round == null)"
            )
        )
        assertTrue(
            transfer.contains(
                "Finish the current Casino round before importing another save."
            )
        )
        assertTrue(
            transfer.contains(
                "importing a known-good backup is the supported non-destructive repair path"
            )
        )
    }

    @Test
    fun importRejectsActiveRoundIdsAlreadyConsumedOnThisDevice() {
        val transfer = source("GameSaveTransfer.kt")
        val validator = source("PuppyCasinoSaveValidator.kt")

        assertTrue(
            transfer.contains(
                "addAll(PuppyCasinoPersistence.loadCompletedRoundIds(currentMainPrefs))"
            )
        )
        assertTrue(
            transfer.contains(
                "addAll(PuppyCasinoRewardPersistence.load(currentMainPrefs).evaluatedRoundIds)"
            )
        )
        assertTrue(
            transfer.contains(
                "addAll(PuppyCasinoPuppyRewardPersistence.load(currentMainPrefs).evaluatedRoundIds)"
            )
        )
        assertTrue(
            validator.contains(
                "if (round.roundId in disallowedActiveRoundIds)"
            )
        )
    }

    @Test
    fun importedMainStoreIsValidatedBeforeItReplacesLocalPrefs() {
        val transfer = source("GameSaveTransfer.kt")

        val validate = transfer.indexOf(
            "PuppyCasinoSaveValidator.validateTransferMainStore(mainStore)"
        )
        val restore = transfer.indexOf(
            "SecurePreferenceCodec.restore(\n            context.getSharedPreferences(MAIN_PREFS"
        )

        assertTrue(validate >= 0)
        assertTrue(restore > validate)
        assertTrue(
            transfer.contains(
                "Casino save validation failed:"
            )
        )
    }

    @Test
    fun legacyV1ImportsCannotInjectModernCasinoKeys() {
        val transfer = source("GameSaveTransfer.kt")

        assertTrue(transfer.contains("if (key.startsWith(\"casino_\"))"))
        assertTrue(transfer.contains("Legacy v1 JSON saves predate the Casino ledger"))
    }

    @Test
    fun oldSavesDoNotRequireCasinoKeys() {
        val validator = source("PuppyCasinoSaveValidator.kt")

        assertTrue(
            validator.contains(
                "if (!values.has(PuppyCasinoPersistence.ACTIVE_ROUND_KEY))"
            )
        )
        assertFalse(
            validator.contains(
                "error(\"Save is missing Casino"
            )
        )
    }

    @Test
    fun successfulImportReloadsRoundAndRewardLedgers() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")

        assertTrue(viewModel.contains("val casinoInspection = PuppyCasinoPersistence.inspectActiveRound(prefs)"))
        assertTrue(viewModel.contains("_casinoRound.value = casinoInspection.round"))
        assertTrue(viewModel.contains("_casinoRecoveryIssue.value = casinoInspection.issue"))
        assertTrue(viewModel.contains("_casinoRewardLedger.value = PuppyCasinoRewardPersistence.load(prefs)"))
        assertTrue(viewModel.contains("_casinoPuppyRewardLedger.value = PuppyCasinoPuppyRewardPersistence.load(prefs)"))
    }

    @Test
    fun corruptRuntimeRoundBlocksNewWagersInsteadOfSilentlyStartingFresh() {
        val viewModel = source("PuppyClickerV6ViewModel.kt")
        val hub = source("PuppyCasinoHub.kt")

        assertTrue(viewModel.contains("if (_casinoRecoveryIssue.value != null)"))
        assertTrue(viewModel.contains("PuppyCasinoTransactionFailure.CORRUPT_SAVE"))
        assertTrue(hub.contains("Casino recovery protection"))
        assertTrue(hub.contains("New wagers are blocked to protect your Treat balance"))
    }
}
