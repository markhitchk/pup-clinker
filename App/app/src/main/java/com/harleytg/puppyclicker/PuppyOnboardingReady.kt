package com.harleytg.puppyclicker

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun PuppyOnboardingReady(
    ui: PuppyUiState,
    session: PuppyOnboardingSessionState,
    onReviewSetup: () -> Unit,
    onStartPlaying: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val discordAccount = DiscordSignupAuth.account(context)
    var pupEyeState by remember { mutableStateOf("Checking") }

    LaunchedEffect(Unit) {
        val safe = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(
                PuppyClickerV6ViewModel.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            PupEyeSaveGuard.verifyAndRecover(context, prefs) &&
                ExternalGameSave.verifyExisting(context)
        }
        pupEyeState = if (safe) "Protected" else "Checked"
    }

    PuppyOnboardingShell(
        step = PuppyOnboardingStep.READY,
        title = "Ready to Play",
        canGoBack = false,
        primaryLabel = "Start Playing",
        primaryEnabled = pupEyeState != "Checking",
        onPrimary = onStartPlaying
    ) {
        Text(
            "Your setup is ready.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Text(
            "You can change profile and appearance options later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        ReadyStatusRow("Username", PuppyPlayerIdentity.username(context))
        ReadyStatusRow("Player ID", PuppyPlayerIdentity.publicPlayerId(context))
        ReadyStatusRow("Friend Code", PuppyPlayerIdentity.publicFriendCode(context))

        if (discordAccount != null) {
            ReadyStatusRow("Discord", "@${discordAccount.username}")
        }

        if (session.importedSave) {
            ReadyStatusRow("Save restored", "Yes")
        }

        if (ui.hasBirthday) {
            ReadyStatusRow(
                "Birthday",
                "${readyMonthName(ui.birthdayMonth)} ${ui.birthdayDay}"
            )
        }

        ReadyStatusRow(
            "Theme",
            when (ui.themeMode) {
                PuppyThemeMode.SYSTEM -> "Follow System"
                PuppyThemeMode.LIGHT -> "Light"
                PuppyThemeMode.DARK -> "Dark"
            }
        )
        ReadyStatusRow(
            "UI scale",
            when (ui.uiScale) {
                PuppyUiScale.COMPACT -> "Compact"
                PuppyUiScale.DEFAULT -> "Default"
                PuppyUiScale.LARGE -> "Large"
            }
        )
        ReadyStatusRow("PupEye", pupEyeState)

        Spacer(Modifier.height(10.dp))
        TextButton(
            onClick = onReviewSetup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Review Setup")
        }
    }
}

@Composable
private fun ReadyStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End
        )
    }
}

private fun readyMonthName(month: Int): String =
    Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
