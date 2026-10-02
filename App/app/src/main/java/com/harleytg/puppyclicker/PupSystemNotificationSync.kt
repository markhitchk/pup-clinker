package com.harleytg.puppyclicker

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pulls authenticated developer/system messages into the existing Puppy Clicker inbox.
 *
 * The server is authoritative for message delivery. Read/unread state stays local so opening a
 * notification never requires a network round-trip. Message IDs are stable and the local history
 * codec de-duplicates retries.
 */
internal object PupSystemNotificationSync {
    private const val FOREGROUND_REFRESH_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)

    fun start(context: Context) {
        val app = context.applicationContext
        if (!SupabasePupEyeClient.isConfigured()) return
        if (!started.compareAndSet(false, true)) return

        scope.launch {
            while (true) {
                if (PuppyAppRuntime.isForeground && PuppyUiPreferences.current(app).setupComplete) {
                    runCatching { refresh(app, postAndroidNotification = false) }
                }
                delay(FOREGROUND_REFRESH_MS)
            }
        }
    }

    suspend fun refresh(
        context: Context,
        postAndroidNotification: Boolean
    ) {
        val app = context.applicationContext
        if (!SupabasePupEyeClient.isConfigured()) return
        if (!PuppyUiPreferences.current(app).setupComplete) return
        if (!refreshing.compareAndSet(false, true)) return

        try {
            val messages = SupabasePupEyeClient.readSystemNotifications(app)
            messages.asReversed().forEach { message ->
                val localId = "server:${message.id}"
                val existed = PuppyNotificationHistory.findById(app, localId) != null
                PuppyNotificationHistory.record(
                    app,
                    PuppyNotificationItem(
                        id = localId,
                        type = PuppyNotificationType.DEVELOPER,
                        title = message.title,
                        body = message.body,
                        createdAtMs = message.createdAtMs,
                        read = false,
                        route = PuppyNotificationRoute.NONE
                    )
                )
                if (!existed && postAndroidNotification && !PuppyAppRuntime.isForeground) {
                    PuppyNotificationCenter.notifyDeveloperSystemMessage(
                        context = app,
                        messageId = message.id,
                        title = message.title,
                        body = message.body
                    )
                }
            }
        } finally {
            refreshing.set(false)
        }
    }
}
