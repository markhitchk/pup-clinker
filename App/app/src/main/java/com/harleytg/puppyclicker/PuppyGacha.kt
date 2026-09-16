package com.harleytg.puppyclicker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

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
    var result by remember { mutableStateOf<PuppyGachaPullResult?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val knobRotation = remember { Animatable(0f) }
    val capsuleDrop = remember { Animatable(-58f) }
    val trayBounce = remember { Animatable(1f) }
    val openProgress = remember { Animatable(0f) }

    LaunchedEffect(stage) {
        when (stage) {
            1 -> {
                knobRotation.snapTo(0f)
                capsuleDrop.snapTo(-58f)
                trayBounce.snapTo(1f)
                openProgress.snapTo(0f)

                knobRotation.animateTo(
                    targetValue = 300f,
                    animationSpec = tween(durationMillis = 520)
                )
                capsuleDrop.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.46f, stiffness = 340f)
                )
                trayBounce.animateTo(1.08f, tween(90))
                trayBounce.animateTo(
                    1f,
                    spring(dampingRatio = 0.38f, stiffness = 420f)
                )
                stage = 2
            }

            3 -> {
                openProgress.snapTo(0f)
                openProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 560)
                )
                delay(180)
                stage = 4
            }
        }
    }

    if (stage == 4 && result?.success == true) {
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

    fun startPull(payment: PuppyGachaPayment) {
        if (stage != 0) return
        val pulled = vm.pullPuppyGacha(payment)
        result = pulled
        if (pulled.success) {
            message = null
            stage = 1
        } else {
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
                    "Turn · drop · open · reveal!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        PuppyGachaWallet(
            treats = state.treats,
            commonTickets = commonTickets
        )

        Spacer(Modifier.height(10.dp))

        PuppyMechanicalGachaMachine(
            stage = stage,
            knobRotation = knobRotation.value,
            capsuleDrop = capsuleDrop.value,
            trayScale = trayBounce.value,
            openProgress = openProgress.value,
            onOpenCapsule = {
                if (stage == 2) stage = 3
            }
        )

        Spacer(Modifier.height(10.dp))

        Text(
            when {
                eligible.isEmpty() -> "No puppies are currently eligible for Gacha."
                remaining.isEmpty() ->
                    "Collection complete · future pulls reveal an owned eligible puppy."
                remaining.size == 1 -> "1 new puppy remains · new puppies are guaranteed first."
                else -> remaining.size.toString() + " new puppies remain · new puppies are guaranteed first."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { startPull(PuppyGachaPayment.TREATS) },
                enabled = eligible.isNotEmpty() &&
                    state.treats >= PuppyGachaEngine.COST_TREATS &&
                    stage == 0,
                modifier = Modifier.weight(1f).height(58.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🍪 Pull with Treats", fontWeight = FontWeight.Black, maxLines = 1)
                    Text("2,500 Treats", style = MaterialTheme.typography.labelMedium)
                }
            }

            Button(
                onClick = { startPull(PuppyGachaPayment.COMMON_TICKET) },
                enabled = eligible.isNotEmpty() &&
                    commonTickets >= PuppyGachaEngine.COST_COMMON_TICKETS &&
                    stage == 0,
                modifier = Modifier.weight(1f).height(58.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎟️ Common Ticket", fontWeight = FontWeight.Black, maxLines = 1)
                    Text("1 Ticket", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(10.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Redeem-only and special locked puppies are excluded. Uses earned Treats or Common Upgrade Tickets only; no real money or cash-out.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun PuppyGachaWallet(
    treats: Long,
    commonTickets: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PuppyGachaBalance(
                icon = "🍪",
                label = "Treats",
                value = treats.toString(),
                modifier = Modifier.weight(1f)
            )
            VerticalDivider(
                modifier = Modifier.height(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            PuppyGachaBalance(
                icon = "🎟️",
                label = "Common Tickets",
                value = commonTickets.toString(),
                modifier = Modifier.weight(1f)
            )
        }
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
        modifier = modifier.padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 23.sp)
        Spacer(Modifier.width(7.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun PuppyMechanicalGachaMachine(
    stage: Int,
    knobRotation: Float,
    capsuleDrop: Float,
    trayScale: Float,
    openProgress: Float,
    onOpenCapsule: () -> Unit
) {
    val machineScale by animateFloatAsState(
        targetValue = if (stage == 1) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "mechanical-gacha-scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth().scale(machineScale),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    "🐾  PUPPY GACHA  🐾",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            PuppyGachaDome()

            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(
                    topStart = 10.dp,
                    topEnd = 10.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp
                ),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                border = BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Surface(
                            modifier = Modifier.width(80.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                        ) {
                            Text(
                                "TURN\nPULL\nPUPPY!",
                                modifier = Modifier.padding(7.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp
                            )
                        }

                        PuppyGachaKnob(knobRotation)

                        Surface(
                            modifier = Modifier.size(58.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🐾", fontSize = 28.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    PuppyGachaChute(stage)

                    Spacer(Modifier.height(6.dp))

                    PuppyGachaTray(
                        stage = stage,
                        capsuleDrop = capsuleDrop,
                        trayScale = trayScale,
                        openProgress = openProgress,
                        onOpenCapsule = onOpenCapsule
                    )
                }
            }
        }
    }
}

@Composable
private fun PuppyGachaDome() {
    val capsuleColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(0.88f).height(164.dp),
        shape = RoundedCornerShape(
            topStart = 70.dp,
            topEnd = 70.dp,
            bottomStart = 28.dp,
            bottomEnd = 28.dp
        ),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        border = BorderStroke(
            3.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    )
                )
        ) {
            PuppyMiniMechanicalCapsule(
                color = capsuleColors[0],
                modifier = Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 26.dp)
            )
            PuppyMiniMechanicalCapsule(
                color = capsuleColors[1],
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
            )
            PuppyMiniMechanicalCapsule(
                color = capsuleColors[2],
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 30.dp, top = 27.dp)
            )
            PuppyMiniMechanicalCapsule(
                color = capsuleColors[3],
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 17.dp, top = 20.dp)
            )
            PuppyMiniMechanicalCapsule(
                color = capsuleColors[4],
                modifier = Modifier.align(Alignment.Center)
            )
            PuppyMiniMechanicalCapsule(
                color = capsuleColors[5],
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 17.dp, top = 18.dp)
            )
            PuppyMiniMechanicalCapsule(
                color = capsuleColors[2],
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 47.dp, bottom = 13.dp)
            )
            PuppyMiniMechanicalCapsule(
                color = capsuleColors[0],
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
            PuppyMiniMechanicalCapsule(
                color = capsuleColors[1],
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun PuppyMiniMechanicalCapsule(
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Text("🐾", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PuppyGachaKnob(rotation: Float) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer { rotationZ = rotation },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            4.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.width(18.dp).height(50.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {}
            Text(
                "↻",
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PuppyGachaChute(stage: Int) {
    Surface(
        modifier = Modifier.width(104.dp).height(38.dp),
        shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = 8.dp,
            bottomEnd = 8.dp
        ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (stage == 1) "⬇ CAPSULE" else "CAPSULE CHUTE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun PuppyGachaTray(
    stage: Int,
    capsuleDrop: Float,
    trayScale: Float,
    openProgress: Float,
    onOpenCapsule: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .height(118.dp)
            .scale(trayScale),
        shape = RoundedCornerShape(
            topStart = 14.dp,
            topEnd = 14.dp,
            bottomStart = 26.dp,
            bottomEnd = 26.dp
        ),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        ),
        shadowElevation = 3.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "CAPSULE TRAY",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (stage) {
                0 -> {
                    Text(
                        "Choose Treats or a Ticket",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                1 -> {
                    PuppyClosedCapsule(
                        modifier = Modifier
                            .padding(top = 13.dp)
                            .size(58.dp)
                            .graphicsLayer {
                                translationY = capsuleDrop
                                rotationZ = -12f + (capsuleDrop / -58f) * 12f
                            }
                    )
                }

                2 -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 13.dp)
                    ) {
                        PuppyClosedCapsule(Modifier.size(54.dp))
                        Spacer(Modifier.height(3.dp))
                        Button(
                            onClick = onOpenCapsule,
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
                        ) {
                            Text("Open Capsule", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                3, 4 -> {
                    PuppyOpeningCapsule(
                        progress = openProgress,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PuppyOpeningCapsule(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(width = 120.dp, height = 78.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "✨",
            modifier = Modifier
                .align(Alignment.Center)
                .scale(0.65f + progress * 1.15f),
            fontSize = 32.sp
        )
        Text(
            "★",
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer {
                    translationX = -8f * progress
                    translationY = -8f * progress
                    alpha = progress
                },
            color = MaterialTheme.colorScheme.primary,
            fontSize = 17.sp
        )
        Text(
            "★",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationX = 8f * progress
                    translationY = -10f * progress
                    alpha = progress
                },
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 15.sp
        )

        PuppyCapsuleHalf(
            color = MaterialTheme.colorScheme.primaryContainer,
            topHalf = true,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationY = -18f * progress
                    rotationZ = -18f * progress
                }
        )
        PuppyCapsuleHalf(
            color = MaterialTheme.colorScheme.secondaryContainer,
            topHalf = false,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationY = 18f * progress
                    rotationZ = 18f * progress
                }
        )
    }
}

@Composable
private fun PuppyCapsuleHalf(
    color: Color,
    topHalf: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(70.dp).height(31.dp),
        shape = if (topHalf) {
            RoundedCornerShape(
                topStart = 34.dp,
                topEnd = 34.dp,
                bottomStart = 7.dp,
                bottomEnd = 7.dp
            )
        } else {
            RoundedCornerShape(
                topStart = 7.dp,
                topEnd = 7.dp,
                bottomStart = 34.dp,
                bottomEnd = 34.dp
            )
        },
        color = color,
        border = BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🐾", fontSize = 11.sp)
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
    val revealScale = remember { Animatable(0.84f) }

    LaunchedEffect(Unit) {
        revealScale.animateTo(
            1f,
            spring(dampingRatio = 0.48f, stiffness = 420f)
        )
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(revealScale.value)
                .animateContentSize(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "✨  CAPSULE OPENED!  ✨",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (pulled.isNewUnlock) "NEW PUPPY!" else "PUPPY REVEALED!",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(10.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✨       ✨",
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp),
                            fontSize = 18.sp
                        )
                        pulled.puppyId?.let { id ->
                            StreamedPuppyPortrait(
                                styleId = id,
                                size = 188.dp,
                                accessory = "None",
                                unlocked = true,
                                background = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }

                Spacer(Modifier.height(9.dp))
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
private fun PuppyClosedCapsule(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            )
            Text("🐾", fontSize = 27.sp)
        }
    }
}
