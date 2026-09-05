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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class UpgradeEffect { CLICK, AUTO }

data class Upgrade(
    val id: String,
    val name: String,
    val description: String,
    val baseCost: Long,
    val effect: UpgradeEffect,
    val amount: Int,
    val emoji: String
)

data class PuppyStyle(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val redeemOnly: Boolean = false
)

/**
 * Every puppy style uses the original Puppy Clicker pup image as its visual base.
 * The emoji is only a small theme badge used by the Android UI.
 */
val PUPPY_STYLES = listOf(
    PuppyStyle("classic", "Buddy", "🐾", "The original Puppy Clicker pup."),
    PuppyStyle("golden", "Sunny", "🌻", "The original pup with a sunny flower theme."),
    PuppyStyle("poodle", "Mochi", "🎀", "The original pup with a soft pink bow theme."),
    PuppyStyle("spotty", "Pepper", "🖤", "The original pup with a playful dark-bandana theme."),
    PuppyStyle("midnight", "Midnight", "🌙", "A moonlit version of the original pup.", redeemOnly = true),
    PuppyStyle("cloud", "Cloud", "☁️", "A dreamy cloud version of the original pup.", redeemOnly = true)
)

val UPGRADES = listOf(
    Upgrade("better_treats", "Better Treats", "+1 treat per tap", 25, UpgradeEffect.CLICK, 1, "🦴"),
    Upgrade("chew_toy", "Chew Toy", "+1 treat every second", 75, UpgradeEffect.AUTO, 1, "🧸"),
    Upgrade("golden_bowl", "Golden Bowl", "+5 treats per tap", 350, UpgradeEffect.CLICK, 5, "🥣"),
    Upgrade("playmate", "Playmate", "+5 treats every second", 700, UpgradeEffect.AUTO, 5, "🐕"),
    Upgrade("puppy_power", "Puppy Power", "+25 treats per tap", 2_500, UpgradeEffect.CLICK, 25, "⚡"),
    Upgrade("dog_park_crew", "Dog Park Crew", "+25 treats every second", 5_000, UpgradeEffect.AUTO, 25, "🌳"),
    Upgrade("treat_factory", "Treat Factory", "+100 treats every second", 25_000, UpgradeEffect.AUTO, 100, "🏭"),
    Upgrade("legendary_snacks", "Legendary Snacks", "+100 treats per tap", 40_000, UpgradeEffect.CLICK, 100, "✨")
)

data class GameState(
    val puppyName: String = "Buddy",
    val puppyStyle: String = "classic",
    val unlockedPuppies: Set<String> = DEFAULT_UNLOCKED_PUPPIES,
    val treats: Long = 0,
    val lifetimeTreats: Long = 0,
    val clickPower: Int = 1,
    val autoPerSecond: Int = 0,
    val upgrades: Map<String, Int> = emptyMap(),
    val upgradeTickets: Int = 1,
    val accessory: String = "None",
    val pupEyeStrikes: Int = 0,
    val cooldownUntilMs: Long = 0,
    val offlineEarned: Long = 0,
    val happiness: Int = 100,
    val fullness: Int = 100,
    val energy: Int = 100,
    val totalTaps: Long = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val lastTapMs: Long = 0,
    val careActions: Int = 0,
    val parkActive: Boolean = false,
    val parkReadyAtMs: Long = 0,
    val lastDailyClaimDay: Long = Long.MIN_VALUE,
    val claimedMissions: Set<String> = emptySet(),
    val redeemedCodeIds: Set<String> = emptySet(),
    val hapticsEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val compactNumbers: Boolean = true
) {
    val level: Int
        get() = 1 + sqrt(lifetimeTreats.coerceAtLeast(0).toDouble() / 100.0).toInt()

    val careScore: Int
        get() = ((happiness + fullness + energy) / 3).coerceIn(0, 100)

    val mood: String
        get() = when {
            careScore >= 85 -> "Thrilled"
            careScore >= 65 -> "Happy"
            careScore >= 40 -> "Okay"
            careScore >= 20 -> "Needs care"
            else -> "Very tired"
        }

    // Kept for compatibility with older UI. Combos NEVER increase tap rewards.
    val comboMultiplier: Int
        get() = 1

    val fairPlayStatus: String
        get() = if (System.currentTimeMillis() < cooldownUntilMs) "Cooldown active" else "Fair play active"
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val unlocked: (GameState) -> Boolean
)

