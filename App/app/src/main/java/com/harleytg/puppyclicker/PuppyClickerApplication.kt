package com.harleytg.puppyclicker

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Shared foreground state used to stop live production while the app is away. */
object PuppyAppRuntime {
    @Volatile
    var isForeground: Boolean = false
}

class PuppyClickerApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0
    private var welcomeVisible = false

    private val prefs by lazy {
        getSharedPreferences(PuppyClickerV5ViewModel.PREFS_NAME, MODE_PRIVATE)
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val externalSaveWriter = Runnable {
        startupSafely("background PupEye seal") { PupEyeSaveGuard.seal(this, prefs) }
        startupSafely("background Android/data save") { ExternalGameSave.write(this, prefs) }
    }
    private val saveChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Mark this change as an authorized in-process save before the debounced
        // encrypted seal catches up. PupEye still detects edits made outside this path.
        PupEyeSaveGuard.noteAuthorizedPreferenceChange(this)
        // One saveState() changes many keys. Debounce those callbacks into one encrypted write/seal.
        mainHandler.removeCallbacks(externalSaveWriter)
        mainHandler.postDelayed(externalSaveWriter, EXTERNAL_SAVE_DEBOUNCE_MS)
    }

    override fun onCreate() {
        super.onCreate()

        // Optional protection/mirroring features must never make the game process unlaunchable.
        // If a vendor Keystore, external storage provider, or old cached file is broken, the
        // internal SharedPreferences save remains usable and the app continues to the UI.
        startupSafely("PupEye save verification") {
            PupEyeSaveGuard.verifyAndRecover(this, prefs)
        }

        // Older releases and legacy JSON imports may contain the right value under a different
        // SharedPreferences numeric type. Normalize those values before V6 constructs its state,
        // otherwise Android's typed getters can throw ClassCastException during first launch.
        startupSafely("legacy save compatibility") {
            PuppySaveCompatibility.normalizeMainSave(prefs)
        }

        registerActivityLifecycleCallbacks(this)
        prefs.registerOnSharedPreferenceChangeListener(saveChangeListener)

        startupSafely("support reporting") { PuppySupportReporting.initialize(this) }
        startupSafely("dynamic puppy roster") { DynamicPuppyRoster.initialize(this) }
        startupSafely("remote feature flags") { PuppyFeatureFlags.initialize(this) }
        startupSafely("monthly rewards stream") { PuppyMonthlyRewards.initialize(this) }
        startupSafely("redeem code stream") { StreamedRedeemCodes.initialize(this) }
        startupSafely("notification history") { PuppyNotificationHistory.initialize(this) }
        startupSafely("Discord auth migration") { DiscordSignupAuth.observe(this) }
        startupSafely("initial Android/data save") { ExternalGameSave.write(this, prefs) }
        startupSafely("notification scheduling") { PuppyNotificationCenter.schedule(this) }

        // If Android killed the process while it was in the background, the timestamp survives
        // and is converted into a pending bounded settlement here.
        startupSafely("AFK reward preparation") { prepareAfkReward(System.currentTimeMillis()) }
        startupSafely("initial PupEye seal") { PupEyeSaveGuard.seal(this, prefs) }
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
            startupSafely("foreground AFK reward preparation") {
                prepareAfkReward(System.currentTimeMillis())
            }
        }
        // Economy V7 routes AFK settlements into the local notification inbox.
        // AfkWelcomeActivity remains packaged only for backward compatibility.
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity.isChangingConfigurations) return
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            PuppyAppRuntime.isForeground = false
            runCatching {
                val setupComplete = getSharedPreferences(
                    PuppyUiPreferences.PREFS_NAME,
                    MODE_PRIVATE
                ).getBoolean("setup_complete", false)

                prefs.edit()
                    .putLong(
                        PuppyClickerV5ViewModel.KEY_AFK_BACKGROUND_AT,
                        if (setupComplete) System.currentTimeMillis() else 0L
                    )
                    .apply()
            }.onFailure { Log.w(TAG, "Unable to store AFK background timestamp", it) }

            mainHandler.removeCallbacks(externalSaveWriter)
            startupSafely("stop PupEye seal") { PupEyeSaveGuard.seal(this, prefs) }
            startupSafely("stop Android/data save") { ExternalGameSave.write(this, prefs) }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is AfkWelcomeActivity) welcomeVisible = false
    }

    private fun prepareAfkReward(now: Long) {
        val setupComplete = getSharedPreferences(
            PuppyUiPreferences.PREFS_NAME,
            MODE_PRIVATE
        ).getBoolean("setup_complete", false)
        if (!setupComplete) {
            prefs.edit()
                .putLong(PuppyClickerV5ViewModel.KEY_AFK_BACKGROUND_AT, 0L)
                .apply()
            return
        }

        // Migrate a claim that the legacy welcome activity had already staged but V6 had not
        // consumed. Recording first and clearing second makes retries safe after process death.
        val legacyClaim = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_CLAIM_READY, 0L)
            .coerceIn(0L, 7_000L)
        if (legacyClaim > 0L) {
            val legacyClaimId = prefs.getString(PuppyAfkPolicy.KEY_CLAIM_SETTLEMENT_ID, null)
                ?.takeIf { it.isNotBlank() }
                ?: "afk:legacy-claim:$legacyClaim"
            if (!recordAfkSystemReward(legacyClaimId, legacyClaim, now)) return
            val cleared = prefs.edit()
                .putLong(PuppyClickerV5ViewModel.KEY_AFK_CLAIM_READY, 0L)
                .remove(PuppyAfkPolicy.KEY_CLAIM_SETTLEMENT_ID)
                .putString(PuppyAfkPolicy.KEY_LAST_SETTLED_ID, legacyClaimId)
                .commit()
            if (cleared) PupEyeSaveGuard.seal(this, prefs)
        }

        // Convert legacy pending AFK rewards directly into the new inbox format.
        val existingPending = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, 0L)
            .coerceIn(0L, 7_000L)
        if (existingPending > 0L) {
            val existingAway = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_AWAY_MS, 0L)
                .coerceIn(0L, PuppyAfkPolicy.MAX_AWAY_MS)
            val existingId = prefs.getString(PuppyAfkPolicy.KEY_PENDING_SETTLEMENT_ID, null)
                ?.takeIf { it.isNotBlank() }
                ?: "afk:legacy-pending:$existingPending:$existingAway"
            if (!recordAfkSystemReward(existingId, existingPending, now)) return
            val cleared = prefs.edit()
                .putLong(PuppyClickerV5ViewModel.KEY_AFK_BACKGROUND_AT, 0L)
                .putLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, 0L)
                .putLong(PuppyClickerV5ViewModel.KEY_AFK_AWAY_MS, 0L)
                .remove(PuppyAfkPolicy.KEY_PENDING_SETTLEMENT_ID)
                .remove(PuppyAfkPolicy.KEY_PENDING_START)
                .remove(PuppyAfkPolicy.KEY_PENDING_END)
                .putString(PuppyAfkPolicy.KEY_LAST_SETTLED_ID, existingId)
                .commit()
            if (cleared) PupEyeSaveGuard.seal(this, prefs)
            return
        }

        val backgroundAt = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_BACKGROUND_AT, 0L)
        if (backgroundAt <= 0L) return
        val settlement = PuppyAfkPolicy.prepare(backgroundAt, now)
        if (settlement == null || settlement.earnedTreats <= 0L) {
            prefs.edit()
                .putLong(PuppyClickerV5ViewModel.KEY_AFK_BACKGROUND_AT, 0L)
                .apply()
            return
        }

        val lastSettledId = prefs.getString(PuppyAfkPolicy.KEY_LAST_SETTLED_ID, null)
        if (settlement.settlementId != lastSettledId) {
            if (
                !recordAfkSystemReward(
                    settlementId = settlement.settlementId,
                    amount = settlement.earnedTreats,
                    createdAtMs = now
                )
            ) return
        }

        val committed = prefs.edit()
            .putLong(PuppyClickerV5ViewModel.KEY_AFK_BACKGROUND_AT, 0L)
            .putLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, 0L)
            .putLong(PuppyClickerV5ViewModel.KEY_AFK_AWAY_MS, 0L)
            .remove(PuppyAfkPolicy.KEY_PENDING_SETTLEMENT_ID)
            .remove(PuppyAfkPolicy.KEY_PENDING_START)
            .remove(PuppyAfkPolicy.KEY_PENDING_END)
            .putString(PuppyAfkPolicy.KEY_LAST_SETTLED_ID, settlement.settlementId)
            .commit()
        if (committed) PupEyeSaveGuard.seal(this, prefs)
    }

    private fun recordAfkSystemReward(
        settlementId: String,
        amount: Long,
        createdAtMs: Long
    ): Boolean {
        val recorded = PuppyNotificationHistory.recordSystemReward(
            context = this,
            id = settlementId,
            title = "Your puppies saved some Treats!",
            body = "Welcome back. Claim your saved Treats from this system message.",
            currency = PuppyRewardCurrency.TREATS,
            amount = amount,
            createdAtMs = createdAtMs
        ) ?: return false

        if (recorded.hasClaimableReward) {
            PuppyNotificationCenter.notifySystemRewardAvailable(
                context = this,
                settlementId = recorded.id,
                amount = recorded.rewardAmount
            )
        }
        return true
    }

    private fun maybeShowWelcome(activity: Activity) {
        if (welcomeVisible || activity is AfkWelcomeActivity) return
        val pending = runCatching {
            prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, 0L)
        }.getOrDefault(0L)
        if (pending <= 0L) return

        welcomeVisible = true
        runCatching {
            activity.startActivity(Intent(activity, AfkWelcomeActivity::class.java))
        }.onFailure {
            welcomeVisible = false
            Log.w(TAG, "Unable to show AFK welcome activity", it)
        }
    }

    private inline fun startupSafely(label: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            Log.e(TAG, "$label failed; continuing app startup", error)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    companion object {
        private const val TAG = "PuppyClickerStartup"
        const val AFK_TREATS_PER_DAY = PuppyAfkPolicy.TREATS_PER_DAY
        const val DAY_MS = PuppyAfkPolicy.DAY_MS
        private const val EXTERNAL_SAVE_DEBOUNCE_MS = 300L
    }
}
