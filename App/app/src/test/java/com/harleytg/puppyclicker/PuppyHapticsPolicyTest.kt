package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class PuppyHapticsPolicyTest {
    @Test
    fun normalUiEventsPreferSemanticFeedbackWithDirectFallback() {
        val events = listOf(
            PuppyHapticEvent.TAP,
            PuppyHapticEvent.SELECTION,
            PuppyHapticEvent.NAVIGATION,
            PuppyHapticEvent.TOGGLE
        )

        events.forEach { event ->
            val plan = PuppyHapticPolicy.plan(event, enabled = true)
            assertEquals(PuppyHapticRoute.SEMANTIC_THEN_DIRECT, plan.route)
            assertEquals(PuppyHapticStrength.LIGHT, plan.strength)
        }
    }

    @Test
    fun successAndErrorKeepSemanticFeedbackButUseStrongerFallbacks() {
        assertEquals(
            PuppyHapticPlan(PuppyHapticRoute.SEMANTIC_THEN_DIRECT, PuppyHapticStrength.MEDIUM),
            PuppyHapticPolicy.plan(PuppyHapticEvent.SUCCESS, enabled = true)
        )
        assertEquals(
            PuppyHapticPlan(PuppyHapticRoute.SEMANTIC_THEN_DIRECT, PuppyHapticStrength.STRONG),
            PuppyHapticPolicy.plan(PuppyHapticEvent.ERROR, enabled = true)
        )
    }

    @Test
    fun specialEventsUseDirectVibration() {
        val events = listOf(
            PuppyHapticEvent.REWARD,
            PuppyHapticEvent.TICKET_DROP,
            PuppyHapticEvent.DANGER_CONFIRM,
            PuppyHapticEvent.TEST
        )

        events.forEach { event ->
            val plan = PuppyHapticPolicy.plan(event, enabled = true)
            assertEquals(PuppyHapticRoute.DIRECT, plan.route)
            assertEquals(PuppyHapticStrength.STRONG, plan.strength)
        }
    }

    @Test
    fun disabledHapticsNeverEmitFeedback() {
        PuppyHapticEvent.entries.forEach { event ->
            assertEquals(
                PuppyHapticPlan(PuppyHapticRoute.NONE, PuppyHapticStrength.LIGHT),
                PuppyHapticPolicy.plan(event, enabled = false)
            )
        }
    }
}
