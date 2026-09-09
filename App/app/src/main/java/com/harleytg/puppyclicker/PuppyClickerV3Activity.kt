package com.harleytg.puppyclicker

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harleytg.puppyclicker.ui.theme.PuppyClickerTheme
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PuppyClickerV3Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuppyClickerTheme {
                val vm: GameViewModel = viewModel()
                PuppyClickerV3App(vm)
            }
        }
    }
}

private enum class V3Tab(val label: String, val emoji: String) {
    PLAY("Play", "🐾"),
    CARE("Care", "💖"),
    SHOP("Shop", "🛍️"),
    PUPS("Pups", "🐶"),
    SETTINGS("Settings", "⚙️")
}

private enum class UpgradeFilter(val label: String) {
    ALL("All"), TAP("Tap"), AUTO("Auto")
}

@Composable
private fun PuppyClickerV3App(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(V3Tab.PLAY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
                V3Tab.entries.forEach { item ->
                    val selected = item == tab
                    val scale by animateFloatAsState(
                        targetValue = if (selected && state.animationsEnabled) 1.14f else 1f,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
                        label = "navScale"
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item },
                        icon = { Text(item.emoji, fontSize = 19.sp, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) },
                        label = { Text(item.label, fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.065f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.045f)
                        )
                    )
                )
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (!state.animationsEnabled) {
                        fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                    } else {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(170))) togetherWith
                            (slideOutHorizontally(tween(190)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(130)))
                    }
                },
                label = "v3Screen"
            ) { current ->
                when (current) {
                    V3Tab.PLAY -> V3PlayScreen(state, viewModel)
                    V3Tab.CARE -> V3CareScreen(state, viewModel)
                    V3Tab.SHOP -> V3ShopScreen(state, viewModel)
                    V3Tab.PUPS -> V3PupsScreen(state, viewModel)
                    V3Tab.SETTINGS -> V3SettingsScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun V3PlayScreen(state: GameState, viewModel: GameViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tapScale = remember { Animatable(1f) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }

    val cooldownMs = (state.cooldownUntilMs - now).coerceAtLeast(0L)
    val idle = rememberInfiniteTransition(label = "v3Idle")
    val idleY by idle.animateFloat(
        initialValue = if (state.animationsEnabled) -3f else 0f,
        targetValue = if (state.animationsEnabled) 5f else 0f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "v3IdleY"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        V3BrandHeader(state)
        Spacer(Modifier.height(12.dp))
        WalletCard(state)
        Spacer(Modifier.height(12.dp))

        Surface(
            Modifier.fillMaxWidth().height(420.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.075f)
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    NeedPill("💖", state.happiness, Modifier.weight(1f))
                    NeedPill("🍖", state.fullness, Modifier.weight(1f))
                    NeedPill("⚡", state.energy, Modifier.weight(1f))
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
                        .size(298.dp)
                        .graphicsLayer {
                            translationY = idleY
                            scaleX = tapScale.value
                            scaleY = tapScale.value
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
                        .clickable(enabled = cooldownMs == 0L) {
                            viewModel.tapPuppy()
                            if (state.hapticsEnabled) performPuppyHaptic(context)
                            if (state.animationsEnabled) {
                                scope.launch {
                                    tapScale.snapTo(0.89f)
                                    tapScale.animateTo(1f, spring(dampingRatio = 0.43f, stiffness = 620f))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    OriginalPuppyPortrait(state.puppyStyle, 270.dp, state.accessory, unlocked = true)
                }

                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (cooldownMs > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)
                ) {
                    Text(
                        if (cooldownMs > 0) {
                            "🐶👁️ Fair-play cooldown · ${(cooldownMs + 999) / 1000}s"
                        } else {
                            "Tap ${state.puppyName} · +${formatV3(state.clickPower.toLong(), state.compactNumbers)} 🍪"
                        },
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🛍️", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Tap strength stays Shop-only", fontWeight = FontWeight.Bold)
                    Text("Combos never multiply treats. Upgrade Tickets only discount Shop purchases.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun V3BrandHeader(state: GameState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Image(
                painter = painterResource(R.drawable.source_logo),
                contentDescription = "Puppy Clicker",
                modifier = Modifier.padding(5.dp).size(54.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Puppy Clicker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("${state.puppyName} · ${state.mood}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
            Text("LV ${state.level}", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun WalletCard(state: GameState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🍪 Treats", style = MaterialTheme.typography.labelMedium)
                Text(formatV3(state.treats, state.compactNumbers), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
            MiniStat("🎟️", state.upgradeTickets.toString(), "tickets")
            Spacer(Modifier.width(13.dp))
            MiniStat("👆", formatV3(state.clickPower.toLong(), state.compactNumbers), "per tap")
            Spacer(Modifier.width(13.dp))
            MiniStat("⏱️", formatV3(state.autoPerSecond.toLong(), state.compactNumbers), "per sec")
        }
    }
}

@Composable
private fun MiniStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$emoji $value", fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NeedPill(emoji: String, value: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(value / 100f, tween(430), label = "v3Need")
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text("$emoji  $value%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun OriginalPuppyPortrait(
    styleId: String,
    size: Dp,
    accessory: String = "None",
    unlocked: Boolean = true
) {
    val style = PUPPY_STYLES.firstOrNull { it.id == styleId } ?: PUPPY_STYLES.first()
    val bg = when (style.id) {
        "golden" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f)
        "poodle" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
        "spotty" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        "midnight" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)
        "cloud" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    }

    Box(
        Modifier.size(size).clip(CircleShape).background(bg).alpha(if (unlocked) 1f else 0.38f),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.source_pup),
            contentDescription = style.name,
            modifier = Modifier.size(size * 0.90f),
            contentScale = ContentScale.Fit
        )

        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(size * 0.05f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            Text(style.emoji, fontSize = (size.value * 0.13f).sp, modifier = Modifier.padding(size * 0.025f))
        }

        if (unlocked) {
            when (accessory) {
                "Bandana" -> Text("🧣", fontSize = (size.value * 0.18f).sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.07f))
                "Bow" -> Text("🎀", fontSize = (size.value * 0.16f).sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = size * 0.04f))
                "Crown" -> Text("👑", fontSize = (size.value * 0.18f).sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = size * 0.015f))
            }
        } else {
            Text("🔒", fontSize = (size.value * 0.20f).sp)
        }
    }
}

@Composable
private fun V3CareScreen(state: GameState, viewModel: GameViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    val today = LocalDate.now().toEpochDay()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        V3Header("Care for ${state.puppyName}", "Care rewards help progression, but never raise treats per tap.")
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OriginalPuppyPortrait(state.puppyStyle, 118.dp, state.accessory)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(state.mood, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Overall care ${state.careScore}%")
                Text("${state.careActions} care actions", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        CareMeter("💖 Happiness", state.happiness)
        Spacer(Modifier.height(8.dp))
        CareMeter("🍖 Fullness", state.fullness)
        Spacer(Modifier.height(8.dp))
        CareMeter("⚡ Energy", state.energy)

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::feedPuppy, enabled = state.treats >= GameViewModel.FEED_COST && state.fullness < 100, modifier = Modifier.weight(1f)) { Text("🍖 Feed") }
            Button(onClick = viewModel::playWithPuppy, enabled = state.energy >= 10, modifier = Modifier.weight(1f)) { Text("🎾 Play") }
            Button(onClick = viewModel::restPuppy, enabled = state.energy < 100, modifier = Modifier.weight(1f)) { Text("💤 Rest") }
        }

        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎁", fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daily Puppy Gift", fontWeight = FontWeight.Black)
                    Text("Treats + 1 Upgrade Ticket each day.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = viewModel::claimDailyReward, enabled = state.lastDailyClaimDay != today) {
                    Text(if (state.lastDailyClaimDay == today) "Claimed" else "Claim")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        val remaining = ((state.parkReadyAtMs - now).coerceAtLeast(0) + 999) / 1_000
        val parkReady = state.parkActive && remaining == 0L
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🌳", fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dog Park Adventure", fontWeight = FontWeight.Black)
                    Text(
                        when {
                            parkReady -> "Adventure complete — reward ready."
                            state.parkActive -> "Returns in ${remaining}s"
                            else -> "Spend 20 energy on a 60-second trip."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = if (parkReady) viewModel::claimParkAdventure else viewModel::startParkAdventure,
                    enabled = parkReady || (!state.parkActive && state.energy >= 20)
                ) { Text(if (parkReady) "Claim" else if (state.parkActive) "Away" else "Go") }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Missions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("Ticket missions make Shop upgrades a little easier.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(
                            buildString {
                                append("+${formatV3(mission.reward, state.compactNumbers)} 🍪")
                                if (mission.ticketReward > 0) append("  +${mission.ticketReward} 🎟️")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
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
private fun CareMeter(label: String, value: Int) {
    val progress by animateFloatAsState(value / 100f, tween(500), label = "careMeter")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontWeight = FontWeight.Bold)
                Text("$value%", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(7.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun V3ShopScreen(state: GameState, viewModel: GameViewModel) {
    var filter by rememberSaveable { mutableStateOf(UpgradeFilter.ALL) }
    var code by rememberSaveable { mutableStateOf("") }
    var codeMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var codeSuccess by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        V3Header("Puppy Shop", "Upgrades, tickets, and Puppy Codes in one place.")
        Spacer(Modifier.height(12.dp))
        WalletCard(state)
        Spacer(Modifier.height(12.dp))

        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎟️", fontSize = 30.sp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Upgrade Tickets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text("Use one ticket for 20% off one upgrade purchase.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(state.upgradeTickets.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(8.dp))
                Text("Earn tickets from the Daily Puppy Gift, selected missions, and Puppy Codes.", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(14.dp))
        PuppyCodeCounter(
            code = code,
            onCodeChange = { code = it.take(64); codeMessage = null },
            message = codeMessage,
            success = codeSuccess,
            onRedeem = {
                val result = viewModel.redeemCode(code)
                codeSuccess = result.success
                codeMessage = result.message
                if (result.success) code = ""
            }
        )

        Spacer(Modifier.height(18.dp))
        Text("Upgrades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("Tap power can only increase here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            UpgradeFilter.entries.forEach { option ->
                FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
            }
        }
        Spacer(Modifier.height(10.dp))

        UPGRADES.filter {
            when (filter) {
                UpgradeFilter.ALL -> true
                UpgradeFilter.TAP -> it.effect == UpgradeEffect.CLICK
                UpgradeFilter.AUTO -> it.effect == UpgradeEffect.AUTO
            }
        }.forEach { upgrade ->
            UpgradeCardV3(state, upgrade, viewModel)
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PuppyCodeCounter(
    code: String,
    onCodeChange: (String) -> Unit,
    message: String?,
    success: Boolean,
    onRedeem: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎫", fontSize = 30.sp)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("Puppy Code Counter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("Offline, one-time codes stored as salted hashes in the app.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Enter Puppy Code") },
                placeholder = { Text("BUDDY-…") }
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRedeem, enabled = code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Redeem Puppy Code")
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (success) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(it, Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun UpgradeCardV3(state: GameState, upgrade: Upgrade, viewModel: GameViewModel) {
    val owned = state.upgrades[upgrade.id] ?: 0
    val fullCost = upgradeCost(upgrade, owned)
    val ticketCost = ticketUpgradeCost(upgrade, owned)
    val accent by animateColorAsState(
        if (upgrade.effect == UpgradeEffect.CLICK) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
        tween(250),
        label = "upgradeBg"
    )

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = accent)) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(upgrade.emoji, fontSize = 32.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(upgrade.name, fontWeight = FontWeight.Black)
                    Text(upgrade.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Owned $owned · ${if (upgrade.effect == UpgradeEffect.CLICK) "Tap upgrade" else "Auto upgrade"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.buyUpgrade(upgrade, false) },
                    enabled = state.treats >= fullCost,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("${formatV3(fullCost, state.compactNumbers)} 🍪")
                }
                Button(
                    onClick = { viewModel.buyUpgrade(upgrade, true) },
                    enabled = state.upgradeTickets > 0 && state.treats >= ticketCost,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🎟️ ${formatV3(ticketCost, state.compactNumbers)}")
                }
            }
            if (state.upgradeTickets > 0) {
                Text(
                    "Ticket saves ${formatV3(fullCost - ticketCost, state.compactNumbers)} treats",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun V3PupsScreen(state: GameState, viewModel: GameViewModel) {
    var rename by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        V3Header("Puppy Collection", "Every character keeps the original Puppy Clicker pup as its base.")
        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                OriginalPuppyPortrait(state.puppyStyle, 96.dp, state.accessory)
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
        Text("Choose your pup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))

        PUPPY_STYLES.forEach { puppy ->
            val unlocked = puppy.id in state.unlockedPuppies
            val selected = puppy.id == state.puppyStyle
            val bg by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f) else MaterialTheme.colorScheme.surface,
                tween(240),
                label = "pupChoice"
            )
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg)) {
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = unlocked) { viewModel.setPuppyStyle(puppy.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OriginalPuppyPortrait(puppy.id, 76.dp, unlocked = unlocked)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(puppy.name, fontWeight = FontWeight.Black)
                        Text(puppy.description, style = MaterialTheme.typography.bodySmall)
                        if (puppy.redeemOnly) {
                            Text(
                                if (unlocked) "Puppy Code unlocked" else "Special Puppy Code character",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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
private fun V3SettingsScreen(state: GameState, viewModel: GameViewModel) {
    val context = LocalContext.current
    var resetConfirm by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = System.currentTimeMillis() } }
    val cooldownMs = (state.cooldownUntilMs - now).coerceAtLeast(0L)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        V3Header("Settings", "Controls, accessibility, fair play, and app data.")
        Spacer(Modifier.height(14.dp))

        Text("Feedback & motion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        SettingSwitchV3("📳", "Haptic feedback", "Uses the Android vibrator directly for more reliable tap feedback.", state.hapticsEnabled, viewModel::setHapticsEnabled)
        Spacer(Modifier.height(7.dp))
        OutlinedButton(
            onClick = { performPuppyHaptic(context) },
            enabled = state.hapticsEnabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Test haptic") }
        Spacer(Modifier.height(8.dp))
        SettingSwitchV3("✨", "Animations", "Puppy motion and screen transitions.", state.animationsEnabled, viewModel::setAnimationsEnabled)
        Spacer(Modifier.height(8.dp))
        SettingSwitchV3("🔢", "Compact numbers", "Show 1.2K / 3.4M instead of long values.", state.compactNumbers, viewModel::setCompactNumbers)

        Spacer(Modifier.height(18.dp))
        Text("Fair play", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🐶👁️", fontSize = 27.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Pup Eye Fair Play", fontWeight = FontWeight.Black)
                        Text(
                            if (cooldownMs > 0) "Cooldown ${(cooldownMs + 999) / 1000}s" else "Monitoring locally",
                            color = if (cooldownMs > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Fast human tapping is allowed. Pup Eye looks for repeated machine-like timing, impossible intervals, or extreme sustained rates before applying a short cooldown.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(5.dp))
                Text("No permanent bans · no uploads · no device ID · no account tracking", style = MaterialTheme.typography.labelMedium)
                Text("Confirmed cooldowns: ${state.pupEyeStrikes}", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("Puppy Clicker ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Black)
                Text("Package: ${BuildConfig.APPLICATION_ID}", style = MaterialTheme.typography.bodySmall)
                Text("Original Puppy Clicker character art + Android gameplay", style = MaterialTheme.typography.bodySmall)
                Text("Puppy Codes redeemed here: ${state.redeemedCodeIds.size}", style = MaterialTheme.typography.bodySmall)
                Text("Upgrade Tickets: ${state.upgradeTickets}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { resetConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset game progress") }
        Text(
            "Reset keeps Settings, redeemed-code history, and Puppy Code unlocks. Shop upgrades and tickets reset.",
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
            text = { Text("Treats, levels, care progress, missions, Shop upgrades, and tickets will reset. Redeem history and special puppy unlocks stay protected.") },
            confirmButton = { TextButton(onClick = { viewModel.resetGame(); resetConfirm = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingSwitchV3(
    emoji: String,
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
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

@Composable
private fun V3Header(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun performPuppyHaptic(context: Context) {
    try {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return

        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    } catch (_: Throwable) {
        // Haptics are optional; unsupported devices should never crash the game.
    }
}

private fun formatV3(value: Long, compact: Boolean): String {
    if (!compact) return "%,d".format(value)
    return when {
        value >= 1_000_000_000_000L -> "%.2fT".format(value / 1_000_000_000_000.0)
        value >= 1_000_000_000L -> "%.2fB".format(value / 1_000_000_000.0)
        value >= 1_000_000L -> "%.2fM".format(value / 1_000_000.0)
        value >= 1_000L -> "%.1fK".format(value / 1_000.0)
        else -> value.toString()
    }
}
