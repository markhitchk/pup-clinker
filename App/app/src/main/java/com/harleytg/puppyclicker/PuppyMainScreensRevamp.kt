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
    val rewardSchedule by PuppyMonthlyRewards.schedule.collectAsState()
    val todayGoals = remember(rewardSchedule) {
        PuppyMonthlyRewards.currentGoals(LocalDate.now())
    }
    val nextGoal = todayGoals.firstOrNull { it.id !in state.claimedDailyTasks }
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
                .padding(horizontal = 14.dp, vertical = if (tiny) 2.dp else 4.dp)
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
            val target = nextGoal?.target ?: 1L
            val progressNow = nextGoal?.progress(state)?.coerceAtMost(target) ?: target
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
                            Text(
                                nextGoal?.let { "Next Reward · ${it.title}" } ?: "Today complete",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                if (nextGoal != null) "$progressNow/$target" else "✓",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progressNow.toFloat() / target.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        nextGoal?.let { "${it.rewardTreats} 🍪" } ?: "All claimed",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        V6TicketDropOverlay(
            visible = ticketVisible,
            rarity = state.lastTicketDrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp, start = 24.dp, end = 24.dp)
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
                .padding(horizontal = 14.dp, vertical = if (tiny) 2.dp else 4.dp)
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
    ) {
        PuppyMainPageHeader("Puppy Shop", "Active upgrades, rare tickets, Pup Coins and Puppy Codes.")
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("🍪 Upgrades") }, modifier = Modifier.weight(1f))
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("🎟️ Tickets") }, modifier = Modifier.weight(1f))
            FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("🪙 Pup Coins") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> V5_UPGRADES.filter { it.type == V5UpgradeType.COOKIE }.forEach { upgrade ->
                PuppyCookieUpgradeCard(state, upgrade, vm)
                Spacer(Modifier.height(9.dp))
            }
            1 -> {
                PuppyTicketInventoryCard(state)
                Spacer(Modifier.height(10.dp))
                V5_UPGRADES.filter { it.type == V5UpgradeType.TICKET }.forEach { upgrade ->
                    PuppyTicketUpgradeCard(state, upgrade, vm)
                    Spacer(Modifier.height(9.dp))
                }
            }
            else -> PuppyPupCoinShop(state, vm)
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
    onOpenPrestige: () -> Unit,
    onOpenCasino: () -> Unit,
    onOpenGacha: () -> Unit
) {
    val rewardSchedule by PuppyMonthlyRewards.schedule.collectAsState()
    val casinoFlags by PuppyFeatureFlags.flags.collectAsState()
    val activeCasinoRound by vm.casinoRound.collectAsState()
    val casinoRecoveryIssue by vm.casinoRecoveryIssue.collectAsState()
    val showCasinoEntry = PuppyCasinoFeaturePolicy.shouldExposeCasinoEntry(
        flags = casinoFlags,
        activeRound = activeCasinoRound,
        hasRecoveryIssue = casinoRecoveryIssue != null
    )
    val todayDate = LocalDate.now()
    val today = todayDate.toEpochDay()
    val nextReward = 150L + (if (state.lastDailyClaimDay == today - 1) state.dailyStreak + 1 else 1) * 25L + state.level * 10L
    val todayGoals = PuppyMonthlyRewards.currentGoals(todayDate)
    val activeGoals = todayGoals.filterNot { it.id in state.claimedDailyTasks }
    val claimedGoals = todayGoals.filter { it.id in state.claimedDailyTasks }
    val dailyGiftClaimed = state.lastDailyClaimDay == today

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
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
                        repeat(7) { index ->
                            val filled = index < state.dailyStreak.coerceIn(0, 7)
                            Surface(
                                shape = CircleShape,
                                color = if (filled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (filled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                            ) {
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (filled) {
                                        Text(
                                            "✓",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!dailyGiftClaimed) {
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
                        Text(nextReward.toString() + " Treats + 2 Bones + 3 Pup Coins", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = vm::claimDailyReward) {
                        Text("Claim")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Today’s goals", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                rewardSchedule.month + " · " + when (rewardSchedule.source) {
                    "github" -> "Live"
                    "cache" -> "Cached"
                    else -> "Fallback"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        if (activeGoals.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f)
            ) {
                Text(
                    "All of today’s streamed goals are claimed. 🎉",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            activeGoals.forEachIndexed { index, goal ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                PuppyDailyGoal(goal, state, vm)
            }
        }

        if (dailyGiftClaimed || claimedGoals.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Claimed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            if (dailyGiftClaimed) {
                PuppyClaimedReward(
                    emoji = "🎁",
                    title = "Daily Puppy Gift",
                    rewardText = "Treats + 2 Bones + 3 Pup Coins"
                )
            }
            claimedGoals.forEach { goal ->
                if (dailyGiftClaimed || goal != claimedGoals.first()) Spacer(Modifier.height(7.dp))
                PuppyClaimedReward(
                    emoji = goal.emoji,
                    title = goal.title,
                    rewardText = goal.rewardTreats.toString() + " Treats + 1 Bone + 1 Pup Coin"
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🫧", fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Puppy Gacha", fontWeight = FontWeight.Black)
                    Text(
                        "Capsule machine · guaranteed new eligible puppy",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Uses earned Treats only · separate from Puppy Casino",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onOpenGacha) { Text("Open") }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (showCasinoEntry) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎰", fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Puppy Casino", fontWeight = FontWeight.Black)
                        Text("Casino Chip wagers · Slots · Roulette · Blackjack", style = MaterialTheme.typography.bodySmall)
                        Text(
                            when {
                                casinoRecoveryIssue != null ->
                                    "Recovery warning · saved Casino data needs attention."
                                activeCasinoRound != null ->
                                    "Recovery required · finish the saved casino round."
                                else ->
                                    "Hub available · new wagers follow remote release flags."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onOpenCasino) {
                        Text(
                            if (activeCasinoRound != null || casinoRecoveryIssue != null) {
                                "Recover"
                            } else {
                                "Open"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }

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
            PuppyMiniStat("🦴", state.bones.toString(), "Bones", Modifier.weight(1f))
            PuppyMiniStat("🪙", state.pupCoins.toString(), "Pup Coins", Modifier.weight(1f))
            PuppyMiniStat("🐾", state.casinoChips.toString(), "Casino Chips", Modifier.weight(1f))
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
    val boneCost = v6UpgradeBoneCost(upgrade)
    PuppyUpgradeCard(
        emoji = upgrade.emoji,
        title = upgrade.name,
        description = upgrade.description,
        owned = owned,
        costText = "$cost 🍪 + $boneCost 🦴",
        enabled = state.treats >= cost && state.bones >= boneCost,
        onBuy = { vm.buyCookieUpgrade(upgrade) }
    )
}

@Composable
private fun PuppyTicketUpgradeCard(state: V6GameState, upgrade: V5Upgrade, vm: PuppyClickerV6ViewModel) {
    val rarity = upgrade.rarity ?: TicketRarity.COMMON
    val ticketCost = v6UpgradeTicketCost(state, upgrade)
    val treatCost = v6UpgradeTreatCost(state, upgrade)
    val boneCost = v6UpgradeBoneCost(upgrade)
    val canBuy =
        (state.ticketInventory[rarity] ?: 0) >= ticketCost &&
            state.treats >= treatCost &&
            state.bones >= boneCost
    PuppyUpgradeCard(
        emoji = upgrade.emoji,
        title = upgrade.name,
        description = upgrade.description + " · " + rarity.displayName,
        owned = state.upgrades[upgrade.id] ?: 0,
        costText = "$treatCost 🍪 + $boneCost 🦴 + $ticketCost ${rarity.emoji}",
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
    costText: String,
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
                Text(costText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            }
            Button(onClick = onBuy, enabled = enabled, shape = RoundedCornerShape(18.dp)) { Text("Buy") }
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
            Text("Rare drop: about 1 Upgrade Ticket per 500 legitimate taps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
internal fun PuppyPupCoinShop(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("🪙 Pup Coin Shop", fontWeight = FontWeight.Black)
            Text(
                "${state.pupCoins} Pup Coins",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                "Pup Coins are the normal Shop currency. They are not Casino Chips.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Accessories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(6.dp))

    PuppyAccessoryShopRow(
        accessory = "None",
        owned = true,
        equipped = state.accessory == "None",
        price = null,
        canBuy = true,
        onBuyOrEquip = { vm.setAccessory("None") }
    )
    PuppyClickerV6ViewModel.ACCESSORIES.filterNot { it == "None" }.forEach { accessory ->
        Spacer(Modifier.height(7.dp))
        val price = PuppyEconomyV7.accessoryPrice(accessory) ?: 0L
        val owned = accessory in state.ownedAccessories
        PuppyAccessoryShopRow(
            accessory = accessory,
            owned = owned,
            equipped = state.accessory == accessory,
            price = price,
            canBuy = owned || state.pupCoins >= price,
            onBuyOrEquip = {
                if (owned) {
                    vm.setAccessory(accessory)
                } else if (vm.buyAccessoryWithPupCoins(accessory)) {
                    vm.setAccessory(accessory)
                }
            }
        )
    }

    Spacer(Modifier.height(16.dp))
    Text("Upgrade Tickets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    Text(
        "Each purchase raises that rarity’s next price by 25% of its base price, capped at 6×.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    TicketRarity.entries.forEach { rarity ->
        val purchased = state.ticketShopPurchases[rarity] ?: 0
        val cost = PuppyEconomyV7.ticketShopCost(rarity, purchased)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${rarity.emoji} ${rarity.displayName}", fontWeight = FontWeight.Black)
                    Text(
                        "Owned ${state.ticketInventory[rarity] ?: 0} · Next $cost 🪙",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Button(
                    onClick = { vm.buyUpgradeTicketWithPupCoins(rarity) },
                    enabled = state.pupCoins >= cost
                ) { Text("Buy 1") }
            }
        }
    }
}

@Composable
private fun PuppyAccessoryShopRow(
    accessory: String,
    owned: Boolean,
    equipped: Boolean,
    price: Long?,
    canBuy: Boolean,
    onBuyOrEquip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(accessory, fontWeight = FontWeight.Black)
                Text(
                    when {
                        accessory == "None" -> "Always available"
                        owned -> "Owned permanently"
                        else -> "$price Pup Coins"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onBuyOrEquip,
                enabled = canBuy && !equipped
            ) {
                Text(
                    when {
                        equipped -> "Equipped"
                        owned || accessory == "None" -> "Equip"
                        else -> "Buy"
                    }
                )
            }
        }
    }
}

@Composable
private fun PuppyDailyGoal(
    goal: PuppyRewardGoal,
    state: V6GameState,
    vm: PuppyClickerV6ViewModel
) {
    val progress = goal.progress(state)
    val complete = goal.isComplete(state)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)) {
                Text(goal.emoji, modifier = Modifier.padding(12.dp), fontSize = 28.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(goal.title, fontWeight = FontWeight.Black)
                    Text(progress.coerceAtMost(goal.target).toString() + "/" + goal.target, fontWeight = FontWeight.Black)
                }
                Text(goal.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (progress.toFloat() / goal.target.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Reward: " + goal.rewardTreats + " 🍪 + 1 🦴 + 1 🪙",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Button(onClick = { vm.claimDailyTask(goal.id) }, enabled = complete) {
                        Text("Claim")
                    }
                }
            }
        }
    }
}

@Composable
private fun PuppyClaimedReward(
    emoji: String,
    title: String,
    rewardText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.26f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(rewardText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "✓ Claimed",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
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
