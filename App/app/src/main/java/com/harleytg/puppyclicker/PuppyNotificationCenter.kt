package com.harleytg.puppyclicker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class PuppyReleaseUpdate(
    val versionCode: Int,
    val versionName: String,
    val releaseName: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val unread: Boolean
)

internal object PuppyNotificationCenter {
    private const val CHANNEL_REWARDS = "puppy_rewards_v1"
    private const val CHANNEL_EVENTS = "puppy_events_v1"
    private const val CHANNEL_UPDATES = "puppy_updates_v1"

    private const val NOTIFY_DAILY = 42101
    private const val NOTIFY_EVENT = 42102
    private const val NOTIFY_UPDATE = 42103
    private const val NOTIFY_ROSTER = 42104
    private const val NOTIFY_TEST = 42105
    private const val NOTIFY_DISCORD_AUTH_UPGRADE = 42106
    private const val NOTIFY_SYSTEM_REWARD_BASE = 42200
    private const val NOTIFY_SEASONAL_BASE = 42400

    private const val WORK_SWEEP = "puppy_notification_sweep_v1"
    private const val WORK_NOW = "puppy_notification_now_v1"
    private const val WORK_PARK = "puppy_park_ready_v1"

    private const val DELIVERY_PREFS = "puppy_notification_delivery_v1"
    private const val KEY_DAILY_DAY = "daily_day"
    private const val KEY_PARK_READY_AT = "park_ready_at"
    private const val KEY_UPDATE_VERSION = "update_version"
    private const val KEY_UPDATE_CHECKED_AT = "update_checked_at"
    private const val KEY_CACHED_UPDATE_VERSION = "cached_update_version"
    private const val KEY_CACHED_UPDATE_NAME = "cached_update_name"
    private const val KEY_CACHED_UPDATE_RELEASE_NAME = "cached_update_release_name"
    private const val KEY_CACHED_UPDATE_NOTES = "cached_update_notes"
    private const val KEY_CACHED_UPDATE_URL = "cached_update_url"
    private const val KEY_CACHED_UPDATE_APK_URL = "cached_update_apk_url"
    private const val KEY_CACHED_UPDATE_UNREAD = "cached_update_unread"

    private const val UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/markhitchk/pup-clinker/releases/latest"

