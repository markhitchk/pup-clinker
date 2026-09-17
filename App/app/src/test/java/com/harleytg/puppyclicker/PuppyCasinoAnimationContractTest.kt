package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoAnimationContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/" + name).readText()

    @Test
    fun slotsVisiblyCycleReelsBeforePersistedOutcomeReveal() {
        val slots = source("PuppySlotsUi.kt")

        assertTrue(slots.contains("revealRoundId = round.roundId"))
        assertTrue(slots.contains("repeat(ticks)"))
        assertTrue(slots.contains("val stopTicks = listOf(16, 23, 30)"))
        assertTrue(slots.contains("PuppySlotSymbol.entries"))
        assertTrue(slots.contains("AnimatedContent("))
        assertTrue(slots.contains("slideInVertically("))
        assertTrue(slots.contains("slideOutVertically("))
        assertTrue(slots.contains("visibleEmoji = emoji"))
        assertTrue(slots.contains("revealFinishedRoundId = round.roundId"))
        assertTrue(slots.contains("Result reveals after the final reel locks."))
        assertTrue(slots.contains("delay(if (state.animationsEnabled) 2_350 else 250)"))
    }

    @Test
    fun rouletteUsesCircularEuropeanWheelAndAnimatedLandingBall() {
        val roulette = source("PuppyRouletteUi.kt")

        assertTrue(roulette.contains("EuropeanRouletteWheelOrder"))
        assertTrue(roulette.contains("Canvas(Modifier.size(176.dp))"))
        assertTrue(roulette.contains("RouletteColorBoard("))
        assertTrue(roulette.contains("RouletteNumberPickerDialog("))
        assertTrue(roulette.contains("showNumberPicker"))
        assertTrue(roulette.contains("drawArc("))
        assertTrue(roulette.contains("val wheelRotation = remember { Animatable(0f) }"))
        assertTrue(roulette.contains("val ballAngle = remember { Animatable(-90f) }"))
        assertTrue(roulette.contains("val ballRadiusFraction = remember { Animatable(0.93f) }"))
        assertTrue(roulette.contains("ballCruiseTarget"))
        assertTrue(roulette.contains("ballRadiusFraction.animateTo("))
        assertTrue(roulette.contains("ballTarget"))
        assertTrue(roulette.contains("if (showResult) outcome?.winningNumber"))
        assertTrue(roulette.contains("revealFinishedRoundId = round.roundId"))
        assertTrue(roulette.contains("if (resultRevealReady && displayOutcome != null)"))
        assertTrue(roulette.contains("delay(if (state.animationsEnabled) 2_600 else 250)"))
    }

    @Test
    fun blackjackDealsAndRevealsCardsWithMotion() {
        val blackjack = source("PuppyBlackjackUi.kt")

        assertTrue(blackjack.contains("val reveal = remember { Animatable(1f) }"))
        assertTrue(blackjack.contains("dealDelayMs = index * 120L"))
        assertTrue(blackjack.contains("translationX = (1f - reveal.value) * 42f"))
        assertTrue(blackjack.contains("translationY = (1f - reveal.value) * -46f"))
        assertTrue(blackjack.contains("rotationY = (1f - reveal.value) * 105f"))
        assertTrue(blackjack.contains("rotationZ = (1f - reveal.value) * -7f"))
        assertTrue(blackjack.contains("shadowElevation = 2f + (9f * reveal.value)"))
        assertTrue(blackjack.contains("BlackjackActionPanel("))
        assertTrue(blackjack.contains("🐾 + HIT"))
        assertTrue(blackjack.contains("■ STAND"))
        assertTrue(blackjack.contains("animateFloatAsState("))
        assertTrue(blackjack.contains("animationToken = id.toString() + \":\" + hidden"))
    }

    @Test
    fun newGamesUseRequiredPhysicalInteractions() {
        val plinko = source("PuppyPlinkoUi.kt")
        val scratchers = source("PuppyScratchersUi.kt")
        val streamedCoin = source("StreamedPupCoin.kt")
        val luckyWheel = source("PuppyLuckyWheelUi.kt")

        assertTrue(plinko.contains("PlinkoBoard("))
        assertTrue(plinko.contains("progress.animateTo("))
        assertTrue(plinko.contains("delay(if (state.animationsEnabled) 2_200 else 250)"))

        assertTrue(scratchers.contains("detectDragGestures("))
        assertTrue(scratchers.contains("REVEAL_THRESHOLD"))
        assertTrue(scratchers.contains("revealAll = revealed"))
        assertTrue(scratchers.contains("Pup Coin"))
        assertTrue(scratchers.contains("speedBoost"))
        assertTrue(scratchers.contains("coinRotation"))
        assertTrue(scratchers.contains("contentAlignment = Alignment.TopStart"))
        assertTrue(scratchers.contains("markSegment("))
        assertTrue(scratchers.contains("change.uptimeMillis"))
        assertTrue(scratchers.contains("coinRadiusPx *"))
        assertTrue(scratchers.contains("SCRATCH_COLUMNS = 48"))
        assertTrue(scratchers.contains("streamedPupCoinPainter()"))
        assertTrue(streamedCoin.contains("assets/pup_coin.png"))
        assertTrue(streamedCoin.contains("PupCoinAssetStream"))

        assertTrue(luckyWheel.contains("LuckyWheelBoard("))
        assertTrue(luckyWheel.contains("rotation.animateTo("))
        assertTrue(luckyWheel.contains("PUP UNLOCK"))
        assertTrue(luckyWheel.contains("delay(if (state.animationsEnabled) 2_650 else 250)"))
    }

    @Test
    fun animationsNeverGenerateCasinoOutcomeInUi() {
        val slots = source("PuppySlotsUi.kt")
        val roulette = source("PuppyRouletteUi.kt")
        val blackjack = source("PuppyBlackjackUi.kt")
        val plinko = source("PuppyPlinkoUi.kt")
        val scratchers = source("PuppyScratchersUi.kt")
        val luckyWheel = source("PuppyLuckyWheelUi.kt")

        assertFalse(slots.contains("SecureRandom"))
        assertFalse(roulette.contains("SecureRandom"))
        assertFalse(blackjack.contains("SecureRandom"))
        assertFalse(plinko.contains("SecureRandom"))
        assertFalse(scratchers.contains("SecureRandom"))
        assertFalse(luckyWheel.contains("SecureRandom"))
        assertTrue(slots.contains("vm.startSlotsSpin(wager)"))
        assertTrue(roulette.contains("vm.startRouletteSpin("))
        assertTrue(blackjack.contains("vm.startBlackjackRound(wager)"))
        assertTrue(plinko.contains("vm.startPlinkoDrop(wager)"))
        assertTrue(scratchers.contains("vm.startScratcher(wager)"))
        assertTrue(luckyWheel.contains("vm.startLuckyPupWheel(wager)"))
    }
}
