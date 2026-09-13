package com.harleytg.puppyclicker

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppySlotsEngineTest {
    @Test
    fun publishedWeightsCoverExactlyOneHundredPoints() {
        assertEquals(100, PuppySlotSymbol.entries.sumOf { it.weightPercent })
        assertEquals(
            listOf(30, 25, 20, 13, 8, 4),
            PuppySlotSymbol.entries.map { it.weightPercent }
        )
    }

    @Test
    fun rollBoundariesMapToPublishedSymbols() {
        assertEquals(PuppySlotSymbol.TREAT, PuppySlotsEngine.symbolForRoll(0))
        assertEquals(PuppySlotSymbol.TREAT, PuppySlotsEngine.symbolForRoll(29))
        assertEquals(PuppySlotSymbol.BALL, PuppySlotsEngine.symbolForRoll(30))
        assertEquals(PuppySlotSymbol.BALL, PuppySlotsEngine.symbolForRoll(54))
        assertEquals(PuppySlotSymbol.PAW, PuppySlotsEngine.symbolForRoll(55))
        assertEquals(PuppySlotSymbol.PAW, PuppySlotsEngine.symbolForRoll(74))
        assertEquals(PuppySlotSymbol.TICKET, PuppySlotsEngine.symbolForRoll(75))
        assertEquals(PuppySlotSymbol.TICKET, PuppySlotsEngine.symbolForRoll(87))
        assertEquals(PuppySlotSymbol.PUPPY, PuppySlotsEngine.symbolForRoll(88))
        assertEquals(PuppySlotSymbol.PUPPY, PuppySlotsEngine.symbolForRoll(95))
        assertEquals(PuppySlotSymbol.STAR, PuppySlotsEngine.symbolForRoll(96))
        assertEquals(PuppySlotSymbol.STAR, PuppySlotsEngine.symbolForRoll(99))
    }

    @Test
    fun pairReturnsHalfOfOriginalWager() {
        val outcome = PuppySlotsEngine.spinFromRolls(
            wagerTreats = 100L,
            rolls = intArrayOf(0, 1, 30)
        )

        assertEquals(PuppySlotsWinKind.PAIR, outcome.winKind)
        assertEquals("0.5×", outcome.multiplierLabel)
        assertEquals(50L, outcome.payoutTreats)
    }

    @Test
    fun noMatchReturnsZero() {
        val outcome = PuppySlotsEngine.spinFromRolls(
            wagerTreats = 100L,
            rolls = intArrayOf(0, 30, 55)
        )

        assertEquals(PuppySlotsWinKind.LOSS, outcome.winKind)
        assertEquals(0L, outcome.payoutTreats)
    }

    @Test
    fun triplePayoutsAreTotalReturnIncludingWager() {
        val cases = listOf(
            intArrayOf(0, 0, 0) to 700L,
            intArrayOf(30, 30, 30) to 1_200L,
            intArrayOf(55, 55, 55) to 1_800L,
            intArrayOf(75, 75, 75) to 3_500L,
            intArrayOf(88, 88, 88) to 10_000L,
            intArrayOf(96, 96, 96) to 50_000L
        )

        cases.forEach { (rolls, expectedReturn) ->
            val outcome = PuppySlotsEngine.spinFromRolls(100L, rolls)
            assertEquals(PuppySlotsWinKind.TRIPLE, outcome.winKind)
            assertEquals(expectedReturn, outcome.payoutTreats)
        }
    }

    @Test
    fun maximumPublishedJackpotStaysInsideSafeLongRange() {
        val outcome = PuppySlotsEngine.spinFromRolls(
            wagerTreats = PuppySlotsEngine.MAX_WAGER_TREATS,
            rolls = intArrayOf(99, 99, 99)
        )

        assertEquals(50_000_000L, outcome.payoutTreats)
    }

    @Test
    fun publishedTableHasAboutNinetyTwoPointSixSixPercentRtp() {
        val pairProbability = PuppySlotSymbol.entries.sumOf { symbol ->
            val p = symbol.weightPercent / 100.0
            3.0 * p * p * (1.0 - p)
        }
        val pairReturn = pairProbability * 0.5
        val tripleReturn = PuppySlotSymbol.entries.sumOf { symbol ->
            val p = symbol.weightPercent / 100.0
            p * p * p * symbol.tripleReturnMultiplier
        }
        val rtpPercent = (pairReturn + tripleReturn) * 100.0

        assertTrue(abs(rtpPercent - 92.6598) < 0.0001)
        assertEquals("92.66%", PuppySlotsEngine.PUBLISHED_RTP_PERCENT)
    }

    @Test
    fun encodedOutcomeRoundTripsOnlyWhenPayoutMatchesTable() {
        val outcome = PuppySlotsEngine.spinFromRolls(
            wagerTreats = 100L,
            rolls = intArrayOf(96, 96, 96)
        )
        val encoded = PuppySlotsOutcomeCodec.encode(outcome)

        val decoded = PuppySlotsOutcomeCodec.decodeAndValidate(encoded, 100L)

        assertEquals(outcome, decoded)
    }

    @Test
    fun tamperedSavedPayoutIsRejected() {
        val outcome = PuppySlotsEngine.spinFromRolls(
            wagerTreats = 100L,
            rolls = intArrayOf(0, 0, 0)
        )
        val encoded = PuppySlotsOutcomeCodec.encode(outcome)
        val tampered = encoded.replace("\"payoutTreats\":700", "\"payoutTreats\":70000")

        assertNull(PuppySlotsOutcomeCodec.decodeAndValidate(tampered, 100L))
    }

    @Test
    fun wagerRulesAreBoundedAndIncremented() {
        assertFalse(PuppySlotsEngine.isValidWager(10L))
        assertTrue(PuppySlotsEngine.isValidWager(20L))
        assertTrue(PuppySlotsEngine.isValidWager(100_000L))
        assertFalse(PuppySlotsEngine.isValidWager(100_010L))
        assertFalse(PuppySlotsEngine.isValidWager(25L))
    }
}
