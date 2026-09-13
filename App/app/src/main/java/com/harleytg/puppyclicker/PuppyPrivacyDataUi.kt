package com.harleytg.puppyclicker

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PuppyOnboardingPrivacy(
    ui: PuppyUiState,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current

    PuppyOnboardingShell(
        step = PuppyOnboardingStep.PRIVACY,
        title = "Permissions & Privacy",
        canGoBack = true,
        primaryLabel = "Continue",
        onBack = onBack,
        onPrimary = {
            PuppyUiPreferences.markPrivacyConsentReviewed(context)
            onComplete()
        }
    ) {
        Text(
            "Choose optional data sharing. Declining these options does not block Puppy Clicker.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Anonymous diagnostics and crash reporting are OFF by default and can be changed later in Settings → Privacy & Data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        PrivacyToggleCard(
            title = "Optional anonymous diagnostics",
            detail = "Allow anonymous technical diagnostics to be sent directly to Puppy Clicker's Discord support diagnostics channel using the encrypted webhook. No gameplay access is blocked when this is off.",
            checked = ui.anonymousDiagnosticsEnabled,
            onCheckedChange = {
                PuppyUiPreferences.setAnonymousDiagnosticsEnabled(context, it)
            }
        )

        Spacer(Modifier.height(8.dp))

        PrivacyToggleCard(
            title = "Optional crash reports",
            detail = "Allow crash information to be sent directly to Puppy Clicker's Discord support diagnostics channel using the encrypted webhook. This is optional and defaults to off.",
            checked = ui.crashReportsEnabled,
            onCheckedChange = {
                PuppyUiPreferences.setCrashReportsEnabled(context, it)
            }
        )

        Spacer(Modifier.height(8.dp))

        PrivacyToggleCard(
            title = "Include account identity in support reports",
            detail = "Optional. When enabled, support reports may include your Puppy Clicker username, public Player ID, Friend Code, and linked Discord display name/username and Discord user ID. OAuth tokens, birthday, save data, and Treat balances are never included.",
            checked = ui.supportIdentityEnabled,
            onCheckedChange = {
                PuppyUiPreferences.setSupportIdentityEnabled(context, it)
            }
        )

        Spacer(Modifier.height(12.dp))

        PrivacyInfoCard(
            title = "🔔 Notifications",
            detail = "Notification choices are configured on the next step. Android notification permission is requested only there, when needed."
        )

        Spacer(Modifier.height(8.dp))

        PrivacyInfoCard(
            title = "🌐 Internet & network functionality",
            detail = "Puppy Clicker uses network access for streamed assets, feature flags, community links, and online features. INTERNET is an app capability, not a runtime consent prompt."
        )

        Spacer(Modifier.height(8.dp))

        PrivacyInfoCard(
            title = "🛡️ PupEye fair-play diagnostics",
            detail = "Local PupEye fair-play and save-integrity validation stays active on-device. Turning off optional telemetry does not disable local protection."
        )
    }
}

@Composable
internal fun PuppyPrivacyDataSettings(ui: PuppyUiState) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Privacy & Data", fontWeight = FontWeight.Black)
            Text(
                "Optional reporting can be changed or withdrawn at any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (ui.privacyConsentVersion >= PuppyUiPreferences.CURRENT_PRIVACY_CONSENT_VERSION) {
                    "Privacy choices reviewed for onboarding v4."
                } else {
                    "Privacy choices have not been reviewed in onboarding v4."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    PrivacyToggleCard(
        title = "Anonymous diagnostics",
        detail = "Optional anonymous technical diagnostics sent directly to Puppy Clicker's Discord support diagnostics channel using the encrypted webhook. Turning this off withdraws consent for future uploads.",
        checked = ui.anonymousDiagnosticsEnabled,
        onCheckedChange = {
            PuppyUiPreferences.setAnonymousDiagnosticsEnabled(context, it)
            PuppyUiPreferences.markPrivacyConsentReviewed(context)
        }
    )

    Spacer(Modifier.height(8.dp))

    PrivacyToggleCard(
        title = "Crash reports",
        detail = "Optional crash reporting sent directly to Puppy Clicker's Discord support diagnostics channel using the encrypted webhook. Turning this off withdraws consent for future crash-report uploads.",
        checked = ui.crashReportsEnabled,
        onCheckedChange = {
            PuppyUiPreferences.setCrashReportsEnabled(context, it)
            PuppyUiPreferences.markPrivacyConsentReviewed(context)
        }
    )

    Spacer(Modifier.height(8.dp))

    PrivacyToggleCard(
        title = "Include account identity in support reports",
        detail = "Optional. Adds your Puppy Clicker username, public Player ID, Friend Code, and linked Discord identity to support embeds. Turning this off returns future reports to anonymous diagnostics.",
        checked = ui.supportIdentityEnabled,
        onCheckedChange = {
            PuppyUiPreferences.setSupportIdentityEnabled(context, it)
            PuppyUiPreferences.markPrivacyConsentReviewed(context)
        }
    )

    Spacer(Modifier.height(14.dp))

    PrivacyInfoCard(
        title = "PupEye local protection",
        detail = "PupEye local fair-play and save-integrity checks are separate from optional reporting and remain available when telemetry is disabled."
    )

    Spacer(Modifier.height(8.dp))

    PrivacyInfoCard(
        title = "Network functionality",
        detail = "Internet access supports streamed roster/assets, feature flags, community links, and future online services. It is not an Android runtime permission."
    )

    Spacer(Modifier.height(8.dp))

    PrivacyInfoCard(
        title = "Notifications",
        detail = "Notification categories and Android notification permission are managed separately in Notification Settings."
    )
}

@Composable
private fun PrivacyToggleCard(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun PrivacyInfoCard(
    title: String,
    detail: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
