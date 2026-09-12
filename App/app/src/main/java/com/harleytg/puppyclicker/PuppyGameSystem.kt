package com.harleytg.puppyclicker

import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Canonical gameplay model and economy contracts for the six-system Kotlin architecture.
 *
 * This file replaces the legacy V5 ViewModel dependency for shared upgrade definitions while
 * preserving the existing save keys and V6 gameplay API.
 */

internal object PuppySaveContract {
    const val PREFS_NAME = "puppy_clicker_save"
    const val KEY_AFK_BACKGROUND_AT = "afk_background_at_v6"
    const val KEY_AFK_PENDING = "afk_pending_treats_v6"
    const val KEY_AFK_AWAY_MS = "afk_away_ms_v6"
    const val KEY_AFK_CLAIM_READY = "afk_claim_ready_v6"
}

// ---- Shared upgrade economy ----
enum class V5UpgradeEffect { CLICK, AUTO }
enum class V5UpgradeType { COOKIE, TICKET }

data class V5Upgrade(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val effect: V5UpgradeEffect,
    val amount: Int,
    val type: V5UpgradeType,
    val baseCookieCost: Long,
    val rarity: TicketRarity? = null,
    val baseTicketCost: Int = 0
)

val V5_UPGRADES = listOf(
    V5Upgrade("better_treats", "Better Treats", "+1 treat per tap", "🦴", V5UpgradeEffect.CLICK, 1, V5UpgradeType.COOKIE, 35),
    V5Upgrade("chew_toy", "Chew Toy", "+1 treat every second", "🧸", V5UpgradeEffect.AUTO, 1, V5UpgradeType.COOKIE, 100),
    V5Upgrade("golden_bowl", "Golden Bowl", "+5 treats per tap", "🥣", V5UpgradeEffect.CLICK, 5, V5UpgradeType.COOKIE, 750),
    V5Upgrade("playmate", "Playmate", "+5 treats every second", "🐕", V5UpgradeEffect.AUTO, 5, V5UpgradeType.COOKIE, 1_500),

    V5Upgrade("lucky_collar", "Lucky Collar", "+3 treats per tap", "🍀", V5UpgradeEffect.CLICK, 3, V5UpgradeType.TICKET, 75, TicketRarity.COMMON, 2),
    V5Upgrade("training_whistle", "Training Whistle", "+5 treats every second", "📯", V5UpgradeEffect.AUTO, 5, V5UpgradeType.TICKET, 125, TicketRarity.UNCOMMON, 2),
    V5Upgrade("puppy_power", "Puppy Power", "+25 treats per tap", "⚡", V5UpgradeEffect.CLICK, 25, V5UpgradeType.TICKET, 250, TicketRarity.RARE, 2),
    V5Upgrade("dog_park_crew", "Dog Park Crew", "+25 treats every second", "🌳", V5UpgradeEffect.AUTO, 25, V5UpgradeType.TICKET, 350, TicketRarity.RARE, 3),
    V5Upgrade("treat_factory", "Treat Factory", "+100 treats every second", "🏭", V5UpgradeEffect.AUTO, 100, V5UpgradeType.TICKET, 600, TicketRarity.EPIC, 3),
    V5Upgrade("legendary_snacks", "Legendary Snacks", "+100 treats per tap", "✨", V5UpgradeEffect.CLICK, 100, V5UpgradeType.TICKET, 900, TicketRarity.LEGENDARY, 2)
)

fun v5CookieCost(upgrade: V5Upgrade, owned: Int): Long {
    val growth = if (upgrade.type == V5UpgradeType.COOKIE) 1.38 else 1.18
    return (upgrade.baseCookieCost * growth.pow(owned.coerceAtLeast(0).toDouble())).toLong()
        .coerceAtLeast(upgrade.baseCookieCost)
}

fun v5TicketCost(upgrade: V5Upgrade, owned: Int): Int {
    if (upgrade.type != V5UpgradeType.TICKET) return 0
    return upgrade.baseTicketCost + (owned.coerceAtLeast(0) / 3).coerceAtMost(2)
}

// ---- V6 gameplay model ----
enum class PrestigeSkill(
    val title: String,
    val emoji: String,
    val description: String,
    val maxLevel: Int
) {
    TAP_TRAINING(
        "Tap Training", "👆",
        "Each CLICK upgrade bought in the Shop gains +1 extra tap power per skill level.", 5
    ),
    AUTO_TRAINING(
        "Auto Training", "⏱️",
        "Each AUTO upgrade bought in the Shop gains +1 extra treat/sec per skill level.", 5
    ),
    SMART_SHOPPER(
        "Smart Shopper", "🛍️",
        "Shop treat prices are 5% cheaper per skill level.", 5
    ),
    TICKET_SENSE(
        "Ticket Sense", "🎟️",
        "At levels 2 and 4, matching-ticket upgrade requirements drop by 1 (minimum 1).", 5
    )
}

