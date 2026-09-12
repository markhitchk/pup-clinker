package com.harleytg.puppyclicker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
        mutableIntStateOf(ui.setupStep.coerceIn(0, 4))
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

    PuppyOnboardingShell(
        step = PuppyOnboardingStep.WELCOME,
        title = "Welcome",
        canGoBack = false,
        primaryLabel = "Get Started",
        onPrimary = onNext,
        footer = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PuppyLegalLinks(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PuppyDevelopmentNotice()
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.harleys_studios_icon),
                        contentDescription = "Harley's Studios",
                        modifier = Modifier.size(22.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        "Harley's Studios",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.source_logo),
                contentDescription = "Puppy Clicker logo",
                modifier = Modifier.size(112.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Welcome to Puppy Clicker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                "Tap, care for puppies, build your collection, and make the game yours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StreamedPupEyeBranding(
                        modifier = Modifier.size(42.dp),
                        contentDescription = "PupEye protection"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        Text("Protected by PupEye", fontWeight = FontWeight.Black)
                        Text(
                            "Fair-play checks, save integrity, and encrypted backup protection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { PuppyLinks.openDiscord(context) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_discord),
                        contentDescription = "Discord",
                        modifier = Modifier.size(38.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    ) {
                        Text("Discord Community", fontWeight = FontWeight.Black)
                        Text(
                            "Optional account connection, community access, and Puppy Clicker updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "Open ›",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { PuppyLinks.openRoadmap(context) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Puppy Clicker Roadmap",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "See planned features, work in progress, and upcoming changes on Trello.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        "Trello ›",
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
