package com.harleytg.puppyclicker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

internal enum class PuppyGachaPayment {
    TREATS,
    COMMON_TICKET
}

internal enum class PuppyGachaFailure {
    NOT_ENOUGH_TREATS,
    NOT_ENOUGH_TICKETS,
    NO_ELIGIBLE_PUPPIES
}

internal data class PuppyGachaPullResult(
    val success: Boolean,
    val puppyId: String? = null,
    val puppyName: String? = null,
    val puppyEmoji: String = "🐶",
    val costTreats: Long = 0L,
    val costTickets: Int = 0,
    val payment: PuppyGachaPayment = PuppyGachaPayment.TREATS,
    val isNewUnlock: Boolean = true,
    val failure: PuppyGachaFailure? = null
)

internal object PuppyGachaEngine {
    const val COST_TREATS = 2_500L
    const val COST_COMMON_TICKETS = 1

    fun allEligiblePuppies(styles: List<PuppyStyle>): List<PuppyStyle> = styles
        .asSequence()
        .distinctBy { it.id }
        .filter { !it.redeemOnly }
        .sortedBy { it.id }
        .toList()

    fun eligiblePuppies(
        styles: List<PuppyStyle>,
        unlocked: Set<String>
    ): List<PuppyStyle> = allEligiblePuppies(styles)
        .filter { it.id !in unlocked }

    fun pullPool(
        styles: List<PuppyStyle>,
        unlocked: Set<String>
    ): List<PuppyStyle> {
        val unowned = eligiblePuppies(styles, unlocked)
        return if (unowned.isNotEmpty()) {
            unowned
        } else {
            allEligiblePuppies(styles).filter { it.id in unlocked }
        }
    }

    internal fun select(candidates: List<PuppyStyle>, roll: Int): PuppyStyle? {
        if (candidates.isEmpty()) return null
        return candidates[Math.floorMod(roll, candidates.size)]
    }
}