data class V6GameState(
    val puppyName: String = "Buddy",
    val puppyStyle: String = "classic",
    val unlockedPuppies: Set<String> = DEFAULT_V6_PUPPIES,
    val accessory: String = "None",

    val treats: Long = 0,
    val lifetimeTreats: Long = 0,
    val clickPower: Int = 1,
    val autoPerSecond: Int = 0,
    val upgrades: Map<String, Int> = V5_UPGRADES.associate { it.id to 0 },
    val totalShopPurchases: Long = 0,

    val ticketInventory: Map<TicketRarity, Int> = TicketRarity.entries.associateWith { 0 },
    val lastTicketDrop: TicketRarity? = null,
    val ticketDropSerial: Long = 0,
    val totalTicketsFound: Long = 0,

    val happiness: Int = 100,
    val fullness: Int = 100,
    val energy: Int = 100,
    val cleanliness: Int = 100,
    val bond: Int = 10,
    val careActions: Long = 0,

    val totalTaps: Long = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val lastTapMs: Long = 0,

    val parkActive: Boolean = false,
    val parkReadyAtMs: Long = 0,

    val lastDailyClaimDay: Long = Long.MIN_VALUE,
    val dailyStreak: Int = 0,
    val dailyDay: Long = LocalDate.now().toEpochDay(),
    val dailyTapStart: Long = 0,
    val dailyCareStart: Long = 0,
    val dailyShopStart: Long = 0,
    val claimedDailyTasks: Set<String> = emptySet(),

    val redeemedCodeIds: Set<String> = emptySet(),
    val hapticsEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val compactNumbers: Boolean = true,

    val pupEyeStrikes: Int = 0,
    val cooldownUntilMs: Long = 0,

    val prestigeCount: Int = 0,
    val skillPoints: Int = 0,
    val prestigeSkills: Map<PrestigeSkill, Int> = PrestigeSkill.entries.associateWith { 0 },
    val totalPrestigePointsEarned: Long = 0,

    val afkLastClaimed: Long = 0
) {
    val level: Int
        get() = 1 + sqrt(lifetimeTreats.coerceAtLeast(0).toDouble() / 100.0).toInt()

    val careScore: Int
        get() = ((happiness + fullness + energy + cleanliness) / 4).coerceIn(0, 100)

    val mood: String
        get() = when {
            careScore >= 90 && bond >= 70 -> "Best friend"
            careScore >= 85 -> "Thrilled"
            careScore >= 65 -> "Happy"
            careScore >= 40 -> "Okay"
            careScore >= 20 -> "Needs care"
            else -> "Very tired"
        }

    val ticketsOwned: Int
        get() = ticketInventory.values.sum()

    val dailyTaps: Long
        get() = (totalTaps - dailyTapStart).coerceAtLeast(0)
    val dailyCareActions: Long
        get() = (careActions - dailyCareStart).coerceAtLeast(0)
    val dailyShopPurchases: Long
        get() = (totalShopPurchases - dailyShopStart).coerceAtLeast(0)

    val prestigePointsAvailable: Int
        get() = when {
            lifetimeTreats < PRESTIGE_MIN_TREATS -> 0
            else -> (1 + ((lifetimeTreats - PRESTIGE_MIN_TREATS) / PRESTIGE_BONUS_STEP).toInt())
                .coerceIn(1, MAX_POINTS_PER_PRESTIGE)
        }

    val canPrestige: Boolean
        get() = prestigePointsAvailable > 0
}

data class V6RedeemOutcome(val success: Boolean, val message: String)

fun v6SkillCost(state: V6GameState, skill: PrestigeSkill): Int {
    val level = state.prestigeSkills[skill] ?: 0
    return 1 + level / 2
}

fun v6UpgradeTreatCost(state: V6GameState, upgrade: V5Upgrade): Long {
    val owned = state.upgrades[upgrade.id] ?: 0
    val base = v5CookieCost(upgrade, owned)
    val shopper = (state.prestigeSkills[PrestigeSkill.SMART_SHOPPER] ?: 0).coerceIn(0, 5)
    val percent = (100 - shopper * 5).coerceAtLeast(75)
    return ((base * percent) / 100L).coerceAtLeast(1L)
}

fun v6UpgradeTicketCost(state: V6GameState, upgrade: V5Upgrade): Int {
    val owned = state.upgrades[upgrade.id] ?: 0
    val base = v5TicketCost(upgrade, owned)
    val sense = (state.prestigeSkills[PrestigeSkill.TICKET_SENSE] ?: 0).coerceIn(0, 5)
    val reduction = (if (sense >= 2) 1 else 0) + (if (sense >= 4) 1 else 0)
    return (base - reduction).coerceAtLeast(1)
}

private val DEFAULT_V6_PUPPIES = setOf("classic", "golden", "poodle", "spotty")
private const val PRESTIGE_MIN_TREATS = 50_000L
private const val PRESTIGE_BONUS_STEP = 100_000L
private const val MAX_POINTS_PER_PRESTIGE = 8
