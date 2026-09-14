package com.harleytg.puppyclicker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PuppyUpdateBellButton(
    hasUnread: Boolean,
    onClick: () -> Unit
) {
    Box {
        OutlinedIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Open notifications" }
        ) {
            Text("🔔", fontSize = 18.sp)
        }

        if (hasUnread) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
            )
        }
    }
}

@Composable
internal fun PuppySettingsHeaderButton(onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = "Open settings" }
    ) {
        Text("⚙️", fontSize = 18.sp)
    }
}

@Composable
internal fun PuppyNotificationsDialog(
    update: PuppyReleaseUpdate?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notifications", fontWeight = FontWeight.Black) },
        text = {
            Column {
                if (update == null) {
                    Text(
                        "You're all caught up. No Puppy Clicker update is currently available.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Update available",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                update.releaseName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                if (update.versionName.isNotBlank()) {
                                    "Version ${update.versionName} · Build ${update.versionCode}"
                                } else {
                                    "Build ${update.versionCode}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (update.notes.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    update.notes.take(1_400),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check for updates")
                }
            }
        },
        confirmButton = {
            if (update != null) {
                Button(onClick = onUpdate) {
                    Text(if (update.apkUrl != null) "Update" else "Open release")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (update != null) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}

internal fun openPuppyUpdateUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
