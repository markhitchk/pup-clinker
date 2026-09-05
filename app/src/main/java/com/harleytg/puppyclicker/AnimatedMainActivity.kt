package com.harleytg.puppyclicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harleytg.puppyclicker.ui.theme.PuppyClickerTheme
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class AnimatedMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PuppyClickerTheme {
                val vm: GameViewModel = viewModel()
                AnimatedPuppyClickerApp(vm)
            }
        }
    }
}

private enum class AnimatedTab(val label: String, val emoji: String) {
    PLAY("Play", "🐾"), CARE("Care", "💖"), SHOP("Shop", "🛒"), PUP("My Pup", "🏆")
}

private data class TapFx(val id: Long, val amount: Long)

@Composable
private fun AnimatedPuppyClickerApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AnimatedTab.PLAY) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                tonalElevation = 2.dp,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
            ) {
                AnimatedTab.entries.forEach { item ->
                    val selected = tab == item
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.18f else 1f,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 500f),
                        label = "navIcon"
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item },
                        icon = {
                            Text(
                                item.emoji,
                                fontSize = 20.sp,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            )
                        },
                        label = {
                            Text(item.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.055f)
                        )
                    )
                )
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        initialOffsetX = { if (forward) it / 5 else -it / 5 }
                    ) + fadeIn(tween(220))
                    val exit = slideOutHorizontally(
                        animationSpec = tween(220),
                        targetOffsetX = { if (forward) -it / 8 else it / 8 }
                    ) + fadeOut(tween(160))
                    enter togetherWith exit
                },
                label = "tabTransition"
            ) { current ->
                when (current) {
                    AnimatedTab.PLAY -> AnimatedPlayScreen(state, viewModel)
                    AnimatedTab.CARE -> AnimatedCareScreen(state, viewModel)
                    AnimatedTab.SHOP -> AnimatedShopScreen(state, viewModel)
                    AnimatedTab.PUP -> AnimatedPupScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun AnimatedPlayScreen(state: GameState, viewModel: GameViewModel) {
    val today = LocalDate.now().toEpochDay()
    val dailyAvailable = state.lastDailyClaimDay != today
    val dailyReward = 100L + state.level * 25L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedGameHeader(state)
        Spacer(Modifier.height(10.dp))

        AnimatedVisibility(
            visible = state.offlineEarned > 0,
            enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::dismissOfflineBonus)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✨", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Welcome back · +${fmt(state.offlineEarned)} treats",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("×")
                }
            }
        }

        if (state.offlineEarned > 0) Spacer(Modifier.height(10.dp))
        AnimatedTreatHud(state)
        Spacer(Modifier.height(12.dp))
        AnimatedPuppyPlayground(state, viewModel)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedActionButton(
                emoji = "🍖",
                label = "Feed",
                detail = "${GameViewModel.FEED_COST} 🍪",
                enabled = state.treats >= GameViewModel.FEED_COST && state.fullness < 100,
                onClick = viewModel::feedPuppy,
                modifier = Modifier.weight(1f)
            )
            AnimatedActionButton(
                emoji = "🎾",
                label = "Play",
                detail = "+mood",
                enabled = state.energy >= 10,
                onClick = viewModel::playWithPuppy,
                modifier = Modifier.weight(1f)
            )
            AnimatedActionButton(
                emoji = "🎁",
                label = if (dailyAvailable) "Gift" else "Claimed",
                detail = if (dailyAvailable) "+${fmt(dailyReward)}" else "tomorrow",
                enabled = dailyAvailable,
                onClick = viewModel::claimDailyReward,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        AnimatedLevelProgress(state)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedInfoCard("🎯", "Missions", "${MISSIONS.count { it.complete(state) && it.id !in state.claimedMissions }} ready", Modifier.weight(1f))
            AnimatedInfoCard("🏆", "Best combo", state.bestCombo.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AnimatedGameHeader(state: GameState) {
    val infinite = rememberInfiniteTransition(label = "logoFloat")
    val logoTilt by infinite.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoTilt"
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)) {
            Image(
                painter = painterResource(R.drawable.source_logo),
                contentDescription = "Puppy Clicker logo",
                modifier = Modifier.padding(6.dp).size(54.dp).graphicsLayer { rotationZ = logoTilt },
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Puppy Clicker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            AnimatedContent(targetState = state.mood, label = "moodText") { mood ->
                Text(
                    "${state.puppyName} · $mood",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LVL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                AnimatedContent(targetState = state.level, label = "levelNumber") { level ->
                    Text(level.toString(), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun AnimatedTreatHud(state: GameState) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(state.treats) {
        pulse.snapTo(1.08f)
        pulse.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 480f))
    }

    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 1.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🍪", fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Treats", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedContent(targetState = state.treats, label = "treatCounter") { amount ->
                    Text(fmt(amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
            }
            HudStat("👆", fmt(state.clickPower.toLong()), "tap")
            Spacer(Modifier.width(12.dp))
            HudStat("⏱️", fmt(state.autoPerSecond.toLong()), "sec")
        }
    }
}

@Composable
private fun HudStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$emoji $value", fontWeight = FontWeight.Black)
        Text("per $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnimatedPuppyPlayground(state: GameState, viewModel: GameViewModel) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tapScale = remember { Animatable(1f) }
    val tapRotation = remember { Animatable(0f) }
    val effects = remember { mutableStateListOf<TapFx>() }
    var effectId by remember { mutableLongStateOf(0L) }

    val idle = rememberInfiniteTransition(label = "puppyIdle")
    val idleY by idle.animateFloat(
        initialValue = -3f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleY"
    )
    val idleRotation by idle.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idleRotation"
    )

    val comboPulse = rememberInfiniteTransition(label = "comboPulse")
    val comboScale by comboPulse.animateFloat(
        initialValue = 1f,
        targetValue = if (state.combo >= 5) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "comboScale"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().height(400.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.13f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
                    )
                )
            )
        ) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(14.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                AnimatedNeedPill("💖", state.happiness, Modifier.weight(1f))
                AnimatedNeedPill("🍖", state.fullness, Modifier.weight(1f))
                AnimatedNeedPill("⚡", state.energy, Modifier.weight(1f))
            }

            AnimatedVisibility(
                visible = state.combo >= 5,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 62.dp, end = 16.dp),
                enter = scaleIn(initialScale = 0.5f, animationSpec = spring()) + fadeIn(),
                exit = scaleOut(targetScale = 0.7f) + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f),
                    modifier = Modifier.graphicsLayer { scaleX = comboScale; scaleY = comboScale }
                ) {
                    Text(
                        "🔥 ${state.combo} · x${state.comboMultiplier}",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 8.dp)
                    .size(292.dp)
                    .graphicsLayer {
                        translationY = idleY
                        rotationZ = idleRotation + tapRotation.value
                        scaleX = tapScale.value
                        scaleY = tapScale.value
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                            )
                        )
                    )
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        val reward = state.clickPower.toLong() * max(1, state.comboMultiplier).toLong()
                        viewModel.tapPuppy()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        effectId += 1
                        val fx = TapFx(effectId, reward)
                        effects += fx
                        scope.launch {
                            tapScale.snapTo(0.88f)
                            tapRotation.snapTo(if (effectId % 2L == 0L) -4f else 4f)
                            launch { tapScale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 620f)) }
                            launch { tapRotation.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.source_pup),
                    contentDescription = state.puppyName,
                    modifier = Modifier.size(270.dp),
                    contentScale = ContentScale.Fit
                )
                when (state.accessory) {
                    "Bandana" -> Text("🧣", fontSize = 54.sp, modifier = Modifier.offset(y = 86.dp))
                    "Bow" -> Text("🎀", fontSize = 46.sp, modifier = Modifier.offset(y = (-92).dp))
                    "Crown" -> Text("👑", fontSize = 56.sp, modifier = Modifier.offset(y = (-104).dp))
                }
            }

            effects.takeLast(6).forEachIndexed { index, fx ->
                FloatingTreatEffect(
                    fx = fx,
                    lane = index,
                    onFinished = { effects.remove(fx) },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.91f)
            ) {
                AnimatedContent(targetState = state.combo, label = "tapHint") { combo ->
                    Text(
                        if (combo >= 5) "Keep it going! · x${state.comboMultiplier}" else "Tap ${state.puppyName} · +${fmt(state.clickPower.toLong())}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingTreatEffect(fx: TapFx, lane: Int, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val y = remember { Animatable(20f) }
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(0.75f) }

    LaunchedEffect(fx.id) {
        launch { y.animateTo(-150f, tween(750, easing = FastOutSlowInEasing)) }
        launch {
            scale.animateTo(1.18f, spring(dampingRatio = 0.5f, stiffness = 500f))
            scale.animateTo(0.96f, tween(350))
        }
        delay(330)
        alpha.animateTo(0f, tween(420))
        onFinished()
    }

    Text(
        "+${fmt(fx.amount)} 🍪",
        modifier = modifier.graphicsLayer {
            translationX = ((lane % 3) - 1) * 32f
            translationY = y.value
            this.alpha = alpha.value
            scaleX = scale.value
            scaleY = scale.value
        },
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AnimatedNeedPill(emoji: String, value: Int, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(value / 100f, tween(480, easing = FastOutSlowInEasing), label = "need")
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.87f)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Text("$value%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun AnimatedActionButton(
    emoji: String,
    label: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 650f),
        label = "actionScale"
    )
    val target = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
    val bg by animateColorAsState(target, tween(250), label = "actionColor")

    Surface(
        modifier = modifier.height(82.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = bg,
        tonalElevation = if (enabled) 1.dp else 0.dp
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(emoji, fontSize = 22.sp)
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnimatedLevelProgress(state: GameState) {
    val level = state.level
    val start = (level - 1L) * (level - 1L) * 100L
    val target = level.toLong() * level.toLong() * 100L
    val span = max(1L, target - start)
    val raw = ((state.lifetimeTreats - start).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(raw, tween(650, easing = FastOutSlowInEasing), label = "levelProgress")

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level $level progress", fontWeight = FontWeight.Bold)
                Text("${fmt(state.lifetimeTreats)} / ${fmt(target)}", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape))
        }
    }
}

@Composable
private fun AnimatedInfoCard(emoji: String, title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedContent(targetState = value, label = "infoValue") { current ->
                    Text(current, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun AnimatedCareScreen(state: GameState, viewModel: GameViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        AnimatedSectionHeader("Care for ${state.puppyName}", "Feed, play, rest, and explore together.")
        Spacer(Modifier.height(14.dp))

        val idle = rememberInfiniteTransition(label = "carePup")
        val pupY by idle.animateFloat(-2f, 4f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "careY")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)) {
                Image(
                    painter = painterResource(R.drawable.source_pup),
                    contentDescription = state.puppyName,
                    modifier = Modifier.size(118.dp).graphicsLayer { translationY = pupY },
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                AnimatedContent(targetState = state.mood, label = "careMood") { mood ->
                    Text(mood, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
                Text("Overall care ${state.careScore}%")
                Text("${state.careActions} care actions", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        AnimatedNeedCard("Happiness", "💖", state.happiness, "Play together to improve mood.")
        Spacer(Modifier.height(8.dp))
        AnimatedNeedCard("Fullness", "🍖", state.fullness, "Feed treats when your puppy gets hungry.")
        Spacer(Modifier.height(8.dp))
        AnimatedNeedCard("Energy", "⚡", state.energy, "Rest restores energy for adventures.")
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedActionButton("🍖", "Feed", "${GameViewModel.FEED_COST} 🍪", state.treats >= GameViewModel.FEED_COST && state.fullness < 100, viewModel::feedPuppy, Modifier.weight(1f))
            AnimatedActionButton("🎾", "Play", "+happy", state.energy >= 10, viewModel::playWithPuppy, Modifier.weight(1f))
            AnimatedActionButton("💤", "Rest", "+energy", state.energy < 100, viewModel::restPuppy, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        AnimatedParkCard(state, now, viewModel)
        Spacer(Modifier.height(18.dp))
        Text("Missions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        MISSIONS.forEach { mission ->
            val complete = mission.complete(state)
            val claimed = mission.id in state.claimedMissions
            Card(Modifier.fillMaxWidth().animateContentSize()) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (claimed) "✅" else if (complete) "🎯" else "📋", fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mission.title, fontWeight = FontWeight.Bold)
                        Text(mission.description, style = MaterialTheme.typography.bodySmall)
                        Text("Reward: ${fmt(mission.reward)} 🍪", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(onClick = { viewModel.claimMission(mission) }, enabled = complete && !claimed) { Text(if (claimed) "Done" else "Claim") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun AnimatedNeedCard(title: String, emoji: String, value: Int, description: String) {
    val progress by animateFloatAsState(value / 100f, tween(600, easing = FastOutSlowInEasing), label = "needCard")
    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$emoji $title", fontWeight = FontWeight.Bold)
                AnimatedContent(targetState = value, label = "needValue") { v -> Text("$v%", fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
            Spacer(Modifier.height(5.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnimatedParkCard(state: GameState, now: Long, viewModel: GameViewModel) {
    val remaining = ((state.parkReadyAtMs - now).coerceAtLeast(0) + 999) / 1000
    val ready = state.parkActive && remaining == 0L
    val infinite = rememberInfiniteTransition(label = "park")
    val treeScale by infinite.animateFloat(0.96f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "tree")

    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌳", fontSize = 34.sp, modifier = Modifier.graphicsLayer { scaleX = treeScale; scaleY = treeScale })
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Dog Park Adventure", fontWeight = FontWeight.Black)
                AnimatedContent(targetState = Triple(ready, state.parkActive, remaining), label = "parkState") { info ->
                    Text(
                        when {
                            info.first -> "Adventure complete! Reward ready."
                            info.second -> "${state.puppyName} returns in ${info.third}s"
                            else -> "Spend 20 energy on a 60-second adventure."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Button(
                onClick = if (ready) viewModel::claimParkAdventure else viewModel::startParkAdventure,
                enabled = ready || (!state.parkActive && state.energy >= 20)
            ) { Text(if (ready) "Claim" else if (state.parkActive) "Away" else "Go") }
        }
    }
}

@Composable
private fun AnimatedShopScreen(state: GameState, viewModel: GameViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        AnimatedSectionHeader("Puppy Shop", "Upgrade taps and automatic treat production.")
        Spacer(Modifier.height(12.dp))
        AnimatedTreatHud(state)
        Spacer(Modifier.height(12.dp))
        UPGRADES.forEach { upgrade ->
            val owned = state.upgrades[upgrade.id] ?: 0
            val cost = upgradeCost(upgrade, owned)
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(), label = "upgrade")
            Card(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.animateContentSize()) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(upgrade.emoji, fontSize = 32.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(upgrade.name, fontWeight = FontWeight.Bold)
                        Text(upgrade.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AnimatedContent(targetState = owned, label = "owned") { count -> Text("Owned: $count", style = MaterialTheme.typography.labelMedium) }
                    }
                    Button(
                        onClick = { viewModel.buyUpgrade(upgrade) },
                        enabled = state.treats >= cost,
                        interactionSource = interaction
                    ) { Text("${fmt(cost)} 🍪") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun AnimatedPupScreen(state: GameState, viewModel: GameViewModel) {
    var rename by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val idle = rememberInfiniteTransition(label = "profilePup")
    val rotation by idle.animateFloat(-1.5f, 1.5f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "profileRot")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
        AnimatedSectionHeader("My Pup", "Customize your puppy and track progress.")
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.source_pup),
                        contentDescription = state.puppyName,
                        modifier = Modifier.size(82.dp).graphicsLayer { rotationZ = rotation },
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.puppyName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        AnimatedContent(targetState = Pair(state.level, state.mood), label = "profileState") { info -> Text("Level ${info.first} • ${info.second}") }
                        Text("${fmt(state.lifetimeTreats)} lifetime treats", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { rename = true }) { Text("Rename") }
                }
                Spacer(Modifier.height(14.dp))
                Text("Accessory", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    GameViewModel.ACCESSORIES.forEach { accessory ->
                        FilterChip(
                            selected = state.accessory == accessory,
                            onClick = { viewModel.setAccessory(accessory) },
                            label = { Text(accessory) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Achievements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        ACHIEVEMENTS.forEach { achievement ->
            val unlocked = achievement.unlocked(state)
            val bg by animateColorAsState(
                if (unlocked) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                tween(350), label = "achievementColor"
            )
            Card(Modifier.fillMaxWidth().animateContentSize(), colors = CardDefaults.cardColors(containerColor = bg)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(targetState = unlocked, label = "achievementIcon") { open ->
                        Text(if (open) achievement.emoji else "🔒", fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(achievement.title, fontWeight = FontWeight.Bold)
                        Text(achievement.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { reset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset game") }
        Spacer(Modifier.height(18.dp))
    }

    if (rename) {
        var name by rememberSaveable { mutableStateOf(state.puppyName) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename puppy") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(18) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.renamePuppy(name); rename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } }
        )
    }
    if (reset) {
        AlertDialog(
            onDismissRequest = { reset = false },
            title = { Text("Reset Puppy Clicker?") },
            text = { Text("This permanently resets game progress.") },
            confirmButton = { TextButton(onClick = { viewModel.resetGame(); reset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AnimatedSectionHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun fmt(value: Long): String = when {
    value >= 1_000_000_000_000L -> "%.2fT".format(value / 1_000_000_000_000.0)
    value >= 1_000_000_000L -> "%.2fB".format(value / 1_000_000_000.0)
    value >= 1_000_000L -> "%.2fM".format(value / 1_000_000.0)
    value >= 1_000L -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}
