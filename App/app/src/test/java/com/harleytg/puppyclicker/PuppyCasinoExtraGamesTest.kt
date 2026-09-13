package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoExtraGamesTest {

    @Test
    fun plinkoPathRoundTripsAndValidatorAcceptsCommittedRound() {
        val outcome = PuppyPlinkoEngine.outcomeForPath(
            wagerTreats = 100L,
            pathRight = List(PuppyPlinkoEngine.ROWS) { false }
        )
        assertEquals(0, outcome.binIndex)
        assertEquals(5, outcome.multiplierHundredths)
        assertEquals(5L, outcome.payoutTreats)

        val payload = PuppyPlinkoOutcomeCodec.encode(outcome)
        assertEquals(outcome, PuppyPlinkoOutcomeCodec.decodeAndValidate(payload, 100L))

        val round = PuppyCasinoRound(
            roundId = "round_plinko_test01",
            game = PuppyCasinoGame.PLINKO,
            wagerTreats = 100L,
            state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
            acceptedAtMs = 1_000L,
            outcomePayload = payload,
            payoutTreats = outcome.payoutTreats,
            outcomeCommittedAtMs = 1_100L
        )
        assertTrue(PuppyCasinoSaveValidator.validateActiveRound(round).valid)
    }

    @Test
    fun scratcherJackpotRoundTripsAndRequiresCommittedPrizeMath() {
        val outcome = PuppyScratchersEngine.outcomeFor(
            wagerTreats = 100L,
            prize = PuppyScratcherPrize.JACKPOT,
            symbols = List(3) { PuppyScratcherPrize.JACKPOT.emoji }
        )
        assertEquals(2_000L, outcome.payoutTreats)

        val payload = PuppyScratcherOutcomeCodec.encode(outcome)
        assertEquals(outcome, PuppyScratcherOutcomeCodec.decodeAndValidate(payload, 100L))

        val round = PuppyCasinoRound(
            roundId = "round_scratch_test01",
            game = PuppyCasinoGame.SCRATCHERS,
            wagerTreats = 100L,
            state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
            acceptedAtMs = 2_000L,
            outcomePayload = payload,
            payoutTreats = outcome.payoutTreats,
            outcomeCommittedAtMs = 2_100L
        )
        assertTrue(PuppyCasinoSaveValidator.validateActiveRound(round).valid)
    }

    @Test
    fun luckyWheelPuppyOutcomeUsesExistingCasinoEligibleStyleId() {
        val styleId = PuppyCasinoPuppyRewardEngine.eligibleStyleIds.first()
        val outcome = PuppyLuckyWheelEngine.outcomeFor(
            wagerTreats = 100L,
            prize = LuckyPupWheelPrize.PUPPY_UNLOCK,
            puppyStyleId = styleId
        )

        assertEquals(0L, outcome.payoutTreats)
        assertEquals(styleId, outcome.puppyStyleId)
        val payload = LuckyPupWheelOutcomeCodec.encode(outcome)
        assertEquals(outcome, LuckyPupWheelOutcomeCodec.decodeAndValidate(payload, 100L))

        val round = PuppyCasinoRound(
            roundId = "round_wheel_test001",
            game = PuppyCasinoGame.LUCKY_WHEEL,
            wagerTreats = 100L,
            state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
            acceptedAtMs = 3_000L,
            outcomePayload = payload,
            payoutTreats = 0L,
            outcomeCommittedAtMs = 3_100L
        )
        assertTrue(PuppyCasinoSaveValidator.validateActiveRound(round).valid)
    }

    @Test
    fun luckyWheelGuaranteedPupUnlockUsesExistingLedgerAndDailyCap() {
        val styleId = PuppyCasinoPuppyRewardEngine.eligibleStyleIds.first()
        val round = PuppyCasinoRound(
            roundId = "round_pup_unlock01",
            game = PuppyCasinoGame.LUCKY_WHEEL,
            wagerTreats = 100L,
            state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
            acceptedAtMs = 4_000L,
            outcomePayload = "{}",
            payoutTreats = 0L,
            outcomeCommittedAtMs = 4_100L
        )
        val before = V6GameState(
            unlockedPuppies = DEFAULT_V6_PUPPIES - styleId
        )

        val applied = PuppyCasinoPuppyRewardEngine.applyGuaranteedUnlock(
            before = before,
            settledRound = round,
            styleId = styleId,
            ledger = PuppyCasinoPuppyRewardLedger(dailyEpochDay = 42L),
            todayEpochDay = 42L
        )

        assertEquals(PuppyCasinoPuppyRewardStatus.UNLOCKED, applied.reward.status)
        assertTrue(styleId in applied.state.unlockedPuppies)
        assertEquals(1, applied.ledger.dailyPuppyUnlocks)
        assertTrue(round.roundId in applied.ledger.evaluatedRoundIds)

        val duplicate = PuppyCasinoPuppyRewardEngine.applyGuaranteedUnlock(
            before = applied.state,
            settledRound = round,
            styleId = styleId,
            ledger = applied.ledger,
            todayEpochDay = 42L
        )
        assertEquals(PuppyCasinoPuppyRewardStatus.ALREADY_EVALUATED, duplicate.reward.status)
    }

    @Test
    fun luckyWheelReplacesUnavailablePupSliceWithTreatRefund() {
        val outcome = PuppyLuckyWheelEngine.outcomeFor(
            wagerTreats = 100L,
            prize = LuckyPupWheelPrize.REFUND,
            puppyStyleId = null
        )
        assertNotNull(outcome)
        assertEquals(100L, outcome.payoutTreats)
    }
}