val ACHIEVEMENTS = listOf(
    Achievement("first_treat", "First Treat", "Give your puppy its first treat.", "🐾") { it.lifetimeTreats >= 1 },
    Achievement("snack_stash", "Snack Stash", "Earn 100 lifetime treats.", "🍪") { it.lifetimeTreats >= 100 },
    Achievement("puppy_pro", "Puppy Pro", "Earn 1,000 lifetime treats.", "🏆") { it.lifetimeTreats >= 1_000 },
    Achievement("combo_hero", "Combo Hero", "Reach a 20 tap combo.", "🔥") { it.bestCombo >= 20 },
    Achievement("big_taps", "Big Taps", "Buy enough Shop upgrades to reach 25 treats per tap.", "💪") { it.clickPower >= 25 },
    Achievement("auto_pup", "Automatic Pup", "Reach 10 treats per second.", "⏱️") { it.autoPerSecond >= 10 },
    Achievement("happy_home", "Happy Home", "Keep all three care meters at 90 or higher.", "💖") {
        it.happiness >= 90 && it.fullness >= 90 && it.energy >= 90
    },
    Achievement("park_regular", "Park Regular", "Complete 5 care actions.", "🌳") { it.careActions >= 5 },
    Achievement("level_ten", "Best Friend", "Reach level 10.", "💜") { it.level >= 10 }
)

data class Mission(
    val id: String,
    val title: String,
    val description: String,
    val reward: Long,
    val ticketReward: Int = 0,
    val complete: (GameState) -> Boolean
)

val MISSIONS = listOf(
    Mission("tap_50", "Fast Paws", "Tap your puppy 50 times.", 250, ticketReward = 1) { it.totalTaps >= 50 },
    Mission("combo_15", "Stay in the Groove", "Reach a 15 tap combo.", 400) { it.bestCombo >= 15 },
    Mission("care_5", "Good Pup Parent", "Complete 5 care actions.", 500, ticketReward = 1) { it.careActions >= 5 },
    Mission("level_5", "Growing Up", "Reach level 5.", 750, ticketReward = 1) { it.level >= 5 },
    Mission("auto_25", "Treat Machine", "Reach 25 treats per second.", 1_000, ticketReward = 1) { it.autoPerSecond >= 25 }
)

data class RedeemOutcome(val success: Boolean, val message: String)

/** Slightly friendlier than the old 1.58 growth curve without trivializing progression. */
fun upgradeCost(upgrade: Upgrade, owned: Int): Long {
    val scaled = upgrade.baseCost.toDouble() * 1.54.pow(owned.toDouble())
    return scaled.toLong().coerceAtLeast(upgrade.baseCost)
}

