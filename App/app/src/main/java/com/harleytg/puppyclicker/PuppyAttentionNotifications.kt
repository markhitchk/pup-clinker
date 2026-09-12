package com.harleytg.puppyclicker

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal object PuppyAttentionNotifier {
    const val PREF_ENABLED = "setting_afk_notifications"
    private const val CHANNEL_ID = "puppy_attention_v1"
    private const val NOTIFICATION_ID = 41021
    private const val REQUEST_CODE = 41022
    private const val ACTION_ATTENTION = "com.harleytg.puppyclicker.PUPPY_ATTENTION"
    private const val FIRST_REMINDER_MS = 2L * 60L * 60L * 1000L
    private const val REMINDER_INTERVAL_MS = 2L * 60L * 60L * 1000L

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Puppy Attention",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Optional reminders from your puppy while Puppy Clicker is AFK."
        }
        manager.createNotificationChannel(channel)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ENABLED, enabled)
            .apply()
        if (!enabled) cancel(context)
    }

    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        createChannel(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val operation = attentionPendingIntent(context)
        alarm.cancel(operation)
        alarm.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + FIRST_REMINDER_MS,
            REMINDER_INTERVAL_MS,
            operation
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(attentionPendingIntent(context))
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun postAttention(context: Context) {
        if (!isEnabled(context) || !canNotify(context)) return
        val prefs = context.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        val backgroundAt = prefs.getLong(PuppySaveContract.KEY_AFK_BACKGROUND_AT, 0L)
        val now = System.currentTimeMillis()
        if (backgroundAt <= 0L || now - backgroundAt < FIRST_REMINDER_MS) return

        val puppyName = prefs.getString("puppy_name", "Buddy")?.takeIf { it.isNotBlank() } ?: "Buddy"
        val happiness = prefs.getInt("happiness", 100)
        val fullness = prefs.getInt("fullness", 100)
        val cleanliness = prefs.getInt("cleanliness_v5", 100)
        val needHint = when (minOf(happiness, fullness, cleanliness)) {
            in 0..34 -> "$puppyName says this is now an urgent snack/cuddle situation."
            in 35..64 -> "$puppyName would like some care, snacks, or play time."
            else -> "$puppyName is waiting for attention and has noticed you are AFK."
        }
        val titles = listOf(
            "🐾 Human? Attention required.",
            "🐶 Your puppy is looking for you",
            "🎾 AFK detected: puppy demands playtime",
            "🍪 A very serious puppy request"
        )
        val title = titles[((now / REMINDER_INTERVAL_MS) % titles.size).toInt()]

        val openGame = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            Intent(context, PuppyClickerV6Activity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.source_logo)
            .setContentTitle(title)
            .setContentText(needHint)
            .setStyle(NotificationCompat.BigTextStyle().bigText(needHint))
            .setContentIntent(openGame)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun attentionPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, PuppyAttentionReceiver::class.java).setAction(ACTION_ATTENTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class PuppyAttentionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PuppyAttentionNotifier.postAttention(context.applicationContext)
    }
}

@Composable
internal fun PuppyAttentionLifecycle() {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, context) {
        PuppyAttentionNotifier.createChannel(context)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> PuppyAttentionNotifier.cancel(context)
                Lifecycle.Event.ON_STOP -> PuppyAttentionNotifier.schedule(context)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun PuppyAttentionSettings() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(PuppyAttentionNotifier.isEnabled(context)) }
    var permissionGranted by remember { mutableStateOf(PuppyAttentionNotifier.canNotify(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔔", fontSize = 24.sp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("AFK puppy attention", fontWeight = FontWeight.Bold)
                    Text(
                        "Your puppy can nag its human about every 2 hours while the app is away.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { value ->
                        enabled = value
                        PuppyAttentionNotifier.setEnabled(context, value)
                        if (value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
            if (enabled && !permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "Android notification permission is still required.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow puppy notifications")
                }
            }
        }
    }
}
