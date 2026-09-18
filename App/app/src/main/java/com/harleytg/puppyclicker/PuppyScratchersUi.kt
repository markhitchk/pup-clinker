package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val SCRATCH_COLUMNS = 48
private const val SCRATCH_ROWS = 20
private const val SCRATCH_CELL_COUNT = SCRATCH_COLUMNS * SCRATCH_ROWS

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
    val selectedCard = PuppyScratchersEngine.cardForWager(wager)
        ?: PuppyScratchersEngine.cardTypes.first()
    val displayCard = round?.let { PuppyScratchersEngine.cardForWager(it.wagerTreats) }
        ?: selectedCard
    val scratched = remember(round?.roundId) { mutableStateMapOf<Int, Boolean>() }
    val scratchProgress = scratched.size.toFloat() / SCRATCH_CELL_COUNT.toFloat()

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
            "Pick a scratch card, then drag the Pup Coin across the coating. The coin has weighted movement and scratches a wider trail when you move it faster.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        ScratcherWallet(state)
        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.pup_coin),
                    contentDescription = "Pup Coin",
                    modifier = Modifier.size(54.dp),
                    contentScale = ContentScale.Fit
                )
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Pup Coin", fontWeight = FontWeight.Black)
                    Text(
                        "Weighted drag • speed-sensitive scratch radius • physical coin rotation",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ScratcherCartoonStage(
            card = displayCard,
            outcome = display,
            scratched = scratched,
            enabled = round?.state == PuppyCasinoRoundState.OUTCOME_COMMITTED && !revealed,
            revealAll = revealed,
            status = when {
                display == null -> "Choose a card below to begin."
                revealed -> "${display.prize.label} • ${display.payoutTreats} Treats returned"
                round?.state == PuppyCasinoRoundState.OUTCOME_COMMITTED ->
                    "Scratch with the Pup Coin • ${(scratchProgress * 100).toInt()}%"
                else -> "Scratch to reveal."
            }
        )

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
        Text("Scratch cards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            "Every card keeps the published 91% Treat RTP, but each has a different prize distribution.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        PuppyScratchersEngine.cardTypes.chunked(2).forEach { rowCards ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCards.forEach { card ->
                    FilterChip(
                        selected = wager == card.costTreats,
                        onClick = {
                            if (activeRound == null) wager = card.costTreats
                        },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${card.emoji} ${card.shortName}", fontWeight = FontWeight.Bold)
                                Text("${card.costTreats} Treats", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowCards.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(4.dp))
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
                    else -> "Buy ${selectedCard.name} · $wager Treats"
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
                Text("${displayCard.emoji} ${displayCard.name} odds", fontWeight = FontWeight.Black)
                Text(
                    "Expected Treat return: ${PuppyScratchersEngine.PUBLISHED_RTP_PERCENT}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                val odds = PuppyScratchersEngine.oddsFor(displayCard.costTreats)
                PuppyScratcherPrize.entries.forEach { prize ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${prize.emoji} ${prize.label}", Modifier.weight(1f))
                        Text("${odds[prize] ?: 0}%", fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("• The outcome is committed before scratching.", style = MaterialTheme.typography.bodySmall)
                Text("• 68% of the coating must be removed before automatic reveal.", style = MaterialTheme.typography.bodySmall)
                Text("• Faster Pup Coin movement creates a slightly wider scratch trail.", style = MaterialTheme.typography.bodySmall)
                Text("• All costs and payouts continue to use Treats.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ScratcherCartoonStage(
    card: PuppyScratcherCardType,
    outcome: PuppyScratcherOutcome?,
    scratched: MutableMap<Int, Boolean>,
    enabled: Boolean,
    revealAll: Boolean,
    status: String
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(941f / 728f)
    ) {
        ScratcherCard(
            card = card,
            outcome = outcome,
            scratched = scratched,
            enabled = enabled,
            revealAll = revealAll,
            modifier = Modifier
                .offset(
                    x = maxWidth * (125f / 941f),
                    y = maxHeight * (309f / 728f)
                )
                .size(
                    width = maxWidth * (692f / 941f),
                    height = maxHeight * (331f / 728f)
                )
        )

        Image(
            painter = painterResource(R.drawable.puppy_scratch_stage),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Text(
            status,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * 0.035f),
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScratcherCard(
    card: PuppyScratcherCardType,
    outcome: PuppyScratcherOutcome?,
    scratched: MutableMap<Int, Boolean>,
    enabled: Boolean,
    revealAll: Boolean,
    modifier: Modifier = Modifier
) {
    var coinPosition by remember(outcome) { mutableStateOf<Offset?>(null) }
    var coinRotation by remember(outcome) { mutableStateOf(0f) }
    var lastPointerTimeMillis by remember(outcome) { mutableLongStateOf(0L) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val coinDiameter = 56.dp
    val coinRadiusPx = with(density) { coinDiameter.toPx() / 2f }

    Box(
        modifier = modifier
            .onSizeChanged { cardSize = it },
        contentAlignment = Alignment.TopStart
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(15.dp),
            color = scratcherCardBackground(card),
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.65f)),
            shadowElevation = 3.dp
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.45f), Color.Transparent, Color(0xFFFFD965).copy(alpha = 0.10f))
                        )
                    )
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val symbols = outcome?.symbols ?: listOf("?", "?", "?")
                symbols.forEach { symbol ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.70f),
                        border = BorderStroke(1.dp, Color(0xFFD7B557).copy(alpha = 0.55f)),
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(symbol, style = MaterialTheme.typography.displaySmall)
                            Text("PRIZE", color = Color(0xFF2F3A36), fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        if (!revealAll) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, outcome) {
                        if (!enabled || outcome == null) return@pointerInput

                        fun clampCoin(position: Offset): Offset {
                            val minX = coinRadiusPx
                            val minY = coinRadiusPx
                            val maxX = (size.width.toFloat() - coinRadiusPx).coerceAtLeast(minX)
                            val maxY = (size.height.toFloat() - coinRadiusPx).coerceAtLeast(minY)
                            return Offset(
                                x = position.x.coerceIn(minX, maxX),
                                y = position.y.coerceIn(minY, maxY)
                            )
                        }

                        fun scratchRadius(speedPxPerMs: Float): Float {
                            val cellW = size.width.toFloat() / SCRATCH_COLUMNS.toFloat()
                            val cellH = size.height.toFloat() / SCRATCH_ROWS.toFloat()
                            val speedBoost = (speedPxPerMs / 2.4f).coerceIn(0f, 1f)
                            return maxOf(
                                minOf(cellW, cellH) * 1.45f,
                                coinRadiusPx * (0.34f + (speedBoost * 0.10f))
                            )
                        }

                        fun markPoint(position: Offset, speedPxPerMs: Float) {
                            val cellW = size.width.toFloat() / SCRATCH_COLUMNS.toFloat()
                            val cellH = size.height.toFloat() / SCRATCH_ROWS.toFloat()
                            val radius = scratchRadius(speedPxPerMs)
                            val radiusSquared = radius * radius

                            for (row in 0 until SCRATCH_ROWS) {
                                for (col in 0 until SCRATCH_COLUMNS) {
                                    val centerX = (col + 0.5f) * cellW
                                    val centerY = (row + 0.5f) * cellH
                                    val dx = centerX - position.x
                                    val dy = centerY - position.y
                                    if ((dx * dx) + (dy * dy) <= radiusSquared) {
                                        val index = row * SCRATCH_COLUMNS + col
                                        if (index !in scratched) scratched[index] = true
                                    }
                                }
                            }
                        }

                        fun markSegment(from: Offset, to: Offset, speedPxPerMs: Float) {
                            val dx = to.x - from.x
                            val dy = to.y - from.y
                            val distance = sqrt((dx * dx) + (dy * dy))
                            val spacing = maxOf(4f, scratchRadius(speedPxPerMs) * 0.42f)
                            val steps = ceil(distance / spacing).toInt().coerceAtLeast(1)

                            for (step in 1..steps) {
                                val t = step.toFloat() / steps.toFloat()
                                markPoint(
                                    Offset(
                                        x = from.x + (dx * t),
                                        y = from.y + (dy * t)
                                    ),
                                    speedPxPerMs
                                )
                            }
                        }

                        detectDragGestures(
                            onDragStart = { start ->
                                val clampedStart = clampCoin(start)
                                coinPosition = clampedStart
                                coinRotation = 0f
                                lastPointerTimeMillis = 0L
                                markPoint(clampedStart, 0f)
                            },
                            onDragEnd = {
                                coinRotation *= 0.28f
                                lastPointerTimeMillis = 0L
                            },
                            onDragCancel = {
                                coinRotation *= 0.28f
                                lastPointerTimeMillis = 0L
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()

                                val target = clampCoin(change.position)
                                val current = coinPosition ?: target
                                val dtMillis = if (lastPointerTimeMillis == 0L) {
                                    16L
                                } else {
                                    (change.uptimeMillis - lastPointerTimeMillis).coerceIn(1L, 50L)
                                }
                                lastPointerTimeMillis = change.uptimeMillis

                                val pointerDistance = sqrt(
                                    (dragAmount.x * dragAmount.x) +
                                        (dragAmount.y * dragAmount.y)
                                )
                                val speedPxPerMs = pointerDistance / dtMillis.toFloat()
                                val follow = (0.68f + (speedPxPerMs / 12f))
                                    .coerceIn(0.68f, 0.88f)
                                val next = clampCoin(
                                    Offset(
                                        x = current.x + ((target.x - current.x) * follow),
                                        y = current.y + ((target.y - current.y) * follow)
                                    )
                                )

                                coinPosition = next
                                coinRotation = (
                                    (coinRotation * 0.82f) +
                                        (dragAmount.x * 0.10f) +
                                        (dragAmount.y * 0.02f)
                                    ).coerceIn(-28f, 28f)

                                markSegment(current, next, speedPxPerMs)
                            }
                        )
                    }
            ) {
                val cellW = size.width / SCRATCH_COLUMNS
                val cellH = size.height / SCRATCH_ROWS
                val coating = scratcherCoatingColor(card)

                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.55f),
                            coating,
                            coating.copy(alpha = 0.94f),
                            Color.Black.copy(alpha = 0.10f)
                        )
                    )
                )

                for (row in 0 until SCRATCH_ROWS) {
                    for (col in 0 until SCRATCH_COLUMNS) {
                        val index = row * SCRATCH_COLUMNS + col
                        if (index !in scratched) {
                            drawRect(
                                color = coating.copy(alpha = 0.92f),
                                topLeft = Offset(col * cellW, row * cellH),
                                size = Size(cellW + 1.2f, cellH + 1.2f)
                            )
                            if (index % 9 == 0) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.26f),
                                    radius = minOf(cellW, cellH) * 0.18f,
                                    center = Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH)
                                )
                            }
                            if (index % 17 == 0) {
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.06f),
                                    radius = minOf(cellW, cellH) * 0.12f,
                                    center = Offset((col + 0.72f) * cellW, (row + 0.36f) * cellH)
                                )
                            }
                        }
                    }
                }

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.38f),
                    topLeft = Offset(5f, 5f),
                    size = Size(size.width - 10f, size.height - 10f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(17f, 17f),
                    style = Stroke(2.4f)
                )
            }
        }

        val restingPosition = if (enabled && cardSize.width > 0 && cardSize.height > 0) {
            Offset(cardSize.width * 0.5f, cardSize.height * 0.82f)
        } else {
            null
        }
        val visibleCoinPosition = coinPosition ?: restingPosition
        if (!revealAll && visibleCoinPosition != null) {
            PupCoin(
                position = visibleCoinPosition,
                radiusPx = coinRadiusPx,
                diameter = coinDiameter,
                rotation = coinRotation
            )
        }
    }
}

