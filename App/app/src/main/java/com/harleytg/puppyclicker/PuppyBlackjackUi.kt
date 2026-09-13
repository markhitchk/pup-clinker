package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private val BlackjackTableGreen = Color(0xFF0E5B3A)
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
            delay(900)

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
            .padding(horizontal = 16.dp, vertical = 10.dp)
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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = BlackjackTableGreen,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Dealer",
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(6.dp))
                BlackjackCardRow(
                    cards = displayState?.dealerCards.orEmpty(),
                    hideAfterFirst = displayState?.complete == false
                )

                if (displayState != null && displayState.complete) {
                    val dealerValue = PuppyBlackjackEngine.handValue(displayState.dealerCards)
                    Text(
                        "Dealer total: " + dealerValue.total,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Your hand" + if (displayState != null && displayState.hands.size > 1) {
                        "s"
                    } else {
                        ""
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(6.dp))

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
                            active = !displayState.complete &&
                                index == displayState.activeHandIndex,
                            outcome = displayOutcome?.hands?.getOrNull(index)
                        )
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
                Text(text, modifier = Modifier.padding(12.dp))
            }
        }

        if (
            blackjackRound?.state == PuppyCasinoRoundState.WAGER_ACCEPTED &&
            roundState != null &&
            !roundState.complete
        ) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                "Current hand wager: " + (activeHand?.wagerTreats ?: 0L) + " Treats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        message = commandMessage(vm.blackjackHit())
                    },
                    enabled = PuppyBlackjackEngine.canHit(roundState),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Hit")
                }
                Button(
                    onClick = {
                        message = commandMessage(vm.blackjackStand())
                    },
                    enabled = PuppyBlackjackEngine.canStand(roundState),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stand")
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        message = commandMessage(vm.blackjackDouble())
                    },
                    enabled =
                        PuppyBlackjackEngine.canDouble(roundState) &&
                            activeHand != null &&
                            state.treats >= activeHand.wagerTreats,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Double")
                }
                OutlinedButton(
                    onClick = {
                        message = commandMessage(vm.blackjackSplit())
                    },
                    enabled =
                        PuppyBlackjackEngine.canSplit(roundState) &&
                            activeHand != null &&
                            state.treats >= activeHand.wagerTreats,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Split")
                }
            }
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BlackjackWalletCard(state: V6GameState) {
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
                "CPU Dealer",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BlackjackCardRow(
    cards: List<Int>,
    hideAfterFirst: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        cards.forEachIndexed { index, id ->
            BlackjackPlayingCard(
                card = if (hideAfterFirst && index > 0) null else PuppyBlackjackCard(id)
            )
        }
    }
}

@Composable
private fun BlackjackPlayingCard(card: PuppyBlackjackCard?) {
    val red = card != null &&
        card.suit in setOf(PuppyBlackjackSuit.DIAMONDS, PuppyBlackjackSuit.HEARTS)

    Surface(
        modifier = Modifier.width(54.dp).height(72.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (card == null) MaterialTheme.colorScheme.primary else Color.White,
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (card == null) {
                Text("🐾", fontSize = 24.sp, color = Color.White)
            } else {
                Text(
                    card.rankLabel,
                    fontWeight = FontWeight.Black,
                    color = if (red) BlackjackCardRed else Color.Black
                )
                Text(
                    card.suit.symbol,
                    fontSize = 20.sp,
                    color = if (red) BlackjackCardRed else Color.Black
                )
            }
        }
    }
}

@Composable
private fun BlackjackHandCard(
    index: Int,
    hand: PuppyBlackjackHand,
    active: Boolean,
    outcome: PuppyBlackjackHandOutcome?
) {
    val value = PuppyBlackjackEngine.handValue(hand.cards)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = if (active) 0.18f else 0.09f),
        border = BorderStroke(
            if (active) 2.dp else 1.dp,
            Color.White.copy(alpha = if (active) 0.8f else 0.22f)
        )
    ) {
        Column(Modifier.padding(10.dp)) {
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
            BlackjackCardRow(cards = hand.cards, hideAfterFirst = false)
            Spacer(Modifier.height(6.dp))
            Text(
                "Total " + value.total +
                    if (value.soft) " · soft" else "",
                color = Color.White
            )
            val status = when {
                outcome != null ->
                    outcome.result.name + " · " + outcome.payoutTreats + " returned"
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
