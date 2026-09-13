package com.harleytg.puppyclicker

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyRouletteEngineTest {
    @Test
    fun europeanColorsMatchPublishedTable() {
        assertEquals(PuppyRouletteColor.GREEN, PuppyRouletteEngine.colorOf(0))
        assertEquals(PuppyRouletteColor.RED, PuppyRouletteEngine.colorOf(1))
        assertEquals(PuppyRouletteColor.BLACK, PuppyRouletteEngine.colorOf(2))
        assertEquals(PuppyRouletteColor.RED, PuppyRouletteEngine.colorOf(36))
    }

    @Test
    fun zeroLosesAllEvenMoneyBets() {
        val bets = listOf(
            PuppyRouletteBet(PuppyRouletteBetType.RED),
            PuppyRouletteBet(PuppyRouletteBetType.BLACK),
            PuppyRouletteBet(PuppyRouletteBetType.ODD),
            PuppyRouletteBet(PuppyRouletteBetType.EVEN),
            PuppyRouletteBet(PuppyRouletteBetType.LOW),
            PuppyRouletteBet(PuppyRouletteBetType.HIGH)
        )

        bets.forEach { bet ->
            val outcome = PuppyRouletteEngine.spinFromNumber(100L, bet, 0)
            assertFalse(outcome.won)
            assertEquals(0L, outcome.payoutTreats)
        }
    }

    @Test
    fun straightZeroWinsAtThirtySixTimesTotalReturn() {
        val outcome = PuppyRouletteEngine.spinFromNumber(
            wagerTreats = 100L,
            bet = PuppyRouletteBet(PuppyRouletteBetType.STRAIGHT, 0),
            winningNumber = 0
        )

        assertTrue(outcome.won)
        assertEquals(PuppyRouletteColor.GREEN, outcome.color)
        assertEquals(36, outcome.totalReturnMultiplier)
        assertEquals(3_600L, outcome.payoutTreats)
    }

    @Test
    fun evenMoneyBetsReturnTwoTimesTotal() {
        val cases = listOf(
            PuppyRouletteBet(PuppyRouletteBetType.RED) to 1,
            PuppyRouletteBet(PuppyRouletteBetType.BLACK) to 2,
            PuppyRouletteBet(PuppyRouletteBetType.ODD) to 3,
            PuppyRouletteBet(PuppyRouletteBetType.EVEN) to 4,
            PuppyRouletteBet(PuppyRouletteBetType.LOW) to 18,
            PuppyRouletteBet(PuppyRouletteBetType.HIGH) to 19
        )

        cases.forEach { (bet, number) ->
            val outcome = PuppyRouletteEngine.spinFromNumber(100L, bet, number)
            assertTrue(outcome.won)
            assertEquals(2, outcome.totalReturnMultiplier)
            assertEquals(200L, outcome.payoutTreats)
        }
    }

    @Test
    fun wrongSelectionsLose() {
        assertFalse(
            PuppyRouletteEngine.spinFromNumber(
                100L,
                PuppyRouletteBet(PuppyRouletteBetType.RED),
                2
            ).won
        )
        assertFalse(
            PuppyRouletteEngine.spinFromNumber(
                100L,
                PuppyRouletteBet(PuppyRouletteBetType.LOW),
                19
            ).won
        )
        assertFalse(
            PuppyRouletteEngine.spinFromNumber(
                100L,
                PuppyRouletteBet(PuppyRouletteBetType.STRAIGHT, 7),
                8
            ).won
        )
    }

    @Test
    fun theoreticalRtpMatchesSingleZeroEuropeanRoulette() {
        val rtp = 36.0 / 37.0 * 100.0
        assertTrue(abs(rtp - 97.2972972973) < 0.0000001)
        assertEquals("97.30%", PuppyRouletteEngine.PUBLISHED_RTP_PERCENT)
    }

    @Test
    fun wagerCodecRoundTripsSelectedBet() {
        val bet = PuppyRouletteBet(PuppyRouletteBetType.STRAIGHT, 27)
        val encoded = PuppyRouletteBetCodec.encode(bet)
        assertEquals(bet, PuppyRouletteBetCodec.decodeAndValidate(encoded))
    }

    @Test
    fun outcomeCodecRejectsChangedBet() {
        val wager = 100L
        val bet = PuppyRouletteBet(PuppyRouletteBetType.RED)
        val outcome = PuppyRouletteEngine.spinFromNumber(wager, bet, 1)
        val encoded = PuppyRouletteOutcomeCodec.encode(outcome)

        assertNull(
            PuppyRouletteOutcomeCodec.decodeAndValidate(
                raw = encoded,
                wagerTreats = wager,
                expectedBet = PuppyRouletteBet(PuppyRouletteBetType.BLACK)
            )
        )
    }

    @Test
    fun outcomeCodecRejectsTamperedWinningNumberOrPayout() {
        val wager = 100L
        val bet = PuppyRouletteBet(PuppyRouletteBetType.RED)
        val outcome = PuppyRouletteEngine.spinFromNumber(wager, bet, 1)
        val encoded = PuppyRouletteOutcomeCodec.encode(outcome)

        val payoutTampered = encoded.replace(
            "\"payoutTreats\":200",
            "\"payoutTreats\":20000"
        )
        assertNull(
            PuppyRouletteOutcomeCodec.decodeAndValidate(
                payoutTampered,
                wager,
                bet
            )
        )

        val numberTampered = encoded.replace(
            "\"winningNumber\":1",
            "\"winningNumber\":2"
        )
        assertNull(
            PuppyRouletteOutcomeCodec.decodeAndValidate(
                numberTampered,
                wager,
                bet
            )
        )
    }

    @Test
    fun wagerRulesAreBoundedAndIncremented() {
        assertFalse(PuppyRouletteEngine.isValidWager(10L))
        assertTrue(PuppyRouletteEngine.isValidWager(20L))
        assertTrue(PuppyRouletteEngine.isValidWager(100_000L))
        assertFalse(PuppyRouletteEngine.isValidWager(100_010L))
        assertFalse(PuppyRouletteEngine.isValidWager(25L))
    }
}
