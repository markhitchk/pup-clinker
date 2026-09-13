package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
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
    val canPlayFeature = PuppyCasinoFeaturePolicy.canStartNewRound(
        game = PuppyCasinoGame.SLOTS,
        flags = flags
    )

    var wager by rememberSaveable { mutableLongStateOf(100L) }
    var lastOutcomePayload by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOutcomeWager by rememberSaveable { mutableLongStateOf(0L) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

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
        delay(900)

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
                    PuppySlotsEngine.PUBLISHED_RTP_PERCENT + " RTP",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val display = recoveredOutcome ?: lastOutcome

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(PuppySlotsEngine.REEL_COUNT) { index ->
                        SlotReel(
                            emoji = display?.symbols?.getOrNull(index)?.emoji ?: "?",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (display != null) {
                    val resultText = when (display.winKind) {
                        PuppySlotsWinKind.LOSS -> "No match"
                        PuppySlotsWinKind.PAIR -> "Pair · " + display.multiplierLabel
                        PuppySlotsWinKind.TRIPLE -> "Triple · " + display.multiplierLabel
                    }
                    Text(
                        resultText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        display.payoutTreats.toString() + " Treats returned",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (slotsRound?.state == PuppyCasinoRoundState.WAGER_ACCEPTED) {
                    Text(
                        "Wager accepted. Outcome not committed.",
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        "Choose a wager and spin.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

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
                        Text("Refund " + slotsRound.wagerTreats + " Treats")
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
            "Multipliers are the total Treat return, including the original wager.",
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
            onClick = {
                message = null
                val result = vm.startSlotsSpin(wager)
                if (!result.success) {
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
            },
            enabled =
                canPlayFeature &&
                    activeRound == null &&
                    PuppySlotsEngine.isValidWager(wager) &&
                    state.treats >= wager,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !canPlayFeature -> "Slots Locked"
                    activeRound != null -> "Round In Progress"
                    state.treats < wager -> "Not Enough Treats"
                    else -> "Spin for " + wager + " Treats"
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        SlotsRulesCard()

        Spacer(Modifier.height(16.dp))
        SlotsPayoutTable()

        Spacer(Modifier.height(16.dp))
        SlotsWeightTable()

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SlotReel(
    emoji: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                emoji,
                fontSize = 42.sp,
                textAlign = TextAlign.Center
            )
        }
    }
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
                "• No matching pair returns 0 Treats.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "• The Ticket symbol is visual in Slots; this engine pays Treats only.",
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
