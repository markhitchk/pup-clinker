package com.harleytg.puppyclicker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    var revealRoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var revealFinishedRoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNumberPicker by rememberSaveable { mutableStateOf(false) }
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
        revealRoundId = round.roundId
        revealFinishedRoundId = null

        delay(if (state.animationsEnabled) 2_600 else 250)
        revealFinishedRoundId = round.roundId

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
    val resultRevealReady =
        displayOutcome != null &&
            (revealRoundId == null || revealFinishedRoundId == revealRoundId)

    if (showNumberPicker) {
        RouletteNumberPickerDialog(
            selectedNumber = selectedNumber,
            onSelect = { number ->
                selectedNumber = number
                selectedTypeName = PuppyRouletteBetType.STRAIGHT.name
                showNumberPicker = false
            },
            onDismiss = { showNumberPicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
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

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0D2118),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RouletteWheel(
                    outcome = displayOutcome,
                    spinKey = revealRoundId,
                    animationsEnabled = state.animationsEnabled,
                    showResult = resultRevealReady
                )

                Spacer(Modifier.height(4.dp))

                RouletteColorBoard(
                    selectedType = selectedType,
                    selectedNumber = selectedNumber,
                    onSelectNumber = { number ->
                        selectedNumber = number
                        selectedTypeName = PuppyRouletteBetType.STRAIGHT.name
                    },
                    onSelectType = { type ->
                        selectedTypeName = type.name
                    },
                    onPickNumber = { showNumberPicker = true }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (resultRevealReady && displayOutcome != null) {
            RouletteResultCard(displayOutcome)
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

        Text(
            "Bet board: red, black, green zero, or choose an exact landing number.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

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

        Spacer(Modifier.height(4.dp))
    }
}

private val EuropeanRouletteWheelOrder = listOf(
    0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
    5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
)

@Composable
private fun RouletteWheel(
    outcome: PuppyRouletteOutcome?,
    spinKey: String?,
    animationsEnabled: Boolean,
    showResult: Boolean
) {
    val wheelRotation = remember { Animatable(0f) }
    val ballAngle = remember { Animatable(-90f) }
    val ballRadiusFraction = remember { Animatable(0.93f) }
    val sweep = 360f / EuropeanRouletteWheelOrder.size
    val density = LocalDensity.current
    val maximumBallOrbitPx = with(density) { 76.dp.toPx() }

    LaunchedEffect(spinKey, outcome?.winningNumber, animationsEnabled) {
        val result = outcome ?: return@LaunchedEffect
        val index = EuropeanRouletteWheelOrder.indexOf(result.winningNumber).coerceAtLeast(0)
        val pocketAngle = -90f + (index * sweep) + (sweep / 2f)

        if (!animationsEnabled || spinKey == null) {
            wheelRotation.snapTo(0f)
            ballAngle.snapTo(pocketAngle)
            ballRadiusFraction.snapTo(0.80f)
            return@LaunchedEffect
        }

        wheelRotation.snapTo(0f)
        ballAngle.snapTo(-90f)
        ballRadiusFraction.snapTo(0.93f)

        val wheelTarget = (360f * 5f) + 75f
        val ballCruiseTarget = -90f - (360f * 7f)
        val ballTarget = wheelTarget + pocketAngle - (360f * 7f)

        coroutineScope {
            launch {
                wheelRotation.animateTo(
                    wheelTarget,
                    animationSpec = tween(2_400, easing = FastOutSlowInEasing)
                )
            }
            launch {
                ballAngle.animateTo(
                    ballCruiseTarget,
                    animationSpec = tween(1_750, easing = LinearEasing)
                )
                ballAngle.animateTo(
                    ballTarget,
                    animationSpec = tween(650, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(1_650)
                ballRadiusFraction.animateTo(
                    0.80f,
                    animationSpec = tween(750, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(204.dp),
        contentAlignment = Alignment.Center
    ) {
        // Static illustrated bowl/frame. It never rotates.
        Image(
            painter = painterResource(R.drawable.puppy_roulette_wheel_base),
            contentDescription = null,
            modifier = Modifier.size(190.dp)
        )

        // Only the numbered rotor rotates.
        Image(
            painter = painterResource(R.drawable.puppy_roulette_rotor),
            contentDescription = "European roulette rotor",
            modifier = Modifier
                .size(154.dp)
                .graphicsLayer {
                    rotationZ = wheelRotation.value
                }
        )

        // Canvas is effects-only: it highlights the already-committed pocket.
        Canvas(Modifier.size(176.dp)) {
            val result = outcome
            if (showResult && result != null) {
                val index =
                    EuropeanRouletteWheelOrder.indexOf(result.winningNumber).coerceAtLeast(0)
                val pocketAngle = -90f + (index * sweep) + (sweep / 2f)
                drawArc(
                    color = Color.White.copy(alpha = 0.78f),
                    startAngle = pocketAngle + wheelRotation.value - (sweep / 2f),
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(9.dp.toPx(), 9.dp.toPx()),
                    size = Size(size.width - 18.dp.toPx(), size.height - 18.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        // The ball is a separate PNG and runs opposite the rotor before dropping inward.
        val ballRadians = ballAngle.value * PI / 180.0
        val ballOrbitPx = maximumBallOrbitPx * ballRadiusFraction.value
        Image(
            painter = painterResource(R.drawable.puppy_roulette_ball),
            contentDescription = "Roulette ball",
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    translationX = cos(ballRadians).toFloat() * ballOrbitPx
                    translationY = sin(ballRadians).toFloat() * ballOrbitPx
                }
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.60f))
        ) {
            Text(
                if (showResult) outcome?.winningNumber?.toString() ?: "—" else "SPIN",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private const val ROULETTE_TABLE_WIDTH = 1600f
private const val ROULETTE_TABLE_HEIGHT = 900f
private const val ROULETTE_GRID_X = 190f
private const val ROULETTE_GRID_Y = 245f
private const val ROULETTE_ZERO_X = 100f
private const val ROULETTE_ZERO_WIDTH = 90f
private const val ROULETTE_CELL_WIDTH = 95f
private const val ROULETTE_CELL_HEIGHT = 95f
private const val ROULETTE_OUTSIDE_Y = 570f
private const val ROULETTE_OUTSIDE_HEIGHT = 105f
private const val ROULETTE_OUTSIDE_WIDTH = 1_140f

@Composable
private fun RouletteColorBoard(
    selectedType: PuppyRouletteBetType,
    selectedNumber: Int,
    onSelectNumber: (Int) -> Unit,
    onSelectType: (PuppyRouletteBetType) -> Unit,
    onPickNumber: () -> Unit
) {
    val straightSelected = selectedType == PuppyRouletteBetType.STRAIGHT

    Column(Modifier.fillMaxWidth()) {
        Text(
            "BET BOARD",
            modifier = Modifier.fillMaxWidth(),
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(5.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ROULETTE_TABLE_WIDTH / ROULETTE_TABLE_HEIGHT)
        ) {
            Image(
                painter = painterResource(R.drawable.puppy_roulette_table),
                contentDescription = "Puppy Roulette betting table",
                modifier = Modifier.fillMaxSize()
            )

            RouletteBoardHitTarget(
                x = maxWidth * (ROULETTE_ZERO_X / ROULETTE_TABLE_WIDTH),
                y = maxHeight * (ROULETTE_GRID_Y / ROULETTE_TABLE_HEIGHT),
                width = maxWidth * (ROULETTE_ZERO_WIDTH / ROULETTE_TABLE_WIDTH),
                height = maxHeight * ((ROULETTE_CELL_HEIGHT * 3f) / ROULETTE_TABLE_HEIGHT),
                selected = straightSelected && selectedNumber == 0,
                description = "Bet on 0",
                onClick = { onSelectNumber(0) }
            )

            (1..36).forEach { number ->
                val column = (number - 1) / 3
                val row = ((column + 1) * 3) - number
                RouletteBoardHitTarget(
                    x = maxWidth *
                        ((ROULETTE_GRID_X + column * ROULETTE_CELL_WIDTH) /
                            ROULETTE_TABLE_WIDTH),
                    y = maxHeight *
                        ((ROULETTE_GRID_Y + row * ROULETTE_CELL_HEIGHT) /
                            ROULETTE_TABLE_HEIGHT),
                    width = maxWidth * (ROULETTE_CELL_WIDTH / ROULETTE_TABLE_WIDTH),
                    height = maxHeight * (ROULETTE_CELL_HEIGHT / ROULETTE_TABLE_HEIGHT),
                    selected = straightSelected && selectedNumber == number,
                    description = "Bet on " + number,
                    onClick = { onSelectNumber(number) }
                )
            }

            val outsideTypes = listOf(
                PuppyRouletteBetType.LOW,
                PuppyRouletteBetType.EVEN,
                PuppyRouletteBetType.RED,
                PuppyRouletteBetType.BLACK,
                PuppyRouletteBetType.ODD,
                PuppyRouletteBetType.HIGH
            )
            outsideTypes.forEachIndexed { index, type ->
                RouletteBoardHitTarget(
                    x = maxWidth *
                        ((ROULETTE_GRID_X + index * (ROULETTE_OUTSIDE_WIDTH / 6f)) /
                            ROULETTE_TABLE_WIDTH),
                    y = maxHeight * (ROULETTE_OUTSIDE_Y / ROULETTE_TABLE_HEIGHT),
                    width = maxWidth *
                        ((ROULETTE_OUTSIDE_WIDTH / 6f) / ROULETTE_TABLE_WIDTH),
                    height = maxHeight * (ROULETTE_OUTSIDE_HEIGHT / ROULETTE_TABLE_HEIGHT),
                    selected = selectedType == type,
                    description = "Bet on " + type.label,
                    onClick = { onSelectType(type) }
                )
            }
        }

        Spacer(Modifier.height(7.dp))
        OutlinedButton(
            onClick = onPickNumber,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
        ) {
            Text(
                if (straightSelected) {
                    "Exact number: " + selectedNumber + " · picker"
                } else {
                    "Choose exact landing number"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RouletteBoardHitTarget(
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    selected: Boolean,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .width(width)
            .height(height)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
    )
}

@Composable
private fun RouletteNumberPickerDialog(
    selectedNumber: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Choose landing number", fontWeight = FontWeight.Black)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Pick 0–36. Exact-number bets return 36× total.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                (0..36).toList().chunked(3).forEach { rowNumbers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowNumbers.forEach { number ->
                            val color = when (PuppyRouletteEngine.colorOf(number)) {
                                PuppyRouletteColor.GREEN -> RouletteGreen
                                PuppyRouletteColor.RED -> RouletteRed
                                PuppyRouletteColor.BLACK -> RouletteBlack
                            }
                            Button(
                                onClick = { onSelect(number) },
                                colors = ButtonDefaults.buttonColors(containerColor = color),
                                modifier = Modifier.weight(1f),
                                border = if (number == selectedNumber) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                }
                            ) {
                                Text(
                                    number.toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        repeat(3 - rowNumbers.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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
