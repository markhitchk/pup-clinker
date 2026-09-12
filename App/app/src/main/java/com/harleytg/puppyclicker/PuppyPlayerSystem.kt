package com.harleytg.puppyclicker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Player preferences, birthday validation, developer mode, motion, and haptic policy.
 *
 * Consolidated as part of the six-system Kotlin architecture.
 */

// ---- PuppyBirthday ----
internal object PuppyBirthday {
    fun maxDay(month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> 29
        else -> 0
    }

    fun isValid(month: Int, day: Int): Boolean {
        val maximum = maxDay(month)
        return maximum > 0 && day in 1..maximum
    }
}

// ---- PuppyDeveloperPreferences ----
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

// ---- PuppyMotionPolicy ----
enum class PuppyMotionPreset {
    MINIMAL,
    BALANCED,
    PLAYFUL,
    CUSTOM
}

enum class PuppyMotionIntensity {
    LOW,
    MEDIUM,
    HIGH
}

enum class PuppyMotionMode {
    FULL,
    PERFORMANCE,
    REDUCED,
    STATIC
}

data class PuppyMotionConfig(
    val preset: PuppyMotionPreset,
    val intensity: PuppyMotionIntensity,
    val animatedUi: Boolean,
    val buttonAnimations: Boolean,
    val screenTransitions: Boolean,
    val cardAnimations: Boolean,
    val counterAnimations: Boolean,
    val celebrations: Boolean,
    val warningAnimations: Boolean,
    val loadingAnimations: Boolean,
    val shimmerEffects: Boolean,
    val adaptivePerformance: Boolean,
    val reducedMotion: Boolean = false
)

object PuppyMotionPresets {
    fun config(preset: PuppyMotionPreset): PuppyMotionConfig = when (preset) {
        PuppyMotionPreset.MINIMAL -> PuppyMotionConfig(
            preset = PuppyMotionPreset.MINIMAL,
            intensity = PuppyMotionIntensity.LOW,
            animatedUi = true,
            buttonAnimations = false,
            screenTransitions = true,
            cardAnimations = false,
            counterAnimations = false,
            celebrations = false,
            warningAnimations = true,
            loadingAnimations = true,
            shimmerEffects = false,
            adaptivePerformance = true
        )

        PuppyMotionPreset.BALANCED -> PuppyMotionConfig(
            preset = PuppyMotionPreset.BALANCED,
            intensity = PuppyMotionIntensity.MEDIUM,
            animatedUi = true,
            buttonAnimations = true,
            screenTransitions = true,
            cardAnimations = true,
            counterAnimations = true,
            celebrations = true,
            warningAnimations = true,
            loadingAnimations = true,
            shimmerEffects = true,
            adaptivePerformance = true
        )

        PuppyMotionPreset.PLAYFUL -> PuppyMotionConfig(
            preset = PuppyMotionPreset.PLAYFUL,
            intensity = PuppyMotionIntensity.HIGH,
            animatedUi = true,
            buttonAnimations = true,
            screenTransitions = true,
            cardAnimations = true,
            counterAnimations = true,
            celebrations = true,
            warningAnimations = true,
            loadingAnimations = true,
            shimmerEffects = true,
            adaptivePerformance = true
        )

        PuppyMotionPreset.CUSTOM -> config(PuppyMotionPreset.BALANCED).copy(preset = PuppyMotionPreset.CUSTOM)
    }
}

object PuppyMotionPolicy {
    fun mode(config: PuppyMotionConfig, performanceConstrained: Boolean): PuppyMotionMode = when {
        config.reducedMotion -> PuppyMotionMode.REDUCED
        !config.animatedUi -> PuppyMotionMode.STATIC
        config.adaptivePerformance && performanceConstrained -> PuppyMotionMode.PERFORMANCE
        else -> PuppyMotionMode.FULL
    }

    fun afterManualOverride(current: PuppyMotionPreset): PuppyMotionPreset = PuppyMotionPreset.CUSTOM
}

// ---- PuppyHapticsPolicy ----
enum class PuppyHapticEvent {
    TAP,
    SELECTION,
    NAVIGATION,
    TOGGLE,
    SUCCESS,
    ERROR,
    REWARD,
    TICKET_DROP,
    DANGER_CONFIRM,
    TEST
}

enum class PuppyHapticRoute {
    NONE,
    SEMANTIC_THEN_DIRECT,
    DIRECT
}

enum class PuppyHapticStrength {
    LIGHT,
    MEDIUM,
    STRONG
}

data class PuppyHapticPlan(
    val route: PuppyHapticRoute,
    val strength: PuppyHapticStrength
)

enum class PuppyHapticResult {
    DISABLED,
    SEMANTIC,
    DIRECT,
    UNAVAILABLE,
    ERROR
}

object PuppyHapticExecution {
    fun resolve(
        route: PuppyHapticRoute,
        semanticSucceeded: Boolean,
        directSucceeded: Boolean
    ): PuppyHapticResult = when {
        route == PuppyHapticRoute.NONE -> PuppyHapticResult.DISABLED
        route == PuppyHapticRoute.SEMANTIC_THEN_DIRECT && semanticSucceeded -> PuppyHapticResult.SEMANTIC
        directSucceeded -> PuppyHapticResult.DIRECT
        else -> PuppyHapticResult.UNAVAILABLE
    }
}

object PuppyHapticPolicy {
    fun plan(event: PuppyHapticEvent, enabled: Boolean): PuppyHapticPlan {
        if (!enabled) {
            return PuppyHapticPlan(PuppyHapticRoute.NONE, PuppyHapticStrength.LIGHT)
        }

        return when (event) {
            PuppyHapticEvent.TAP,
            PuppyHapticEvent.SELECTION,
            PuppyHapticEvent.NAVIGATION,
            PuppyHapticEvent.TOGGLE ->
                PuppyHapticPlan(PuppyHapticRoute.SEMANTIC_THEN_DIRECT, PuppyHapticStrength.LIGHT)

            PuppyHapticEvent.SUCCESS ->
                PuppyHapticPlan(PuppyHapticRoute.SEMANTIC_THEN_DIRECT, PuppyHapticStrength.MEDIUM)

            PuppyHapticEvent.ERROR ->
                PuppyHapticPlan(PuppyHapticRoute.SEMANTIC_THEN_DIRECT, PuppyHapticStrength.STRONG)

            PuppyHapticEvent.REWARD,
            PuppyHapticEvent.TICKET_DROP,
            PuppyHapticEvent.DANGER_CONFIRM,
            PuppyHapticEvent.TEST ->
                PuppyHapticPlan(PuppyHapticRoute.DIRECT, PuppyHapticStrength.STRONG)
        }
    }
}