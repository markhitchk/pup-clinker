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

class PuppyClickerV6Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuppyClickerTheme {
                val vm: PuppyClickerV6ViewModel = viewModel()
                PuppyClickerV6App(vm)
            }
        }
    }
}

private enum class V6Tab(val label: String, val emoji: String) {
    PLAY("Play", "🐾"), CARE("Care", "💖"), SHOP("Shop", "🛍️"), PUPS("Pups", "🐶"), SETTINGS("Settings", "⚙️")
}

@Composable
private fun PuppyClickerV6App(vm: PuppyClickerV6ViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
                V6Tab.entries.forEach { item ->
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
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (tab) {
                V6Tab.PLAY -> V6Play(state, vm)
                V6Tab.CARE -> V6CareAndDaily(state, vm)
                V6Tab.SHOP -> V6Shop(state, vm)
                V6Tab.PUPS -> V6Pups(state, vm)
                V6Tab.SETTINGS -> V6Settings(state, vm)
            }
        }
    }
}

@Composable
private fun V6Play(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tapScale = remember { Animatable(1f) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ticketVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) { delay(250); now = System.currentTimeMillis() }
    }
    LaunchedEffect(state.ticketDropSerial) {
        if (state.ticketDropSerial > 0 && state.lastTicketDrop != null) {
            ticketVisible = true
            if (state.hapticsEnabled) performV6Haptic(context, true)
            delay(2_000)
            ticketVisible = false
        }
    }

    val idle = rememberInfiniteTransition(label = "idle")
    val idleY by idle.animateFloat(
        if (state.animationsEnabled) -3f else 0f,
        if (state.animationsEnabled) 5f else 0f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleY"
    )
    val cooldown = (state.cooldownUntilMs - now).coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        V6Header("Puppy Clicker", "${state.puppyName} · ${state.mood}")
        Spacer(Modifier.height(10.dp))
        V6Wallet(state)
        Spacer(Modifier.height(10.dp))

        if (state.prestigeCount > 0) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)) {
                Text("⭐ Prestige ${state.prestigeCount} · ${state.skillPoints} unspent skill point${if (state.skillPoints == 1) "" else "s"}", Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }

        AnimatedVisibility(ticketVisible, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            val rarity = state.lastTicketDrop ?: TicketRarity.COMMON
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = rarityContainerV6(rarity)) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rarity.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${rarity.displayName} Upgrade Ticket!", fontWeight = FontWeight.Black)
                        Text("Added to Ticket Upgrades.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (ticketVisible) Spacer(Modifier.height(8.dp))

        Surface(
            Modifier.fillMaxWidth().height(410.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.075f)
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    V6NeedPill("💖", state.happiness, Modifier.weight(1f))
                    V6NeedPill("🍖", state.fullness, Modifier.weight(1f))
                    V6NeedPill("⚡", state.energy, Modifier.weight(1f))
                    V6NeedPill("🫧", state.cleanliness, Modifier.weight(1f))
                }

                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(294.dp)
                        .graphicsLayer {
                            translationY = idleY
                            scaleX = tapScale.value
                            scaleY = tapScale.value
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .clickable(enabled = cooldown == 0L) {
                            vm.tapPuppy()
                            if (state.hapticsEnabled) performV6Haptic(context)
                            if (state.animationsEnabled) {
                                scope.launch {
                                    tapScale.snapTo(0.89f)
                                    tapScale.animateTo(1f, spring(dampingRatio = 0.43f, stiffness = 620f))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    V6PuppyPortrait(state.puppyStyle, 270.dp, state.accessory)
                }

                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (cooldown > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ) {
                    Text(
                        if (cooldown > 0) "🐶👁️ Fair-play cooldown · ${(cooldown + 999) / 1000}s"
                        else "Tap ${state.puppyName} · +${formatV6(state.clickPower.toLong(), state.compactNumbers)} 🍪",
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎟️", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("50/50 ticket roll per accepted tap", fontWeight = FontWeight.Black)
                    Text("Common and Uncommon dominate drops; Rare, Epic and Legendary stay much harder. Machine-like taps receive no ticket roll.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun V6CareAndDaily(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V6Header("Pup Care", "Care, bond, daily rewards and adventures.")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = page == 0, onClick = { page = 0 }, label = { Text("💖 Care") }, modifier = Modifier.weight(1f))
            FilterChip(selected = page == 1, onClick = { page = 1 }, label = { Text("📅 Daily") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        if (page == 0) V6CarePanel(state, vm) else V6DailyPanel(state, vm)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun V6CarePanel(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        V6PuppyPortrait(state.puppyStyle, 125.dp, state.accessory)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(state.puppyName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("${state.mood} · wellness ${state.careScore}%")
            Text("🤝 Bond ${state.bond}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(14.dp))
    V6CareMeter("💖 Happiness", state.happiness)
    Spacer(Modifier.height(7.dp))
    V6CareMeter("🍖 Fullness", state.fullness)
    Spacer(Modifier.height(7.dp))
    V6CareMeter("⚡ Energy", state.energy)
    Spacer(Modifier.height(7.dp))
    V6CareMeter("🫧 Cleanliness", state.cleanliness)
    Spacer(Modifier.height(7.dp))
    V6CareMeter("🤝 Bond", state.bond)

    Spacer(Modifier.height(14.dp))
    Text("Care actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(9.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::feedPuppy, enabled = state.treats >= PuppyClickerV6ViewModel.FEED_COST && state.fullness < 100, modifier = Modifier.weight(1f)) { Text("🍖 Feed") }
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
            Text(if (state.careScore >= 85) "Active: +10% live automatic treats." else "Reach 85% wellness for +10% live automatic treats.", style = MaterialTheme.typography.bodySmall)
            Text("Off-app AFK treats remain fixed at 1,000 per 24 hours.", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun V6DailyPanel(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    val today = LocalDate.now().toEpochDay()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 36.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("${state.dailyStreak} day streak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Daily rewards give treats, never Upgrade Tickets.", style = MaterialTheme.typography.bodySmall)
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
                Text("${formatV6(nextReward, state.compactNumbers)} treats + mood + bond", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = vm::claimDailyReward, enabled = state.lastDailyClaimDay != today) {
                Text(if (state.lastDailyClaimDay == today) "Claimed" else "Claim")
            }
        }
    }

    Spacer(Modifier.height(15.dp))
    Text("Today’s goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(8.dp))
    V6DailyTask("tap75", "🐾 Tap Time", "Tap your puppy 75 times", state.dailyTaps, 75, 300, state, vm)
    Spacer(Modifier.height(7.dp))
    V6DailyTask("care3", "💖 Good Care", "Complete 3 care actions", state.dailyCareActions, 3, 250, state, vm)
    Spacer(Modifier.height(7.dp))
    V6DailyTask("shop1", "🛍️ Shop Visit", "Buy 1 upgrade", state.dailyShopPurchases, 1, 400, state, vm)
    Spacer(Modifier.height(7.dp))
    V6DailyTask("wellness", "✨ Happy Home", "Reach 90% wellness", state.careScore.toLong(), 90, 350, state, vm)

    Spacer(Modifier.height(15.dp))
    val remaining = ((state.parkReadyAtMs - now).coerceAtLeast(0L) + 999L) / 1000L
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
private fun V6DailyTask(id: String, title: String, description: String, progress: Long, target: Long, reward: Long, state: V6GameState, vm: PuppyClickerV6ViewModel) {
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
                Text("Reward: ${formatV6(reward, state.compactNumbers)} 🍪", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Button(onClick = { vm.claimDailyTask(id) }, enabled = complete && !claimed) { Text(if (claimed) "Claimed" else "Claim") }
            }
        }
    }
}

@Composable
private fun V6Shop(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    var shopTab by rememberSaveable { mutableIntStateOf(0) }
    var redeemOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V6Header("Puppy Shop", "Cookie upgrades, rarity-ticket upgrades and Puppy Codes.")
        Spacer(Modifier.height(12.dp))
        V6Wallet(state)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { redeemOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("🎫 Redeem Puppy Code") }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = shopTab == 0, onClick = { shopTab = 0 }, label = { Text("🍪 Cookie Upgrades") }, modifier = Modifier.weight(1f))
            FilterChip(selected = shopTab == 1, onClick = { shopTab = 1 }, label = { Text("🎟️ Ticket Upgrades") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        if (shopTab == 0) {
            if ((state.prestigeSkills[PrestigeSkill.SMART_SHOPPER] ?: 0) > 0) {
                Text("⭐ Smart Shopper discount active", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
            }
            V5_UPGRADES.filter { it.type == V5UpgradeType.COOKIE }.forEach { upgrade ->
                V6CookieUpgradeCard(state, upgrade, vm)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            V6TicketInventory(state)
            Spacer(Modifier.height(10.dp))
            V5_UPGRADES.filter { it.type == V5UpgradeType.TICKET }.forEach { upgrade ->
                V6TicketUpgradeCard(state, upgrade, vm)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (redeemOpen) V6RedeemDialog(vm) { redeemOpen = false }
}

@Composable
private fun V6CookieUpgradeCard(state: V6GameState, upgrade: V5Upgrade, vm: PuppyClickerV6ViewModel) {
    val owned = state.upgrades[upgrade.id] ?: 0
    val cost = v6UpgradeTreatCost(state, upgrade)
    val skill = if (upgrade.effect == V5UpgradeEffect.CLICK) state.prestigeSkills[PrestigeSkill.TAP_TRAINING] ?: 0 else state.prestigeSkills[PrestigeSkill.AUTO_TRAINING] ?: 0
    val actual = upgrade.amount + skill
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(upgrade.emoji, fontSize = 31.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(upgrade.name, fontWeight = FontWeight.Black)
                Text(if (skill > 0) "${upgrade.description} · Prestige value +$actual" else upgrade.description, style = MaterialTheme.typography.bodySmall)
                Text("Owned $owned", style = MaterialTheme.typography.labelMedium)
            }
            Button(onClick = { vm.buyCookieUpgrade(upgrade) }, enabled = state.treats >= cost) { Text("${formatV6(cost, state.compactNumbers)} 🍪") }
        }
    }
}

@Composable
private fun V6TicketUpgradeCard(state: V6GameState, upgrade: V5Upgrade, vm: PuppyClickerV6ViewModel) {
    val owned = state.upgrades[upgrade.id] ?: 0
    val rarity = upgrade.rarity ?: TicketRarity.COMMON
    val tickets = v6UpgradeTicketCost(state, upgrade)
    val ownedTickets = state.ticketInventory[rarity] ?: 0
    val treatFee = v6UpgradeTreatCost(state, upgrade)
    val canBuy = ownedTickets >= tickets && state.treats >= treatFee
    val skill = if (upgrade.effect == V5UpgradeEffect.CLICK) state.prestigeSkills[PrestigeSkill.TAP_TRAINING] ?: 0 else state.prestigeSkills[PrestigeSkill.AUTO_TRAINING] ?: 0

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = rarityContainerV6(rarity).copy(alpha = 0.68f))) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(upgrade.emoji, fontSize = 31.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(upgrade.name, fontWeight = FontWeight.Black)
                    Text(if (skill > 0) "${upgrade.description} · skill bonus +$skill" else upgrade.description, style = MaterialTheme.typography.bodySmall)
                    Text("Owned $owned", style = MaterialTheme.typography.labelMedium)
                }
                Text("${rarity.emoji} ${rarity.displayName}", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Need $tickets · you have $ownedTickets", fontWeight = FontWeight.Bold)
                    Text("+ ${formatV6(treatFee, state.compactNumbers)} 🍪 fee", style = MaterialTheme.typography.labelMedium)
                }
                Button(onClick = { vm.buyTicketUpgrade(upgrade) }, enabled = canBuy) { Text("Upgrade") }
            }
        }
    }
}

@Composable
private fun V6TicketInventory(state: V6GameState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text("🎟️ Ticket inventory", fontWeight = FontWeight.Black)
            Text("Accepted taps have a 50% chance to drop a ticket.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(7.dp))
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
private fun V6RedeemDialog(vm: PuppyClickerV6ViewModel, close: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var success by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("🎫 Redeem Puppy Code") },
        text = {
            Column {
                Text("Puppy Codes work locally. They can grant treats or special puppies, but never Upgrade Tickets or prestige points.")
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
private fun V6Pups(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V6Header("Puppy Collection", "Original Puppy Clicker character base with unique fur variants.")
        Spacer(Modifier.height(14.dp))
        Text("Unlocked ${state.unlockedPuppies.count { it in V6_PUPPY_IDS }} / ${V6_PUPPY_STYLES.size}", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))

        V6_PUPPY_STYLES.forEach { puppy ->
            val unlocked = puppy.id in state.unlockedPuppies
            val selected = state.puppyStyle == puppy.id
            Card(
                Modifier.fillMaxWidth().clickable(enabled = unlocked) { vm.setPuppyStyle(puppy.id) },
                colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    V6PuppyPortrait(puppy.id, 86.dp, unlocked = unlocked)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(puppy.name, fontWeight = FontWeight.Black)
                        Text(v6FurDescription(puppy.id), style = MaterialTheme.typography.bodySmall)
                        if (puppy.redeemOnly) Text(if (unlocked) "Puppy Code unlocked" else "Special Puppy Code unlock", style = MaterialTheme.typography.labelSmall)
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
            PuppyClickerV6ViewModel.ACCESSORIES.forEach { accessory ->
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
private fun V6Settings(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    var prestigeConfirm by rememberSaveable { mutableStateOf(false) }
    var resetConfirm by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V6Header("Settings", "Game settings, fair play and permanent prestige progression.")
        Spacer(Modifier.height(14.dp))
        V6Switch("📳", "Haptic feedback", "Direct Android vibration feedback.", state.hapticsEnabled, vm::setHapticsEnabled)
        Spacer(Modifier.height(7.dp))
        OutlinedButton(onClick = { performV6Haptic(context, true) }, enabled = state.hapticsEnabled, modifier = Modifier.fillMaxWidth()) { Text("Test haptic") }
        Spacer(Modifier.height(8.dp))
        V6Switch("✨", "Animations", "Puppy idle and tap motion.", state.animationsEnabled, vm::setAnimationsEnabled)
        Spacer(Modifier.height(8.dp))
        V6Switch("🔢", "Compact numbers", "Use K/M/B abbreviations.", state.compactNumbers, vm::setCompactNumbers)

        Spacer(Modifier.height(16.dp))
        V6PrestigeCenter(state, vm, onPrestige = { prestigeConfirm = true })

        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp)) {
                Text("🐶👁️ Pup Eye Fair Play", fontWeight = FontWeight.Black)
                Text("Fast human tapping is allowed. Repeated machine-like timing and extreme sustained rates trigger a short cooldown.", style = MaterialTheme.typography.bodySmall)
                Text("Suspicious taps receive no 50/50 ticket roll. No permanent bans or uploads.", style = MaterialTheme.typography.bodySmall)
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
                Text("Prestiges: ${state.prestigeCount} · lifetime skill points: ${state.totalPrestigePointsEarned}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = { resetConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset current run without prestige") }
        Spacer(Modifier.height(18.dp))
    }

    if (prestigeConfirm) {
        AlertDialog(
            onDismissRequest = { prestigeConfirm = false },
            title = { Text("⭐ Prestige this run?") },
            text = {
                Text("You will earn ${state.prestigePointsAvailable} skill point${if (state.prestigePointsAvailable == 1) "" else "s"}. Current treats, upgrades, tickets, daily progress and run stats reset. Puppy Code unlocks, puppy collection, settings, bond, prestige level and existing skills stay.")
            },
            confirmButton = {
                TextButton(onClick = { vm.prestige(); prestigeConfirm = false }, enabled = state.canPrestige) { Text("Prestige") }
            },
            dismissButton = { TextButton(onClick = { prestigeConfirm = false }) { Text("Cancel") } }
        )
    }

    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text("Reset current run?") },
            text = { Text("This does not award skill points. Your code puppies, prestige level, permanent skills and settings remain protected.") },
            confirmButton = { TextButton(onClick = { vm.resetRunWithoutPrestige(); resetConfirm = false }) { Text("Reset run") } },
            dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun V6PrestigeCenter(state: V6GameState, vm: PuppyClickerV6ViewModel, onPrestige: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⭐", fontSize = 32.sp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Prestige Center", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Prestige ${state.prestigeCount} · ${state.skillPoints} skill points available", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))

            if (state.canPrestige) {
                Text("This run can prestige now for +${state.prestigePointsAvailable} skill point${if (state.prestigePointsAvailable == 1) "" else "s"}.", fontWeight = FontWeight.Bold)
            } else {
                val remaining = (50_000L - state.lifetimeTreats).coerceAtLeast(0L)
                Text("Earn ${formatV6(remaining, state.compactNumbers)} more lifetime treats this run to unlock Prestige.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    { (state.lifetimeTreats.toFloat() / 50_000f).coerceIn(0f, 1f) },
                    Modifier.fillMaxWidth().height(7.dp).clip(CircleShape)
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onPrestige, enabled = state.canPrestige, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.canPrestige) "Prestige for +${state.prestigePointsAvailable} SP" else "Prestige locked")
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Permanent skill tree", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    Text("Skills improve Shop upgrades; they do not give free tap power by themselves.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    PrestigeSkill.entries.forEach { skill ->
        val level = state.prestigeSkills[skill] ?: 0
        val maxed = level >= skill.maxLevel
        val cost = v6SkillCost(state, skill)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(skill.emoji, fontSize = 28.sp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(skill.title, fontWeight = FontWeight.Black)
                        Text("Level $level / ${skill.maxLevel}", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(onClick = { vm.buyPrestigeSkill(skill) }, enabled = !maxed && state.skillPoints >= cost) {
                        Text(if (maxed) "MAX" else "$cost SP")
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(skill.description, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(7.dp))
    }
}

@Composable
private fun V6Wallet(state: V6GameState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🍪 Treats", style = MaterialTheme.typography.labelMedium)
                Text(formatV6(state.treats, state.compactNumbers), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
            V6MiniStat("🎟️", state.ticketsOwned.toString(), "tickets")
            Spacer(Modifier.width(8.dp))
            V6MiniStat("👆", formatV6(state.clickPower.toLong(), state.compactNumbers), "per tap")
            Spacer(Modifier.width(8.dp))
            V6MiniStat("⏱️", formatV6(state.autoPerSecond.toLong(), state.compactNumbers), "per sec")
        }
    }
}

@Composable
private fun V6MiniStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun V6NeedPill(icon: String, value: Int, modifier: Modifier = Modifier) {
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
private fun V6CareMeter(label: String, value: Int) {
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
private fun V6Switch(icon: String, title: String, description: String, checked: Boolean, set: (Boolean) -> Unit) {
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
private fun V6PuppyPortrait(styleId: String, size: Dp, accessory: String = "None", unlocked: Boolean = true) {
    val style = V6_PUPPY_STYLES.firstOrNull { it.id == styleId } ?: V6_PUPPY_STYLES.first()
    val background = v6PuppyBackground(styleId)
    val furFilter = v6PuppyFilter(styleId)

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
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)), contentAlignment = Alignment.Center) {
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

private fun v6PuppyBackground(styleId: String): Color = when (styleId) {
    "golden" -> Color(0xFFFFE6A6)
    "poodle" -> Color(0xFFFFD9E8)
    "spotty" -> Color(0xFFE0E0E0)
    "midnight" -> Color(0xFFD7D9FF)
    "cloud" -> Color(0xFFE5F3FF)
    "aurora" -> Color(0xFFDCCBFF)
    "cocoa" -> Color(0xFFE8C7A6)
    "snowball" -> Color(0xFFEAF7FF)
    "galaxy" -> Color(0xFFCEC3FF)
    "neon_buddy" -> Color(0xFFC9FFF9)
    "golden_night" -> Color(0xFF28232C)
    "halloween" -> Color(0xFFFFD1A6)
    "santa" -> Color(0xFFFFD9DE)
    "birthday" -> Color(0xFFFFE3FA)
    "dev_pup" -> Color(0xFFCFE8FF)
    "secret_snoot" -> Color(0xFFD7CEDF)
    "classic_forever" -> Color(0xFFFFE9C8)
    else -> Color(0xFFF7F2F7)
}

private fun v6PuppyFilter(styleId: String): ColorFilter? = when (styleId) {
    "golden" -> ColorFilter.tint(Color(0xFFFFD47A), BlendMode.Modulate)
    "poodle" -> ColorFilter.tint(Color(0xFFFFC5D7), BlendMode.Modulate)
    "spotty" -> ColorFilter.tint(Color(0xFF929292), BlendMode.Modulate)
    "midnight" -> ColorFilter.tint(Color(0xFF7185D8), BlendMode.Modulate)
    "cloud" -> ColorFilter.tint(Color(0xFFD8EEFF), BlendMode.Modulate)
    "aurora" -> ColorFilter.tint(Color(0xFFA98CFF), BlendMode.Modulate)
    "cocoa" -> ColorFilter.tint(Color(0xFF9B6848), BlendMode.Modulate)
    "snowball" -> ColorFilter.tint(Color(0xFFE9F8FF), BlendMode.Modulate)
    "galaxy" -> ColorFilter.tint(Color(0xFF7052B8), BlendMode.Modulate)
    "neon_buddy" -> ColorFilter.tint(Color(0xFF4DFFE8), BlendMode.Modulate)
    "golden_night" -> ColorFilter.tint(Color(0xFF665436), BlendMode.Modulate)
    "halloween" -> ColorFilter.tint(Color(0xFFFF8B43), BlendMode.Modulate)
    "santa" -> ColorFilter.tint(Color(0xFFFF9E9E), BlendMode.Modulate)
    "birthday" -> ColorFilter.tint(Color(0xFFFFB7E7), BlendMode.Modulate)
    "dev_pup" -> ColorFilter.tint(Color(0xFF74BFFF), BlendMode.Modulate)
    "secret_snoot" -> ColorFilter.tint(Color(0xFF8A718F), BlendMode.Modulate)
    "classic_forever" -> ColorFilter.tint(Color(0xFFFFC780), BlendMode.Modulate)
    else -> null
}

private fun v6FurDescription(styleId: String): String = when (styleId) {
    "classic" -> "Original warm tan fur."
    "golden" -> "Bright golden fur with a sunny look."
    "poodle" -> "Soft blush-cream pastel fur."
    "spotty" -> "Cool charcoal-gray fur."
    "midnight" -> "Blue-indigo moonlit fur."
    "cloud" -> "Pale sky-blue cloud fur."
    "aurora" -> "Purple-teal aurora inspired fur."
    "cocoa" -> "Warm cocoa-brown fur."
    "snowball" -> "Snow-white icy fur."
    "galaxy" -> "Deep cosmic violet fur."
    "neon_buddy" -> "Electric neon cyan fur."
    "golden_night" -> "Dark fur with a gold-night treatment."
    "halloween" -> "Limited pumpkin-orange Halloween fur."
    "santa" -> "Limited holiday red-and-snow look."
    "birthday" -> "Bright birthday-party pastel fur."
    "dev_pup" -> "Cool developer-blue styling."
    "secret_snoot" -> "Hidden smoky-purple mystery fur."
    "classic_forever" -> "Special warm tribute to the original pup."
    else -> "Original Puppy Clicker style."
}

@Composable
private fun V6Header(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.source_logo), null, Modifier.size(50.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun rarityContainerV6(rarity: TicketRarity): Color = when (rarity) {
    TicketRarity.COMMON -> MaterialTheme.colorScheme.surfaceVariant
    TicketRarity.UNCOMMON -> MaterialTheme.colorScheme.secondaryContainer
    TicketRarity.RARE -> MaterialTheme.colorScheme.primaryContainer
    TicketRarity.EPIC -> MaterialTheme.colorScheme.tertiaryContainer
    TicketRarity.LEGENDARY -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
}

private fun performV6Haptic(context: Context, strong: Boolean = false) {
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

private fun formatV6(value: Long, compact: Boolean): String {
    if (!compact) return "%,d".format(value)
    return when {
        value >= 1_000_000_000_000L -> "%.2fT".format(value / 1_000_000_000_000.0)
        value >= 1_000_000_000L -> "%.2fB".format(value / 1_000_000_000.0)
        value >= 1_000_000L -> "%.2fM".format(value / 1_000_000.0)
        value >= 1_000L -> "%.1fK".format(value / 1_000.0)
        else -> value.toString()
    }
}
