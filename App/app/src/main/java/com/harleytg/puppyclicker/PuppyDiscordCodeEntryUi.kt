package com.harleytg.puppyclicker

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CodePurple = Color(0xFF6D70E8)

@Composable
internal fun PuppyDiscordCodeEntryDialog(
    onDismiss: () -> Unit,
    onVerified: (PuppyDiscordVerifySnapshot) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = puppyEdgeToEdgeDialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .puppyDialogSafeDrawingPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 16.dp
            ) {
                PuppyDiscordCodeEntryContent(onClose = onDismiss, onVerified = onVerified)
            }
        }
    }
}

@Composable
internal fun PuppyDiscordCodeEntryContent(
    onClose: (() -> Unit)? = null,
    onVerified: (PuppyDiscordVerifySnapshot) -> Unit = {}
) {
    val context = LocalContext.current
    val snapshot by PuppyAuthBotClient.verifyState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }

    LaunchedEffect(snapshot.status, snapshot.discordId) {
        if (snapshot.status == PuppyDiscordLinkStatus.VERIFIED) {
            onVerified(snapshot)
        }
    }

    val normalized = PuppyDiscordDmCodePolicy.normalize(code)
    val boxes = normalized.padEnd(PuppyDiscordDmCodePolicy.MAX_LENGTH, ' ')
        .take(PuppyDiscordDmCodePolicy.MAX_LENGTH)
    val locked = snapshot.status == PuppyDiscordLinkStatus.LOCKED
    val expiresAtMs = snapshot.expiresAtMs
    val expired = snapshot.status == PuppyDiscordLinkStatus.EXPIRED ||
        (expiresAtMs != null && expiresAtMs <= nowMs &&
            snapshot.status == PuppyDiscordLinkStatus.CODE_SENT)
    val remainingMs = expiresAtMs?.let { max(0L, it - nowMs) } ?: 0L
    val resendInMs = snapshot.resendAvailableAtMs?.let { max(0L, it - nowMs) } ?: 0L

    Column(Modifier.padding(20.dp)) {
        Text("Enter Discord code", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(
            "Puppy Clicker Auth DMed a one-time Pup Account login code to your Discord account. Type or paste it below to finish sign-in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Box {
            BasicTextField(
                value = normalized,
                onValueChange = { incoming ->
                    code = PuppyDiscordDmCodePolicy.normalize(incoming)
                        .take(PuppyDiscordDmCodePolicy.MAX_LENGTH)
                    localError = null
                },
                enabled = !locked && !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                boxes.forEach { char ->
                    Surface(
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, CodePurple.copy(alpha = 0.45f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (char.isWhitespace()) "" else char.toString(),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                locked -> "Too many attempts. Wait for a new code or contact Support."
                expired -> "That code expired. Resend a new one."
                snapshot.status == PuppyDiscordLinkStatus.DM_FAILED ->
                    snapshot.message
                        ?: "Enable DMs from server members or join the Puppy Clicker server, then resend."
                snapshot.expiresAtMs != null -> "Code expires in ${formatCountdown(remainingMs)}"
                else -> "Codes are 6–8 letters or numbers and last about 10 minutes."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (locked || expired || snapshot.status == PuppyDiscordLinkStatus.DM_FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        localError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                if (!PuppyDiscordDmCodePolicy.isWellFormed(normalized)) {
                    localError = "Enter the 6–8 character code from Discord."
                    return@Button
                }
                busy = true
                localError = null
                scope.launch {
                    val result = runCatching {
                        PuppyAuthBotClient.submitVerificationCode(context, normalized)
                    }.getOrElse { error ->
                        PuppyDiscordVerifySnapshot(
                            status = PuppyDiscordLinkStatus.OFFLINE,
                            message = error.message,
                            offline = true
                        )
                    }
                    busy = false
                    when (result.status) {
                        PuppyDiscordLinkStatus.VERIFIED -> onVerified(result)
                        PuppyDiscordLinkStatus.LOCKED -> localError = "This code is locked after too many tries."
                        PuppyDiscordLinkStatus.EXPIRED -> localError = "That code expired. Resend a new one."
                        else -> localError = result.message ?: "That code is not correct."
                    }
                }
            },
            enabled = !busy && !locked,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CodePurple, contentColor = Color.White)
        ) {
            Text(if (busy) "Checking…" else "Verify code", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val pasted = currentClipboardText(context)
                    if (!pasted.isNullOrBlank()) {
                        code = PuppyDiscordDmCodePolicy.normalize(pasted)
                            .take(PuppyDiscordDmCodePolicy.MAX_LENGTH)
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Paste") }
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        val result = runCatching { DiscordSignupAuth.resendPendingVerification(context) }
                            .getOrElse { error ->
                                PuppyDiscordVerifySnapshot(
                                    status = PuppyDiscordLinkStatus.OFFLINE,
                                    message = error.message,
                                    offline = true
                                )
                            }
                        busy = false
                        localError = result.message
                    }
                },
                enabled = !busy && !locked && resendInMs <= 0L,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (resendInMs > 0L) "Resend ${formatCountdown(resendInMs)}" else "Resend",
                    textAlign = TextAlign.Center
                )
            }
        }
        if (onClose != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Attempts left: ${snapshot.attemptsRemaining}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCountdown(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}

private fun currentClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}
