package com.harleytg.puppyclicker

data class PuppyCodeHistoryEntry(
    val redemptionId: String,
    val redeemedAtMs: Long,
    val summary: String,
    val typeSummary: String,
    val claimed: Boolean = true,
    val artworkId: String? = null
)

enum class PuppyRewardGrantFailure {
    UNSUPPORTED_REWARD,
    INVALID_PUPPY,
    INVENTORY_OVERFLOW
}

data class PuppyRewardGrantResult(
    val success: Boolean,
    val state: V6GameState,
    val failure: PuppyRewardGrantFailure? = null,
    val summary: String = ""
)

object RewardGrantEngine {
    private const val MAX_TICKETS_PER_RARITY = 9_999

    /**
     * Computes a complete next V6 state without mutating the input. Any reward that
     * this app cannot safely persist rejects the whole bundle and returns [before].
     */
    fun applyTo(before: V6GameState, rewards: List<PuppyCodeReward>): PuppyRewardGrantResult {
        if (rewards.isEmpty()) {
            return PuppyRewardGrantResult(false, before, PuppyRewardGrantFailure.UNSUPPORTED_REWARD)
        }

        // Preflight every element before calculating any state changes.
        for (reward in rewards) {
            when (reward) {
                is PuppyCodeReward.Treats -> if (reward.amount <= 0L) {
                    return PuppyRewardGrantResult(false, before, PuppyRewardGrantFailure.UNSUPPORTED_REWARD)
                }
                is PuppyCodeReward.Puppy -> if (reward.puppyId !in V6_PUPPY_IDS || SeasonalPuppyEvents.isSeasonal(reward.puppyId)) {
                    return PuppyRewardGrantResult(false, before, PuppyRewardGrantFailure.INVALID_PUPPY)
                }
                is PuppyCodeReward.UpgradeTickets -> {
                    if (reward.amount !in 1..100) {
                        return PuppyRewardGrantResult(false, before, PuppyRewardGrantFailure.INVENTORY_OVERFLOW)
                    }
                    val current = before.ticketInventory[reward.rarity] ?: 0
                    if (current > MAX_TICKETS_PER_RARITY - reward.amount) {
                        return PuppyRewardGrantResult(false, before, PuppyRewardGrantFailure.INVENTORY_OVERFLOW)
                    }
                }
                // These names are recognized by schema 2, but V6 has no durable save
                // fields for them yet. Fail closed rather than grant a partial bundle.
                is PuppyCodeReward.Cosmetic,
                is PuppyCodeReward.Badge,
                is PuppyCodeReward.Boost,
                is PuppyCodeReward.Unknown -> {
                    return PuppyRewardGrantResult(false, before, PuppyRewardGrantFailure.UNSUPPORTED_REWARD)
                }
            }
        }

        var treats = before.treats
        var lifetimeTreats = before.lifetimeTreats
        var unlockedPuppies = before.unlockedPuppies
        var puppyStyle = before.puppyStyle
        val inventory = before.ticketInventory.toMutableMap()

        for (reward in rewards) {
            when (reward) {
                is PuppyCodeReward.Treats -> {
                    treats = safeAdd(treats, reward.amount)
                    lifetimeTreats = safeAdd(lifetimeTreats, reward.amount)
                }
                is PuppyCodeReward.Puppy -> {
                    unlockedPuppies = unlockedPuppies + reward.puppyId
                    puppyStyle = reward.puppyId
                }
                is PuppyCodeReward.UpgradeTickets -> {
                    inventory[reward.rarity] = (inventory[reward.rarity] ?: 0) + reward.amount
                }
                else -> error("Reward preflight allowed an unsupported type")
            }
        }

        val next = before.copy(
            treats = treats,
            lifetimeTreats = lifetimeTreats,
            unlockedPuppies = unlockedPuppies,
            puppyStyle = puppyStyle,
            ticketInventory = inventory.toMap()
        )
        return PuppyRewardGrantResult(true, next, summary = PuppyCodeHistory.summarizeRewards(rewards))
    }

    fun isImmediateCompactClaim(rewards: List<PuppyCodeReward>, flags: Set<String>): Boolean =
        rewards.size == 1 && rewards.single() is PuppyCodeReward.Treats && "SPECIAL_REVEAL" !in flags

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}

object PuppyCodeHistory {
    fun createEntry(definition: PuppyCodeDefinition, redeemedAtMs: Long): PuppyCodeHistoryEntry {
        val puppyId = definition.rewards.filterIsInstance<PuppyCodeReward.Puppy>().firstOrNull()?.puppyId
        return PuppyCodeHistoryEntry(
            redemptionId = definition.id,
            redeemedAtMs = redeemedAtMs,
            summary = summarizeRewards(definition.rewards),
            typeSummary = summarizeTypes(definition.rewards),
            artworkId = puppyId
        )
    }

    fun summarizeRewards(rewards: List<PuppyCodeReward>): String = rewards.joinToString(" · ") { reward ->
        when (reward) {
            is PuppyCodeReward.Treats -> "+${reward.amount} Treats"
            is PuppyCodeReward.Puppy -> "Puppy: ${displayPuppyName(reward.puppyId)}"
            is PuppyCodeReward.UpgradeTickets -> "+${reward.amount} ${reward.rarity.displayName} Ticket${if (reward.amount == 1) "" else "s"}"
            is PuppyCodeReward.Cosmetic -> "Cosmetic: ${reward.id}"
            is PuppyCodeReward.Badge -> "Badge: ${reward.id}"
            is PuppyCodeReward.Boost -> "Boost: ${reward.id}"
            is PuppyCodeReward.Unknown -> "Unsupported reward"
        }
    }

    private fun summarizeTypes(rewards: List<PuppyCodeReward>): String = rewards
        .map { reward ->
            when (reward) {
                is PuppyCodeReward.Treats -> "Treats"
                is PuppyCodeReward.Puppy -> "Puppy"
                is PuppyCodeReward.UpgradeTickets -> "Upgrade Tickets"
                is PuppyCodeReward.Cosmetic -> "Cosmetic"
                is PuppyCodeReward.Badge -> "Badge"
                is PuppyCodeReward.Boost -> "Boost"
                is PuppyCodeReward.Unknown -> "Unsupported"
            }
        }
        .distinct()
        .joinToString(" + ")

    private fun displayPuppyName(id: String): String =
        V6_PUPPY_STYLES.firstOrNull { it.id == id }?.name ?: id
}
