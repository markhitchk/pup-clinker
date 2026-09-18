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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    ): List<PuppyStyle> = allEligiblePuppies(styles).filter { it.id !in unlocked }

    fun pullPool(
        styles: List<PuppyStyle>,
        unlocked: Set<String>
    ): List<PuppyStyle> = eligiblePuppies(styles, unlocked)

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
    var payment by rememberSaveable { mutableStateOf(PuppyGachaPayment.TREATS) }

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
                    "Collection complete · duplicate puppy pulls are disabled."
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (payment == PuppyGachaPayment.TREATS) {
                Button(
                    onClick = { payment = PuppyGachaPayment.TREATS },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("🍪 Treats", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { payment = PuppyGachaPayment.TREATS },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("🍪 Treats", fontWeight = FontWeight.Bold)
                }
            }

            if (payment == PuppyGachaPayment.COMMON_TICKET) {
                Button(
                    onClick = { payment = PuppyGachaPayment.COMMON_TICKET },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("🎟 Ticket", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { payment = PuppyGachaPayment.COMMON_TICKET },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("🎟 Ticket", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = { startPull(payment) },
            enabled = remaining.isNotEmpty() &&
                stage == 0 &&
                when (payment) {
                    PuppyGachaPayment.TREATS -> state.treats >= PuppyGachaEngine.COST_TREATS
                    PuppyGachaPayment.COMMON_TICKET ->
                        commonTickets >= PuppyGachaEngine.COST_COMMON_TICKETS
                },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(19.dp)
        ) {
            Text(
                when (payment) {
                    PuppyGachaPayment.TREATS -> "Pull Capsule • 2,500 Treats"
                    PuppyGachaPayment.COMMON_TICKET -> "Pull Capsule • 1 Ticket"
                },
                fontWeight = FontWeight.Black
            )
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
        targetValue = if (stage == 1) 1.012f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 390f),
        label = "mechanical-gacha-scale"
    )

    PuppyCasinoRenderStage(
        title = "PUPPY GACHA",
        height = 330.dp,
        accent = Color(0xFF00B8F0),
        modifier = Modifier.scale(machineScale)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .offset(y = 40.dp)
        ) {
            PuppyGachaDome()
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .offset(y = 173.dp)
        ) {
            PuppyGachaBody(
                stage = stage,
                knobRotation = knobRotation,
                capsuleDrop = capsuleDrop,
                trayScale = trayScale,
                openProgress = openProgress,
                onOpenCapsule = onOpenCapsule
            )
        }
    }
}

@Composable
private fun PuppyGachaBody(
    stage: Int,
    knobRotation: Float,
    capsuleDrop: Float,
    trayScale: Float,
    openProgress: Float,
    onOpenCapsule: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(136.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF11B6E9),
        shadowElevation = 0.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                "TURN\nPULL\nPUPPY!",
                modifier = Modifier.align(Alignment.TopStart).padding(start = 17.dp, top = 17.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color(0xFF074255),
                lineHeight = 15.sp
            )

            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 13.dp)
            ) {
                PuppyGachaKnob(knobRotation)
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 23.dp, top = 18.dp)
                    .size(width = 62.dp, height = 58.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFE7F9FD),
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🐾", color = Color(0xFF087EAE), fontSize = 21.sp)
                }
            }

            Button(
                onClick = onOpenCapsule,
                enabled = stage == 2,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 15.dp)
                    .width(192.dp)
                    .height(36.dp)
                    .scale(trayScale),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEAFBFE),
                    contentColor = Color(0xFF07526B),
                    disabledContainerColor = Color(0xFFEAFBFE),
                    disabledContentColor = Color(0xFF07526B)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                when (stage) {
                    1 -> PuppyClosedCapsule(
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { translationY = capsuleDrop }
                    )
                    2 -> Text("OPEN CAPSULE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    3, 4 -> PuppyOpeningCapsule(progress = openProgress, modifier = Modifier.size(30.dp))
                    else -> Text("CAPSULE TRAY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun PuppyGachaDome() {
    val capsuleColors = listOf(
        Color(0xFFBCEAF7),
        Color(0xFFE4D6FF),
        Color(0xFFFFD9E4),
        Color(0xFFC9F0E2),
        Color(0xFFBDE7F5),
        Color(0xFFF4D5E9)
    )

    Surface(
        modifier = Modifier.fillMaxWidth().height(138.dp),
        shape = RoundedCornerShape(58.dp),
        color = Color(0xFFDDF8FF),
        border = BorderStroke(2.dp, Color(0xFF79DCF7)),
        shadowElevation = 0.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(48.dp))
                .background(Color(0xFFCDEFF6))
        ) {
            PuppyMiniMechanicalCapsule(
                capsuleColors[0],
                Modifier.align(Alignment.TopStart).offset(x = 12.dp, y = 2.dp)
            )
            PuppyMiniMechanicalCapsule(
                capsuleColors[1],
                Modifier.align(Alignment.TopCenter).offset(y = -8.dp)
            )
            PuppyMiniMechanicalCapsule(
                capsuleColors[2],
                Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = 2.dp)
            )
            PuppyMiniMechanicalCapsule(
                capsuleColors[3],
                Modifier.align(Alignment.BottomStart).offset(x = 44.dp, y = (-2).dp)
            )
            PuppyMiniMechanicalCapsule(
                capsuleColors[4],
                Modifier.align(Alignment.BottomCenter).offset(x = 20.dp, y = (-4).dp)
            )
            PuppyMiniMechanicalCapsule(
                capsuleColors[5],
                Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-2).dp)
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
        modifier = modifier.size(42.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(1.dp, Color(0xFF7CCFE6)),
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🐾", fontSize = 12.sp, color = Color(0xFF1685B5))
        }
    }
}

