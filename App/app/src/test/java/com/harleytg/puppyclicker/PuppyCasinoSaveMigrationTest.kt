package com.harleytg.puppyclicker

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoSaveMigrationTest {
    private fun typedStore(strings: Map<String, String> = emptyMap()): JSONObject {
        val values = JSONObject()
        val types = JSONObject()
        strings.forEach { (key, value) ->
            values.put(key, value)
            types.put(key, "string")
        }
        return JSONObject()
            .put("values", values)
            .put("types", types)
    }

    private fun encodedRound(round: PuppyCasinoRound): String =
        JSONObject().apply {
            put("schemaVersion", 1)
            put("roundId", round.roundId)
            put("game", round.game.name)
            put("wagerTreats", round.wagerTreats)
            put("state", round.state.name)
            put("acceptedAtMs", round.acceptedAtMs)
            round.wagerPayload?.let { put("wagerPayload", it) }
            round.outcomePayload?.let { put("outcomePayload", it) }
            put("payoutTreats", round.payoutTreats)
            put("outcomeCommittedAtMs", round.outcomeCommittedAtMs)
        }.toString()

    private fun emptyTicketLedger(): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("dailyEpochDay", LocalDate.now().toEpochDay())
            .put("dailyTicketAwards", 0)
            .put("evaluatedRoundIds", JSONArray())
            .toString()

    private fun emptyPuppyLedger(): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("dailyEpochDay", LocalDate.now().toEpochDay())
            .put("dailyPuppyUnlocks", 0)
            .put("evaluatedRoundIds", JSONArray())
            .toString()

    @Test
    fun preCasinoStoreWithNoCasinoKeysMigratesCleanly() {
        val validation = PuppyCasinoSaveValidator.validateTransferMainStore(
            typedStore()
        )

        assertTrue(validation.valid)
    }

    @Test
    fun validInterruptedSlotsWagerSurvivesImportValidation() {
        val round = PuppyCasinoRound(
            roundId = "round_slots_migrate1",
            game = PuppyCasinoGame.SLOTS,
            wagerTreats = 100L,
            state = PuppyCasinoRoundState.WAGER_ACCEPTED,
            acceptedAtMs = 1_000L
        )
        val store = typedStore(
            mapOf(
                PuppyCasinoPersistence.ACTIVE_ROUND_KEY to encodedRound(round),
                PuppyCasinoPersistence.COMPLETED_ROUND_IDS_KEY to "[]",
                PuppyCasinoRewardPersistence.LEDGER_KEY to emptyTicketLedger(),
                PuppyCasinoPuppyRewardPersistence.LEDGER_KEY to emptyPuppyLedger()
            )
        )

        assertTrue(
            PuppyCasinoSaveValidator.validateTransferMainStore(store).valid
        )
    }

    @Test
    fun activeRoundAlreadyInCompletedHistoryIsRejected() {
        val round = PuppyCasinoRound(
            roundId = "round_duplicate_migrate1",
            game = PuppyCasinoGame.SLOTS,
            wagerTreats = 100L,
            state = PuppyCasinoRoundState.WAGER_ACCEPTED,
            acceptedAtMs = 1_000L
        )
        val completed = JSONArray().put(round.roundId).toString()
        val store = typedStore(
            mapOf(
                PuppyCasinoPersistence.ACTIVE_ROUND_KEY to encodedRound(round),
                PuppyCasinoPersistence.COMPLETED_ROUND_IDS_KEY to completed
            )
        )

        assertFalse(
            PuppyCasinoSaveValidator.validateTransferMainStore(store).valid
        )
    }

    @Test
    fun malformedActiveRoundIsRejectedInsteadOfBecomingNoRound() {
        val store = typedStore(
            mapOf(
                PuppyCasinoPersistence.ACTIVE_ROUND_KEY to "{not-json"
            )
        )

        assertFalse(
            PuppyCasinoSaveValidator.validateTransferMainStore(store).valid
        )
    }

    @Test
    fun malformedCompletedHistoryIsRejected() {
        val store = typedStore(
            mapOf(
                PuppyCasinoPersistence.COMPLETED_ROUND_IDS_KEY to ""
            )
        )

        assertFalse(
            PuppyCasinoSaveValidator.validateTransferMainStore(store).valid
        )
    }

    @Test
    fun malformedTicketRewardLedgerIsRejected() {
        val badLedger = JSONObject()
            .put("schemaVersion", 1)
            .put("dailyEpochDay", LocalDate.now().toEpochDay())
            .put("dailyTicketAwards", PuppyCasinoRewardEngine.MAX_DAILY_CASINO_TICKETS + 1)
            .put("evaluatedRoundIds", JSONArray())
            .toString()

        assertFalse(
            PuppyCasinoSaveValidator.validateTransferMainStore(
                typedStore(
                    mapOf(PuppyCasinoRewardPersistence.LEDGER_KEY to badLedger)
                )
            ).valid
        )
    }

    @Test
    fun malformedPuppyRewardLedgerIsRejected() {
        val duplicateId = "round_reward_dup1"
        val badLedger = JSONObject()
            .put("schemaVersion", 1)
            .put("dailyEpochDay", LocalDate.now().toEpochDay())
            .put("dailyPuppyUnlocks", 0)
            .put("evaluatedRoundIds", JSONArray().put(duplicateId).put(duplicateId))
            .toString()

        assertFalse(
            PuppyCasinoSaveValidator.validateTransferMainStore(
                typedStore(
                    mapOf(PuppyCasinoPuppyRewardPersistence.LEDGER_KEY to badLedger)
                )
            ).valid
        )
    }

    @Test
    fun wrongPreferenceTypeForCasinoKeyIsRejected() {
        val values = JSONObject()
            .put(PuppyCasinoPersistence.COMPLETED_ROUND_IDS_KEY, 5)
        val types = JSONObject()
            .put(PuppyCasinoPersistence.COMPLETED_ROUND_IDS_KEY, "int")
        val store = JSONObject()
            .put("values", values)
            .put("types", types)

        assertFalse(
            PuppyCasinoSaveValidator.validateTransferMainStore(store).valid
        )
    }

    @Test
    fun committedSlotsPayoutMustMatchSerializedOutcome() {
        val outcome = PuppySlotsEngine.spinFromRolls(
            wagerTreats = 100L,
            rolls = intArrayOf(0, 0, 0)
        )
        val round = PuppyCasinoRound(
            roundId = "round_slots_commit1",
            game = PuppyCasinoGame.SLOTS,
            wagerTreats = 100L,
            state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
            acceptedAtMs = 1_000L,
            outcomePayload = PuppySlotsOutcomeCodec.encode(outcome),
            payoutTreats = outcome.payoutTreats + 1L,
            outcomeCommittedAtMs = 1_500L
        )

        assertFalse(PuppyCasinoSaveValidator.validateActiveRound(round).valid)
    }

    @Test
    fun validInterruptedBlackjackStatePassesMigrationValidation() {
        val state = PuppyBlackjackEngine.newRoundFromDeck(
            wagerTreats = 100L,
            shuffledDeck = listOf(4, 7, 5, 8) +
                (0..51).filterNot { it in setOf(4, 7, 5, 8) }
        )
        val round = PuppyCasinoRound(
            roundId = "round_blackjack_migrate1",
            game = PuppyCasinoGame.BLACKJACK,
            wagerTreats = PuppyBlackjackEngine.totalWager(state),
            state = PuppyCasinoRoundState.WAGER_ACCEPTED,
            acceptedAtMs = 1_000L,
            wagerPayload = PuppyBlackjackStateCodec.encode(state)
        )

        assertTrue(PuppyCasinoSaveValidator.validateActiveRound(round).valid)
    }
}
