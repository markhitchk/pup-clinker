package com.harleytg.puppyclicker

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    private fun winningId(prefix: String, chanceBasisPoints: Int = 500): String {
        for (index in 0..100_000) {
            val id = prefix + index.toString().padStart(6, '0')
            if (PuppyCasinoRewardEngine.deterministicRoll(id, "ticket-drop", 10_000) < chanceBasisPoints) {
                return id
            }
        }
        error("No deterministic winning round ID found")
    }

    private fun losingId(prefix: String, chanceBasisPoints: Int = 500): String {
        for (index in 0..100_000) {
            val id = prefix + index.toString().padStart(6, '0')
            if (PuppyCasinoRewardEngine.deterministicRoll(id, "ticket-drop", 10_000) >= chanceBasisPoints) {
                return id
            }
        }
        error("No deterministic losing round ID found")
    }

    @Test
    fun chanceBandsMatchEconomyV7Policy() {
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
    fun hundredXIsFivePercentNotGuaranteed() {
        val today = LocalDate.now().toEpochDay()
        val id = losingId("round_hundred_no_")
        val application = PuppyCasinoRewardEngine.apply(
            before = V6GameState(),
            settledRound = round(id, payout = 10_000L),
            ledger = PuppyCasinoRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )

        assertEquals(500, application.reward.dropChanceBasisPoints)
        assertEquals(PuppyCasinoRewardStatus.NO_DROP, application.reward.status)
        assertEquals(0, application.state.ticketsOwned)
    }

    @Test
    fun successfulDeterministicRollAwardsExactlyOneExistingTicket() {
        val before = V6GameState()
        val today = LocalDate.now().toEpochDay()
        val id = winningId("round_ticket_win_")
        val application = PuppyCasinoRewardEngine.apply(
            before = before,
            settledRound = round(id, payout = 10_000L),
            ledger = PuppyCasinoRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoRewardStatus.AWARDED, application.reward.status)
        val rarity = application.reward.rarity!!
        assertEquals(1, application.state.ticketInventory[rarity])
        assertEquals(1, application.state.ticketsOwned)
        assertEquals(1L, application.state.totalTicketsFound)
        assertEquals(1, application.ledger.dailyTicketAwards)
        assertTrue(id in application.ledger.evaluatedRoundIds)
    }

    @Test
    fun sameRoundCannotAwardAgain() {
        val today = LocalDate.now().toEpochDay()
        val id = winningId("round_duplicate_")
        val first = PuppyCasinoRewardEngine.apply(
            before = V6GameState(),
            settledRound = round(id, payout = 10_000L),
            ledger = PuppyCasinoRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )
        val second = PuppyCasinoRewardEngine.apply(
            before = first.state,
            settledRound = round(id, payout = 10_000L),
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
        val id = winningId("round_daily_cap_")
        val application = PuppyCasinoRewardEngine.apply(
            before = V6GameState(),
            settledRound = round(id, payout = 10_000L),
            ledger = PuppyCasinoRewardLedger(
                dailyEpochDay = today,
                dailyTicketAwards = PuppyCasinoRewardEngine.MAX_DAILY_CASINO_TICKETS
            ),
            todayEpochDay = today
        )

        assertEquals(1, PuppyCasinoRewardEngine.MAX_DAILY_CASINO_TICKETS)
        assertEquals(PuppyCasinoRewardStatus.DAILY_CAP_REACHED, application.reward.status)
        assertEquals(0, application.state.ticketsOwned)
        assertTrue(id in application.ledger.evaluatedRoundIds)
        assertEquals(1, application.ledger.dailyTicketAwards)
    }

    @Test
    fun rarityInventoryCapPreventsOverflowAndConsumesEvent() {
        val today = LocalDate.now().toEpochDay()
        val id = winningId("round_inventory_")
        val settled = round(id, payout = 10_000L)
        val rarity = PuppyCasinoRewardEngine.deterministicRarity(settled.roundId)
        val before = V6GameState(
            ticketInventory = TicketRarity.entries.associateWith {
                if (it == rarity) PuppyCasinoRewardEngine.MAX_TICKETS_PER_RARITY else 0
            }
        )

        val application = PuppyCasinoRewardEngine.apply(
            before = before,
            settledRound = settled,
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
        assertTrue(settled.roundId in application.ledger.evaluatedRoundIds)
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
        val id = winningId("round_new_day_")
        val application = PuppyCasinoRewardEngine.apply(
            before = V6GameState(),
            settledRound = round(id, payout = 10_000L),
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
        assertTrue(id in application.ledger.evaluatedRoundIds)
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
        assertNotEquals(10_000, PuppyCasinoRewardEngine.dropChanceBasisPoints(round(id, payout = 10_000L)))
    }
}
