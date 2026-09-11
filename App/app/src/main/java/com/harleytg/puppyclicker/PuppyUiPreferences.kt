package com.harleytg.puppyclicker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PuppyThemeMode { LIGHT, DARK, SYSTEM }
enum class PuppyUiScale { COMPACT, DEFAULT, LARGE }

data class PuppyUiState(
    val themeMode: PuppyThemeMode = PuppyThemeMode.SYSTEM,
    val accentHex: String = PuppyUiPreferences.DEFAULT_ACCENT,
    val customAccentHex: String = PuppyUiPreferences.DEFAULT_ACCENT,
    val animatedUi: Boolean = true,
    val buttonAnimations: Boolean = true,
    val motionPreset: PuppyMotionPreset = PuppyMotionPreset.BALANCED,
    val motionIntensity: PuppyMotionIntensity = PuppyMotionIntensity.MEDIUM,
    val screenTransitions: Boolean = true,
    val cardAnimations: Boolean = true,
    val counterAnimations: Boolean = true,
    val celebrationAnimations: Boolean = true,
    val warningAnimations: Boolean = true,
    val loadingAnimations: Boolean = true,
    val shimmerEffects: Boolean = true,
    val adaptivePerformance: Boolean = true,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val uiScale: PuppyUiScale = PuppyUiScale.COMPACT,
    val birthdayMonth: Int = 0,
    val birthdayDay: Int = 0,
    val setupComplete: Boolean = false,
    val setupStep: Int = 0,
    val dailyRewardNotifications: Boolean = true,
    val gameEventNotifications: Boolean = true,
    val updateNotifications: Boolean = true,
    val migrationComplete: Boolean = false
) {
    val hasBirthday: Boolean
        get() = PuppyBirthday.isValid(birthdayMonth, birthdayDay)

    fun motionConfig(): PuppyMotionConfig = PuppyMotionConfig(
        preset = motionPreset,
        intensity = motionIntensity,
        animatedUi = animatedUi,
        buttonAnimations = buttonAnimations,
        screenTransitions = screenTransitions,
        cardAnimations = cardAnimations,
        counterAnimations = counterAnimations,
        celebrations = celebrationAnimations,
        warningAnimations = warningAnimations,
        loadingAnimations = loadingAnimations,
        shimmerEffects = shimmerEffects,
        adaptivePerformance = adaptivePerformance,
        reducedMotion = reducedMotion
    )
}

/**
 * Central persistence for Settings/onboarding preferences introduced by the UI revamp.
 * Existing gameplay state remains in the V6 game store; this object owns only app UI/profile
 * settings that should not be scattered through activities and composables.
 */
internal object PuppyUiPreferences {
    const val PREFS_NAME = "puppy_ui_preferences_v2"
    const val DEFAULT_ACCENT = "#00B8F0"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT = "accent_hex"
    private const val KEY_CUSTOM_ACCENT = "custom_accent_hex"
    private const val KEY_ANIMATED_UI = "animated_ui"
    private const val KEY_BUTTON_ANIMATIONS = "button_animations"
    private const val KEY_MOTION_PRESET = "motion_preset"
    private const val KEY_MOTION_INTENSITY = "motion_intensity"
    private const val KEY_SCREEN_TRANSITIONS = "screen_transitions"
    private const val KEY_CARD_ANIMATIONS = "card_animations"
    private const val KEY_COUNTER_ANIMATIONS = "counter_animations"
    private const val KEY_CELEBRATION_ANIMATIONS = "celebration_animations"
    private const val KEY_WARNING_ANIMATIONS = "warning_animations"
    private const val KEY_LOADING_ANIMATIONS = "loading_animations"
    private const val KEY_SHIMMER_EFFECTS = "shimmer_effects"
    private const val KEY_ADAPTIVE_PERFORMANCE = "adaptive_performance"
    private const val KEY_REDUCED_MOTION = "reduced_motion"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_UI_SCALE = "ui_scale"
    private const val KEY_BIRTHDAY_MONTH = "birthday_month"
    private const val KEY_BIRTHDAY_DAY = "birthday_day"
    private const val KEY_BIRTHDAY_YEAR = "birthday_year"
    private const val KEY_SETUP_COMPLETE = "setup_complete"
    private const val KEY_SETUP_STEP = "setup_step"
    private const val KEY_SETUP_FLOW_VERSION = "setup_flow_version"
    private const val SETUP_FLOW_VERSION = 2
    private const val KEY_NOTIFY_DAILY = "notify_daily_rewards"
    private const val KEY_NOTIFY_EVENTS = "notify_game_events"
    private const val KEY_NOTIFY_UPDATES = "notify_app_updates"
    private const val KEY_MIGRATION_COMPLETE = "migration_complete"

