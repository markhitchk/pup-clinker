package com.harleytg.puppyclicker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

private val BlackjackCardRed = Color(0xFFC5323D)

@Composable
internal fun PuppyBlackjackScreen(
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
                game = PuppyCasinoGame.BLACKJACK,
                flags = flags
            )

    var wager by rememberSaveable { mutableLongStateOf(100L) }
    var lastStatePayload by rememberSaveable { mutableStateOf<String?>(null) }
    var lastRoundId by rememberSaveable { mutableStateOf("") }
    var lastOutcomePayload by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    fun runBlackjackCommand(
        actionName: String,
        action: () -> PuppyBlackjackCommandResult
    ) {
        val result = PuppyCasinoRuntimeGuard.run(
            PuppyCasinoGame.BLACKJACK,
            actionName
        ) {
            action()
        }.getOrNull()
        message = if (result == null) {
            "Blackjack recovered from a runtime error while processing $actionName."
        } else {
            commandMessage(result)
        }
    }

    val blackjackRound = activeRound?.takeIf { it.game == PuppyCasinoGame.BLACKJACK }
    val roundState = remember(blackjackRound?.wagerPayload) {
        PuppyBlackjackStateCodec.decodeAndValidate(blackjackRound?.wagerPayload)
    }
    val roundOutcome = remember(blackjackRound?.outcomePayload, roundState) {
        roundState?.takeIf { it.complete }?.let {
            PuppyBlackjackOutcomeCodec.decodeAndValidate(
                raw = blackjackRound?.outcomePayload,
                state = it
            )
        }
    }

    LaunchedEffect(
        blackjackRound?.roundId,
        blackjackRound?.state,
        blackjackRound?.wagerPayload,
        blackjackRound?.outcomePayload
    ) {
        val round = blackjackRound ?: return@LaunchedEffect
        val savedState = PuppyBlackjackStateCodec.decodeAndValidate(round.wagerPayload)
            ?: run {
                message = "Saved Blackjack hand failed validation."
                return@LaunchedEffect
            }

        if (
            round.state == PuppyCasinoRoundState.WAGER_ACCEPTED &&
            savedState.complete
        ) {
            val finalized = PuppyCasinoRuntimeGuard.run(
                PuppyCasinoGame.BLACKJACK,
                "finalize"
            ) {
                vm.finalizePendingBlackjackRound()
            }.getOrNull()
            if (finalized == null) {
                message = "Blackjack recovered from a runtime error while finalizing the hand."
                return@LaunchedEffect
            }
            if (!finalized.success) {
                message = "Unable to commit the completed Blackjack hand: " +
                    (finalized.failure?.name ?: "unknown error")
            }
            return@LaunchedEffect
        }

        if (round.state == PuppyCasinoRoundState.OUTCOME_COMMITTED) {
            val outcome = PuppyBlackjackOutcomeCodec.decodeAndValidate(
                raw = round.outcomePayload,
                state = savedState
            )
            if (
                outcome == null ||
                outcome.totalPayoutTreats != round.payoutTreats ||
                PuppyBlackjackEngine.totalWager(savedState) != round.wagerTreats
            ) {
                message = "Saved Blackjack outcome failed validation. Settlement was blocked."
                return@LaunchedEffect
            }

            lastRoundId = round.roundId
            lastStatePayload = round.wagerPayload
            lastOutcomePayload = round.outcomePayload
            delay(if (state.animationsEnabled) 1_500 else 250)

            val settled = PuppyCasinoRuntimeGuard.run(
                PuppyCasinoGame.BLACKJACK,
                "settle"
            ) {
                vm.settleCasinoRound(round.roundId)
            }.getOrNull()
            if (settled == null) {
                message = "Blackjack recovered from a runtime error while settling the hand."
                return@LaunchedEffect
            }
            if (!settled.success) {
                message = "Unable to settle the saved Blackjack round: " +
                    (settled.failure?.name ?: "unknown error")
            }
        }
    }

    val lastState = remember(lastStatePayload) {
        PuppyBlackjackStateCodec.decodeAndValidate(lastStatePayload)
    }
    val lastOutcome = remember(lastOutcomePayload, lastState) {
        lastState?.takeIf { it.complete }?.let {
            PuppyBlackjackOutcomeCodec.decodeAndValidate(
                raw = lastOutcomePayload,
                state = it
            )
        }
    }

    val displayState = roundState ?: lastState
    val displayOutcome = if (roundState != null) roundOutcome else lastOutcome

    PuppyBlackjackGameContent(
        displayState = displayState,
        displayOutcome = displayOutcome,
        chips = state.casinoChips,
        acceptingActions = blackjackRound?.state == PuppyCasinoRoundState.WAGER_ACCEPTED && roundState?.complete == false,
        animationsEnabled = state.animationsEnabled,
        roundKey = blackjackRound?.roundId ?: lastRoundId,
        message = message,
        onBack = onBack,
        onHit = { runBlackjackCommand("hit") { vm.blackjackHit() } },
        onStand = { runBlackjackCommand("stand") { vm.blackjackStand() } },
        onDouble = { runBlackjackCommand("double") { vm.blackjackDouble() } },
        onSplit = { runBlackjackCommand("split") { vm.blackjackSplit() } }
    ) {
        if (activeRound == null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Wager",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                "Natural Blackjack returns 2.5× total. Normal wins return 2×. Pushes return 1×.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 340.dp && LocalDensity.current.fontScale <= 1.3f) 4 else 2
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PuppyBlackjackEngine.wagerPresets.chunked(columns).forEach { presets ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            presets.forEach { preset ->
                                FilterChip(selected = wager == preset, onClick = { wager = preset },
                                    label = { Text(preset.toString()) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    runBlackjackCommand("start") {
                        vm.startBlackjackRound(wager)
                    }
                },
                enabled =
                    canPlayFeature &&
                        PuppyBlackjackEngine.isValidInitialWager(wager) &&
                        state.casinoChips >= wager,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        !canPlayFeature -> "Blackjack Locked"
                        state.casinoChips < wager -> "Not Enough Chips"
                        else -> "Deal for " + wager + " Chips"
                    }
                )
            }
        } else if (activeRound?.game != PuppyCasinoGame.BLACKJACK) {
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            ) {
                Text(
                    "Another casino round is already in progress. Finish or refund it from the Casino hub first.",
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

    }
}


