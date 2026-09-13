package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val RouletteRed = Color(0xFFB92B35)
private val RouletteBlack = Color(0xFF16181C)
private val RouletteGreen = Color(0xFF187A45)

@Composable
internal fun PuppyRouletteScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onBack: () -> Unit
) {
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val activeRound by vm.casinoRound.collectAsStateWithLifecycle()
    val recoveryIssue by vm.casinoRecoveryIssue.collectAsStateWithLifecycle()
    val canPlayFeature =
        recoveryIssue == null &&
            PuppyCasinoFeaturePolicy.canStartNewRound(
                game = PuppyCasinoGame.ROULETTE,
                flags = flags
            )

    var wager by rememberSaveable { mutableLongStateOf(100L) }
    var selectedTypeName by rememberSaveable {
        mutableStateOf(PuppyRouletteBetType.RED.name)
    }
    var selectedNumber by rememberSaveable { mutableIntStateOf(0) }
    var lastOutcomePayload by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOutcomeWager by rememberSaveable { mutableLongStateOf(0L) }
    var lastBetPayload by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedType = runCatching {
        PuppyRouletteBetType.valueOf(selectedTypeName)
    }.getOrDefault(PuppyRouletteBetType.RED)
    val selectedBet = remember(selectedType, selectedNumber) {
        if (selectedType == PuppyRouletteBetType.STRAIGHT) {
            PuppyRouletteBet(PuppyRouletteBetType.STRAIGHT, selectedNumber)
        } else {
            PuppyRouletteBet(selectedType)
        }
    }

    val rouletteRound = activeRound?.takeIf { it.game == PuppyCasinoGame.ROULETTE }
    val recoveredBet = remember(rouletteRound?.wagerPayload) {
        PuppyRouletteBetCodec.decodeAndValidate(rouletteRound?.wagerPayload)
    }
    val recoveredOutcome = remember(
        rouletteRound?.outcomePayload,
        rouletteRound?.wagerTreats,
        recoveredBet
    ) {
        val round = rouletteRound
        val bet = recoveredBet
        if (
            round != null &&
            bet != null &&
            round.state == PuppyCasinoRoundState.OUTCOME_COMMITTED
        ) {
            PuppyRouletteOutcomeCodec.decodeAndValidate(
                raw = round.outcomePayload,
                wagerTreats = round.wagerTreats,
                expectedBet = bet
            )
        } else {
            null
        }
    }

    LaunchedEffect(
        rouletteRound?.roundId,
        rouletteRound?.state,
        rouletteRound?.outcomePayload,
        rouletteRound?.wagerPayload
    ) {
        val round = rouletteRound ?: return@LaunchedEffect
        if (round.state != PuppyCasinoRoundState.OUTCOME_COMMITTED) return@LaunchedEffect

        val bet = PuppyRouletteBetCodec.decodeAndValidate(round.wagerPayload)
        val outcome = bet?.let {
            PuppyRouletteOutcomeCodec.decodeAndValidate(
                raw = round.outcomePayload,
                wagerTreats = round.wagerTreats,
                expectedBet = it
            )
        }

        if (outcome == null || outcome.payoutTreats != round.payoutTreats) {
            message = "Saved Roulette outcome failed validation. Settlement was blocked."
            return@LaunchedEffect
        }

        lastOutcomePayload = round.outcomePayload
        lastOutcomeWager = round.wagerTreats
        lastBetPayload = round.wagerPayload

        delay(900)

        val settled = vm.settleCasinoRound(round.roundId)
        if (!settled.success) {
            message = "Unable to settle the saved Roulette round: " +
                (settled.failure?.name ?: "unknown error")
        }
    }

    val lastBet = remember(lastBetPayload) {
        PuppyRouletteBetCodec.decodeAndValidate(lastBetPayload)
    }
    val lastOutcome = remember(lastOutcomePayload, lastOutcomeWager, lastBet) {
        lastBet?.let {
            PuppyRouletteOutcomeCodec.decodeAndValidate(
                raw = lastOutcomePayload,
                wagerTreats = lastOutcomeWager,
                expectedBet = it
            )
        }
    }
    val displayOutcome = recoveredOutcome ?: lastOutcome

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("‹ Puppy Casino", fontWeight = FontWeight.Bold)
        }

        Text(
            "Puppy Roulette",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "European single-zero roulette. Your bet is saved before the wheel resolves.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        RouletteWalletCard(state)

        Spacer(Modifier.height(12.dp))

        displayOutcome?.let { outcome ->
            RouletteResultCard(outcome)
            Spacer(Modifier.height(12.dp))
        }

        rouletteRound?.let { round ->
            if (round.state == PuppyCasinoRoundState.WAGER_ACCEPTED) {
                RouletteInterruptedWagerCard(
                    round = round,
                    bet = recoveredBet,
                    onRefund = { vm.refundCasinoRound(round.roundId) }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        message?.let { text ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(text, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        Text("Choose bet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            "Zero is green and loses red/black, odd/even, and 1–18/19–36 bets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        RouletteOutsideBetRow(
            selectedType = selectedType,
            onSelect = { selectedTypeName = it.name }
        )

        Spacer(Modifier.height(12.dp))

        Text("Straight number", fontWeight = FontWeight.Black)
        Text(
            "Pick any number from 0 to 36 for a 36× total return.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        RouletteNumberTable(
            selectedNumber = selectedNumber,
            selected = selectedType == PuppyRouletteBetType.STRAIGHT,
            onSelect = { number ->
                selectedNumber = number
                selectedTypeName = PuppyRouletteBetType.STRAIGHT.name
            }
        )

        Spacer(Modifier.height(16.dp))

        Text("Wager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            "Payout multipliers are total Treats returned, including the wager.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PuppyRouletteEngine.wagerPresets.forEach { preset ->
                FilterChip(
                    selected = wager == preset,
                    onClick = { wager = preset },
                    label = { Text(preset.toString()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                message = null
                val result = vm.startRouletteSpin(
                    bet = selectedBet,
                    wagerTreats = wager
                )
                if (!result.success) {
                    message = when (result.failure) {
                        PuppyRouletteStartFailure.INVALID_WAGER -> "Invalid Roulette wager."
                        PuppyRouletteStartFailure.INVALID_BET -> "Invalid Roulette bet."
                        PuppyRouletteStartFailure.TRANSACTION_REJECTED ->
                            "Spin blocked: " +
                                (result.transactionFailure?.name ?: "transaction rejected")
                        PuppyRouletteStartFailure.OUTCOME_GENERATION_FAILED ->
                            "Wheel resolution failed. The accepted wager was refunded."
                        PuppyRouletteStartFailure.OUTCOME_COMMIT_FAILED ->
                            "Outcome could not be committed. Use the interrupted-wager recovery control."
                        null -> "Roulette spin could not start."
                    }
                }
            },
            enabled =
                canPlayFeature &&
                    activeRound == null &&
                    PuppyRouletteEngine.isValidWager(wager) &&
                    state.treats >= wager,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !canPlayFeature -> "Roulette Locked"
                    activeRound != null -> "Round In Progress"
                    state.treats < wager -> "Not Enough Treats"
                    else -> "Spin " + selectedBet.label + " · " + wager + " Treats"
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        RouletteRulesCard()

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RouletteWalletCard(state: V6GameState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🍪", fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Treat Wallet", fontWeight = FontWeight.Black)
                Text(
                    state.treats.toString() + " Treats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                PuppyRouletteEngine.PUBLISHED_RTP_PERCENT + " RTP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RouletteOutsideBetRow(
    selectedType: PuppyRouletteBetType,
    onSelect: (PuppyRouletteBetType) -> Unit
) {
    val rows = listOf(
        listOf(PuppyRouletteBetType.RED, PuppyRouletteBetType.BLACK),
        listOf(PuppyRouletteBetType.ODD, PuppyRouletteBetType.EVEN),
        listOf(PuppyRouletteBetType.LOW, PuppyRouletteBetType.HIGH)
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { onSelect(type) },
                        label = { Text(type.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RouletteNumberTable(
    selectedNumber: Int,
    selected: Boolean,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        RouletteNumberButton(
            number = 0,
            selected = selected && selectedNumber == 0,
            onClick = { onSelect(0) },
            modifier = Modifier.fillMaxWidth()
        )

        (1..36).chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                row.forEach { number ->
                    RouletteNumberButton(
                        number = number,
                        selected = selected && selectedNumber == number,
                        onClick = { onSelect(number) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun RouletteNumberButton(
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when (PuppyRouletteEngine.colorOf(number)) {
        PuppyRouletteColor.GREEN -> RouletteGreen
        PuppyRouletteColor.RED -> RouletteRed
        PuppyRouletteColor.BLACK -> RouletteBlack
    }

    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = color,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                number.toString(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RouletteResultCard(outcome: PuppyRouletteOutcome) {
    val color = when (outcome.color) {
        PuppyRouletteColor.GREEN -> RouletteGreen
        PuppyRouletteColor.RED -> RouletteRed
        PuppyRouletteColor.BLACK -> RouletteBlack
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                outcome.winningNumber.toString(),
                fontSize = 44.sp,
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            Text(
                outcome.color.name,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (outcome.won) {
                    "WIN · " + outcome.totalReturnMultiplier + "× · " +
                        outcome.payoutTreats + " Treats returned"
                } else {
                    "No win · 0 Treats returned"
                },
                color = Color.White,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RouletteInterruptedWagerCard(
    round: PuppyCasinoRound,
    bet: PuppyRouletteBet?,
    onRefund: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Interrupted Roulette wager", fontWeight = FontWeight.Black)
            Text(
                (bet?.label ?: "Saved bet unavailable") +
                    " · " + round.wagerTreats + " Treats",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "The bet was persisted before the wheel resolved.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRefund, modifier = Modifier.fillMaxWidth()) {
                Text("Refund Accepted Wager")
            }
        }
    }
}

@Composable
private fun RouletteRulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Published Roulette rules", fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            RouletteRuleRow("Straight number 0–36", "36× total")
            RouletteRuleRow("Red / Black", "2× total")
            RouletteRuleRow("Odd / Even", "2× total")
            RouletteRuleRow("1–18 / 19–36", "2× total")
            Spacer(Modifier.height(6.dp))
            Text(
                "Zero is green. It wins only a straight bet on 0 and loses all even-money bets.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "The selected bet is stored with the accepted wager before the winning number is generated.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "European single-zero theoretical RTP: " +
                    PuppyRouletteEngine.PUBLISHED_RTP_PERCENT + ".",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RouletteRuleRow(label: String, payout: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(payout, fontWeight = FontWeight.Black)
    }
}
