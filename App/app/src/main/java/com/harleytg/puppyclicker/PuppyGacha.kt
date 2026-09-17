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
    ): List<PuppyStyle> = allEligiblePuppies(styles, unlocked)

    private fun allEligiblePuppies(styles: List<PuppyStyle>, unlocked: Set<String>): List<PuppyStyle> =
        allEligiblePuppies(styles).filter { it.id !in unlocked }

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
    val palette = puppyCasinoCartoonPalette()
    val machineScale by animateFloatAsState(
        targetValue = if (stage == 1) 1.012f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 390f),
        label = "mechanical-gacha-scale"
    )

    CartoonStageFrame(
        modifier = Modifier.fillMaxWidth().scale(machineScale),
        accent = MaterialTheme.colorScheme.primary,
        background = Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                MaterialTheme.colorScheme.surface,
                palette.woodLight.copy(alpha = 0.12f)
            )
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 58.dp, end = 12.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🐶", fontSize = 42.sp, modifier = Modifier.offset(y = 8.dp))
            CartoonBonePlaque("PUPPY GACHA")
            Spacer(Modifier.height(8.dp))
            PuppyGachaDome()
            Spacer(Modifier.height(0.dp))
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
    val palette = puppyCasinoCartoonPalette()
    Surface(
        modifier = Modifier.fillMaxWidth(0.92f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 34.dp, bottomEnd = 34.dp),
        color = MaterialTheme.colorScheme.primary,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)),
        shadowElevation = 9.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.23f),
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Surface(
                    modifier = Modifier.width(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = palette.cream,
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.55f)),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        "TURN\nPULL\nPUPPY!",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = palette.woodDark,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )
                }

                PuppyGachaKnob(knobRotation)

                Surface(
                    modifier = Modifier.size(62.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = palette.cream,
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.55f)),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🐾", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
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

@Composable
private fun PuppyGachaDome() {
    val palette = puppyCasinoCartoonPalette()
    val capsuleColors = listOf(
        Color(0xFF70CFFF),
        Color(0xFFFF9DC4),
        Color(0xFFA9E9D0),
        Color(0xFFC7AEFF),
        Color(0xFF8EDDEB),
        Color(0xFFFFB6D0),
        Color(0xFF88D6FF),
        Color(0xFFBDEED8),
        Color(0xFFD2BEFF)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(0.88f).height(205.dp),
        shape = RoundedCornerShape(topStart = 84.dp, topEnd = 84.dp, bottomStart = 34.dp, bottomEnd = 34.dp),
        color = palette.glass,
        border = BorderStroke(5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)),
        shadowElevation = 7.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 80.dp, topEnd = 80.dp, bottomStart = 30.dp, bottomEnd = 30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.80f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        )
                    )
                )
        ) {
            Box(
                Modifier
                    .width(34.dp)
                    .height(132.dp)
                    .align(Alignment.TopStart)
                    .offset(x = 24.dp, y = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.52f))
            )
            PuppyMiniMechanicalCapsule(capsuleColors[0], Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 62.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[1], Modifier.align(Alignment.TopCenter).padding(top = 45.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[2], Modifier.align(Alignment.TopEnd).padding(end = 28.dp, top = 66.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[3], Modifier.align(Alignment.CenterStart).padding(start = 18.dp, top = 26.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[4], Modifier.align(Alignment.Center).padding(top = 18.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[5], Modifier.align(Alignment.CenterEnd).padding(end = 18.dp, top = 25.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[6], Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 12.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[7], Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[8], Modifier.align(Alignment.BottomEnd).padding(end = 49.dp, bottom = 12.dp))
        }
    }
}

@Composable
private fun PuppyMiniMechanicalCapsule(
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(51.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.72f)),
        shadowElevation = 4.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.44f), Color.Transparent, Color.Black.copy(alpha = 0.07f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.7f))
            )
            Text("🐾", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PuppyGachaKnob(rotation: Float) {
    val palette = puppyCasinoCartoonPalette()
    Surface(
        modifier = Modifier.size(88.dp),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(5.dp, palette.cream),
        shadowElevation = 9.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp),
                shape = CircleShape,
                color = palette.cream,
                border = BorderStroke(2.dp, palette.woodLight.copy(alpha = 0.45f))
            ) {}
            Surface(
                modifier = Modifier
                    .width(21.dp)
                    .height(58.dp)
                    .graphicsLayer { rotationZ = rotation },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 3.dp
            ) {
                Box(
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.32f), Color.Transparent, Color.Black.copy(alpha = 0.08f))
                        )
                    )
                )
            }
            Text(
                "↻",
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = palette.woodDark
            )
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
