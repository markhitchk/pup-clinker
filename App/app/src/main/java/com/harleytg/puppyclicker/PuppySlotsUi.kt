package com.harleytg.puppyclicker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun PuppySlotsScreen(
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
                game = PuppyCasinoGame.SLOTS,
                flags = flags
            )

    var wager by rememberSaveable { mutableLongStateOf(100L) }
    var lastOutcomePayload by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOutcomeWager by rememberSaveable { mutableLongStateOf(0L) }
    var revealRoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var revealFinishedRoundId by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var leverPullToken by rememberSaveable { mutableLongStateOf(0L) }

    val slotsRound = activeRound?.takeIf { it.game == PuppyCasinoGame.SLOTS }
    val recoveredOutcome = remember(slotsRound?.outcomePayload, slotsRound?.wagerTreats) {
        slotsRound
            ?.takeIf { it.state == PuppyCasinoRoundState.OUTCOME_COMMITTED }
            ?.let { round ->
                PuppySlotsOutcomeCodec.decodeAndValidate(
                    raw = round.outcomePayload,
                    wagerTreats = round.wagerTreats
                )
            }
    }

    LaunchedEffect(
        slotsRound?.roundId,
        slotsRound?.state,
        slotsRound?.outcomePayload
    ) {
        val round = slotsRound ?: return@LaunchedEffect
        if (round.state != PuppyCasinoRoundState.OUTCOME_COMMITTED) return@LaunchedEffect

        val outcome = PuppySlotsOutcomeCodec.decodeAndValidate(
            raw = round.outcomePayload,
            wagerTreats = round.wagerTreats
        )
        if (outcome == null || outcome.payoutTreats != round.payoutTreats) {
            message = "Saved Slots outcome failed validation. Settlement was blocked."
            return@LaunchedEffect
        }

        lastOutcomePayload = round.outcomePayload
        lastOutcomeWager = round.wagerTreats
        revealRoundId = round.roundId
        revealFinishedRoundId = null
        delay(if (state.animationsEnabled) 2_350 else 250)
        revealFinishedRoundId = round.roundId

        val settled = vm.settleCasinoRound(round.roundId)
        if (!settled.success) {
            message = "Unable to settle the saved Slots round: " +
                (settled.failure?.name ?: "unknown error")
        }
    }

    val lastOutcome = remember(lastOutcomePayload, lastOutcomeWager) {
        PuppySlotsOutcomeCodec.decodeAndValidate(
            raw = lastOutcomePayload,
            wagerTreats = lastOutcomeWager
        )
    }

    val startSlotsRound: () -> Unit = {
        message = null
        val result = vm.startSlotsSpin(wager)
        if (result.success) {
            leverPullToken += 1L
        } else {
            message = when (result.failure) {
                PuppySlotsStartFailure.INVALID_WAGER -> "Invalid Slots wager."
                PuppySlotsStartFailure.TRANSACTION_REJECTED ->
                    "Spin blocked: " + (result.transactionFailure?.name ?: "transaction rejected")
                PuppySlotsStartFailure.OUTCOME_GENERATION_FAILED ->
                    "Outcome generation failed. The accepted wager was refunded."
                PuppySlotsStartFailure.OUTCOME_COMMIT_FAILED ->
                    "Outcome could not be committed. Use the interrupted-wager recovery control."
                null -> "Spin could not start."
            }
        }
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
            "Puppy Slots",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "Three weighted reels. The outcome is saved before it is revealed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🐾", fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Casino Chip Wallet", fontWeight = FontWeight.Black)
                    Text(
                        state.casinoChips.toString() + " Casino Chips",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    PuppySlotsEngine.PUBLISHED_RTP_PERCENT + " RTP",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val display = recoveredOutcome ?: lastOutcome
        val resultRevealReady =
            display != null &&
                (revealRoundId == null || revealFinishedRoundId == revealRoundId)
        val reelsSpinning =
            display != null &&
                revealRoundId != null &&
                revealFinishedRoundId != revealRoundId

        SlotsMachineCard(
            display = display,
            resultRevealReady = resultRevealReady,
            reelsSpinning = reelsSpinning,
            spinKey = revealRoundId,
            roundState = slotsRound?.state,
            animationsEnabled = state.animationsEnabled,
            leverEnabled =
                canPlayFeature &&
                    activeRound == null &&
                    PuppySlotsEngine.isValidWager(wager) &&
                    state.casinoChips >= wager,
            leverPullToken = leverPullToken,
            onLeverPull = startSlotsRound
        )

        if (slotsRound?.state == PuppyCasinoRoundState.WAGER_ACCEPTED) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Interrupted wager", fontWeight = FontWeight.Black)
                    Text(
                        "This wager was accepted before an outcome was committed. It can be refunded safely.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.refundCasinoRound(slotsRound.roundId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refund " + slotsRound.wagerTreats + " Casino Chips")
                    }
                }
            }
        }

        message?.let { text ->
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Wager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            "Multipliers are the total Casino Chip return, including the original wager.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PuppySlotsEngine.wagerPresets.forEach { preset ->
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
            onClick = startSlotsRound,
            enabled =
                canPlayFeature &&
                    activeRound == null &&
                    PuppySlotsEngine.isValidWager(wager) &&
                    state.casinoChips >= wager,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !canPlayFeature -> "Slots Locked"
                    activeRound != null -> "Round In Progress"
                    state.casinoChips < wager -> "Not Enough Casino Chips"
                    else -> "Pull Lever · " + wager + " Casino Chips"
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        SlotsRulesCard()

        Spacer(Modifier.height(16.dp))
        SlotsPayoutTable()

        Spacer(Modifier.height(16.dp))
        SlotsWeightTable()

        Spacer(Modifier.height(4.dp))
    }
}

