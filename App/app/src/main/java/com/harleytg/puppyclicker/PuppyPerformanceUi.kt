package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PuppyPerformancePresetSelector() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(PuppyPerformancePreferences.current(context)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Performance", fontWeight = FontWeight.Black)
            Text(
                when (selected) {
                    PuppyPerformancePreset.AUTOMATIC -> "Automatically balances effects and device performance."
                    PuppyPerformancePreset.QUALITY -> "Keeps the intended visual experience at full quality."
                    PuppyPerformancePreset.BATTERY_SAVER -> "Reduces non-essential motion, shimmer, and celebration effects."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PuppyPerformancePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = selected == preset,
                        onClick = {
                            selected = preset
                            PuppyPerformancePreferences.set(context, preset)
                        },
                        label = {
                            Text(
                                when (preset) {
                                    PuppyPerformancePreset.AUTOMATIC -> "Automatic"
                                    PuppyPerformancePreset.QUALITY -> "Quality"
                                    PuppyPerformancePreset.BATTERY_SAVER -> "Battery Saver"
                                }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