@Composable
private fun PuppyGachaKnob(rotation: Float) {
    Surface(
        modifier = Modifier.size(70.dp),
        shape = CircleShape,
        color = Color(0xFFE7F9FD),
        border = BorderStroke(2.dp, Color(0xFF79DCF7)),
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .width(14.dp)
                    .height(50.dp)
                    .graphicsLayer { rotationZ = rotation },
                shape = RoundedCornerShape(7.dp),
                color = Color(0xFF00B8F0),
                shadowElevation = 0.dp
            ) {}
        }
    }
}

@Composable
private fun PuppyGachaChute(stage: Int) {
    val palette = puppyCasinoCartoonPalette()
    Surface(
        modifier = Modifier.width(126.dp).height(47.dp),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        color = palette.cream,
        border = BorderStroke(2.dp, palette.woodLight.copy(alpha = 0.55f)),
        shadowElevation = 5.dp
    ) {
        Box(
            Modifier.background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.5f), Color.Transparent, palette.shadow.copy(alpha = 0.08f))
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (stage == 1) "⬇ CAPSULE" else "CAPSULE CHUTE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = palette.woodDark
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
    val palette = puppyCasinoCartoonPalette()
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .height(132.dp)
            .scale(trayScale),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 30.dp, bottomEnd = 30.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(3.dp, palette.cream.copy(alpha = 0.95f)),
        shadowElevation = 7.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.40f), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "CAPSULE TRAY",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("🐾", modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), fontSize = 24.sp)
            Text("🐾", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), fontSize = 24.sp)

            when (stage) {
                0 -> Text(
                    "Choose Treats or a Ticket",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                1 -> PuppyClosedCapsule(
                    modifier = Modifier
                        .padding(top = 13.dp)
                        .size(64.dp)
                        .graphicsLayer {
                            translationY = capsuleDrop
                            rotationZ = -12f + (capsuleDrop / -58f) * 12f
                        }
                )

                2 -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 14.dp)
                ) {
                    PuppyClosedCapsule(Modifier.size(58.dp))
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

                3, 4 -> PuppyOpeningCapsule(
                    progress = openProgress,
                    modifier = Modifier.padding(top = 14.dp)
                )
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
        modifier = modifier.size(width = 128.dp, height = 82.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "✨",
            modifier = Modifier.align(Alignment.Center).scale(0.65f + progress * 1.15f),
            fontSize = 34.sp
        )
        Text(
            "★",
            modifier = Modifier.align(Alignment.TopStart).graphicsLayer {
                translationX = -8f * progress
                translationY = -8f * progress
                alpha = progress
            },
            color = MaterialTheme.colorScheme.primary,
            fontSize = 17.sp
        )
        Text(
            "★",
            modifier = Modifier.align(Alignment.TopEnd).graphicsLayer {
                translationX = 8f * progress
                translationY = -10f * progress
                alpha = progress
            },
            color = Color(0xFFFFC83D),
            fontSize = 15.sp
        )

        PuppyCapsuleHalf(
            color = Color(0xFF75D3FF),
            topHalf = true,
            modifier = Modifier.align(Alignment.Center).graphicsLayer {
                translationY = -18f * progress
                rotationZ = -18f * progress
            }
        )
        PuppyCapsuleHalf(
            color = Color(0xFFFFA8C8),
            topHalf = false,
            modifier = Modifier.align(Alignment.Center).graphicsLayer {
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
        modifier = modifier.width(72.dp).height(32.dp),
        shape = if (topHalf) {
            RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 7.dp, bottomEnd = 7.dp)
        } else {
            RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 36.dp, bottomEnd = 36.dp)
        },
        color = color,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.72f)),
        shadowElevation = 3.dp
    ) {
        Box(
            Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            Text("🐾", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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
        revealScale.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 420f))
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier.fillMaxWidth().scale(revealScale.value).animateContentSize(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
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
                        Text("✨       ✨", modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp), fontSize = 18.sp)
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
                    if (pulled.isNewUnlock) "Added to your Puppy Roster." else "Already in your Puppy Roster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onUsePuppy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Use Puppy") }
                Spacer(Modifier.height(7.dp))
                OutlinedButton(
                    onClick = onAnother,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Another Capsule") }
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
        color = Color(0xFF75D3FF),
        border = BorderStroke(3.dp, Color.White.copy(alpha = 0.76f)),
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.52f), Color(0xFF75D3FF), Color(0xFFFFA8C8))
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.8f)))
            Text("🐾", fontSize = 27.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
