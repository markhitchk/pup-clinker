package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PuppyMonthlyRewardsSelectionTest {
    private val first = PuppyRewardGoal(
        id = "first_goal",
        metric = PuppyRewardMetric.TAPS,
        emoji = "🐾",
        title = "First Goal",
        description = "First test goal",
        target = 75L,
        rewardTreats = 300L
    )
    private val second = PuppyRewardGoal(
        id = "second_goal",
        metric = PuppyRewardMetric.CARE,
        emoji = "💖",
        title = "Second Goal",
        description = "Second test goal",
        target = 3L,
        rewardTreats = 450L
    )

    @Test
    fun nextUnclaimedGoalSkipsClaimedGoals() {
        val next = PuppyMonthlyRewards.nextUnclaimedGoal(
            goals = listOf(first, second),
            claimedIds = setOf(first.id)
        )

        assertEquals(second.id, next?.id)
    }

    @Test
    fun nextUnclaimedGoalReturnsNullWhenAllGoalsAreClaimed() {
        val next = PuppyMonthlyRewards.nextUnclaimedGoal(
            goals = listOf(first, second),
            claimedIds = setOf(first.id, second.id)
        )

        assertNull(next)
    }

    @Test
    fun completedButUnclaimedGoalRemainsEligibleUntilClaimed() {
        val next = PuppyMonthlyRewards.nextUnclaimedGoal(
            goals = listOf(first, second),
            claimedIds = emptySet()
        )

        assertEquals(first.id, next?.id)
    }
}
