package com.harleytg.puppyclicker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
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
        targetValue = if (stage == 1) 1.008f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 390f),
        label = "mechanical-gacha-scale"
    )

    CartoonStageFrame(
        modifier = Modifier.fillMaxWidth().scale(machineScale),
        accent = Color(0xFF12AEEA),
        background = Brush.verticalGradient(
            listOf(
                Color(0xFFDDF4FF),
                Color(0xFFF7FCFF),
                palette.cream.copy(alpha = 0.72f)
            )
        )
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val paw = Color(0xFF2E9ED7).copy(alpha = 0.10f)
                val r = size.minDimension * 0.035f
                fun pawAt(x: Float, y: Float) {
                    drawCircle(paw, r, Offset(x, y))
                    drawCircle(paw, r * 0.42f, Offset(x - r * 0.78f, y - r * 0.92f))
                    drawCircle(paw, r * 0.42f, Offset(x - r * 0.20f, y - r * 1.22f))
                    drawCircle(paw, r * 0.42f, Offset(x + r * 0.42f, y - r * 1.15f))
                    drawCircle(paw, r * 0.42f, Offset(x + r * 0.90f, y - r * 0.72f))
                }
                pawAt(size.width * 0.10f, size.height * 0.16f)
                pawAt(size.width * 0.90f, size.height * 0.20f)
                pawAt(size.width * 0.12f, size.height * 0.79f)
                pawAt(size.width * 0.88f, size.height * 0.76f)
            }

            Column(
                Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PuppyGachaMascot()
                Box(
                    modifier = Modifier.offset(y = (-14).dp),
                    contentAlignment = Alignment.Center
                ) {
                    CartoonBonePlaque("PUPPY GACHA")
                }

                Box(Modifier.offset(y = (-18).dp)) {
                    PuppyGachaDome()
                }

                Box(Modifier.offset(y = (-20).dp)) {
                    PuppyGachaBody(
                        stage = stage,
                        knobRotation = knobRotation
                    )
                }

                Box(Modifier.offset(y = (-18).dp)) {
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
private fun PuppyGachaMascot() {
    Canvas(
        modifier = Modifier
            .width(196.dp)
            .height(92.dp)
    ) {
        val centerX = size.width / 2f
        val headCenter = Offset(centerX, size.height * 0.47f)
        val headRadius = size.height * 0.33f
        val brown = Color(0xFFB86E3D)
        val darkBrown = Color(0xFF6D3823)
        val cream = Color(0xFFFFE8C8)
        val white = Color(0xFFFFF9EE)
        val pink = Color(0xFFFF8E9D)

        drawOval(
            color = brown,
            topLeft = Offset(centerX - headRadius * 1.55f, headCenter.y - headRadius * 0.72f),
            size = Size(headRadius * 0.95f, headRadius * 1.45f)
        )
        drawOval(
            color = brown,
            topLeft = Offset(centerX + headRadius * 0.60f, headCenter.y - headRadius * 0.72f),
            size = Size(headRadius * 0.95f, headRadius * 1.45f)
        )
        drawCircle(cream, headRadius, headCenter)
        drawOval(
            color = white,
            topLeft = Offset(centerX - headRadius * 0.22f, headCenter.y - headRadius),
            size = Size(headRadius * 0.45f, headRadius * 1.30f)
        )

        val eyeY = headCenter.y - headRadius * 0.16f
        drawArc(
            color = darkBrown,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(centerX - headRadius * 0.56f, eyeY - 4f),
            size = Size(headRadius * 0.28f, headRadius * 0.18f),
            style = Stroke(width = 4f)
        )
        drawArc(
            color = darkBrown,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(centerX + headRadius * 0.28f, eyeY - 4f),
            size = Size(headRadius * 0.28f, headRadius * 0.18f),
            style = Stroke(width = 4f)
        )

        drawOval(
            color = white,
            topLeft = Offset(centerX - headRadius * 0.48f, headCenter.y + headRadius * 0.06f),
            size = Size(headRadius * 0.96f, headRadius * 0.62f)
        )
        drawOval(
            color = darkBrown,
            topLeft = Offset(centerX - headRadius * 0.14f, headCenter.y + headRadius * 0.03f),
            size = Size(headRadius * 0.28f, headRadius * 0.20f)
        )
        drawArc(
            color = darkBrown,
            startAngle = 8f,
            sweepAngle = 164f,
            useCenter = false,
            topLeft = Offset(centerX - headRadius * 0.30f, headCenter.y + headRadius * 0.16f),
            size = Size(headRadius * 0.60f, headRadius * 0.42f),
            style = Stroke(width = 3.5f)
        )
        drawOval(
            color = pink,
            topLeft = Offset(centerX - headRadius * 0.12f, headCenter.y + headRadius * 0.39f),
            size = Size(headRadius * 0.24f, headRadius * 0.20f)
        )

        val pawY = size.height * 0.84f
        drawOval(
            color = cream,
            topLeft = Offset(centerX - headRadius * 0.92f, pawY - headRadius * 0.24f),
            size = Size(headRadius * 0.64f, headRadius * 0.48f)
        )
        drawOval(
            color = cream,
            topLeft = Offset(centerX + headRadius * 0.28f, pawY - headRadius * 0.24f),
            size = Size(headRadius * 0.64f, headRadius * 0.48f)
        )
        drawCircle(Color(0xFFFFD23F), 5f, Offset(centerX - headRadius * 1.18f, size.height * 0.34f))
        drawCircle(Color(0xFFFFD23F), 4f, Offset(centerX + headRadius * 1.20f, size.height * 0.30f))
    }
}

@Composable
private fun PuppyGachaBody(
    stage: Int,
    knobRotation: Float
) {
    val palette = puppyCasinoCartoonPalette()

    Surface(
        modifier = Modifier.fillMaxWidth(0.88f),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 34.dp, bottomEnd = 34.dp),
        color = Color(0xFF10A9E8),
        border = BorderStroke(3.dp, Color(0xFF087EB9)),
        shadowElevation = 12.dp
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF66D8FF),
                            Color(0xFF18B4F0),
                            Color(0xFF0497D8)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(size.width * 0.06f, size.height * 0.06f),
                    size = Size(size.width * 0.88f, size.height * 0.18f),
                    cornerRadius = CornerRadius(22f, 22f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = 18f,
                    center = Offset(size.width * 0.09f, size.height * 0.75f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = 18f,
                    center = Offset(size.width * 0.91f, size.height * 0.75f)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Surface(
                        modifier = Modifier.width(88.dp).height(82.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFEFFAFF),
                        border = BorderStroke(2.dp, Color(0xFF8DD8F5)),
                        shadowElevation = 5.dp
                    ) {
                        Box(
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(Color.White, Color(0xFFE3F7FF))
                                )
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "TURN\nPULL\nPUPPY!",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF162735),
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    PuppyGachaKnob(knobRotation)

                    Surface(
                        modifier = Modifier.size(74.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFEFFAFF),
                        border = BorderStroke(2.dp, Color(0xFF8DD8F5)),
                        shadowElevation = 5.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PuppyPawMark(
                                color = Color(0xFF119FD7),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                PuppyGachaChute(stage)
            }
        }
    }
}

@Composable
private fun PuppyGachaDome() {
    val capsuleColors = listOf(
        Color(0xFF57C8FF),
        Color(0xFFFF8EB9),
        Color(0xFF7ADFC2),
        Color(0xFFC5A1FF),
        Color(0xFF56D7EA),
        Color(0xFFFFA2C6),
        Color(0xFF4FC3F7),
        Color(0xFF8FE0C9),
        Color(0xFFC7A8F5)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(0.82f).height(222.dp),
        shape = RoundedCornerShape(topStart = 96.dp, topEnd = 96.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
        color = Color(0xFFDFF5FF).copy(alpha = 0.78f),
        border = BorderStroke(5.dp, Color(0xFF159ED8)),
        shadowElevation = 10.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 92.dp, topEnd = 92.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.94f),
                            Color(0xFFCDEEFF).copy(alpha = 0.72f),
                            Color(0xFF93D8F5).copy(alpha = 0.55f)
                        )
                    )
                )
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawOval(
                    color = Color.White.copy(alpha = 0.58f),
                    topLeft = Offset(size.width * 0.08f, size.height * 0.09f),
                    size = Size(size.width * 0.08f, size.height * 0.53f)
                )
                drawOval(
                    color = Color.White.copy(alpha = 0.32f),
                    topLeft = Offset(size.width * 0.80f, size.height * 0.13f),
                    size = Size(size.width * 0.055f, size.height * 0.34f)
                )
            }

            PuppyMiniMechanicalCapsule(capsuleColors[0], Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 72.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[1], Modifier.align(Alignment.TopCenter).padding(top = 42.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[2], Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 76.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[3], Modifier.align(Alignment.CenterStart).padding(start = 14.dp, top = 24.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[4], Modifier.align(Alignment.Center).padding(top = 24.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[5], Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 30.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[6], Modifier.align(Alignment.BottomStart).padding(start = 42.dp, bottom = 8.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[7], Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp))
            PuppyMiniMechanicalCapsule(capsuleColors[8], Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 10.dp))
        }
    }
}

