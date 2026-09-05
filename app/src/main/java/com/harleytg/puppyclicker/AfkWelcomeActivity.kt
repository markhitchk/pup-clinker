package com.harleytg.puppyclicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harleytg.puppyclicker.ui.theme.PuppyClickerTheme

class AfkWelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences(PuppyClickerV5ViewModel.PREFS_NAME, MODE_PRIVATE)
        val pending = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, 0L).coerceAtLeast(0L)
        val awayMs = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_AWAY_MS, 0L).coerceAtLeast(0L)

        if (pending <= 0L) {
            finish()
            return
        }

        setContent {
            PuppyClickerTheme {
                BackHandler(enabled = true) { /* Reward stays until collected. */ }
                AfkWelcomeScreen(
                    reward = pending,
                    awayMs = awayMs,
                    onCollect = {
                        val latestReward = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, 0L)
                            .coerceAtLeast(0L)
                        if (latestReward > 0L) {
                            val existingClaim = prefs.getLong(PuppyClickerV5ViewModel.KEY_AFK_CLAIM_READY, 0L)
                                .coerceAtLeast(0L)
                            val combined = safeAdd(existingClaim, latestReward)
                            prefs.edit()
                                .putLong(PuppyClickerV5ViewModel.KEY_AFK_PENDING, 0L)
                                .putLong(PuppyClickerV5ViewModel.KEY_AFK_AWAY_MS, 0L)
                                .putLong(PuppyClickerV5ViewModel.KEY_AFK_CLAIM_READY, combined)
                                .apply()
                        }
                        finish()
                    }
                )
            }
        }
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}

@Composable
private fun AfkWelcomeScreen(
    reward: Long,
    awayMs: Long,
    onCollect: () -> Unit
) {
    val awayText = remember(awayMs) { formatAwayTime(awayMs) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
                    )
                )
            )
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.source_logo),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Welcome back!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Buddy kept a tiny treat stash while you were away.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))

                Text("You were away", style = MaterialTheme.typography.labelLarge)
                Text(
                    awayText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        Modifier.padding(horizontal = 26.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🍪 AFK treats", fontWeight = FontWeight.Bold)
                        Text(
                            "+${"%,d".format(reward)}",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Slow kennel rate: 1,000 treats every 24 hours · pro-rated by time away",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onCollect,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Collect ${"%,d".format(reward)} treats", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private fun formatAwayTime(ms: Long): String {
    var minutes = (ms / 60_000L).coerceAtLeast(0L)
    val days = minutes / (24L * 60L)
    minutes %= 24L * 60L
    val hours = minutes / 60L
    minutes %= 60L

    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes.coerceAtLeast(1L)}m"
    }
}