/** Stateless rendering: persisted engine state enters here; only commands leave. */
@Composable
internal fun PuppyBlackjackGameContent(
    displayState: PuppyBlackjackState?,
    displayOutcome: PuppyBlackjackOutcome?,
    chips: Long,
    acceptingActions: Boolean,
    animationsEnabled: Boolean,
    roundKey: String,
    message: String? = null,
    onBack: () -> Unit = {},
    onHit: () -> Unit = {},
    onStand: () -> Unit = {},
    onDouble: () -> Unit = {},
    onSplit: () -> Unit = {},
    betting: @Composable () -> Unit = {}
) {
    var selectedHand by remember(roundKey, displayState?.activeHandIndex) {
        mutableStateOf(displayState?.activeHandIndex?.coerceAtLeast(0) ?: 0)
    }
    fun act(command: () -> Unit) {
        selectedHand = displayState?.activeHandIndex?.coerceAtLeast(0) ?: 0
        command()
    }
    // The parent app owns its header. Unconsumed cutout/navigation insets are respected here.
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val viewportHeight = maxHeight
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        val motion = animationsEnabled && android.animation.ValueAnimator.areAnimatorsEnabled()
        val fontScale = LocalDensity.current.fontScale
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Casino") }
                Text("Puppy Blackjack", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            val controls: @Composable () -> Unit = {
                BlackjackWalletCopy(chips)
                if (displayState != null) {
                    BlackjackHandSummary(displayState, displayOutcome, motion)
                }
                if (acceptingActions && displayState != null && !displayState.complete) {
                    BlackjackActionPanel(displayState, chips, motion, { act(onHit) }, { act(onStand) }, { act(onDouble) }, { act(onSplit) })
                }
                betting()
                message?.let {
                    Text(it, Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error)
                }
                BlackjackHelp()
            }
            if (wide) {
                Row(Modifier.widthIn(max = 1000.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BlackjackCartoonTable(displayState, motion, roundKey, selectedHand, { selectedHand = it },
                            Modifier.width(minOf(430.dp, (viewportHeight - 54.dp).coerceAtLeast(280.dp) * PuppyBlackjackLayout.TABLE_ASPECT)))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { controls() }
                }
            } else {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    BlackjackWalletCopy(chips)
                    // Fit artwork to the viewport budget, while controls and type retain their native size.
                    val tableHeight = (viewportHeight - if (fontScale > 1.3f) 370.dp else 300.dp).coerceIn(280.dp, 500.dp)
                    BlackjackCartoonTable(displayState, motion, roundKey, selectedHand, { selectedHand = it },
                        Modifier.widthIn(max = tableHeight * PuppyBlackjackLayout.TABLE_ASPECT).fillMaxWidth())
                    if (displayState != null) BlackjackHandSummary(displayState, displayOutcome, motion)
                    if (acceptingActions && displayState != null && !displayState.complete) {
                        BlackjackActionPanel(displayState, chips, motion, { act(onHit) }, { act(onStand) }, { act(onDouble) }, { act(onSplit) })
                    }
                    betting()
                    message?.let {
                        Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                    }
                    BlackjackHelp()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BlackjackWalletCopy(casinoChips: Long) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant) {
        Text("🐾 Casino Chip Wallet · $casinoChips Chips", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BlackjackCartoonTable(state: PuppyBlackjackState?, motion: Boolean, roundKey: String,
    selectedHand: Int, onSelectHand: (Int) -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (state != null && state.hands.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.hands.forEachIndexed { index, _ ->
                    FilterChip(selected = selectedHand == index, onClick = { onSelectHand(index) },
                        label = { Text("Hand ${index + 1}") }, modifier = Modifier.weight(1f).semantics {
                            stateDescription = if (!state.complete && state.activeHandIndex == index) "Current turn" else "Inspect hand"
                        })
                }
            }
        }
        val hand = state?.hands?.getOrNull(selectedHand)
        key(roundKey) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(PuppyBlackjackLayout.TABLE_ASPECT).testTag("blackjack_table")) {
                val metrics = PuppyBlackjackLayout.table(maxWidth.value)
                val sharedCardWidth = minOf(metrics.cardWidth,
                    PuppyBlackjackLayout.fan(metrics.dealer.width, metrics.cardWidth, state?.dealerCards?.size ?: 0).cardWidth,
                    PuppyBlackjackLayout.fan(metrics.player.width, metrics.cardWidth, hand?.cards?.size ?: 0).cardWidth).dp
                val chip = remember { Animatable(1f) }
                LaunchedEffect(state?.hands?.map { it.wagerTreats }, motion) {
                    if (state == null || !motion) chip.snapTo(1f)
                    else { chip.snapTo(0f); chip.animateTo(1f, tween(240)) }
                }
                Layout(content = {
                    Image(painterResource(R.drawable.puppy_blackjack_table), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    BlackjackCardRow(state?.dealerCards.orEmpty(), state?.complete == false, motion, sharedCardWidth, "dealer")
                    BlackjackCardRow(hand?.cards.orEmpty(), false, motion, sharedCardWidth, "player")
                }, modifier = Modifier.fillMaxSize().clipToBounds()) { measurables, constraints ->
                    val artwork = measurables[0].measure(Constraints.fixed(constraints.maxWidth, metrics.artworkHeight.dp.roundToPx()))
                    fun measureZone(index: Int, zone: PuppyBlackjackLayout.Zone) = measurables[index].measure(
                        androidx.compose.ui.unit.Constraints.fixed(zone.width.dp.roundToPx(), zone.height.dp.roundToPx()))
                    val dealer = measureZone(1, metrics.dealer)
                    val player = measureZone(2, metrics.player)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        artwork.placeRelative(0, -metrics.artworkTop.dp.roundToPx())
                        dealer.placeRelative(metrics.dealer.left.dp.roundToPx(), metrics.dealer.top.dp.roundToPx())
                        player.placeRelative(metrics.player.left.dp.roundToPx(), metrics.player.top.dp.roundToPx())
                    }
                }
                if (state != null) {
                    val density = LocalDensity.current
                    Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * .17f).size(14.dp).graphicsLayer {
                        alpha = .75f + .25f * chip.value
                        translationY = (1f - chip.value) * with(density) { (-24).dp.toPx() }
                    }) {
                        drawCircle(Color(0xFFFFCF4A))
                        drawCircle(Color(0xFF8F5425), radius = size.minDimension * .32f)
                    }
                }
            }
        }
        if (state == null) Text("Choose a wager to deal.", style = MaterialTheme.typography.bodySmall)
        else {
            val dealer = if (state.complete) "Dealer · ${PuppyBlackjackEngine.handValue(state.dealerCards).total} points"
                else "Dealer · ${PuppyBlackjackCard(state.dealerCards.first()).label} showing · hole card hidden"
            Text(dealer, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun BlackjackCardRow(cards: List<Int>, hideAfterFirst: Boolean, motion: Boolean, cardWidth: Dp, tag: String) {
    Layout(content = {
        cards.forEachIndexed { index, id ->
            val hidden = hideAfterFirst && index > 0
            key(index, id) {
                BlackjackPlayingCard(if (hidden) null else PuppyBlackjackCard(id), "$id:$hidden", motion, index * 65L)
            }
        }
    }, modifier = Modifier.testTag("blackjack_${tag}_cards")) { measurables, constraints ->
        val fan = PuppyBlackjackLayout.fan(constraints.maxWidth.toFloat(), cardWidth.toPx(), cards.size)
        val width = fan.cardWidth.roundToInt().coerceAtLeast(1)
        val height = (fan.cardWidth * 1.4f).roundToInt().coerceAtMost(constraints.maxHeight)
        val children = measurables.map { it.measure(Constraints.fixed(width, height)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val left = ((constraints.maxWidth - fan.width) / 2f).roundToInt()
            children.forEachIndexed { index, child ->
                child.placeRelative(left + (index * fan.step).roundToInt(), (constraints.maxHeight - height) / 2)
            }
        }
    }
}

@Composable
private fun BlackjackPlayingCard(card: PuppyBlackjackCard?, animationToken: String, motion: Boolean, dealDelayMs: Long) {
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(animationToken, motion) {
        if (!motion) reveal.snapTo(1f)
        else {
            reveal.snapTo(0f)
            delay(dealDelayMs)
            reveal.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.graphicsLayer {
        alpha = reveal.value
        translationX = (1f - reveal.value) * with(density) { 18.dp.toPx() }
        translationY = (1f - reveal.value) * with(density) { (-12).dp.toPx() }
        rotationY = (1f - reveal.value) * 80f
        cameraDistance = 12f * density.density
    }.semantics(mergeDescendants = true) { contentDescription = card?.label ?: "Hidden dealer card" }) {
        Image(painterResource(if (card == null) R.drawable.glossy_blue_paw_print_card_back else R.drawable.blank_glossy_cream_card_panel),
            null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (card != null) {
            val color = if (card.suit == PuppyBlackjackSuit.DIAMONDS || card.suit == PuppyBlackjackSuit.HEARTS) BlackjackCardRed else Color(0xFF171717)
            // Printed playing-card typography scales with the card, not accessibility UI text.
            // TalkBack exposes the complete card name even when the hand uses overlap.
            val printSize = (maxWidth.value * .29f / density.fontScale).sp
            Column(Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 2.dp).clearAndSetSemantics {}) {
                Text(card.rankLabel, color = color, fontSize = printSize, lineHeight = printSize, fontWeight = FontWeight.Black)
                Text(card.suit.symbol, color = color, fontSize = printSize, lineHeight = printSize)
            }
            Text(card.suit.symbol, Modifier.align(Alignment.Center).clearAndSetSemantics {}, color = color,
                fontSize = (maxWidth.value * .43f / density.fontScale).sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BlackjackHandSummary(state: PuppyBlackjackState, outcome: PuppyBlackjackOutcome?, motion: Boolean) {
    val active = state.activeHandIndex.coerceAtLeast(0)
    val won = outcome?.hands?.any { it.result == PuppyBlackjackHandResult.BLACKJACK || it.result == PuppyBlackjackHandResult.WIN } == true
    val busted = outcome?.hands?.any { it.result == PuppyBlackjackHandResult.BUST } == true
    val feedback = remember { Animatable(0f) }
    LaunchedEffect(outcome, motion) {
        if (outcome == null || !motion) feedback.snapTo(0f)
        else {
            feedback.snapTo(1f)
            feedback.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        }
    }
    val feedbackColor = when {
        won -> Color(0xFF25633E)
        busted -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val accent by animateColorAsState(if (state.complete) MaterialTheme.colorScheme.outlineVariant else Color(0xFFFFCF4A),
        if (motion) tween(180) else snap(), label = "blackjack_turn")
    Surface(Modifier.fillMaxWidth().graphicsLayer {
        scaleX = 1f + .012f * feedback.value
        scaleY = 1f + .012f * feedback.value
    }.testTag("blackjack_hand_summary").semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, accent), color = feedbackColor,
        contentColor = if (won) Color.White else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.hands.forEachIndexed { index, hand ->
                val value = PuppyBlackjackEngine.handValue(hand.cards)
                val result = outcome?.hands?.getOrNull(index)
                val status = when {
                    result != null -> "${result.result.name.lowercase().replaceFirstChar { it.uppercase() }} · ${result.payoutTreats} returned"
                    value.total > 21 -> "Bust"
                    hand.stood -> "Standing"
                    index == active && !state.complete -> "Your turn"
                    state.complete -> "Round complete"
                    else -> "Waiting"
                }
                Text("Hand ${index + 1} · ${value.total}${if (value.soft) " soft" else ""} · ${hand.wagerTreats} Chips · $status",
                    style = MaterialTheme.typography.labelLarge, fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun BlackjackActionPanel(state: PuppyBlackjackState, chips: Long, motion: Boolean,
    onHit: () -> Unit, onStand: () -> Unit, onDouble: () -> Unit, onSplit: () -> Unit) {
    val actions = PuppyBlackjackControls.from(state, chips)
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("blackjack_actions")) {
        val singleRow = maxWidth >= 520.dp && LocalDensity.current.fontScale <= 1.3f
        val buttons: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> BlackjackActionButton("🐾 HIT", Color(0xFF1463AD), Color.White, actions.hit, motion, onHit, m) },
            { m -> BlackjackActionButton("STAND", Color(0xFF237246), Color.White, actions.stand, motion, onStand, m) },
            { m -> BlackjackActionButton("2× DOUBLE", Color(0xFFFFCF4A), Color(0xFF322408), actions.double, motion, onDouble, m) },
            { m -> BlackjackActionButton("⇄ SPLIT", Color(0xFF7845AD), Color.White, actions.split, motion, onSplit, m) }
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            buttons.chunked(if (singleRow) 4 else 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { it(Modifier.weight(1f)) } }
            }
        }
    }
}

@Composable
private fun BlackjackActionButton(label: String, background: Color, foreground: Color, enabled: Boolean, motion: Boolean,
    onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) .97f else 1f, if (motion) tween(90) else snap(), label = "blackjack_press")
    Button(onClick, modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale }, enabled = enabled,
        interactionSource = interaction, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = foreground,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
        Text(label, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BlackjackHelp() {
    PuppyCasinoHelp { BlackjackRulesCard() }
}

@Composable
private fun BlackjackRulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Published Blackjack rules", fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            BlackjackRuleRow("Dealer", "Stands on soft 17")
            BlackjackRuleRow("Natural Blackjack", "3:2 profit · 2.5× total")
            BlackjackRuleRow("Normal win", "2× total")
            BlackjackRuleRow("Push", "1× wager returned")
            BlackjackRuleRow("Double", "First two cards · one card then stand")
            BlackjackRuleRow("Split", "Up to 3 player hands")
            BlackjackRuleRow("Split aces", "One card each")
            Spacer(Modifier.height(6.dp))
            Text(
                "Dealer Blackjack is resolved immediately after the initial deal.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Split aces cannot be resplit in this version.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Every deck position, hand, wager, split, double, and dealer draw is saved to the active casino round.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BlackjackRuleRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun commandMessage(result: PuppyBlackjackCommandResult): String? {
    if (result.success) return null
    return when (result.failure) {
        PuppyBlackjackCommandFailure.INVALID_WAGER ->
            "Invalid Blackjack wager."
        PuppyBlackjackCommandFailure.TRANSACTION_REJECTED ->
            "Action blocked: " + (result.transactionFailure?.name ?: "transaction rejected")
        PuppyBlackjackCommandFailure.INVALID_SAVED_STATE ->
            "Saved Blackjack state failed validation."
        PuppyBlackjackCommandFailure.ACTION_NOT_ALLOWED ->
            "Action not allowed: " + (result.actionError?.name ?: "invalid action")
        PuppyBlackjackCommandFailure.OUTCOME_COMMIT_FAILED ->
            "Completed hand could not be committed. It remains saved for recovery."
        null ->
            "Blackjack action failed."
    }
}