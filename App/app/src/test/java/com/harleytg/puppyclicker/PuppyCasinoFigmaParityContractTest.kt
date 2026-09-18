package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoFigmaParityContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun sharedRenderStageKeepsApprovedDarkFrameLanguage() {
        val common = source("PuppyCasinoCartoonUi.kt")
        assertTrue(common.contains("height: Dp = 330.dp"))
        assertTrue(common.contains("background: Color = Color(0xFF101820)"))
        assertTrue(common.contains("shape = RoundedCornerShape(26.dp)"))
        assertTrue(common.contains("color = Color(0xFF16232D)"))
    }

    @Test
    fun gachaMatchesApprovedMachineProportionsAndSinglePurchaseCta() {
        val gacha = source("PuppyGacha.kt")
        assertTrue(gacha.contains("title = \"PUPPY GACHA\""))
        assertTrue(gacha.contains("height = 330.dp"))
        assertTrue(gacha.contains("Modifier.fillMaxWidth().height(138.dp)"))
        assertTrue(gacha.contains("Modifier.fillMaxWidth().height(136.dp)"))
        assertTrue(gacha.contains(".width(192.dp)"))
        assertTrue(gacha.contains("Pull Capsule • 2,500 Treats"))
        assertTrue(gacha.contains("duplicate puppy pulls are disabled"))
        assertFalse(gacha.contains("future pulls reveal an owned eligible puppy"))
    }

    @Test
    fun wheelMatchesApprovedPurple330StageAnd190Wheel() {
        val wheel = source("PuppyLuckyWheelUi.kt")
        assertTrue(wheel.contains("title = \"LUCKY PUP WHEEL\""))
        assertTrue(wheel.contains("height = 330.dp"))
        assertTrue(wheel.contains("accent = Color(0xFF7C4DFF)"))
        assertTrue(wheel.contains("Canvas(Modifier.size(190.dp))"))
        assertTrue(wheel.contains("Modifier.fillMaxWidth().height(54.dp)"))
    }

    @Test
    fun plinkoMatchesApprovedBoardAndStatusGeometry() {
        val plinko = source("PuppyPlinkoUi.kt")
        assertTrue(plinko.contains("title = \"PUP PLINKO\""))
        assertTrue(plinko.contains("height = 330.dp"))
        assertTrue(plinko.contains(".height(224.dp)"))
        assertTrue(plinko.contains("color = Color(0xFF0B1117)"))
        assertTrue(plinko.contains("Color(0xFF394854)"))
        assertTrue(plinko.contains("Color(0xFFCBD7DE)"))
    }

    @Test
    fun scratchersMatchApprovedGreenGold292Stage() {
        val scratch = source("PuppyScratchersUi.kt")
        assertTrue(scratch.contains("height = 292.dp"))
        assertTrue(scratch.contains("accent = Color(0xFFF8C24E)"))
        assertTrue(scratch.contains("background = Color(0xFF174C3F)"))
        assertTrue(scratch.contains(".height(150.dp)"))
        assertTrue(scratch.contains("color = Color(0xFF0F6A4A)"))
    }

    @Test
    fun blackjackMatchesApprovedTableAndActionPanel() {
        val blackjack = source("PuppyBlackjackUi.kt")
        assertTrue(blackjack.contains("Modifier.fillMaxWidth().height(360.dp)"))
        assertTrue(blackjack.contains("color = Color(0xFF0F6A4A)"))
        assertTrue(blackjack.contains("Modifier.fillMaxWidth().height(144.dp)"))
        assertTrue(blackjack.contains("color = Color(0xFF2C7A5D)"))
        assertTrue(blackjack.contains("Modifier.fillMaxWidth().height(114.dp)"))
        assertTrue(blackjack.contains(".width(58.dp)"))
        assertTrue(blackjack.contains(".height(82.dp)"))
    }
}
