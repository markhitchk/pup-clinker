package com.harleytg.puppyclicker

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
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
            "Spin for Treat multipliers or a puppy unlock",
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
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(14.dp)
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
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(19.dp)
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
    PuppyCasinoRenderStage(
        title = "LUCKY PUP WHEEL",
        height = 330.dp,
        accent = Color(0xFF7C4DFF)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 38.dp, bottom = 22.dp)
        ) {
            LuckyWheelBoard(
                outcome = outcome,
                spinKey = spinKey,
                animationsEnabled = animationsEnabled
            )
            PuppyCasinoRenderStatusBar(
                text = status,
                centered = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun LuckyWheelBoard(
    outcome: LuckyPupWheelOutcome?,
    spinKey: String?,
    animationsEnabled: Boolean
) {
    val rotation = remember { Animatable(0f) }
    val prizes = LuckyPupWheelPrize.entries
    val colors = listOf(
        Color(0xFF34A9ED),
        Color(0xFF788592),
        Color(0xFF14B985),
        Color(0xFF18A1E7),
        Color(0xFF8B45E6),
        Color(0xFFFFC239),
        Color(0xFF24B8D5)
    )
    val palette = puppyCasinoCartoonPalette()
    val primary = MaterialTheme.colorScheme.primary
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    LaunchedEffect(spinKey, outcome?.segmentIndex, animationsEnabled) {
        val result = outcome ?: return@LaunchedEffect
        val weights = prizes.map { it.weightPercent.toFloat() }
        val centerAngle = PuppyCasinoCartoonMath.segmentCenterDegrees(weights, result.segmentIndex)
        val target = 360f * 6f + (-90f - centerAngle)
        if (!animationsEnabled || spinKey == null) {
            rotation.snapTo(target)
        } else {
            rotation.snapTo(0f)
            rotation.animateTo(target, tween(2_500, easing = FastOutSlowInEasing))
        }
    }

    Box(Modifier.fillMaxWidth().height(218.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.size(190.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f + 4f)
            val outerRadius = size.minDimension * 0.49f
            val rimRadius = outerRadius * 0.92f
            val topLeft = Offset(center.x - rimRadius, center.y - rimRadius)
            val wheelSize = Size(rimRadius * 2f, rimRadius * 2f)

            drawCircle(
                color = palette.shadow.copy(alpha = 0.42f),
                radius = outerRadius,
                center = Offset(center.x + 3f, center.y + 7f)
            )
            drawCircle(palette.woodLight, outerRadius, center)
            drawCircle(primary, outerRadius * 0.93f, center)
            drawCircle(Color.White.copy(alpha = 0.20f), outerRadius * 0.87f, center, style = Stroke(4f))

            var cursor = -90f
            prizes.forEachIndexed { index, prize ->
                val sweep = prize.weightPercent * 3.6f
                val start = cursor + rotation.value
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = start,
                    sweepAngle = sweep + 0.35f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = wheelSize
                )
                drawArc(
                    color = Color.White.copy(alpha = 0.12f),
                    startAngle = start,
                    sweepAngle = sweep + 0.35f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = Size(wheelSize.width, wheelSize.height * 0.88f)
                )
                val angle = (start + sweep / 2f) * PI / 180.0
                val labelRadius = rimRadius * 0.68f
                val x = center.x + cos(angle).toFloat() * labelRadius
                val y = center.y + sin(angle).toFloat() * labelRadius
                drawContext.canvas.nativeCanvas.drawText(prize.emoji, x, y + 9f, paint)
                cursor += sweep
            }

            repeat(16) { index ->
                val angle = index * (2.0 * PI / 16.0)
                val studRadius = outerRadius * 0.86f
                val x = center.x + cos(angle).toFloat() * studRadius
                val y = center.y + sin(angle).toFloat() * studRadius
                drawCircle(palette.goldDeep.copy(alpha = 0.35f), 6f, Offset(x + 1.5f, y + 2f))
                drawCircle(palette.gold, 5f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.75f), 1.7f, Offset(x - 1.5f, y - 1.5f))
            }

            drawCircle(palette.shadow.copy(alpha = 0.28f), rimRadius * 0.31f, Offset(center.x + 2f, center.y + 4f))
            drawCircle(primary, rimRadius * 0.30f, center)
            drawCircle(Color.White.copy(alpha = 0.26f), rimRadius * 0.26f, center, style = Stroke(3f))
        }

        Text(
            "▼",
            color = Color(0xFFE74D56),
            fontSize = 25.sp,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        Text("🐾", fontSize = 22.sp, modifier = Modifier.padding(top = 84.dp))
    }
}

@Composable
private fun WheelWallet(state: V6GameState) {
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
                Box(contentAlignment = Alignment.Center) {
                    Text("🍪", fontSize = 22.sp)
                }
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
                "2% PUP",
                color = Color(0xFF00B8F0),
                fontWeight = FontWeight.Black
            )
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