@Composable
internal fun PuppyGachaScreen(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    onBack: () -> Unit
) {
    val groups by DynamicPuppyRoster.groups.collectAsState()
    val allStyles = remember(groups) { groups.flatMap { it.puppies } }
    val eligible = remember(allStyles) { PuppyGachaEngine.allEligiblePuppies(allStyles) }
    val remaining = remember(eligible, state.unlockedPuppies) {
        eligible.filter { it.id !in state.unlockedPuppies }
    }
    val commonTickets = state.ticketInventory[TicketRarity.COMMON] ?: 0

    var stage by rememberSaveable { mutableIntStateOf(0) }
    var paymentIndex by rememberSaveable { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<PuppyGachaPullResult?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val payment = if (paymentIndex == 0) {
        PuppyGachaPayment.TREATS
    } else {
        PuppyGachaPayment.COMMON_TICKET
    }
    val canAfford = when (payment) {
        PuppyGachaPayment.TREATS -> state.treats >= PuppyGachaEngine.COST_TREATS
        PuppyGachaPayment.COMMON_TICKET ->
            commonTickets >= PuppyGachaEngine.COST_COMMON_TICKETS
    }

    val machineScale by animateFloatAsState(
        targetValue = if (stage == 1) 1.025f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "gacha-machine-scale"
    )
    val capsuleRotation by animateFloatAsState(
        targetValue = if (stage == 1) 10f else 0f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 380f),
        label = "gacha-capsule-rotation"
    )

    if (stage == 2 && result?.success == true) {
        val pulled = result!!
        PuppyGachaRevealDialog(
            pulled = pulled,
            onUsePuppy = {
                pulled.puppyId?.let(vm::setPuppyStyle)
                stage = 0
                result = null
                message = null
            },
            onAnother = {
                stage = 0
                result = null
                message = null
            },
            onClose = {
                stage = 0
                result = null
                message = null
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(42.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                Text("← Back")
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Puppy Gacha",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Capsules reveal roster puppies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PuppyGachaBalance(
                    icon = "🍪",
                    label = "Treats",
                    value = state.treats.toString(),
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    modifier = Modifier.height(42.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                PuppyGachaBalance(
                    icon = "🎟️",
                    label = "Common tickets",
                    value = commonTickets.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                if (stage == 1 && result?.success == true) {
                    Text(
                        "Capsule ready",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Open it to reveal the puppy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { stage = 2 },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Open Capsule")
                    }
                } else {
                    Text("Pay with", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (payment == PuppyGachaPayment.TREATS) {
                            Button(
                                onClick = { paymentIndex = 0 },
                                modifier = Modifier.weight(1f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("🍪 2,500 Treats", maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { paymentIndex = 0 },
                                modifier = Modifier.weight(1f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("🍪 2,500 Treats", maxLines = 1)
                            }
                        }

                        if (payment == PuppyGachaPayment.COMMON_TICKET) {
                            Button(
                                onClick = { paymentIndex = 1 },
                                modifier = Modifier.weight(1f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("🎟️ 1 Ticket", maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { paymentIndex = 1 },
                                modifier = Modifier.weight(1f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("🎟️ 1 Ticket", maxLines = 1)
                            }
                        }
                    }

                    Spacer(Modifier.height(7.dp))
                    Text(
                        when {
                            eligible.isEmpty() -> "No puppies are currently eligible for Gacha."
                            remaining.isEmpty() ->
                                "Collection complete · pulls can reveal an owned eligible puppy."
                            remaining.size == 1 -> "1 new puppy remains · new puppies are guaranteed first."
                            else -> remaining.size.toString() + " new puppies remain · new puppies are guaranteed first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    val buttonText = when (payment) {
                        PuppyGachaPayment.TREATS -> "Dispense · 2,500 Treats"
                        PuppyGachaPayment.COMMON_TICKET -> "Dispense · 1 Common Ticket"
                    }
                    Button(
                        onClick = {
                            val pulled = vm.pullPuppyGacha(payment)
                            result = pulled
                            if (pulled.success) {
                                stage = 1
                                message = null
                            } else {
                                stage = 0
                                message = when (pulled.failure) {
                                    PuppyGachaFailure.NOT_ENOUGH_TREATS ->
                                        "You need " + PuppyGachaEngine.COST_TREATS + " Treats."
                                    PuppyGachaFailure.NOT_ENOUGH_TICKETS ->
                                        "You need " + PuppyGachaEngine.COST_COMMON_TICKETS + " Common Ticket."
                                    PuppyGachaFailure.NO_ELIGIBLE_PUPPIES ->
                                        "No eligible puppies are available right now."
                                    null -> "The capsule machine could not complete the pull."
                                }
                            }
                        },
                        enabled = eligible.isNotEmpty() && canAfford,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(buttonText)
                    }
                }

                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            it,
                            modifier = Modifier.padding(9.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        PuppyGachaMachine(
            stage = stage,
            scale = machineScale,
            capsuleRotation = capsuleRotation
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Redeem-only and special locked puppies are excluded. Gacha uses earned in-game Treats or Common Upgrade Tickets only; no real money or cash-out.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun PuppyGachaBalance(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PuppyGachaRevealDialog(
    pulled: PuppyGachaPullResult,
    onUsePuppy: () -> Unit,
    onAnother: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "✨ CAPSULE OPENED! ✨",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (pulled.isNewUnlock) "New puppy unlocked" else "Puppy revealed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        pulled.puppyId?.let { id ->
                            StreamedPuppyPortrait(
                                styleId = id,
                                size = 190.dp,
                                accessory = "None",
                                unlocked = true,
                                background = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    pulled.puppyEmoji + " " + (pulled.puppyName ?: "Puppy"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (pulled.isNewUnlock) "Added to your Puppy Roster."
                    else "Already in your Puppy Roster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onUsePuppy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Use Puppy")
                }
                Spacer(Modifier.height(7.dp))
                OutlinedButton(
                    onClick = onAnother,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Another Capsule")
                }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}

@Composable
private fun PuppyGachaMachine(
    stage: Int,
    scale: Float,
    capsuleRotation: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth().scale(scale),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(128.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                                )
                            )
                        )
                ) {
                    PuppyMiniCapsule(Modifier.align(Alignment.TopStart).padding(15.dp))
                    PuppyMiniCapsule(Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
                    PuppyMiniCapsule(Modifier.align(Alignment.TopEnd).padding(16.dp))
                    PuppyMiniCapsule(Modifier.align(Alignment.BottomStart).padding(18.dp))
                    PuppyMiniCapsule(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
                    PuppyMiniCapsule(Modifier.align(Alignment.BottomEnd).padding(19.dp))
                    Text("🐾", modifier = Modifier.align(Alignment.Center), fontSize = 34.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(3.dp, MaterialTheme.colorScheme.surface)
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (stage == 1) "↻" else "●",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(0.56f).height(44.dp),
                shape = RoundedCornerShape(bottomStart = 17.dp, bottomEnd = 17.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (stage == 1) {
                        PuppyClosedCapsule(Modifier.size(36.dp).rotate(capsuleRotation))
                    } else {
                        Text(
                            "CAPSULE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PuppyMiniCapsule(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(34.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🐾", fontSize = 12.sp)
        }
    }
}

@Composable
private fun PuppyClosedCapsule(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("🐾", fontSize = 30.sp)
        }
    }
}
