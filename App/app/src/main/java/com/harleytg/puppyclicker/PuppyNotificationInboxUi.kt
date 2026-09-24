package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private val InboxPurple = Color(0xFF6D70E8)
private val InboxPurpleDeep = Color(0xFF4F52C7)
private val InboxBlue = Color(0xFF25A7F6)
private val InboxUnreadRed = Color(0xFFE83F4F)
private val InboxClaimGreen = Color(0xFF33AC55)
private val InboxClaimGreenDark = Color(0xFF268A42)
private val InboxRewardBorder = Color(0xFFFFD36A)

private enum class PuppyInboxFilter(val label: String, val emoji: String) {
    ALL("All", ""),
    REWARDS("Rewards", "🎁"),
    PUPPIES("Puppies", "🐾"),
    SYSTEM("System", "⚙️")
}

private enum class PuppyInboxDay(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    EARLIER("Earlier")
}

@Composable
internal fun PuppyNotificationInboxDialog(
    items: List<PuppyNotificationItem>,
    onDismiss: () -> Unit,
    onOpenItem: (PuppyNotificationItem) -> Unit,
    onClaimReward: (PuppyNotificationItem) -> Unit,
    onMarkAllRead: () -> Unit
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(PuppyInboxFilter.ALL) }
    val unread = items.count { !it.read }
    val visibleItems = remember(items, filter) {
        items.filter { item -> filter.matches(item) }
    }
    val groupedItems = remember(visibleItems) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        PuppyInboxDay.entries.mapNotNull { group ->
            val values = visibleItems.filter { it.dayGroup(zone, today) == group }
            if (values.isEmpty()) null else group to values
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.90f),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 20.dp,
                tonalElevation = 4.dp
            ) {
                Column {
                    PuppyInboxHeader(onDismiss = onDismiss)

                    PuppyInboxFilterBar(
                        selected = filter,
                        unread = unread,
                        onSelect = { filter = it }
                    )

                    if (visibleItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🐶", fontSize = 42.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    if (items.isEmpty()) "You're all caught up!" else "No ${filter.label.lowercase()} notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    if (items.isEmpty()) {
                                        "New Puppy Clicker alerts will appear here."
                                    } else {
                                        "Try another notification category."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                                .padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            groupedItems.forEach { (group, groupItems) ->
                                item(key = "group-${group.name}") {
                                    Text(
                                        group.label,
                                        modifier = Modifier.padding(top = 13.dp, bottom = 1.dp, start = 4.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                items(groupItems, key = { it.id }) { item ->
                                    PuppyNotificationCard(
                                        item = item,
                                        group = group,
                                        onOpen = { onOpenItem(item) },
                                        onClaimReward = { onClaimReward(item) }
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(5.dp)) }
                        }
                    }

                    PuppyInboxActions(
                        hasRead = items.any { it.read },
                        hasUnread = unread > 0,
                        onClearRead = { PuppyNotificationHistory.clearRead(context) },
                        onMarkAllRead = onMarkAllRead
                    )
                }
            }
        }
    }
}