fun ticketUpgradeCost(upgrade: Upgrade, owned: Int): Long =
    (upgradeCost(upgrade, owned) * 0.80).toLong().coerceAtLeast(1L)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val recentTapTimes = ArrayDeque<Long>()
    private var suspicionHits = 0
    private var suspicionWindowStartedMs = 0L

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000)
                seconds += 1
                val now = System.currentTimeMillis()
                val current = _state.value

                if (current.autoPerSecond > 0) {
                    val moodBonus = if (current.careScore >= 80) current.autoPerSecond / 5 else 0
                    addTreats((current.autoPerSecond + moodBonus).toLong())
                }

                if (_state.value.combo > 0 && now - _state.value.lastTapMs > COMBO_TIMEOUT_MS) {
                    _state.update { it.copy(combo = 0) }
                }

                if (seconds % 60 == 0) decayNeeds()
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

        if (looksAutomated(now)) {
            if (suspicionWindowStartedMs == 0L || now - suspicionWindowStartedMs > SUSPICION_CONFIRM_WINDOW_MS) {
                suspicionWindowStartedMs = now
                suspicionHits = 1
            } else {
                suspicionHits += 1
            }

            // One suspicious sample is treated as a warning only. Two machine-like
            // samples close together are required before any cooldown is applied.
            if (suspicionHits >= REQUIRED_SUSPICION_HITS) {
                recentTapTimes.clear()
                suspicionHits = 0
                suspicionWindowStartedMs = 0L
                val nextStrike = current.pupEyeStrikes + 1
                val cooldown = (BASE_FAIR_PLAY_COOLDOWN_MS + (nextStrike - 1) * 2_000L)
                    .coerceAtMost(MAX_FAIR_PLAY_COOLDOWN_MS)
                _state.value = current.copy(
                    pupEyeStrikes = nextStrike,
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

        val nextCombo = if (now - current.lastTapMs <= COMBO_CHAIN_MS) {
            (current.combo + 1).coerceAtMost(50)
        } else {
            1
        }

        // Tap reward is controlled ONLY by CLICK upgrades bought in the Shop.
        val reward = current.clickPower.toLong()
        val nextTotalTaps = safeAdd(current.totalTaps, 1)
        val energyLoss = if (nextTotalTaps % 8L == 0L) 1 else 0
        val happinessGain = if (nextTotalTaps % 12L == 0L) 1 else 0

        _state.value = current.copy(
            treats = safeAdd(current.treats, reward),
            lifetimeTreats = safeAdd(current.lifetimeTreats, reward),
            totalTaps = nextTotalTaps,
            combo = nextCombo,
            bestCombo = maxOf(current.bestCombo, nextCombo),
            lastTapMs = now,
            energy = (current.energy - energyLoss).coerceIn(0, 100),
            happiness = (current.happiness + happinessGain).coerceIn(0, 100),
            offlineEarned = 0
        )
    }

    /** Legacy call used by older screens. */
    fun buyUpgrade(upgrade: Upgrade) = buyUpgrade(upgrade, useTicket = false)

    fun buyUpgrade(upgrade: Upgrade, useTicket: Boolean) {
        val current = _state.value
        val owned = current.upgrades[upgrade.id] ?: 0
        val ticketApplied = useTicket && current.upgradeTickets > 0
        val cost = if (ticketApplied) ticketUpgradeCost(upgrade, owned) else upgradeCost(upgrade, owned)
        if (current.treats < cost) return

        val nextUpgrades = current.upgrades.toMutableMap().apply { this[upgrade.id] = owned + 1 }
        _state.value = current.copy(
            treats = current.treats - cost,
            upgrades = nextUpgrades,
            upgradeTickets = current.upgradeTickets - if (ticketApplied) 1 else 0,
            clickPower = current.clickPower + if (upgrade.effect == UpgradeEffect.CLICK) upgrade.amount else 0,
            autoPerSecond = current.autoPerSecond + if (upgrade.effect == UpgradeEffect.AUTO) upgrade.amount else 0,
            happiness = (current.happiness + 2).coerceAtMost(100),
            offlineEarned = 0
        )
        saveState()
    }

    fun feedPuppy() {
        val current = _state.value
        if (current.treats < FEED_COST || current.fullness >= 100) return
        _state.value = current.copy(
            treats = current.treats - FEED_COST,
            fullness = (current.fullness + 30).coerceAtMost(100),
            happiness = (current.happiness + 5).coerceAtMost(100),
            careActions = current.careActions + 1
        )
        saveState()
    }

    fun playWithPuppy() {
        val current = _state.value
        if (current.energy < 10) return
        val reward = (5L * current.level).coerceAtLeast(5L)
        _state.value = current.copy(
            treats = safeAdd(current.treats, reward),
            lifetimeTreats = safeAdd(current.lifetimeTreats, reward),
            happiness = (current.happiness + 25).coerceAtMost(100),
            fullness = (current.fullness - 3).coerceAtLeast(0),
            energy = (current.energy - 10).coerceAtLeast(0),
            careActions = current.careActions + 1
        )
        saveState()
    }

    fun restPuppy() {
        val current = _state.value
        if (current.energy >= 100) return
        _state.value = current.copy(
            energy = (current.energy + 30).coerceAtMost(100),
            happiness = (current.happiness + 3).coerceAtMost(100),
            careActions = current.careActions + 1
        )
        saveState()
    }

    fun startParkAdventure() {
        val current = _state.value
        if (current.parkActive || current.energy < 20) return
        _state.value = current.copy(
            parkActive = true,
            parkReadyAtMs = System.currentTimeMillis() + PARK_ADVENTURE_MS,
            energy = (current.energy - 20).coerceAtLeast(0)
        )
        saveState()
    }

    fun claimParkAdventure() {
        val current = _state.value
        if (!current.parkActive || System.currentTimeMillis() < current.parkReadyAtMs) return
        val reward = 250L + current.level * 50L
        _state.value = current.copy(
            treats = safeAdd(current.treats, reward),
            lifetimeTreats = safeAdd(current.lifetimeTreats, reward),
            happiness = (current.happiness + 15).coerceAtMost(100),
            fullness = (current.fullness - 5).coerceAtLeast(0),
            parkActive = false,
            parkReadyAtMs = 0,
            careActions = current.careActions + 1
        )
        saveState()
    }

    fun claimDailyReward() {
        val current = _state.value
        val today = LocalDate.now().toEpochDay()
        if (current.lastDailyClaimDay == today) return
        val reward = 100L + current.level * 25L
        _state.value = current.copy(
            treats = safeAdd(current.treats, reward),
            lifetimeTreats = safeAdd(current.lifetimeTreats, reward),
            upgradeTickets = (current.upgradeTickets + 1).coerceAtMost(MAX_UPGRADE_TICKETS),
            lastDailyClaimDay = today,
            happiness = (current.happiness + 10).coerceAtMost(100)
        )
        saveState()
    }

    fun claimMission(mission: Mission) {
        val current = _state.value
        if (!mission.complete(current) || mission.id in current.claimedMissions) return
        _state.value = current.copy(
            treats = safeAdd(current.treats, mission.reward),
            lifetimeTreats = safeAdd(current.lifetimeTreats, mission.reward),
            upgradeTickets = (current.upgradeTickets + mission.ticketReward).coerceAtMost(MAX_UPGRADE_TICKETS),
            claimedMissions = current.claimedMissions + mission.id
        )
        saveState()
    }

    fun renamePuppy(name: String) {
        val clean = name.trim().replace("\n", " ").take(18)
        if (clean.isBlank()) return
        _state.update { it.copy(puppyName = clean) }
        saveState()
    }

    fun setPuppyStyle(styleId: String) {
        val current = _state.value
        if (styleId !in current.unlockedPuppies) return
        if (PUPPY_STYLES.none { it.id == styleId }) return
        _state.value = current.copy(puppyStyle = styleId)
        saveState()
    }

    fun setAccessory(accessory: String) {
        if (accessory !in ACCESSORIES) return
        _state.update { it.copy(accessory = accessory) }
        saveState()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _state.update { it.copy(hapticsEnabled = enabled) }
        saveState()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        _state.update { it.copy(animationsEnabled = enabled) }
        saveState()
    }

    fun setCompactNumbers(enabled: Boolean) {
        _state.update { it.copy(compactNumbers = enabled) }
        saveState()
    }

    /**
     * Offline promo redemption. The APK stores only salted SHA-256 digests of
     * promo codes, not the plain-text codes. Rewards never modify clickPower.
     */
    fun redeemCode(rawCode: String): RedeemOutcome {
        val reward = LocalRedeemCodes.find(rawCode)
            ?: return RedeemOutcome(false, "That Puppy Code is invalid or unavailable in this version.")

        val current = _state.value
        if (reward.id in current.redeemedCodeIds) {
            return RedeemOutcome(false, "That Puppy Code has already been redeemed on this device.")
        }

        val puppy = reward.puppyId?.let { id -> PUPPY_STYLES.firstOrNull { it.id == id } }
        val nextUnlocked = if (puppy != null) current.unlockedPuppies + puppy.id else current.unlockedPuppies

        _state.value = current.copy(
            treats = safeAdd(current.treats, reward.treats),
            lifetimeTreats = safeAdd(current.lifetimeTreats, reward.treats),
            upgradeTickets = (current.upgradeTickets + reward.tickets).coerceAtMost(MAX_UPGRADE_TICKETS),
            unlockedPuppies = nextUnlocked,
            puppyStyle = puppy?.id ?: current.puppyStyle,
            redeemedCodeIds = current.redeemedCodeIds + reward.id
        )
        saveState()
        return RedeemOutcome(true, reward.message)
    }

    fun dismissOfflineBonus() {
        _state.update { it.copy(offlineEarned = 0) }
    }

    fun resetGame() {
        val keep = _state.value
        prefs.edit().clear().apply()
        recentTapTimes.clear()
        suspicionHits = 0
        suspicionWindowStartedMs = 0L
        _state.value = GameState(
            upgrades = UPGRADES.associate { it.id to 0 },
            upgradeTickets = 1,
            unlockedPuppies = keep.unlockedPuppies,
            redeemedCodeIds = keep.redeemedCodeIds,
            hapticsEnabled = keep.hapticsEnabled,
            animationsEnabled = keep.animationsEnabled,
            compactNumbers = keep.compactNumbers
        )
        saveState()
    }

    private fun looksAutomated(now: Long): Boolean {
        val taps = recentTapTimes.filter { it >= now - FAIR_PLAY_SAMPLE_MS }
        if (taps.size < MIN_FAIR_PLAY_SAMPLE_TAPS) return false

        // A truly extreme sustained rate is suspicious regardless of timing shape.
        if (taps.size >= EXTREME_TAPS_IN_SAMPLE) return true

        val intervals = taps.zipWithNext { a, b -> (b - a).toDouble() }
        if (intervals.size < MIN_INTERVAL_SAMPLE) return false

        // Repeated sub-22 ms taps are outside reasonable touch-screen human input.
        if (intervals.takeLast(8).count { it <= IMPOSSIBLE_INTERVAL_MS } >= 6) return true

        val mean = intervals.average()
        if (mean <= 0.0 || mean > MACHINE_MEAN_MAX_MS) return false

        val variance = intervals.sumOf { (it - mean) * (it - mean) } / intervals.size
        val stdDev = sqrt(variance)
        val nearMeanRatio = intervals.count { abs(it - mean) <= MACHINE_NEAR_MEAN_MS }.toDouble() / intervals.size

        // Human fast tapping is noisy. Auto clickers tend to repeat nearly identical
        // intervals for a sustained run, so regularity matters more than raw speed.
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

    private fun decayNeeds() {
        _state.update {
            it.copy(
                happiness = (it.happiness - 1).coerceAtLeast(0),
                fullness = (it.fullness - 1).coerceAtLeast(0),
                energy = (it.energy + 1).coerceAtMost(100)
            )
        }
    }

    private fun loadState(): GameState {
        val owned = UPGRADES.associate { upgrade ->
            upgrade.id to prefs.getInt("upgrade_${upgrade.id}", 0).coerceAtLeast(0)
        }
        val clickPower = 1 + UPGRADES
            .filter { it.effect == UpgradeEffect.CLICK }
            .sumOf { it.amount * (owned[it.id] ?: 0) }
        val autoPerSecond = UPGRADES
            .filter { it.effect == UpgradeEffect.AUTO }
            .sumOf { it.amount * (owned[it.id] ?: 0) }

        val now = System.currentTimeMillis()
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, now)
        val elapsedSeconds = ((now - lastSeen).coerceAtLeast(0) / 1_000).coerceAtMost(MAX_OFFLINE_SECONDS)
        val offlineEarned = safeMultiply(autoPerSecond.toLong(), elapsedSeconds)
        val savedTreats = prefs.getLong(KEY_TREATS, 0).coerceAtLeast(0)
        val savedLifetime = prefs.getLong(KEY_LIFETIME, 0).coerceAtLeast(0)
        val elapsedMinutes = ((now - lastSeen).coerceAtLeast(0) / 60_000).coerceAtMost(180)
        val savedHappiness = prefs.getInt(KEY_HAPPINESS, 100).coerceIn(0, 100)
        val savedFullness = prefs.getInt(KEY_FULLNESS, 100).coerceIn(0, 100)
        val savedEnergy = prefs.getInt(KEY_ENERGY, 100).coerceIn(0, 100)

        val unlocked = (prefs.getStringSet(KEY_UNLOCKED_PUPPIES, DEFAULT_UNLOCKED_PUPPIES)?.toSet()
            ?: DEFAULT_UNLOCKED_PUPPIES) + DEFAULT_UNLOCKED_PUPPIES
        val selectedStyle = prefs.getString(KEY_PUPPY_STYLE, "classic")
            ?.takeIf { it in unlocked && PUPPY_STYLES.any { style -> style.id == it } }
            ?: "classic"

        return GameState(
            puppyName = prefs.getString(KEY_NAME, "Buddy") ?: "Buddy",
            puppyStyle = selectedStyle,
            unlockedPuppies = unlocked,
            treats = safeAdd(savedTreats, offlineEarned),
            lifetimeTreats = safeAdd(savedLifetime, offlineEarned),
            clickPower = clickPower,
            autoPerSecond = autoPerSecond,
            upgrades = owned,
            upgradeTickets = prefs.getInt(KEY_UPGRADE_TICKETS, 1).coerceIn(0, MAX_UPGRADE_TICKETS),
            accessory = prefs.getString(KEY_ACCESSORY, "None")?.takeIf { it in ACCESSORIES } ?: "None",
            pupEyeStrikes = prefs.getInt(KEY_STRIKES, 0).coerceAtLeast(0),
            cooldownUntilMs = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L).coerceAtLeast(0L),
            offlineEarned = offlineEarned,
            happiness = (savedHappiness - elapsedMinutes.toInt()).coerceAtLeast(0),
            fullness = (savedFullness - elapsedMinutes.toInt()).coerceAtLeast(0),
            energy = (savedEnergy + elapsedMinutes.toInt()).coerceAtMost(100),
            totalTaps = prefs.getLong(KEY_TOTAL_TAPS, 0).coerceAtLeast(0),
            bestCombo = prefs.getInt(KEY_BEST_COMBO, 0).coerceAtLeast(0),
            careActions = prefs.getInt(KEY_CARE_ACTIONS, 0).coerceAtLeast(0),
            parkActive = prefs.getBoolean(KEY_PARK_ACTIVE, false),
            parkReadyAtMs = prefs.getLong(KEY_PARK_READY_AT, 0),
            lastDailyClaimDay = prefs.getLong(KEY_DAILY_DAY, Long.MIN_VALUE),
            claimedMissions = prefs.getStringSet(KEY_CLAIMED_MISSIONS, emptySet())?.toSet() ?: emptySet(),
            redeemedCodeIds = prefs.getStringSet(KEY_REDEEMED_CODES, emptySet())?.toSet() ?: emptySet(),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
            animationsEnabled = prefs.getBoolean(KEY_ANIMATIONS, true),
            compactNumbers = prefs.getBoolean(KEY_COMPACT_NUMBERS, true)
        )
    }

    private fun saveState() {
        val current = _state.value
        prefs.edit().apply {
            putString(KEY_NAME, current.puppyName)
            putString(KEY_PUPPY_STYLE, current.puppyStyle)
            putStringSet(KEY_UNLOCKED_PUPPIES, current.unlockedPuppies.toSet())
            putLong(KEY_TREATS, current.treats)
            putLong(KEY_LIFETIME, current.lifetimeTreats)
            putInt(KEY_UPGRADE_TICKETS, current.upgradeTickets)
            putString(KEY_ACCESSORY, current.accessory)
            putInt(KEY_STRIKES, current.pupEyeStrikes)
            putLong(KEY_COOLDOWN_UNTIL, current.cooldownUntilMs)
            putLong(KEY_LAST_SEEN, System.currentTimeMillis())
            putInt(KEY_HAPPINESS, current.happiness)
            putInt(KEY_FULLNESS, current.fullness)
            putInt(KEY_ENERGY, current.energy)
            putLong(KEY_TOTAL_TAPS, current.totalTaps)
            putInt(KEY_BEST_COMBO, current.bestCombo)
            putInt(KEY_CARE_ACTIONS, current.careActions)
            putBoolean(KEY_PARK_ACTIVE, current.parkActive)
            putLong(KEY_PARK_READY_AT, current.parkReadyAtMs)
            putLong(KEY_DAILY_DAY, current.lastDailyClaimDay)
            putStringSet(KEY_CLAIMED_MISSIONS, current.claimedMissions.toSet())
            putStringSet(KEY_REDEEMED_CODES, current.redeemedCodeIds.toSet())
            putBoolean(KEY_HAPTICS, current.hapticsEnabled)
            putBoolean(KEY_ANIMATIONS, current.animationsEnabled)
            putBoolean(KEY_COMPACT_NUMBERS, current.compactNumbers)
            current.upgrades.forEach { (id, count) -> putInt("upgrade_$id", count) }
        }.apply()
    }

    override fun onCleared() {
        saveState()
        super.onCleared()
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0 && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    private fun safeMultiply(a: Long, b: Long): Long = when {
        a <= 0 || b <= 0 -> 0
        a > Long.MAX_VALUE / b -> Long.MAX_VALUE
        else -> a * b
    }

    companion object {
        val ACCESSORIES = listOf("None", "Bandana", "Bow", "Crown")
        const val FEED_COST = 20L
        const val PARK_ADVENTURE_MS = 60_000L
        const val TICKET_DISCOUNT_PERCENT = 20

        private const val MAX_UPGRADE_TICKETS = 99
        private const val PREFS_NAME = "puppy_clicker_save"
        private const val KEY_NAME = "puppy_name"
        private const val KEY_PUPPY_STYLE = "puppy_style"
        private const val KEY_UNLOCKED_PUPPIES = "unlocked_puppies"
        private const val KEY_TREATS = "treats"
        private const val KEY_LIFETIME = "lifetime_treats"
        private const val KEY_UPGRADE_TICKETS = "upgrade_tickets"
        private const val KEY_ACCESSORY = "accessory"
        private const val KEY_STRIKES = "pup_eye_strikes"
        private const val KEY_COOLDOWN_UNTIL = "fair_play_cooldown_until"
        private const val KEY_LAST_SEEN = "last_seen"
        private const val KEY_HAPPINESS = "happiness"
        private const val KEY_FULLNESS = "fullness"
        private const val KEY_ENERGY = "energy"
        private const val KEY_TOTAL_TAPS = "total_taps"
        private const val KEY_BEST_COMBO = "best_combo"
        private const val KEY_CARE_ACTIONS = "care_actions"
        private const val KEY_PARK_ACTIVE = "park_active"
        private const val KEY_PARK_READY_AT = "park_ready_at"
        private const val KEY_DAILY_DAY = "daily_claim_day"
        private const val KEY_CLAIMED_MISSIONS = "claimed_missions"
        private const val KEY_REDEEMED_CODES = "redeemed_code_ids"
        private const val KEY_HAPTICS = "setting_haptics"
        private const val KEY_ANIMATIONS = "setting_animations"
        private const val KEY_COMPACT_NUMBERS = "setting_compact_numbers"

        private const val MAX_OFFLINE_SECONDS = 8L * 60L * 60L
        private const val COMBO_CHAIN_MS = 900L
        private const val COMBO_TIMEOUT_MS = 1_600L

        // Fair-play thresholds intentionally favor avoiding false positives.
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

private val DEFAULT_UNLOCKED_PUPPIES = setOf("classic", "golden", "poodle", "spotty")
