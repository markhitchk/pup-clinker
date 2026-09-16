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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class PuppyGachaFailure {
    NOT_ENOUGH_TREATS,
    COLLECTION_COMPLETE,
    NO_ELIGIBLE_PUPPIES
}

internal data class PuppyGachaPullResult(
    val success: Boolean,
    val puppyId: String? = null,
    val puppyName: String? = null,
    val puppyEmoji: String = "🐶",
    val costTreats: Long = PuppyGachaEngine.COST_TREATS,
    val failure: PuppyGachaFailure? = null
)

internal object PuppyGachaEngine {
    const val COST_TREATS = 2_500L

    fun eligiblePuppies(
        styles: List<PuppyStyle>,
        unlocked: Set<String>
    ): List<PuppyStyle> = styles
        .asSequence()
        .distinctBy { it.id }
        .filter { !it.redeemOnly }
        .filter { it.id !in unlocked }
        .sortedBy { it.id }
        .toList()

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
    val remaining = remember(allStyles, state.unlockedPuppies) {
        PuppyGachaEngine.eligiblePuppies(allStyles, state.unlockedPuppies)
    }

    var stage by rememberSaveable { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<PuppyGachaPullResult?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val machineScale by animateFloatAsState(
        targetValue = if (stage == 1) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "gacha-machine-scale"
    )
    val capsuleRotation by animateFloatAsState(
        targetValue = if (stage == 1) 10f else 0f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 380f),
        label = "gacha-capsule-rotation"
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Puppy Gacha",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Capsule machine · collect a new puppy every pull.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🍪", fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Treat balance", fontWeight = FontWeight.Black)
                    Text(
                        formatNumber(state.treats),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        PuppyGachaEngine.COST_TREATS.toString() + " Treats",
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "per capsule",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        PuppyGachaMachine(
            stage = stage,
            scale = machineScale,
            capsuleRotation = capsuleRotation
        )

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    stage == 2 && result?.success == true -> {
                        val pulled = result!!
                        Text("✨ Capsule opened!", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Spacer(Modifier.height(12.dp))
                        pulled.puppyId?.let { id ->
                            StreamedPuppyPortrait(
                                styleId = id,
                                size = 170.dp,
                                accessory = "None",
                                unlocked = true,
                                background = MaterialTheme.colorScheme.surface
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            pulled.puppyEmoji + " " + (pulled.puppyName ?: "New Puppy"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Added to your Puppy Roster.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pulled.puppyId?.let(vm::setPuppyStyle) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Use Puppy")
                            }
                            Button(
                                onClick = {
                                    stage = 0
                                    result = null
                                    message = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Another")
                            }
                        }
                    }

                    stage == 1 && result?.success == true -> {
                        Text("A capsule dropped!", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Spacer(Modifier.height(8.dp))
                        PuppyClosedCapsule(Modifier.size(126.dp).rotate(capsuleRotation))
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { stage = 2 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Capsule")
                        }
                    }

                    remaining.isEmpty() -> {
                        Text("🏆 Collection complete", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "You already own every puppy currently eligible for Puppy Gacha. New eligible roster puppies will appear here automatically.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    else -> {
                        Text(
                            remaining.size.toString() + " puppies still available",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Every successful capsule unlocks one puppy you do not already own. There are no duplicate pulls.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val pulled = vm.pullPuppyGacha()
                                result = pulled
                                if (pulled.success) {
                                    stage = 1
                                    message = null
                                } else {
                                    stage = 0
                                    message = when (pulled.failure) {
                                        PuppyGachaFailure.NOT_ENOUGH_TREATS ->
                                            "You need " + PuppyGachaEngine.COST_TREATS + " Treats for a capsule."
                                        PuppyGachaFailure.COLLECTION_COMPLETE,
                                        PuppyGachaFailure.NO_ELIGIBLE_PUPPIES ->
                                            "No eligible puppies are available right now."
                                        null -> "The capsule machine could not complete the pull."
                                    }
                                }
                            },
                            enabled = state.treats >= PuppyGachaEngine.COST_TREATS,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Dispense Capsule · " + PuppyGachaEngine.COST_TREATS + " Treats")
                        }
                    }
                }

                message?.let {
                    Spacer(Modifier.height(10.dp))
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
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Capsule rules", fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(
                    "• Uses earned Treats only\n• Every pull is a new eligible puppy\n• Redeem-only and special locked puppies are excluded\n• No real-money purchase or cash-out",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PUPPY GACHA", fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                shape = RoundedCornerShape(26.dp),
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
                    PuppyMiniCapsule(Modifier.align(Alignment.TopStart).padding(22.dp))
                    PuppyMiniCapsule(Modifier.align(Alignment.TopCenter).padding(top = 30.dp))
                    PuppyMiniCapsule(Modifier.align(Alignment.TopEnd).padding(24.dp))
                    PuppyMiniCapsule(Modifier.align(Alignment.CenterStart).padding(36.dp))
                    PuppyMiniCapsule(Modifier.align(Alignment.Center))
                    PuppyMiniCapsule(Modifier.align(Alignment.CenterEnd).padding(38.dp))
                    Text("🐾", modifier = Modifier.align(Alignment.Center), fontSize = 42.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface)
            ) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (stage == 1) "↻" else "●",
                        fontSize = 30.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(0.62f).height(54.dp),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (stage == 1) {
                        PuppyClosedCapsule(Modifier.size(42.dp).rotate(capsuleRotation))
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
        modifier = modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🐾", fontSize = 15.sp)
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
            Text("🐾", fontSize = 38.sp)
        }
    }
}
