package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PuppyScratchersScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onBack: () -> Unit
) {
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val activeRound by vm.casinoRound.collectAsStateWithLifecycle()
    val recoveryIssue by vm.casinoRecoveryIssue.collectAsStateWithLifecycle()
    var wager by rememberSaveable { mutableLongStateOf(PuppyScratchersEngine.wagerPresets.first()) }
    var lastOutcome by remember { mutableStateOf<PuppyScratcherOutcome?>(null) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val canPlayFeature =
        recoveryIssue == null &&
            PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.SCRATCHERS, flags)
    val round = activeRound?.takeIf { it.game == PuppyCasinoGame.SCRATCHERS }
    val recovered = remember(round?.outcomePayload, round?.wagerTreats) {
        round?.takeIf { it.state == PuppyCasinoRoundState.OUTCOME_COMMITTED }
            ?.let { PuppyScratcherOutcomeCodec.decodeAndValidate(it.outcomePayload, it.wagerTreats) }
    }
    val display = recovered ?: lastOutcome
    val scratched = remember(round?.roundId) { mutableStateListOf<Int>() }
    val cellCount = 60
    val scratchProgress = scratched.size.toFloat() / cellCount.toFloat()

    LaunchedEffect(round?.roundId) {
        if (round != null) revealed = false
    }

    LaunchedEffect(round?.roundId, round?.state, scratchProgress) {
        val savedRound = round ?: return@LaunchedEffect
        if (savedRound.state != PuppyCasinoRoundState.OUTCOME_COMMITTED) return@LaunchedEffect
        if (scratchProgress < PuppyScratchersEngine.REVEAL_THRESHOLD) return@LaunchedEffect
        val outcome = PuppyScratcherOutcomeCodec.decodeAndValidate(savedRound.outcomePayload, savedRound.wagerTreats)
        if (outcome == null || outcome.payoutTreats != savedRound.payoutTreats) {
            message = "Saved Scratcher outcome failed validation. Settlement was blocked."
            return@LaunchedEffect
        }
        lastOutcome = outcome
        revealed = true
        val settled = vm.settleCasinoRound(savedRound.roundId)
        if (!settled.success) {
            message = "Unable to settle the saved Scratcher: " +
                (settled.failure?.name ?: "unknown error")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Puppy Casino", fontWeight = FontWeight.Bold) }
        Text("Pup Scratchers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "Scratch the coating with your finger. The saved result settles only after enough of the card is uncovered.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        ScratcherWallet(state)
        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF163B35),
            border = BorderStroke(2.dp, Color(0xFFD8C477))
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎟️ PUP SCRATCHER", color = Color.White, fontWeight = FontWeight.Black)
                Text("SCRATCH TO REVEAL", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
                ScratcherCard(
                    outcome = display,
                    scratched = scratched,
                    enabled = round?.state == PuppyCasinoRoundState.OUTCOME_COMMITTED && !revealed,
                    revealAll = revealed
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        display == null -> "Buy a card to begin."
                        !revealed && round != null -> "Scratch to reveal • ${(scratchProgress * 100).toInt()}%"
                        revealed || round == null -> "${display.prize.label} • ${display.payoutTreats} Treats returned"
                        else -> "Scratch to reveal."
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (round?.state == PuppyCasinoRoundState.WAGER_ACCEPTED) {
            Spacer(Modifier.height(10.dp))
            ScratcherInterruptedCard(round, vm)
        }
        message?.let {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Card cost", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PuppyScratchersEngine.wagerPresets.forEach { preset ->
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
                revealed = false
                scratched.clear()
                val result = vm.startScratcher(wager)
                if (result.success) {
                    lastOutcome = result.outcome
                } else {
                    message = "Scratcher blocked: " +
                        (result.transactionFailure?.name ?: result.failure?.name ?: "unknown error")
                }
            },
            enabled = canPlayFeature && activeRound == null &&
                PuppyScratchersEngine.isValidWager(wager) && state.treats >= wager,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !canPlayFeature -> "Scratchers Locked"
                    activeRound != null -> "Round In Progress"
                    state.treats < wager -> "Not Enough Treats"
                    else -> "Buy Scratcher · $wager Treats"
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Published prize odds", fontWeight = FontWeight.Black)
                Text(
                    "Expected Treat return: ${PuppyScratchersEngine.PUBLISHED_RTP_PERCENT}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                PuppyScratcherPrize.entries.forEach { prize ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${prize.emoji} ${prize.label}", Modifier.weight(1f))
                        Text("${prize.weightPercent}%", fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("• The outcome is committed before scratching.", style = MaterialTheme.typography.bodySmall)
                Text("• At least 55% of the coating must be removed before settlement.", style = MaterialTheme.typography.bodySmall)
                Text("• All costs and payouts use Treats.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ScratcherCard(
    outcome: PuppyScratcherOutcome?,
    scratched: MutableList<Int>,
    enabled: Boolean,
    revealAll: Boolean
) {
    val columns = 12
    val rows = 5
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFFF7D6)
        ) {
            Row(
                Modifier.fillMaxSize().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val symbols = outcome?.symbols ?: listOf("?", "?", "?")
                symbols.forEach { symbol ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(symbol, style = MaterialTheme.typography.displaySmall)
                        Text("PRIZE", color = Color(0xFF2F3A36), fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        if (!revealAll) Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, outcome) {
                    if (!enabled || outcome == null) return@pointerInput
                    fun mark(position: Offset) {
                        val col = ((position.x / size.width) * columns).toInt().coerceIn(0, columns - 1)
                        val row = ((position.y / size.height) * rows).toInt().coerceIn(0, rows - 1)
                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                val c = col + dx
                                val r = row + dy
                                if (c in 0 until columns && r in 0 until rows) {
                                    val index = r * columns + c
                                    if (index !in scratched) scratched.add(index)
                                }
                            }
                        }
                    }
                    detectDragGestures(
                        onDragStart = { mark(it) },
                        onDrag = { change, _ -> mark(change.position) }
                    )
                }
        ) {
            val cellW = size.width / columns
            val cellH = size.height / rows
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index !in scratched) {
                        drawRect(
                            color = Color(0xFFC7CCD1),
                            topLeft = Offset(col * cellW, row * cellH),
                            size = Size(cellW + 1f, cellH + 1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScratcherWallet(state: V6GameState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🍪", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Treat Wallet", fontWeight = FontWeight.Black)
                Text("${state.treats} Treats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            Text(PuppyScratchersEngine.PUBLISHED_RTP_PERCENT + " RTP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ScratcherInterruptedCard(round: PuppyCasinoRound, vm: PuppyClickerV6ViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Interrupted card purchase", fontWeight = FontWeight.Black)
            Text("The card cost was accepted before a result was committed.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.refundCasinoRound(round.roundId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refund ${round.wagerTreats} Treats")
            }
        }
    }
}
