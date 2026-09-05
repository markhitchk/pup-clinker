package com.harleytg.puppyclicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harleytg.puppyclicker.ui.theme.PuppyClickerTheme
import java.time.LocalDate
import kotlin.math.max
import kotlinx.coroutines.delay

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
    CARE("Care", "💖"),
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
                        icon = { Text(item.emoji, fontSize = 20.sp) },
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
                AppTab.CARE -> CareScreen(state, viewModel)
                AppTab.SHOP -> ShopScreen(state, viewModel)
                AppTab.PUP -> PupScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlayScreen(state: GameState, viewModel: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.source_logo),
            contentDescription = "Original Puppy Clicker logo",
            modifier = Modifier.fillMaxWidth().height(72.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            "Original Puppy Clicker • Android Edition",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        if (state.offlineEarned > 0) {
            AssistChip(
                onClick = viewModel::dismissOfflineBonus,
                label = { Text("Welcome back! +${formatNumber(state.offlineEarned)} offline treats ✨") }
            )
            Spacer(Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Treats", formatNumber(state.treats), "🍪", Modifier.weight(1f))
            StatCard("Per tap", formatNumber(state.clickPower.toLong()), "👆", Modifier.weight(1f))
            StatCard("Per sec", formatNumber(state.autoPerSecond.toLong()), "⏱️", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        LevelCard(state)
        Spacer(Modifier.height(10.dp))
        CareSummaryCard(state)
        Spacer(Modifier.height(18.dp))
        PuppyButton(state, viewModel::tapPuppy)
        Spacer(Modifier.height(10.dp))

        val inCooldown = System.currentTimeMillis() < state.cooldownUntilMs
        Text(
            when {
                inCooldown -> "Pup Eye cooldown active"
                state.combo >= 10 -> "🔥 ${state.combo} combo • x${state.comboMultiplier} treats"
                else -> "Tap ${state.puppyName}! +${formatNumber(state.clickPower.toLong())} treats"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Best combo ${state.bestCombo} • ${formatNumber(state.totalTaps)} total taps",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        DailyRewardCard(state, viewModel)
        Spacer(Modifier.height(10.dp))
        PupEyeCard(state)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PuppyButton(state: GameState, onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(),
        label = "puppyScale"
    )
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(245.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    )
                )
            )
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTap()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.source_pup),
            contentDescription = "${state.puppyName} the puppy",
            modifier = Modifier.size(222.dp),
            contentScale = ContentScale.Fit
        )
        when (state.accessory) {
            "Bandana" -> Text("🧣", fontSize = 52.sp, modifier = Modifier.offset(y = 76.dp))
            "Bow" -> Text("🎀", fontSize = 44.sp, modifier = Modifier.offset(y = (-78).dp))
            "Crown" -> Text("👑", fontSize = 52.sp, modifier = Modifier.offset(y = (-92).dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, emoji: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(emoji, fontSize = 20.sp)
            Text(value, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level $level", fontWeight = FontWeight.Bold)
                Text("${formatNumber(state.lifetimeTreats)} / ${formatNumber(nextTarget)}")
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            )
        }
    }
}

@Composable
private fun CareSummaryCard(state: GameState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${state.puppyName} is ${state.mood.lowercase()}", fontWeight = FontWeight.Bold)
                Text("${state.careScore}%", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniNeed("💖", state.happiness, Modifier.weight(1f))
                MiniNeed("🍖", state.fullness, Modifier.weight(1f))
                MiniNeed("⚡", state.energy, Modifier.weight(1f))
            }
            if (state.careScore >= 80 && state.autoPerSecond > 0) {
                Spacer(Modifier.height(7.dp))
                Text("Happy pup bonus: +20% automatic treats", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MiniNeed(emoji: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji)
            Spacer(Modifier.width(4.dp))
            Text("$value%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth().height(5.dp))
    }
}

@Composable
private fun DailyRewardCard(state: GameState, viewModel: GameViewModel) {
    val today = LocalDate.now().toEpochDay()
    val available = state.lastDailyClaimDay != today
    val reward = 100L + state.level * 25L

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎁", fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Daily Puppy Gift", fontWeight = FontWeight.Bold)
                Text(
                    if (available) "Claim ${formatNumber(reward)} treats today" else "Come back tomorrow for another gift",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = viewModel::claimDailyReward, enabled = available) {
                Text(if (available) "Claim" else "Done")
            }
        }
    }
}

@Composable
private fun PupEyeCard(state: GameState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🐶👁️", fontSize = 27.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Pup Eye", fontWeight = FontWeight.Bold)
                Text(
                    "Local anti-auto-click protection • ${state.pupEyeStrikes} cooldown${if (state.pupEyeStrikes == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CareScreen(state: GameState, viewModel: GameViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        ScreenHeader("Care for ${state.puppyName}", "Feed, play, rest, and go on dog-park adventures.")
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.source_pup),
                    contentDescription = state.puppyName,
                    modifier = Modifier.size(108.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(state.mood, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Overall care ${state.careScore}%")
                Text("${state.careActions} care actions completed", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        NeedCard("Happiness", "💖", state.happiness, "Play together to improve your puppy's mood.")
        Spacer(Modifier.height(8.dp))
        NeedCard("Fullness", "🍖", state.fullness, "Feed treats when your puppy gets hungry.")
        Spacer(Modifier.height(8.dp))
        NeedCard("Energy", "⚡", state.energy, "Rest restores energy for play and adventures.")

        Spacer(Modifier.height(16.dp))
        Text("Care actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CareActionButton(
                "🍖", "Feed", "${GameViewModel.FEED_COST} 🍪",
                state.treats >= GameViewModel.FEED_COST && state.fullness < 100,
                viewModel::feedPuppy,
                Modifier.weight(1f)
            )
            CareActionButton(
                "🎾", "Play", "+happy",
                state.energy >= 10,
                viewModel::playWithPuppy,
                Modifier.weight(1f)
            )
            CareActionButton(
                "💤", "Rest", "+energy",
                state.energy < 100,
                viewModel::restPuppy,
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))
        ParkAdventureCard(state, now, viewModel)
        Spacer(Modifier.height(16.dp))

        Text("Missions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        MISSIONS.forEach { mission ->
            MissionCard(mission, state, viewModel)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun NeedCard(title: String, emoji: String, value: Int, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$emoji $title", fontWeight = FontWeight.Bold)
                Text("$value%", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Spacer(Modifier.height(5.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CareActionButton(
    emoji: String,
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(78.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 20.sp)
            Text(label, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ParkAdventureCard(state: GameState, now: Long, viewModel: GameViewModel) {
    val remainingMs = (state.parkReadyAtMs - now).coerceAtLeast(0)
    val remainingSeconds = (remainingMs + 999) / 1_000
    val ready = state.parkActive && remainingMs == 0L
    val reward = 250L + state.level * 50L

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌳", fontSize = 34.sp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Dog Park Adventure", fontWeight = FontWeight.Black)
                Text(
                    when {
                        ready -> "Adventure complete! ${formatNumber(reward)} treats are ready."
                        state.parkActive -> "${state.puppyName} returns in ${remainingSeconds}s"
                        else -> "Spend 20 energy on a 60-second adventure."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = if (ready) viewModel::claimParkAdventure else viewModel::startParkAdventure,
                enabled = ready || (!state.parkActive && state.energy >= 20)
            ) {
                Text(if (ready) "Claim" else if (state.parkActive) "Away" else "Go")
            }
        }
    }
}

@Composable
private fun MissionCard(mission: Mission, state: GameState, viewModel: GameViewModel) {
    val completed = mission.complete(state)
    val claimed = mission.id in state.claimedMissions

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (claimed) "✅" else if (completed) "🎯" else "📋", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(mission.title, fontWeight = FontWeight.Bold)
                Text(mission.description, style = MaterialTheme.typography.bodySmall)
                Text("Reward: ${formatNumber(mission.reward)} 🍪", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = { viewModel.claimMission(mission) },
                enabled = completed && !claimed
            ) {
                Text(if (claimed) "Done" else "Claim")
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
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        ScreenHeader("Puppy Shop", "Upgrade taps and automatic treat production.")
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Treat balance", fontWeight = FontWeight.Bold)
                Text("🍪 ${formatNumber(state.treats)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(12.dp))
        UPGRADES.forEach { upgrade ->
            UpgradeCard(upgrade, state.upgrades[upgrade.id] ?: 0, state.treats) {
                viewModel.buyUpgrade(upgrade)
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun UpgradeCard(upgrade: Upgrade, owned: Int, treats: Long, onBuy: () -> Unit) {
    val cost = upgradeCost(upgrade, owned)
    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(upgrade.emoji, fontSize = 32.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(upgrade.name, fontWeight = FontWeight.Bold)
                Text(upgrade.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Owned: $owned", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(8.dp))
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
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        ScreenHeader("My Pup", "Customize your puppy and track long-term progress.")
        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.source_pup),
                        contentDescription = state.puppyName,
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.puppyName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("Level ${state.level} • ${state.mood}")
                        Text("${formatNumber(state.lifetimeTreats)} lifetime treats", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { showRename = true }) { Text("Rename") }
                }

                Spacer(Modifier.height(14.dp))
                Text("Accessory", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
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

        Spacer(Modifier.height(14.dp))
        Text("Achievements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        ACHIEVEMENTS.forEach { achievement ->
            val unlocked = achievement.unlocked(state)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (unlocked) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (unlocked) achievement.emoji else "🔒", fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(achievement.title, fontWeight = FontWeight.Bold)
                        Text(achievement.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
        }

        Spacer(Modifier.height(10.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.source_htg),
                    contentDescription = "Original HTG artwork",
                    modifier = Modifier.size(58.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Original Puppy Clicker assets", fontWeight = FontWeight.Bold)
                    Text("pup.png • logo.png • htg.png", style = MaterialTheme.typography.bodySmall)
                    Text("HarleyTG-O/Puppy-Clicker", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showReset = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset game")
        }
        Spacer(Modifier.height(18.dp))
    }

    if (showRename) {
        var name by rememberSaveable { mutableStateOf(state.puppyName) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename puppy") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(18) },
                    singleLine = true,
                    label = { Text("Puppy name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renamePuppy(name)
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset Puppy Clicker?") },
            text = { Text("This permanently resets treats, upgrades, care progress, missions, and achievements.") },
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

private fun formatNumber(value: Long): String = when {
    value >= 1_000_000_000_000L -> "%.2fT".format(value / 1_000_000_000_000.0)
    value >= 1_000_000_000L -> "%.2fB".format(value / 1_000_000_000.0)
    value >= 1_000_000L -> "%.2fM".format(value / 1_000_000.0)
    value >= 1_000L -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}
