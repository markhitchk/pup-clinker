package com.harleytg.puppyclicker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun PuppyPlinkoScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onBack: () -> Unit
) {
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val activeRound by vm.casinoRound.collectAsStateWithLifecycle()
    val recoveryIssue by vm.casinoRecoveryIssue.collectAsStateWithLifecycle()
    var wager by rememberSaveable { mutableLongStateOf(PuppyPlinkoEngine.wagerPresets.first()) }
    var lastOutcome by remember { mutableStateOf<PuppyPlinkoOutcome?>(null) }
    var revealRoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var revealFinishedRoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val canPlayFeature = recoveryIssue == null &&
        PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.PLINKO, flags)
    val plinkoRound = activeRound?.takeIf { it.game == PuppyCasinoGame.PLINKO }
    val recoveredOutcome = remember(plinkoRound?.outcomePayload, plinkoRound?.wagerTreats) {
        plinkoRound
            ?.takeIf { it.state == PuppyCasinoRoundState.OUTCOME_COMMITTED }
            ?.let { PuppyPlinkoOutcomeCodec.decodeAndValidate(it.outcomePayload, it.wagerTreats) }
    }

    LaunchedEffect(plinkoRound?.roundId, plinkoRound?.state, plinkoRound?.outcomePayload) {
        val round = plinkoRound ?: return@LaunchedEffect
        if (round.state != PuppyCasinoRoundState.OUTCOME_COMMITTED) return@LaunchedEffect
        val outcome = PuppyPlinkoOutcomeCodec.decodeAndValidate(round.outcomePayload, round.wagerTreats)
        if (outcome == null || outcome.payoutTreats != round.payoutTreats) {
            message = "Saved Plinko outcome failed validation. Settlement was blocked."
            return@LaunchedEffect
        }
        lastOutcome = outcome
        revealRoundId = round.roundId
        revealFinishedRoundId = null
        delay(if (state.animationsEnabled) 2_200 else 250)
        revealFinishedRoundId = round.roundId
        val settled = vm.settleCasinoRound(round.roundId)
        if (!settled.success) {
            message = "Unable to settle the saved Plinko round: " +
                (settled.failure?.name ?: "unknown error")
        }
    }

    val display = recoveredOutcome ?: lastOutcome
    val dropping = revealRoundId != null && revealFinishedRoundId != revealRoundId
    val showResult = display != null && !dropping

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Puppy Casino", fontWeight = FontWeight.Bold) }
        Text("Pup Plinko", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "Drop a Treat ball through eight rows of pegs. The path is committed before the animation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        PlinkoWallet(state)
        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF10141A),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🐾 PUP PLINKO", color = Color.White, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                PlinkoBoard(
                    outcome = display,
                    dropKey = revealRoundId,
                    animationsEnabled = state.animationsEnabled
                )
                Text(
                    PuppyPlinkoEngine.binMultiplierHundredths.joinToString("  ") {
                        if (it % 100 == 0) (it / 100).toString() + "×" else (it / 100.0).toString() + "×"
                    },
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        dropping -> "Ball in motion…"
                        showResult -> "Landed on ${display!!.multiplierLabel} • ${display.payoutTreats} Treats returned"
                        else -> "Choose a wager and drop the ball."
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (plinkoRound?.state == PuppyCasinoRoundState.WAGER_ACCEPTED) {
            Spacer(Modifier.height(10.dp))
            PlinkoInterruptedCard(plinkoRound, vm)
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
        Text("Wager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PuppyPlinkoEngine.wagerPresets.forEach { preset ->
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
                val result = vm.startPlinkoDrop(wager)
                if (!result.success) {
                    message = "Plinko drop blocked: " +
                        (result.transactionFailure?.name ?: result.failure?.name ?: "unknown error")
                }
            },
            enabled = canPlayFeature && activeRound == null &&
                PuppyPlinkoEngine.isValidWager(wager) && state.treats >= wager,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !canPlayFeature -> "Plinko Locked"
                    activeRound != null -> "Round In Progress"
                    state.treats < wager -> "Not Enough Treats"
                    else -> "Drop Ball · $wager Treats"
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
                Text("How Pup Plinko works", fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text("• Eight saved left/right bounces determine one of nine bins.", style = MaterialTheme.typography.bodySmall)
                Text("• Published RTP: ${PuppyPlinkoEngine.PUBLISHED_RTP_PERCENT}.", style = MaterialTheme.typography.bodySmall)
                Text("• The exact path, bin, multiplier and payout are committed before the ball moves.", style = MaterialTheme.typography.bodySmall)
                Text("• All wagers and payouts use the existing Treat balance.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PlinkoBoard(
    outcome: PuppyPlinkoOutcome?,
    dropKey: String?,
    animationsEnabled: Boolean
) {
    val progress = remember { Animatable(1f) }
    val pegColor = Color.White.copy(alpha = 0.72f)
    val ballColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(dropKey, outcome, animationsEnabled) {
        if (outcome == null) {
            progress.snapTo(0f)
        } else if (!animationsEnabled || dropKey == null) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(2_050, easing = FastOutSlowInEasing))
        }
    }

    Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val rows = PuppyPlinkoEngine.ROWS
            val centerX = size.width / 2f
            val topY = 18f
            val bottomY = size.height - 30f
            val rowGap = (bottomY - topY) / (rows + 1f)
            val xStep = size.width / (rows + 3f) / 2f

            for (row in 0 until rows) {
                val count = row + 1
                val y = topY + (row + 1) * rowGap
                val firstX = centerX - row * xStep
                repeat(count) { col ->
                    drawCircle(pegColor, radius = 4.5f, center = Offset(firstX + col * xStep * 2f, y))
                }
            }
            repeat(rows + 1) { bin ->
                val x = centerX + (bin - rows / 2f) * xStep * 2f
                drawLine(
                    Color.White.copy(alpha = 0.25f),
                    Offset(x - xStep, bottomY),
                    Offset(x - xStep, size.height),
                    strokeWidth = 2f
                )
            }

            val path = outcome?.pathRight ?: emptyList()
            val scaled = progress.value * rows
            val completedRows = scaled.toInt().coerceIn(0, rows)
            val fraction = (scaled - completedRows).coerceIn(0f, 1f)
            var x = centerX
            for (i in 0 until completedRows.coerceAtMost(path.size)) {
                x += if (path[i]) xStep else -xStep
            }
            if (completedRows < rows && completedRows < path.size) {
                x += (if (path[completedRows]) xStep else -xStep) * fraction
            }
            val y = topY + scaled * rowGap
            drawCircle(Color.Black.copy(alpha = 0.35f), 10f, Offset(x + 2f, y + 3f))
            drawCircle(ballColor, 9f, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.5f), 2f, Offset(x - 3f, y - 3f))
            drawRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(4f, 4f),
                size = Size(size.width - 8f, size.height - 8f),
                style = Stroke(2f)
            )
        }
    }
}

@Composable
private fun PlinkoWallet(state: V6GameState) {
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
            Text(PuppyPlinkoEngine.PUBLISHED_RTP_PERCENT + " RTP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlinkoInterruptedCard(round: PuppyCasinoRound, vm: PuppyClickerV6ViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Interrupted wager", fontWeight = FontWeight.Black)
            Text("The wager was accepted before an outcome was committed.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.refundCasinoRound(round.roundId) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refund ${round.wagerTreats} Treats") }
        }
    }
}
