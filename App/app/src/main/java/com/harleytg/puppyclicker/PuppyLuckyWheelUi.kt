package com.harleytg.puppyclicker

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.Color
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
    val spinning = revealRoundId != null && revealFinishedRoundId != revealRoundId
    val showResult = display != null && !spinning

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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF11151C),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🐶 LUCKY PUP WHEEL", color = Color.White, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                LuckyWheelBoard(
                    outcome = display,
                    spinKey = revealRoundId,
                    animationsEnabled = state.animationsEnabled
                )
                Text(
                    when {
                        spinning -> "Wheel spinning…"
                        showResult && display!!.prize == LuckyPupWheelPrize.PUPPY_UNLOCK -> {
                            val style = V6_PUPPY_STYLES.firstOrNull { it.id == display.puppyStyleId }
                            "PUP UNLOCK • ${style?.name ?: display.puppyStyleId.orEmpty()}"
                        }
                        showResult -> "${display!!.prize.label} • ${display.payoutTreats} Treats returned"
                        else -> "Choose a wager and spin."
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
            }
        }

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
private fun LuckyWheelBoard(
    outcome: LuckyPupWheelOutcome?,
    spinKey: String?,
    animationsEnabled: Boolean
) {
    val rotation = remember { Animatable(0f) }
    val prizes = LuckyPupWheelPrize.entries
    val colors = listOf(
        Color(0xFF30343B),
        Color(0xFF455A64),
        Color(0xFF00695C),
        Color(0xFF1565C0),
        Color(0xFF6A1B9A),
        Color(0xFFAD7A00),
        Color(0xFF00838F)
    )
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    LaunchedEffect(spinKey, outcome?.segmentIndex, animationsEnabled) {
        val result = outcome ?: return@LaunchedEffect
        var start = -90f
        for (index in 0 until result.segmentIndex) {
            start += prizes[index].weightPercent * 3.6f
        }
        val centerAngle = start + prizes[result.segmentIndex].weightPercent * 1.8f
        val target = 360f * 6f + (-90f - centerAngle)
        if (!animationsEnabled || spinKey == null) {
            rotation.snapTo(target)
        } else {
            rotation.snapTo(0f)
            rotation.animateTo(target, tween(2_500, easing = FastOutSlowInEasing))
        }
    }

    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(190.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.47f
            val topLeft = Offset(center.x - radius, center.y - radius)
            val wheelSize = Size(radius * 2f, radius * 2f)
            var cursor = -90f
            prizes.forEachIndexed { index, prize ->
                val sweep = prize.weightPercent * 3.6f
                val start = cursor + rotation.value
                drawArc(
                    color = colors[index],
                    startAngle = start,
                    sweepAngle = sweep + 0.25f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = wheelSize
                )
                val angle = (start + sweep / 2f) * PI / 180.0
                val labelRadius = radius * 0.68f
                val x = center.x + cos(angle).toFloat() * labelRadius
                val y = center.y + sin(angle).toFloat() * labelRadius
                drawContext.canvas.nativeCanvas.drawText(prize.emoji, x, y + 8f, paint)
                cursor += sweep
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(3f)
            )
            drawCircle(Color(0xFF11151C), radius * 0.30f, center)
        }
        Text("▼", color = Color.White, fontSize = 28.sp, modifier = Modifier.align(Alignment.TopCenter))
        Text("🐾", fontSize = 30.sp)
    }
}

@Composable
private fun WheelWallet(state: V6GameState) {
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
