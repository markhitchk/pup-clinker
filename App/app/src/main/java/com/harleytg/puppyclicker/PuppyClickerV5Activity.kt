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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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

class PuppyClickerV5Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuppyClickerTheme {
                val vm: PuppyClickerV5ViewModel = viewModel()
                PuppyClickerV5App(vm)
            }
        }
    }
}

private enum class V5Tab(val label: String, val emoji: String) {
    PLAY("Play", "🐾"), CARE("Care", "💖"), SHOP("Shop", "🛍️"), PUPS("Pups", "🐶"), SETTINGS("Settings", "⚙️")
}

@Composable
private fun PuppyClickerV5App(viewModel: PuppyClickerV5ViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(V5Tab.PLAY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
                V5Tab.entries.forEach { item ->
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
                V5Tab.PLAY -> V5Play(state, viewModel)
                V5Tab.CARE -> V5CareAndDaily(state, viewModel)
                V5Tab.SHOP -> V5Shop(state, viewModel)
                V5Tab.PUPS -> V5Pups(state, viewModel)
                V5Tab.SETTINGS -> V5Settings(state, viewModel)
            }
        }
    }
}

@Composable
private fun V5Play(state: V5GameState, vm: PuppyClickerV5ViewModel) {
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
            if (state.hapticsEnabled) performV5Haptic(context, true)
            delay(2_500)
            dropVisible = false
        }
    }

    val idle = rememberInfiniteTransition(label = "v5Idle")
    val idleY by idle.animateFloat(
        if (state.animationsEnabled) -3f else 0f,
        if (state.animationsEnabled) 5f else 0f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "v5IdleY"
    )
    val cooldown = (state.cooldownUntilMs - now).coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        V5Header("Puppy Clicker", "${state.puppyName} · ${state.mood}")
        Spacer(Modifier.height(10.dp))
        V5Wallet(state)
        Spacer(Modifier.height(10.dp))

        AnimatedVisibility(dropVisible, enter = fadeIn() + scaleIn(initialScale = 0.75f), exit = fadeOut() + scaleOut(targetScale = 0.85f)) {
            val rarity = state.lastTicketDrop ?: TicketRarity.COMMON
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = rarityContainerV5(rarity)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rarity.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${rarity.displayName} Upgrade Ticket!", fontWeight = FontWeight.Black)
                        Text("Saved in the Ticket Upgrades shop.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (dropVisible) Spacer(Modifier.height(8.dp))

        Surface(
            Modifier.fillMaxWidth().height(420.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.075f)
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    V5NeedPill("💖", state.happiness, Modifier.weight(1f))
                    V5NeedPill("🍖", state.fullness, Modifier.weight(1f))
                    V5NeedPill("⚡", state.energy, Modifier.weight(1f))
                    V5NeedPill("🫧", state.cleanliness, Modifier.weight(1f))
                }

                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(300.dp)
                        .graphicsLayer {
                            translationY = idleY
                            scaleX = tapScale.value
                            scaleY = tapScale.value
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .clickable(enabled = cooldown == 0L) {
                            vm.tapPuppy()
                            if (state.hapticsEnabled) performV5Haptic(context)
                            if (state.animationsEnabled) {
                                scope.launch {
                                    tapScale.snapTo(0.89f)
                                    tapScale.animateTo(1f, spring(dampingRatio = 0.43f, stiffness = 620f))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    V5PuppyPortrait(state.puppyStyle, 276.dp, state.accessory)
                }

                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (cooldown > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ) {
                    Text(
                        if (cooldown > 0) "🐶👁️ Fair-play cooldown · ${(cooldown + 999) / 1000}s"
                        else "Tap ${state.puppyName} · +${formatV5(state.clickPower.toLong(), state.compactNumbers)} 🍪",
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎟️", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("1-in-100 ticket chance per accepted tap", fontWeight = FontWeight.Bold)
                    Text("Fast human taps are okay; machine-like taps do not roll tickets.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun V5CareAndDaily(state: V5GameState, vm: PuppyClickerV5ViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V5Header("Pup Care", "Care for your puppy and finish daily goals.")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = page == 0, onClick = { page = 0 }, label = { Text("💖 Care") }, modifier = Modifier.weight(1f))
            FilterChip(selected = page == 1, onClick = { page = 1 }, label = { Text("📅 Daily") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        if (page == 0) V5CarePanel(state, vm) else V5DailyPanel(state, vm)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun V5CarePanel(state: V5GameState, vm: PuppyClickerV5ViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        V5PuppyPortrait(state.puppyStyle, 125.dp, state.accessory)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(state.puppyName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("${state.mood} · wellness ${state.careScore}%")
            Text("🤝 Bond ${state.bond}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(14.dp))
    V5CareMeter("💖 Happiness", state.happiness)
    Spacer(Modifier.height(7.dp))
    V5CareMeter("🍖 Fullness", state.fullness)
    Spacer(Modifier.height(7.dp))
    V5CareMeter("⚡ Energy", state.energy)
    Spacer(Modifier.height(7.dp))
    V5CareMeter("🫧 Cleanliness", state.cleanliness)
    Spacer(Modifier.height(7.dp))
    V5CareMeter("🤝 Bond", state.bond)

    Spacer(Modifier.height(14.dp))
    Text("Care actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    Text("Different actions affect different needs, so one button can’t max everything.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::feedPuppy, enabled = state.treats >= PuppyClickerV5ViewModel.FEED_COST && state.fullness < 100, modifier = Modifier.weight(1f)) { Text("🍖 Feed") }
        Button(onClick = vm::playWithPuppy, enabled = state.energy >= 12, modifier = Modifier.weight(1f)) { Text("🎾 Play") }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::restPuppy, enabled = state.energy < 100, modifier = Modifier.weight(1f)) { Text("💤 Rest") }
        Button(onClick = vm::groomPuppy, enabled = state.cleanliness < 100, modifier = Modifier.weight(1f)) { Text("🫧 Groom") }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = vm::cuddlePuppy, enabled = state.happiness < 100 || state.bond < 100, modifier = Modifier.fillMaxWidth()) {
        Text("🤗 Cuddle · build bond")
    }

    Spacer(Modifier.height(14.dp))
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
        Column(Modifier.padding(12.dp)) {
            Text("Care bonus", fontWeight = FontWeight.Black)
            Text(if (state.careScore >= 85) "Active: +10% automatic treats." else "Reach 85% wellness for +10% automatic treats.", style = MaterialTheme.typography.bodySmall)
            Text("Tap power is never boosted by care.", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun V5DailyPanel(state: V5GameState, vm: PuppyClickerV5ViewModel) {
    val today = LocalDate.now().toEpochDay()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 36.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${state.dailyStreak} day streak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Claim the daily gift each day to keep it going.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎁", fontSize = 34.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Daily Puppy Gift", fontWeight = FontWeight.Black)
                val nextReward = 150L + (if (state.lastDailyClaimDay == today - 1) state.dailyStreak + 1 else 1) * 25L + state.level * 10L
                Text("${formatV5(nextReward, state.compactNumbers)} treats + mood + bond", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = vm::claimDailyReward, enabled = state.lastDailyClaimDay != today) {
                Text(if (state.lastDailyClaimDay == today) "Claimed" else "Claim")
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("Today’s goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    Text("Daily goals reward treats only. Upgrade Tickets still come from taps.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    V5DailyTask("tap75", "🐾 Tap Time", "Tap your puppy 75 times", state.dailyTaps, 75, 300, state, vm)
    Spacer(Modifier.height(7.dp))
    V5DailyTask("care3", "💖 Good Care", "Complete 3 care actions", state.dailyCareActions, 3, 250, state, vm)
    Spacer(Modifier.height(7.dp))
    V5DailyTask("shop1", "🛍️ Shop Visit", "Buy 1 upgrade", state.dailyShopPurchases, 1, 400, state, vm)
    Spacer(Modifier.height(7.dp))
    V5DailyTask("wellness", "✨ Happy Home", "Reach 90% wellness", state.careScore.toLong(), 90, 350, state, vm)

    Spacer(Modifier.height(16.dp))
    val remaining = ((state.parkReadyAtMs - now).coerceAtLeast(0) + 999) / 1000
    val ready = state.parkActive && remaining == 0L
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌳", fontSize = 32.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Dog Park Adventure", fontWeight = FontWeight.Black)
                Text(if (ready) "Adventure reward ready." else if (state.parkActive) "Back in ${remaining}s" else "60 seconds · uses 20 energy", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = if (ready) vm::claimParkAdventure else vm::startParkAdventure,
                enabled = ready || (!state.parkActive && state.energy >= 20)
            ) { Text(if (ready) "Claim" else if (state.parkActive) "Away" else "Go") }
        }
    }
}

@Composable
private fun V5DailyTask(id: String, title: String, description: String, progress: Long, target: Long, reward: Long, state: V5GameState, vm: PuppyClickerV5ViewModel) {
    val claimed = id in state.claimedDailyTasks
    val complete = progress >= target
    val pct by animateFloatAsState((progress.toFloat() / target.toFloat()).coerceIn(0f, 1f), tween(350), label = "daily")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Black)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
                Text("${progress.coerceAtMost(target)}/$target", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator({ pct }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reward: ${formatV5(reward, state.compactNumbers)} 🍪", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Button(onClick = { vm.claimDailyTask(id) }, enabled = complete && !claimed) { Text(if (claimed) "Claimed" else "Claim") }
            }
        }
    }
}

@Composable
private fun V5Shop(state: V5GameState, vm: PuppyClickerV5ViewModel) {
    var shopTab by rememberSaveable { mutableIntStateOf(0) }
    var redeemOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V5Header("Puppy Shop", "Choose treat upgrades or rarity-ticket upgrades.")
        Spacer(Modifier.height(12.dp))
        V5Wallet(state)
        Spacer(Modifier.height(10.dp))

        OutlinedButton(onClick = { redeemOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("🎫 Redeem Puppy Code")
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = shopTab == 0, onClick = { shopTab = 0 }, label = { Text("🍪 Cookie Upgrades") }, modifier = Modifier.weight(1f))
            FilterChip(selected = shopTab == 1, onClick = { shopTab = 1 }, label = { Text("🎟️ Ticket Upgrades") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        if (shopTab == 0) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)) {
                Text("Basic upgrades use treats only and are intentionally easier to buy. Tap strength still increases only here in the Shop.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            V5_UPGRADES.filter { it.type == V5UpgradeType.COOKIE }.forEach { upgrade ->
                V5CookieUpgradeCard(state, upgrade, vm)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            V5TicketInventory(state)
            Spacer(Modifier.height(10.dp))
            V5_UPGRADES.filter { it.type == V5UpgradeType.TICKET }.forEach { upgrade ->
                V5TicketUpgradeCard(state, upgrade, vm)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (redeemOpen) V5RedeemDialog(vm) { redeemOpen = false }
}

@Composable
private fun V5CookieUpgradeCard(state: V5GameState, upgrade: V5Upgrade, vm: PuppyClickerV5ViewModel) {
    val owned = state.upgrades[upgrade.id] ?: 0
    val cost = v5CookieCost(upgrade, owned)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(upgrade.emoji, fontSize = 31.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(upgrade.name, fontWeight = FontWeight.Black)
                Text(upgrade.description, style = MaterialTheme.typography.bodySmall)
                Text("Owned $owned", style = MaterialTheme.typography.labelMedium)
            }
            Button(onClick = { vm.buyCookieUpgrade(upgrade) }, enabled = state.treats >= cost) {
                Text("${formatV5(cost, state.compactNumbers)} 🍪")
            }
        }
    }
}

@Composable
private fun V5TicketUpgradeCard(state: V5GameState, upgrade: V5Upgrade, vm: PuppyClickerV5ViewModel) {
    val owned = state.upgrades[upgrade.id] ?: 0
    val rarity = upgrade.rarity ?: TicketRarity.COMMON
    val tickets = v5TicketCost(upgrade, owned)
    val ownedTickets = state.ticketInventory[rarity] ?: 0
    val treatFee = v5CookieCost(upgrade, owned)
    val canBuy = ownedTickets >= tickets && state.treats >= treatFee

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = rarityContainerV5(rarity).copy(alpha = 0.65f))) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(upgrade.emoji, fontSize = 31.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(upgrade.name, fontWeight = FontWeight.Black)
                    Text(upgrade.description, style = MaterialTheme.typography.bodySmall)
                    Text("Owned $owned", style = MaterialTheme.typography.labelMedium)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                    Text("${rarity.emoji} ${rarity.displayName}", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Need $tickets ${rarity.displayName} ticket${if (tickets == 1) "" else "s"} · you have $ownedTickets", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("+ ${formatV5(treatFee, state.compactNumbers)} 🍪 small treat fee", style = MaterialTheme.typography.labelMedium)
                }
                Button(onClick = { vm.buyTicketUpgrade(upgrade) }, enabled = canBuy) { Text("Upgrade") }
            }
        }
    }
}

@Composable
private fun V5TicketInventory(state: V5GameState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text("🎟️ Ticket inventory", fontWeight = FontWeight.Black)
            Text("Tickets drop only from legitimate taps.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            TicketRarity.entries.forEach { rarity ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("${rarity.emoji} ${rarity.displayName}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("×${state.ticketInventory[rarity] ?: 0}", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun V5RedeemDialog(vm: PuppyClickerV5ViewModel, close: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var success by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("🎫 Redeem Puppy Code") },
        text = {
            Column {
                Text("Codes work locally on this device. They can unlock treats or special puppies, never Upgrade Tickets.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(64); message = null },
                    label = { Text("Puppy Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = if (success) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer) {
                        Text(it, Modifier.padding(9.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val result = vm.redeemCode(code)
                success = result.success
                message = result.message
                if (result.success) code = ""
            }, enabled = code.isNotBlank()) { Text("Redeem") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Close") } }
    )
}

@Composable
private fun V5Pups(state: V5GameState, vm: PuppyClickerV5ViewModel) {
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V5Header("Puppy Collection", "Same original character style, now with visibly different fur looks.")
        Spacer(Modifier.height(14.dp))
        PUPPY_STYLES.forEach { puppy ->
            val unlocked = puppy.id in state.unlockedPuppies
            val selected = state.puppyStyle == puppy.id
            Card(
                Modifier.fillMaxWidth().clickable(enabled = unlocked) { vm.setPuppyStyle(puppy.id) },
                colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    V5PuppyPortrait(puppy.id, 86.dp, unlocked = unlocked)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(puppy.name, fontWeight = FontWeight.Black)
                        Text(v5FurDescription(puppy.id), style = MaterialTheme.typography.bodySmall)
                        if (puppy.redeemOnly) Text(if (unlocked) "Puppy Code unlocked" else "Special Puppy Code pup", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(if (selected) "✓" else if (unlocked) "Choose" else "🔒", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { renameOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Rename ${state.puppyName}") }
        Spacer(Modifier.height(12.dp))
        Text("Accessories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PuppyClickerV5ViewModel.ACCESSORIES.forEach { accessory ->
                FilterChip(selected = state.accessory == accessory, onClick = { vm.setAccessory(accessory) }, label = { Text(accessory) })
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    if (renameOpen) {
        var name by rememberSaveable { mutableStateOf(state.puppyName) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename puppy") },
            text = { OutlinedTextField(name, { name = it.take(18) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renamePuppy(name); renameOpen = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun V5Settings(state: V5GameState, vm: PuppyClickerV5ViewModel) {
    val context = LocalContext.current
    var reset by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V5Header("Settings", "Feedback, accessibility, fair play, and game data.")
        Spacer(Modifier.height(14.dp))
        V5Switch("📳", "Haptic feedback", "Direct Android vibration feedback.", state.hapticsEnabled, vm::setHapticsEnabled)
        Spacer(Modifier.height(7.dp))
        OutlinedButton(onClick = { performV5Haptic(context, true) }, enabled = state.hapticsEnabled, modifier = Modifier.fillMaxWidth()) { Text("Test haptic") }
        Spacer(Modifier.height(8.dp))
        V5Switch("✨", "Animations", "Puppy idle and tap motion.", state.animationsEnabled, vm::setAnimationsEnabled)
        Spacer(Modifier.height(8.dp))
        V5Switch("🔢", "Compact numbers", "Use K/M/B abbreviations.", state.compactNumbers, vm::setCompactNumbers)

        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("🐶👁️ Pup Eye Fair Play", fontWeight = FontWeight.Black)
                Text("Fast human tapping is allowed. Pup Eye looks for repeated machine-like timing and extreme sustained rates.", style = MaterialTheme.typography.bodySmall)
                Text("Suspicious taps do not receive ticket rolls. No permanent bans or uploads.", style = MaterialTheme.typography.bodySmall)
                Text("Confirmed cooldowns: ${state.pupEyeStrikes}", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("About", fontWeight = FontWeight.Black)
                Text("Puppy Clicker ${BuildConfig.VERSION_NAME}")
                Text("Package: ${BuildConfig.APPLICATION_ID}", style = MaterialTheme.typography.bodySmall)
                Text("Tickets found: ${state.totalTicketsFound} · owned: ${state.ticketsOwned}", style = MaterialTheme.typography.bodySmall)
                Text("Care actions: ${state.careActions} · bond: ${state.bond}%", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { reset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset game progress") }
        Spacer(Modifier.height(18.dp))
    }

    if (reset) {
        AlertDialog(
            onDismissRequest = { reset = false },
            title = { Text("Reset game progress?") },
            text = { Text("Treats, upgrades, care, daily progress, and tickets will reset. Redeemed special-puppy unlocks stay protected.") },
            confirmButton = { TextButton(onClick = { vm.resetGame(); reset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun V5Wallet(state: V5GameState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🍪 Treats", style = MaterialTheme.typography.labelMedium)
                Text(formatV5(state.treats, state.compactNumbers), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
            V5MiniStat("🎟️", state.ticketsOwned.toString(), "tickets")
            Spacer(Modifier.width(10.dp))
            V5MiniStat("👆", formatV5(state.clickPower.toLong(), state.compactNumbers), "per tap")
            Spacer(Modifier.width(10.dp))
            V5MiniStat("⏱️", formatV5(state.autoPerSecond.toLong(), state.compactNumbers), "per sec")
        }
    }
}

@Composable
private fun V5MiniStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun V5NeedPill(icon: String, value: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(value / 100f, tween(400), label = "need")
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 5.dp)) {
            Text("$icon $value%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun V5CareMeter(label: String, value: Int) {
    val progress by animateFloatAsState(value / 100f, tween(400), label = "care")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontWeight = FontWeight.Bold)
                Text("$value%", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun V5Switch(icon: String, title: String, description: String, checked: Boolean, set: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked, set)
        }
    }
}

@Composable
private fun V5PuppyPortrait(styleId: String, size: Dp, accessory: String = "None", unlocked: Boolean = true) {
    val style = PUPPY_STYLES.firstOrNull { it.id == styleId } ?: PUPPY_STYLES.first()
    val background = when (styleId) {
        "golden" -> Color(0xFFFFE6A6)
        "poodle" -> Color(0xFFFFD9E8)
        "spotty" -> Color(0xFFE0E0E0)
        "midnight" -> Color(0xFFD7D9FF)
        "cloud" -> Color(0xFFE5F3FF)
        else -> MaterialTheme.colorScheme.surface
    }
    val furFilter = when (styleId) {
        "golden" -> ColorFilter.tint(Color(0xFFFFD47A), BlendMode.Modulate)
        "poodle" -> ColorFilter.tint(Color(0xFFFFC5D7), BlendMode.Modulate)
        "spotty" -> ColorFilter.tint(Color(0xFF9A9A9A), BlendMode.Modulate)
        "midnight" -> ColorFilter.tint(Color(0xFF7185D8), BlendMode.Modulate)
        "cloud" -> ColorFilter.tint(Color(0xFFD8EEFF), BlendMode.Modulate)
        else -> null
    }

    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.source_pup),
            contentDescription = style.name,
            modifier = Modifier.size(size * 0.90f),
            contentScale = ContentScale.Fit,
            colorFilter = furFilter
        )
        Surface(Modifier.align(Alignment.TopEnd).padding(size * 0.05f), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
            Text(style.emoji, fontSize = (size.value * 0.13f).sp, modifier = Modifier.padding(size * 0.025f))
        }
        if (!unlocked) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Text("🔒", fontSize = (size.value * 0.22f).sp) }
        } else {
            when (accessory) {
                "Bandana" -> Text("🧣", fontSize = (size.value * 0.18f).sp, modifier = Modifier.align(Alignment.BottomCenter))
                "Bow" -> Text("🎀", fontSize = (size.value * 0.16f).sp, modifier = Modifier.align(Alignment.TopCenter))
                "Crown" -> Text("👑", fontSize = (size.value * 0.18f).sp, modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

private fun v5FurDescription(styleId: String): String = when (styleId) {
    "classic" -> "Original warm tan fur."
    "golden" -> "Bright golden fur with a sunny look."
    "poodle" -> "Soft blush-cream fur with a cute pastel look."
    "spotty" -> "Cool charcoal-gray fur and darker styling."
    "midnight" -> "Rare blue-indigo moonlit fur."
    "cloud" -> "Rare pale sky-blue cloud fur."
    else -> "Original Puppy Clicker style."
}

@Composable
private fun V5Header(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(streamedRepoLogoPainter(RepoLogoAsset.PUPPY_CLICKER, R.drawable.source_logo), null, Modifier.size(50.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun rarityContainerV5(rarity: TicketRarity): Color = when (rarity) {
    TicketRarity.COMMON -> MaterialTheme.colorScheme.surfaceVariant
    TicketRarity.UNCOMMON -> MaterialTheme.colorScheme.secondaryContainer
    TicketRarity.RARE -> MaterialTheme.colorScheme.primaryContainer
    TicketRarity.EPIC -> MaterialTheme.colorScheme.tertiaryContainer
    TicketRarity.LEGENDARY -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
}

private fun performV5Haptic(context: Context, strong: Boolean = false) {
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
    } catch (_: Throwable) { }
}

private fun formatV5(value: Long, compact: Boolean): String {
    if (!compact) return "%,d".format(value)
    return when {
        value >= 1_000_000_000_000L -> "%.2fT".format(value / 1_000_000_000_000.0)
        value >= 1_000_000_000L -> "%.2fB".format(value / 1_000_000_000.0)
        value >= 1_000_000L -> "%.2fM".format(value / 1_000_000.0)
        value >= 1_000L -> "%.1fK".format(value / 1_000.0)
        else -> value.toString()
    }
}
