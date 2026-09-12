package com.harleytg.puppyclicker

/**
 * Canonical main-screen UI system for the six-system Kotlin architecture.
 * Owns Play, Care, Shop, Rewards, and the compatibility Rewards entry point.
 */

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.delay

@Composable
internal fun PuppyRevampedPlayScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onOpenRoster: () -> Unit
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ticketVisible by remember { mutableStateOf(false) }

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

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            PuppyMainPageHeader("Play", "Tap, earn treats, and have fun with your puppy!")
            Spacer(Modifier.height(14.dp))
            PuppyActivePuppyCard(state, onOpenRoster)
            Spacer(Modifier.height(10.dp))
            PuppyWalletCard(state)
            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.065f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StreamedPuppyPortrait(
                        styleId = state.puppyStyle,
                        size = 230.dp,
                        accessory = state.accessory,
                        background = Color.Transparent
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            vm.tapPuppy()
                            if (state.hapticsEnabled) performV6Haptic(context)
                        },
                        enabled = cooldown == 0L,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            if (cooldown > 0L) {
                                "🐶👁️ Fair-play cooldown · " + ((cooldown + 999L) / 1000L) + "s"
                            } else {
                                "🐾 Tap the Puppy!  +" + state.clickPower + " treat"
                            },
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuppyQuickAction(
                    "🍖", "Feed", "+Fullness",
                    state.treats >= PuppyClickerV6ViewModel.FEED_COST && state.fullness < 100,
                    vm::feedPuppy,
                    Modifier.weight(1f)
                )
                PuppyQuickAction(
                    "🎾", "Play", "+Happiness",
                    state.energy >= 12,
                    vm::playWithPuppy,
                    Modifier.weight(1f)
                )
                PuppyQuickAction(
                    "🫧", "Clean", "+Clean",
                    state.cleanliness < 100,
                    vm::groomPuppy,
                    Modifier.weight(1f)
                )
                PuppyQuickAction(
                    "🌙", "Rest", "+Energy",
                    state.energy < 100,
                    vm::restPuppy,
                    Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            val tapProgress = state.dailyTaps.coerceAtMost(75L).toFloat() / 75f
            PuppyProgressCard(
                emoji = "🎁",
                title = "Next Reward",
                subtitle = if (state.dailyTaps >= 75L) {
                    "Ready to claim in Rewards."
                } else {
                    "Tap " + (75L - state.dailyTaps.coerceAtMost(75L)) + " more times"
                },
                progressText = state.dailyTaps.coerceAtMost(75L).toString() + "/75",
                progress = tapProgress,
                footer = "Reward: 300 🍪"
            )
            Spacer(Modifier.height(20.dp))
        }

        V6TicketDropOverlay(
            visible = ticketVisible,
            rarity = state.lastTicketDrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 150.dp, start = 24.dp, end = 24.dp)
        )
    }
}

@Composable
internal fun PuppyRevampedCareScreen(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        PuppyMainPageHeader("Pup Care", "Care for and bond with your active puppy.")
        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                StreamedPuppyPortrait(
                    styleId = state.puppyStyle,
                    size = 132.dp,
                    accessory = state.accessory,
                    background = Color.Transparent
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    PuppyStatusPill("🐾 Current Puppy")
                    Spacer(Modifier.height(5.dp))
                    Text(state.puppyName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(state.mood + " · wellness " + state.careScore + "%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("🤝 Bond " + state.bond + "%", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.careScore < 50) "Give your puppy some care to help them feel better!" else "Your puppy is doing well. Keep it up!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        PuppyCareMeter("💖 Happiness", "Keep your puppy happy with love and play.", state.happiness)
        Spacer(Modifier.height(8.dp))
        PuppyCareMeter("🍖 Fullness", "Feed your puppy to keep them full.", state.fullness)
        Spacer(Modifier.height(8.dp))
        PuppyCareMeter("⚡ Energy", "Play and rest to keep their energy up.", state.energy)
        Spacer(Modifier.height(8.dp))
        PuppyCareMeter("🫧 Cleanliness", "Keep your puppy clean and fresh.", state.cleanliness)
        Spacer(Modifier.height(8.dp))
        PuppyCareMeter("🤝 Bond", "Spend time together to build a stronger bond.", state.bond)

        Spacer(Modifier.height(16.dp))
        Text("Care actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text("Tap an action to care for your puppy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PuppyCareAction("🍖", "Feed", "+Fullness", state.treats >= PuppyClickerV6ViewModel.FEED_COST && state.fullness < 100, vm::feedPuppy)
            PuppyCareAction("🎾", "Play", "+Happiness", state.energy >= 12, vm::playWithPuppy)
            PuppyCareAction("🌙", "Rest", "+Energy", state.energy < 100, vm::restPuppy)
            PuppyCareAction("🫧", "Clean", "+Clean", state.cleanliness < 100, vm::groomPuppy)
            PuppyCareAction("🤗", "Cuddle", "+Bond", state.happiness < 100 || state.bond < 100, vm::cuddlePuppy)
        }
        Spacer(Modifier.height(20.dp))
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
        modifier = modifier.heightIn(min = 94.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 24.sp)
            Text(title, fontWeight = FontWeight.Black, maxLines = 1)
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


/** Compatibility entry point retained for generated navigation and older callers. */
@Composable
internal fun PuppyRewardsHub(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onOpenPrestige: () -> Unit
) {
    PuppyRevampedRewardsScreen(
        state = state,
        vm = vm,
        onOpenPrestige = onOpenPrestige
    )
}
