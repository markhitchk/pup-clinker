package com.harleytg.puppyclicker

import java.time.LocalDate

internal enum class PuppyCasinoRecoveryAction {
    REFUND_ACCEPTED_WAGER,
    RESUME_BLACKJACK,
    SETTLE_COMMITTED_OUTCOME
}

internal object PuppyCasinoFeaturePolicy {
    const val CASINO_FLAG_KEY = "puppy_casino"

    fun gameFlagKey(game: PuppyCasinoGame): String = when (game) {
        PuppyCasinoGame.SLOTS -> "casino_slots"
        PuppyCasinoGame.ROULETTE -> "casino_roulette"
        PuppyCasinoGame.BLACKJACK -> "casino_blackjack"
    }

    fun canStartNewRound(
        game: PuppyCasinoGame,
        flags: Map<String, PuppyFeatureFlag>,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val casino = flags[CASINO_FLAG_KEY] ?: return false
        val gameFlag = flags[gameFlagKey(game)] ?: return false
        return casino.isAvailable(today) && gameFlag.isAvailable(today)
    }

    /**
     * Remote visibility may hide the Casino for users with no active wager.
     * Recovery always wins over visibility so a remote kill switch can never
     * make an accepted/committed wager unreachable.
     */
    fun shouldExposeCasinoEntry(
        flags: Map<String, PuppyFeatureFlag>,
        activeRound: PuppyCasinoRound?
    ): Boolean =
        activeRound != null || flags[CASINO_FLAG_KEY]?.visible == true

    /**
     * Existing rounds deliberately do not consult feature flags.
     * A kill switch blocks the next wager, not recovery of money already committed.
     */
    fun recoveryAction(round: PuppyCasinoRound): PuppyCasinoRecoveryAction =
        when (round.state) {
            PuppyCasinoRoundState.OUTCOME_COMMITTED ->
                PuppyCasinoRecoveryAction.SETTLE_COMMITTED_OUTCOME

            PuppyCasinoRoundState.WAGER_ACCEPTED ->
                if (round.game == PuppyCasinoGame.BLACKJACK) {
                    PuppyCasinoRecoveryAction.RESUME_BLACKJACK
                } else {
                    PuppyCasinoRecoveryAction.REFUND_ACCEPTED_WAGER
                }
        }
}
