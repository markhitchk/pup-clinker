package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoTransactionEngineTest {
    private val roundId = "round_test_0001"

    @Test
    fun disabledCasinoBlocksOnlyNewWagers() {
        val before = V6GameState(treats = 1_000, casinoChips = 500, lifetimeTreats = 5_000)
        val blocked = PuppyCasinoTransactionEngine.acceptWager(
            before, null, emptyList(), roundId, PuppyCasinoGame.SLOTS,
            100, false, 1000
        )
        assertFalse(blocked.success)
        assertEquals(PuppyCasinoTransactionFailure.FEATURE_DISABLED, blocked.failure)
        assertEquals(before, blocked.state)
    }

    @Test
    fun acceptingWagerDeductsCasinoChipsAndLeavesTreatProgressionUntouched() {
        val before = V6GameState(treats = 1_000, casinoChips = 1_000, lifetimeTreats = 5_000)
        val accepted = PuppyCasinoTransactionEngine.acceptWager(
            before, null, emptyList(), roundId, PuppyCasinoGame.ROULETTE,
            250, true, 1000
        )
        assertTrue(accepted.success)
        assertEquals(750L, accepted.state.casinoChips)
        assertEquals(1_000L, accepted.state.treats)
        assertEquals(5_000L, accepted.state.lifetimeTreats)
        assertEquals(PuppyCasinoRoundState.WAGER_ACCEPTED, accepted.activeRound?.state)
        assertEquals(250L, accepted.activeRound?.wagerChips)
    }

    @Test
    fun onlyOneCasinoRoundCanBeActiveAtATime() {
        val before = V6GameState(casinoChips = 1_000)
        val active = PuppyCasinoRound(
            "round_active_01", PuppyCasinoGame.SLOTS, 100,
            PuppyCasinoRoundState.WAGER_ACCEPTED, 1000
        )
        val result = PuppyCasinoTransactionEngine.acceptWager(
            before, active, emptyList(), roundId, PuppyCasinoGame.BLACKJACK,
            100, true, 2000
        )
        assertFalse(result.success)
        assertEquals(PuppyCasinoTransactionFailure.ROUND_ALREADY_ACTIVE, result.failure)
        assertEquals(before, result.state)
    }

    @Test
    fun blackjackAdditionalWagerUsesCasinoChipsAtomicallyWithPayload() {
        val before = V6GameState(treats = 900, casinoChips = 900, lifetimeTreats = 5_000)
        val active = PuppyCasinoRound(
            roundId = roundId,
            game = PuppyCasinoGame.BLACKJACK,
            wagerTreats = 100,
            state = PuppyCasinoRoundState.WAGER_ACCEPTED,
            acceptedAtMs = 1000,
            wagerPayload = "{\"step\":1}"
        )

        val updated = PuppyCasinoTransactionEngine.updateAcceptedRound(
            before = before,
            activeRound = active,
            completedRoundIds = emptyList(),
            roundId = roundId,
            wagerPayload = "{\"step\":2}",
            additionalWagerTreats = 100
        )

        assertTrue(updated.success)
        assertEquals(800L, updated.state.casinoChips)
        assertEquals(900L, updated.state.treats)
        assertEquals(5_000L, updated.state.lifetimeTreats)
        assertEquals(200L, updated.activeRound?.wagerChips)
        assertEquals("{\"step\":2}", updated.activeRound?.wagerPayload)
    }

    @Test
    fun additionalWagerRejectsInsufficientCasinoChips() {
        val before = V6GameState(treats = 9_999, casinoChips = 50)
        val active = PuppyCasinoRound(
            roundId = roundId,
            game = PuppyCasinoGame.BLACKJACK,
            wagerTreats = 100,
            state = PuppyCasinoRoundState.WAGER_ACCEPTED,
            acceptedAtMs = 1000,
            wagerPayload = "{\"step\":1}"
        )

        val updated = PuppyCasinoTransactionEngine.updateAcceptedRound(
            before = before,
            activeRound = active,
            completedRoundIds = emptyList(),
            roundId = roundId,
            wagerPayload = "{\"step\":2}",
            additionalWagerTreats = 100
        )

        assertFalse(updated.success)
        assertEquals(PuppyCasinoTransactionFailure.INSUFFICIENT_CHIPS, updated.failure)
        assertEquals(before, updated.state)
        assertEquals(active, updated.activeRound)
    }

    @Test
    fun initialWagerRejectsInsufficientCasinoChipsEvenWithManyTreats() {
        val before = V6GameState(treats = 1_000_000, casinoChips = 99)
        val result = PuppyCasinoTransactionEngine.acceptWager(
            before, null, emptyList(), roundId, PuppyCasinoGame.SLOTS,
            100, true, 1000
        )
        assertFalse(result.success)
        assertEquals(PuppyCasinoTransactionFailure.INSUFFICIENT_CHIPS, result.failure)
        assertEquals(before, result.state)
    }

    @Test
    fun outcomeIsCommittedBeforeSettlementAndDoesNotChangeBalance() {
        val afterWager = V6GameState(treats = 900, casinoChips = 900, lifetimeTreats = 5_000)
        val active = PuppyCasinoRound(
            roundId, PuppyCasinoGame.BLACKJACK, 100,
            PuppyCasinoRoundState.WAGER_ACCEPTED, 1000
        )
        val committed = PuppyCasinoTransactionEngine.commitOutcome(
            afterWager, active, emptyList(), roundId,
            "{\"result\":\"natural_blackjack\"}", 250, 1500
        )
        assertTrue(committed.success)
        assertEquals(afterWager, committed.state)
        assertEquals(PuppyCasinoRoundState.OUTCOME_COMMITTED, committed.activeRound?.state)
        assertEquals(250L, committed.activeRound?.payoutChips)
    }

    @Test
    fun casinoPayoutCreditsChipsOnlyAndNeverLifetimeTreats() {
        val afterWager = V6GameState(treats = 900, casinoChips = 900, lifetimeTreats = 5_000)
        val committed = PuppyCasinoRound(
            roundId = roundId,
            game = PuppyCasinoGame.BLACKJACK,
            wagerTreats = 100,
            state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
            acceptedAtMs = 1000,
            outcomePayload = "{\"result\":\"natural_blackjack\"}",
            payoutTreats = 250,
            outcomeCommittedAtMs = 1500
        )
        val settled = PuppyCasinoTransactionEngine.settle(
            afterWager, committed, emptyList(), roundId
        )
        assertTrue(settled.success)
        assertEquals(1_150L, settled.state.casinoChips)
        assertEquals(900L, settled.state.treats)
        assertEquals(5_000L, settled.state.lifetimeTreats)
        assertNull(settled.activeRound)
        assertTrue(roundId in settled.completedRoundIds)
    }

    @Test
    fun losingRoundSettlesWithoutChangingTreatsOrLifetimeTreats() {
        val afterWager = V6GameState(treats = 900, casinoChips = 900, lifetimeTreats = 5_000)
        val committed = PuppyCasinoRound(
            roundId = roundId,
            game = PuppyCasinoGame.ROULETTE,
            wagerTreats = 100,
            state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
            acceptedAtMs = 1000,
            outcomePayload = "{\"number\":0}",
            payoutTreats = 0,
            outcomeCommittedAtMs = 1500
        )
        val settled = PuppyCasinoTransactionEngine.settle(
            afterWager, committed, emptyList(), roundId
        )
        assertTrue(settled.success)
        assertEquals(900L, settled.state.casinoChips)
        assertEquals(900L, settled.state.treats)
        assertEquals(5_000L, settled.state.lifetimeTreats)
    }

    @Test
    fun acceptedRoundRefundReturnsCasinoChipsOnly() {
        val afterWager = V6GameState(treats = 900, casinoChips = 900, lifetimeTreats = 5_000)
        val accepted = PuppyCasinoRound(
            roundId, PuppyCasinoGame.SLOTS, 100,
            PuppyCasinoRoundState.WAGER_ACCEPTED, 1000
        )
        val refunded = PuppyCasinoTransactionEngine.refund(
            afterWager, accepted, emptyList(), roundId
        )
        assertTrue(refunded.success)
        assertEquals(1_000L, refunded.state.casinoChips)
        assertEquals(900L, refunded.state.treats)
        assertEquals(5_000L, refunded.state.lifetimeTreats)
        assertNull(refunded.activeRound)
    }

    @Test
    fun committedOutcomeCannotBeRefundedOrRerolled() {
        val committed = PuppyCasinoRound(
            roundId = roundId,
            game = PuppyCasinoGame.SLOTS,
            wagerTreats = 100,
            state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
            acceptedAtMs = 1000,
            outcomePayload = "{\"symbols\":[\"BONE\",\"BONE\",\"BONE\"]}",
            payoutTreats = 200,
            outcomeCommittedAtMs = 1500
        )
        val balance = V6GameState(treats = 900, casinoChips = 900)
        val refunded = PuppyCasinoTransactionEngine.refund(
            balance, committed, emptyList(), roundId
        )
        val secondOutcome = PuppyCasinoTransactionEngine.commitOutcome(
            balance, committed, emptyList(), roundId,
            "{\"symbols\":[\"OTHER\"]}", 999, 2000
        )
        assertFalse(refunded.success)
        assertEquals(PuppyCasinoTransactionFailure.INVALID_ROUND_STATE, refunded.failure)
        assertFalse(secondOutcome.success)
        assertEquals(PuppyCasinoTransactionFailure.INVALID_ROUND_STATE, secondOutcome.failure)
    }

    @Test
    fun completedRoundIdPreventsDuplicateSettlement() {
        val result = PuppyCasinoTransactionEngine.settle(
            V6GameState(casinoChips = 1_000), null, listOf(roundId), roundId
        )
        assertFalse(result.success)
        assertEquals(PuppyCasinoTransactionFailure.DUPLICATE_ROUND, result.failure)
    }

    @Test
    fun completedRoundHistoryIsBounded() {
        val prior = (0 until PuppyCasinoTransactionEngine.MAX_COMPLETED_ROUND_IDS)
            .map { index -> "round_history_" + index.toString().padStart(4, '0') }
        val accepted = PuppyCasinoRound(
            roundId, PuppyCasinoGame.SLOTS, 100,
            PuppyCasinoRoundState.WAGER_ACCEPTED, 1000
        )
        val refunded = PuppyCasinoTransactionEngine.refund(
            V6GameState(casinoChips = 900), accepted, prior, roundId
        )
        assertTrue(refunded.success)
        assertEquals(PuppyCasinoTransactionEngine.MAX_COMPLETED_ROUND_IDS, refunded.completedRoundIds.size)
        assertEquals(roundId, refunded.completedRoundIds.last())
    }
}
