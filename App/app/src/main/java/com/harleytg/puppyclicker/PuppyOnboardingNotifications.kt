package com.harleytg.puppyclicker

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PuppyOnboardingNotifications(
    ui: PuppyUiState,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(PuppyNotificationCenter.canNotify(context))
    }

    val wantsNotifications =
        ui.dailyRewardNotifications ||
            ui.gameEventNotifications ||
            ui.updateNotifications

    val permissionDecision = notificationPermissionDecision(
        sdkInt = Build.VERSION.SDK_INT,
        notificationsEnabled = wantsNotifications,
        permissionGranted = permissionGranted
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        PuppyNotificationCenter.schedule(context)
        if (granted) {
            PuppyNotificationCenter.requestImmediate(context)
        }
        onComplete()
    }

    PuppyOnboardingShell(
        step = PuppyOnboardingStep.NOTIFICATIONS,
        title = "Notifications",
        canGoBack = true,
        primaryLabel = if (permissionDecision == PuppyNotificationPermissionDecision.REQUEST) {
            "Enable Notifications"
        } else {
            "Continue"
        },
        onBack = onBack,
        onPrimary = {
            if (permissionDecision == PuppyNotificationPermissionDecision.REQUEST) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                PuppyNotificationCenter.schedule(context)
                if (
                    permissionGranted ||
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                ) {
                    PuppyNotificationCenter.requestImmediate(context)
                }
                onComplete()
            }
        }
    ) {
        Text(
            "Choose what Puppy Clicker may alert you about.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "You can change these choices later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        NotificationPreferenceCard(
            title = "Daily rewards",
            detail = "Reminders when daily rewards are available.",
            checked = ui.dailyRewardNotifications,
            onCheckedChange = {
                PuppyUiPreferences.setDailyRewardNotifications(context, it)
            }
        )

        Spacer(Modifier.height(8.dp))

        NotificationPreferenceCard(
            title = "Game events",
            detail = "Seasonal and important in-game events.",
            checked = ui.gameEventNotifications,
            onCheckedChange = {
                PuppyUiPreferences.setGameEventNotifications(context, it)
            }
        )

        Spacer(Modifier.height(8.dp))

        NotificationPreferenceCard(
            title = "App updates",
            detail = "Important Puppy Clicker update notices.",
            checked = ui.updateNotifications,
            onCheckedChange = {
                PuppyUiPreferences.setUpdateNotifications(context, it)
            }
        )

        Spacer(Modifier.height(12.dp))

        if (permissionDecision == PuppyNotificationPermissionDecision.REQUEST) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Notification permission", fontWeight = FontWeight.Bold)
                    Text(
                        "Android will ask for permission after you continue. You can skip this and enable it later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(
                onClick = {
                    PuppyNotificationCenter.schedule(context)
                    onComplete()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Not Now")
            }
        } else if (
            wantsNotifications &&
            (permissionGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "✓ Notifications are ready.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NotificationPreferenceCard(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
