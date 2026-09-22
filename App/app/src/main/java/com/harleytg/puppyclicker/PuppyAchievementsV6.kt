package com.harleytg.puppyclicker

data class PuppyAchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val target: Long,
    val rewardDescription: String,
    val progress: (V6GameState) -> Long
)

data class PuppyAchievementStatus(
    val definition: PuppyAchievementDefinition,
    val progress: Long,
    val completed: Boolean,
    val xpRewarded: Boolean
)

internal object PuppyAchievementsV6 {
    private const val REWARD = "+50 XP"

    val definitions = listOf(
        PuppyAchievementDefinition(
            "first_treat", "First Treat", "Earn your first lifetime Treat.", "🐾", 1, REWARD
        ) { it.lifetimeTreats },
        PuppyAchievementDefinition(
            "snack_stash", "Snack Stash", "Earn 100 lifetime Treats.", "🍪", 100, REWARD
        ) { it.lifetimeTreats },
        PuppyAchievementDefinition(
            "puppy_pro", "Puppy Pro", "Earn 1,000 lifetime Treats.", "🏆", 1_000, REWARD
        ) { it.lifetimeTreats },
        PuppyAchievementDefinition(
            "combo_hero", "Combo Hero", "Reach a 20 tap combo.", "🔥", 20, REWARD
        ) { it.bestCombo.toLong() },
        PuppyAchievementDefinition(
            "ticket_hunter", "Ticket Hunter", "Find your first Upgrade Ticket.", "🎟️", 1, REWARD
        ) { it.totalTicketsFound },
        PuppyAchievementDefinition(
            "big_taps", "Big Taps", "Reach 25 Treats per tap.", "💪", 25, REWARD
        ) { it.clickPower.toLong() },
        PuppyAchievementDefinition(
            "auto_pup", "Bone Collector", "Collect 50 Bones through active play.", "🦴", 50, REWARD
        ) { it.bones },
        PuppyAchievementDefinition(
            "happy_home", "Happy Home", "Keep all four care meters at 90 or higher.", "💖", 90, REWARD
        ) { minOf(it.happiness, it.fullness, it.energy, it.cleanliness).toLong() },
        PuppyAchievementDefinition(
            "level_ten", "Best Friend", "Reach player level 10.", "💜", 10, REWARD
        ) { it.level.toLong() }
    )

    fun status(
        definition: PuppyAchievementDefinition,
        state: V6GameState,
        rewardedIds: Set<String>
    ): PuppyAchievementStatus {
        val progress = definition.progress(state).coerceAtLeast(0L)
        return PuppyAchievementStatus(
            definition = definition,
            progress = progress,
            completed = progress >= definition.target,
            xpRewarded = definition.id in rewardedIds
        )
    }

    fun statuses(state: V6GameState, rewardedIds: Set<String>): List<PuppyAchievementStatus> =
        definitions.map { status(it, state, rewardedIds) }

    fun newlyCompleted(state: V6GameState, rewardedIds: Set<String>): List<PuppyAchievementDefinition> =
        definitions.filter { definition ->
            definition.id !in rewardedIds && definition.progress(state) >= definition.target
        }
}
