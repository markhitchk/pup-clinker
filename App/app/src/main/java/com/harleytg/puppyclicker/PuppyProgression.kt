package com.harleytg.puppyclicker

import kotlin.math.sqrt

enum class PuppyBondMilestone(val minimum: Int, val label: String) {
    NEW_FRIEND(0, "New Friend"),
    BUDDY(25, "Buddy"),
    CLOSE_PAL(50, "Close Pal"),
    BEST_FRIEND(70, "Best Friend"),
    FOREVER_FRIEND(90, "Forever Friend")
}

enum class PuppyXpEvent(val amount: Long) {
    MANUAL_TAP(1),
    CARE_ACTION(5),
    DAILY_TASK(25),
    ACHIEVEMENT(50),
    NEW_PUPPY(100),
    EVENT_REWARD(50)
}

internal object PuppyProgression {
    const val DEFAULT_BOND = 10

    fun bondFor(values: Map<String, Int>, puppyId: String): Int =
        values[puppyId]?.coerceIn(0, 100) ?: DEFAULT_BOND

    fun withBondDelta(
        values: Map<String, Int>,
        puppyId: String,
        delta: Int
    ): Map<String, Int> {
        if (puppyId.isBlank()) return values
        val current = bondFor(values, puppyId)
        val next = when {
            delta > 0 && current > 100 - delta.coerceAtMost(100) -> 100
            delta < 0 && current < -delta.coerceAtLeast(-100) -> 0
            else -> (current + delta).coerceIn(0, 100)
        }
        return values + (puppyId to next)
    }

    fun milestoneFor(bond: Int): PuppyBondMilestone {
        val value = bond.coerceIn(0, 100)
        return PuppyBondMilestone.entries.last { value >= it.minimum }
    }

    const val MAX_PLAYER_LEVEL = 100
    const val MAX_PLAYER_XP = 250_000L
    private const val XP_SQUARE_STEP = 25.0

    // Preserve gameplay balances: only player XP is bounded by the new curve.
    fun seedXpFromLifetimeTreats(lifetimeTreats: Long): Long =
        lifetimeTreats.coerceIn(0L, MAX_PLAYER_XP)

    fun levelForXp(xp: Long): Int =
        (1 + sqrt(xp.coerceIn(0L, MAX_PLAYER_XP).toDouble() / XP_SQUARE_STEP).toInt())
            .coerceIn(1, MAX_PLAYER_LEVEL)

    fun addXp(current: Long, event: PuppyXpEvent): Long =
        (current.coerceIn(0L, MAX_PLAYER_XP) + event.amount.coerceAtLeast(0L))
            .coerceAtMost(MAX_PLAYER_XP)
}

data class PuppyXpSettlementResult(
    val xp: Long,
    val settlements: Set<String>,
    val applied: Boolean
)

internal object PuppyProgressionSettlement {
    fun apply(
        currentXp: Long,
        settlements: Set<String>,
        event: PuppyXpEvent,
        settlementId: String?
    ): PuppyXpSettlementResult {
        val stableId = settlementId?.trim()?.takeIf { it.isNotEmpty() }
        if (stableId != null && stableId in settlements) {
            return PuppyXpSettlementResult(currentXp.coerceAtLeast(0L), settlements, false)
        }
        return PuppyXpSettlementResult(
            xp = PuppyProgression.addXp(currentXp, event),
            settlements = if (stableId == null) settlements else settlements + stableId,
            applied = true
        )
    }
}
