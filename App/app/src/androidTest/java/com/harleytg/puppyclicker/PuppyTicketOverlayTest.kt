package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PuppyTicketOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun overlayDoesNotMoveUnderlyingContentAndUsesCompactCopy() {
        val visible = mutableStateOf(false)
        compose.setContent {
            Box {
                Column {
                    Box(Modifier.width(200.dp).height(40.dp).testTag("wallet"))
                    Box(Modifier.width(200.dp).height(100.dp).testTag("play-card"))
                }
                V6TicketDropOverlay(
                    visible = visible.value,
                    rarity = TicketRarity.COMMON,
                    modifier = Modifier.testTag("ticket-overlay")
                )
            }
        }

        val before = compose.onNodeWithTag("play-card").getUnclippedBoundsInRoot()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("play-card").getUnclippedBoundsInRoot()

        assertEquals(before, after)
        compose.onNodeWithTag("ticket-overlay").assertIsDisplayed()
        compose.onNodeWithText("Common Ticket +1").assertIsDisplayed()
        compose.onNodeWithText("Ticket Upgrades").assertIsDisplayed()
    }
}
