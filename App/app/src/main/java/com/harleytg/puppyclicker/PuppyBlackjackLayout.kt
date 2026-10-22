package com.harleytg.puppyclicker

import kotlin.math.min

/** Artwork coordinates, never screen coordinates. All measurements use the same unit. */
internal object PuppyBlackjackLayout {
    const val SOURCE_WIDTH = 1122f
    const val SOURCE_HEIGHT = 1402f
    // The bitmap has near-transparent padding above/below the painted table (alpha <= 10).
    // Exclude that padding from layout, without rescaling axes or modifying any artwork.
    const val ART_TOP = 86f
    const val ART_HEIGHT = 1188f
    const val TABLE_ASPECT = SOURCE_WIDTH / ART_HEIGHT
    data class Zone(val left: Float, val top: Float, val width: Float, val height: Float)
    data class Table(val width: Float, val height: Float, val dealer: Zone, val player: Zone, val cardWidth: Float, val artworkHeight: Float, val artworkTop: Float)
    data class Fan(val cardWidth: Float, val step: Float, val width: Float)

    fun table(width: Float): Table {
        val height = width / TABLE_ASPECT
        val artworkHeight = width * SOURCE_HEIGHT / SOURCE_WIDTH
        val artworkTop = width * ART_TOP / SOURCE_WIDTH
        return Table(width, height,
            Zone(width * .25f, artworkHeight * .245f - artworkTop, width * .48f, artworkHeight * .15f),
            Zone(width * .18f, artworkHeight * .585f - artworkTop, width * .64f, artworkHeight * .20f),
            min(width * .13f, 56f), artworkHeight, artworkTop)
    }

    fun fan(available: Float, preferredCardWidth: Float, count: Int): Fan {
        if (count <= 0) return Fan(preferredCardWidth, 0f, 0f)
        val cardWidth = min(preferredCardWidth, available / (1f + (count - 1) * .43f))
        val step = if (count == 1) 0f else min(cardWidth + 6f, (available - cardWidth) / (count - 1))
        return Fan(cardWidth, step, cardWidth + step * (count - 1))
    }
}

/** Presentation eligibility delegates to the authoritative engine. */
internal data class PuppyBlackjackControls(val hit: Boolean, val stand: Boolean, val double: Boolean, val split: Boolean) {
    companion object {
        fun from(state: PuppyBlackjackState, chips: Long): PuppyBlackjackControls {
            val hand = state.hands.getOrNull(state.activeHandIndex)
            val funded = hand != null && chips >= hand.wagerTreats
            return PuppyBlackjackControls(PuppyBlackjackEngine.canHit(state), PuppyBlackjackEngine.canStand(state),
                funded && PuppyBlackjackEngine.canDouble(state), funded && PuppyBlackjackEngine.canSplit(state))
        }
    }
}
