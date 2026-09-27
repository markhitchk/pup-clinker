package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PuppyErrorReportDialog(
    error: PuppyError,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var whatHappened by rememberSaveable(error.code) { mutableStateOf("") }
    var submitting by rememberSaveable(error.code) { mutableStateOf(false) }
    var status by rememberSaveable(error.code) { mutableStateOf<String?>(null) }
    var sentReportId by rememberSaveable(error.code) { mutableStateOf<String?>(null) }

    val sourceName = when (error.domain) {
        PuppyErrorDomain.PUPPY_CLICKER -> "Puppy Clicker"
        PuppyErrorDomain.PUPEYE -> "PupEye"
    }
    val configured = PuppySupportReporting.isErrorSubmissionConfigured()

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("Report " + sourceName + " Error", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(
                    error.code + " · " + error.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.size(8.dp))
                Text("Tell support what you were doing when this happened.")
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = whatHappened,
                    onValueChange = {
                        whatHappened = it.take(1_500)
                        status = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What happened?") },
                    supportingText = {
                        Text(whatHappened.length.toString() + "/1500 · minimum 10 characters")
                    },
                    minLines = 4,
                    enabled = !submitting && sentReportId == null
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Sends the error code, your description, app/build info, device model, detected identity type, and Support Installation Code. Your backup password is never included.",
                    style = MaterialTheme.typography.labelSmall
                )
                status?.let { message ->
                    Spacer(Modifier.size(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sentReportId != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            if (sentReportId == null) {
                Button(
                    onClick = {
                        submitting = true
                        status = "Sending error report…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                PuppySupportReporting.submitErrorReport(
                                    context = context,
                                    error = error,
                                    whatHappened = whatHappened
                                )
                            }
                            submitting = false
                            if (result.success) {
                                sentReportId = result.reportId
                                status = "Sent to support · " + (result.reportId ?: "report received")
                            } else {
                                status = when (result.error) {
                                    "description_too_short" -> "Add a little more detail about what happened."
                                    "rate_limited" -> "Please wait a few seconds before sending another error report."
                                    "discord_destination_unavailable" -> "Error reporting is not configured in this build."
                                    else -> "Could not send the report. Try again."
                                }
                            }
                        }
                    },
                    enabled = !submitting && configured && whatHappened.trim().length >= 10
                ) {
                    Text(
                        when {
                            submitting -> "Sending…"
                            !configured -> "Reporting unavailable"
                            else -> "Send Report"
                        }
                    )
                }
            } else {
                Button(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            if (sentReportId == null) {
                TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") }
            }
        }
    )
}
