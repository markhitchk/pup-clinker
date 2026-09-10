package com.harleytg.puppyclicker

object PuppyCodeAntiAbuse {
    private const val INVALID_WINDOW_MS = 2L * 60L * 1_000L
    private const val ESCALATION_WINDOW_MS = 10L * 60L * 1_000L
    private const val RAPID_WINDOW_MS = 10_000L
    private const val FIRST_COOLDOWN_MS = 30_000L
    private const val REPEAT_COOLDOWN_MS = 60_000L
    private const val PUPEYE_COOLDOWN_MS = 60_000L
    private const val MAX_COOLDOWN_MS = 5L * 60L * 1_000L

    data class State(
        val invalidAttemptTimes: List<Long> = emptyList(),
        val submissionTimes: List<Long> = emptyList(),
        val lastPenaltyAtMs: Long = 0L,
        val cooldownUntilMs: Long = 0L,
        val pupEyeEscalated: Boolean = false
    )

    fun recordSubmission(state: State, nowMs: Long): State {
        val recent = (state.submissionTimes + nowMs).filter { it >= nowMs - RAPID_WINDOW_MS }
        if (recent.size < 5) return state.copy(submissionTimes = recent)

        val requestedUntil = safeAdd(nowMs, PUPEYE_COOLDOWN_MS)
        return state.copy(
            submissionTimes = recent,
            cooldownUntilMs = maxOf(state.cooldownUntilMs, requestedUntil).coerceAtMost(safeAdd(nowMs, MAX_COOLDOWN_MS)),
            lastPenaltyAtMs = nowMs,
            pupEyeEscalated = true
        )
    }

    fun recordInvalid(state: State, nowMs: Long): State {
        val recent = (state.invalidAttemptTimes + nowMs).filter { it >= nowMs - INVALID_WINDOW_MS }
        if (recent.size < 5) return state.copy(invalidAttemptTimes = recent)

        val isRepeatedBurst = state.lastPenaltyAtMs > 0L && nowMs - state.lastPenaltyAtMs in 0..ESCALATION_WINDOW_MS
        val penalty = if (isRepeatedBurst) REPEAT_COOLDOWN_MS else FIRST_COOLDOWN_MS
        return state.copy(
            invalidAttemptTimes = recent,
            lastPenaltyAtMs = nowMs,
            cooldownUntilMs = maxOf(state.cooldownUntilMs, safeAdd(nowMs, penalty))
                .coerceAtMost(safeAdd(nowMs, MAX_COOLDOWN_MS))
        )
    }

    fun recordSuccess(state: State): State = state.copy(
        invalidAttemptTimes = emptyList(),
        submissionTimes = emptyList(),
        pupEyeEscalated = false
    )

    fun remainingCooldownMs(state: State, nowMs: Long): Long =
        (state.cooldownUntilMs - nowMs).coerceAtLeast(0L)

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}
