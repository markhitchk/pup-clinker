package com.harleytg.puppyclicker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.ArrayDeque
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

val UPGRADES = listOf(
    Upgrade("better_treats", "Better Treats", "+1 treat per tap", 25, UpgradeEffect.CLICK, 1, "🦴"),
    Upgrade("chew_toy", "Chew Toy", "+1 treat every second", 75, UpgradeEffect.AUTO, 1, "🧸"),
    Upgrade("golden_bowl", "Golden Bowl", "+5 treats per tap", 350, UpgradeEffect.CLICK, 5, "🥣"),
    Upgrade("playmate", "Playmate", "+5 treats every second", 700, UpgradeEffect.AUTO, 5, "🐕"),
    Upgrade("puppy_power", "Puppy Power", "+25 treats per tap", 2_500, UpgradeEffect.CLICK, 25, "⚡"),
    Upgrade("dog_park_crew", "Dog Park Crew", "+25 treats every second", 5_000, UpgradeEffect.AUTO, 25, "🌳")
)

data class GameState(
    val puppyName: String = "Buddy",
    val treats: Long = 0,
    val lifetimeTreats: Long = 0,
    val clickPower: Int = 1,
    val autoPerSecond: Int = 0,
    val upgrades: Map<String, Int> = emptyMap(),
    val accessory: String = "None",
    val pupEyeStrikes: Int = 0,
    val cooldownUntilMs: Long = 0,
    val offlineEarned: Long = 0
) {
    val level: Int
        get() = 1 + sqrt(lifetimeTreats.coerceAtLeast(0).toDouble() / 100.0).toInt()
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
    Achievement("big_taps", "Big Taps", "Reach 25 treats per tap.", "💪") { it.clickPower >= 25 },
    Achievement("auto_pup", "Automatic Pup", "Reach 10 treats per second.", "⏱️") { it.autoPerSecond >= 10 },
    Achievement("level_ten", "Best Friend", "Reach level 10.", "💜") { it.level >= 10 }
)

fun upgradeCost(upgrade: Upgrade, owned: Int): Long {
    val scaled = upgrade.baseCost.toDouble() * 1.58.pow(owned.toDouble())
    return scaled.toLong().coerceAtLeast(upgrade.baseCost)
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val recentTapTimes = ArrayDeque<Long>()

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val auto = _state.value.autoPerSecond
                if (auto > 0) addTreats(auto.toLong())
                saveState()
            }
        }
    }

    fun tapPuppy() {
        val now = System.currentTimeMillis()
        if (now < _state.value.cooldownUntilMs) return

        recentTapTimes.addLast(now)
        while (recentTapTimes.isNotEmpty() && recentTapTimes.first < now - 1_000) {
            recentTapTimes.removeFirst()
        }

        if (recentTapTimes.size > MAX_TAPS_PER_SECOND) {
            recentTapTimes.clear()
            _state.update {
                it.copy(
                    pupEyeStrikes = it.pupEyeStrikes + 1,
                    cooldownUntilMs = now + PUP_EYE_COOLDOWN_MS
                )
            }
            saveState()
            return
        }

        addTreats(_state.value.clickPower.toLong())
        saveState()
    }

    fun buyUpgrade(upgrade: Upgrade) {
        val current = _state.value
        val owned = current.upgrades[upgrade.id] ?: 0
        val cost = upgradeCost(upgrade, owned)
        if (current.treats < cost) return

        val nextUpgrades = current.upgrades.toMutableMap().apply {
            this[upgrade.id] = owned + 1
        }

        _state.value = current.copy(
            treats = current.treats - cost,
            upgrades = nextUpgrades,
            clickPower = current.clickPower + if (upgrade.effect == UpgradeEffect.CLICK) upgrade.amount else 0,
            autoPerSecond = current.autoPerSecond + if (upgrade.effect == UpgradeEffect.AUTO) upgrade.amount else 0,
            offlineEarned = 0
        )
        saveState()
    }

    fun renamePuppy(name: String) {
        val clean = name.trim().replace("\n", " ").take(18)
        if (clean.isBlank()) return
        _state.update { it.copy(puppyName = clean) }
        saveState()
    }

    fun setAccessory(accessory: String) {
        if (accessory !in ACCESSORIES) return
        _state.update { it.copy(accessory = accessory) }
        saveState()
    }

    fun dismissOfflineBonus() {
        _state.update { it.copy(offlineEarned = 0) }
    }

    fun resetGame() {
        prefs.edit().clear().apply()
        recentTapTimes.clear()
        _state.value = GameState(upgrades = UPGRADES.associate { it.id to 0 })
        saveState()
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
        val elapsedSeconds = ((now - lastSeen).coerceAtLeast(0) / 1_000)
            .coerceAtMost(MAX_OFFLINE_SECONDS)
        val offlineEarned = safeMultiply(autoPerSecond.toLong(), elapsedSeconds)
        val savedTreats = prefs.getLong(KEY_TREATS, 0).coerceAtLeast(0)
        val savedLifetime = prefs.getLong(KEY_LIFETIME, 0).coerceAtLeast(0)

        return GameState(
            puppyName = prefs.getString(KEY_NAME, "Buddy") ?: "Buddy",
            treats = safeAdd(savedTreats, offlineEarned),
            lifetimeTreats = safeAdd(savedLifetime, offlineEarned),
            clickPower = clickPower,
            autoPerSecond = autoPerSecond,
            upgrades = owned,
            accessory = prefs.getString(KEY_ACCESSORY, "None")?.takeIf { it in ACCESSORIES } ?: "None",
            pupEyeStrikes = prefs.getInt(KEY_STRIKES, 0).coerceAtLeast(0),
            offlineEarned = offlineEarned
        )
    }

    private fun saveState() {
        val current = _state.value
        prefs.edit().apply {
            putString(KEY_NAME, current.puppyName)
            putLong(KEY_TREATS, current.treats)
            putLong(KEY_LIFETIME, current.lifetimeTreats)
            putString(KEY_ACCESSORY, current.accessory)
            putInt(KEY_STRIKES, current.pupEyeStrikes)
            putLong(KEY_LAST_SEEN, System.currentTimeMillis())
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
        private const val PREFS_NAME = "puppy_clicker_save"
        private const val KEY_NAME = "puppy_name"
        private const val KEY_TREATS = "treats"
        private const val KEY_LIFETIME = "lifetime_treats"
        private const val KEY_ACCESSORY = "accessory"
        private const val KEY_STRIKES = "pup_eye_strikes"
        private const val KEY_LAST_SEEN = "last_seen"
        private const val MAX_TAPS_PER_SECOND = 24
        private const val PUP_EYE_COOLDOWN_MS = 2_500L
        private const val MAX_OFFLINE_SECONDS = 8L * 60L * 60L
    }
}
