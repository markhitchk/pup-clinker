package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PuppyErrorDetails(
    error: PuppyError,
    actorLabel: String,
    supportInstallationCode: String
) {
    Column {
        Text(
            error.code,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.size(4.dp))
        Text(
            error.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.size(8.dp))
        Text(
            error.message,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.size(10.dp))
        Text(
            "What you can do",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            error.recovery,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.size(10.dp))
        Text(
            "Source: " + when (error.domain) {
                PuppyErrorDomain.PUPPY_CLICKER -> "Puppy Clicker"
                PuppyErrorDomain.PUPEYE -> "PupEye"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Severity: " + error.severity.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            "PupEye detected: $actorLabel",
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            "Support Installation Code: $supportInstallationCode",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
