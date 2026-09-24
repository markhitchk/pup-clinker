package com.harleytg.puppyclicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PuppySupportReportSettings(ui: PuppyUiState) {
    val context = LocalContext.current
    var typeName by rememberSaveable { mutableStateOf(PuppySupportReportType.BUG.name) }
    var typeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var subject by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var reportContext by rememberSaveable { mutableStateOf("") }
    var reportedUser by rememberSaveable { mutableStateOf("") }
    var includeIdentity by rememberSaveable { mutableStateOf(false) }
    var includeDiagnostics by rememberSaveable { mutableStateOf(false) }
    var prepared by remember { mutableStateOf<PuppyPreparedSupportReport?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val selectedType = runCatching { PuppySupportReportType.valueOf(typeName) }
        .getOrDefault(PuppySupportReportType.BUG)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("🐾 Tier 1 Support", fontWeight = FontWeight.Black)
            Text(
                "Reports use Puppy Clicker's existing support subsystem and submit directly to the Tier 1 Discord inbox using the encrypted webhook.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Prepared does not mean submitted. Submission is confirmed only after Discord delivery succeeds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp)
            )
            PuppySupportReporting.lastPreparedReportId(context)?.let { reportId ->
                Text(
                    "Last prepared: $reportId",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Text("Report type", fontWeight = FontWeight.Bold)
    OutlinedButton(
        onClick = { typeMenuOpen = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("${selectedType.emoji} ${selectedType.label}")
    }
    DropdownMenu(
        expanded = typeMenuOpen,
        onDismissRequest = { typeMenuOpen = false }
    ) {
        PuppySupportReportType.entries.forEach { item ->
            DropdownMenuItem(
                text = { Text("${item.emoji} ${item.label}") },
                onClick = {
                    typeName = item.name
                    typeMenuOpen = false
                    prepared = null
                }
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = subject,
        onValueChange = {
            subject = it.take(80)
            prepared = null
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Short subject") },
        singleLine = true
    )

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = description,
        onValueChange = {
            description = it.take(2_000)
            prepared = null
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("What happened?") },
        minLines = 4,
        supportingText = { Text("${description.length}/2000") }
    )

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = reportContext,
        onValueChange = {
            reportContext = it.take(1_500)
            prepared = null
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Steps to reproduce / context") },
        minLines = 3
    )

    if (selectedType == PuppySupportReportType.PLAYER) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = reportedUser,
            onValueChange = {
                reportedUser = it.take(100)
                prepared = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Reported username / Friend Code") },
            supportingText = {
                Text("Enter only what support needs to identify the reported player.")
            },
            singleLine = true
        )
    }

    Spacer(Modifier.height(12.dp))
    SupportAttachmentConsentRow(
        title = "Include support identity",
        detail = if (ui.supportIdentityEnabled) {
            "Uses the existing Privacy & Data support-identity consent."
        } else {
            "Disabled in Privacy & Data. Enable support identity there first."
        },
        checked = includeIdentity && ui.supportIdentityEnabled,
        enabled = ui.supportIdentityEnabled,
        onCheckedChange = {
            includeIdentity = it
            prepared = null
        }
    )
    SupportAttachmentConsentRow(
        title = "Include sanitized session diagnostics",
        detail = if (ui.anonymousDiagnosticsEnabled) {
            "Uses the existing diagnostics consent and PuppyDebugLog redaction."
        } else {
            "Disabled in Privacy & Data. Enable anonymous diagnostics there first."
        },
        checked = includeDiagnostics && ui.anonymousDiagnosticsEnabled,
        enabled = ui.anonymousDiagnosticsEnabled,
        onCheckedChange = {
            includeDiagnostics = it
            prepared = null
        }
    )

    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            prepared = PuppySupportReporting.prepareUserReport(
                context = context,
                draft = PuppySupportReportDraft(
                    type = selectedType,
                    subject = subject,
                    description = description,
                    context = reportContext,
                    reportedUser = reportedUser,
                    includeIdentity = includeIdentity,
                    includeDiagnostics = includeDiagnostics
                )
            )
            status = "Report prepared locally. It has not been submitted."
        },
        enabled = description.trim().length >= 10 &&
            (selectedType != PuppySupportReportType.PLAYER || reportedUser.isNotBlank()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Prepare Support Report")
    }

    prepared?.let { report ->
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Prepared locally", fontWeight = FontWeight.Black)
                Text(
                    report.reportId,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Use Submit to send directly to Puppy Clicker's Tier 1 Discord support inbox. Copy/Share remain available as fallback delivery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        submitting = true
                        status = "Submitting report…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                PuppySupportReporting.submitPreparedReport(context, report)
                            }
                            submitting = false
                            status = if (result.success) {
                                "Submitted to Tier 1 support · ${report.reportId}"
                            } else {
                                val detail = result.statusCode?.let { "HTTP $it" }
                                    ?: result.error
                                    ?: "unknown error"
                                "Discord support delivery failed ($detail). Use Copy or Share."
                            }
                        }
                    },
                    enabled = !submitting && PuppySupportReporting.isSubmissionConfigured(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            submitting -> "Submitting…"
                            PuppySupportReporting.isSubmissionConfigured() -> "Submit to Tier 1 Support"
                            else -> "Discord Support Not Configured"
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            PuppySupportReporting.copyPreparedReport(context, report)
                            status = "Report copied to clipboard."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy")
                    }
                    OutlinedButton(
                        onClick = {
                            PuppySupportReporting.sharePreparedReport(context, report)
                            status = "Android Share opened."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Share")
                    }
                }
            }
        }
    }

    status?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SupportAttachmentConsentRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
