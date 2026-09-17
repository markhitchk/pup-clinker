package com.harleytg.puppyclicker

data class PuppyAfkSettlement(
    val settlementId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val creditedAwayMs: Long,
    val earnedTreats: Long
)

internal object PuppyAfkPolicy {
    const val DAY_MS = 24L * 60L * 60L * 1_000L
    const val MAX_AWAY_MS = 7L * DAY_MS
    const val TREATS_PER_DAY = 1_000L

    fun prepare(backgroundAtMs: Long, nowMs: Long): PuppyAfkSettlement? {
        if (backgroundAtMs <= 0L || nowMs <= backgroundAtMs) return null
        val rawAway = nowMs - backgroundAtMs
        if (rawAway <= 0L) return null
        val credited = rawAway.coerceAtMost(MAX_AWAY_MS)
        val effectiveEnd = backgroundAtMs + credited
        val fullDays = credited / DAY_MS
        val remainder = credited % DAY_MS
        val earned = safeAdd(
            safeMultiply(fullDays, TREATS_PER_DAY),
            safeMultiply(remainder, TREATS_PER_DAY) / DAY_MS
        ).coerceIn(0L, 7_000L)
        return PuppyAfkSettlement(
            settlementId = "afk:$backgroundAtMs:$effectiveEnd",
            startedAtMs = backgroundAtMs,
            endedAtMs = effectiveEnd,
            creditedAwayMs = credited,
            earnedTreats = earned
        )
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    private fun safeMultiply(a: Long, b: Long): Long = when {
        a <= 0L || b <= 0L -> 0L
        a > Long.MAX_VALUE / b -> Long.MAX_VALUE
        else -> a * b
    }
}
