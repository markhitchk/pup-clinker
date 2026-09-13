package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyBlackjackEngineTest {
    private fun deckWithPrefix(vararg prefix: Int): List<Int> =
        prefix.toList() + (0..51).filterNot { it in prefix.toSet() }

    @Test
    fun playerNaturalPaysThreeToTwoProfit() {
        val state = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(0, 9, 12, 6)
        )

        assertTrue(state.complete)
        val outcome = PuppyBlackjackEngine.outcome(state)
        assertEquals(PuppyBlackjackHandResult.BLACKJACK, outcome.hands.single().result)
        assertEquals(250L, outcome.totalPayoutTreats)
    }

    @Test
    fun dealerBlackjackResolvesImmediatelyBeforePlayerActions() {
        val state = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(9, 0, 6, 12)
        )

        assertTrue(state.complete)
        val outcome = PuppyBlackjackEngine.outcome(state)
        assertTrue(outcome.dealerBlackjack)
        assertEquals(PuppyBlackjackHandResult.LOSS, outcome.hands.single().result)
        assertEquals(0L, outcome.totalPayoutTreats)
    }

    @Test
    fun simultaneousNaturalsPush() {
        val state = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(0, 13, 12, 25)
        )

        assertTrue(state.complete)
        val outcome = PuppyBlackjackEngine.outcome(state)
        assertTrue(outcome.dealerBlackjack)
        assertEquals(PuppyBlackjackHandResult.PUSH, outcome.hands.single().result)
        assertEquals(100L, outcome.totalPayoutTreats)
    }

    @Test
    fun dealerStandsOnSoftSeventeen() {
        val initial = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(9, 0, 6, 18, 1)
        )
        assertFalse(initial.complete)

        val stood = PuppyBlackjackEngine.stand(initial)

        assertTrue(stood.success)
        assertTrue(stood.state.complete)
        assertEquals(2, stood.state.dealerCards.size)
        val dealer = PuppyBlackjackEngine.handValue(stood.state.dealerCards)
        assertEquals(17, dealer.total)
        assertTrue(dealer.soft)
    }

    @Test
    fun doubleUsesEqualAdditionalWagerAndOneFinalCard() {
        val initial = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(4, 7, 5, 8, 9, 2, 3)
        )
        assertTrue(PuppyBlackjackEngine.canDouble(initial))

        val doubled = PuppyBlackjackEngine.doubleDown(initial)

        assertTrue(doubled.success)
        assertEquals(100L, doubled.additionalWagerTreats)
        assertEquals(200L, doubled.state.hands.single().wagerTreats)
        assertEquals(3, doubled.state.hands.single().cards.size)
        assertTrue(doubled.state.hands.single().doubled)
        assertTrue(doubled.state.complete)
    }

    @Test
    fun splitAddsOneEqualWagerAndCreatesTwoHands() {
        val initial = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(7, 4, 20, 8, 1, 2, 3)
        )
        assertTrue(PuppyBlackjackEngine.canSplit(initial))

        val split = PuppyBlackjackEngine.split(initial)

        assertTrue(split.success)
        assertEquals(100L, split.additionalWagerTreats)
        assertEquals(2, split.state.hands.size)
        assertEquals(200L, PuppyBlackjackEngine.totalWager(split.state))
        assertTrue(split.state.hands.all { it.fromSplit })
        assertFalse(split.state.complete)
    }

    @Test
    fun splitAcesReceiveOneCardEachAndCannotBeResplit() {
        val initial = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(0, 4, 13, 8, 26, 1, 2)
        )

        val split = PuppyBlackjackEngine.split(initial)

        assertTrue(split.success)
        assertEquals(2, split.state.hands.size)
        assertTrue(split.state.hands.all { it.splitAces })
        assertTrue(split.state.hands.all { it.cards.size == 2 })
        assertTrue(split.state.hands.all { it.stood })
        assertTrue(split.state.complete)
    }

    @Test
    fun maximumThreePlayerHandsAreAllowed() {
        val state = PuppyBlackjackState(
            remainingDeck = (0..51).filterNot {
                it in setOf(7, 20, 1, 2, 3, 4, 5, 6, 8, 9)
            },
            dealerCards = listOf(8, 9),
            hands = listOf(
                PuppyBlackjackHand(listOf(1, 2), 100L, stood = true, fromSplit = true),
                PuppyBlackjackHand(listOf(3, 4), 100L, stood = true, fromSplit = true),
                PuppyBlackjackHand(listOf(7, 20), 100L, fromSplit = true)
            ),
            activeHandIndex = 2,
            complete = false
        )

        assertFalse(PuppyBlackjackEngine.canSplit(state))
    }

    @Test
    fun equalTotalsPushAndReturnTheHandWager() {
        val initial = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(9, 8, 6, 7, 1)
        )
        val final = PuppyBlackjackEngine.stand(initial).state
        val outcome = PuppyBlackjackEngine.outcome(final)

        assertEquals(PuppyBlackjackHandResult.PUSH, outcome.hands.single().result)
        assertEquals(100L, outcome.totalPayoutTreats)
    }

    @Test
    fun stateCodecRejectsDuplicateOrTamperedCards() {
        val state = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(4, 7, 5, 8, 9)
        )
        val encoded = PuppyBlackjackStateCodec.encode(state)
        val firstRemaining = state.remainingDeck.first()
        val tampered = encoded.replaceFirst(
            "\"remainingDeck\":[" + firstRemaining,
            "\"remainingDeck\":[4"
        )

        assertNull(PuppyBlackjackStateCodec.decodeAndValidate(tampered))
    }

    @Test
    fun outcomeCodecRejectsTamperedPayout() {
        val state = PuppyBlackjackEngine.newRoundFromDeck(
            100L,
            deckWithPrefix(0, 9, 12, 6)
        )
        val outcome = PuppyBlackjackEngine.outcome(state)
        val encoded = PuppyBlackjackOutcomeCodec.encode(outcome)
        val tampered = encoded.replace(
            "\"totalPayoutTreats\":250",
            "\"totalPayoutTreats\":25000"
        )

        assertNull(PuppyBlackjackOutcomeCodec.decodeAndValidate(tampered, state))
    }

    @Test
    fun wagerRulesStayWithinCasinoLimits() {
        assertFalse(PuppyBlackjackEngine.isValidInitialWager(10L))
        assertTrue(PuppyBlackjackEngine.isValidInitialWager(20L))
        assertTrue(PuppyBlackjackEngine.isValidInitialWager(100_000L))
        assertFalse(PuppyBlackjackEngine.isValidInitialWager(100_010L))
        assertFalse(PuppyBlackjackEngine.isValidInitialWager(25L))
    }
}
