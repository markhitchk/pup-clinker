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
import androidx.compose.ui.graphics.graphicsLayer
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
                showResult -> "Landed on ${display!!.multiplierLabel} • ${display.payoutTreats} Chips returned"
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
                PuppyPlinkoEngine.isValidWager(wager) && state.casinoChips >= wager,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !canPlayFeature -> "Plinko Locked"
                    activeRound != null -> "Round In Progress"
                    state.casinoChips < wager -> "Not Enough Chips"
                    else -> "Drop Ball · $wager Chips"
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
    val palette = puppyCasinoCartoonPalette()
    CartoonStageFrame(
        modifier = Modifier.fillMaxWidth().height(448.dp),
        accent = MaterialTheme.colorScheme.primary,
        background = Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                MaterialTheme.colorScheme.surface,
                palette.woodLight.copy(alpha = 0.16f)
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 10.dp, top = 18.dp, end = 10.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🐶", fontSize = 33.sp)
                CartoonBonePlaque("PUP PLINKO")
                Text("🐶", fontSize = 33.sp)
            }
            Spacer(Modifier.height(5.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().height(292.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF12345C),
                border = BorderStroke(7.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 7.dp
            ) {
                PlinkoBoard(outcome, dropKey, animationsEnabled)
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.cream,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                shadowElevation = 3.dp
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = palette.woodDark,
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
            val binCount = rows + 1
            val centerX = size.width / 2f

            /*
             * The visual geometry intentionally derives from the nine payout pockets.
             * With xStep = half a pocket, eight committed left/right decisions map
             * exactly onto outcome.binIndex without the animation choosing a result.
             */
            val pocketWidth = size.width / binCount.toFloat()
            val xStep = pocketWidth / 2f
            val launchY = 22f
            val pocketHeight = (size.height * 0.19f).coerceAtLeast(52f)
            val pocketTop = size.height - pocketHeight - 4f
            val pocketCenterY = pocketTop + pocketHeight * 0.52f
            val travelSegments = rows + 1
            val segmentGap = (pocketCenterY - launchY) / travelSegments.toFloat()

            // Bright casino-blue felt main field.
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF2685EE),
                        Color(0xFF1267CC),
                        Color(0xFF094A9E),
                        Color(0xFF073778)
                    )
                ),
                topLeft = Offset(3f, 3f),
                size = Size(size.width - 6f, size.height - 6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.16f),
                topLeft = Offset(8f, 8f),
                size = Size(size.width - 16f, size.height - 16f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f),
                style = Stroke(2f)
            )

            // Subtle felt lighting keeps the center readable while retaining the blue field.
            drawCircle(
                color = Color.White.copy(alpha = 0.045f),
                radius = size.width * 0.43f,
                center = Offset(centerX, size.height * 0.38f)
            )

            // Gold launch ring.
            drawCircle(
                color = Color.Black.copy(alpha = 0.23f),
                radius = 19f,
                center = Offset(centerX + 1.8f, launchY + 3f)
            )
            drawCircle(
                color = palette.gold,
                radius = 17f,
                center = Offset(centerX, launchY),
                style = Stroke(5f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = 13.5f,
                center = Offset(centerX, launchY),
                style = Stroke(1.6f)
            )

            // Eight rows of fixed pegs. They are visual only; the committed path drives the ball.
            for (row in 0 until rows) {
                val count = row + 1
                val y = launchY + (row + 1) * segmentGap
                val firstX = centerX - row * xStep
                val impactAt = (row + 1f) / travelSegments.toFloat()
                val pulse = PuppyCasinoCartoonMath.impactPulse(
                    progress.value,
                    impactAt,
                    0.045f
                )
                repeat(count) { col ->
                    val x = firstX + col * xStep * 2f
                    drawCircle(
                        Color.Black.copy(alpha = 0.30f),
                        radius = 6.8f + pulse * 2.1f,
                        center = Offset(x + 1.5f, y + 2.5f)
                    )
                    drawCircle(
                        Color(0xFFFFC746),
                        radius = 5.7f + pulse * 2.1f,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        Color(0xFFFFE9A6),
                        radius = 3.4f + pulse,
                        center = Offset(x - 0.8f, y - 0.8f)
                    )
                    drawCircle(
                        Color.White.copy(alpha = 0.88f),
                        radius = 1.5f,
                        center = Offset(x - 2f, y - 2f)
                    )
                }
            }

            /*
             * Nine real-looking bottom pockets are part of the main playfield.
             * The colored floors are decorative; Compose overlays the live multiplier labels.
             */
            repeat(binCount) { bin ->
                val left = bin * pocketWidth
                val winning =
                    outcome != null &&
                        bin == outcome.binIndex &&
                        progress.value >= 0.92f
                val glow = if (winning) {
                    (0.35f + (progress.value - 0.92f) * 4.5f).coerceAtMost(0.75f)
                } else {
                    0f
                }

                // Deep pocket opening.
                drawRoundRect(
                    color = Color(0xFF04172B),
                    topLeft = Offset(left + 1.5f, pocketTop),
                    size = Size(pocketWidth - 3f, pocketHeight - 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )

                // Colored pocket floor inset below the dark mouth.
                drawRoundRect(
                    color = binColors[bin].copy(alpha = 0.90f),
                    topLeft = Offset(left + 4f, pocketTop + 9f),
                    size = Size(pocketWidth - 8f, pocketHeight - 13f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )

                // Shadowed lip makes each bottom slot read as an actual pocket.
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.38f),
                    topLeft = Offset(left + 3f, pocketTop + 1f),
                    size = Size(pocketWidth - 6f, 12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = palette.gold.copy(alpha = 0.86f),
                    topLeft = Offset(left + 2f, pocketTop - 2f),
                    size = Size(pocketWidth - 4f, 4.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )

                if (winning) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = glow),
                        topLeft = Offset(left + 2.5f, pocketTop + 2f),
                        size = Size(pocketWidth - 5f, pocketHeight - 6f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                        style = Stroke(3f)
                    )
                }
            }

            // Gold dividers sit above the pocket mouths.
            repeat(binCount - 1) { divider ->
                val x = (divider + 1) * pocketWidth
                drawLine(
                    color = Color.Black.copy(alpha = 0.25f),
                    start = Offset(x + 1.5f, pocketTop - 1f),
                    end = Offset(x + 1.5f, size.height - 4f),
                    strokeWidth = 6.5f
                )
                drawLine(
                    color = palette.gold,
                    start = Offset(x, pocketTop - 2f),
                    end = Offset(x, size.height - 4f),
                    strokeWidth = 4.5f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.48f),
                    start = Offset(x - 1f, pocketTop),
                    end = Offset(x - 1f, size.height - 6f),
                    strokeWidth = 1f
                )
            }

            // Follow only the already-committed path. Animation never rolls the outcome.
            val path = outcome?.pathRight ?: emptyList()
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

            // The engine commits fresh secure-random motion jitter for every bounce.
            // It is zero at both ends of each segment, so it can never move the ball
            // into a different payout pocket.
            val jitterPermille =
                outcome?.bounceJitterPermille?.getOrNull(completedRows) ?: 0
            val segmentArc =
                kotlin.math.sin(fraction * kotlin.math.PI).toFloat()
            x += xStep * (jitterPermille / 1000f) * segmentArc

            val baseY = launchY + scaled * segmentGap
            val bounceLift =
                if (completedRows < rows) {
                    segmentArc * 3.5f
                } else {
                    0f
                }
            val y = baseY - bounceLift

            // Paw-stamped Plinko ball.
            drawCircle(
                Color.Black.copy(alpha = 0.35f),
                12.8f,
                Offset(x + 2.5f, y + 4f)
            )
            drawCircle(Color(0xFFE19127), 12f, Offset(x, y))
            drawCircle(Color(0xFFFFD45A), 10.2f, Offset(x, y))
            drawCircle(Color(0xFFFFF2B3), 7.5f, Offset(x, y))
            drawCircle(Color(0xFF7B4528), 2.6f, Offset(x, y + 2.5f))
            drawCircle(Color(0xFF7B4528), 1.45f, Offset(x - 3f, y - 1.2f))
            drawCircle(Color(0xFF7B4528), 1.45f, Offset(x, y - 2.6f))
            drawCircle(Color(0xFF7B4528), 1.45f, Offset(x + 3f, y - 1.2f))
            drawCircle(
                Color.White.copy(alpha = 0.78f),
                2f,
                Offset(x - 4f, y - 5f)
            )
        }

        // Dynamic values sit over the permanent pockets so balancing never needs new artwork.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 7.dp, end = 7.dp, bottom = 13.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PuppyPlinkoEngine.binMultiplierHundredths.forEachIndexed { index, value ->
                val selected =
                    outcome != null &&
                        index == outcome.binIndex &&
                        progress.value >= 0.92f
                Text(
                    text =
                        if (value % 100 == 0) {
                            (value / 100).toString() + "×"
                        } else {
                            (value / 100.0).toString().trimEnd('0').trimEnd('.') + "×"
                        },
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (selected) 11.sp else 10.sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = if (selected) 1.12f else 1f
                        scaleY = if (selected) 1.12f else 1f
                    }
                )
            }
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
                onClick = { vm.refundCasinoRound(round.roundId) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refund ${round.wagerTreats} Chips") }
        }
    }
}
