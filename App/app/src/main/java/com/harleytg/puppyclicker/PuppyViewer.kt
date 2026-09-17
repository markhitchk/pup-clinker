package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PuppyViewerDialog(
    asset: PuppyRosterAsset,
    state: V6GameState,
    retryToken: Int,
    onDismiss: () -> Unit,
    onUse: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryArtwork: () -> Unit,
    onRename: (() -> Unit)? = null
) {
    val id = asset.style.id
    val unlocked = id in state.unlockedPuppies
    val active = id == state.puppyStyle
    val favorite = id in state.favoritePuppies
    val bond = PuppyProgression.bondFor(state.bondByPuppyId, id)
    val milestone = PuppyProgression.milestoneFor(bond)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(asset.style.name, fontWeight = FontWeight.Black)
                Text(
                    asset.assetId,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StreamedPuppyPortrait(
                    styleId = id,
                    size = 220.dp,
                    accessory = if (active) state.accessory else "None",
                    unlocked = unlocked,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    retryToken = retryToken
                )
                Spacer(Modifier.height(10.dp))
                Text(asset.groupTitle, style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        active -> "Current Puppy"
                        unlocked -> "Owned"
                        else -> "Locked"
                    },
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text("Bond $bond% · ${milestone.label}", fontWeight = FontWeight.Black)
                if (!unlocked && asset.unlockSource != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        asset.unlockSource,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (active && state.accessory != "None") {
                    Spacer(Modifier.height(4.dp))
                    Text("Accessory: ${state.accessory}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = if (favorite) {
                                    "Remove ${asset.style.name} from favorites"
                                } else {
                                    "Add ${asset.style.name} to favorites"
                                }
                            }
                    ) {
                        Text(if (favorite) "♥ Favorite" else "♡ Favorite")
                    }
                    OutlinedButton(
                        onClick = onRetryArtwork,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Retry artwork for ${asset.style.name}" }
                    ) {
                        Text("↻ Retry Art")
                    }
                }
                if (active && onRename != null) {
                    TextButton(onClick = onRename) { Text("Rename Puppy") }
                }
            }
        },
        confirmButton = {
            if (unlocked && !active) {
                Button(onClick = onUse) { Text("Use Puppy") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (unlocked && !active) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
