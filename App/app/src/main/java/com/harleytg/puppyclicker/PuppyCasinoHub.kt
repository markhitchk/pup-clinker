package com.harleytg.puppyclicker

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val ui by PuppyUiPreferences.observe(context).collectAsStateWithLifecycle()
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val activeRound by vm.casinoRound.collectAsStateWithLifecycle()
    val recoveryIssue by vm.casinoRecoveryIssue.collectAsStateWithLifecycle()
    val rewardLedger by vm.casinoRewardLedger.collectAsStateWithLifecycle()
    val lastTicketReward by vm.lastCasinoTicketReward.collectAsStateWithLifecycle()
    val puppyRewardLedger by vm.casinoPuppyRewardLedger.collectAsStateWithLifecycle()
    val lastPuppyReward by vm.lastCasinoPuppyReward.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("hub") }
    var casinoWarningAcceptedForVisit by rememberSaveable { mutableStateOf(false) }
    var dontShowCasinoWarningAgain by rememberSaveable { mutableStateOf(false) }
    var reportedCasinoOpen by rememberSaveable { mutableStateOf(false) }

    val showCasinoWarning =
        !ui.casinoDisclaimerHidden && !casinoWarningAcceptedForVisit

    BackHandler(enabled = page != "hub") {
        page = "hub"
    }

    if (showCasinoWarning) {
        AlertDialog(
            onDismissRequest = onBack,
            title = {
                Text(
                    "Puppy Casino — Simulated Gambling",
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column {
                    Text(
                        "This area contains simulated casino-style games, including slots, roulette, blackjack, Plinko, scratch cards, and the Lucky Pup Wheel."
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No real money is used, no prizes have real-world cash value, and nothing can be cashed out."
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "By continuing, you acknowledge that this content is for entertainment only."
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowCasinoWarningAgain,
                            onCheckedChange = { dontShowCasinoWarningAgain = it }
                        )
                        Text("Don’t show again")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dontShowCasinoWarningAgain) {
                            PuppyUiPreferences.setCasinoDisclaimerHidden(context, true)
                        }
                        casinoWarningAcceptedForVisit = true
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("Go Back")
                }
            }
        )
    }

    LaunchedEffect(showCasinoWarning, page) {
        PuppyCasinoRuntimeGuard.markPage(page)
        if (!showCasinoWarning && page == "hub" && !reportedCasinoOpen) {
            reportedCasinoOpen = true
            PuppySupportReporting.reportTelemetry(context, "casino_open")
        }
    }

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
    if (page == "plinko") {
        PuppyPlinkoScreen(
            state = state,
            vm = vm,
            onBack = { page = "hub" }
        )
        return
    }
    if (page == "scratchers") {
        PuppyScratchersScreen(
            state = state,
            vm = vm,
            onBack = { page = "hub" }
        )
        return
    }
    if (page == "lucky_wheel") {
        PuppyLuckyWheelScreen(
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
    val plinkoFlag = flags["casino_plinko"] ?: PuppyFeatureFlags.flag("casino_plinko")
    val scratchersFlag = flags["casino_scratchers"] ?: PuppyFeatureFlags.flag("casino_scratchers")
    val luckyWheelFlag = flags["casino_lucky_wheel"] ?: PuppyFeatureFlags.flag("casino_lucky_wheel")
    val recoveryHealthy = recoveryIssue == null
    val slotsCanStart = recoveryHealthy &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.SLOTS, flags)
    val rouletteCanStart = recoveryHealthy &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.ROULETTE, flags)
    val blackjackCanStart = recoveryHealthy &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.BLACKJACK, flags)
    val plinkoCanStart = recoveryHealthy &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.PLINKO, flags)
    val scratchersCanStart = recoveryHealthy &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.SCRATCHERS, flags)
    val luckyWheelCanStart = recoveryHealthy &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.LUCKY_WHEEL, flags)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
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
            "Casino Chip games isolated from normal Puppy Clicker progression.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Simulated gambling • No real-money wagering or cash prizes.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(14.dp))

        CasinoWalletCard(state, vm)

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
                    "All wagers, payouts, refunds and extra wagers use Casino Chips only.",
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
                        "New wagers are blocked to protect your Casino Chip balance. Restore a known-good .pupsave or use the existing full local-data reset if you intentionally want to discard the damaged save.",
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

        Spacer(Modifier.height(16.dp))
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

        Spacer(Modifier.height(8.dp))

        CasinoGameCard(
            emoji = "📍",
            title = "Pup Plinko",
            detail = "Drop the Plinko ball through eight rows of pegs into published multiplier bins.",
            flag = plinkoFlag,
            canStart = plinkoCanStart,
            onOpen = { page = "plinko" }
        )

        Spacer(Modifier.height(8.dp))

        CasinoGameCard(
            emoji = "🎟️",
            title = "Pup Scratchers",
            detail = "Scratch the coating with touch input to reveal a committed prize.",
            flag = scratchersFlag,
            canStart = scratchersCanStart,
            onOpen = { page = "scratchers" }
        )

        Spacer(Modifier.height(8.dp))

        CasinoGameCard(
            emoji = "🎡",
            title = "Lucky Pup Wheel",
            detail = "Spin for Casino Chip multipliers or a casino-eligible puppy unlock.",
            flag = luckyWheelFlag,
            canStart = luckyWheelCanStart,
            onOpen = { page = "lucky_wheel" }
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

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CasinoWalletCard(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🐾", fontSize = 34.sp)
                Column(Modifier.weight(1f)) {
                    Text("Casino Chip Wallet", fontWeight = FontWeight.Black)
                    Text(
                        "${state.casinoChips} Casino Chips",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "${state.treats} Treats available for exchange",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("🐾 Get Casino Chips", fontWeight = FontWeight.Black)
            Text(
                "Casino Chips are isolated from progression. Chips cannot be converted back to Treats.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                500L to 50L,
                2_500L to 250L,
                10_000L to 1_000L
            ).forEach { (treats, chips) ->
                OutlinedButton(
                    onClick = { vm.convertTreatsToCasinoChips(treats) },
                    enabled = state.treats >= treats,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Text("$treats Treats → $chips Chips")
                }
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
                "Wager: " + round.wagerTreats + " Chips",
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
                "Drop chance: profitable <2× 0.25% · 2×–<5× 0.5% · " +
                    "5×–<20× 1% · 20×–<100× 2% · 100×+ 5%.",
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
