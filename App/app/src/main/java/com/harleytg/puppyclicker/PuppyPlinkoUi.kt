package com.harleytg.puppyclicker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
        val settled = PuppyCasinoRuntimeGuard.run(
            PuppyCasinoGame.PLINKO,
            "settle"
        ) {
            vm.settleCasinoRound(round.roundId)
        }.getOrNull()
        if (settled == null) {
            message = "Plinko recovered from a runtime error while settling the round."
            return@LaunchedEffect
        }
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
            "Drop the Plinko ball through eight rows of pegs into one of nine blue-felt prize pockets. The path is committed before the animation.",
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
                showResult && display != null ->
                    "Landed on ${display.multiplierLabel} • ${display.payoutTreats} Chips returned"
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
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val dropEnabled =
            canPlayFeature &&
                activeRound == null &&
                PuppyPlinkoEngine.isValidWager(wager) &&
                state.casinoChips >= wager
        val dropLabel = when {
            !canPlayFeature -> "Plinko Locked"
            activeRound != null -> "Round In Progress"
            state.casinoChips < wager -> "Not Enough Chips"
            else -> "Drop Ball · $wager Chips"
        }
        PlinkoDropAssetButton(
            enabled = dropEnabled,
            label = dropLabel,
            onClick = {
                message = null
                val result = PuppyCasinoRuntimeGuard.run(
                    PuppyCasinoGame.PLINKO,
                    "start"
                ) {
                    vm.startPlinkoDrop(wager)
                }.getOrNull()
                if (result == null) {
                    message = "Plinko recovered from a runtime error. No new drop was started."
                } else if (!result.success) {
                    message = "Plinko drop blocked: " +
                        (result.transactionFailure?.name ?: result.failure?.name ?: "unknown error")
                }
            }
        )

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
                Text("• All wagers and payouts use the Casino Chip wallet.", style = MaterialTheme.typography.bodySmall)
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val boardWidth = if (maxWidth > 460.dp) 460.dp else maxWidth
        val boardHeight = boardWidth * 1.25f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(boardHeight + 96.dp)
        ) {
            PlinkoBoard(
                outcome = outcome,
                dropKey = dropKey,
                animationsEnabled = animationsEnabled,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .width(boardWidth)
                    .aspectRatio(4f / 5f)
            )

            Image(
                painter = painterResource(R.drawable.puppy_plinko_header),
                contentDescription = "Puppy Plinko",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(boardWidth * 0.92f)
                    .aspectRatio(3f),
                contentScale = ContentScale.Fit
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                shadowElevation = 2.dp
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun PlinkoBoard(
    outcome: PuppyPlinkoOutcome?,
    dropKey: String?,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }

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

    BoxWithConstraints(modifier = modifier) {
        val rows = PuppyPlinkoEngine.ROWS
        val travelSegments = rows + 1
        // Keep the live path inside the cabinet's inner playfield instead of
        // using the artwork's full outer-frame width.
        val centerX = maxWidth * 0.50f
        val xStep = maxWidth * 0.0395f
        val launchY = maxHeight * 0.205f
        val pocketCenterY = maxHeight * 0.835f
        val segmentGap = (pocketCenterY - launchY) / travelSegments.toFloat()

        val path = outcome?.pathRight ?: emptyList()
        val scaled = progress.value * travelSegments
        val completedRows = scaled.toInt().coerceIn(0, rows)
        val fraction = (scaled - completedRows).coerceIn(0f, 1f)

        var ballX = centerX
        for (i in 0 until completedRows.coerceAtMost(path.size)) {
            ballX += if (path[i]) xStep else -xStep
        }
        if (completedRows < rows && completedRows < path.size) {
            ballX += (if (path[completedRows]) xStep else -xStep) * fraction
        }

        val segmentArc = kotlin.math.sin(fraction * kotlin.math.PI).toFloat()
        val jitterPermille =
            outcome?.bounceJitterPermille?.getOrNull(completedRows) ?: 0
        ballX += xStep * (jitterPermille / 1000f) * segmentArc

        val baseY = launchY + segmentGap * scaled
        val bounceLift =
            if (completedRows < rows) maxHeight * 0.009f * segmentArc else 0.dp
        val ballY = baseY - bounceLift
        val ballSize = maxWidth * 0.10f

        // Layered gameplay stack. The live ball remains a separate moving PNG so
        // committed paths/recovery math are unchanged by the visual conversion.
        Image(
            painter = painterResource(R.drawable.puppy_plinko_pegs),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.105f)
                .fillMaxWidth(0.84f)
                .fillMaxHeight(0.73f),
            contentScale = ContentScale.FillBounds
        )

        Image(
            painter = painterResource(R.drawable.puppy_plinko_ball),
            contentDescription = "Plinko ball",
            modifier = Modifier
                .offset(
                    x = ballX - ballSize / 2f,
                    y = ballY - ballSize / 2f
                )
                .size(ballSize),
            contentScale = ContentScale.Fit
        )

        Image(
            painter = painterResource(R.drawable.puppy_plinko_glass),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.58f },
            contentScale = ContentScale.FillBounds
        )

        Image(
            painter = painterResource(R.drawable.puppy_plinko_frame),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        PlinkoBinsOverlay(
            outcome = outcome,
            progress = progress.value,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.88f)
                .padding(bottom = maxHeight * 0.028f)
                .aspectRatio(4f)
        )
    }
}

@Composable
private fun PlinkoBinsOverlay(
    outcome: PuppyPlinkoOutcome?,
    progress: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.puppy_plinko_bins),
            contentDescription = "Plinko prize bins",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = maxWidth * (56f / 800f),
                    end = maxWidth * (56f / 800f),
                    top = maxHeight * 0.24f,
                    bottom = maxHeight * 0.16f
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PuppyPlinkoEngine.binMultiplierHundredths.forEachIndexed { index, value ->
                val selected =
                    outcome != null &&
                        index == outcome.binIndex &&
                        progress >= 0.92f
                Text(
                    text =
                        if (value % 100 == 0) {
                            (value / 100).toString() + "×"
                        } else {
                            (value / 100.0).toString().trimEnd('0').trimEnd('.') + "×"
                        },
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (selected) 10.sp else 9.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = if (selected) 1.12f else 1f
                            scaleY = if (selected) 1.12f else 1f
                        }
                )
            }
        }
    }
}

@Composable
private fun PlinkoDropAssetButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.puppy_plinko_drop_button),
            contentDescription = label,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.42f }
                .clickable(enabled = enabled, onClick = onClick),
            contentScale = ContentScale.Fit
        )
        if (!enabled) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🐾", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Casino Chip Wallet", fontWeight = FontWeight.Black)
                Text("${state.casinoChips} Casino Chips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
                onClick = {
            PuppyCasinoRuntimeGuard.run(PuppyCasinoGame.PLINKO, "refund") {
                vm.refundCasinoRound(round.roundId)
            }
        },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refund ${round.wagerTreats} Chips") }
        }
    }
}
