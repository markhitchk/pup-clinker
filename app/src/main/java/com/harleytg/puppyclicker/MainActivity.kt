package com.harleytg.puppyclicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harleytg.puppyclicker.ui.theme.PuppyClickerTheme
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuppyClickerTheme {
                val gameViewModel: GameViewModel = viewModel()
                PuppyClickerApp(gameViewModel)
            }
        }
    }
}

private enum class AppTab(val label: String, val emoji: String) {
    PLAY("Play", "🐾"),
    SHOP("Shop", "🛒"),
    PUP("My Pup", "🏆")
}

@Composable
private fun PuppyClickerApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AppTab.PLAY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.emoji, fontSize = 22.sp) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (tab) {
                AppTab.PLAY -> PlayScreen(state, viewModel)
                AppTab.SHOP -> ShopScreen(state, viewModel)
                AppTab.PUP -> PupScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlayScreen(state: GameState, viewModel: GameViewModel) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScreenHeader("Puppy Clicker", "Tap, upgrade, and keep ${state.puppyName} happy.")
        Spacer(Modifier.height(16.dp))

        if (state.offlineEarned > 0) {
            AssistChip(
                onClick = viewModel::dismissOfflineBonus,
                label = { Text("Welcome back! +${formatNumber(state.offlineEarned)} offline treats ✨") }
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard("Treats", formatNumber(state.treats), "🍪", Modifier.weight(1f))
            StatCard("Per tap", formatNumber(state.clickPower.toLong()), "👆", Modifier.weight(1f))
            StatCard("Per sec", formatNumber(state.autoPerSecond.toLong()), "⏱️", Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        LevelCard(state)
        Spacer(Modifier.height(22.dp))
        PuppyButton(state, onTap = viewModel::tapPuppy)
        Spacer(Modifier.height(12.dp))

        Text(
            text = if (System.currentTimeMillis() < state.cooldownUntilMs) {
                "Pup Eye cooldown active"
            } else {
                "Tap ${state.puppyName}! +${formatNumber(state.clickPower.toLong())} treats"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Lifetime treats: ${formatNumber(state.lifetimeTreats)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(22.dp))
        PupEyeCard(state)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, emoji: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LevelCard(state: GameState) {
    val level = state.level
    val currentStart = (level - 1L) * (level - 1L) * 100L
    val nextTarget = level.toLong() * level.toLong() * 100L
    val span = max(1L, nextTarget - currentStart)
    val progress = ((state.lifetimeTreats - currentStart).toFloat() / span.toFloat()).coerceIn(0f, 1f)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level $level", fontWeight = FontWeight.Bold)
                Text("${formatNumber(state.lifetimeTreats)} / ${formatNumber(nextTarget)}")
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            )
        }
    }
}

@Composable
private fun PuppyButton(state: GameState, onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(),
        label = "puppyScale"
    )
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(238.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTap()
            },
        contentAlignment = Alignment.Center
    ) {
        Text("🐶", fontSize = 126.sp)
        when (state.accessory) {
            "Bandana" -> Text("🧣", fontSize = 52.sp, modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-24).dp))
            "Bow" -> Text("🎀", fontSize = 44.sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = 28.dp))
            "Crown" -> Text("👑", fontSize = 52.sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = 10.dp))
        }
    }
}

@Composable
private fun PupEyeCard(state: GameState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🐶👁️", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Pup Eye", fontWeight = FontWeight.Bold)
                Text(
                    "Local fair-play protection • ${state.pupEyeStrikes} cooldown${if (state.pupEyeStrikes == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShopScreen(state: GameState, viewModel: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenHeader("Puppy Shop", "Spend treats to make every tap and every second count.")
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Treat balance", fontWeight = FontWeight.Bold)
                Text("🍪 ${formatNumber(state.treats)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(14.dp))
        UPGRADES.forEach { upgrade ->
            UpgradeCard(
                upgrade = upgrade,
                owned = state.upgrades[upgrade.id] ?: 0,
                treats = state.treats,
                onBuy = { viewModel.buyUpgrade(upgrade) }
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun UpgradeCard(upgrade: Upgrade, owned: Int, treats: Long, onBuy: () -> Unit) {
    val cost = upgradeCost(upgrade, owned)
    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(upgrade.emoji, fontSize = 34.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(upgrade.name, fontWeight = FontWeight.Bold)
                Text(upgrade.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Owned: $owned", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(10.dp))
            Button(onClick = onBuy, enabled = treats >= cost) {
                Text("${formatNumber(cost)} 🍪")
            }
        }
    }
}

@Composable
private fun PupScreen(state: GameState, viewModel: GameViewModel) {
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showReset by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenHeader("My Pup", "Personalize your puppy and track achievements.")
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🐶", fontSize = 54.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.puppyName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("Level ${state.level} • ${formatNumber(state.lifetimeTreats)} lifetime treats")
                    }
                    OutlinedButton(onClick = { showRename = true }) { Text("Rename") }
                }

                Spacer(Modifier.height(18.dp))
                Text("Accessory", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GameViewModel.ACCESSORIES.forEach { accessory ->
                        val emoji = when (accessory) {
                            "Bandana" -> "🧣 "
                            "Bow" -> "🎀 "
                            "Crown" -> "👑 "
                            else -> ""
                        }
                        FilterChip(
                            selected = state.accessory == accessory,
                            onClick = { viewModel.setAccessory(accessory) },
                            label = { Text(emoji + accessory) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Achievements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))

        ACHIEVEMENTS.forEach { achievement ->
            val unlocked = achievement.unlocked(state)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (unlocked) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (unlocked) achievement.emoji else "🔒", fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(achievement.title, fontWeight = FontWeight.Bold)
                        Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { showReset = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Reset game data")
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showRename) {
        RenameDialog(
            currentName = state.puppyName,
            onDismiss = { showRename = false },
            onSave = {
                viewModel.renamePuppy(it)
                showRename = false
            }
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset Puppy Clicker?") },
            text = { Text("This clears treats, upgrades, name, accessories, and Pup Eye history on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetGame()
                    showReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name your puppy") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(18) },
                singleLine = true,
                label = { Text("Puppy name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatNumber(value: Long): String = when {
    value >= 1_000_000_000_000 -> "%.1fT".format(value / 1_000_000_000_000.0)
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}
