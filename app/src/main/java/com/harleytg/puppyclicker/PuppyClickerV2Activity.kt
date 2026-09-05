package com.harleytg.puppyclicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class PuppyClickerV2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuppyClickerTheme {
                val vm: GameViewModel = viewModel()
                PuppyClickerV2App(vm)
            }
        }
    }
}

private enum class GameTab(val label: String, val emoji: String) {
    PLAY("Play", "🐾"),
    CARE("Care", "💖"),
    SHOP("Shop", "🛒"),
    PUPS("Pups", "🐶"),
    SETTINGS("Settings", "⚙️")
}

@Composable
private fun PuppyClickerV2App(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(GameTab.PLAY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
                GameTab.entries.forEach { item ->
                    val selected = tab == item
                    val scale by animateFloatAsState(
                        targetValue = if (selected && state.animationsEnabled) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 500f),
                        label = "nav"
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item },
                        icon = {
                            Text(item.emoji, fontSize = 19.sp, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                        },
                        label = { Text(item.label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.045f)
                        )
                    )
                )
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (!state.animationsEnabled) {
                        fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                    } else {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(200)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(140)))
                    }
                },
                label = "screen"
            ) { current ->
                when (current) {
                    GameTab.PLAY -> V2PlayScreen(state, viewModel)
                    GameTab.CARE -> V2CareScreen(state, viewModel)
                    GameTab.SHOP -> V2ShopScreen(state, viewModel)
                    GameTab.PUPS -> V2PupsScreen(state, viewModel)
                    GameTab.SETTINGS -> V2SettingsScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun V2Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun V2PlayScreen(state: GameState, viewModel: GameViewModel) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tapScale = remember { Animatable(1f) }
    val idle = rememberInfiniteTransition(label = "idle")
    val idleY by idle.animateFloat(
        initialValue = if (state.animationsEnabled) -3f else 0f,
        targetValue = if (state.animationsEnabled) 5f else 0f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleY"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.source_logo),
                contentDescription = "Puppy Clicker",
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Puppy Clicker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("${state.puppyName} · ${state.mood}", style = MaterialTheme.typography.bodySmall)
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
                Text("LV ${state.level}", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🍪 Treats", style = MaterialTheme.typography.labelMedium)
                    AnimatedContent(targetState = state.treats, label = "treats") { value ->
                        Text(formatValue(value, state.compactNumbers), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    }
                }
                StatBlock("👆", formatValue(state.clickPower.toLong(), state.compactNumbers), "per tap")
                Spacer(Modifier.width(14.dp))
                StatBlock("⏱️", formatValue(state.autoPerSecond.toLong(), state.compactNumbers), "per sec")
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(
            Modifier.fillMaxWidth().height(410.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.075f)
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    NeedMini("💖", state.happiness, Modifier.weight(1f))
                    NeedMini("🍖", state.fullness, Modifier.weight(1f))
                    NeedMini("⚡", state.energy, Modifier.weight(1f))
                }

                if (state.combo >= 5) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 62.dp, end = 16.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                    ) {
                        Text("🔥 ${state.combo} combo", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Black)
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(290.dp)
                        .graphicsLayer {
                            translationY = idleY
                            scaleX = tapScale.value
                            scaleY = tapScale.value
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                        .clickable {
                            viewModel.tapPuppy()
                            if (state.hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.animationsEnabled) {
                                scope.launch {
                                    tapScale.snapTo(0.89f)
                                    tapScale.animateTo(1f, spring(dampingRatio = 0.43f, stiffness = 620f))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PuppyAvatar(state.puppyStyle, 258.dp, state.animationsEnabled)
                    AccessoryOverlay(state.accessory)
                }

                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    Text(
                        "Tap ${state.puppyName} · +${formatValue(state.clickPower.toLong(), state.compactNumbers)}",
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🛒", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Tap strength comes from the Shop", fontWeight = FontWeight.Bold)
                    Text("Combos are for fun and achievements only — they do not increase treats per tap.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun StatBlock(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$emoji $value", fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NeedMini(emoji: String, value: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(value / 100f, tween(450), label = "needMini")
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text("$emoji  $value%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun PuppyAvatar(styleId: String, size: androidx.compose.ui.unit.Dp, animations: Boolean) {
    val style = PUPPY_STYLES.firstOrNull { it.id == styleId } ?: PUPPY_STYLES.first()
    if (style.id == "classic") {
        Image(
            painter = painterResource(R.drawable.source_pup),
            contentDescription = style.name,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit
        )
    } else {
        val infinite = rememberInfiniteTransition(label = "pupEmoji")
        val tilt by infinite.animateFloat(
            if (animations) -2f else 0f,
            if (animations) 2f else 0f,
            infiniteRepeatable(tween(1800), RepeatMode.Reverse),
            label = "pupTilt"
        )
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text(style.emoji, fontSize = (size.value * 0.48f).sp, modifier = Modifier.graphicsLayer { rotationZ = tilt })
        }
    }
}

@Composable
private fun AccessoryOverlay(accessory: String) {
    when (accessory) {
        "Bandana" -> Text("🧣", fontSize = 52.sp, modifier = Modifier.offset(y = 88.dp))
        "Bow" -> Text("🎀", fontSize = 44.sp, modifier = Modifier.offset(y = (-90).dp))
        "Crown" -> Text("👑", fontSize = 54.sp, modifier = Modifier.offset(y = (-104).dp))
    }
}

@Composable
private fun V2CareScreen(state: GameState, viewModel: GameViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        V2Header("Care for ${state.puppyName}", "Keep your pup happy, fed, rested, and active.")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                PuppyAvatar(state.puppyStyle, 120.dp, state.animationsEnabled)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(state.mood, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Overall care ${state.careScore}%")
                Text("${state.careActions} care actions", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        NeedCardV2("Happiness", "💖", state.happiness)
        Spacer(Modifier.height(8.dp))
        NeedCardV2("Fullness", "🍖", state.fullness)
        Spacer(Modifier.height(8.dp))
        NeedCardV2("Energy", "⚡", state.energy)
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::feedPuppy, enabled = state.treats >= GameViewModel.FEED_COST && state.fullness < 100, modifier = Modifier.weight(1f)) { Text("🍖 Feed") }
            Button(onClick = viewModel::playWithPuppy, enabled = state.energy >= 10, modifier = Modifier.weight(1f)) { Text("🎾 Play") }
            Button(onClick = viewModel::restPuppy, enabled = state.energy < 100, modifier = Modifier.weight(1f)) { Text("💤 Rest") }
        }

        Spacer(Modifier.height(16.dp))
        val remaining = ((state.parkReadyAtMs - now).coerceAtLeast(0) + 999) / 1_000
        val ready = state.parkActive && remaining == 0L
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🌳", fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dog Park Adventure", fontWeight = FontWeight.Black)
                    Text(
                        when {
                            ready -> "Adventure complete — reward ready!"
                            state.parkActive -> "Returns in ${remaining}s"
                            else -> "Spend 20 energy on a 60-second adventure."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = if (ready) viewModel::claimParkAdventure else viewModel::startParkAdventure,
                    enabled = ready || (!state.parkActive && state.energy >= 20)
                ) { Text(if (ready) "Claim" else if (state.parkActive) "Away" else "Go") }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Missions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        MISSIONS.forEach { mission ->
            val complete = mission.complete(state)
            val claimed = mission.id in state.claimedMissions
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (claimed) "✅" else if (complete) "🎯" else "📋", fontSize = 24.sp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mission.title, fontWeight = FontWeight.Bold)
                        Text(mission.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { viewModel.claimMission(mission) }, enabled = complete && !claimed) { Text(if (claimed) "Done" else "Claim") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun NeedCardV2(title: String, emoji: String, value: Int) {
    val progress by animateFloatAsState(value / 100f, tween(500), label = "need")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$emoji $title", fontWeight = FontWeight.Bold)
                Text("$value%", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun V2ShopScreen(state: GameState, viewModel: GameViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        V2Header("Puppy Shop", "The only place that can increase treats per tap.")
        Spacer(Modifier.height(12.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
            Text(
                "👆 Current tap power: ${formatValue(state.clickPower.toLong(), state.compactNumbers)} treats per tap",
                Modifier.padding(12.dp),
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(12.dp))
        UPGRADES.forEach { upgrade ->
            val owned = state.upgrades[upgrade.id] ?: 0
            val cost = upgradeCost(upgrade, owned)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(upgrade.emoji, fontSize = 31.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(upgrade.name, fontWeight = FontWeight.Bold)
                        Text(upgrade.description, style = MaterialTheme.typography.bodySmall)
                        Text("Owned: $owned", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(onClick = { viewModel.buyUpgrade(upgrade) }, enabled = state.treats >= cost) {
                        Text("${formatValue(cost, state.compactNumbers)} 🍪")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun V2PupsScreen(state: GameState, viewModel: GameViewModel) {
    var rename by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        V2Header("Puppy Collection", "Pick your favorite pup. Puppy choice never changes tap power.")
        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PuppyAvatar(state.puppyStyle, 92.dp, state.animationsEnabled)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.puppyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(PUPPY_STYLES.firstOrNull { it.id == state.puppyStyle }?.name ?: "Buddy")
                    Text("Level ${state.level} · ${state.mood}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { rename = true }) { Text("Rename") }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Choose a puppy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        PUPPY_STYLES.forEach { puppy ->
            val unlocked = puppy.id in state.unlockedPuppies
            val selected = puppy.id == state.puppyStyle
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                tween(260),
                label = "pupCard"
            )
            Card(colors = CardDefaults.cardColors(containerColor = bg), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = unlocked) { viewModel.setPuppyStyle(puppy.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (puppy.id == "classic") PuppyAvatar(puppy.id, 66.dp, state.animationsEnabled)
                    else Text(if (unlocked) puppy.emoji else "🔒", fontSize = 44.sp, textAlign = TextAlign.Center, modifier = Modifier.width(66.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(puppy.name, fontWeight = FontWeight.Black)
                        Text(puppy.description, style = MaterialTheme.typography.bodySmall)
                        if (puppy.redeemOnly) {
                            Text(if (unlocked) "Redeem puppy unlocked" else "Special redeem-code puppy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(if (selected) "✓" else if (unlocked) "Choose" else "Locked", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(10.dp))
        Text("Accessories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GameViewModel.ACCESSORIES.forEach { accessory ->
                FilterChip(
                    selected = state.accessory == accessory,
                    onClick = { viewModel.setAccessory(accessory) },
                    label = { Text(accessory) }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    if (rename) {
        var name by rememberSaveable { mutableStateOf(state.puppyName) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename puppy") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(18) }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { viewModel.renamePuppy(name); rename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun V2SettingsScreen(state: GameState, viewModel: GameViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    var redeemMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var redeemSuccess by rememberSaveable { mutableStateOf(false) }
    var resetConfirm by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        V2Header("Settings", "Game controls, accessibility, redeem codes, and data.")
        Spacer(Modifier.height(14.dp))

        Text("Gameplay", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        SettingSwitch("📳", "Haptic feedback", "Vibrate gently when tapping your puppy.", state.hapticsEnabled, viewModel::setHapticsEnabled)
        Spacer(Modifier.height(8.dp))
        SettingSwitch("✨", "Animations", "Puppy idle motion, spring taps, and screen transitions.", state.animationsEnabled, viewModel::setAnimationsEnabled)
        Spacer(Modifier.height(8.dp))
        SettingSwitch("🔢", "Compact numbers", "Show 1.2K / 3.4M instead of long values.", state.compactNumbers, viewModel::setCompactNumbers)

        Spacer(Modifier.height(18.dp))
        Text("Redeem code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(
            "Codes are cryptographically signed. They can grant treats or special puppies, but never tap power.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.take(4096); redeemMessage = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Puppy Clicker code") },
            placeholder = { Text("PC1.…") },
            minLines = 2,
            maxLines = 4
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val result = viewModel.redeemCode(code)
                redeemSuccess = result.success
                redeemMessage = result.message
                if (result.success) code = ""
            },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Redeem securely") }

        redeemMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (redeemSuccess) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
            ) {
                Text(message, Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Fair play", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("🐶👁️ Pup Eye", fontWeight = FontWeight.Black)
                Text("Local rapid-tap protection is always active.", style = MaterialTheme.typography.bodySmall)
                Text("Tap power can only be increased by Shop upgrades.", style = MaterialTheme.typography.bodySmall)
                Text("Cooldowns recorded: ${state.pupEyeStrikes}", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("Puppy Clicker ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Black)
                Text("Package: ${BuildConfig.APPLICATION_ID}", style = MaterialTheme.typography.bodySmall)
                Text("Original Puppy Clicker assets + Android Edition gameplay", style = MaterialTheme.typography.bodySmall)
                Text("Redeemed codes on this device: ${state.redeemedCodeIds.size}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { resetConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset game progress") }
        Text(
            "Reset keeps Settings, redeemed-code history, and unlocked redeem puppies so codes cannot simply be reused after a reset.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(18.dp))
    }

    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text("Reset game progress?") },
            text = { Text("Treats, levels, care progress, missions, and Shop upgrades will reset. Settings and redeem history stay protected.") },
            confirmButton = { TextButton(onClick = { viewModel.resetGame(); resetConfirm = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingSwitch(emoji: String, title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

private fun formatValue(value: Long, compact: Boolean): String {
    if (!compact) return "%,d".format(value)
    return when {
        value >= 1_000_000_000_000L -> "%.2fT".format(value / 1_000_000_000_000.0)
        value >= 1_000_000_000L -> "%.2fB".format(value / 1_000_000_000.0)
        value >= 1_000_000L -> "%.2fM".format(value / 1_000_000.0)
        value >= 1_000L -> "%.1fK".format(value / 1_000.0)
        else -> value.toString()
    }
}
