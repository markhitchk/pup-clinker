package com.harleytg.puppyclicker

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** Shared foreground state used to stop live production while the app is away. */
object PuppyAppRuntime {
    @Volatile
    var isForeground: Boolean = true
}

class PuppyClickerApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0
    private var welcomeVisible = false

    private val prefs by lazy {
        getSharedPreferences(PuppyClickerV5ViewModel.PREFS_NAME, MODE_PRIVATE)
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val externalSaveWriter = Runnable { ExternalGameSave.write(this, prefs) }
    private val saveChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // One saveState() changes many keys. Debounce those callbacks into one external write.
        mainHandler.removeCallbacks(externalSaveWriter)
        mainHandler.postDelayed(externalSaveWriter, EXTERNAL_SAVE_DEBOUNCE_MS)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        prefs.registerOnSharedPreferenceChangeListener(saveChangeListener)
        DynamicPuppyRoster.initialize(this)
        StreamedRedeemCodes.initialize(this)

        // Ensure a readable save snapshot exists in emulated Android/data storage.
        ExternalGameSave.write(this, prefs)

        // If Android killed the process while it was in the background, the
        // timestamp survives and is converted into a pending reward here.
        prepareAfkReward(System.currentTimeMillis())
    }

    override fun onActivityStarted(activity: Activity) {
        val returningFromBackground = startedActivities == 0
        startedActivities++
        PuppyAppRuntime.isForeground = true

        if (activity is AfkWelcomeActivity) {
            welcomeVisible = true
            return
        }

        if (returningFromBackground) {
            prepareAfkReward(System.currentTimeMillis())
        }
        maybeShowWelcome(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity.isChangingConfigurations) return
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            PuppyAppRuntime.isForeground = false
            prefs.edit()
                .putLong(PuppyClickerV5ViewModel.KEY_AFK_BACKGROUND_AT, System.currentTimeMillis())
                .apply()

            // Flush the most recent persisted state into the user-requested
            // app-specific emulated-storage location when leaving the app.
            mainHandler.removeCallbacks(externalSaveWriter)
            ExternalGameSave.write(this, prefs)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is AfkWelcomeActivity) welcomeVisible = false
    }

    private fun prepareAfkReward(now: Long) {
        val backgroundAt = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_BACKGROUND_AT, 0L)
        if (backgroundAt <= 0L || now <= backgroundAt) return

        val awayMs = now - backgroundAt
        val fullDays = awayMs / DAY_MS
        val remainder = awayMs % DAY_MS
        val earned = safeAdd(
            safeMultiply(fullDays, AFK_TREATS_PER_DAY),
            safeMultiply(remainder, AFK_TREATS_PER_DAY) / DAY_MS
        )

        val existingPending = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, 0L).coerceAtLeast(0L)
        val existingAway = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_AWAY_MS, 0L).coerceAtLeast(0L)

        prefs.edit()
            .putLong(PuppyClickerV5ViewModel.KEY_AFK_BACKGROUND_AT, 0L)
            .putLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, safeAdd(existingPending, earned))
            .putLong(PuppyClickerV5ViewModel.KEY_AFK_AWAY_MS, safeAdd(existingAway, awayMs))
            .apply()
    }

    private fun maybeShowWelcome(activity: Activity) {
        if (welcomeVisible || activity is AfkWelcomeActivity) return
        val pending = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, 0L)
        if (pending <= 0L) return

        welcomeVisible = true
        activity.startActivity(Intent(activity, AfkWelcomeActivity::class.java))
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    private fun safeMultiply(a: Long, b: Long): Long = when {
        a <= 0L || b <= 0L -> 0L
        a > Long.MAX_VALUE / b -> Long.MAX_VALUE
        else -> a * b
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    companion object {
        const val AFK_TREATS_PER_DAY = 1_000L
        const val DAY_MS = 24L * 60L * 60L * 1_000L
        private const val EXTERNAL_SAVE_DEBOUNCE_MS = 300L
    }
}
