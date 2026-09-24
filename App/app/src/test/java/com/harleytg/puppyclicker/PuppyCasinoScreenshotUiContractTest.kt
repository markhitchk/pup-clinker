package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoScreenshotUiContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun luckyWheelKeepsPrizeLabelsReadableWhileDiscRotates() {
        val wheel = source("PuppyLuckyWheelUi.kt")

        assertTrue(wheel.contains("LuckyWheelSegmentLabels("))
        assertTrue(wheel.contains("rotationZ = -wheelRotationDegrees"))
    }

    @Test
    fun blackjackWalletReplacesBakedTreatCopyBeforeDrawingCasinoChipCopy() {
        val blackjack = source("PuppyBlackjackUi.kt")

        assertTrue(blackjack.contains("BlackjackWalletCopy("))
        assertTrue(blackjack.contains("\"Casino Chip Wallet\""))
        assertFalse(blackjack.contains("modifier = Modifier.offset(x = maxWidth * 0.23f, y = maxHeight * 0.49f)"))
    }

    @Test
    fun scratcherStatusLivesOutsideTheScratchArtwork() {
        val scratchers = source("PuppyScratchersUi.kt")

        assertTrue(scratchers.contains("ScratcherStatusStrip("))
        assertTrue(scratchers.contains("stageModifier = Modifier"))
    }

    @Test
    fun slotsMascotIsNotBuriedBehindTheMachineMarquee() {
        val slots = source("PuppySlotsUi.kt")

        assertTrue(slots.contains("val mascotTop = machineTop - machineHeight * 0.16f"))
    }

    @Test
    fun plinkoLabelsShareTheExactBinOverlayGeometry() {
        val plinko = source("PuppyPlinkoUi.kt")

        assertTrue(plinko.contains("PlinkoBinsOverlay("))
        assertTrue(plinko.contains("val pocketCenterY = maxHeight * 0.884f"))
        assertTrue(plinko.contains("val xStep = maxWidth * 0.04675f"))
    }
}
