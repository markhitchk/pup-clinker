package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import com.harleytg.puppyclicker.ui.theme.LocalPuppyAnimatedUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PuppyRevampedPlayScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onOpenRoster: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animatePuppy = LocalPuppyAnimatedUi.current && state.animationsEnabled
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ticketVisible by remember { mutableStateOf(false) }
    val tapScale = remember { Animatable(1f) }
    val tapRotation = remember { Animatable(0f) }
    val tapLift = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(state.ticketDropSerial) {
        if (state.ticketDropSerial > 0 && state.lastTicketDrop != null) {
            ticketVisible = true
            if (state.hapticsEnabled) performV6Haptic(context, true)
            delay(2_750)
            ticketVisible = false
        }
    }

    val cooldown = (state.cooldownUntilMs - now).coerceAtLeast(0L)

    fun tapPuppy() {
        if (cooldown != 0L) return
        vm.tapPuppy()
        if (state.hapticsEnabled) performV6Haptic(context)
        if (animatePuppy) {
            scope.launch {
                tapScale.stop()
                tapRotation.stop()
                tapLift.stop()
                tapScale.snapTo(0.92f)
                tapRotation.snapTo(-4f)
                tapLift.snapTo(7f)
                launch {
                    tapScale.animateTo(1.08f, spring(dampingRatio = 0.48f, stiffness = 620f))
                    tapScale.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 520f))
                }
                launch {
                    tapRotation.animateTo(4f, spring(dampingRatio = 0.5f, stiffness = 520f))
                    tapRotation.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 460f))
                }
                launch {
                    tapLift.animateTo(-5f, spring(dampingRatio = 0.52f, stiffness = 560f))
                    tapLift.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 480f))
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        val tiny = maxHeight < 590.dp
        val portraitSize = when {
            tiny -> 122.dp
            compact -> 150.dp
            else -> 185.dp
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = if (tiny) 7.dp else 10.dp)
        ) {
            PuppyMainPageHeader(
                "Play",
                if (compact) "Tap your puppy and earn treats." else "Tap, earn treats, and have fun with your puppy!"
            )
            Spacer(Modifier.height(if (tiny) 5.dp else 8.dp))
            PuppyWalletCard(state)
            Spacer(Modifier.height(if (tiny) 5.dp else 8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.065f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            ) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer(
                                scaleX = tapScale.value,
                                scaleY = tapScale.value,
                                rotationZ = tapRotation.value,
                                translationY = tapLift.value
                            )
                            .clickable(enabled = cooldown == 0L, onClick = ::tapPuppy),
                        contentAlignment = Alignment.Center
                    ) {
                        StreamedPuppyPortrait(
                            styleId = state.puppyStyle,
                            size = portraitSize,
                            accessory = state.accessory,
                            background = Color.Transparent
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = ::tapPuppy,
                        enabled = cooldown == 0L,
                        modifier = Modifier.fillMaxWidth().height(if (tiny) 44.dp else 50.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            if (cooldown > 0L) {
                                "🐶👁️ Fair-play cooldown · " + ((cooldown + 999L) / 1000L) + "s"
                            } else {
                                "🐾 Tap " + state.puppyName + "  +" + state.clickPower
                            },
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (tiny) 5.dp else 8.dp))
            val target = 75L
            val progressNow = state.dailyTaps.coerceAtMost(target)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = if (tiny) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎁", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Next Reward", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                            Text("$progressNow/$target", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                        }
                        LinearProgressIndicator(
                            progress = { progressNow.toFloat() / target.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("300 🍪", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        V6TicketDropOverlay(
            visible = ticketVisible,
            rarity = state.lastTicketDrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 105.dp, start = 24.dp, end = 24.dp)
        )
    }
}

@Composable
internal fun PuppyRevampedCareScreen(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        val tiny = maxHeight < 590.dp
        val portraitSize = when {
            tiny -> 66.dp
            compact -> 78.dp
            else -> 92.dp
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = if (tiny) 7.dp else 10.dp)
        ) {
            PuppyMainPageHeader(
                "Pup Care",
                if (compact) "Keep every care stat healthy." else "Care for and bond with your active puppy."
            )
            Spacer(Modifier.height(if (tiny) 5.dp else 8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (tiny) 7.dp else 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StreamedPuppyPortrait(
                        styleId = state.puppyStyle,
                        size = portraitSize,
                        accessory = state.accessory,
                        background = Color.Transparent
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.puppyName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(
                            state.mood + " · Wellness " + state.careScore + "% · Bond " + state.bond + "%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (tiny) 5.dp else 8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = if (tiny) 6.dp else 8.dp)) {
                    PuppyCompactCareMeter("💖 Happiness", state.happiness)
                    PuppyCompactCareMeter("🍖 Fullness", state.fullness)
                    PuppyCompactCareMeter("⚡ Energy", state.energy)
                    PuppyCompactCareMeter("🫧 Cleanliness", state.cleanliness)
                    PuppyCompactCareMeter("🤝 Bond", state.bond)
                }
            }

            Spacer(Modifier.height(if (tiny) 5.dp else 8.dp))
            Text("Care actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PuppyQuickAction(
                    "🍖",
                    "Feed",
                    "-" + PuppyClickerV6ViewModel.FEED_COST + " 🍪",
                    state.treats >= PuppyClickerV6ViewModel.FEED_COST && state.fullness < 100,
                    vm::feedPuppy,
                    Modifier.weight(1f)
                )
                PuppyQuickAction(
                    "🎾",
                    "Play",
                    "-12 Energy",
                    state.energy >= 12 && (state.happiness < 100 || state.bond < 100),
                    vm::playWithPuppy,
                    Modifier.weight(1f)
                )
                PuppyQuickAction("🫧", "Clean", "+35 Clean", state.cleanliness < 100, vm::groomPuppy, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PuppyQuickAction("🌙", "Rest", "+30 Energy", state.energy < 100, vm::restPuppy, Modifier.weight(1f))
                PuppyQuickAction("🤗", "Cuddle", "+Happy / Bond", state.happiness < 100 || state.bond < 100, vm::cuddlePuppy, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PuppyCompactCareMeter(label: String, value: Int) {
    val progress = (value / 100f).coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(92.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(5.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value.toString() + "%",
            modifier = Modifier.width(38.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
internal fun PuppyRevampedShopScreen(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var redeemOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        PuppyMainPageHeader("Puppy Shop", "Cookie upgrades, rarity-ticket upgrades and Puppy Codes.")
        Spacer(Modifier.height(14.dp))
        PuppyWalletCard(state)
        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { redeemOpen = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("🎫  Redeem Code", fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("🍪 Cookie Upgrades") }, modifier = Modifier.weight(1f))
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("🎟️ Ticket Upgrades") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        if (tab == 0) {
            V5_UPGRADES.filter { it.type == V5UpgradeType.COOKIE }.forEach { upgrade ->
                PuppyCookieUpgradeCard(state, upgrade, vm)
                Spacer(Modifier.height(9.dp))
            }
        } else {
            PuppyTicketInventoryCard(state)
            Spacer(Modifier.height(10.dp))
            V5_UPGRADES.filter { it.type == V5UpgradeType.TICKET }.forEach { upgrade ->
                PuppyTicketUpgradeCard(state, upgrade, vm)
                Spacer(Modifier.height(9.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (redeemOpen) {
        PuppyRevampedRedeemDialog(vm) { redeemOpen = false }
    }
}

@Composable
internal fun PuppyRevampedRewardsScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onOpenPrestige: () -> Unit
) {
    val today = LocalDate.now().toEpochDay()
    val nextReward = 150L + (if (state.lastDailyClaimDay == today - 1) state.dailyStreak + 1 else 1) * 25L + state.level * 10L

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        PuppyMainPageHeader("Rewards", "Daily goals, adventures and permanent progression.")
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", fontSize = 42.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.dailyStreak.toString() + " day streak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Keep going! Consistent care unlocks bigger rewards.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(7) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Spacer(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎁", fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Daily Puppy Gift", fontWeight = FontWeight.Black)
                    Text(nextReward.toString() + " treats + mood + bond", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = vm::claimDailyReward, enabled = state.lastDailyClaimDay != today) {
                    Text(if (state.lastDailyClaimDay == today) "Claimed" else "Claim")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Today’s goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        PuppyDailyGoal("tap75", "🐾", "Tap Time", "Tap your puppy 75 times", state.dailyTaps, 75L, 300L, state, vm)
        Spacer(Modifier.height(8.dp))
        PuppyDailyGoal("care3", "💖", "Good Care", "Complete 3 care actions", state.dailyCareActions, 3L, 250L, state, vm)
        Spacer(Modifier.height(8.dp))
        PuppyDailyGoal("shop1", "🛍️", "Shop Visit", "Buy 1 upgrade", state.dailyShopPurchases, 1L, 400L, state, vm)
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⭐", fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Prestige", fontWeight = FontWeight.Black)
                    Text(state.prestigeCount.toString() + " prestiges · " + state.skillPoints + " skill points", style = MaterialTheme.typography.bodySmall)
                    Text("Permanent skills and progression.", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(onClick = onOpenPrestige) { Text("Open") }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PuppyMainPageHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PuppyActivePuppyCard(state: V6GameState, onOpenRoster: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)) {
                Box(Modifier.padding(5.dp)) {
                    StreamedPuppyPortrait(styleId = state.puppyStyle, size = 76.dp, accessory = state.accessory, background = Color.Transparent)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PuppyStatusPill("🐾 Current Puppy")
                Spacer(Modifier.height(4.dp))
                Text(state.puppyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(state.puppyStyle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onOpenRoster) { Text("Change") }
        }
    }
}

@Composable
private fun PuppyStatusPill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PuppyWalletCard(state: V6GameState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
            PuppyMiniStat("🍪", state.treats.toString(), "Treats", Modifier.weight(1f))
            PuppyMiniStat("🎟️", state.ticketsOwned.toString(), "Tickets", Modifier.weight(1f))
            PuppyMiniStat("👆", state.clickPower.toString(), "Per tap", Modifier.weight(1f))
            PuppyMiniStat("⏱️", state.autoPerSecond.toString(), "Per sec", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PuppyMiniStat(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 23.sp)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PuppyQuickAction(
    emoji: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 72.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 21.sp)
            Text(title, fontWeight = FontWeight.Black, maxLines = 1, style = MaterialTheme.typography.labelLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun PuppyCareAction(emoji: String, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    PuppyQuickAction(emoji, title, subtitle, enabled, onClick, Modifier.width(104.dp))
}

@Composable
private fun PuppyCareMeter(title: String, subtitle: String, value: Int) {
    val pct = (value / 100f).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Black)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value.toString() + "%", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun PuppyProgressCard(
    emoji: String,
    title: String,
    subtitle: String,
    progressText: String,
    progress: Float,
    footer: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, fontWeight = FontWeight.Black)
                    Text(progressText, fontWeight = FontWeight.Black)
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                Spacer(Modifier.height(5.dp))
                Text(footer, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PuppyCookieUpgradeCard(state: V6GameState, upgrade: V5Upgrade, vm: PuppyClickerV6ViewModel) {
    val owned = state.upgrades[upgrade.id] ?: 0
    val cost = v6UpgradeTreatCost(state, upgrade)
    PuppyUpgradeCard(
        emoji = upgrade.emoji,
        title = upgrade.name,
        description = upgrade.description,
        owned = owned,
        buttonText = cost.toString() + " 🍪",
        enabled = state.treats >= cost,
        onBuy = { vm.buyCookieUpgrade(upgrade) }
    )
}

@Composable
private fun PuppyTicketUpgradeCard(state: V6GameState, upgrade: V5Upgrade, vm: PuppyClickerV6ViewModel) {
    val rarity = upgrade.rarity ?: TicketRarity.COMMON
    val ticketCost = v6UpgradeTicketCost(state, upgrade)
    val treatCost = v6UpgradeTreatCost(state, upgrade)
    val canBuy = (state.ticketInventory[rarity] ?: 0) >= ticketCost && state.treats >= treatCost
    PuppyUpgradeCard(
        emoji = upgrade.emoji,
        title = upgrade.name,
        description = upgrade.description + " · " + rarity.displayName,
        owned = state.upgrades[upgrade.id] ?: 0,
        buttonText = "Upgrade",
        enabled = canBuy,
        onBuy = { vm.buyTicketUpgrade(upgrade) }
    )
}

@Composable
private fun PuppyUpgradeCard(
    emoji: String,
    title: String,
    description: String,
    owned: Int,
    buttonText: String,
    enabled: Boolean,
    onBuy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                Text(emoji, modifier = Modifier.padding(12.dp), fontSize = 30.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Owned " + owned, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onBuy, enabled = enabled, shape = RoundedCornerShape(18.dp)) { Text(buttonText) }
        }
    }
}

@Composable
private fun PuppyTicketInventoryCard(state: V6GameState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("🎟️ Ticket inventory", fontWeight = FontWeight.Black)
            Text("Every 5th accepted tap awards one ticket.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
            TicketRarity.entries.forEach { rarity ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(rarity.emoji + " " + rarity.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("×" + (state.ticketInventory[rarity] ?: 0), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun PuppyDailyGoal(
    id: String,
    emoji: String,
    title: String,
    description: String,
    progress: Long,
    target: Long,
    reward: Long,
    state: V6GameState,
    vm: PuppyClickerV6ViewModel
) {
    val claimed = id in state.claimedDailyTasks
    val complete = progress >= target
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)) {
                Text(emoji, modifier = Modifier.padding(12.dp), fontSize = 28.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, fontWeight = FontWeight.Black)
                    Text(progress.coerceAtMost(target).toString() + "/" + target, fontWeight = FontWeight.Black)
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (progress.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Reward: " + reward + " 🍪", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Button(onClick = { vm.claimDailyTask(id) }, enabled = complete && !claimed) {
                        Text(if (claimed) "Claimed" else "Claim")
                    }
                }
            }
        }
    }
}

@Composable
private fun PuppyRevampedRedeemDialog(vm: PuppyClickerV6ViewModel, onClose: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var success by rememberSaveable { mutableStateOf(false) }
    var checking by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!checking) onClose() },
        title = { Text("🎫 Redeem Puppy Code") },
        text = {
            Column {
                Text(
                    "Enter the Puppy Code exactly as provided. Claims are checked live before rewards are applied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        if (!checking) {
                            code = it.take(64)
                            message = null
                        }
                    },
                    label = { Text("Puppy Code") },
                    singleLine = true,
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                if (checking) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (success) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Text(it, Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    checking = true
                    success = false
                    message = null
                    vm.redeemCode(code) { result ->
                        checking = false
                        success = result.success
                        message = result.message
                        if (result.success) code = ""
                    }
                },
                enabled = code.isNotBlank() && !checking
            ) {
                Text(if (checking) "Checking…" else "Redeem")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !checking) { Text("Close") }
        }
    )
}
