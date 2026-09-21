package com.harleytg.puppyclicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PuppyNotificationInboxDialog(
    items: List<PuppyNotificationItem>,
    onDismiss: () -> Unit,
    onOpenItem: (PuppyNotificationItem) -> Unit,
    onClaimReward: (PuppyNotificationItem) -> Unit,
    onMarkAllRead: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Notifications", fontWeight = FontWeight.Black)
                val unread = items.count { !it.read }
                if (unread > 0) Badge { Text(unread.toString()) }
            }
        },
        text = {
            if (items.isEmpty()) {
                Text("You're all caught up. New Puppy Clicker alerts will appear here.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onOpenItem(item) }
                                .semantics {
                                    contentDescription = buildString {
                                        if (!item.read) append("Unread. ")
                                        append(item.title)
                                        if (item.body.isNotBlank()) append(". ${item.body}")
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.read) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                }
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        item.title,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = if (item.read) FontWeight.Bold else FontWeight.Black
                                    )
                                    if (!item.read) Text("NEW", style = MaterialTheme.typography.labelSmall)
                                }
                                if (item.body.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        item.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (
                                    item.type == PuppyNotificationType.SYSTEM_REWARD &&
                                    item.rewardCurrency != null &&
                                    item.rewardAmount > 0L
                                ) {
                                    Spacer(Modifier.height(8.dp))
                                    if (item.claimed) {
                                        Text(
                                            "Claimed ✓",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Black
                                        )
                                    } else {
                                        Button(
                                            onClick = { onClaimReward(item) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "Claim ${item.rewardAmount} ${item.rewardCurrency.displayName}",
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(item.createdAtMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (items.any { !it.read }) {
                TextButton(onClick = onMarkAllRead) { Text("Mark all read") }
            }
        }
    )
}
