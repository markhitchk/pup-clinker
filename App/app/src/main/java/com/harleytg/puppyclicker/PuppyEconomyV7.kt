package com.harleytg.puppyclicker

import kotlin.math.pow

/**
 * Economy V7 constants and deterministic currency math.
 *
 * Treats remain progression currency, Bones reward active play, Pup Coins are the normal Shop
 * currency, and Casino Chips are isolated wagering currency. Casino Chips never convert back.
 */
internal object PuppyEconomyV7 {
    const val TREAT_TO_CHIP_RATE = 10L
    const val ACTIVE_BONUS_INTERVAL = 10L
    const val BONE_TAP_INTERVAL = 20L
    const val PUP_COIN_TAP_INTERVAL = 250L
    const val TICKET_DROP_DENOMINATOR = 500

    const val DAILY_BONES = 2L
    const val DAILY_PUP_COINS = 3L
    const val DAILY_TASK_BONES = 1L
    const val DAILY_TASK_PUP_COINS = 1L

    val ACCESSORY_PRICES: Map<String, Long> = linkedMapOf(
        "Bandana" to 150L,
        "Bow" to 250L,
        "Crown" to 1_000L
    )

    val TICKET_BASE_PRICES: Map<TicketRarity, Long> = linkedMapOf(
        TicketRarity.COMMON to 250L,
        TicketRarity.UNCOMMON to 750L,
        TicketRarity.RARE to 2_500L,
        TicketRarity.EPIC to 7_500L,
        TicketRarity.LEGENDARY to 25_000L
    )

    val CHIP_PURCHASE_PRESETS: List<Long> = listOf(500L, 2_500L, 10_000L)

    fun canConvertTreatsToChips(treats: Long): Boolean =
        treats > 0L && treats % TREAT_TO_CHIP_RATE == 0L

    fun chipsForTreats(treats: Long): Long =
        if (canConvertTreatsToChips(treats)) treats / TREAT_TO_CHIP_RATE else 0L

    fun upgradeTreatCost(upgrade: V5Upgrade, owned: Int): Long {
        val growth = if (upgrade.type == V5UpgradeType.COOKIE) 1.55 else 1.35
        val exponent = owned.coerceAtLeast(0).toDouble()
        val raw = upgrade.baseCookieCost.toDouble() * growth.pow(exponent)
        if (!raw.isFinite() || raw >= Long.MAX_VALUE.toDouble()) return Long.MAX_VALUE
        return raw.toLong().coerceAtLeast(upgrade.baseCookieCost)
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

    fun accessoryPrice(accessory: String): Long? = ACCESSORY_PRICES[accessory]

    /**
     * Each purchase raises the next price by exactly 25% of the original base price.
     * Integer division is deterministic and the multiplier is capped at 6x.
     */
    fun ticketShopPrice(rarity: TicketRarity, purchased: Int): Long {
        val base = TICKET_BASE_PRICES.getValue(rarity)
        val quarterSteps = (4L + purchased.coerceAtLeast(0).toLong()).coerceAtMost(24L)
        return safeMultiply(base, quarterSteps) / 4L
    }

    private fun safeMultiply(a: Long, b: Long): Long =
        if (a <= 0L || b <= 0L) 0L
        else if (a > Long.MAX_VALUE / b) Long.MAX_VALUE
        else a * b
}
