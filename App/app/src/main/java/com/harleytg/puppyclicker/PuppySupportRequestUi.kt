package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
internal fun PuppyInAppSupportPanel(banId: String? = null) {
    val context = LocalContext.current
    val requests by PuppyAuthBotClient.requests.collectAsStateWithLifecycle()
    val online by PuppyAuthBotClient.online.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(PuppySupportRequestType.HARDWARE_RESET) }
    var subject by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { PuppyAuthBotClient.refreshSupportRequests(context) }
    }

    Text("In-app Support", fontWeight = FontWeight.Black)
    Text(
        "Open Hardware Reset, Account Recovery, or Ban Appeal from Puppy Clicker. Staff decisions appear here and in Notifications.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!online) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Puppy Clicker Auth is offline. You can still use the Discord fallback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Spacer(Modifier.height(10.dp))
    PuppySupportRequestType.entries.forEach { value ->
        OutlinedButton(onClick = { type = value }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (type == value) "● ${value.label}" else value.label,
                fontWeight = if (type == value) FontWeight.Black else FontWeight.Normal
            )
        }
        Spacer(Modifier.height(4.dp))
    }
    OutlinedTextField(
        value = subject,
        onValueChange = { subject = it.take(120) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Subject") },
        singleLine = true
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = details,
        onValueChange = { details = it.take(2_000) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        label = { Text("What happened?") }
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            if (subject.isBlank() || details.isBlank()) {
                message = "Add a subject and a short description."
                return@Button
            }
            busy = true
            scope.launch {
                val result = runCatching {
                    PuppyAuthBotClient.submitSupportRequest(context, type, subject, details, banId)
                }
                busy = false
                message = result.fold(
                    onSuccess = { "Request ${it.id} submitted (${it.type.label})." },
                    onFailure = { it.message ?: "Could not reach Puppy Clicker Auth." }
                )
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (busy) "Sending…" else "Submit ${type.label}")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { PuppyLinks.openDiscord(context) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Discord fallback")
    }
    message?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    if (requests.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text("Request history", fontWeight = FontWeight.Black)
        requests.forEach { request ->
            Spacer(Modifier.height(8.dp))
            Text("${request.type.label} · ${request.state.label}", fontWeight = FontWeight.Bold)
            Text(request.subject, style = MaterialTheme.typography.bodySmall)
            request.staffNote?.let { Text("Staff: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
