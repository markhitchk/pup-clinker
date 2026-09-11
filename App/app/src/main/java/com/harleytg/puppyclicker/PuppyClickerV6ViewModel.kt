package com.harleytg.puppyclicker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

class PuppyClickerV6ViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val recentTapTimes = ArrayDeque<Long>()
    private var suspicionHits = 0
    private var suspicionWindowStartedMs = 0L
    private var suppressPersistence = false

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<V6GameState> = _state.asStateFlow()

    init {
        rollDailyDayIfNeeded()
        consumeClaimedAfkReward()
        viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000)
                seconds++
                consumeClaimedAfkReward()

                val now = System.currentTimeMillis()
                val current = _state.value
                if (PuppyAppRuntime.isForeground && current.autoPerSecond > 0) {
                    val careBonus = if (current.careScore >= 85) current.autoPerSecond / 10 else 0
                    addTreats((current.autoPerSecond + careBonus).toLong())
                }

                if (_state.value.combo > 0 && now - _state.value.lastTapMs > COMBO_TIMEOUT_MS) {
                    _state.update { it.copy(combo = 0) }
                }
                if (PuppyAppRuntime.isForeground && seconds % 120 == 0) decayNeeds()
                if (seconds % 5 == 0) saveState()
            }
        }
    }

    fun tapPuppy() {
        val now = System.currentTimeMillis()
        val current = _state.value
        val enforcePupEyeFairPlay =
            PuppyPlayerIdentity.shouldEnforcePupEyeFairPlay(getApplication<Application>())

        if (enforcePupEyeFairPlay && now < current.cooldownUntilMs) return

        if (enforcePupEyeFairPlay) {
            recentTapTimes.addLast(now)
            while (recentTapTimes.isNotEmpty() && recentTapTimes.first < now - FAIR_PLAY_HISTORY_MS) {
                recentTapTimes.removeFirst()
            }
        } else if (recentTapTimes.isNotEmpty() || suspicionHits != 0 || suspicionWindowStartedMs != 0L) {
            // Developer exemption applies only to behavioral fair-play detection.
            // Reset transient detector state so an old player-mode sample cannot leak
            // into a verified developer session.
            recentTapTimes.clear()
            suspicionHits = 0
            suspicionWindowStartedMs = 0L
        }

        val suspiciousThisTap = enforcePupEyeFairPlay && looksAutomated(now)
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
        } else 1

        val nextTaps = safeAdd(current.totalTaps, 1)

        // Tamed ticket cadence: one Upgrade Ticket on every 5th accepted human tap.
        // Suspicious/machine-like taps never receive a ticket.
        val ticketDrop = if (!suspiciousThisTap && nextTaps % 5L == 0L) {
            rollTicketRarityV6()
        } else null
        val inventory = if (ticketDrop == null) current.ticketInventory else {
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
            totalTicketsFound = if (ticketDrop != null) safeAdd(current.totalTicketsFound, 1) else current.totalTicketsFound
        )
        if (ticketDrop != null) saveState()
    }

    fun buyCookieUpgrade(upgrade: V5Upgrade) {
        if (upgrade.type != V5UpgradeType.COOKIE) return
        val current = _state.value
        val owned = current.upgrades[upgrade.id] ?: 0
        val cost = v6UpgradeTreatCost(current, upgrade)
        if (current.treats < cost) return
        applyUpgradePurchase(current, upgrade, owned, current.treats - cost, current.ticketInventory)
    }

    fun buyTicketUpgrade(upgrade: V5Upgrade) {
        if (upgrade.type != V5UpgradeType.TICKET || upgrade.rarity == null) return
        val current = _state.value
        val owned = current.upgrades[upgrade.id] ?: 0
        val ticketCost = v6UpgradeTicketCost(current, upgrade)
        val ownedTickets = current.ticketInventory[upgrade.rarity] ?: 0
        val treatFee = v6UpgradeTreatCost(current, upgrade)
        if (ownedTickets < ticketCost || current.treats < treatFee) return

        val inventory = current.ticketInventory.toMutableMap().apply {
            this[upgrade.rarity] = (ownedTickets - ticketCost).coerceAtLeast(0)
        }
        applyUpgradePurchase(current, upgrade, owned, current.treats - treatFee, inventory)
    }

    private fun applyUpgradePurchase(
        current: V6GameState,
        upgrade: V5Upgrade,
        owned: Int,
        nextTreats: Long,
        inventory: Map<TicketRarity, Int>
    ) {
        val nextUpgrades = current.upgrades.toMutableMap().apply { this[upgrade.id] = owned + 1 }
        val tapSkill = current.prestigeSkills[PrestigeSkill.TAP_TRAINING] ?: 0
        val autoSkill = current.prestigeSkills[PrestigeSkill.AUTO_TRAINING] ?: 0
        val actualAmount = when (upgrade.effect) {
            V5UpgradeEffect.CLICK -> upgrade.amount + tapSkill
            V5UpgradeEffect.AUTO -> upgrade.amount + autoSkill
        }

        _state.value = current.copy(
            treats = nextTreats,
            upgrades = nextUpgrades,
            ticketInventory = inventory,
            clickPower = current.clickPower + if (upgrade.effect == V5UpgradeEffect.CLICK) actualAmount else 0,
            autoPerSecond = current.autoPerSecond + if (upgrade.effect == V5UpgradeEffect.AUTO) actualAmount else 0,
            totalShopPurchases = safeAdd(current.totalShopPurchases, 1),
            happiness = (current.happiness + 2).coerceAtMost(100)
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
        val readyAt = System.currentTimeMillis() + PARK_ADVENTURE_MS
        _state.value = s.copy(
            parkActive = true,
            parkReadyAtMs = readyAt,
            energy = (s.energy - 20).coerceAtLeast(0)
        )
        saveState()
        PuppyNotificationCenter.scheduleParkReady(getApplication(), readyAt)
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
        PuppyNotificationCenter.cancelParkReady(getApplication())
    }

    fun claimDailyReward() {
        rollDailyDayIfNeeded()
        val s = _state.value
        val today = LocalDate.now().toEpochDay()
        if (s.lastDailyClaimDay == today) return
        val streak = if (s.lastDailyClaimDay == today - 1) (s.dailyStreak + 1).coerceAtMost(365) else 1
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
        PuppyNotificationCenter.cancelDailyReward(getApplication())
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

    fun redeemCode(rawCode: String): V6RedeemOutcome {
        val reward = LocalRedeemCodes.find(rawCode)
            ?: return V6RedeemOutcome(false, "That Puppy Code is invalid or unavailable in this version.")
        val s = _state.value
        if (reward.id in s.redeemedCodeIds) {
            return V6RedeemOutcome(false, "That Puppy Code was already redeemed on this device.")
        }

        val puppy = reward.puppyId?.let { id -> V6_PUPPY_STYLES.firstOrNull { it.id == id } }
        _state.value = s.copy(
            treats = safeAdd(s.treats, reward.treats),
            lifetimeTreats = safeAdd(s.lifetimeTreats, reward.treats),
            unlockedPuppies = if (puppy != null) s.unlockedPuppies + puppy.id else s.unlockedPuppies,
            puppyStyle = puppy?.id ?: s.puppyStyle,
            redeemedCodeIds = s.redeemedCodeIds + reward.id
        )
        saveState()
        return V6RedeemOutcome(true, reward.message)
    }

    fun prestige() {
        val current = _state.value
        val gained = current.prestigePointsAvailable
        if (gained <= 0) return
        val today = LocalDate.now().toEpochDay()

        // Current run economy resets. Collection, code history, settings and skills remain.
        _state.value = current.copy(
            treats = 0,
            lifetimeTreats = 0,
            clickPower = 1,
            autoPerSecond = 0,
            upgrades = V5_UPGRADES.associate { it.id to 0 },
            totalShopPurchases = 0,
            ticketInventory = TicketRarity.entries.associateWith { 0 },
            lastTicketDrop = null,
            ticketDropSerial = current.ticketDropSerial,
            happiness = 100,
            fullness = 100,
            energy = 100,
            cleanliness = 100,
            careActions = 0,
            totalTaps = 0,
            combo = 0,
            bestCombo = 0,
            lastTapMs = 0,
            parkActive = false,
            parkReadyAtMs = 0,
            lastDailyClaimDay = Long.MIN_VALUE,
            dailyStreak = 0,
            dailyDay = today,
            dailyTapStart = 0,
            dailyCareStart = 0,
            dailyShopStart = 0,
            claimedDailyTasks = emptySet(),
            prestigeCount = current.prestigeCount + 1,
            skillPoints = current.skillPoints + gained,
            totalPrestigePointsEarned = safeAdd(current.totalPrestigePointsEarned, gained.toLong()),
            cooldownUntilMs = 0
        )
        recentTapTimes.clear()
        suspicionHits = 0
        suspicionWindowStartedMs = 0L
        saveState(clearUpgradeKeys = true)
    }

    fun buyPrestigeSkill(skill: PrestigeSkill) {
        val s = _state.value
        val level = s.prestigeSkills[skill] ?: 0
        if (level >= skill.maxLevel) return
        val cost = v6SkillCost(s, skill)
        if (s.skillPoints < cost) return
        val nextSkills = s.prestigeSkills.toMutableMap().apply { this[skill] = level + 1 }
        _state.value = s.copy(skillPoints = s.skillPoints - cost, prestigeSkills = nextSkills)
        saveState()
    }

    fun renamePuppy(name: String) {
        val clean = name.trim().replace("\n", " ").take(18)
        if (clean.isBlank()) return
        _state.update { it.copy(puppyName = clean) }
        saveState()
    }

    fun setPuppyStyle(id: String) {
        val s = _state.value
        if (id !in s.unlockedPuppies || id !in V6_PUPPY_IDS) return
        _state.value = s.copy(puppyStyle = id)
        saveState()
    }

    fun receiveExchangePuppy(puppyId: String): Boolean {
        val asset = DynamicPuppyRoster.asset(puppyId) ?: return false
        val policy = asset.transferPolicy
        if (!policy.giftable || policy.bound) return false
        val current = _state.value
        if (asset.style.id in current.unlockedPuppies) return true
        _state.value = current.copy(unlockedPuppies = current.unlockedPuppies + asset.style.id)
        saveState()
        return true
    }

    fun applyExchangeTrade(sentPuppyIds: Set<String>, receivedPuppyIds: Set<String>): Boolean {
        if (sentPuppyIds.isEmpty() || receivedPuppyIds.isEmpty()) return false
        if (sentPuppyIds.size > PuppyExchangeLedger.MAX_TRADE_ITEMS ||
            receivedPuppyIds.size > PuppyExchangeLedger.MAX_TRADE_ITEMS
        ) return false
        val current = _state.value
        if (!current.unlockedPuppies.containsAll(sentPuppyIds)) return false

        val sentAssets = sentPuppyIds.map { DynamicPuppyRoster.asset(it) ?: return false }
        val receivedAssets = receivedPuppyIds.map { DynamicPuppyRoster.asset(it) ?: return false }
        if ((sentAssets + receivedAssets).any { asset ->
                val policy = asset.transferPolicy
                !policy.tradeable || policy.bound || policy.sourceCopy
            }
        ) return false

        val nextUnlocked = (current.unlockedPuppies - sentPuppyIds) + receivedPuppyIds
        if (nextUnlocked.isEmpty()) return false
        val nextStyle = if (current.puppyStyle in sentPuppyIds) {
            nextUnlocked.firstOrNull { it in V6_PUPPY_IDS } ?: nextUnlocked.first()
        } else current.puppyStyle

        _state.value = current.copy(
            unlockedPuppies = nextUnlocked,
            puppyStyle = nextStyle
        )
        saveState()
        return true
    }

    fun setAccessory(value: String) {
        if (value !in ACCESSORIES) return
        _state.update { it.copy(accessory = value) }
        saveState()
    }

    fun setHapticsEnabled(value: Boolean) { _state.update { it.copy(hapticsEnabled = value) }; saveState() }
    fun setAnimationsEnabled(value: Boolean) { _state.update { it.copy(animationsEnabled = value) }; saveState() }
    fun setCompactNumbers(value: Boolean) { _state.update { it.copy(compactNumbers = value) }; saveState() }

    /**
     * Stops this ViewModel from writing its old in-memory state after "Delete Local Save Data".
     * Activity/task teardown normally calls onCleared(), which otherwise saves the state again.
     */
    fun prepareForFullLocalDataErase() {
        suppressPersistence = true
        recentTapTimes.clear()
        suspicionHits = 0
        suspicionWindowStartedMs = 0L
        _state.value = V6GameState()
    }

    /** Full non-prestige reset. Special code collection and settings stay protected. */
    fun resetRunWithoutPrestige() {
        val keep = _state.value
        prefs.edit().apply {
            V5_UPGRADES.forEach { remove("upgrade_${it.id}") }
            TicketRarity.entries.forEach { remove(ticketKey(it)) }
        }.apply()
        _state.value = V6GameState(
            puppyName = keep.puppyName,
            puppyStyle = keep.puppyStyle,
            unlockedPuppies = keep.unlockedPuppies,
            accessory = keep.accessory,
            redeemedCodeIds = keep.redeemedCodeIds,
            hapticsEnabled = keep.hapticsEnabled,
            animationsEnabled = keep.animationsEnabled,
            compactNumbers = keep.compactNumbers,
            prestigeCount = keep.prestigeCount,
            skillPoints = keep.skillPoints,
            prestigeSkills = keep.prestigeSkills,
            totalPrestigePointsEarned = keep.totalPrestigePointsEarned,
            totalTicketsFound = keep.totalTicketsFound
        )
        saveState(clearUpgradeKeys = true)
    }

    private fun consumeClaimedAfkReward() {
        val amount = prefs.getLong(KEY_AFK_CLAIM_READY, 0L).coerceAtLeast(0L)
        if (amount <= 0L) return
        prefs.edit().putLong(KEY_AFK_CLAIM_READY, 0L).apply()
        _state.update {
            it.copy(
                treats = safeAdd(it.treats, amount),
                lifetimeTreats = safeAdd(it.lifetimeTreats, amount),
                afkLastClaimed = amount
            )
        }
        saveState()
    }

    private fun rollTicketRarityV6(): TicketRarity {
        val roll = Random.nextInt(1, 1001)
        return when {
            roll <= 680 -> TicketRarity.COMMON       // 68% of drops; about 1 common per 7.4 accepted taps
            roll <= 920 -> TicketRarity.UNCOMMON    // 24% of drops; about 1 uncommon per 20.8 accepted taps
            roll <= 980 -> TicketRarity.RARE        // 6% of drops; about 1 rare per 83 accepted taps
            roll <= 997 -> TicketRarity.EPIC        // 1.7% of drops; about 1 epic per 294 accepted taps
            else -> TicketRarity.LEGENDARY          // 0.3% of drops; about 1 legendary per 1,667 accepted taps
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
                lifetimeTreats = safeAdd(it.lifetimeTreats, amount)
            )
        }
    }

    private fun loadState(): V6GameState {
        val skills = PrestigeSkill.entries.associateWith { skill ->
            prefs.getInt(skillKey(skill), 0).coerceIn(0, skill.maxLevel)
        }
        val owned = V5_UPGRADES.associate { upgrade ->
            upgrade.id to prefs.getInt("upgrade_${upgrade.id}", 0).coerceAtLeast(0)
        }
        val tapSkill = skills[PrestigeSkill.TAP_TRAINING] ?: 0
        val autoSkill = skills[PrestigeSkill.AUTO_TRAINING] ?: 0
        val clickPower = 1 + V5_UPGRADES
            .filter { it.effect == V5UpgradeEffect.CLICK }
            .sumOf { (it.amount + tapSkill) * (owned[it.id] ?: 0) }
        val auto = V5_UPGRADES
            .filter { it.effect == V5UpgradeEffect.AUTO }
            .sumOf { (it.amount + autoSkill) * (owned[it.id] ?: 0) }

        val now = System.currentTimeMillis()
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, now)
        val elapsedMinutes = ((now - lastSeen).coerceAtLeast(0) / 60_000L).coerceAtMost(240L).toInt()
        val totalTaps = prefs.getLong(KEY_TOTAL_TAPS, 0L).coerceAtLeast(0L)
        val careActions = prefs.getLong(KEY_CARE_ACTIONS, prefs.getInt("care_actions", 0).toLong()).coerceAtLeast(0L)
        val totalShop = prefs.getLong(KEY_TOTAL_SHOP, owned.values.sum().toLong()).coerceAtLeast(0L)
        val today = LocalDate.now().toEpochDay()
        val savedDailyDay = prefs.getLong(KEY_DAILY_DAY, today)
        val isToday = savedDailyDay == today

        val unlocked = (prefs.getStringSet(KEY_UNLOCKED_PUPPIES, DEFAULT_V6_PUPPIES)?.toSet() ?: DEFAULT_V6_PUPPIES) + DEFAULT_V6_PUPPIES
        val style = prefs.getString(KEY_PUPPY_STYLE, "classic")
            ?.takeIf { it in unlocked && it in V6_PUPPY_IDS }
            ?: "classic"
        val inventory = TicketRarity.entries.associateWith { rarity ->
            prefs.getInt(ticketKey(rarity), 0).coerceIn(0, MAX_TICKETS_PER_RARITY)
        }

        return V6GameState(
            puppyName = prefs.getString(KEY_NAME, "Buddy") ?: "Buddy",
            puppyStyle = style,
            unlockedPuppies = unlocked,
            accessory = prefs.getString(KEY_ACCESSORY, "None")?.takeIf { it in ACCESSORIES } ?: "None",
            treats = prefs.getLong(KEY_TREATS, 0L).coerceAtLeast(0L),
            lifetimeTreats = prefs.getLong(KEY_LIFETIME, 0L).coerceAtLeast(0L),
            clickPower = clickPower,
            autoPerSecond = auto,
            upgrades = owned,
            totalShopPurchases = totalShop,
            ticketInventory = inventory,
            totalTicketsFound = prefs.getLong(KEY_TOTAL_TICKETS_FOUND, 0L).coerceAtLeast(0L),
            happiness = (prefs.getInt(KEY_HAPPINESS, 100).coerceIn(0, 100) - elapsedMinutes / 3).coerceAtLeast(0),
            fullness = (prefs.getInt(KEY_FULLNESS, 100).coerceIn(0, 100) - elapsedMinutes / 2).coerceAtLeast(0),
            energy = (prefs.getInt(KEY_ENERGY, 100).coerceIn(0, 100) - elapsedMinutes / 4).coerceAtLeast(0),
            cleanliness = (prefs.getInt(KEY_CLEANLINESS, 100).coerceIn(0, 100) - elapsedMinutes / 4).coerceAtLeast(0),
            bond = prefs.getInt(KEY_BOND, 10).coerceIn(0, 100),
            careActions = careActions,
            totalTaps = totalTaps,
            bestCombo = prefs.getInt(KEY_BEST_COMBO, 0).coerceAtLeast(0),
            parkActive = prefs.getBoolean(KEY_PARK_ACTIVE, false),
            parkReadyAtMs = prefs.getLong(KEY_PARK_READY_AT, 0L),
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
            cooldownUntilMs = prefs.getLong(KEY_COOLDOWN, 0L).coerceAtLeast(0L),
            prestigeCount = prefs.getInt(KEY_PRESTIGE_COUNT, 0).coerceAtLeast(0),
            skillPoints = prefs.getInt(KEY_SKILL_POINTS, 0).coerceAtLeast(0),
            prestigeSkills = skills,
            totalPrestigePointsEarned = prefs.getLong(KEY_TOTAL_PRESTIGE_POINTS, 0L).coerceAtLeast(0L)
        )
    }

    private fun saveState(clearUpgradeKeys: Boolean = false) {
        if (suppressPersistence) return
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
            TicketRarity.entries.forEach { rarity -> putInt(ticketKey(rarity), s.ticketInventory[rarity] ?: 0) }
            if (clearUpgradeKeys) V5_UPGRADES.forEach { remove("upgrade_${it.id}") }
            s.upgrades.forEach { (id, count) -> putInt("upgrade_$id", count) }
            putInt(KEY_HAPPINESS, s.happiness)
            putInt(KEY_FULLNESS, s.fullness)
            putInt(KEY_ENERGY, s.energy)
            putInt(KEY_CLEANLINESS, s.cleanliness)
            putInt(KEY_BOND, s.bond)
            putLong(KEY_CARE_ACTIONS, s.careActions)
            putLong(KEY_TOTAL_TAPS, s.totalTaps)
            putInt(KEY_BEST_COMBO, s.bestCombo)
            putBoolean(KEY_PARK_ACTIVE, s.parkActive)
            putLong(KEY_PARK_READY_AT, s.parkReadyAtMs)
            putLong(KEY_DAILY_CLAIM, s.lastDailyClaimDay)
            putInt(KEY_DAILY_STREAK, s.dailyStreak)
            putLong(KEY_DAILY_DAY, s.dailyDay)
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
            putInt(KEY_PRESTIGE_COUNT, s.prestigeCount)
            putInt(KEY_SKILL_POINTS, s.skillPoints)
            putLong(KEY_TOTAL_PRESTIGE_POINTS, s.totalPrestigePointsEarned)
            PrestigeSkill.entries.forEach { skill -> putInt(skillKey(skill), s.prestigeSkills[skill] ?: 0) }
            putLong(KEY_LAST_SEEN, System.currentTimeMillis())
        }.apply()
    }

    override fun onCleared() {
        saveState()
        super.onCleared()
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    companion object {
        const val PREFS_NAME = "puppy_clicker_save"
        val ACCESSORIES = listOf("None", "Bandana", "Bow", "Crown")
        const val FEED_COST = 20L
        const val PARK_ADVENTURE_MS = 60_000L

        private const val PRESTIGE_MIN_TREATS = 50_000L
        private const val PRESTIGE_BONUS_STEP = 100_000L
        private const val MAX_POINTS_PER_PRESTIGE = 8

        private const val KEY_NAME = "puppy_name"
        private const val KEY_PUPPY_STYLE = "puppy_style"
        private const val KEY_UNLOCKED_PUPPIES = "unlocked_puppies"
        private const val KEY_ACCESSORY = "accessory"
        private const val KEY_TREATS = "treats"
        private const val KEY_LIFETIME = "lifetime_treats"
        private const val KEY_TOTAL_SHOP = "total_shop_purchases_v5"
        private const val KEY_TOTAL_TICKETS_FOUND = "total_tickets_found"
        private const val KEY_HAPPINESS = "happiness"
        private const val KEY_FULLNESS = "fullness"
        private const val KEY_ENERGY = "energy"
        private const val KEY_CLEANLINESS = "cleanliness_v5"
        private const val KEY_BOND = "bond_v5"
        private const val KEY_CARE_ACTIONS = "care_actions_v5"
        private const val KEY_TOTAL_TAPS = "total_taps"
        private const val KEY_BEST_COMBO = "best_combo"
        private const val KEY_PARK_ACTIVE = "park_active"
        private const val KEY_PARK_READY_AT = "park_ready_at"
        private const val KEY_DAILY_CLAIM = "daily_claim_day"
        private const val KEY_DAILY_STREAK = "daily_streak_v5"
        private const val KEY_DAILY_DAY = "daily_day_v5"
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
        private const val KEY_LAST_SEEN = "last_seen"

        private const val KEY_PRESTIGE_COUNT = "prestige_count_v6"
        private const val KEY_SKILL_POINTS = "prestige_skill_points_v6"
        private const val KEY_TOTAL_PRESTIGE_POINTS = "prestige_total_points_v6"

        private const val KEY_AFK_CLAIM_READY = "afk_claim_ready_v6"

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

        private fun ticketKey(rarity: TicketRarity) = "upgrade_ticket_${rarity.name.lowercase()}"
        private fun skillKey(skill: PrestigeSkill) = "prestige_skill_${skill.name.lowercase()}_v6"
    }
}

private val DEFAULT_V6_PUPPIES = setOf("classic", "golden", "poodle", "spotty")
private const val PRESTIGE_MIN_TREATS = 50_000L
private const val PRESTIGE_BONUS_STEP = 100_000L
private const val MAX_POINTS_PER_PRESTIGE = 8
