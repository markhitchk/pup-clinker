package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PuppyCasinoHub(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onBack: () -> Unit
) {
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val activeRound by vm.casinoRound.collectAsStateWithLifecycle()
    val recoveryIssue by vm.casinoRecoveryIssue.collectAsStateWithLifecycle()
    val rewardLedger by vm.casinoRewardLedger.collectAsStateWithLifecycle()
    val lastTicketReward by vm.lastCasinoTicketReward.collectAsStateWithLifecycle()
    val puppyRewardLedger by vm.casinoPuppyRewardLedger.collectAsStateWithLifecycle()
    val lastPuppyReward by vm.lastCasinoPuppyReward.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("hub") }

    if (page == "slots") {
        PuppySlotsScreen(
            state = state,
            vm = vm,
            onBack = { page = "hub" }
        )
        return
    }
    if (page == "roulette") {
        PuppyRouletteScreen(
            state = state,
            vm = vm,
            onBack = { page = "hub" }
        )
        return
    }
    if (page == "blackjack") {
        PuppyBlackjackScreen(
            state = state,
            vm = vm,
            onBack = { page = "hub" }
        )
        return
    }
    val casinoFlag = flags["puppy_casino"] ?: PuppyFeatureFlags.flag("puppy_casino")
    val slotsFlag = flags["casino_slots"] ?: PuppyFeatureFlags.flag("casino_slots")
    val rouletteFlag = flags["casino_roulette"] ?: PuppyFeatureFlags.flag("casino_roulette")
    val blackjackFlag = flags["casino_blackjack"] ?: PuppyFeatureFlags.flag("casino_blackjack")
    val recoveryHealthy = recoveryIssue == null
    val slotsCanStart = recoveryHealthy &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.SLOTS, flags)
    val rouletteCanStart = recoveryHealthy &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.ROULETTE, flags)
    val blackjackCanStart = recoveryHealthy &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.BLACKJACK, flags)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("‹ Rewards", fontWeight = FontWeight.Bold)
        }

        Text(
            "Puppy Casino",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "Treat-based games using your existing Puppy Clicker economy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        CasinoWalletCard(state)

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("🎰 " + casinoFlag.statusLabel(), fontWeight = FontWeight.Black)
                Text(
                    "The hub is installed now, but new wagers stay locked until each game engine passes its rules and transaction tests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No chips or second wallet: every wager and payout uses Treats.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (recoveryIssue != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("⚠️ Casino recovery protection", fontWeight = FontWeight.Black)
                    Text(
                        recoveryIssue ?: "Saved Casino data failed validation.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "New wagers are blocked to protect your Treat balance. Restore a known-good .pupsave or use the existing full local-data reset if you intentionally want to discard the damaged save.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (activeRound != null) {
            Spacer(Modifier.height(12.dp))
            CasinoRecoveryCard(
                round = activeRound!!,
                vm = vm,
                onResumeBlackjack = { page = "blackjack" }
            )
        }

        Spacer(Modifier.height(16.dp)
        Text("Games", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))

        CasinoGameCard(
            emoji = "🎰",
            title = "Puppy Slots",
            detail = "Weighted symbols, published payout table, and committed outcomes before animations.",
            flag = slotsFlag,
            canStart = slotsCanStart,
            onOpen = { page = "slots" }
        )

        Spacer(Modifier.height(8.dp))

        CasinoGameCard(
            emoji = "🎡",
            title = "Puppy Roulette",
            detail = "Single-zero table with red/black, odd/even, halves, and recorded bets before the spin.",
            flag = rouletteFlag,
            canStart = rouletteCanStart,
            onOpen = { page = "roulette" }
        )

        Spacer(Modifier.height(8.dp))

        CasinoGameCard(
            emoji = "🃏",
            title = "Puppy Blackjack",
            detail = "CPU dealer, soft-17 rules, 3:2 natural Blackjack, doubling and splits.",
            flag = blackjackFlag,
            canStart = blackjackCanStart,
            onOpen = { page = "blackjack" }
        )

        Spacer(Modifier.height(16.dp))

        CasinoRewardsCard(
            ledger = rewardLedger,
            lastReward = lastTicketReward
        )

        Spacer(Modifier.height(10.dp))

        CasinoPuppyRewardsCard(
            state = state,
            ledger = puppyRewardLedger,
            lastReward = lastPuppyReward
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("🛡️ Fair Play", fontWeight = FontWeight.Black)
                Text(
                    "Every accepted round has one round ID, one wager, one committed outcome, and one settlement. Closing the app cannot create a new roll for the same wager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CasinoWalletCard(state: V6GameState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🍪", fontSize = 34.sp)
            Column(Modifier.weight(1f)) {
                Text("Treat Wallet", fontWeight = FontWeight.Black)
                Text(
                    state.treats.toString() + " Treats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Tickets", style = MaterialTheme.typography.labelSmall)
                Text(
                    "🎟️ " + state.ticketsOwned,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CasinoGameCard(
    emoji: String,
    title: String,
    detail: String,
    flag: PuppyFeatureFlag,
    canStart: Boolean,
    onOpen: (() -> Unit)? = null
) {
    val statusLabel =
        if (canStart) flag.statusLabel()
        else if (flag.isAvailable()) "Casino Unavailable"
        else flag.statusLabel()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (canStart) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (canStart) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            OutlinedButton(
                onClick = { onOpen?.invoke() },
                enabled = onOpen != null
            ) {
                Text(
                    when {
                        onOpen != null && canStart -> "Play"
                        onOpen != null -> "View"
                        canStart -> "Not Ready"
                        else -> "Locked"
                    }
                )
            }
        }
    }
}

@Composable
private fun CasinoRecoveryCard(
    round: PuppyCasinoRound,
    vm: PuppyClickerV6ViewModel,
    onResumeBlackjack: () -> Unit
) {
    val stateLabel = when (round.state) {
        PuppyCasinoRoundState.WAGER_ACCEPTED -> "Wager accepted"
        PuppyCasinoRoundState.OUTCOME_COMMITTED -> "Outcome committed"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Interrupted casino round", fontWeight = FontWeight.Black)
            Text(
                round.game.name.replace('_', ' ') + " · " + stateLabel,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Wager: " + round.wagerTreats + " Treats",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Round ID: " + round.roundId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            when (PuppyCasinoFeaturePolicy.recoveryAction(round)) {
                PuppyCasinoRecoveryAction.REFUND_ACCEPTED_WAGER -> {
                    OutlinedButton(
                        onClick = { vm.refundCasinoRound(round.roundId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refund Accepted Wager")
                    }
                }

                PuppyCasinoRecoveryAction.RESUME_BLACKJACK -> {
                    Button(
                        onClick = onResumeBlackjack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resume Blackjack Hand")
                    }
                }

                PuppyCasinoRecoveryAction.SETTLE_COMMITTED_OUTCOME -> {
                    Button(
                        onClick = { vm.settleCasinoRound(round.roundId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Complete Saved Settlement")
                    }
                }
            }
        }
    }
}


@Composable
private fun CasinoRewardsCard(
    ledger: PuppyCasinoRewardLedger,
    lastReward: PuppyCasinoTicketReward?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("🎟️ Casino Upgrade Tickets", fontWeight = FontWeight.Black)
            Text(
                "Profitable settled rounds can drop at most 1 existing Upgrade Ticket.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(7.dp))

            Row(Modifier.fillMaxWidth()) {
                Text("Daily casino cap", modifier = Modifier.weight(1f))
                Text(
                    ledger.dailyTicketAwards.toString() + " / " +
                        PuppyCasinoRewardEngine.MAX_DAILY_CASINO_TICKETS,
                    fontWeight = FontWeight.Black
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Inventory cap", modifier = Modifier.weight(1f))
                Text(
                    PuppyCasinoRewardEngine.MAX_TICKETS_PER_RARITY.toString() +
                        " / rarity",
                    fontWeight = FontWeight.Black
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Rarity weights", modifier = Modifier.weight(1f))
                Text("60 / 25 / 10 / 4 / 1", fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(7.dp))

            Text(
                "Drop chance: <2× profit return 5% · 2× 10% · 5× 20% · " +
                    "20× 35% · 100×+ guaranteed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Each settled round is evaluated once. Restarting or replaying settlement cannot reroll the Ticket.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            lastReward?.let { reward ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ) {
                    Text(
                        casinoRewardLabel(reward),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

        }
    }
}

private fun casinoRewardLabel(reward: PuppyCasinoTicketReward): String =
    when (reward.status) {
        PuppyCasinoRewardStatus.AWARDED ->
            "Ticket awarded: " +
                (reward.rarity?.emoji ?: "🎟️") + " " +
                (reward.rarity?.displayName ?: "Upgrade Ticket")
        PuppyCasinoRewardStatus.NO_DROP -> "No Ticket dropped this round."
        PuppyCasinoRewardStatus.NOT_ELIGIBLE -> "No Ticket: the round did not finish with a profit."
        PuppyCasinoRewardStatus.DAILY_CAP_REACHED -> "No Ticket: daily casino Ticket cap reached."
        PuppyCasinoRewardStatus.INVENTORY_CAP_REACHED ->
            "No Ticket: " + (reward.rarity?.displayName ?: "rarity") + " inventory is full."
        PuppyCasinoRewardStatus.ALREADY_EVALUATED -> "Reward already evaluated for this round."
    }


@Composable
private fun CasinoPuppyRewardsCard(
    state: V6GameState,
    ledger: PuppyCasinoPuppyRewardLedger,
    lastReward: PuppyCasinoPuppyReward?
) {
    val eligible = PuppyCasinoPuppyRewardEngine.eligibleStyles
    val ownedEligible = eligible.count { it.id in state.unlockedPuppies }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("🐶 Casino Puppy Rewards", fontWeight = FontWeight.Black)
            Text(
                "Casino wins can unlock verified puppies directly into your existing Roster.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(7.dp))

            Row(Modifier.fillMaxWidth()) {
                Text("Eligible puppies", modifier = Modifier.weight(1f))
                Text(
                    ownedEligible.toString() + " / " + eligible.size + " owned",
                    fontWeight = FontWeight.Black
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Daily casino cap", modifier = Modifier.weight(1f))
                Text(
                    ledger.dailyPuppyUnlocks.toString() + " / " +
                        PuppyCasinoPuppyRewardEngine.MAX_DAILY_CASINO_PUPPIES,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(7.dp))

            Text(
                "Drop chance: <2× profitable return 0.25% · 2× 0.50% · " +
                    "5× 1.50% · 20× 5% · 100×+ guaranteed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "A settled round is evaluated once. The chosen puppy is deterministic and duplicates are skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "Pool: " + eligible.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(6.dp))
            Text(
                "Reserved: seasonal, developer, secret, tribute, and branded special puppies are not casino drops.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            lastReward?.let { reward ->
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ) {
                    Text(
                        casinoPuppyRewardLabel(reward),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun casinoPuppyRewardLabel(reward: PuppyCasinoPuppyReward): String =
    when (reward.status) {
        PuppyCasinoPuppyRewardStatus.UNLOCKED -> {
            val style = reward.styleId?.let { id ->
                PuppyCasinoPuppyRewardEngine.eligibleStyles.firstOrNull { it.id == id }
            }
            "Puppy unlocked: " +
                (style?.emoji ?: "🐶") + " " +
                (style?.name ?: reward.styleId ?: "Puppy")
        }
        PuppyCasinoPuppyRewardStatus.NO_DROP -> "No puppy dropped this round."
        PuppyCasinoPuppyRewardStatus.NOT_ELIGIBLE ->
            "No puppy roll: the round did not finish with a profit."
        PuppyCasinoPuppyRewardStatus.DAILY_CAP_REACHED ->
            "No puppy: daily casino puppy cap reached."
        PuppyCasinoPuppyRewardStatus.ALL_ELIGIBLE_OWNED ->
            "All casino-eligible puppies are already in your Roster."
        PuppyCasinoPuppyRewardStatus.ALREADY_EVALUATED ->
            "Puppy reward already evaluated for this round."
    }
