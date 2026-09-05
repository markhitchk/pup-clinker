package com.harleytg.puppyclicker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

data class V5GameState(
    val puppyName: String = "Buddy",
    val puppyStyle: String = "classic",
    val unlockedPuppies: Set<String> = setOf("classic", "golden", "poodle", "spotty"),
    val accessory: String = "None",

    val treats: Long = 0,
    val lifetimeTreats: Long = 0,
    val clickPower: Int = 1,
    val autoPerSecond: Int = 0,
    val upgrades: Map<String, Int> = emptyMap(),
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
    val offlineEarned: Long = 0
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
}

data class V5RedeemOutcome(val success: Boolean, val message: String)

class PuppyClickerV5ViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val recentTapTimes = ArrayDeque<Long>()
    private var suspicionHits = 0
    private var suspicionWindowStartedMs = 0L

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<V5GameState> = _state.asStateFlow()

    init {
        rollDailyDayIfNeeded()
        viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000)
                seconds++
                val now = System.currentTimeMillis()
                val current = _state.value

                if (current.autoPerSecond > 0) {
                    val careBonus = if (current.careScore >= 85) current.autoPerSecond / 10 else 0
                    addTreats((current.autoPerSecond + careBonus).toLong())
                }

                if (_state.value.combo > 0 && now - _state.value.lastTapMs > COMBO_TIMEOUT_MS) {
                    _state.update { it.copy(combo = 0) }
                }
                if (seconds % 120 == 0) decayNeeds()
                if (seconds % 5 == 0) saveState()
            }
        }
    }

    fun tapPuppy() {
        val now = System.currentTimeMillis()
        val current = _state.value
        if (now < current.cooldownUntilMs) return

        recentTapTimes.addLast(now)
        while (recentTapTimes.isNotEmpty() && recentTapTimes.first < now - FAIR_PLAY_HISTORY_MS) {
            recentTapTimes.removeFirst()
        }

        val suspiciousThisTap = looksAutomated(now)
        if (suspiciousThisTap) {
            if (suspicionWindowStartedMs == 0L || now - suspicionWindowStartedMs > SUSPICION_CONFIRM_WINDOW_MS) {
                suspicionWindowStartedMs = now
                suspicionHits = 1
            } else {
                suspicionHits++
            }
            if (suspicionHits >= REQUIRED_SUSPICION_HITS) {
                recentTapTimes.clear()
                suspicionHits = 0
                suspicionWindowStartedMs = 0L
                val strike = current.pupEyeStrikes + 1
                val cooldown = (BASE_FAIR_PLAY_COOLDOWN_MS + (strike - 1) * 2_000L)
                    .coerceAtMost(MAX_FAIR_PLAY_COOLDOWN_MS)
                _state.value = current.copy(
                    pupEyeStrikes = strike,
                    cooldownUntilMs = now + cooldown,
                    combo = 0
                )
                saveState()
                return
            }
        } else if (suspicionWindowStartedMs != 0L && now - suspicionWindowStartedMs > SUSPICION_CONFIRM_WINDOW_MS) {
            suspicionHits = 0
            suspicionWindowStartedMs = 0L
        }

        val combo = if (now - current.lastTapMs <= COMBO_CHAIN_MS) {
            (current.combo + 1).coerceAtMost(50)
        } else {
            1
        }
        val nextTaps = safeAdd(current.totalTaps, 1)

        // Fair-play taps now get a true 50/50 ticket roll. The rarity table is
        // deliberately weighted toward Common and Uncommon so entry upgrades
        // are attainable, while the top tiers remain meaningfully rare.
        val ticketDrop = if (!suspiciousThisTap && Random.nextBoolean()) rollTicketRarity() else null
        val inventory = if (ticketDrop == null) {
            current.ticketInventory
        } else {
            current.ticketInventory.toMutableMap().apply {
                this[ticketDrop] = ((this[ticketDrop] ?: 0) + 1).coerceAtMost(MAX_TICKETS_PER_RARITY)
            }
        }

        _state.value = current.copy(
            treats = safeAdd(current.treats, current.clickPower.toLong()),
            lifetimeTreats = safeAdd(current.lifetimeTreats, current.clickPower.toLong()),
            totalTaps = nextTaps,
            combo = combo,
            bestCombo = maxOf(current.bestCombo, combo),
            lastTapMs = now,
            energy = if (nextTaps % 12L == 0L) (current.energy - 1).coerceAtLeast(0) else current.energy,
            happiness = if (nextTaps % 15L == 0L) (current.happiness + 1).coerceAtMost(100) else current.happiness,
            ticketInventory = inventory,
            lastTicketDrop = ticketDrop ?: current.lastTicketDrop,
            ticketDropSerial = if (ticketDrop != null) current.ticketDropSerial + 1 else current.ticketDropSerial,
            totalTicketsFound = if (ticketDrop != null) safeAdd(current.totalTicketsFound, 1) else current.totalTicketsFound,
            offlineEarned = 0
        )
        if (ticketDrop != null) saveState()
    }

    fun buyCookieUpgrade(upgrade: V5Upgrade) {
        if (upgrade.type != V5UpgradeType.COOKIE) return
        val current = _state.value
        val owned = current.upgrades[upgrade.id] ?: 0
        val cost = v5CookieCost(upgrade, owned)
        if (current.treats < cost) return
        applyUpgradePurchase(current, upgrade, owned, current.treats - cost, current.ticketInventory)
    }

    fun buyTicketUpgrade(upgrade: V5Upgrade) {
        if (upgrade.type != V5UpgradeType.TICKET || upgrade.rarity == null) return
        val current = _state.value
        val owned = current.upgrades[upgrade.id] ?: 0
        val ticketCost = v5TicketCost(upgrade, owned)
        val ownedTickets = current.ticketInventory[upgrade.rarity] ?: 0
        val treatFee = v5CookieCost(upgrade, owned)
        if (ownedTickets < ticketCost || current.treats < treatFee) return
        val inventory = current.ticketInventory.toMutableMap().apply {
            this[upgrade.rarity] = (ownedTickets - ticketCost).coerceAtLeast(0)
        }
        applyUpgradePurchase(current, upgrade, owned, current.treats - treatFee, inventory)
    }

    private fun applyUpgradePurchase(
        current: V5GameState,
        upgrade: V5Upgrade,
        owned: Int,
        nextTreats: Long,
        inventory: Map<TicketRarity, Int>
    ) {
        val upgrades = current.upgrades.toMutableMap().apply { this[upgrade.id] = owned + 1 }
        _state.value = current.copy(
            treats = nextTreats,
            upgrades = upgrades,
            ticketInventory = inventory,
            clickPower = current.clickPower + if (upgrade.effect == V5UpgradeEffect.CLICK) upgrade.amount else 0,
            autoPerSecond = current.autoPerSecond + if (upgrade.effect == V5UpgradeEffect.AUTO) upgrade.amount else 0,
            totalShopPurchases = safeAdd(current.totalShopPurchases, 1),
            happiness = (current.happiness + 2).coerceAtMost(100),
            offlineEarned = 0
        )
        saveState()
    }

    fun feedPuppy() {
        val s = _state.value
        if (s.treats < FEED_COST || s.fullness >= 100) return
        _state.value = s.copy(
            treats = s.treats - FEED_COST,
            fullness = (s.fullness + 25).coerceAtMost(100),
            happiness = (s.happiness + 5).coerceAtMost(100),
            bond = (s.bond + 1).coerceAtMost(100),
            cleanliness = (s.cleanliness - 1).coerceAtLeast(0),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun playWithPuppy() {
        val s = _state.value
        if (s.energy < 12) return
        _state.value = s.copy(
            happiness = (s.happiness + 20).coerceAtMost(100),
            fullness = (s.fullness - 4).coerceAtLeast(0),
            energy = (s.energy - 12).coerceAtLeast(0),
            cleanliness = (s.cleanliness - 3).coerceAtLeast(0),
            bond = (s.bond + 3).coerceAtMost(100),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun restPuppy() {
        val s = _state.value
        if (s.energy >= 100) return
        _state.value = s.copy(
            energy = (s.energy + 30).coerceAtMost(100),
            happiness = (s.happiness + 3).coerceAtMost(100),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun groomPuppy() {
        val s = _state.value
        if (s.cleanliness >= 100) return
        _state.value = s.copy(
            cleanliness = (s.cleanliness + 35).coerceAtMost(100),
            happiness = (s.happiness + 4).coerceAtMost(100),
            bond = (s.bond + 2).coerceAtMost(100),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun cuddlePuppy() {
        val s = _state.value
        if (s.happiness >= 100 && s.bond >= 100) return
        _state.value = s.copy(
            happiness = (s.happiness + 8).coerceAtMost(100),
            bond = (s.bond + 2).coerceAtMost(100),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun startParkAdventure() {
        val s = _state.value
        if (s.parkActive || s.energy < 20) return
        _state.value = s.copy(
            parkActive = true,
            parkReadyAtMs = System.currentTimeMillis() + PARK_ADVENTURE_MS,
            energy = (s.energy - 20).coerceAtLeast(0)
        )
        saveState()
    }

    fun claimParkAdventure() {
        val s = _state.value
        if (!s.parkActive || System.currentTimeMillis() < s.parkReadyAtMs) return
        val reward = 250L + s.level * 50L
        _state.value = s.copy(
            treats = safeAdd(s.treats, reward),
            lifetimeTreats = safeAdd(s.lifetimeTreats, reward),
            happiness = (s.happiness + 12).coerceAtMost(100),
            fullness = (s.fullness - 5).coerceAtLeast(0),
            cleanliness = (s.cleanliness - 5).coerceAtLeast(0),
            bond = (s.bond + 3).coerceAtMost(100),
            parkActive = false,
            parkReadyAtMs = 0,
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun claimDailyReward() {
        rollDailyDayIfNeeded()
        val s = _state.value
        val today = LocalDate.now().toEpochDay()
        if (s.lastDailyClaimDay == today) return
        val streak = if (s.lastDailyClaimDay == today - 1) {
            (s.dailyStreak + 1).coerceAtMost(365)
        } else {
            1
        }
        val reward = 150L + streak * 25L + s.level * 10L
        _state.value = s.copy(
            treats = safeAdd(s.treats, reward),
            lifetimeTreats = safeAdd(s.lifetimeTreats, reward),
            lastDailyClaimDay = today,
            dailyStreak = streak,
            happiness = (s.happiness + 8).coerceAtMost(100),
            bond = (s.bond + 1).coerceAtMost(100)
        )
        saveState()
    }

    fun claimDailyTask(id: String) {
        rollDailyDayIfNeeded()
        val s = _state.value
        if (id in s.claimedDailyTasks) return
        val reward = when (id) {
            "tap75" -> if (s.dailyTaps >= 75) 300L else return
            "care3" -> if (s.dailyCareActions >= 3) 250L else return
            "shop1" -> if (s.dailyShopPurchases >= 1) 400L else return
            "wellness" -> if (s.careScore >= 90) 350L else return
            else -> return
        }
        _state.value = s.copy(
            treats = safeAdd(s.treats, reward),
            lifetimeTreats = safeAdd(s.lifetimeTreats, reward),
            claimedDailyTasks = s.claimedDailyTasks + id
        )
        saveState()
    }

    fun redeemCode(rawCode: String): V5RedeemOutcome {
        val reward = LocalRedeemCodes.find(rawCode)
            ?: return V5RedeemOutcome(false, "That Puppy Code is invalid or unavailable in this version.")
        val s = _state.value
        if (reward.id in s.redeemedCodeIds) {
            return V5RedeemOutcome(false, "That Puppy Code was already redeemed on this device.")
        }
        val puppy = reward.puppyId?.let { id -> PUPPY_STYLES.firstOrNull { it.id == id } }
        _state.value = s.copy(
            treats = safeAdd(s.treats, reward.treats),
            lifetimeTreats = safeAdd(s.lifetimeTreats, reward.treats),
            unlockedPuppies = if (puppy != null) s.unlockedPuppies + puppy.id else s.unlockedPuppies,
            puppyStyle = puppy?.id ?: s.puppyStyle,
            redeemedCodeIds = s.redeemedCodeIds + reward.id
        )
        saveState()
        return V5RedeemOutcome(true, reward.message)
    }

    fun renamePuppy(name: String) {
        val clean = name.trim().replace("\n", " ").take(18)
        if (clean.isBlank()) return
        _state.update { it.copy(puppyName = clean) }
        saveState()
    }

    fun setPuppyStyle(id: String) {
        val s = _state.value
        if (id !in s.unlockedPuppies || PUPPY_STYLES.none { it.id == id }) return
        _state.value = s.copy(puppyStyle = id)
        saveState()
    }

    fun setAccessory(value: String) {
        if (value !in ACCESSORIES) return
        _state.update { it.copy(accessory = value) }
        saveState()
    }

    fun setHapticsEnabled(value: Boolean) {
        _state.update { it.copy(hapticsEnabled = value) }
        saveState()
    }

    fun setAnimationsEnabled(value: Boolean) {
        _state.update { it.copy(animationsEnabled = value) }
        saveState()
    }

    fun setCompactNumbers(value: Boolean) {
        _state.update { it.copy(compactNumbers = value) }
        saveState()
    }

    fun resetGame() {
        val keep = _state.value
        val afkBackgroundAt = prefs.getLong(KEY_AFK_BACKGROUND_AT, 0L)
        val afkPending = prefs.getLong(KEY_AFK_PENDING, 0L)
        val afkAwayMs = prefs.getLong(KEY_AFK_AWAY_MS, 0L)
        prefs.edit().clear().apply()
        prefs.edit()
            .putLong(KEY_AFK_BACKGROUND_AT, afkBackgroundAt)
            .putLong(KEY_AFK_PENDING, afkPending)
            .putLong(KEY_AFK_AWAY_MS, afkAwayMs)
            .apply()
        recentTapTimes.clear()
        suspicionHits = 0
        suspicionWindowStartedMs = 0L
        _state.value = V5GameState(
            upgrades = V5_UPGRADES.associate { it.id to 0 },
            unlockedPuppies = keep.unlockedPuppies,
            redeemedCodeIds = keep.redeemedCodeIds,
            hapticsEnabled = keep.hapticsEnabled,
            animationsEnabled = keep.animationsEnabled,
            compactNumbers = keep.compactNumbers
        )
        saveState()
    }

    private fun rollTicketRarity(): TicketRarity {
        // Distribution inside the 50% successful ticket roll:
        // Common 80%, Uncommon 16%, Rare 3%, Epic 0.8%, Legendary 0.2%.
        // Overall per accepted tap this is approximately 40%, 8%, 1.5%,
        // 0.4%, and 0.1% respectively.
        val roll = Random.nextInt(1, 1_001)
        return when {
            roll <= 800 -> TicketRarity.COMMON
            roll <= 960 -> TicketRarity.UNCOMMON
            roll <= 990 -> TicketRarity.RARE
            roll <= 998 -> TicketRarity.EPIC
            else -> TicketRarity.LEGENDARY
        }
    }

    private fun rollDailyDayIfNeeded() {
        val today = LocalDate.now().toEpochDay()
        val s = _state.value
        if (s.dailyDay == today) return
        _state.value = s.copy(
            dailyDay = today,
            dailyTapStart = s.totalTaps,
            dailyCareStart = s.careActions,
            dailyShopStart = s.totalShopPurchases,
            claimedDailyTasks = emptySet()
        )
        saveState()
    }

    private fun decayNeeds() {
        _state.update {
            it.copy(
                happiness = (it.happiness - 1).coerceAtLeast(0),
                fullness = (it.fullness - 2).coerceAtLeast(0),
                energy = (it.energy - 1).coerceAtLeast(0),
                cleanliness = (it.cleanliness - 1).coerceAtLeast(0)
            )
        }
    }

    private fun looksAutomated(now: Long): Boolean {
        val taps = recentTapTimes.filter { it >= now - FAIR_PLAY_SAMPLE_MS }
        if (taps.size < MIN_FAIR_PLAY_SAMPLE_TAPS) return false
        if (taps.size >= EXTREME_TAPS_IN_SAMPLE) return true
        val intervals = taps.zipWithNext { a, b -> (b - a).toDouble() }
        if (intervals.size < MIN_INTERVAL_SAMPLE) return false
        if (intervals.takeLast(8).count { it <= IMPOSSIBLE_INTERVAL_MS } >= 6) return true
        val mean = intervals.average()
        if (mean <= 0.0 || mean > MACHINE_MEAN_MAX_MS) return false
        val variance = intervals.sumOf { (it - mean) * (it - mean) } / intervals.size
        val stdDev = sqrt(variance)
        val nearMeanRatio = intervals.count { abs(it - mean) <= MACHINE_NEAR_MEAN_MS }.toDouble() / intervals.size
        return stdDev <= MACHINE_STDDEV_MAX_MS && nearMeanRatio >= MACHINE_REGULARITY_RATIO
    }

    private fun addTreats(amount: Long) {
        if (amount <= 0) return
        _state.update {
            it.copy(
                treats = safeAdd(it.treats, amount),
                lifetimeTreats = safeAdd(it.lifetimeTreats, amount),
                offlineEarned = 0
            )
        }
    }

    private fun loadState(): V5GameState {
        val owned = V5_UPGRADES.associate { upgrade ->
            upgrade.id to prefs.getInt("upgrade_${upgrade.id}", 0).coerceAtLeast(0)
        }
        val clickPower = 1 + V5_UPGRADES
            .filter { it.effect == V5UpgradeEffect.CLICK }
            .sumOf { it.amount * (owned[it.id] ?: 0) }
        val auto = V5_UPGRADES
            .filter { it.effect == V5UpgradeEffect.AUTO }
            .sumOf { it.amount * (owned[it.id] ?: 0) }

        val now = System.currentTimeMillis()
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, now)
        val elapsedMinutes = ((now - lastSeen).coerceAtLeast(0) / 60_000).coerceAtMost(240).toInt()

        val totalTaps = prefs.getLong(KEY_TOTAL_TAPS, 0).coerceAtLeast(0)
        val careActions = prefs.getLong(KEY_CARE_ACTIONS_V5, prefs.getInt("care_actions", 0).toLong()).coerceAtLeast(0)
        val totalShop = prefs.getLong(KEY_TOTAL_SHOP, owned.values.sum().toLong()).coerceAtLeast(0)
        val today = LocalDate.now().toEpochDay()
        val savedDailyDay = prefs.getLong(KEY_DAILY_DAY_V5, today)
        val isToday = savedDailyDay == today

        val defaultPuppies = setOf("classic", "golden", "poodle", "spotty")
        val unlocked = (prefs.getStringSet(KEY_UNLOCKED_PUPPIES, defaultPuppies)?.toSet() ?: defaultPuppies) + defaultPuppies
        val style = prefs.getString(KEY_PUPPY_STYLE, "classic")
            ?.takeIf { it in unlocked && PUPPY_STYLES.any { pup -> pup.id == it } }
            ?: "classic"
        val inventory = TicketRarity.entries.associateWith { rarity ->
            prefs.getInt("upgrade_ticket_${rarity.name.lowercase()}", 0)
                .coerceIn(0, MAX_TICKETS_PER_RARITY)
        }

        val savedHappiness = prefs.getInt(KEY_HAPPINESS, 100).coerceIn(0, 100)
        val savedFullness = prefs.getInt(KEY_FULLNESS, 100).coerceIn(0, 100)
        val savedEnergy = prefs.getInt(KEY_ENERGY, 100).coerceIn(0, 100)
        val savedClean = prefs.getInt(KEY_CLEANLINESS, 100).coerceIn(0, 100)

        // Off-app earnings are deliberately NOT calculated here anymore.
        // PuppyClickerApplication owns the slow AFK clock and AfkWelcomeActivity
        // makes the player explicitly collect that reward on return.
        return V5GameState(
            puppyName = prefs.getString(KEY_NAME, "Buddy") ?: "Buddy",
            puppyStyle = style,
            unlockedPuppies = unlocked,
            accessory = prefs.getString(KEY_ACCESSORY, "None")?.takeIf { it in ACCESSORIES } ?: "None",
            treats = prefs.getLong(KEY_TREATS, 0).coerceAtLeast(0),
            lifetimeTreats = prefs.getLong(KEY_LIFETIME, 0).coerceAtLeast(0),
            clickPower = clickPower,
            autoPerSecond = auto,
            upgrades = owned,
            totalShopPurchases = totalShop,
            ticketInventory = inventory,
            totalTicketsFound = prefs.getLong(KEY_TOTAL_TICKETS_FOUND, 0).coerceAtLeast(0),
            happiness = (savedHappiness - elapsedMinutes / 3).coerceAtLeast(0),
            fullness = (savedFullness - elapsedMinutes / 2).coerceAtLeast(0),
            energy = (savedEnergy - elapsedMinutes / 4).coerceAtLeast(0),
            cleanliness = (savedClean - elapsedMinutes / 4).coerceAtLeast(0),
            bond = prefs.getInt(KEY_BOND, 10).coerceIn(0, 100),
            careActions = careActions,
            totalTaps = totalTaps,
            bestCombo = prefs.getInt(KEY_BEST_COMBO, 0).coerceAtLeast(0),
            parkActive = prefs.getBoolean(KEY_PARK_ACTIVE, false),
            parkReadyAtMs = prefs.getLong(KEY_PARK_READY_AT, 0),
            lastDailyClaimDay = prefs.getLong(KEY_DAILY_CLAIM, Long.MIN_VALUE),
            dailyStreak = prefs.getInt(KEY_DAILY_STREAK, 0).coerceAtLeast(0),
            dailyDay = today,
            dailyTapStart = if (isToday) prefs.getLong(KEY_DAILY_TAP_START, totalTaps) else totalTaps,
            dailyCareStart = if (isToday) prefs.getLong(KEY_DAILY_CARE_START, careActions) else careActions,
            dailyShopStart = if (isToday) prefs.getLong(KEY_DAILY_SHOP_START, totalShop) else totalShop,
            claimedDailyTasks = if (isToday) prefs.getStringSet(KEY_DAILY_TASKS, emptySet())?.toSet() ?: emptySet() else emptySet(),
            redeemedCodeIds = prefs.getStringSet(KEY_REDEEMED_CODES, emptySet())?.toSet() ?: emptySet(),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
            animationsEnabled = prefs.getBoolean(KEY_ANIMATIONS, true),
            compactNumbers = prefs.getBoolean(KEY_COMPACT_NUMBERS, true),
            pupEyeStrikes = prefs.getInt(KEY_STRIKES, 0).coerceAtLeast(0),
            cooldownUntilMs = prefs.getLong(KEY_COOLDOWN, 0).coerceAtLeast(0),
            offlineEarned = 0
        )
    }

    private fun saveState() {
        val s = _state.value
        prefs.edit().apply {
            putString(KEY_NAME, s.puppyName)
            putString(KEY_PUPPY_STYLE, s.puppyStyle)
            putStringSet(KEY_UNLOCKED_PUPPIES, s.unlockedPuppies)
            putString(KEY_ACCESSORY, s.accessory)
            putLong(KEY_TREATS, s.treats)
            putLong(KEY_LIFETIME, s.lifetimeTreats)
            putLong(KEY_TOTAL_SHOP, s.totalShopPurchases)
            putLong(KEY_TOTAL_TICKETS_FOUND, s.totalTicketsFound)
            TicketRarity.entries.forEach {
                putInt("upgrade_ticket_${it.name.lowercase()}", s.ticketInventory[it] ?: 0)
            }
            s.upgrades.forEach { (id, count) -> putInt("upgrade_$id", count) }
            putInt(KEY_HAPPINESS, s.happiness)
            putInt(KEY_FULLNESS, s.fullness)
            putInt(KEY_ENERGY, s.energy)
            putInt(KEY_CLEANLINESS, s.cleanliness)
            putInt(KEY_BOND, s.bond)
            putLong(KEY_CARE_ACTIONS_V5, s.careActions)
            putLong(KEY_TOTAL_TAPS, s.totalTaps)
            putInt(KEY_BEST_COMBO, s.bestCombo)
            putBoolean(KEY_PARK_ACTIVE, s.parkActive)
            putLong(KEY_PARK_READY_AT, s.parkReadyAtMs)
            putLong(KEY_DAILY_CLAIM, s.lastDailyClaimDay)
            putInt(KEY_DAILY_STREAK, s.dailyStreak)
            putLong(KEY_DAILY_DAY_V5, s.dailyDay)
            putLong(KEY_DAILY_TAP_START, s.dailyTapStart)
            putLong(KEY_DAILY_CARE_START, s.dailyCareStart)
            putLong(KEY_DAILY_SHOP_START, s.dailyShopStart)
            putStringSet(KEY_DAILY_TASKS, s.claimedDailyTasks)
            putStringSet(KEY_REDEEMED_CODES, s.redeemedCodeIds)
            putBoolean(KEY_HAPTICS, s.hapticsEnabled)
            putBoolean(KEY_ANIMATIONS, s.animationsEnabled)
            putBoolean(KEY_COMPACT_NUMBERS, s.compactNumbers)
            putInt(KEY_STRIKES, s.pupEyeStrikes)
            putLong(KEY_COOLDOWN, s.cooldownUntilMs)
            putLong(KEY_LAST_SEEN, System.currentTimeMillis())
        }.apply()
    }

    override fun onCleared() {
        saveState()
        super.onCleared()
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0 && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    companion object {
        val ACCESSORIES = listOf("None", "Bandana", "Bow", "Crown")
        const val FEED_COST = 20L
        const val PARK_ADVENTURE_MS = 60_000L

        const val PREFS_NAME = "puppy_clicker_save"
        const val KEY_AFK_BACKGROUND_AT = "afk_background_at_v6"
        const val KEY_AFK_PENDING = "afk_pending_treats_v6"
        const val KEY_AFK_AWAY_MS = "afk_away_ms_v6"
        const val KEY_LAST_SEEN = "last_seen"
        const val KEY_TREATS = "treats"
        const val KEY_LIFETIME = "lifetime_treats"

        private const val KEY_NAME = "puppy_name"
        private const val KEY_PUPPY_STYLE = "puppy_style"
        private const val KEY_UNLOCKED_PUPPIES = "unlocked_puppies"
        private const val KEY_ACCESSORY = "accessory"
        private const val KEY_TOTAL_SHOP = "total_shop_purchases_v5"
        private const val KEY_TOTAL_TICKETS_FOUND = "total_tickets_found"
        private const val KEY_HAPPINESS = "happiness"
        private const val KEY_FULLNESS = "fullness"
        private const val KEY_ENERGY = "energy"
        private const val KEY_CLEANLINESS = "cleanliness_v5"
        private const val KEY_BOND = "bond_v5"
        private const val KEY_CARE_ACTIONS_V5 = "care_actions_v5"
        private const val KEY_TOTAL_TAPS = "total_taps"
        private const val KEY_BEST_COMBO = "best_combo"
        private const val KEY_PARK_ACTIVE = "park_active"
        private const val KEY_PARK_READY_AT = "park_ready_at"
        private const val KEY_DAILY_CLAIM = "daily_claim_day"
        private const val KEY_DAILY_STREAK = "daily_streak_v5"
        private const val KEY_DAILY_DAY_V5 = "daily_day_v5"
        private const val KEY_DAILY_TAP_START = "daily_tap_start_v5"
        private const val KEY_DAILY_CARE_START = "daily_care_start_v5"
        private const val KEY_DAILY_SHOP_START = "daily_shop_start_v5"
        private const val KEY_DAILY_TASKS = "daily_tasks_v5"
        private const val KEY_REDEEMED_CODES = "redeemed_code_ids"
        private const val KEY_HAPTICS = "setting_haptics"
        private const val KEY_ANIMATIONS = "setting_animations"
        private const val KEY_COMPACT_NUMBERS = "setting_compact_numbers"
        private const val KEY_STRIKES = "pup_eye_strikes"
        private const val KEY_COOLDOWN = "fair_play_cooldown_until"

        private const val MAX_TICKETS_PER_RARITY = 9_999
        private const val COMBO_CHAIN_MS = 900L
        private const val COMBO_TIMEOUT_MS = 1_600L
        private const val FAIR_PLAY_HISTORY_MS = 3_000L
        private const val FAIR_PLAY_SAMPLE_MS = 2_000L
        private const val MIN_FAIR_PLAY_SAMPLE_TAPS = 12
        private const val MIN_INTERVAL_SAMPLE = 11
        private const val EXTREME_TAPS_IN_SAMPLE = 48
        private const val IMPOSSIBLE_INTERVAL_MS = 22.0
        private const val MACHINE_MEAN_MAX_MS = 115.0
        private const val MACHINE_STDDEV_MAX_MS = 5.5
        private const val MACHINE_NEAR_MEAN_MS = 7.0
        private const val MACHINE_REGULARITY_RATIO = 0.82
        private const val REQUIRED_SUSPICION_HITS = 2
        private const val SUSPICION_CONFIRM_WINDOW_MS = 5_000L
        private const val BASE_FAIR_PLAY_COOLDOWN_MS = 4_000L
        private const val MAX_FAIR_PLAY_COOLDOWN_MS = 20_000L
    }
}