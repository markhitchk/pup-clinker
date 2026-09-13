package com.harleytg.puppyclicker

import org.json.JSONObject

internal data class PuppyCasinoSaveValidation(
    val valid: Boolean,
    val message: String? = null
)

internal object PuppyCasinoSaveValidator {
    fun validateActiveRound(round: PuppyCasinoRound): PuppyCasinoSaveValidation {
        if (!PuppyCasinoTransactionEngine.isValidRoundId(round.roundId)) {
            return invalid("Casino round ID is invalid")
        }
        if (round.wagerTreats <= 0L) {
            return invalid("Casino wager is invalid")
        }
        if (round.payoutTreats < 0L) {
            return invalid("Casino payout is invalid")
        }
        if (round.state == PuppyCasinoRoundState.WAGER_ACCEPTED && round.outcomePayload != null) {
            return invalid("Accepted Casino round already contains an outcome")
        }
        if (
            round.state == PuppyCasinoRoundState.OUTCOME_COMMITTED &&
            round.outcomePayload.isNullOrBlank()
        ) {
            return invalid("Committed Casino round has no outcome")
        }

        return when (round.game) {
            PuppyCasinoGame.SLOTS -> validateSlots(round)
            PuppyCasinoGame.ROULETTE -> validateRoulette(round)
            PuppyCasinoGame.BLACKJACK -> validateBlackjack(round)
        }
    }

    /**
     * Validates a typed SharedPreferences store before GameSaveTransfer replaces
     * the device's current save. Missing Casino keys are valid pre-Casino saves.
     */
    fun validateTransferMainStore(store: JSONObject): PuppyCasinoSaveValidation {
        val values = runCatching { store.getJSONObject("values") }.getOrNull()
            ?: return invalid("Save main store is missing preference values")
        val types = runCatching { store.getJSONObject("types") }.getOrNull()
            ?: return invalid("Save main store is missing preference types")

        if (!values.has(PuppyCasinoPersistence.ACTIVE_ROUND_KEY)) {
            return PuppyCasinoSaveValidation(valid = true)
        }

        if (
            !types.has(PuppyCasinoPersistence.ACTIVE_ROUND_KEY) ||
            types.optString(PuppyCasinoPersistence.ACTIVE_ROUND_KEY) != "string"
        ) {
            return invalid("Casino active round has the wrong save type")
        }

        val raw = values.optString(PuppyCasinoPersistence.ACTIVE_ROUND_KEY, "")
        val round = PuppyCasinoPersistence.decodeRoundForValidation(raw)
            ?: return invalid("Casino active round is malformed")

        return validateActiveRound(round)
    }

    private fun validateSlots(round: PuppyCasinoRound): PuppyCasinoSaveValidation {
        if (!PuppySlotsEngine.isValidWager(round.wagerTreats)) {
            return invalid("Slots wager is outside the supported range")
        }
        if (round.wagerPayload != null) {
            return invalid("Slots round contains unexpected wager data")
        }
        if (round.state == PuppyCasinoRoundState.WAGER_ACCEPTED) {
            if (round.payoutTreats != 0L || round.outcomeCommittedAtMs != 0L) {
                return invalid("Accepted Slots round contains committed payout data")
            }
            return PuppyCasinoSaveValidation(valid = true)
        }

        val outcome = PuppySlotsOutcomeCodec.decodeAndValidate(
            raw = round.outcomePayload,
            wagerTreats = round.wagerTreats
        ) ?: return invalid("Slots outcome failed validation")

        if (outcome.payoutTreats != round.payoutTreats) {
            return invalid("Slots payout does not match the committed outcome")
        }
        return PuppyCasinoSaveValidation(valid = true)
    }

    private fun validateRoulette(round: PuppyCasinoRound): PuppyCasinoSaveValidation {
        if (!PuppyRouletteEngine.isValidWager(round.wagerTreats)) {
            return invalid("Roulette wager is outside the supported range")
        }
        val bet = PuppyRouletteBetCodec.decodeAndValidate(round.wagerPayload)
            ?: return invalid("Roulette bet failed validation")

        if (round.state == PuppyCasinoRoundState.WAGER_ACCEPTED) {
            if (round.payoutTreats != 0L || round.outcomeCommittedAtMs != 0L) {
                return invalid("Accepted Roulette round contains committed payout data")
            }
            return PuppyCasinoSaveValidation(valid = true)
        }

        val outcome = PuppyRouletteOutcomeCodec.decodeAndValidate(
            raw = round.outcomePayload,
            wagerTreats = round.wagerTreats,
            expectedBet = bet
        ) ?: return invalid("Roulette outcome failed validation")

        if (outcome.payoutTreats != round.payoutTreats) {
            return invalid("Roulette payout does not match the committed outcome")
        }
        return PuppyCasinoSaveValidation(valid = true)
    }

    private fun validateBlackjack(round: PuppyCasinoRound): PuppyCasinoSaveValidation {
        val state = PuppyBlackjackStateCodec.decodeAndValidate(round.wagerPayload)
            ?: return invalid("Blackjack hand state failed validation")

        val totalWager = runCatching {
            PuppyBlackjackEngine.totalWager(state)
        }.getOrNull() ?: return invalid("Blackjack wager total overflowed")

        if (totalWager != round.wagerTreats) {
            return invalid("Blackjack hand wagers do not match the Casino ledger")
        }

        if (round.state == PuppyCasinoRoundState.WAGER_ACCEPTED) {
            if (round.payoutTreats != 0L || round.outcomeCommittedAtMs != 0L) {
                return invalid("Accepted Blackjack round contains committed payout data")
            }
            return PuppyCasinoSaveValidation(valid = true)
        }

        if (!state.complete) {
            return invalid("Committed Blackjack round has an unfinished hand")
        }

        val outcome = PuppyBlackjackOutcomeCodec.decodeAndValidate(
            raw = round.outcomePayload,
            state = state
        ) ?: return invalid("Blackjack outcome failed validation")

        if (outcome.totalPayoutTreats != round.payoutTreats) {
            return invalid("Blackjack payout does not match the committed outcome")
        }
        return PuppyCasinoSaveValidation(valid = true)
    }

    private fun invalid(message: String) =
        PuppyCasinoSaveValidation(valid = false, message = message)
}