private const val SLOTS_MACHINE_VISIBLE_WIDTH = 440f
private const val SLOTS_MACHINE_VISIBLE_HEIGHT = 483f
private const val SLOTS_REEL_TOP = 180f / SLOTS_MACHINE_VISIBLE_HEIGHT
private const val SLOTS_REEL_HEIGHT = 182f / SLOTS_MACHINE_VISIBLE_HEIGHT
private val SLOTS_REEL_LEFTS = listOf(
    57f / SLOTS_MACHINE_VISIBLE_WIDTH,
    170f / SLOTS_MACHINE_VISIBLE_WIDTH,
    285f / SLOTS_MACHINE_VISIBLE_WIDTH
)
private val SLOTS_REEL_WIDTHS = listOf(
    97f / SLOTS_MACHINE_VISIBLE_WIDTH,
    98f / SLOTS_MACHINE_VISIBLE_WIDTH,
    98f / SLOTS_MACHINE_VISIBLE_WIDTH
)

@Composable
private fun SlotsMachineCard(
    display: PuppySlotsOutcome?,
    resultRevealReady: Boolean,
    reelsSpinning: Boolean,
    spinKey: String?,
    roundState: PuppyCasinoRoundState?,
    animationsEnabled: Boolean,
    leverEnabled: Boolean,
    leverPullToken: Long,
    onLeverPull: () -> Unit
) {
    val idleMotion = rememberInfiniteTransition(label = "slots_mascot_idle")
    val idleBob by idleMotion.animateFloat(
        initialValue = -2f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slots_mascot_bob"
    )
    val idleSway by idleMotion.animateFloat(
        initialValue = -1.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slots_mascot_sway"
    )
    val lightPulse by idleMotion.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slots_light_pulse"
    )
    val winReady =
        resultRevealReady &&
            display != null &&
            display.winKind != PuppySlotsWinKind.LOSS
    val mascotReaction by animateFloatAsState(
        targetValue = when {
            !animationsEnabled -> 0f
            winReady -> -8f
            reelsSpinning -> -4f
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "slots_mascot_reaction"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF17191F),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                val machineWidth = maxWidth * 0.82f
                val machineHeight =
                    machineWidth * (SLOTS_MACHINE_VISIBLE_HEIGHT / SLOTS_MACHINE_VISIBLE_WIDTH)
                val machineLeft = maxWidth * 0.05f
                val machineTop = maxHeight * 0.10f

                val mascotWidth = machineWidth * 0.48f
                val mascotHeight = mascotWidth * (131f / 180f)
                val mascotLeft = machineLeft + machineWidth * 0.26f
                val mascotTop = machineTop - machineHeight * 0.07f

                // Mascot sits behind the cabinet marquee so the artwork remains layered.
                Image(
                    painter = painterResource(R.drawable.puppy_slots_mascot),
                    contentDescription = "Puppy Slots mascot",
                    modifier = Modifier
                        .offset(x = mascotLeft, y = mascotTop)
                        .width(mascotWidth)
                        .height(mascotHeight)
                        .graphicsLayer {
                            if (animationsEnabled) {
                                translationY = idleBob + mascotReaction
                                rotationZ = idleSway * if (reelsSpinning || winReady) 1.7f else 1f
                                val reactionScale = if (winReady) 1.05f else 1f
                                scaleX = reactionScale
                                scaleY = reactionScale
                            }
                        }
                )

                Image(
                    painter = painterResource(R.drawable.puppy_slots_machine),
                    contentDescription = "Puppy Slots machine",
                    modifier = Modifier
                        .offset(x = machineLeft, y = machineTop)
                        .width(machineWidth)
                        .height(machineHeight)
                )

                repeat(PuppySlotsEngine.REEL_COUNT) { index ->
                    val idleSymbol = when (index) {
                        0 -> PuppySlotSymbol.TREAT
                        1 -> PuppySlotSymbol.PUPPY
                        else -> PuppySlotSymbol.PAW
                    }
                    SlotReel(
                        emoji = display?.symbols?.getOrNull(index) ?: idleSymbol,
                        spinKey = spinKey,
                        reelIndex = index,
                        animationsEnabled = animationsEnabled,
                        modifier = Modifier
                            .offset(
                                x = machineLeft + machineWidth * SLOTS_REEL_LEFTS[index],
                                y = machineTop + machineHeight * SLOTS_REEL_TOP
                            )
                            .width(machineWidth * SLOTS_REEL_WIDTHS[index])
                            .height(machineHeight * SLOTS_REEL_HEIGHT)
                    )
                }

                val leverBaseWidth = machineWidth * 0.17f
                Image(
                    painter = painterResource(R.drawable.puppy_slots_lever_base),
                    contentDescription = null,
                    modifier = Modifier
                        .offset(
                            x = machineLeft + machineWidth * 0.91f,
                            y = machineTop + machineHeight * 0.49f
                        )
                        .width(leverBaseWidth)
                        .height(leverBaseWidth * (120f / 81f))
                )

                PuppySlotsLever(
                    pullToken = leverPullToken,
                    enabled = leverEnabled,
                    animationsEnabled = animationsEnabled,
                    onPull = onLeverPull,
                    modifier = Modifier
                        .offset(
                            x = machineLeft + machineWidth * 0.91f,
                            y = machineTop + machineHeight * 0.16f
                        )
                        .width(machineWidth * 0.15f)
                        .height(machineWidth * 0.15f * (180f / 66f))
                )

                val glowColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (reelsSpinning || winReady) {
                        val alphaBase = if (winReady) 0.80f else 0.42f
                        SLOTS_REEL_LEFTS.indices.forEach { index ->
                            drawRoundRect(
                                color = glowColor.copy(
                                    alpha = alphaBase *
                                        if (animationsEnabled) lightPulse else 0.65f
                                ),
                                topLeft = Offset(
                                    x = (machineLeft + machineWidth * SLOTS_REEL_LEFTS[index]).toPx(),
                                    y = (machineTop + machineHeight * SLOTS_REEL_TOP).toPx()
                                ),
                                size = Size(
                                    width = (machineWidth * SLOTS_REEL_WIDTHS[index]).toPx(),
                                    height = (machineHeight * SLOTS_REEL_HEIGHT).toPx()
                                ),
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            when {
                reelsSpinning -> {
                    Text(
                        "Reel 1 → Reel 2 → Reel 3",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Result reveals after the final reel locks.",
                        color = Color.White.copy(alpha = 0.70f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                resultRevealReady && display != null -> {
                    val resultText = when (display.winKind) {
                        PuppySlotsWinKind.LOSS -> "No match"
                        PuppySlotsWinKind.PAIR -> "Pair · " + display.multiplierLabel
                        PuppySlotsWinKind.TRIPLE -> "Triple · " + display.multiplierLabel
                    }
                    Text(
                        resultText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        display.payoutTreats.toString() + " Casino Chips returned",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                roundState == PuppyCasinoRoundState.WAGER_ACCEPTED -> {
                    Text(
                        "Wager accepted. Outcome not committed.",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    Text(
                        "Choose a wager and pull the machine.",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PuppySlotsLever(
    pullToken: Long,
    enabled: Boolean,
    animationsEnabled: Boolean,
    onPull: () -> Unit,
    modifier: Modifier = Modifier
) {
    val leverRotation = remember { Animatable(0f) }

    LaunchedEffect(pullToken, animationsEnabled) {
        if (pullToken <= 0L) return@LaunchedEffect
        if (!animationsEnabled) {
            leverRotation.snapTo(0f)
            return@LaunchedEffect
        }
        leverRotation.snapTo(0f)
        leverRotation.animateTo(
            targetValue = 24f,
            animationSpec = tween(140, easing = FastOutSlowInEasing)
        )
        leverRotation.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Image(
        painter = painterResource(R.drawable.puppy_slots_lever_handle),
        contentDescription = "Pull Puppy Slots lever",
        modifier = modifier
            .graphicsLayer {
                rotationZ = leverRotation.value
                transformOrigin = TransformOrigin(0.50f, 0.88f)
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onPull
            )
    )
}

@Composable
private fun SlotReel(
    emoji: PuppySlotSymbol,
    spinKey: String?,
    reelIndex: Int,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var visibleEmoji by remember { mutableStateOf(emoji) }

    LaunchedEffect(spinKey, emoji, animationsEnabled) {
        if (spinKey == null || !animationsEnabled) {
            visibleEmoji = emoji
            return@LaunchedEffect
        }

        val symbols = PuppySlotSymbol.entries
        val stopTicks = listOf(16, 23, 30)
        val ticks = stopTicks.getOrElse(reelIndex) { 30 }
        repeat(ticks) { tick ->
            visibleEmoji = symbols[(tick + reelIndex * 2) % symbols.size]
            delay(70L)
        }
        visibleEmoji = emoji
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = visibleEmoji,
            transitionSpec = {
                (
                    slideInVertically(
                        animationSpec = tween(75, easing = FastOutSlowInEasing)
                    ) { height -> -height } +
                        fadeIn(animationSpec = tween(55))
                ).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(75, easing = FastOutSlowInEasing)
                    ) { height -> height } +
                        fadeOut(animationSpec = tween(55))
                )
            },
            label = "slot_reel_" + reelIndex
        ) { symbol ->
            Image(
                painter = painterResource(symbol.drawableRes()),
                contentDescription = symbol.label,
                modifier = Modifier.fillMaxWidth(0.76f)
            )
        }
    }
}

@DrawableRes
private fun PuppySlotSymbol.drawableRes(): Int = when (this) {
    PuppySlotSymbol.TREAT -> R.drawable.slot_treat
    PuppySlotSymbol.BALL -> R.drawable.slot_ball
    PuppySlotSymbol.PAW -> R.drawable.slot_paw
    PuppySlotSymbol.TICKET -> R.drawable.slot_ticket
    PuppySlotSymbol.PUPPY -> R.drawable.slot_puppy
    PuppySlotSymbol.STAR -> R.drawable.slot_star
}

@Composable
private fun SlotsRulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("How Puppy Slots works", fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                "• Three reels are rolled independently from the published weights.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "• A triple uses that symbol’s listed multiplier.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "• Any pair that is not a triple returns 0.5× the wager.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "• No matching pair returns 0 Casino Chips.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "• The Ticket symbol is visual in Slots; this engine pays Casino Chips only.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "• The exact symbols and payout are committed to the save before reveal.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SlotsPayoutTable() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Published payout table", fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            PuppySlotsEngine.payoutTable.forEach { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(rule.label, modifier = Modifier.weight(1f))
                    Text(rule.multiplierLabel, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun SlotsWeightTable() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Published reel weights", fontWeight = FontWeight.Black)
            Text(
                "Each reel uses the same 100-point distribution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            PuppySlotSymbol.entries.forEach { symbol ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        symbol.emoji + " " + symbol.label,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        symbol.weightPercent.toString() + "%",
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
