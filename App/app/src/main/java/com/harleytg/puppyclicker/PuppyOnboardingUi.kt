package com.harleytg.puppyclicker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harleytg.puppyclicker.ui.theme.LocalPuppyAnimatedUi
import com.harleytg.puppyclicker.ui.theme.LocalPuppyReducedMotion

@Composable
internal fun PuppyOnboardingFlow(vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    val ui by PuppyUiPreferences.observe(context).collectAsStateWithLifecycle()
    val animateUi = LocalPuppyAnimatedUi.current && !LocalPuppyReducedMotion.current

    var step by rememberSaveable {
        mutableIntStateOf(PuppyOnboardingStep.WELCOME.persistedIndex)
    }
    var selectedMethodName by rememberSaveable {
        mutableStateOf(PuppyPlayerSetupMethod.LOCAL.name)
    }
    var importedSave by rememberSaveable { mutableStateOf(false) }
    var birthdaySkipped by rememberSaveable { mutableStateOf(false) }

    val selectedMethod = runCatching {
        PuppyPlayerSetupMethod.valueOf(selectedMethodName)
    }.getOrDefault(PuppyPlayerSetupMethod.LOCAL)

    val session = PuppyOnboardingSessionState(
        playerSetupMethod = selectedMethod,
        importedSave = importedSave,
        birthdaySkipped = birthdaySkipped
    )

    fun updateSession(next: PuppyOnboardingSessionState) {
        selectedMethodName = next.playerSetupMethod.name
        importedSave = next.importedSave
        birthdaySkipped = next.birthdaySkipped
    }

    LaunchedEffect(step) {
        PuppyUiPreferences.setSetupStep(context, step)
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val duration = if (animateUi) 160 else 1
            fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
        },
        label = "onboarding-step"
    ) { currentStep ->
        when (currentStep) {
            PuppyOnboardingStep.WELCOME.persistedIndex -> {
                PuppyOnboardingWelcome(
                    onNext = { step = PuppyOnboardingStep.PLAYER_SETUP.persistedIndex }
                )
            }

            PuppyOnboardingStep.PLAYER_SETUP.persistedIndex -> {
                PuppyOnboardingPlayerSetup(
                    vm = vm,
                    session = session,
                    onSessionChange = ::updateSession,
                    onBack = { step = PuppyOnboardingStep.WELCOME.persistedIndex },
                    onComplete = { step = PuppyOnboardingStep.PERSONALIZE.persistedIndex }
                )
            }

            PuppyOnboardingStep.PERSONALIZE.persistedIndex -> {
                PuppyOnboardingPersonalize(
                    vm = vm,
                    ui = ui,
                    session = session,
                    onSessionChange = ::updateSession,
                    onBack = { step = PuppyOnboardingStep.PLAYER_SETUP.persistedIndex },
                    onComplete = { step = PuppyOnboardingStep.NOTIFICATIONS.persistedIndex }
                )
            }

            PuppyOnboardingStep.NOTIFICATIONS.persistedIndex -> {
                PuppyOnboardingNotifications(
                    ui = ui,
                    onBack = { step = PuppyOnboardingStep.PERSONALIZE.persistedIndex },
                    onComplete = { step = PuppyOnboardingStep.READY.persistedIndex }
                )
            }

            else -> {
                PuppyOnboardingReady(
                    ui = ui,
                    session = session,
                    onReviewSetup = {
                        step = PuppyOnboardingStep.PLAYER_SETUP.persistedIndex
                    },
                    onStartPlaying = {
                        vm.dismissSeasonalIntro()
                        PuppyUiPreferences.finishSetup(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun PuppyOnboardingWelcome(onNext: () -> Unit) {
    val context = LocalContext.current
    val flags by PuppyFeatureFlags.flags.collectAsStateWithLifecycle()
    val discordFlag = flags["discord_linking"] ?: PuppyFeatureFlags.flag("discord_linking")

    PuppyOnboardingShell(
        step = PuppyOnboardingStep.WELCOME,
        title = "Welcome",
        canGoBack = false,
        primaryLabel = "Get Started",
        onPrimary = onNext,
        preferViewportFit = true,
        footer = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PuppyLegalLinks(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(5.dp))
                PuppyDevelopmentNotice()
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = streamedRepoLogoPainter(
                    RepoLogoAsset.PUPPY_CLICKER,
                    R.drawable.source_logo
                ),
                contentDescription = "Puppy Clicker logo",
                modifier = Modifier.size(96.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "Welcome to Puppy Clicker",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Tap, care for puppies, build your collection, and make the game yours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PuppyWelcomeFeatureCard(
                    modifier = Modifier.weight(1f),
                    title = "PupEye",
                    body = "Fair-play + save integrity.",
                    action = "Protected",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    icon = {
                        StreamedPupEyeBranding(
                            modifier = Modifier.size(40.dp),
                            contentDescription = "PupEye fair-play protection"
                        )
                    }
                )

                PuppyWelcomeFeatureCard(
                    modifier = Modifier.weight(1f),
                    title = "Discord",
                    body = if (discordFlag.isAvailable()) {
                        "Community, updates, and account connection."
                    } else {
                        discordFlag.statusLabel()
                    },
                    action = if (discordFlag.isAvailable()) "Open ›" else discordFlag.statusLabel(),
                    enabled = discordFlag.isAvailable(),
                    onClick = if (discordFlag.isAvailable()) {
                        { PuppyLinks.openDiscord(context) }
                    } else {
                        null
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.ic_discord),
                            contentDescription = "Discord",
                            modifier = Modifier.size(36.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer)
                        )
                    }
                )

                PuppyWelcomeFeatureCard(
                    modifier = Modifier.weight(1f),
                    title = "Roadmap",
                    body = "Planned features and work in progress.",
                    action = "Trello ›",
                    onClick = { PuppyLinks.openRoadmap(context) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    icon = {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "PC",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                )
            }

            Spacer(Modifier.height(11.dp))

            Text(
                "Powered by",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            HarleysStudiosBranding(
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .height(54.dp),
                contentDescription = "Harley's Studios"
            )
        }
    }
}

@Composable
private fun PuppyWelcomeFeatureCard(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    action: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    containerColor: Color,
    icon: @Composable () -> Unit
) {
    var cardModifier = modifier.height(154.dp)
    if (onClick != null) {
        cardModifier = cardModifier.clickable(enabled = enabled, onClick = onClick)
    }

    Surface(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
        }
    }
}
