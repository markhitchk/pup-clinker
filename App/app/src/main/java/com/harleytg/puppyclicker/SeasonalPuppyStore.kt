package com.harleytg.puppyclicker

import android.content.Context
import java.time.Instant
import java.time.MonthDay
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SeasonalPuppySettings(
    val introSeen: Boolean = false,
    val birthday: MonthDay? = null,
    val seenCycles: Set<String> = emptySet()
)

data class SeasonalPuppyClock(val now: Instant, val zone: ZoneId)

/** Separate, device-local preferences. No birth year, age, account ID or network upload. */
internal class SeasonalPuppyStore(context: Context) {
    private val prefs = context.getSharedPreferences("puppy_seasonal_v1", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings = _settings.asStateFlow()
    private val _clock = MutableStateFlow(currentClock())
    val clock = _clock.asStateFlow()

    private fun currentClock() = SeasonalPuppyClock(Instant.now(), ZoneId.systemDefault())

    private fun readSettings(): SeasonalPuppySettings {
        val month = prefs.getInt("birthday_month", 0)
        val day = prefs.getInt("birthday_day", 0)
        return SeasonalPuppySettings(
            introSeen = prefs.getBoolean("intro_seen", false),
            birthday = SeasonalPuppyEvents.birthday(month, day),
            seenCycles = prefs.getStringSet("seen_cycles", emptySet())?.toSet() ?: emptySet()
        )
    }

    fun refreshClock() { _clock.value = currentClock() }

    fun saveBirthday(month: Int, day: Int): Boolean {
        val birthday = SeasonalPuppyEvents.birthday(month, day) ?: return false
        prefs.edit().putInt("birthday_month", month).putInt("birthday_day", day)
            .putBoolean("intro_seen", true).apply()
        _settings.value = _settings.value.copy(birthday = birthday, introSeen = true)
        refreshClock()
        return true
    }

    fun clearBirthday() {
        prefs.edit().remove("birthday_month").remove("birthday_day").apply()
        _settings.value = _settings.value.copy(birthday = null)
        refreshClock()
    }

    fun dismissIntro() {
        prefs.edit().putBoolean("intro_seen", true).apply()
        _settings.value = _settings.value.copy(introSeen = true)
    }

    fun markSeen(cycle: String) {
        if (cycle in _settings.value.seenCycles) return
        val seen = _settings.value.seenCycles + cycle
        prefs.edit().putStringSet("seen_cycles", seen).apply()
        _settings.value = _settings.value.copy(seenCycles = seen)
    }
}
