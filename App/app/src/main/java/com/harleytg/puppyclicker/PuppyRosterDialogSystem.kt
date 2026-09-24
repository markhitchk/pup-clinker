package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Every Puppy Roster overlay goes through this system so Details, How to Unlock,
 * Rename, and Use Puppy share one visual hierarchy, responsive behavior, and
 * action layout.
 */
internal enum class PuppyRosterDialogMode {
    PREVIEW,
    UNLOCK,
    RENAME,
    USE
}

@Composable
internal fun PuppyRosterDialog(
    mode: PuppyRosterDialogMode,
    asset: PuppyRosterAsset,
    unlocked: Boolean,
    current: Boolean,
    activeName: String,
    retryToken: Int,
    onDismiss: () -> Unit,
    onUse: () -> Unit,
    onRename: (String) -> Unit
) {
    val metadataName = asset.style.name
    val displayName = if (current) activeName.ifBlank { metadataName } else metadataName
    var renameValue by rememberSaveable(asset.style.id, activeName) {
        mutableStateOf(activeName.ifBlank { metadataName }.take(MAX_PUPPY_NAME_LENGTH))
    }
    val cleanRename = renameValue
        .trim()
        .replace("\n", " ")
        .take(MAX_PUPPY_NAME_LENGTH)

    PuppyRosterDialogFrame(
        title = when (mode) {
            PuppyRosterDialogMode.PREVIEW -> "Puppy Details"
            PuppyRosterDialogMode.UNLOCK -> "How to Unlock"
            PuppyRosterDialogMode.RENAME -> "Rename Puppy"
            PuppyRosterDialogMode.USE -> "Use Puppy"
        },
        onDismiss = onDismiss
    ) {
        PuppyDialogIdentity(
            asset = asset,
            displayName = displayName,
            unlocked = unlocked,
            current = current,
            retryToken = retryToken,
            portraitSize = when (mode) {
                PuppyRosterDialogMode.PREVIEW -> 132.dp
                else -> 92.dp
            }
        )

        Spacer(Modifier.height(16.dp))

        when (mode) {
            PuppyRosterDialogMode.PREVIEW -> {
                PuppyDialogSection(
                    label = "About",
                    body = asset.style.description.ifBlank {
                        "No additional puppy details are available yet."
                    }
                )
                if (displayName != metadataName) {
                    Spacer(Modifier.height(10.dp))
                    PuppyDialogSection(
                        label = "Original roster name",
                        body = metadataName
                    )
                }
            }

            PuppyRosterDialogMode.UNLOCK -> {
                PuppyDialogSection(
                    label = "Unlock requirement",
                    body = asset.unlockSource?.takeIf { it.isNotBlank() }
                        ?: "Unlock instructions are not available yet.",
                    emphasized = true
                )
            }

            PuppyRosterDialogMode.RENAME -> {
                PuppyDialogSection(
                    label = "Original roster name",
                    body = metadataName
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = {
                        renameValue = it.replace("\n", " ").take(MAX_PUPPY_NAME_LENGTH)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Puppy name") },
                    supportingText = {
                        Text("${renameValue.length}/$MAX_PUPPY_NAME_LENGTH")
                    }
                )
                TextButton(
                    onClick = { renameValue = metadataName.take(MAX_PUPPY_NAME_LENGTH) }
                ) {
                    Text("Use original name")
                }
            }

            PuppyRosterDialogMode.USE -> {
                PuppyDialogSection(
                    label = "Make active puppy",
                    body = "Use $metadataName as your active puppy?",
                    emphasized = true
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        PuppyDialogActions(
            mode = mode,
            saveEnabled = cleanRename.isNotBlank(),
            onDismiss = onDismiss,
            onUse = onUse,
            onSaveName = { onRename(cleanRename) }
        )
    }
}

@Composable
private fun PuppyRosterDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxDialogHeight = (maxHeight - 12.dp).coerceAtLeast(280.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .heightIn(max = maxDialogHeight),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.semantics {
                                contentDescription = "Close roster dialog"
                            }
                        ) {
                            Text("✕", fontSize = 20.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun PuppyDialogIdentity(
    asset: PuppyRosterAsset,
    displayName: String,
    unlocked: Boolean,
    current: Boolean,
    retryToken: Int,
    portraitSize: androidx.compose.ui.unit.Dp
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreamedPuppyPortrait(
                styleId = asset.style.id,
                size = portraitSize,
                unlocked = unlocked,
                background = MaterialTheme.colorScheme.surface,
                retryToken = retryToken
            )

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )

                if (displayName != asset.style.name) {
                    Text(
                        "Original: ${asset.style.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    asset.assetId,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    PuppyDialogPill(asset.groupTitle)
                    PuppyDialogPill(
                        when {
                            current -> "Current"
                            unlocked -> "Unlocked"
                            else -> "🔒 Locked"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PuppyDialogPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.76f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PuppyDialogSection(
    label: String,
    body: String,
    emphasized: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
        }
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.height(5.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun PuppyDialogActions(
    mode: PuppyRosterDialogMode,
    saveEnabled: Boolean,
    onDismiss: () -> Unit,
    onUse: () -> Unit,
    onSaveName: () -> Unit
) {
    when (mode) {
        PuppyRosterDialogMode.PREVIEW,
        PuppyRosterDialogMode.UNLOCK -> {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (mode == PuppyRosterDialogMode.PREVIEW) "Done" else "Close")
            }
        }

        PuppyRosterDialogMode.RENAME -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSaveName,
                    enabled = saveEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }

        PuppyRosterDialogMode.USE -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onUse,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Use Puppy")
                }
            }
        }
    }
}

private const val MAX_PUPPY_NAME_LENGTH = 18
