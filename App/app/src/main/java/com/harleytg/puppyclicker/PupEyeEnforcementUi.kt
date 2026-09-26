package com.harleytg.puppyclicker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.format.DateTimeFormatter

/** The only app route visible while a server-authoritative review or ban is cached. */
@Composable
internal fun PupEyeEnforcementGate(snapshot: PupEyeEnforcementSnapshot) {
    val context = LocalContext.current
    val ban = snapshot.ban
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (snapshot.mode == PupEyeEnforcementMode.GLOBAL_BANNED) {
                    "PupEye Global Ban"
                } else {
                    "PupEye Review Required"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (ban != null) "Puppy Clicker Global" else
                    snapshot.reviewMessage ?: "PupEye requires Support review.",
                textAlign = TextAlign.Center
            )
            if (ban != null) {
                Spacer(Modifier.height(16.dp))
                Text("Ban ID: ${ban.id}")
                Text("Type: ${if (ban.kind == "permanent") "Permanent" else "Temporary"}")
                Text("Reason: ${ban.publicReason}")
                Text("Issued: ${formatEnforcementTime(ban.issuedAtEpochMs)}")
                Text("Expires: ${ban.expiresAtEpochMs?.let(::formatEnforcementTime) ?: "Never"}")
                if (ban.deviceWide) Text("Applies to this recognized device")
            }
            Spacer(Modifier.height(12.dp))
            Text("Support Installation Code: ${PupEyeAuthority.supportInstallationCode(context)}")
            Spacer(Modifier.height(20.dp))
            if (ban != null) {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ban ID", ban.id))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Copy Ban ID")
                }
            }
            OutlinedButton(
                onClick = { PuppyLinks.openDiscord(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Contact Support")
            }
            PuppyLegalLinks(
                modifier = Modifier.fillMaxWidth(),
                acknowledgementText = "Review Puppy Clicker's Terms and Privacy information."
            )
        }
    }
}

private fun formatEnforcementTime(epochMs: Long): String =
    if (epochMs > 0L) DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))
    else "Unavailable"
