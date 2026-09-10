package com.harleytg.puppyclicker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harleytg.puppyclicker.ui.theme.LocalPuppyReducedMotion

/** Ticket feedback overlays its parent Play container and never consumes layout height. */
@Composable
internal fun V6TicketDropOverlay(
    visible: Boolean,
    rarity: TicketRarity?,
    modifier: Modifier = Modifier
) {
    if (rarity == null) return
    val duration = if (LocalPuppyReducedMotion.current) 1 else 160

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.widthIn(max = 286.dp),
        enter = fadeIn(tween(duration)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(duration)),
        exit = fadeOut(tween(duration)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(duration))
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(rarity.emoji, fontSize = 22.sp)
                Spacer(Modifier.size(8.dp))
                Column {
                    Text("${rarity.displayName} Ticket +1", fontWeight = FontWeight.Black)
                    Text("Ticket Upgrades", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
