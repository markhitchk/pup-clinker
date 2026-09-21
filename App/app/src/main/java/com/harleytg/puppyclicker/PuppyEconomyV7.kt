package com.harleytg.puppyclicker

/**
 * Puppy Clicker economy policy introduced with the V7 local-save economy.
 *
 * Currency boundaries:
 * - Treats drive normal progression.
 * - Bones reward active play and are required by upgrades.
 * - Pup Coins are the permanent normal-Shop currency.
 * - Casino Chips are isolated wagering currency and can only be acquired from Treats.
 *
 * All pricing and conversion math is integer-only so save/replay behavior is deterministic.
 */
enum class PuppyRewardCurrency(val displayName: String, val emoji: String) {
    TREATS("Treats", "🍪"),
    BONES("Bones", "🦴"),
    PUP_COINS("Pup Coins", "🪙"),
    CASINO_CHIPS("Casino Chips", "🐾")
}

internal object PuppyEconomyV7 {
    const val ACTIVE_BONUS_TAP_INTERVAL = 10L
    const val BONE_TAP_INTERVAL = 20L
    const val PUP_COIN_TAP_INTERVAL = 250L
    const val TICKET_DROP_DENOMINATOR = 500

    const val DAILY_BONES = 2L
    const val DAILY_PUP_COINS = 3L
    const val DAILY_TASK_BONES = 1L
    const val DAILY_TASK_PUP_COINS = 1L
    const val CARE_ACTION_BONES = 1L

    const val TREATS_PER_CASINO_CHIP = 10L

    val accessoryPrices: Map<String, Long> = linkedMapOf(
        "Bandana" to 150L,
        "Bow" to 250L,
        "Crown" to 1_000L
    )

    val ticketBasePrices: Map<TicketRarity, Long> = linkedMapOf(
        TicketRarity.COMMON to 250L,
        TicketRarity.UNCOMMON to 750L,
        TicketRarity.RARE to 2_500L,
        TicketRarity.EPIC to 7_500L,
        TicketRarity.LEGENDARY to 25_000L
    )

    fun activeBonus(
        upgrades: Map<String, Int>,
        activeTrainingLevel: Int
    ): Int {
        val skill = activeTrainingLevel.coerceIn(0, PrestigeSkill.AUTO_TRAINING.maxLevel)
        val total = V5_UPGRADES.asSequence()
            .filter { it.effect == V5UpgradeEffect.AUTO }
            .sumOf { upgrade ->
                (upgrade.amount.toLong() + skill.toLong()) *
                    (upgrades[upgrade.id] ?: 0).coerceAtLeast(0).toLong()
            }
        return total.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    fun upgradeTreatCost(state: V6GameState, upgrade: V5Upgrade): Long {
        val owned = (state.upgrades[upgrade.id] ?: 0).coerceAtLeast(0)
        val growthNumerator = if (upgrade.type == V5UpgradeType.COOKIE) 155L else 135L
        val grown = growCost(
            base = upgrade.baseCookieCost.coerceAtLeast(1L),
            owned = owned,
            numerator = growthNumerator,
            denominator = 100L
        )
        val shopper = (state.prestigeSkills[PrestigeSkill.SMART_SHOPPER] ?: 0).coerceIn(0, 5)
        val percent = (100L - shopper * 5L).coerceAtLeast(75L)
        return mulDivFloor(grown, percent, 100L).coerceAtLeast(1L)
    }

    fun upgradeBoneCost(upgrade: V5Upgrade): Long {
        val base = when {
            upgrade.amount >= 100 -> 12L
            upgrade.amount >= 25 -> 7L
            upgrade.amount >= 5 -> 3L
            else -> 1L
        }
        return if (upgrade.type == V5UpgradeType.TICKET) base * 2L else base
    }

    fun ticketShopCost(rarity: TicketRarity, purchased: Int): Long {
        val base = ticketBasePrices.getValue(rarity)
        // Linear +25% of the original base after each purchase:
        // 250, 312, 375, 437, ... capped at 6x base.
        val quarterSteps = (4 + purchased.coerceAtLeast(0)).coerceAtMost(24)
        return (base * quarterSteps.toLong()) / 4L
    }

    fun accessoryPrice(accessory: String): Long? = accessoryPrices[accessory]

    fun chipsForTreats(treats: Long): Long? {
        if (treats <= 0L || treats % TREATS_PER_CASINO_CHIP != 0L) return null
        return treats / TREATS_PER_CASINO_CHIP
    }

    fun canConvertTreatsToChips(balanceTreats: Long, spendTreats: Long): Boolean =
        chipsForTreats(spendTreats) != null && balanceTreats >= spendTreats

    fun shouldDropTicket(roll: Int): Boolean =
        roll in 0 until TICKET_DROP_DENOMINATOR && roll == 0

    /**
     * Rarity roll after a successful ~1/500 ticket drop.
     * Input is a 0..9999 basis-point-style roll.
     */
    fun ticketRarityForRoll(roll: Int): TicketRarity {
        val value = roll.coerceIn(0, 9_999)
        return when {
            value < 7_800 -> TicketRarity.COMMON
            value < 9_350 -> TicketRarity.UNCOMMON
            value < 9_850 -> TicketRarity.RARE
            value < 9_980 -> TicketRarity.EPIC
            else -> TicketRarity.LEGENDARY
        }
    }

    private fun growCost(
        base: Long,
        owned: Int,
        numerator: Long,
        denominator: Long
    ): Long {
        var cost = base.coerceAtLeast(1L)
        repeat(owned.coerceAtLeast(0)) {
            if (cost > (Long.MAX_VALUE - (denominator - 1L)) / numerator) {
                return Long.MAX_VALUE
            }
            cost = ((cost * numerator) + denominator - 1L) / denominator
        }
        return cost
    }

    private fun mulDivFloor(value: Long, multiplier: Long, divisor: Long): Long {
        if (value <= 0L || multiplier <= 0L || divisor <= 0L) return 0L
        if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE
        return (value * multiplier) / divisor
    }
}
