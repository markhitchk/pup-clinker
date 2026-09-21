package com.harleytg.puppyclicker

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoRewardEngineTest {
    private fun round(
        id: String,
        wager: Long = 100L,
        payout: Long
    ): PuppyCasinoRound = PuppyCasinoRound(
        roundId = id,
        game = PuppyCasinoGame.SLOTS,
        wagerTreats = wager,
        state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
        acceptedAtMs = 1000L,
        outcomePayload = "{\"ok\":true}",
        payoutTreats = payout,
        outcomeCommittedAtMs = 1500L
    )

    @Test
    fun chanceBandsMatchPublishedPolicy() {
        assertEquals(0, PuppyCasinoRewardEngine.dropChanceBasisPoints(round("round_push_0001", payout = 100L)))
        assertEquals(25, PuppyCasinoRewardEngine.dropChanceBasisPoints(round("round_profit_001", payout = 150L)))
        assertEquals(50, PuppyCasinoRewardEngine.dropChanceBasisPoints(round("round_double_001", payout = 200L)))
        assertEquals(100, PuppyCasinoRewardEngine.dropChanceBasisPoints(round("round_fivex_0001", payout = 500L)))
        assertEquals(200, PuppyCasinoRewardEngine.dropChanceBasisPoints(round("round_twenty_001", payout = 2_000L)))
        assertEquals(500, PuppyCasinoRewardEngine.dropChanceBasisPoints(round("round_hundred_01", payout = 10_000L)))
    }

    @Test
    fun rarityDistributionReusesExistingTicketWeights() {
        assertEquals(
            listOf(60, 25, 10, 4, 1),
            TicketRarity.entries.map { it.rarityWeight }
        )
        assertEquals(100, TicketRarity.entries.sumOf { it.rarityWeight })
    }

    @Test
    fun eligibleHundredXRoundCanAwardAtMostOneExistingTicket() {
        val before = V6GameState()
        val today = LocalDate.now().toEpochDay()
        val application = PuppyCasinoRewardEngine.apply(
            before = before,
            settledRound = round("round_award_0044", payout = 10_000L),
            ledger = PuppyCasinoRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoRewardStatus.AWARDED, application.reward.status)
        val rarity = application.reward.rarity!!
        assertEquals(1, application.state.ticketInventory[rarity])
        assertEquals(1, application.state.ticketsOwned)
        assertEquals(1L, application.state.totalTicketsFound)
        assertEquals(1, application.ledger.dailyTicketAwards)
        assertTrue("round_jackpot_01" in application.ledger.evaluatedRoundIds)
    }

    @Test
    fun sameRoundCannotAwardAgain() {
        val today = LocalDate.now().toEpochDay()
        val first = PuppyCasinoRewardEngine.apply(
            before = V6GameState(),
            settledRound = round("round_award_0040", payout = 10_000L),
            ledger = PuppyCasinoRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )
        val second = PuppyCasinoRewardEngine.apply(
            before = first.state,
            settledRound = round("round_duplicate_1", payout = 10_000L),
            ledger = first.ledger,
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoRewardStatus.ALREADY_EVALUATED, second.reward.status)
        assertEquals(first.state.ticketInventory, second.state.ticketInventory)
        assertEquals(first.state.totalTicketsFound, second.state.totalTicketsFound)
        assertEquals(1, second.ledger.dailyTicketAwards)
    }

    @Test
    fun dailyCasinoTicketCapIsOneAndRoundIsStillConsumed() {
        val today = LocalDate.now().toEpochDay()
        val application = PuppyCasinoRewardEngine.apply(
            before = V6GameState(),
            settledRound = round("round_award_0001", payout = 10_000L),
            ledger = PuppyCasinoRewardLedger(
                dailyEpochDay = today,
                dailyTicketAwards = PuppyCasinoRewardEngine.MAX_DAILY_CASINO_TICKETS
            ),
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoRewardStatus.DAILY_CAP_REACHED, application.reward.status)
        assertEquals(0, application.state.ticketsOwned)
        assertTrue("round_daily_cap1" in application.ledger.evaluatedRoundIds)
        assertEquals(
            PuppyCasinoRewardEngine.MAX_DAILY_CASINO_TICKETS,
            application.ledger.dailyTicketAwards
        )
    }

    @Test
    fun rarityInventoryCapPreventsOverflowAndConsumesEvent() {
        val today = LocalDate.now().toEpochDay()
        val round = round("round_award_0009", payout = 10_000L)
        val rarity = PuppyCasinoRewardEngine.deterministicRarity(round.roundId)
        val before = V6GameState(
            ticketInventory = TicketRarity.entries.associateWith {
                if (it == rarity) PuppyCasinoRewardEngine.MAX_TICKETS_PER_RARITY else 0
            }
        )

        val application = PuppyCasinoRewardEngine.apply(
            before = before,
            settledRound = round,
            ledger = PuppyCasinoRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoRewardStatus.INVENTORY_CAP_REACHED, application.reward.status)
        assertEquals(rarity, application.reward.rarity)
        assertEquals(
            PuppyCasinoRewardEngine.MAX_TICKETS_PER_RARITY,
            application.state.ticketInventory[rarity]
        )
        assertEquals(0, application.ledger.dailyTicketAwards)
        assertTrue(round.roundId in application.ledger.evaluatedRoundIds)
    }

    @Test
    fun pushOrLossNeverDropsATicketButIsEvaluatedOnce() {
        val today = LocalDate.now().toEpochDay()
        val application = PuppyCasinoRewardEngine.apply(
            before = V6GameState(),
            settledRound = round("round_no_profit1", payout = 100L),
            ledger = PuppyCasinoRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoRewardStatus.NOT_ELIGIBLE, application.reward.status)
        assertNull(application.reward.rarity)
        assertEquals(0, application.state.ticketsOwned)
        assertTrue("round_no_profit1" in application.ledger.evaluatedRoundIds)
    }

    @Test
    fun newEpochDayResetsDailyCountButKeepsDuplicateHistory() {
        val yesterday = LocalDate.now().minusDays(1).toEpochDay()
        val today = LocalDate.now().toEpochDay()
        val application = PuppyCasinoRewardEngine.apply(
            before = V6GameState(),
            settledRound = round("round_award_0034", payout = 10_000L),
            ledger = PuppyCasinoRewardLedger(
                evaluatedRoundIds = listOf("round_old_day_01"),
                dailyEpochDay = yesterday,
                dailyTicketAwards = PuppyCasinoRewardEngine.MAX_DAILY_CASINO_TICKETS
            ),
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoRewardStatus.AWARDED, application.reward.status)
        assertEquals(1, application.ledger.dailyTicketAwards)
        assertTrue("round_old_day_01" in application.ledger.evaluatedRoundIds)
        assertTrue("round_new_day_01" in application.ledger.evaluatedRoundIds)
    }

    @Test
    fun deterministicRollAndRarityAreStableForSameRound() {
        val id = "round_stability01"
        assertEquals(
            PuppyCasinoRewardEngine.deterministicRoll(id, "ticket-drop", 10_000),
            PuppyCasinoRewardEngine.deterministicRoll(id, "ticket-drop", 10_000)
        )
        assertEquals(
            PuppyCasinoRewardEngine.deterministicRarity(id),
            PuppyCasinoRewardEngine.deterministicRarity(id)
        )
    }
}