@Composable
private fun PuppyMiniMechanicalCapsule(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(60.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.44f

        drawCircle(
            color = Color.Black.copy(alpha = 0.12f),
            radius = radius,
            center = Offset(center.x + 2.5f, center.y + 4f)
        )
        drawCircle(color = color, radius = radius, center = center)
        drawArc(
            color = Color.White.copy(alpha = 0.72f),
            startAngle = 205f,
            sweepAngle = 115f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.72f, center.y - radius * 0.80f),
            size = Size(radius * 1.10f, radius * 0.88f),
            style = Stroke(width = 5f)
        )
        drawLine(
            color = Color.White.copy(alpha = 0.76f),
            start = Offset(center.x - radius * 0.86f, center.y),
            end = Offset(center.x + radius * 0.86f, center.y),
            strokeWidth = 3f
        )

        val paw = Color(0xFF0E96D0)
        drawCircle(paw, radius * 0.18f, Offset(center.x, center.y + radius * 0.08f))
        drawCircle(paw, radius * 0.075f, Offset(center.x - radius * 0.24f, center.y - radius * 0.17f))
        drawCircle(paw, radius * 0.075f, Offset(center.x - radius * 0.07f, center.y - radius * 0.27f))
        drawCircle(paw, radius * 0.075f, Offset(center.x + radius * 0.10f, center.y - radius * 0.25f))
        drawCircle(paw, radius * 0.075f, Offset(center.x + radius * 0.25f, center.y - radius * 0.13f))
    }
}

