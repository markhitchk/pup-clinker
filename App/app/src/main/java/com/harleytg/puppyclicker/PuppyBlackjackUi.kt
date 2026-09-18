package com.harleytg.puppyclicker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

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
    var lastOutcomePayload by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

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
            val finalized = vm.finalizePendingBlackjackRound()
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

            lastStatePayload = round.wagerPayload
            lastOutcomePayload = round.outcomePayload
            delay(if (state.animationsEnabled) 1_500 else 250)

            val settled = vm.settleCasinoRound(round.roundId)
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
    val displayOutcome = roundOutcome ?: lastOutcome
    val activeHand = displayState
        ?.takeIf { !it.complete }
        ?.hands
        ?.getOrNull(displayState.activeHandIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
    ) {
        // Compose-only title area: no generated app-title-bar artwork.
        TextButton(onClick = onBack) {
            Text("‹ Puppy Casino", fontWeight = FontWeight.Bold)
        }

        Text(
            "Puppy Blackjack",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "Single-player Blackjack against the CPU dealer. Every card and action is persisted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        BlackjackWalletCard(state)
        Spacer(Modifier.height(12.dp))

        BlackjackCartoonTable(
            displayState = displayState,
            displayOutcome = displayOutcome,
            animationsEnabled = state.animationsEnabled
        )

        message?.let { text ->
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(text, modifier = Modifier.padding(12.dp))
            }
        }

        if (
            blackjackRound?.state == PuppyCasinoRoundState.WAGER_ACCEPTED &&
            roundState != null &&
            !roundState.complete
        ) {
            Spacer(Modifier.height(10.dp))
            BlackjackActionPanel(
                roundState = roundState,
                activeHand = activeHand,
                treats = state.treats,
                onHit = { message = commandMessage(vm.blackjackHit()) },
                onStand = { message = commandMessage(vm.blackjackStand()) },
                onDouble = { message = commandMessage(vm.blackjackDouble()) },
                onSplit = { message = commandMessage(vm.blackjackSplit()) }
            )
        } else if (activeRound == null) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PuppyBlackjackEngine.wagerPresets.forEach { preset ->
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
                onClick = { message = commandMessage(vm.startBlackjackRound(wager)) },
                enabled =
                    canPlayFeature &&
                        PuppyBlackjackEngine.isValidInitialWager(wager) &&
                        state.treats >= wager,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        !canPlayFeature -> "Blackjack Locked"
                        state.treats < wager -> "Not Enough Treats"
                        else -> "Deal for " + wager + " Treats"
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

        Spacer(Modifier.height(16.dp))
        BlackjackRulesCard()
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun BlackjackCartoonTable(
    displayState: PuppyBlackjackState?,
    displayOutcome: PuppyBlackjackOutcome?,
    animationsEnabled: Boolean
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1122f / 1402f)
    ) {
        Image(
            painter = painterResource(R.drawable.puppy_blackjack_table),
            contentDescription = "Puppy Blackjack table",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Box(
            modifier = Modifier
                .offset(x = maxWidth * 0.07f, y = maxHeight * 0.16f)
                .width(maxWidth * 0.70f)
                .height(maxHeight * 0.25f)
        ) {
            BlackjackCardRow(
                cards = displayState?.dealerCards.orEmpty(),
                hideAfterFirst = displayState?.complete == false,
                animationsEnabled = animationsEnabled
            )
        }

        if (displayState != null && displayState.complete) {
            val dealerValue = PuppyBlackjackEngine.handValue(displayState.dealerCards)
            Surface(
                modifier = Modifier.offset(x = maxWidth * 0.07f, y = maxHeight * 0.40f),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xCC163629),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
            ) {
                Text(
                    "Dealer total: " + dealerValue.total,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .offset(x = maxWidth * 0.07f, y = maxHeight * 0.60f)
                .width(maxWidth * 0.86f)
        ) {
            if (displayState == null) {
                Text(
                    "Start a round to deal cards.",
                    color = Color.White.copy(alpha = 0.88f),
                    fontWeight = FontWeight.Bold
                )
            } else {
                displayState.hands.forEachIndexed { index, hand ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    BlackjackHandCard(
                        index = index,
                        hand = hand,
                        active = !displayState.complete && index == displayState.activeHandIndex,
                        outcome = displayOutcome?.hands?.getOrNull(index),
                        animationsEnabled = animationsEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun BlackjackActionPanel(
    roundState: PuppyBlackjackState,
    activeHand: PuppyBlackjackHand?,
    treats: Long,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit,
    onSplit: () -> Unit
) {
    val activeValue = activeHand?.let { PuppyBlackjackEngine.handValue(it.cards) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1192f / 900f)
            .animateContentSize(animationSpec = tween(260))
    ) {
        Image(
            painter = painterResource(R.drawable.wooden_paw_themed_game_panel),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Text(
            "Hand " + (roundState.activeHandIndex + 1) +
                " · " + (activeValue?.total ?: 0) + " points",
            modifier = Modifier.offset(x = maxWidth * 0.07f, y = maxHeight * 0.14f),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )

        Text(
            (activeHand?.wagerTreats ?: 0L).toString() + " Treats",
            modifier = Modifier.offset(x = maxWidth * 0.70f, y = maxHeight * 0.14f),
            color = Color.White,
            fontWeight = FontWeight.Black
        )

        BlackjackImageButton(
            drawable = R.drawable.glossy_blue_hit_button,
            contentDescription = "🐾 + HIT",
            enabled = PuppyBlackjackEngine.canHit(roundState),
            onClick = onHit,
            x = maxWidth * 0.055f,
            y = maxHeight * 0.31f,
            width = maxWidth * 0.43f,
            height = maxHeight * 0.26f
        )

        BlackjackImageButton(
            drawable = R.drawable.glossy_green_stand_button,
            contentDescription = "■ STAND",
            enabled = PuppyBlackjackEngine.canStand(roundState),
            onClick = onStand,
            x = maxWidth * 0.515f,
            y = maxHeight * 0.31f,
            width = maxWidth * 0.43f,
            height = maxHeight * 0.26f
        )

        BlackjackImageButton(
            drawable = R.drawable.glossy_golden_2_double_button,
            contentDescription = "2× DOUBLE",
            enabled =
                PuppyBlackjackEngine.canDouble(roundState) &&
                    activeHand != null &&
                    treats >= activeHand.wagerTreats,
            onClick = onDouble,
            x = maxWidth * 0.055f,
            y = maxHeight * 0.62f,
            width = maxWidth * 0.43f,
            height = maxHeight * 0.24f
        )

        BlackjackImageButton(
            drawable = R.drawable.glossy_purple_split_button,
            contentDescription = "⇄ SPLIT",
            enabled =
                PuppyBlackjackEngine.canSplit(roundState) &&
                    activeHand != null &&
                    treats >= activeHand.wagerTreats,
            onClick = onSplit,
            x = maxWidth * 0.515f,
            y = maxHeight * 0.62f,
            width = maxWidth * 0.43f,
            height = maxHeight * 0.24f
        )
    }
}

@Composable
private fun BlackjackImageButton(
    drawable: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = contentDescription,
        modifier = Modifier
            .offset(x = x, y = y)
            .width(width)
            .height(height)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.42f
                scaleX = if (enabled) 1f else 0.985f
                scaleY = if (enabled) 1f else 0.985f
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentScale = ContentScale.FillBounds
    )
}

@Composable
private fun BlackjackWalletCard(state: V6GameState) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1400f / 467f)
    ) {
        Image(
            painter = painterResource(R.drawable.treat_wallet_puppy_banner),
            contentDescription = "Treat Wallet",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        Text(
            state.treats.toString() + " Treats",
            modifier = Modifier.offset(x = maxWidth * 0.23f, y = maxHeight * 0.49f),
            color = Color(0xFF2C261E),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun BlackjackCardRow(
    cards: List<Int>,
    hideAfterFirst: Boolean,
    animationsEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        cards.forEachIndexed { index, id ->
            val hidden = hideAfterFirst && index > 0
            key(index, id) {
                BlackjackPlayingCard(
                    card = if (hidden) null else PuppyBlackjackCard(id),
                    animationToken = id.toString() + ":" + hidden,
                    animationsEnabled = animationsEnabled,
                    dealDelayMs = index * 120L
                )
            }
        }
    }
}

@Composable
private fun BlackjackPlayingCard(
    card: PuppyBlackjackCard?,
    animationToken: String,
    animationsEnabled: Boolean,
    dealDelayMs: Long
) {
    val red =
        card != null &&
            card.suit in setOf(PuppyBlackjackSuit.DIAMONDS, PuppyBlackjackSuit.HEARTS)
    val reveal = remember { Animatable(1f) }

    LaunchedEffect(animationToken, animationsEnabled) {
        if (!animationsEnabled) {
            reveal.snapTo(1f)
            return@LaunchedEffect
        }
        reveal.snapTo(0f)
        delay(dealDelayMs)
        reveal.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .width(58.dp)
            .height(78.dp)
            .graphicsLayer {
                alpha = reveal.value
                translationX = (1f - reveal.value) * 42f
                translationY = (1f - reveal.value) * -46f
                rotationY = (1f - reveal.value) * 105f
                rotationZ = (1f - reveal.value) * -7f
                shadowElevation = 2f + (9f * reveal.value)
                scaleX = 0.68f + (0.32f * reveal.value)
                scaleY = 0.80f + (0.20f * reveal.value)
            }
    ) {
        Image(
            painter = painterResource(
                if (card == null) R.drawable.glossy_blue_paw_print_card_back
                else R.drawable.blank_glossy_cream_card_panel
            ),
            contentDescription = card?.label ?: "Hidden dealer card",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        if (card != null) {
            val cardColor = if (red) BlackjackCardRed else Color(0xFF171717)

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 7.dp, top = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    card.rankLabel,
                    color = cardColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    card.suit.symbol,
                    color = cardColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                card.suit.symbol,
                modifier = Modifier.align(Alignment.Center),
                color = cardColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun BlackjackHandCard(
    index: Int,
    hand: PuppyBlackjackHand,
    active: Boolean,
    outcome: PuppyBlackjackHandOutcome?,
    animationsEnabled: Boolean
) {
    val value = PuppyBlackjackEngine.handValue(hand.cards)
    val activeScale by animateFloatAsState(
        targetValue = if (active) 1f else 0.985f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "blackjack_active_hand_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = activeScale
                scaleY = activeScale
            }
            .animateContentSize(animationSpec = tween(260)),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xCC0A402D).copy(alpha = if (active) 0.82f else 0.60f),
        border = BorderStroke(
            if (active) 2.dp else 1.dp,
            if (active) Color(0xFFFFCF4A) else Color.White.copy(alpha = 0.18f)
        ),
        shadowElevation = if (active) 4.dp else 0.dp
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hand " + (index + 1),
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                Text(
                    hand.wagerTreats.toString() + " Treats",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(5.dp))

            BlackjackCardRow(
                cards = hand.cards,
                hideAfterFirst = false,
                animationsEnabled = animationsEnabled
            )

            Spacer(Modifier.height(5.dp))

            Text(
                "Total " + value.total + if (value.soft) " · soft" else "",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            val status = when {
                outcome != null -> outcome.result.name + " · " + outcome.payoutTreats + " returned"
                hand.splitAces -> "Split aces · one card only"
                hand.doubled -> "Doubled"
                hand.stood -> "Standing"
                active -> "Your turn"
                else -> "Waiting"
            }

            Text(
                status,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
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