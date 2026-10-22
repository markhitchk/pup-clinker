package com.harleytg.puppyclicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1000dp-h1400dp-mdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PuppyBlackjackResponsiveTest {
    @get:Rule val compose = createComposeRule()
    private fun round(vararg prefix: Int): PuppyBlackjackState = PuppyBlackjackEngine.newRoundFromDeck(
        100, prefix.toList() + (0..51).filterNot { it in prefix.toSet() })

    private data class Scene(val name: String, val width: Int, val height: Int, val state: PuppyBlackjackState, val fontScale: Float = 1f)

    @Test fun responsiveScreenshotsAndCardsStayOnFelt() {
        val initial = round(5, 12, 18, 2, 3, 4)
        val pair = round(7, 4, 20, 8, 33, 2, 1, 3)
        val split = PuppyBlackjackEngine.split(pair).state
        val three = PuppyBlackjackEngine.split(split).state
        var manyCards = round(0, 12, 13, 25, 26, 39, 1, 14, 27, 40, 2, 15)
        repeat(8) { manyCards = PuppyBlackjackEngine.hit(manyCards).state }
        val scenes = listOf(
            Scene("phone-small", 320, 640, initial),
            Scene("phone-a-series", 360, 740, initial),
            Scene("phone-large", 430, 900, initial),
            Scene("ten-card-hand", 360, 740, manyCards),
            Scene("landscape", 800, 480, initial),
            Scene("tablet", 800, 1000, initial),
            Scene("split-two", 360, 740, split),
            Scene("split-three", 430, 900, three),
            Scene("large-text", 360, 740, initial, 1.8f),
            Scene("hit", 360, 740, PuppyBlackjackEngine.hit(initial).state),
            Scene("stand", 360, 740, PuppyBlackjackEngine.stand(initial).state),
            Scene("double", 360, 740, PuppyBlackjackEngine.doubleDown(initial).state),
            Scene("blackjack", 360, 740, round(0, 9, 12, 6)),
            Scene("bust", 360, 740, PuppyBlackjackEngine.hit(round(9, 4, 6, 8, 12)).state)
        )
        val current = mutableStateOf(scenes.first())
        lateinit var renderedView: View
        compose.setContent {
            renderedView = LocalView.current
            val scene = current.value
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scene.fontScale)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(Modifier.requiredSize(scene.width.dp, scene.height.dp).testTag("screenshot"), color = MaterialTheme.colorScheme.background) {
                        PuppyBlackjackGameContent(scene.state,
                            if (scene.state.complete) PuppyBlackjackEngine.outcome(scene.state) else null,
                            chips = 900, acceptingActions = !scene.state.complete, animationsEnabled = false, roundKey = scene.name)
                    }
                }
            }
        }
        val output = File("build/outputs/casino-ui").apply { mkdirs() }
        scenes.forEach { scene ->
            compose.runOnIdle { current.value = scene }
            compose.waitForIdle()
            compose.onNodeWithText("Casino Chip Wallet", substring = true).assertExists()
            compose.onNodeWithTag("blackjack_table").assertIsDisplayed()
            compose.onNodeWithTag("blackjack_hand_summary").assertExists()
            if (!scene.state.complete && scene.fontScale == 1f) {
                compose.onNodeWithText("🐾 HIT").assertIsDisplayed()
                compose.onNodeWithText("STAND").assertIsDisplayed()
            }
            if (scene.state.complete) compose.onNodeWithTag("blackjack_actions").assertDoesNotExist()
            val table = compose.onNodeWithTag("blackjack_table").fetchSemanticsNode().boundsInRoot
            for (tag in listOf("dealer", "player")) {
                val area = compose.onNodeWithTag("blackjack_${tag}_cards").fetchSemanticsNode().boundsInRoot
                assertTrue("$tag left in ${scene.name}", area.left >= table.left)
                assertTrue("$tag right in ${scene.name}", area.right <= table.right)
                assertTrue("$tag top in ${scene.name}", area.top >= table.top)
                assertTrue("$tag bottom in ${scene.name}", area.bottom <= table.bottom)
            }
            // Draw the real Android Compose view into a native Canvas; PixelCopy requires
            // a hardware window and is unavailable in this headless renderer.
            val bounds = compose.onNodeWithTag("screenshot").fetchSemanticsNode().boundsInRoot
            val bitmap = Bitmap.createBitmap(scene.width, scene.height, Bitmap.Config.ARGB_8888)
            compose.runOnIdle {
                val canvas = Canvas(bitmap)
                canvas.translate(-bounds.left, -bounds.top)
                renderedView.draw(canvas)
            }
            File(output, "blackjack-${scene.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun splitSelectorShowsTheRequestedHandAndFollowsTheTurn() {
        val split = PuppyBlackjackEngine.split(round(7, 4, 20, 8, 1, 2, 3)).state
        val current = mutableStateOf(split)
        var hits = 0
        compose.setContent {
            MaterialTheme { PuppyBlackjackGameContent(current.value, null, 900, true, false, "split",
                onHit = { hits++ }) }
        }
        val nextCard = PuppyBlackjackCard(split.hands[1].cards.first()).label
        compose.onNodeWithContentDescription(nextCard).assertDoesNotExist()
        compose.onNodeWithText("Hand 2").performClick()
        compose.onNodeWithContentDescription(nextCard).assertExists()
        compose.onNodeWithText("🐾 HIT").performClick()
        compose.runOnIdle {
            assertEquals("Dispatch exactly once while restoring the active hand", 1, hits)
        }
        compose.onNodeWithText("Hand 1").assertIsSelected()
        compose.onNodeWithContentDescription(nextCard).assertDoesNotExist()
        compose.runOnIdle { current.value = split }
        compose.onNodeWithText("Hand 1").performClick()
        compose.runOnIdle { current.value = PuppyBlackjackEngine.stand(split).state }
        compose.onNodeWithContentDescription(nextCard).assertExists()
    }

    @Test fun nativeActionsRespectEligibilityAndClickExactlyOnce() {
        val state = round(5, 12, 18, 2)
        var hits = 0
        compose.setContent {
            MaterialTheme {
                BlackjackActionPanel(state, 99, false, { hits++ }, {}, {}, {})
            }
        }
        compose.onNodeWithText("⇄ SPLIT").assertIsNotEnabled()
        compose.onNodeWithText("2× DOUBLE").assertIsNotEnabled()
        compose.onNodeWithText("🐾 HIT").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, hits) }
    }

    @Test fun rulesStartCollapsedAndExpandOnRequest() {
        compose.setContent {
            MaterialTheme { PuppyBlackjackGameContent(null, null, 900, false, false, "empty") }
        }
        compose.onNodeWithText("Published Blackjack rules").assertDoesNotExist()
        compose.onNodeWithText("Rules & help ▾").performScrollTo().performClick()
        compose.onNodeWithText("Published Blackjack rules").assertExists()
    }
}