    private val _updateNotice = MutableStateFlow<PuppyReleaseUpdate?>(null)
    val updateNotice: StateFlow<PuppyReleaseUpdate?> = _updateNotice.asStateFlow()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_REWARDS,
                    "Puppy Rewards",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily gifts and claimable Puppy Clicker reward reminders."
                },
                NotificationChannel(
                    CHANNEL_EVENTS,
                    "Game Events",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Adventure and Puppy Clicker game-event alerts."
                },
                NotificationChannel(
                    CHANNEL_UPDATES,
                    "App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications when a newer Puppy Clicker build is available."
                }
            )
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context) {
        val app = context.applicationContext
        createChannels(app)
        val request = PeriodicWorkRequestBuilder<PuppyNotificationSweepWorker>(
            15,
            TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            WORK_SWEEP,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun requestImmediate(context: Context) {
        val app = context.applicationContext
        val request = OneTimeWorkRequestBuilder<PuppyNotificationSweepWorker>().build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            WORK_NOW,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleParkReady(context: Context, readyAtMs: Long) {
        val app = context.applicationContext
        val delayMs = (readyAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<PuppyParkReadyWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            WORK_PARK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelDailyReward(context: Context) {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            .cancel(NOTIFY_DAILY)
    }

    fun cancelParkReady(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).cancelUniqueWork(WORK_PARK)
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFY_EVENT)
    }

    fun cancelAppUpdate(context: Context) {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            .cancel(NOTIFY_UPDATE)
    }

    fun loadCachedUpdate(context: Context) {
        _updateNotice.value = readCachedUpdate(context.applicationContext)
    }

    fun markUpdateRead(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CACHED_UPDATE_UNREAD, false)
            .apply()
        _updateNotice.value = _updateNotice.value?.copy(unread = false)
    }

    suspend fun refreshUpdateStatus(
        context: Context,
        force: Boolean = false
    ): PuppyReleaseUpdate? {
        val app = context.applicationContext
        val delivery = app.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastChecked = delivery.getLong(KEY_UPDATE_CHECKED_AT, 0L)

        if (!force && now - lastChecked in 0 until UPDATE_CHECK_INTERVAL_MS) {
            return readCachedUpdate(app).also { _updateNotice.value = it }
        }

        delivery.edit().putLong(KEY_UPDATE_CHECKED_AT, now).apply()

        val release = withContext(Dispatchers.IO) {
            fetchLatestRelease()
        } ?: return readCachedUpdate(app).also { _updateNotice.value = it }

        if (release.versionCode <= BuildConfig.VERSION_CODE) {
            clearCachedUpdate(app)
            cancelAppUpdate(app)
            return null
        }

        val previousVersion = delivery.getInt(KEY_CACHED_UPDATE_VERSION, 0)
        val wasUnread = delivery.getBoolean(KEY_CACHED_UPDATE_UNREAD, false)
        val update = release.copy(
            unread = previousVersion != release.versionCode || wasUnread
        )

        delivery.edit()
            .putInt(KEY_CACHED_UPDATE_VERSION, update.versionCode)
            .putString(KEY_CACHED_UPDATE_NAME, update.versionName)
            .putString(KEY_CACHED_UPDATE_RELEASE_NAME, update.releaseName)
            .putString(KEY_CACHED_UPDATE_NOTES, update.notes)
            .putString(KEY_CACHED_UPDATE_URL, update.releaseUrl)
            .putString(KEY_CACHED_UPDATE_APK_URL, update.apkUrl.orEmpty())
            .putBoolean(KEY_CACHED_UPDATE_UNREAD, update.unread)
            .apply()

        _updateNotice.value = update
        return update
    }

    internal fun notifyRosterUpdated(
        context: Context,
        addedNames: List<String>,
        removedCount: Int,
        changedCount: Int
    ) {
        val app = context.applicationContext
        createChannels(app)
        if (!canNotify(app)) return
        if (!PuppyUiPreferences.current(app).gameEventNotifications) return

        val title = when (addedNames.size) {
            0 -> "Puppy roster updated"
            1 -> "New puppy added to the roster"
            else -> addedNames.size.toString() + " new puppies added"
        }
        val text = when {
            addedNames.size == 1 && removedCount == 0 && changedCount == 0 ->
                addedNames.first() + " joined the Puppy Clicker roster."
            addedNames.isNotEmpty() -> {
                val preview = addedNames.take(3).joinToString(", ")
                val more = if (addedNames.size > 3) " +" + (addedNames.size - 3) + " more" else ""
                "New roster puppies: " + preview + more + "."
            }
            else ->
                "The Puppy Clicker roster changed. Open the app to see the latest roster."
        }

        post(
            context = app,
            channel = CHANNEL_EVENTS,
            id = NOTIFY_ROSTER,
            title = title,
            text = text
        )
    }

    fun postTestNotification(context: Context) {
        val app = context.applicationContext
        createChannels(app)
        if (!canNotify(app)) return
        post(
            context = app,
            channel = CHANNEL_EVENTS,
            id = NOTIFY_TEST,
            title = "Puppy Clicker notifications are working 🐾",
            text = "This is a test notification from Puppy Clicker."
        )
    }

    internal fun notifyDiscordAuthUpgrade(context: Context) {
        val app = context.applicationContext
        createChannels(app)
        if (!canNotify(app)) return
        if (!PuppyUiPreferences.current(app).updateNotifications) return
        post(
            context = app,
            channel = CHANNEL_UPDATES,
            id = NOTIFY_DISCORD_AUTH_UPGRADE,
            title = "Discord verification updated",
            text = "Re-authorize Discord in Settings to verify your server role and unlock Discord Pup."
        )
    }

    internal fun notifySeasonalPuppyUnlocked(
        context: Context,
        event: SeasonalPuppyEvent,
        window: SeasonalWindow
    ) {
        val app = context.applicationContext
        val historyId = "seasonal-unlocked:${window.cycle}"
        val body = if (event.isBirthday) {
            "Happy birthday! ${event.title} was automatically added to your puppy collection."
        } else {
            "${event.description} is here. ${event.title} was automatically added to your puppy collection."
        }

        PuppyNotificationHistory.record(
            app,
            PuppyNotificationItem(
                id = historyId,
                type = PuppyNotificationType.ROSTER_UPDATE,
                title = "${event.emoji} ${event.title} unlocked!",
                body = body,
                createdAtMs = System.currentTimeMillis(),
                read = false,
                route = PuppyNotificationRoute.ROSTER
            )
        )

        if (PuppyAppRuntime.isForeground || !canNotify(app)) return
        if (!PuppyUiPreferences.current(app).gameEventNotifications) return
        val suffix = (window.cycle.hashCode() and 0x7fffffff) % 500
        post(
            context = app,
            channel = CHANNEL_EVENTS,
            id = NOTIFY_SEASONAL_BASE + suffix,
            title = "${event.emoji} ${event.title} unlocked!",
            text = body
        )
    }
    internal fun notifySystemRewardAvailable(
        context: Context,
        settlementId: String,
        amount: Long
    ) {
        val app = context.applicationContext
        createChannels(app)
        if (PuppyAppRuntime.isForeground || !canNotify(app)) return
        if (!PuppyUiPreferences.current(app).dailyRewardNotifications) return
        if (amount <= 0L || settlementId.isBlank()) return

        val suffix = (settlementId.hashCode() and 0x7fffffff) % 700
        post(
            context = app,
            channel = CHANNEL_REWARDS,
            id = NOTIFY_SYSTEM_REWARD_BASE + suffix,
            title = "Your puppies saved some Treats!",
            text = "Claim " + amount + " saved Treats from the Puppy Clicker notification inbox."
        )
    }

    internal suspend fun runSweep(context: Context) {
        val app = context.applicationContext
        createChannels(app)

        // WorkManager wakes periodically even when the activity is not open. The roster
        // itself enforces the one-hour network throttle, so this keeps GitHub roster
        // updates discoverable in the background without polling every sweep.
        withContext(Dispatchers.IO) {
            DynamicPuppyRoster.refreshIfDue(app)
        }

        val ui = PuppyUiPreferences.current(app)
        if (ui.updateNotifications) checkForAppUpdate(app) else cancelAppUpdate(app)

        if (PuppyAppRuntime.isForeground || !canNotify(app)) return

        if (ui.dailyRewardNotifications) postDailyRewardIfAvailable(app) else cancelDailyReward(app)
        if (ui.gameEventNotifications) {
            postParkEventIfReady(app)
            postSeasonalUnlockIfActive(app)
        } else {
            cancelParkReady(app)
        }
    }

    internal fun postSeasonalUnlockIfActive(context: Context) {
        if (PuppyAppRuntime.isForeground || !canNotify(context)) return
        if (!PuppyUiPreferences.current(context).gameEventNotifications) return

        val seasonal = context.getSharedPreferences(
            SeasonalPuppyStore.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val birthday = SeasonalPuppyEvents.birthday(
            seasonal.getInt(SeasonalPuppyStore.KEY_BIRTHDAY_MONTH, 0),
            seasonal.getInt(SeasonalPuppyStore.KEY_BIRTHDAY_DAY, 0)
        )
        val unlocked = context.getSharedPreferences(
            PuppyClickerV6ViewModel.PREFS_NAME,
            Context.MODE_PRIVATE
        ).getStringSet("unlocked_puppies", emptySet())?.toSet().orEmpty()

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val delivery = context.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)

        SeasonalPuppyEvents.events.forEach { event ->
            if (event.puppyId in unlocked) return@forEach
            val window = SeasonalPuppyEvents.activeWindow(event, now, zone, birthday)
                ?: return@forEach
            val deliveryKey = "seasonal-ready:${window.cycle}"
            if (delivery.getBoolean(deliveryKey, false)) return@forEach

            val suffix = (window.cycle.hashCode() and 0x7fffffff) % 500
            val text = if (event.isBirthday) {
                "Happy birthday! Open Puppy Clicker today and ${event.title} will unlock automatically."
            } else {
                "${event.description} is here. Open Puppy Clicker today and ${event.title} will unlock automatically."
            }
            post(
                context = context,
                channel = CHANNEL_EVENTS,
                id = NOTIFY_SEASONAL_BASE + suffix,
                title = "${event.emoji} ${event.title} unlock day!",
                text = text
            )
            delivery.edit().putBoolean(deliveryKey, true).apply()
        }
    }

    internal fun postParkEventIfReady(context: Context) {
        if (PuppyAppRuntime.isForeground || !canNotify(context)) return
        val ui = PuppyUiPreferences.current(context)
        if (!ui.gameEventNotifications) return

        val game = context.getSharedPreferences(
            PuppyClickerV6ViewModel.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val active = game.getBoolean("park_active", false)
        val readyAt = game.getLong("park_ready_at", 0L)
        val now = System.currentTimeMillis()
        if (!active || readyAt <= 0L || now < readyAt) return

        val delivery = context.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)
        if (delivery.getLong(KEY_PARK_READY_AT, 0L) == readyAt) return

        post(
            context = context,
            channel = CHANNEL_EVENTS,
            id = NOTIFY_EVENT,
            title = "Dog Park Adventure is ready",
            text = "Your puppy is back. Open Puppy Clicker to collect the adventure reward."
        )
        delivery.edit().putLong(KEY_PARK_READY_AT, readyAt).apply()
    }

    private fun postDailyRewardIfAvailable(context: Context) {
        val game = context.getSharedPreferences(
            PuppyClickerV6ViewModel.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val today = LocalDate.now().toEpochDay()
        if (game.getLong("daily_claim_day", Long.MIN_VALUE) == today) return

        val delivery = context.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)
        if (delivery.getLong(KEY_DAILY_DAY, Long.MIN_VALUE) == today) return

        post(
            context = context,
            channel = CHANNEL_REWARDS,
            id = NOTIFY_DAILY,
            title = "Daily Puppy Reward available",
            text = "Your daily Puppy Clicker reward is ready to claim."
        )
        delivery.edit().putLong(KEY_DAILY_DAY, today).apply()
    }

    private suspend fun checkForAppUpdate(context: Context) {
        val update = refreshUpdateStatus(context) ?: return
        if (PuppyAppRuntime.isForeground || !canNotify(context)) return

        val delivery = context.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)
        if (delivery.getInt(KEY_UPDATE_VERSION, 0) == update.versionCode) return

        post(
            context = context,
            channel = CHANNEL_UPDATES,
            id = NOTIFY_UPDATE,
            title = "Puppy Clicker update available",
            text = buildString {
                append(update.releaseName)
                if (update.versionName.isNotBlank()) {
                    append(" · ")
                    append(update.versionName)
                }
            }
        )
        delivery.edit().putInt(KEY_UPDATE_VERSION, update.versionCode).apply()
    }

    private fun readCachedUpdate(context: Context): PuppyReleaseUpdate? {
        val delivery = context.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)
        val versionCode = delivery.getInt(KEY_CACHED_UPDATE_VERSION, 0)
        if (versionCode <= BuildConfig.VERSION_CODE) return null

        val releaseUrl = delivery.getString(KEY_CACHED_UPDATE_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return PuppyReleaseUpdate(
            versionCode = versionCode,
            versionName = delivery.getString(KEY_CACHED_UPDATE_NAME, "").orEmpty(),
            releaseName = delivery.getString(KEY_CACHED_UPDATE_RELEASE_NAME, "Puppy Clicker update").orEmpty(),
            notes = delivery.getString(KEY_CACHED_UPDATE_NOTES, "").orEmpty(),
            releaseUrl = releaseUrl,
            apkUrl = delivery.getString(KEY_CACHED_UPDATE_APK_URL, "")
                .orEmpty()
                .takeIf { it.isNotBlank() },
            unread = delivery.getBoolean(KEY_CACHED_UPDATE_UNREAD, false)
        )
    }

    private fun clearCachedUpdate(context: Context) {
        context.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CACHED_UPDATE_VERSION)
            .remove(KEY_CACHED_UPDATE_NAME)
            .remove(KEY_CACHED_UPDATE_RELEASE_NAME)
            .remove(KEY_CACHED_UPDATE_NOTES)
            .remove(KEY_CACHED_UPDATE_URL)
            .remove(KEY_CACHED_UPDATE_APK_URL)
            .remove(KEY_CACHED_UPDATE_UNREAD)
            .apply()
        _updateNotice.value = null
    }

    private fun fetchLatestRelease(): PuppyReleaseUpdate? {
        return runCatching {
            val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 7_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "PuppyClicker-Android")
                connection.setRequestProperty("Accept", "application/vnd.github+json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@runCatching null
                if (responseCode !in 200..299) return@runCatching null

                val json = connection.inputStream.bufferedReader().use { it.readText() }
                parseLatestRelease(json)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    internal fun parseLatestRelease(json: String): PuppyReleaseUpdate? {
        val release = JSONObject(json)
        if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
            return null
        }

        val notes = release.optString("body", "")
        val versionCode = Regex(
            """(?im)^\s*[-*]?\s*(?:version\s*code|versionCode|build)\s*[:=]\s*(\d+)\s*$"""
        ).find(notes)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null

        val tag = release.optString("tag_name", "").trim()
        val versionName = tag.removePrefix("v").removePrefix("V")
        val releaseUrl = release.optString("html_url", "").trim()
        if (releaseUrl.isBlank()) return null

        val assets = release.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name", "")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                apkUrl = asset.optString("browser_download_url", "")
                    .trim()
                    .takeIf { it.isNotBlank() }
                if (apkUrl != null) break
            }
        }

        return PuppyReleaseUpdate(
            versionCode = versionCode,
            versionName = versionName,
            releaseName = release.optString("name", "")
                .trim()
                .ifBlank { if (versionName.isBlank()) "Puppy Clicker update" else "Puppy Clicker $versionName" },
            notes = notes.trim(),
            releaseUrl = releaseUrl,
            apkUrl = apkUrl,
            unread = false
        )
    }

    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String
    ) {
        if (!canNotify(context)) return
        val openApp = PendingIntent.getActivity(
            context,
            id + 100,
            Intent(context, PuppyClickerV6Activity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.source_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup("puppy_clicker")
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}

class PuppyNotificationSweepWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            PuppyNotificationCenter.runSweep(applicationContext)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

class PuppyParkReadyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            PuppyNotificationCenter.postParkEventIfReady(applicationContext)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
