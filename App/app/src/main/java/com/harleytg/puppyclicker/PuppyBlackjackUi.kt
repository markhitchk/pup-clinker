package com.harleytg.puppyclicker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val BlackjackTableGreen = Color(0xFF0E6A46)
private val BlackjackTableDeep = Color(0xFF073D2C)
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
                onHit = {
                    message = commandMessage(vm.blackjackHit())
                },
                onStand = {
                    message = commandMessage(vm.blackjackStand())
                },
                onDouble = {
                    message = commandMessage(vm.blackjackDouble())
                },
                onSplit = {
                    message = commandMessage(vm.blackjackSplit())
                }
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
                onClick = {
                    message = commandMessage(vm.startBlackjackRound(wager))
                },
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
    val palette = puppyCasinoCartoonPalette()
    CartoonStageFrame(
        modifier = Modifier.fillMaxWidth(),
        accent = palette.gold,
        background = Brush.verticalGradient(
            listOf(
                palette.woodLight,
                palette.woodMid,
                palette.woodDark
            )
        ),
        corner = 30.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 18.dp, end = 12.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🐶", fontSize = 36.sp)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = palette.cream,
                border = BorderStroke(2.dp, palette.gold),
                shadowElevation = 5.dp
            ) {
                Text(
                    "🐾  PUPPY BLACKJACK  🐾",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    color = palette.woodDark,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = BlackjackTableGreen,
                border = BorderStroke(3.dp, palette.gold.copy(alpha = 0.75f)),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.07f),
                                    BlackjackTableGreen,
                                    BlackjackTableDeep
                                )
                            )
                        )
                        .padding(15.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = palette.woodDark,
                            border = BorderStroke(2.dp, palette.woodLight),
                            shadowElevation = 3.dp
                        ) {
                            Text(
                                "🐾 DEALER",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = palette.cream,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            modifier = Modifier.size(width = 58.dp, height = 42.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF183A5C),
                            border = BorderStroke(2.dp, palette.gold.copy(alpha = 0.65f)),
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🂠", color = Color.White, fontSize = 23.sp)
                                Text("🐾", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    BlackjackCardRow(
                        cards = displayState?.dealerCards.orEmpty(),
                        hideAfterFirst = displayState?.complete == false,
                        animationsEnabled = animationsEnabled
                    )

                    if (displayState != null && displayState.complete) {
                        val dealerValue = PuppyBlackjackEngine.handValue(displayState.dealerCards)
                        Text(
                            "Dealer total: " + dealerValue.total,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(Modifier.height(9.dp))
                    Text(
                        "BLACKJACK",
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.gold.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "GOOD PUPPIES WIN MORE TREATS",
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.50f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = palette.cream,
                        border = BorderStroke(2.dp, palette.gold),
                        shadowElevation = 3.dp
                    ) {
                        Text(
                            "🐾 YOUR HAND 🐾",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = palette.woodDark,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(7.dp))

                    if (displayState == null) {
                        Text(
                            "Start a round to deal cards.",
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    } else {
                        displayState.hands.forEachIndexed { index, hand ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
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
    val palette = puppyCasinoCartoonPalette()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(260)),
        shape = RoundedCornerShape(24.dp),
        color = palette.woodMid,
        border = BorderStroke(3.dp, palette.woodLight),
        shadowElevation = 7.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), palette.woodMid, palette.woodDark.copy(alpha = 0.88f))
                    )
                )
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "🐾 YOUR TURN",
                        color = palette.cream,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Hand " + (roundState.activeHandIndex + 1) +
                            " · " + (activeValue?.total ?: 0) + " points",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    (activeHand?.wagerTreats ?: 0L).toString() + " 🍪",
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onHit,
                    enabled = PuppyBlackjackEngine.canHit(roundState),
                    modifier = Modifier.weight(1f).height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🐾 + HIT", fontWeight = FontWeight.Black)
                        Text("Take a card", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = onStand,
                    enabled = PuppyBlackjackEngine.canStand(roundState),
                    modifier = Modifier.weight(1f).height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25A45A)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("■ STAND", fontWeight = FontWeight.Black)
                        Text("Hold hand", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDouble,
                    enabled =
                        PuppyBlackjackEngine.canDouble(roundState) &&
                            activeHand != null &&
                            treats >= activeHand.wagerTreats,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2A20A)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("2× DOUBLE", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSplit,
                    enabled =
                        PuppyBlackjackEngine.canSplit(roundState) &&
                            activeHand != null &&
                            treats >= activeHand.wagerTreats,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B49E8)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("⇄ SPLIT", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                "Hit adds a card. Stand locks this hand. Double adds one card and stands.",
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BlackjackWalletCard(state: V6GameState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Text(
                    "CPU Dealer",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
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
    val red = card != null && card.suit in setOf(PuppyBlackjackSuit.DIAMONDS, PuppyBlackjackSuit.HEARTS)
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

    Surface(
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
            },
        shape = RoundedCornerShape(11.dp),
        color = if (card == null) Color(0xFF278FD8) else Color(0xFFFFFDF8),
        border = BorderStroke(2.dp, if (card == null) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.18f)),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (card == null) {
                        Brush.verticalGradient(listOf(Color(0xFF5CB7F2), Color(0xFF1977C8)))
                    } else {
                        Brush.verticalGradient(listOf(Color.White, Color(0xFFFFF9EE)))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (card == null) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🐾", fontSize = 19.sp, color = Color.White)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        card.rankLabel,
                        fontWeight = FontWeight.Black,
                        color = if (red) BlackjackCardRed else Color.Black
                    )
                    Text(
                        card.suit.symbol,
                        fontSize = 22.sp,
                        color = if (red) BlackjackCardRed else Color.Black
                    )
                }
            }
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
    val palette = puppyCasinoCartoonPalette()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = activeScale
                scaleY = activeScale
            }
            .animateContentSize(animationSpec = tween(260)),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = if (active) 0.15f else 0.08f),
        border = BorderStroke(
            if (active) 3.dp else 1.dp,
            if (active) palette.gold else Color.White.copy(alpha = 0.20f)
        ),
        shadowElevation = if (active) 4.dp else 0.dp
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hand " + (index + 1),
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                Text(
                    hand.wagerTreats.toString() + " 🍪",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            BlackjackCardRow(
                cards = hand.cards,
                hideAfterFirst = false,
                animationsEnabled = animationsEnabled
            )
            Spacer(Modifier.height(6.dp))
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
                color = Color.White.copy(alpha = 0.85f),
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            value,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.End
        )
    }
}

private fun commandMessage(result: PuppyBlackjackCommandResult): String? {
    if (result.success) return null
    return when (result.failure) {
        PuppyBlackjackCommandFailure.INVALID_WAGER -> "Invalid Blackjack wager."
        PuppyBlackjackCommandFailure.TRANSACTION_REJECTED ->
            "Action blocked: " + (result.transactionFailure?.name ?: "transaction rejected")
        PuppyBlackjackCommandFailure.INVALID_SAVED_STATE ->
            "Saved Blackjack state failed validation."
        PuppyBlackjackCommandFailure.ACTION_NOT_ALLOWED ->
            "Action not allowed: " + (result.actionError?.name ?: "invalid action")
        PuppyBlackjackCommandFailure.OUTCOME_COMMIT_FAILED ->
            "Completed hand could not be committed. It remains saved for recovery."
        null -> "Blackjack action failed."
    }
}
