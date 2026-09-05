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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harleytg.puppyclicker.ui.theme.PuppyClickerTheme
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PuppyClickerV4Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuppyClickerTheme {
                val vm: GameViewModel = viewModel()
                PuppyClickerV4App(vm)
            }
        }
    }
}

private enum class V4Tab(val label: String, val emoji: String) {
    PLAY("Play", "🐾"), CARE("Care", "💖"), SHOP("Shop", "🛍️"), PUPS("Pups", "🐶"), SETTINGS("Settings", "⚙️")
}

@Composable
private fun PuppyClickerV4App(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(V4Tab.PLAY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
                V4Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.emoji, fontSize = 19.sp) },
                        label = { Text(item.label, fontSize = 10.sp, maxLines = 1) }
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
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (tab) {
                V4Tab.PLAY -> V4Play(state, viewModel)
                V4Tab.CARE -> V4Care(state, viewModel)
                V4Tab.SHOP -> V4Shop(state, viewModel)
                V4Tab.PUPS -> V4Pups(state, viewModel)
                V4Tab.SETTINGS -> V4Settings(state, viewModel)
            }
        }
    }
}

@Composable
private fun V4Play(state: GameState, viewModel: GameViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tapScale = remember { Animatable(1f) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dropVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }

    LaunchedEffect(state.ticketDropSerial) {
        if (state.ticketDropSerial > 0 && state.lastTicketDrop != null) {
            dropVisible = true
            if (state.hapticsEnabled) performV4Haptic(context, strong = true)
            delay(2_400)
            dropVisible = false
        }
    }

    val cooldownMs = (state.cooldownUntilMs - now).coerceAtLeast(0L)
    val idle = rememberInfiniteTransition(label = "pupIdleV4")
    val idleY by idle.animateFloat(
        if (state.animationsEnabled) -3f else 0f,
        if (state.animationsEnabled) 5f else 0f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pupIdleYV4"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        V4Header("Puppy Clicker", "${state.puppyName} · ${state.mood}")
        Spacer(Modifier.height(10.dp))
        V4Wallet(state)
        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
            visible = dropVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.75f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            val rarity = state.lastTicketDrop ?: TicketRarity.COMMON
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = rarityContainer(rarity)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rarity.emoji, fontSize = 32.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${rarity.displayName} Upgrade Ticket found!", fontWeight = FontWeight.Black)
                        Text("-${rarity.discountPercent}% on one Shop upgrade", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (dropVisible) Spacer(Modifier.height(10.dp))

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
                    V4NeedPill("💖", state.happiness, Modifier.weight(1f))
                    V4NeedPill("🍖", state.fullness, Modifier.weight(1f))
                    V4NeedPill("⚡", state.energy, Modifier.weight(1f))
                }

                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(296.dp)
                        .graphicsLayer {
                            translationY = idleY
                            scaleX = tapScale.value
                            scaleY = tapScale.value
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                        .clickable(enabled = cooldownMs == 0L) {
                            viewModel.tapPuppy()
                            if (state.hapticsEnabled) performV4Haptic(context)
                            if (state.animationsEnabled) {
                                scope.launch {
                                    tapScale.snapTo(0.89f)
                                    tapScale.animateTo(1f, spring(dampingRatio = 0.43f, stiffness = 620f))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    V4PuppyPortrait(state.puppyStyle, 270.dp, state.accessory)
                }

                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (cooldownMs > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ) {
                    Text(
                        if (cooldownMs > 0) "🐶👁️ Fair-play cooldown · ${(cooldownMs + 999) / 1000}s"
                        else "Tap ${state.puppyName} · +${formatV4(state.clickPower.toLong(), state.compactNumbers)} 🍪",
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) {
            Column(Modifier.padding(12.dp)) {
                Text("🎟️ Upgrade Ticket drop", fontWeight = FontWeight.Black)
                Text("Every accepted tap rolls 1–100. A ticket drops on 1 roll out of 100.", style = MaterialTheme.typography.bodySmall)
                Text("Auto-clicker-like taps do not receive ticket rolls.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun V4Wallet(state: GameState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🍪 Treats", style = MaterialTheme.typography.labelMedium)
                Text(formatV4(state.treats, state.compactNumbers), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
            V4MiniStat("🎟️", state.upgradeTickets.toString(), "tickets")
            Spacer(Modifier.width(12.dp))
            V4MiniStat("👆", formatV4(state.clickPower.toLong(), state.compactNumbers), "per tap")
            Spacer(Modifier.width(12.dp))
            V4MiniStat("⏱️", formatV4(state.autoPerSecond.toLong(), state.compactNumbers), "per sec")
        }
    }
}

@Composable
private fun V4MiniStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun V4NeedPill(icon: String, value: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(value / 100f, tween(450), label = "needV4")
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text("$icon $value%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun V4Care(state: GameState, viewModel: GameViewModel) {
    val today = LocalDate.now().toEpochDay()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V4Header("Care", "Care rewards treats and mood — tickets still come only from taps.")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            V4PuppyPortrait(state.puppyStyle, 118.dp, state.accessory)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(state.puppyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("${state.mood} · care ${state.careScore}%")
            }
        }
        Spacer(Modifier.height(14.dp))
        V4CareMeter("💖 Happiness", state.happiness)
        Spacer(Modifier.height(8.dp))
        V4CareMeter("🍖 Fullness", state.fullness)
        Spacer(Modifier.height(8.dp))
        V4CareMeter("⚡ Energy", state.energy)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::feedPuppy, enabled = state.treats >= GameViewModel.FEED_COST && state.fullness < 100, modifier = Modifier.weight(1f)) { Text("Feed") }
            Button(onClick = viewModel::playWithPuppy, enabled = state.energy >= 10, modifier = Modifier.weight(1f)) { Text("Play") }
            Button(onClick = viewModel::restPuppy, enabled = state.energy < 100, modifier = Modifier.weight(1f)) { Text("Rest") }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎁", fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daily Puppy Gift", fontWeight = FontWeight.Black)
                    Text("Daily treats only — no Upgrade Tickets.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = viewModel::claimDailyReward, enabled = state.lastDailyClaimDay != today) {
                    Text(if (state.lastDailyClaimDay == today) "Claimed" else "Claim")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val remaining = ((state.parkReadyAtMs - now).coerceAtLeast(0) + 999) / 1000
        val ready = state.parkActive && remaining == 0L
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🌳", fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dog Park Adventure", fontWeight = FontWeight.Black)
                    Text(if (ready) "Reward ready" else if (state.parkActive) "Returns in ${remaining}s" else "60-second adventure", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = if (ready) viewModel::claimParkAdventure else viewModel::startParkAdventure, enabled = ready || (!state.parkActive && state.energy >= 20)) {
                    Text(if (ready) "Claim" else if (state.parkActive) "Away" else "Go")
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun V4CareMeter(label: String, value: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontWeight = FontWeight.Bold)
                Text("$value%", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator({ value / 100f }, Modifier.fillMaxWidth().height(7.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun V4Shop(state: GameState, viewModel: GameViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    var codeMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var codeSuccess by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V4Header("Puppy Shop", "Spend treats or use rarity tickets to make upgrades cheaper.")
        Spacer(Modifier.height(12.dp))
        V4Wallet(state)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("🎟️ Upgrade Ticket inventory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text("Base drop chance: 1 in 100 accepted taps", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                TicketRarity.entries.forEach { rarity ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(rarity.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rarity.displayName, fontWeight = FontWeight.Bold)
                            Text("${rarity.rarityWeight}% of ticket drops · ${rarity.discountPercent}% upgrade discount", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("×${state.ticketInventory[rarity] ?: 0}", fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Found from taps: ${state.totalTicketsFound}", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("🎫 Puppy Codes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text("Local redeem codes can grant treats or special pups. They cannot grant Upgrade Tickets.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(64); codeMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Enter Puppy Code") }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val result = viewModel.redeemCode(code)
                        codeSuccess = result.success
                        codeMessage = result.message
                        if (result.success) code = ""
                    },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Redeem") }
                codeMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (codeSuccess) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) { Text(message, Modifier.padding(10.dp), fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Upgrades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("Only Shop purchases can increase treats per tap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        UPGRADES.forEach { upgrade ->
            V4UpgradeCard(state, upgrade, viewModel)
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun V4UpgradeCard(state: GameState, upgrade: Upgrade, viewModel: GameViewModel) {
    val owned = state.upgrades[upgrade.id] ?: 0
    val fullCost = upgradeCost(upgrade, owned)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(upgrade.emoji, fontSize = 32.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(upgrade.name, fontWeight = FontWeight.Black)
                    Text(upgrade.description, style = MaterialTheme.typography.bodySmall)
                    Text("Owned $owned", style = MaterialTheme.typography.labelMedium)
                }
                Button(onClick = { viewModel.buyUpgrade(upgrade) }, enabled = state.treats >= fullCost) {
                    Text("${formatV4(fullCost, state.compactNumbers)} 🍪")
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Use a rarity ticket", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TicketRarity.entries.forEach { rarity ->
                    val count = state.ticketInventory[rarity] ?: 0
                    val discounted = rarityTicketUpgradeCost(upgrade, owned, rarity)
                    AssistChip(
                        onClick = { viewModel.buyUpgrade(upgrade, rarity) },
                        enabled = count > 0 && state.treats >= discounted,
                        label = {
                            Text("${rarity.emoji} ${rarity.displayName} ×$count · ${formatV4(discounted, state.compactNumbers)}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun V4Pups(state: GameState, viewModel: GameViewModel) {
    var rename by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V4Header("Puppy Collection", "Every style keeps the original Puppy Clicker character as the base.")
        Spacer(Modifier.height(14.dp))
        PUPPY_STYLES.forEach { puppy ->
            val unlocked = puppy.id in state.unlockedPuppies
            val selected = puppy.id == state.puppyStyle
            Card(
                Modifier.fillMaxWidth().clickable(enabled = unlocked) { viewModel.setPuppyStyle(puppy.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    V4PuppyPortrait(puppy.id, 78.dp, unlocked = unlocked)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(puppy.name, fontWeight = FontWeight.Black)
                        Text(puppy.description, style = MaterialTheme.typography.bodySmall)
                        if (puppy.redeemOnly) Text(if (unlocked) "Puppy Code unlocked" else "Special Puppy Code pup", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(if (selected) "✓" else if (unlocked) "Choose" else "Locked", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { rename = true }, modifier = Modifier.fillMaxWidth()) { Text("Rename ${state.puppyName}") }
        Spacer(Modifier.height(10.dp))
        Text("Accessories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GameViewModel.ACCESSORIES.forEach { accessory ->
                FilterChip(selected = state.accessory == accessory, onClick = { viewModel.setAccessory(accessory) }, label = { Text(accessory) })
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    if (rename) {
        var name by rememberSaveable { mutableStateOf(state.puppyName) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename puppy") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(18) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.renamePuppy(name); rename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun V4Settings(state: GameState, viewModel: GameViewModel) {
    val context = LocalContext.current
    var reset by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V4Header("Settings", "Feedback, accessibility, fair play, and game data.")
        Spacer(Modifier.height(14.dp))
        V4Switch("📳", "Haptic feedback", "Direct Android vibrator feedback.", state.hapticsEnabled, viewModel::setHapticsEnabled)
        Spacer(Modifier.height(7.dp))
        OutlinedButton(onClick = { performV4Haptic(context, strong = true) }, enabled = state.hapticsEnabled, modifier = Modifier.fillMaxWidth()) { Text("Test haptic") }
        Spacer(Modifier.height(8.dp))
        V4Switch("✨", "Animations", "Puppy motion and tap animation.", state.animationsEnabled, viewModel::setAnimationsEnabled)
        Spacer(Modifier.height(8.dp))
        V4Switch("🔢", "Compact numbers", "Use K/M/B abbreviations.", state.compactNumbers, viewModel::setCompactNumbers)

        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("🐶👁️ Pup Eye Fair Play", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text("Fast human tapping is allowed. Pup Eye looks for repeated machine-like timing, impossible intervals, and extreme sustained rates.", style = MaterialTheme.typography.bodySmall)
                Text("Suspicious taps never receive Upgrade Ticket rolls.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("No permanent bans · no uploads · local only", style = MaterialTheme.typography.labelMedium)
                Text("Confirmed cooldowns: ${state.pupEyeStrikes}", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text("Puppy Clicker ${BuildConfig.VERSION_NAME}")
                Text("Package: ${BuildConfig.APPLICATION_ID}", style = MaterialTheme.typography.bodySmall)
                Text("Tickets found: ${state.totalTicketsFound}", style = MaterialTheme.typography.bodySmall)
                Text("Tickets owned: ${state.upgradeTickets}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { reset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset game progress") }
        Spacer(Modifier.height(18.dp))
    }

    if (reset) {
        AlertDialog(
            onDismissRequest = { reset = false },
            title = { Text("Reset game progress?") },
            text = { Text("Treats, upgrades, care progress, and rarity tickets will reset. Redeem history and special puppy unlocks stay protected.") },
            confirmButton = { TextButton(onClick = { viewModel.resetGame(); reset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun V4Switch(icon: String, title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
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
private fun V4PuppyPortrait(styleId: String, size: Dp, accessory: String = "None", unlocked: Boolean = true) {
    val style = PUPPY_STYLES.firstOrNull { it.id == styleId } ?: PUPPY_STYLES.first()
    val bg = when (style.id) {
        "golden" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        "poodle" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.60f)
        "spotty" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        "midnight" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        "cloud" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    }

    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.source_pup),
            contentDescription = style.name,
            modifier = Modifier.size(size * 0.90f),
            contentScale = ContentScale.Fit
        )
        Surface(Modifier.align(Alignment.TopEnd).padding(size * 0.05f), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
            Text(style.emoji, fontSize = (size.value * 0.13f).sp, modifier = Modifier.padding(size * 0.025f))
        }
        if (!unlocked) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f)), contentAlignment = Alignment.Center) {
                Text("🔒", fontSize = (size.value * 0.22f).sp)
            }
        } else {
            when (accessory) {
                "Bandana" -> Text("🧣", fontSize = (size.value * 0.18f).sp, modifier = Modifier.align(Alignment.BottomCenter))
                "Bow" -> Text("🎀", fontSize = (size.value * 0.16f).sp, modifier = Modifier.align(Alignment.TopCenter))
                "Crown" -> Text("👑", fontSize = (size.value * 0.18f).sp, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun V4Header(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painterResource(R.drawable.source_logo), contentDescription = null, modifier = Modifier.size(50.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun rarityContainer(rarity: TicketRarity) = when (rarity) {
    TicketRarity.COMMON -> MaterialTheme.colorScheme.surfaceVariant
    TicketRarity.UNCOMMON -> MaterialTheme.colorScheme.secondaryContainer
    TicketRarity.RARE -> MaterialTheme.colorScheme.primaryContainer
    TicketRarity.EPIC -> MaterialTheme.colorScheme.tertiaryContainer
    TicketRarity.LEGENDARY -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
}

private fun performV4Haptic(context: Context, strong: Boolean = false) {
    try {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !strong) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(if (strong) 45L else 18L, if (strong) 180 else VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    } catch (_: Throwable) {
        // Optional feedback must never crash gameplay.
    }
}

private fun formatV4(value: Long, compact: Boolean): String {
    if (!compact) return "%,d".format(value)
    return when {
        value >= 1_000_000_000_000L -> "%.2fT".format(value / 1_000_000_000_000.0)
        value >= 1_000_000_000L -> "%.2fB".format(value / 1_000_000_000.0)
        value >= 1_000_000L -> "%.2fM".format(value / 1_000_000.0)
        value >= 1_000L -> "%.1fK".format(value / 1_000.0)
        else -> value.toString()
    }
}
