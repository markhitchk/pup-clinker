package com.harleytg.puppyclicker

import org.junit.Assert.*
import org.junit.Test

class PuppyBlackjackLayoutTest {
    @Test fun cardsStayInsidePlayingAreasAtPhoneAndTabletWidths() {
        for (width in listOf(240f, 280f, 320f, 360f, 430f, 600f)) {
            val table = PuppyBlackjackLayout.table(width)
            assertEquals(1122f / 1402f, table.width / table.artworkHeight, 0.0001f)
            assertEquals(1122f / 1188f, table.width / table.height, 0.0001f)
            for (zone in listOf(table.dealer, table.player)) {
                for (count in 2..12) {
                    val fan = PuppyBlackjackLayout.fan(zone.width, table.cardWidth, count)
                    assertTrue(fan.cardWidth > 0f)
                    assertTrue(fan.step > 0f)
                    assertTrue(fan.width <= zone.width + 0.01f)
                    assertTrue(fan.cardWidth * 1.4f <= zone.height)
                    assertTrue(zone.left >= 0 && zone.left + zone.width <= table.width)
                    assertTrue(zone.top >= 0 && zone.top + zone.height <= table.height)
                }
            }
        }
    }

    @Test fun normalHandsUseSameCardDimensionsAndCrowdedHandsOverlap() {
        val table = PuppyBlackjackLayout.table(360f)
        val dealer = PuppyBlackjackLayout.fan(table.dealer.width, table.cardWidth, 2)
        val player = PuppyBlackjackLayout.fan(table.player.width, table.cardWidth, 2)
        assertEquals(dealer.cardWidth, player.cardWidth, 0.001f)
        val crowded = PuppyBlackjackLayout.fan(table.player.width, table.cardWidth, 10)
        assertTrue(crowded.step < crowded.cardWidth)
    }

    @Test fun completionAndInsufficientChipsDisableActions() {
        val deck = listOf(7, 4, 20, 8, 1, 2) + (0..51).filterNot { it in listOf(7, 4, 20, 8, 1, 2) }
        val initial = PuppyBlackjackEngine.newRoundFromDeck(100, deck)
        assertTrue(PuppyBlackjackControls.from(initial, 100).split)
        assertTrue(PuppyBlackjackControls.from(initial, 100).double)
        assertFalse(PuppyBlackjackControls.from(initial, 99).split)
        assertFalse(PuppyBlackjackControls.from(initial, 99).double)
        val finished = PuppyBlackjackEngine.stand(initial).state
        assertEquals(PuppyBlackjackControls(false, false, false, false), PuppyBlackjackControls.from(finished, 1000))
    }
}