@Composable
private fun PupCoin(
    position: Offset,
    radiusPx: Float,
    diameter: androidx.compose.ui.unit.Dp,
    rotation: Float
) {
    Image(
        painter = painterResource(R.drawable.pup_coin),
        contentDescription = "Pup Coin",
        modifier = Modifier
            .offset {
                IntOffset(
                    (position.x - radiusPx).roundToInt(),
                    (position.y - radiusPx).roundToInt()
                )
            }
            .size(diameter)
            .graphicsLayer {
                rotationZ = rotation
                shadowElevation = 12f
            },
        contentScale = ContentScale.Fit
    )
}

private fun scratcherAccentColor(card: PuppyScratcherCardType): Color = when (card.id) {
    "bone_bonanza" -> Color(0xFFD8A96A)
    "lucky_fetch" -> Color(0xFF55DFA8)
    "golden_paw" -> Color(0xFFFFD35A)
    "casino_scratch" -> Color(0xFFE45C5C)
    "ultra_pup" -> Color(0xFF6DDCFF)
    else -> Color(0xFFD8C477)
}

private fun scratcherShellColor(card: PuppyScratcherCardType): Color = when (card.id) {
    "bone_bonanza" -> Color(0xFF473019)
    "lucky_fetch" -> Color(0xFF124438)
    "golden_paw" -> Color(0xFF58480E)
    "casino_scratch" -> Color(0xFF4C1818)
    "ultra_pup" -> Color(0xFF102E45)
    else -> Color(0xFF163B35)
}

private fun scratcherCardBackground(card: PuppyScratcherCardType): Color = when (card.id) {
    "casino_scratch" -> Color(0xFFFFECEC)
    "ultra_pup" -> Color(0xFFE9F8FF)
    "golden_paw" -> Color(0xFFFFF7D6)
    else -> Color(0xFFFFF8E7)
}

private fun scratcherCoatingColor(card: PuppyScratcherCardType): Color = when (card.id) {
    "bone_bonanza" -> Color(0xFFB8B0A5)
    "lucky_fetch" -> Color(0xFFB4CFC5)
    "golden_paw" -> Color(0xFFD6C778)
    "casino_scratch" -> Color(0xFFB7B7BC)
    "ultra_pup" -> Color(0xFF9DC9D9)
    else -> Color(0xFFC7CCD1)
}

@Composable
private fun ScratcherWallet(state: V6GameState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🍪", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Treat Wallet", fontWeight = FontWeight.Black)
                Text("${state.treats} Treats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            Text(
                PuppyScratchersEngine.PUBLISHED_RTP_PERCENT + " RTP",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
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
