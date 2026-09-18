package com.harleytg.puppyclicker

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun PuppyLuckyWheelScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onBack: () -> Unit
) {
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val activeRound by vm.casinoRound.collectAsStateWithLifecycle()
    val recoveryIssue by vm.casinoRecoveryIssue.collectAsStateWithLifecycle()
    var wager by rememberSaveable { mutableLongStateOf(PuppyLuckyWheelEngine.wagerPresets.first()) }
    var lastOutcome by remember { mutableStateOf<LuckyPupWheelOutcome?>(null) }
    var revealRoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var revealFinishedRoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val canPlayFeature =
        recoveryIssue == null &&
            PuppyCasinoFeaturePolicy.canStartNewRound(PuppyCasinoGame.LUCKY_WHEEL, flags)
    val round = activeRound?.takeIf { it.game == PuppyCasinoGame.LUCKY_WHEEL }
    val recovered = remember(round?.outcomePayload, round?.wagerTreats) {
        round?.takeIf { it.state == PuppyCasinoRoundState.OUTCOME_COMMITTED }
            ?.let { LuckyPupWheelOutcomeCodec.decodeAndValidate(it.outcomePayload, it.wagerTreats) }
    }

    LaunchedEffect(round?.roundId, round?.state, round?.outcomePayload) {
        val savedRound = round ?: return@LaunchedEffect
        if (savedRound.state != PuppyCasinoRoundState.OUTCOME_COMMITTED) return@LaunchedEffect
        val outcome = LuckyPupWheelOutcomeCodec.decodeAndValidate(savedRound.outcomePayload, savedRound.wagerTreats)
        if (outcome == null || outcome.payoutTreats != savedRound.payoutTreats) {
            message = "Saved Lucky Pup Wheel outcome failed validation. Settlement was blocked."
            return@LaunchedEffect
        }
        lastOutcome = outcome
        revealRoundId = savedRound.roundId
        revealFinishedRoundId = null
        delay(if (state.animationsEnabled) 2_650 else 250)
        revealFinishedRoundId = savedRound.roundId
        val settled = vm.settleCasinoRound(savedRound.roundId)
        if (!settled.success) {
            message = "Unable to settle the saved wheel spin: " +
                (settled.failure?.name ?: "unknown error")
        }
    }

    val display = recovered ?: lastOutcome
    val showResult =
        display != null &&
            revealRoundId != null &&
            revealFinishedRoundId == revealRoundId
    val spinning =
        display != null &&
            !showResult &&
            round?.state == PuppyCasinoRoundState.OUTCOME_COMMITTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Puppy Casino", fontWeight = FontWeight.Bold) }
        Text("Lucky Pup Wheel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "Spin for Treat multipliers or a real eligible puppy unlock from the existing roster.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        WheelWallet(state)
        Spacer(Modifier.height(12.dp))

        LuckyWheelCartoonStage(
            outcome = display,
            spinKey = round?.roundId ?: revealRoundId,
            animationsEnabled = state.animationsEnabled,
            status = when {
                spinning -> "Wheel spinning…"
                showResult && display!!.prize == LuckyPupWheelPrize.PUPPY_UNLOCK -> {
                    val style = V6_PUPPY_STYLES.firstOrNull { it.id == display.puppyStyleId }
                    "PUP UNLOCK • ${style?.name ?: display.puppyStyleId.orEmpty()}"
                }
                showResult -> "${display!!.prize.label} • ${display.payoutTreats} Treats returned"
                else -> "Choose a wager and spin."
            }
        )

        if (round?.state == PuppyCasinoRoundState.WAGER_ACCEPTED) {
            Spacer(Modifier.height(10.dp))
            WheelInterruptedCard(round, vm)
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
            PuppyLuckyWheelEngine.wagerPresets.forEach { preset ->
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
                val result = vm.startLuckyPupWheel(wager)
                if (!result.success) {
                    message = "Wheel spin blocked: " +
                        (result.transactionFailure?.name ?: result.failure?.name ?: "unknown error")
                }
            },
            enabled = canPlayFeature && activeRound == null &&
                PuppyLuckyWheelEngine.isValidWager(wager) && state.treats >= wager,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !canPlayFeature -> "Wheel Locked"
                    activeRound != null -> "Round In Progress"
                    state.treats < wager -> "Not Enough Treats"
                    else -> "Spin Wheel · $wager Treats"
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
                Text("Published wheel", fontWeight = FontWeight.Black)
                Text(
                    "Treat-only expected return: ${PuppyLuckyWheelEngine.PUBLISHED_TREAT_RTP_PERCENT}. Puppy unlock value is separate.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                LuckyPupWheelPrize.entries.forEach { prize ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${prize.emoji} ${prize.label}", Modifier.weight(1f))
                        Text("${prize.weightPercent}%", fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("• Pup Unlock uses the current casino-eligible roster pool only.", style = MaterialTheme.typography.bodySmall)
                Text("• Already-owned pups are excluded before the spin.", style = MaterialTheme.typography.bodySmall)
                Text("• The existing one-casino-puppy-per-day cap still applies.", style = MaterialTheme.typography.bodySmall)
                Text("• If the pup pool/cap cannot award a pup, that slice refunds the wager instead.", style = MaterialTheme.typography.bodySmall)
                Text("• The selected segment and puppy ID are committed before animation.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun LuckyWheelCartoonStage(
    outcome: LuckyPupWheelOutcome?,
    spinKey: String?,
    animationsEnabled: Boolean,
    status: String
) {
    val palette = puppyCasinoCartoonPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, palette.gold.copy(alpha = 0.70f)),
        shadowElevation = 7.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "LUCKY PUP WHEEL",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = palette.woodDark
            )
            Text(
                "Prize committed first · wheel animation is visual only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            LuckyWheelBoard(
                outcome = outcome,
                spinKey = spinKey,
                animationsEnabled = animationsEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(15.dp),
                color = palette.cream,
                border = BorderStroke(2.dp, palette.woodLight.copy(alpha = 0.70f)),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = status,
                        color = palette.woodDark,
                        fontWeight = FontWeight.Black
                    )
                    if (outcome?.prize == LuckyPupWheelPrize.PUPPY_UNLOCK) {
                        Text(
                            "PUP UNLOCK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LuckyWheelBoard(
    outcome: LuckyPupWheelOutcome?,
    spinKey: String?,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    val pointerBounce = remember { Animatable(0f) }
    val prizes = LuckyPupWheelPrize.entries
    val weights = remember { prizes.map { it.weightPercent.toFloat() } }

    LaunchedEffect(spinKey, outcome?.segmentIndex, animationsEnabled) {
        val result = outcome
        if (result == null) {
            rotation.snapTo(0f)
            pointerBounce.snapTo(0f)
            return@LaunchedEffect
        }

        val centerAngle = PuppyCasinoCartoonMath.segmentCenterDegrees(
            weights = weights,
            index = result.segmentIndex
        )
        val target = 360f * 6f + (-90f - centerAngle)

        if (!animationsEnabled || spinKey == null) {
            rotation.snapTo(target)
            pointerBounce.snapTo(0f)
        } else {
            rotation.snapTo(0f)
            pointerBounce.snapTo(0f)

            launch {
                repeat(18) { click ->
                    val bounce = 13f - (click * 0.35f).coerceAtMost(6f)
                    pointerBounce.animateTo(-bounce, tween(30))
                    pointerBounce.animateTo(4f, tween(38))
                    pointerBounce.animateTo(0f, tween(32))
                    delay(25L + click * 2L)
                }
            }

            rotation.animateTo(
                target,
                animationSpec = tween(2_500, easing = FastOutSlowInEasing)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier.aspectRatio(420f / 447f)
    ) {
        val wheelSize = maxWidth * 0.82f
        val wheelX = (maxWidth - wheelSize) * 0.5f
        val wheelY = maxHeight * 0.075f

        Box(
            modifier = Modifier
                .offset(x = wheelX, y = wheelY)
                .size(wheelSize)
                .graphicsLayer {
                    rotationZ = rotation.value
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.puppy_lucky_wheel_disc),
                contentDescription = "Lucky Pup Wheel prize disc",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            prizes.forEachIndexed { index, prize ->
                val angleDegrees = PuppyCasinoCartoonMath.segmentCenterDegrees(weights, index)
                val angleRadians = angleDegrees * PI / 180.0
                val labelRadius = wheelSize * 0.29f
                val labelWidth = wheelSize * 0.19f
                val x = wheelSize * 0.5f +
                    labelRadius * cos(angleRadians).toFloat() -
                    labelWidth * 0.5f
                val y = wheelSize * 0.5f +
                    labelRadius * sin(angleRadians).toFloat() -
                    10.dp

                Text(
                    text = luckyWheelSegmentLabel(prize),
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .width(labelWidth),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.puppy_lucky_wheel_frame),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Image(
            painter = painterResource(R.drawable.puppy_lucky_wheel_pointer),
            contentDescription = "Lucky Pup Wheel pointer",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.018f)
                .width(maxWidth * 0.12f)
                .aspectRatio(100f / 160f)
                .graphicsLayer {
                    rotationZ = pointerBounce.value
                    transformOrigin = TransformOrigin(0.5f, 0.12f)
                },
            contentScale = ContentScale.FillBounds
        )
    }
}

private fun luckyWheelSegmentLabel(prize: LuckyPupWheelPrize): String = when (prize) {
    LuckyPupWheelPrize.MISS -> "MISS"
    LuckyPupWheelPrize.HALF -> "0.5×"
    LuckyPupWheelPrize.REFUND -> "1×"
    LuckyPupWheelPrize.DOUBLE -> "2×"
    LuckyPupWheelPrize.FIVE_X -> "5×"
    LuckyPupWheelPrize.TEN_X -> "10×"
    LuckyPupWheelPrize.PUPPY_UNLOCK -> "PUP"
}

@Composable
private fun WheelWallet(state: V6GameState) {
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
            Text("2% PUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WheelInterruptedCard(round: PuppyCasinoRound, vm: PuppyClickerV6ViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Interrupted spin", fontWeight = FontWeight.Black)
            Text("The wager was accepted before a wheel outcome was committed.", style = MaterialTheme.typography.bodySmall)
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
