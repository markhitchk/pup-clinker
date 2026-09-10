package com.harleytg.puppyclicker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val PUPPY_DEVELOPER_UNLOCK_TAPS = 7

internal data class DeveloperUnlockProgress(
    val tapCount: Int,
    val remainingTaps: Int,
    val unlocked: Boolean
)

internal fun nextDeveloperUnlockProgress(
    currentTapCount: Int,
    alreadyUnlocked: Boolean
): DeveloperUnlockProgress {
    if (alreadyUnlocked) {
        return DeveloperUnlockProgress(
            tapCount = PUPPY_DEVELOPER_UNLOCK_TAPS,
            remainingTaps = 0,
            unlocked = true
        )
    }

    val next = (currentTapCount.coerceAtLeast(0) + 1).coerceAtMost(PUPPY_DEVELOPER_UNLOCK_TAPS)
    return DeveloperUnlockProgress(
        tapCount = next,
        remainingTaps = PUPPY_DEVELOPER_UNLOCK_TAPS - next,
        unlocked = next >= PUPPY_DEVELOPER_UNLOCK_TAPS
    )
}

internal data class PuppyDeveloperState(
    val unlocked: Boolean = false
)

/** Persistence for the hidden Developer Mode unlock only. */
internal object PuppyDeveloperPreferences {
    private const val PREFS_NAME = "puppy_developer_preferences_v1"
    private const val KEY_UNLOCKED = "developer_mode_unlocked"

    private val mutableState = MutableStateFlow(PuppyDeveloperState())
    private var initialized = false
    private var prefs: SharedPreferences? = null

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, _ ->
        mutableState.value = PuppyDeveloperState(
            unlocked = changedPrefs.getBoolean(KEY_UNLOCKED, false)
        )
    }

    @Synchronized
    private fun ensure(context: Context) {
        if (initialized) return
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        mutableState.value = PuppyDeveloperState(
            unlocked = store.getBoolean(KEY_UNLOCKED, false)
        )
        store.registerOnSharedPreferenceChangeListener(listener)
        initialized = true
    }

    fun observe(context: Context): StateFlow<PuppyDeveloperState> {
        ensure(context)
        return mutableState.asStateFlow()
    }

    fun current(context: Context): PuppyDeveloperState {
        ensure(context)
        return mutableState.value
    }

    fun setUnlocked(context: Context, unlocked: Boolean) {
        ensure(context)
        prefs!!.edit().putBoolean(KEY_UNLOCKED, unlocked).apply()
    }

    fun clear(context: Context) {
        ensure(context)
        prefs!!.edit().clear().apply()
    }
}