@Composable
private fun PuppyGachaKnob(rotation: Float) {
    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension * 0.47f
            drawCircle(Color.Black.copy(alpha = 0.15f), outer, Offset(center.x + 2f, center.y + 5f))
            drawCircle(Color(0xFFF8FAFC), outer, center)
            drawCircle(Color(0xFFD9E3EA), outer * 0.86f, center)
            drawCircle(Color.White, outer * 0.70f, center)
            drawArc(
                color = Color(0xFF44BCEB),
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(center.x - outer * 0.68f, center.y - outer * 0.68f),
                size = Size(outer * 1.36f, outer * 1.36f),
                style = Stroke(width = 5f)
            )
        }

        Surface(
            modifier = Modifier
                .width(24.dp)
                .height(64.dp)
                .graphicsLayer { rotationZ = rotation },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF12AEEA),
            border = BorderStroke(2.dp, Color(0xFF087EB9)),
            shadowElevation = 4.dp
        ) {
            Box(
                Modifier.background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.44f), Color.Transparent, Color.Black.copy(alpha = 0.08f))
                    )
                )
            )
        }

        Text(
            "↙",
            modifier = Modifier.align(Alignment.CenterStart),
            color = Color(0xFF159ED8),
            fontWeight = FontWeight.Black
        )
        Text(
            "↘",
            modifier = Modifier.align(Alignment.CenterEnd),
            color = Color(0xFF159ED8),
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun PuppyPawMark(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension
        drawOval(
            color = color,
            topLeft = Offset(c.x - r * 0.18f, c.y - r * 0.02f),
            size = Size(r * 0.36f, r * 0.30f)
        )
        drawCircle(color, r * 0.075f, Offset(c.x - r * 0.22f, c.y - r * 0.20f))
        drawCircle(color, r * 0.075f, Offset(c.x - r * 0.07f, c.y - r * 0.28f))
        drawCircle(color, r * 0.075f, Offset(c.x + r * 0.09f, c.y - r * 0.27f))
        drawCircle(color, r * 0.075f, Offset(c.x + r * 0.23f, c.y - r * 0.18f))
    }
}

@Composable
private fun PuppyGachaChute(stage: Int) {
    Surface(
        modifier = Modifier.width(176.dp).height(96.dp),
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
        color = Color(0xFFDDEBF2),
        border = BorderStroke(3.dp, Color(0xFFB7CCD7)),
        shadowElevation = 7.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.92f), Color(0xFFCDE3EC))
                    )
                )
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .width(112.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFAEDDF0).copy(alpha = 0.55f),
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.86f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (stage <= 1) {
                        PuppyClosedCapsule(Modifier.size(48.dp))
                    } else {
                        PuppyPawMark(
                            color = Color(0xFF159ED8).copy(alpha = 0.22f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .width(142.dp)
                    .height(28.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFFF4E2),
                border = BorderStroke(1.dp, Color(0xFFD4B891))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "CAPSULE CHUTE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF3A332D)
                    )
                }
            }
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
            .fillMaxWidth(0.96f)
            .height(126.dp)
            .scale(trayScale),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFFF8FCFE),
        border = BorderStroke(2.dp, Color(0xFFD6E7EF)),
        shadowElevation = 8.dp
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White,
                            Color(0xFFF3FBFF),
                            Color(0xFFEFF8FD)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "CAPSULE TRAY",
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF64727B)
            )

            PuppyPawMark(
                color = Color(0xFF159ED8).copy(alpha = 0.12f),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(34.dp)
            )
            PuppyPawMark(
                color = Color(0xFF159ED8).copy(alpha = 0.12f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(34.dp)
            )

            when (stage) {
                0 -> Text(
                    "Choose Treats or a Ticket",
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                1 -> PuppyClosedCapsule(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .size(66.dp)
                        .graphicsLayer {
                            translationY = capsuleDrop
                            rotationZ = -16f + (capsuleDrop / -58f) * 16f
                        }
                )

                2 -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    PuppyClosedCapsule(Modifier.size(60.dp))
                    Button(
                        onClick = onOpenCapsule,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
                    ) {
                        Text("Open Capsule", fontWeight = FontWeight.Bold)
                    }
                }

                3, 4 -> PuppyOpeningCapsule(
                    progress = openProgress,
                    modifier = Modifier.padding(top = 15.dp)
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
