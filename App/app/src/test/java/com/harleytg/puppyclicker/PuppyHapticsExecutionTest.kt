package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Test

class PuppyHapticsExecutionTest {
    @Test
    fun semanticSuccessStopsBeforeDirectFallback() {
        assertEquals(
            PuppyHapticResult.SEMANTIC,
            PuppyHapticExecution.resolve(
                route = PuppyHapticRoute.SEMANTIC_THEN_DIRECT,
                semanticSucceeded = true,
                directSucceeded = false
            )
        )
    }

    @Test
    fun semanticFailureFallsBackToDirectVibration() {
        assertEquals(
            PuppyHapticResult.DIRECT,
            PuppyHapticExecution.resolve(
                route = PuppyHapticRoute.SEMANTIC_THEN_DIRECT,
                semanticSucceeded = false,
                directSucceeded = true
            )
        )
    }

    @Test
    fun directRouteReportsUnavailableWhenDeviceCannotVibrate() {
        assertEquals(
            PuppyHapticResult.UNAVAILABLE,
            PuppyHapticExecution.resolve(
                route = PuppyHapticRoute.DIRECT,
                semanticSucceeded = false,
                directSucceeded = false
            )
        )
    }

    @Test
    fun noneRouteReportsDisabled() {
        assertEquals(
            PuppyHapticResult.DISABLED,
            PuppyHapticExecution.resolve(
                route = PuppyHapticRoute.NONE,
                semanticSucceeded = false,
                directSucceeded = false
            )
        )
    }
}
