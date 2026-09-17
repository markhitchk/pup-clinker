package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PuppyOnePointZeroCelebrationDialog(
    onContinue: () -> Unit,
    onOpenReleaseHub: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text("Puppy Clicker 1.0 🎉", fontWeight = FontWeight.Black)
        },
        text = {
            Column {
                Text(PuppyReleaseHubModel.WHATS_NEW_1_0)
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Commemorative badge", style = MaterialTheme.typography.labelMedium)
                        Text(
                            PuppyReleaseMilestones.RELEASE_1_0_BADGE_NAME,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "A permanent local profile badge for the 1.0 launch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onContinue) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onOpenReleaseHub) { Text("What's New") }
        }
    )
}
