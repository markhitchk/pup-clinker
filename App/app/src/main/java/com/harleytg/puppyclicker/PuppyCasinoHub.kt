package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PuppyCasinoHub(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onBack: () -> Unit
) {
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val activeRound by vm.casinoRound.collectAsStateWithLifecycle()
    val casinoFlag = flags["puppy_casino"] ?: PuppyFeatureFlags.flag("puppy_casino")
    val slotsFlag = flags["casino_slots"] ?: PuppyFeatureFlags.flag("casino_slots")
    val rouletteFlag = flags["casino_roulette"] ?: PuppyFeatureFlags.flag("casino_roulette")
    val blackjackFlag = flags["casino_blackjack"] ?: PuppyFeatureFlags.flag("casino_blackjack")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("‹ Rewards", fontWeight = FontWeight.Bold)
        }

        Text(
            "Puppy Casino",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "Treat-based games using your existing Puppy Clicker economy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        CasinoWalletCard(state)

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("🎰 " + casinoFlag.statusLabel(), fontWeight = FontWeight.Black)
                Text(
                    "The hub is installed now, but new wagers stay locked until each game engine passes its rules and transaction tests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No chips or second wallet: every wager and payout uses Treats.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (activeRound != null) {
            Spacer(Modifier.height(12.dp))
            CasinoRecoveryCard(
                round = activeRound!!,
                vm = vm
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Games", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))

        CasinoGameCard(
            emoji = "🎰",
            title = "Puppy Slots",
            detail = "Weighted symbols, published payout table, and committed outcomes before animations.",
            flag = slotsFlag
        )

        Spacer(Modifier.height(8.dp))

        CasinoGameCard(
            emoji = "🎡",
            title = "Puppy Roulette",
            detail = "Single-zero table with red/black, odd/even, halves, and recorded bets before the spin.",
            flag = rouletteFlag
        )

        Spacer(Modifier.height(8.dp))

        CasinoGameCard(
            emoji = "🃏",
            title = "Puppy Blackjack",
            detail = "Dealer CPU first. Natural blackjack is planned at 3:2; multiplayer comes later with server authority.",
            flag = blackjackFlag
        )

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("🐶 Casino Rewards", fontWeight = FontWeight.Black)
                Text(
                    "Casino puppy rewards will map only to verified existing roster IDs. The casino will never create a second puppy inventory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Upgrade Ticket drops will use the existing Ticket rarities and inventory limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("🛡️ Fair Play", fontWeight = FontWeight.Black)
                Text(
                    "Every accepted round has one round ID, one wager, one committed outcome, and one settlement. Closing the app cannot create a new roll for the same wager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CasinoWalletCard(state: V6GameState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🍪", fontSize = 34.sp)
            Column(Modifier.weight(1f)) {
                Text("Treat Wallet", fontWeight = FontWeight.Black)
                Text(
                    state.treats.toString() + " Treats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Tickets", style = MaterialTheme.typography.labelSmall)
                Text(
                    "🎟️ " + state.ticketsOwned,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CasinoGameCard(
    emoji: String,
    title: String,
    detail: String,
    flag: PuppyFeatureFlag
) {
    val available = flag.isAvailable()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (available) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.padding(horizontal = 5.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    flag.statusLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (available) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text(if (available) "Not Ready" else "Locked")
            }
        }
    }
}

@Composable
private fun CasinoRecoveryCard(
    round: PuppyCasinoRound,
    vm: PuppyClickerV6ViewModel
) {
    val stateLabel = when (round.state) {
        PuppyCasinoRoundState.WAGER_ACCEPTED -> "Wager accepted"
        PuppyCasinoRoundState.OUTCOME_COMMITTED -> "Outcome committed"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Interrupted casino round", fontWeight = FontWeight.Black)
            Text(
                round.game.name.replace('_', ' ') + " · " + stateLabel,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Wager: " + round.wagerTreats + " Treats",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Round ID: " + round.roundId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            when (round.state) {
                PuppyCasinoRoundState.WAGER_ACCEPTED -> {
                    OutlinedButton(
                        onClick = { vm.refundCasinoRound(round.roundId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refund Accepted Wager")
                    }
                }
                PuppyCasinoRoundState.OUTCOME_COMMITTED -> {
                    Button(
                        onClick = { vm.settleCasinoRound(round.roundId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Complete Saved Settlement")
                    }
                }
            }
        }
    }
}