    private val mutableState = MutableStateFlow(PuppyUiState())
    private var initialized = false
    private var prefs: SharedPreferences? = null

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, _ ->
        mutableState.value = read(changedPrefs)
    }

    @Synchronized
    private fun ensure(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        val store = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateOnce(app, store)
        migrateSetupFlowIfNeeded(store)
        if (store.contains(KEY_BIRTHDAY_YEAR)) {
            store.edit().remove(KEY_BIRTHDAY_YEAR).apply()
        }
        prefs = store
        mutableState.value = read(store)
        store.registerOnSharedPreferenceChangeListener(listener)
        initialized = true
    }

    fun observe(context: Context): StateFlow<PuppyUiState> {
        ensure(context)
        return mutableState.asStateFlow()
    }

    fun current(context: Context): PuppyUiState {
        ensure(context)
        return mutableState.value
    }

    fun setThemeMode(context: Context, mode: PuppyThemeMode) = edit(context) {
        putString(KEY_THEME_MODE, mode.name)
    }

    fun setAccent(context: Context, hex: String, custom: Boolean = false): Boolean {
        val normalized = normalizeHex(hex) ?: return false
        edit(context) {
            putString(KEY_ACCENT, normalized)
            if (custom) putString(KEY_CUSTOM_ACCENT, normalized)
        }
        return true
    }

    fun setAnimatedUi(context: Context, enabled: Boolean) = edit(context) {
        putBoolean(KEY_ANIMATED_UI, enabled)
    }

    fun setButtonAnimations(context: Context, enabled: Boolean) = edit(context) {
        putBoolean(KEY_BUTTON_ANIMATIONS, enabled)
    }

    fun setReducedMotion(context: Context, enabled: Boolean) = edit(context) {
        putBoolean(KEY_REDUCED_MOTION, enabled)
    }

    fun setHighContrast(context: Context, enabled: Boolean) = edit(context) {
        putBoolean(KEY_HIGH_CONTRAST, enabled)
    }

    fun setUiScale(context: Context, scale: PuppyUiScale) = edit(context) {
        putString(KEY_UI_SCALE, scale.name)
    }

    fun setBirthday(context: Context, month: Int, day: Int): Boolean {
        if (!PuppyBirthday.isValid(month, day)) return false
        edit(context) {
            putInt(KEY_BIRTHDAY_MONTH, month)
            putInt(KEY_BIRTHDAY_DAY, day)
            remove(KEY_BIRTHDAY_YEAR)
        }
        return true
    }

    fun clearBirthday(context: Context) = edit(context) {
        remove(KEY_BIRTHDAY_MONTH)
        remove(KEY_BIRTHDAY_DAY)
        remove(KEY_BIRTHDAY_YEAR)
    }

    fun setSetupStep(context: Context, step: Int) {
        ensure(context)
        if (!mutableState.value.setupComplete) edit(context) {
            putInt(KEY_SETUP_STEP, step.coerceIn(0, 4))
        }
    }

    fun finishSetup(context: Context) = edit(context) {
        putBoolean(KEY_SETUP_COMPLETE, true)
        putInt(KEY_SETUP_STEP, 4)
        putInt(KEY_SETUP_FLOW_VERSION, SETUP_FLOW_VERSION)
    }

    fun keepSetupIncompleteAfterImport(context: Context) = edit(context) {
        putBoolean(KEY_SETUP_COMPLETE, false)
        putInt(KEY_SETUP_STEP, PuppyOnboardingStep.PLAYER_SETUP.persistedIndex)
        putInt(KEY_SETUP_FLOW_VERSION, SETUP_FLOW_VERSION)
        putBoolean(KEY_MIGRATION_COMPLETE, true)
    }

    fun setDailyRewardNotifications(context: Context, enabled: Boolean) = edit(context) {
        putBoolean(KEY_NOTIFY_DAILY, enabled)
    }

    fun setGameEventNotifications(context: Context, enabled: Boolean) = edit(context) {
        putBoolean(KEY_NOTIFY_EVENTS, enabled)
    }

    fun setUpdateNotifications(context: Context, enabled: Boolean) = edit(context) {
        putBoolean(KEY_NOTIFY_UPDATES, enabled)
    }

    /** Reset visual/preferences choices without erasing profile data or replaying onboarding. */
    fun resetInterfaceSettings(context: Context) {
        ensure(context)
        val keep = current(context)
        edit(context) {
            clear()
            if (keep.birthdayMonth > 0) putInt(KEY_BIRTHDAY_MONTH, keep.birthdayMonth)
            if (keep.birthdayDay > 0) putInt(KEY_BIRTHDAY_DAY, keep.birthdayDay)
            putBoolean(KEY_SETUP_COMPLETE, true)
            putInt(KEY_SETUP_STEP, 4)
            putInt(KEY_SETUP_FLOW_VERSION, SETUP_FLOW_VERSION)
            putBoolean(KEY_MIGRATION_COMPLETE, true)
        }
    }

    /**
     * Explicit destructive reset path. Mark migration handled while leaving setup incomplete so
     * an updated installation cannot immediately migrate itself past onboarding after deletion.
     */
    fun prepareFreshSetupAfterDelete(context: Context) {
        ensure(context)
        edit(context) {
            clear()
            putBoolean(KEY_MIGRATION_COMPLETE, true)
            putBoolean(KEY_SETUP_COMPLETE, false)
            putInt(KEY_SETUP_STEP, 0)
            putInt(KEY_SETUP_FLOW_VERSION, SETUP_FLOW_VERSION)
        }
    }

    fun normalizeHex(value: String): String? {
        val candidate = value.trim().removePrefix("#")
        if (!candidate.matches(Regex("[0-9A-Fa-f]{6}"))) return null
        return "#${candidate.uppercase()}"
    }

    private inline fun edit(context: Context, block: SharedPreferences.Editor.() -> Unit) {
        ensure(context)
        prefs!!.edit().apply(block).apply()
    }

    /**
     * Existing installs are not forced through the new onboarding. Android package timestamps
     * distinguish an updated install from a first install; legacy intro/profile state provides
     * a second migration signal for restored installs.
     */
    private fun migrateOnce(context: Context, store: SharedPreferences) {
        if (store.getBoolean(KEY_MIGRATION_COMPLETE, false)) return

        val packageInfo = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val looksUpdated = packageInfo != null &&
            packageInfo.lastUpdateTime > packageInfo.firstInstallTime + 1_000L

        val seasonal = context.getSharedPreferences("puppy_seasonal_v1", Context.MODE_PRIVATE)
        val legacyIntroSeen = seasonal.getBoolean("intro_seen", false)
        val legacyMonth = seasonal.getInt("birthday_month", 0)
        val legacyDay = seasonal.getInt("birthday_day", 0)
        val hasConfiguredUsername = PuppyPlayerIdentity.username(context) != "localplayer"
        val existingInstall = looksUpdated || legacyIntroSeen || hasConfiguredUsername

        store.edit().apply {
            putBoolean(KEY_MIGRATION_COMPLETE, true)
            if (existingInstall) {
                putBoolean(KEY_SETUP_COMPLETE, true)
                putInt(KEY_SETUP_STEP, 5)
            }
            if (PuppyBirthday.isValid(legacyMonth, legacyDay)) {
                putInt(KEY_BIRTHDAY_MONTH, legacyMonth)
                putInt(KEY_BIRTHDAY_DAY, legacyDay)
            }
            remove(KEY_BIRTHDAY_YEAR)
        }.apply()
    }

    private fun migrateSetupFlowIfNeeded(store: SharedPreferences) {
        if (store.getInt(KEY_SETUP_FLOW_VERSION, 1) >= SETUP_FLOW_VERSION) return

        val complete = store.getBoolean(KEY_SETUP_COMPLETE, false)
        val migrated = if (complete) {
            4
        } else {
            migrateLegacyOnboardingStep(store.getInt(KEY_SETUP_STEP, 0))
        }

        store.edit()
            .putInt(KEY_SETUP_STEP, migrated)
            .putInt(KEY_SETUP_FLOW_VERSION, SETUP_FLOW_VERSION)
            .apply()
    }

    private fun read(store: SharedPreferences): PuppyUiState {
        val theme = runCatching {
            PuppyThemeMode.valueOf(store.getString(KEY_THEME_MODE, PuppyThemeMode.SYSTEM.name) ?: PuppyThemeMode.SYSTEM.name)
        }.getOrDefault(PuppyThemeMode.SYSTEM)
        val scale = runCatching {
            PuppyUiScale.valueOf(store.getString(KEY_UI_SCALE, PuppyUiScale.COMPACT.name) ?: PuppyUiScale.COMPACT.name)
        }.getOrDefault(PuppyUiScale.COMPACT)
        val preset = runCatching {
            PuppyMotionPreset.valueOf(store.getString(KEY_MOTION_PRESET, PuppyMotionPreset.BALANCED.name) ?: PuppyMotionPreset.BALANCED.name)
        }.getOrDefault(PuppyMotionPreset.BALANCED)
        val intensity = runCatching {
            PuppyMotionIntensity.valueOf(store.getString(KEY_MOTION_INTENSITY, PuppyMotionIntensity.MEDIUM.name) ?: PuppyMotionIntensity.MEDIUM.name)
        }.getOrDefault(PuppyMotionIntensity.MEDIUM)
        val accent = normalizeHex(store.getString(KEY_ACCENT, DEFAULT_ACCENT) ?: DEFAULT_ACCENT) ?: DEFAULT_ACCENT
        val custom = normalizeHex(store.getString(KEY_CUSTOM_ACCENT, accent) ?: accent) ?: accent

        return PuppyUiState(
            themeMode = theme,
            accentHex = accent,
            customAccentHex = custom,
            animatedUi = store.getBoolean(KEY_ANIMATED_UI, true),
            buttonAnimations = store.getBoolean(KEY_BUTTON_ANIMATIONS, true),
            motionPreset = preset,
            motionIntensity = intensity,
            screenTransitions = store.getBoolean(KEY_SCREEN_TRANSITIONS, true),
            cardAnimations = store.getBoolean(KEY_CARD_ANIMATIONS, true),
            counterAnimations = store.getBoolean(KEY_COUNTER_ANIMATIONS, true),
            celebrationAnimations = store.getBoolean(KEY_CELEBRATION_ANIMATIONS, true),
            warningAnimations = store.getBoolean(KEY_WARNING_ANIMATIONS, true),
            loadingAnimations = store.getBoolean(KEY_LOADING_ANIMATIONS, true),
            shimmerEffects = store.getBoolean(KEY_SHIMMER_EFFECTS, true),
            adaptivePerformance = store.getBoolean(KEY_ADAPTIVE_PERFORMANCE, true),
            reducedMotion = store.getBoolean(KEY_REDUCED_MOTION, false),
            highContrast = store.getBoolean(KEY_HIGH_CONTRAST, false),
            uiScale = scale,
            birthdayMonth = store.getInt(KEY_BIRTHDAY_MONTH, 0),
            birthdayDay = store.getInt(KEY_BIRTHDAY_DAY, 0),
            setupComplete = store.getBoolean(KEY_SETUP_COMPLETE, false),
            setupStep = store.getInt(KEY_SETUP_STEP, 0).coerceIn(0, 4),
            dailyRewardNotifications = store.getBoolean(KEY_NOTIFY_DAILY, true),
            gameEventNotifications = store.getBoolean(KEY_NOTIFY_EVENTS, true),
            updateNotifications = store.getBoolean(KEY_NOTIFY_UPDATES, true),
            migrationComplete = store.getBoolean(KEY_MIGRATION_COMPLETE, false)
        )
    }
}
