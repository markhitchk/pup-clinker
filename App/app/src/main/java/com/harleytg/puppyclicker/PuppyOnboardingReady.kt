package com.harleytg.puppyclicker

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
            "You can change profile, appearance, and notification options later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Player Profile", fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))

                ReadyIdentityField(
                    label = "Username",
                    value = PuppyPlayerIdentity.username(context)
                )
                ReadyIdentityField(
                    label = "Player ID",
                    value = PuppyPlayerIdentity.publicPlayerId(context)
                )
                ReadyIdentityField(
                    label = "Friend Code",
                    value = PuppyPlayerIdentity.publicFriendCode(context)
                )

                if (session.importedSave) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Save restored",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Connected", fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))

                if (discordAccount != null) {
                    ReadyStatusRow("Discord", "@" + discordAccount.username)
                } else {
                    ReadyStatusRow("Discord", "Not connected")
                }
                ReadyStatusRow("PupEye", pupEyeState)

                if (ui.hasBirthday) {
                    ReadyStatusRow(
                        "Birthday",
                        readyMonthName(ui.birthdayMonth) + " " + ui.birthdayDay
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Preferences", fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
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
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onReviewSetup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Review Setup")
        }
    }
}

@Composable
private fun ReadyIdentityField(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ReadyStatusRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun readyMonthName(month: Int): String =
    Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
