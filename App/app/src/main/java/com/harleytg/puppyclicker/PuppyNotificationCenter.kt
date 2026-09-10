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
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PuppyNotificationCenter {
    private const val CHANNEL_REWARDS = "puppy_rewards_v1"
    private const val CHANNEL_EVENTS = "puppy_events_v1"
    private const val CHANNEL_UPDATES = "puppy_updates_v1"

    private const val NOTIFY_DAILY = 42101
    private const val NOTIFY_EVENT = 42102
    private const val NOTIFY_UPDATE = 42103

    private const val WORK_SWEEP = "puppy_notification_sweep_v1"
    private const val WORK_NOW = "puppy_notification_now_v1"
    private const val WORK_PARK = "puppy_park_ready_v1"

    private const val DELIVERY_PREFS = "puppy_notification_delivery_v1"
    private const val KEY_DAILY_DAY = "daily_day"
    private const val KEY_PARK_READY_AT = "park_ready_at"
    private const val KEY_UPDATE_VERSION = "update_version"
    private const val KEY_UPDATE_CHECKED_AT = "update_checked_at"

    private const val UPDATE_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val REMOTE_BUILD =
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/App/app/build.gradle.kts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_REWARDS,
                    "Daily Rewards",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily Puppy Clicker reward reminders."
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

    fun cancelParkReady(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).cancelUniqueWork(WORK_PARK)
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFY_EVENT)
    }

    internal suspend fun runSweep(context: Context) {
        val app = context.applicationContext
        createChannels(app)
        if (PuppyAppRuntime.isForeground || !canNotify(app)) return

        val ui = PuppyUiPreferences.current(app)
        if (ui.dailyRewardNotifications) postDailyRewardIfAvailable(app)
        if (ui.gameEventNotifications) postParkEventIfReady(app)
        if (ui.updateNotifications) checkForAppUpdate(app)
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
        val delivery = context.getSharedPreferences(DELIVERY_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastChecked = delivery.getLong(KEY_UPDATE_CHECKED_AT, 0L)
        if (now - lastChecked in 0 until UPDATE_CHECK_INTERVAL_MS) return

        delivery.edit().putLong(KEY_UPDATE_CHECKED_AT, now).apply()
        val remoteVersion = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(REMOTE_BUILD).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 7_000
                    connection.readTimeout = 8_000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "PuppyClicker-Android")
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    Regex("""versionCode\s*=\s*(\d+)""")
                        .find(text)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        } ?: return

        if (remoteVersion <= BuildConfig.VERSION_CODE) return
        if (delivery.getInt(KEY_UPDATE_VERSION, 0) == remoteVersion) return

        post(
            context = context,
            channel = CHANNEL_UPDATES,
            id = NOTIFY_UPDATE,
            title = "Puppy Clicker update available",
            text = "A newer Puppy Clicker build is available."
        )
        delivery.edit().putInt(KEY_UPDATE_VERSION, remoteVersion).apply()
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