@Composable
private fun PuppyInboxHeader(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(InboxPurple, InboxPurpleDeep)
                )
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.88f)
            ) {
                Image(
                    painter = streamedRepoLogoPainter(
                        RepoLogoAsset.PUPPY_CLICKER,
                        R.drawable.source_logo
                    ),
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.width(12.dp))

            Text(
                "Notifications",
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )

            Surface(
                modifier = Modifier
                    .size(46.dp)
                    .semantics { contentDescription = "Close notifications" }
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.36f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✕", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun PuppyInboxFilterBar(
    selected: PuppyInboxFilter,
    unread: Int,
    onSelect: (PuppyInboxFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        PuppyInboxFilter.entries.forEach { value ->
            val isSelected = selected == value
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button) { onSelect(value) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) InboxPurple else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) InboxPurpleDeep else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (value.emoji.isNotBlank()) {
                        Text(value.emoji, fontSize = 15.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        value.label,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (value == PuppyInboxFilter.ALL && unread > 0) {
                        Spacer(Modifier.width(5.dp))
                        Surface(
                            shape = CircleShape,
                            color = InboxUnreadRed,
                            contentColor = Color.White
                        ) {
                            Text(
                                unread.coerceAtMost(99).toString(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuppyNotificationCard(
    item: PuppyNotificationItem,
    group: PuppyInboxDay,
    onOpen: () -> Unit,
    onClaimReward: () -> Unit
) {
    val claimable = item.hasClaimableReward
    val container = when {
        claimable -> MaterialTheme.colorScheme.surface
        item.read -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics {
                contentDescription = buildString {
                    if (!item.read) append("Unread. ")
                    append(item.title)
                    val body = item.displayBody()
                    if (body.isNotBlank()) append(". $body")
                }
            },
        shape = RoundedCornerShape(21.dp),
        border = if (claimable) BorderStroke(1.5.dp, InboxRewardBorder) else null,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = if (claimable) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = when (item.type) {
                    PuppyNotificationType.SYSTEM_REWARD,
                    PuppyNotificationType.DAILY_REWARD -> Color(0xFFFFF1C7)
                    PuppyNotificationType.PARK_READY,
                    PuppyNotificationType.ROSTER_UPDATE -> Color(0xFFE8F7FF)
                    PuppyNotificationType.APP_UPDATE -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.emoji(), fontSize = 25.sp)
                }
            }

            Spacer(Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.timestamp(group),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (!item.read) {
                        Spacer(Modifier.width(7.dp))
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = CircleShape,
                            color = InboxBlue
                        ) {}
                    }
                }

                val body = item.displayBody()
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (
                    item.type == PuppyNotificationType.SYSTEM_REWARD &&
                    item.rewardCurrency != null &&
                    item.rewardAmount > 0L
                ) {
                    Spacer(Modifier.height(9.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (item.claimed) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    "Claimed ✓",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = onClaimReward,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InboxClaimGreen,
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, InboxClaimGreenDark)
                            ) {
                                Text("🦴", fontSize = 15.sp)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "Claim ${item.rewardCurrency.displayName}",
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuppyInboxActions(
    hasRead: Boolean,
    hasUnread: Boolean,
    onClearRead: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onClearRead,
            enabled = hasRead,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp)
        ) {
            Text("🗑", fontSize = 15.sp)
            Spacer(Modifier.width(5.dp))
            Text("Clear Read", fontWeight = FontWeight.Bold, maxLines = 1)
        }

        Button(
            onClick = onMarkAllRead,
            enabled = hasUnread,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InboxPurple,
                contentColor = Color.White
            )
        ) {
            Text("✓", fontWeight = FontWeight.Black)
            Spacer(Modifier.width(6.dp))
            Text("Mark All Read", fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

private fun PuppyInboxFilter.matches(item: PuppyNotificationItem): Boolean = when (this) {
    PuppyInboxFilter.ALL -> true
    PuppyInboxFilter.REWARDS ->
        item.type == PuppyNotificationType.DAILY_REWARD ||
            item.type == PuppyNotificationType.SYSTEM_REWARD
    PuppyInboxFilter.PUPPIES ->
        item.type == PuppyNotificationType.PARK_READY ||
            item.type == PuppyNotificationType.ROSTER_UPDATE
    PuppyInboxFilter.SYSTEM -> item.type == PuppyNotificationType.APP_UPDATE
}

private fun PuppyNotificationItem.dayGroup(
    zone: ZoneId,
    today: LocalDate
): PuppyInboxDay {
    val itemDate = Instant.ofEpochMilli(createdAtMs.coerceAtLeast(0L))
        .atZone(zone)
        .toLocalDate()
    return when (itemDate) {
        today -> PuppyInboxDay.TODAY
        today.minusDays(1) -> PuppyInboxDay.YESTERDAY
        else -> PuppyInboxDay.EARLIER
    }
}

private fun PuppyNotificationItem.timestamp(group: PuppyInboxDay): String =
    when (group) {
        PuppyInboxDay.TODAY,
        PuppyInboxDay.YESTERDAY ->
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(createdAtMs))
        PuppyInboxDay.EARLIER ->
            DateFormat.getDateInstance(DateFormat.SHORT).format(Date(createdAtMs))
    }

private fun PuppyNotificationItem.displayBody(): String {
    if (
        type == PuppyNotificationType.SYSTEM_REWARD &&
        rewardCurrency != null &&
        rewardAmount > 0L
    ) {
        val amount = NumberFormat.getIntegerInstance().format(rewardAmount)
        return "Your puppies saved $amount ${rewardCurrency.displayName} while you were away."
    }
    return body
}

private fun PuppyNotificationItem.emoji(): String = when (type) {
    PuppyNotificationType.SYSTEM_REWARD -> "🐶"
    PuppyNotificationType.DAILY_REWARD -> "🎁"
    PuppyNotificationType.PARK_READY -> "🐾"
    PuppyNotificationType.ROSTER_UPDATE -> "🐶"
    PuppyNotificationType.APP_UPDATE -> "⚙️"
}
