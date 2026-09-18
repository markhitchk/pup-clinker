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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val showResult =
        display != null &&
            revealRoundId != null &&
            revealFinishedRoundId == revealRoundId
    val dropping =
        display != null &&
            !showResult &&
            plinkoRound?.state == PuppyCasinoRoundState.OUTCOME_COMMITTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Puppy Casino", fontWeight = FontWeight.Bold) }
        Text("Pup Plinko", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "Drop a Treat ball through eight rows of pegs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        PlinkoWallet(state)
        Spacer(Modifier.height(12.dp))

        PlinkoCartoonStage(
            outcome = display,
            dropKey = plinkoRound?.roundId ?: revealRoundId,
            animationsEnabled = state.animationsEnabled,
            status = when {
                dropping -> "Ball in motion…"
                showResult -> "Landed on ${display!!.multiplierLabel} • ${display.payoutTreats} Treats returned"
                else -> "Choose a wager and drop the ball."
            }
        )

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
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(14.dp)
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
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(19.dp)
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
private fun PlinkoCartoonStage(
    outcome: PuppyPlinkoOutcome?,
    dropKey: String?,
    animationsEnabled: Boolean,
    status: String
) {
    PuppyCasinoRenderStage(
        title = "PUP PLINKO",
        height = 330.dp,
        accent = Color(0xFF00B8F0)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(224.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 28.dp)
                .offset(y = 40.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0B1117),
            border = BorderStroke(1.dp, Color(0xFF394854)),
            shadowElevation = 0.dp
        ) {
            PlinkoBoard(outcome, dropKey, animationsEnabled)
        }
        PuppyCasinoRenderStatusBar(
            text = status,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 0.dp)
        )
    }
}

@Composable
private fun PlinkoBoard(
    outcome: PuppyPlinkoOutcome?,
    dropKey: String?,
    animationsEnabled: Boolean
) {
    val progress = remember { Animatable(1f) }
    val palette = puppyCasinoCartoonPalette()
    val binColors = listOf(
        Color(0xFFE9588C),
        Color(0xFF845DE8),
        Color(0xFF30A9E8),
        Color(0xFF22BFC8),
        Color(0xFFFFC13A),
        Color(0xFF22BFC8),
        Color(0xFF30A9E8),
        Color(0xFF845DE8),
        Color(0xFFE9588C)
    )

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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp)) {
            val rows = PuppyPlinkoEngine.ROWS
            val centerX = size.width / 2f
            val topY = 21f
            val bottomY = size.height - 48f
            val rowGap = (bottomY - topY) / (rows + 1f)
            val xStep = size.width / (rows + 3f) / 2f

            drawRoundRect(
                color = Color(0xFF0B1117),
                topLeft = Offset(3f, 3f),
                size = Size(size.width - 6f, size.height - 6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f)
            )
            drawRoundRect(
                color = Color(0xFF394854),
                topLeft = Offset(8f, 8f),
                size = Size(size.width - 16f, size.height - 16f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                style = Stroke(2f)
            )

            for (row in 0 until rows) {
                val count = row + 1
                val y = topY + (row + 1) * rowGap
                val firstX = centerX - row * xStep
                val impactAt = (row + 1f) / (rows + 1f)
                val pulse = PuppyCasinoCartoonMath.impactPulse(progress.value, impactAt, 0.045f)
                repeat(count) { col ->
                    val x = firstX + col * xStep * 2f
                    drawCircle(Color.Black.copy(alpha = 0.28f), radius = 4.2f + pulse * 1.2f, center = Offset(x + 1.5f, y + 2.5f))
                    drawCircle(Color(0xFFD7E4EE), radius = 3.0f + pulse * 1.2f, center = Offset(x, y))
                    drawCircle(Color.White.copy(alpha = 0.70f), radius = 1.0f, center = Offset(x - 0.9f, y - 0.9f))
                }
            }

            val binTop = bottomY - 4f
            val binWidth = size.width / (rows + 1f)
            repeat(rows + 1) { bin ->
                val left = bin * binWidth
                drawLine(
                    color = Color(0xFF394854),
                    start = Offset(left, binTop),
                    end = Offset(left, size.height - 5f),
                    strokeWidth = 1.5f
                )
            }

            val path = outcome?.pathRight ?: emptyList()
            val travelSegments = rows + 1
            val scaled = progress.value * travelSegments
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

            drawCircle(Color.Black.copy(alpha = 0.30f), 8.5f, Offset(x + 1.5f, y + 2.5f))
            drawCircle(Color(0xFFC87932), 7f, Offset(x, y))
            drawCircle(Color(0xFFFFB65C), 5.8f, Offset(x, y - 0.5f))
            drawCircle(Color.White.copy(alpha = 0.62f), 1.5f, Offset(x - 2f, y - 2.5f))
            val chipOffsets = listOf(
                Offset(-4f, -1f), Offset(3f, 1f), Offset(0f, 5f), Offset(4f, -4f), Offset(-3f, 4f)
            )
            chipOffsets.forEach { delta ->
                drawCircle(Color(0xFF6B3A24), 1.5f, Offset(x + delta.x, y + delta.y))
            }

            drawCircle(
                color = palette.gold.copy(alpha = 0.32f),
                radius = 17f,
                center = Offset(centerX, topY),
                style = Stroke(2.5f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(start = 7.dp, end = 7.dp, bottom = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PuppyPlinkoEngine.binMultiplierHundredths.forEach { value ->
                Text(
                    text = if (value % 100 == 0) (value / 100).toString() + "×" else (value / 100.0).toString() + "×",
                    color = Color(0xFFCBD7DE),
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun PlinkoWallet(state: V6GameState) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(78.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(13.dp),
                color = Color(0xFFEAF8FD)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("🍪", fontSize = 22.sp) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Treat Wallet", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${state.treats} Treats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                PuppyPlinkoEngine.PUBLISHED_RTP_PERCENT + " RTP",
                color = Color(0xFF00B8F0),
                fontWeight = FontWeight.Black
            )
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
