package com.harleytg.puppyclicker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
    NO_ELIGIBLE_PUPPIES,
    PUPEYE_BLOCKED
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
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = 620)
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
                    animationSpec = tween(durationMillis = 640)
                )
                delay(300)
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
                PuppyGachaFailure.PUPEYE_BLOCKED ->
                    "Pupeye blocked this Gacha pull because protected progression needs Support review."
                null -> "The capsule machine could not complete the pull."
            }
        }
    }

    var showInfo by rememberSaveable { mutableStateOf(false) }

    if (showInfo) {
        PuppyGachaInfoDialog(onDismiss = { showInfo = false })
    }

    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 28.dp)
    ) {
        PuppyGachaHeader(
            onBack = onBack,
            onInfo = { showInfo = true }
        )

        Spacer(Modifier.height(8.dp))

        PuppyGachaWallet(
            treats = state.treats,
            commonTickets = commonTickets,
            modifier = Modifier.fillMaxWidth()
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
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PuppyGachaActionButton(
                title = "🍪 Pull with Treats",
                subtitle = "2,500 Treats",
                container = Color(0xFFFFB51B),
                border = Color(0xFFD89500),
                enabled = eligible.isNotEmpty() &&
                    state.treats >= PuppyGachaEngine.COST_TREATS &&
                    stage == 0,
                onClick = { startPull(PuppyGachaPayment.TREATS) },
                modifier = Modifier.weight(1f)
            )

            PuppyGachaActionButton(
                title = "🎟️ Use Common Ticket",
                subtitle = "1 Ticket",
                container = Color(0xFF7C3AED),
                border = Color(0xFF5B21B6),
                enabled = eligible.isNotEmpty() &&
                    commonTickets >= PuppyGachaEngine.COST_COMMON_TICKETS &&
                    stage == 0,
                onClick = { startPull(PuppyGachaPayment.COMMON_TICKET) },
                modifier = Modifier.weight(1f)
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

        Spacer(Modifier.height(10.dp))

        Text(
            when {
                eligible.isEmpty() -> "No puppies are currently eligible for Gacha."
                remaining.isEmpty() -> "Collection complete · no unowned Gacha puppies remain."
                remaining.size == 1 -> "1 new puppy remains · new puppies are guaranteed first."
                else -> remaining.size.toString() + " new puppies remain · new puppies are guaranteed first."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "Redeem-only and special locked puppies are excluded. Uses earned Treats or Common Upgrade Tickets only; no real money or cash-out.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun PuppyGachaHeader(
    onBack: () -> Unit,
    onInfo: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.height(44.dp),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
        ) {
            Text("← Back", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                "Puppy Gacha",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                "Turn · Drop · Open · Reveal!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = onInfo,
            modifier = Modifier.height(44.dp),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text("ⓘ  Gacha Info", fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun PuppyGachaWallet(
    treats: Long,
    commonTickets: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF8ADFF7)),
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.90f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PuppyGachaBalance(
                icon = "🍪",
                label = "Treats",
                value = treats.toString(),
                modifier = Modifier.weight(1f)
            )

            VerticalDivider(
                modifier = Modifier.height(42.dp),
                thickness = 2.dp,
                color = Color.White.copy(alpha = 0.86f)
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
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = Color(0xFF102331),
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFF081923),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PuppyGachaActionButton(
    title: String,
    subtitle: String,
    container: Color,
    border: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(2.dp, border),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White,
            disabledContainerColor = container.copy(alpha = 0.36f),
            disabledContentColor = Color.White.copy(alpha = 0.72f)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
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
    onOpenCapsule: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val sceneHeight = maxWidth * (1536f / 1024f)
        val density = LocalDensity.current

        /*
         * The machine artwork is a static asset with an empty dial.
         * The dial knob and capsules are separate image assets so only the
         * moving pieces animate; the machine itself is never redrawn.
         */
        Image(
            painter = painterResource(R.drawable.puppy_gacha_machine),
            contentDescription = "Puppy Gacha machine",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        val knobSize = maxWidth * 0.255f
        val knobCenterY = sceneHeight * (1002f / 1536f)

        Image(
            painter = painterResource(R.drawable.puppy_gacha_knob),
            contentDescription = "Gacha turning knob",
            modifier = Modifier
                .size(knobSize)
                .offset(
                    x = (maxWidth / 2f) - (knobSize / 2f),
                    y = knobCenterY - (knobSize / 2f)
                )
                .graphicsLayer {
                    rotationZ = knobRotation
                },
            contentScale = ContentScale.Fit
        )

        val capsuleSize = maxWidth * 0.205f
        val capsuleCenterY = sceneHeight * (1246f / 1536f)
        val capsuleLeft = (maxWidth / 2f) - (capsuleSize / 2f)
        val capsuleTop = capsuleCenterY - (capsuleSize / 2f)

        if (stage == 1 || stage == 2) {
            val startRotation = (-capsuleDrop / 58f).coerceIn(0f, 1f)

            PuppyClosedCapsule(
                modifier = Modifier
                    .size(capsuleSize)
                    .offset(x = capsuleLeft, y = capsuleTop)
                    .scale(trayScale)
                    .graphicsLayer {
                        translationY = with(density) { capsuleDrop.dp.toPx() }
                        rotationZ = -12f * startRotation
                    }
            )
        }

        if (stage == 3 || stage == 4) {
            val openSize = maxWidth * 0.305f
            val openCenterY = sceneHeight * (1236f / 1536f)

            PuppyOpeningCapsule(
                progress = openProgress,
                modifier = Modifier
                    .size(openSize)
                    .offset(
                        x = (maxWidth / 2f) - (openSize / 2f),
                        y = openCenterY - (openSize / 2f)
                    )
                    .scale(trayScale)
            )
        }

        if (stage == 2) {
            Button(
                onClick = onOpenCapsule,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
                    .height(42.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
            ) {
                Text("Open Capsule", fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PuppyOpeningCapsule(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val p = progress.coerceIn(0f, 1f)
    val tiltAlpha = when {
        p < 0.12f -> p / 0.12f
        p <= 0.62f -> 1f
        else -> ((1f - p) / 0.38f).coerceIn(0f, 1f)
    }
    val openAlpha = ((p - 0.42f) / 0.58f).coerceIn(0f, 1f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.puppy_gacha_capsule_open_tilt),
            contentDescription = "Capsule opening",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = tiltAlpha
                    scaleX = 0.90f + (p * 0.08f)
                    scaleY = 0.90f + (p * 0.08f)
                    rotationZ = -3f * p
                },
            contentScale = ContentScale.Fit
        )

        Image(
            painter = painterResource(R.drawable.puppy_gacha_capsule_open),
            contentDescription = "Open capsule",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = openAlpha
                    scaleX = 0.94f + (p * 0.06f)
                    scaleY = 0.94f + (p * 0.06f)
                    translationY = -with(density) { (6.dp * p).toPx() }
                },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun PuppyGachaInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
        title = {
            Text("Puppy Gacha", fontWeight = FontWeight.Black)
        },
        text = {
            Text(
                "Turn, drop, open, and reveal a puppy. New eligible puppies are guaranteed before duplicate eligible puppies. " +
                    "A pull costs 2,500 Treats or 1 Common Upgrade Ticket. Redeem-only and special locked puppies are excluded."
            )
        }
    )
}

@Composable
private fun PuppyGachaRevealDialog(
    pulled: PuppyGachaPullResult,
    onUsePuppy: () -> Unit,
    onAnother: () -> Unit,
    onClose: () -> Unit
) {
    val revealScale = remember { Animatable(0.84f) }

    LaunchedEffect(Unit
) {
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
    Image(
        painter = painterResource(R.drawable.puppy_gacha_capsule_closed),
        contentDescription = "Closed Puppy Gacha capsule",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
