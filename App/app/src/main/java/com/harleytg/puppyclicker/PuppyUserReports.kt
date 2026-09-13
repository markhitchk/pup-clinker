package com.harleytg.puppyclicker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class PuppySupportReportType(
    val emoji: String,
    val label: String
) {
    BUG("🐛", "Bug / App Problem"),
    CASINO("🎰", "Casino"),
    SAVE_DATA("💾", "Save / Data"),
    ACCOUNT("👤", "Account / Identity"),
    PLAYER("🚩", "Player / User Report"),
    OTHER("📝", "Other")
}

internal data class PuppyPreparedSupportReport(
    val reportId: String,
    val type: PuppySupportReportType,
    val subject: String,
    val body: String
)

internal object PuppyUserReports {
    private const val PREFS = "puppy_user_reports_v1"
    private const val KEY_LAST_REPORT_ID = "last_report_id"
    private const val KEY_LAST_REPORT_TIME = "last_report_time"
    private const val MAX_DIAGNOSTIC_ENTRIES = 30
    private const val MAX_DIAGNOSTIC_MESSAGE_CHARS = 300
    private val random = SecureRandom()

    fun prepare(
        context: Context,
        type: PuppySupportReportType,
        subject: String,
        description: String,
        reproductionSteps: String,
        reportedUser: String,
        includeIdentity: Boolean,
        includeDiagnostics: Boolean
    ): PuppyPreparedSupportReport {
        val safeSubject = subject.trim().take(80).ifBlank { type.label }
        val reportId = newReportId()
        val timestamp = Instant.now().toString()

        val body = buildString {
            appendLine("PUPPY CLICKER — TIER 1 SUPPORT REPORT")
            appendLine("Report ID: $reportId")
            appendLine("Type: ${type.label}")
            appendLine("Created: $timestamp")
            appendLine()
            appendLine("SUBJECT")
            appendLine(safeSubject)
            appendLine()
            appendLine("DESCRIPTION")
            appendLine(description.trim().take(2_000))
            appendLine()
            appendLine("STEPS / CONTEXT")
            appendLine(reproductionSteps.trim().take(1_500).ifBlank { "Not provided" })

            if (type == PuppySupportReportType.PLAYER) {
                appendLine()
                appendLine("REPORTED USER")
                appendLine(reportedUser.trim().take(100).ifBlank { "Not provided" })
            }

            appendLine()
            appendLine("APP INFORMATION")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${PuppyPlayerIdentity.deviceModel()}")

            if (includeIdentity) {
                appendLine()
                appendLine("SUPPORT IDENTITY — USER OPT-IN")
                appendLine("Username: ${PuppyPlayerIdentity.username(context)}")
                appendLine("Player ID: ${PuppyPlayerIdentity.publicPlayerId(context)}")
                appendLine("Friend Code: ${PuppyPlayerIdentity.publicFriendCode(context)}")
                DiscordSignupAuth.account(context)?.let { account ->
                    appendLine("Discord User ID: ${account.id}")
                    appendLine("Discord Username: @${account.username}")
                    appendLine("Discord Display Name: ${account.displayName}")
                }
            }

            if (includeDiagnostics) {
                appendLine()
                appendLine("SANITIZED SESSION DIAGNOSTICS — USER OPT-IN")
                val entries = PuppyDebugLog.snapshot().takeLast(MAX_DIAGNOSTIC_ENTRIES)
                if (entries.isEmpty()) {
                    appendLine("No in-app diagnostic entries available.")
                } else {
                    entries.forEach { entry ->
                        val message = PuppyDebugLog.redactForConsole(entry.message)
                            .take(MAX_DIAGNOSTIC_MESSAGE_CHARS)
                        appendLine(
                            "${entry.timestampMs} ${entry.level.shortName}/${entry.tag}: $message"
                        )
                    }
                }
            }

            appendLine()
            appendLine("DELIVERY")
            appendLine(
                "This report was prepared locally because Puppy Clicker does not have a user-report API. " +
                    "Opening Android Share does not prove that a support destination received it."
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_REPORT_ID, reportId)
            .putLong(KEY_LAST_REPORT_TIME, System.currentTimeMillis())
            .apply()

        return PuppyPreparedSupportReport(
            reportId = reportId,
            type = type,
            subject = safeSubject,
            body = body
        )
    }

    fun lastPreparedReportId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_REPORT_ID, null)
            ?.takeIf { it.isNotBlank() }

    fun copy(context: Context, report: PuppyPreparedSupportReport) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Puppy Clicker Support ${report.reportId}", report.body)
        )
    }

    fun share(context: Context, report: PuppyPreparedSupportReport) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                "[Puppy Clicker] ${report.type.label} · ${report.reportId} · ${report.subject}"
            )
            putExtra(Intent.EXTRA_TEXT, report.body)
        }
        context.startActivity(
            Intent.createChooser(send, "Share Puppy Clicker support report")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun newReportId(): String {
        val date = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val suffix = random.nextInt(0x10000)
            .toString(16)
            .uppercase(Locale.US)
            .padStart(4, '0')
        return "PC-RPT-$date-$suffix"
    }
}

@Composable
internal fun PuppyUserReportSettings() {
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf(PuppySupportReportType.BUG.name) }
    var typeMenuOpen by rememberSaveable { mutableStateOf(false) }
    var subject by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var reproductionSteps by rememberSaveable { mutableStateOf("") }
    var reportedUser by rememberSaveable { mutableStateOf("") }
    var includeIdentity by rememberSaveable { mutableStateOf(false) }
    var includeDiagnostics by rememberSaveable { mutableStateOf(false) }
    var prepared by remember { mutableStateOf<PuppyPreparedSupportReport?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedType = runCatching { PuppySupportReportType.valueOf(type) }
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
                "No user-report API is required. Puppy Clicker prepares the report locally, then you choose where to send it with Android Share.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "The app cannot confirm delivery or show a server-side ticket status until a support API exists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp)
            )
            PuppyUserReports.lastPreparedReportId(context)?.let { lastId ->
                Text(
                    "Last prepared: $lastId",
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
                    type = item.name
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
        value = reproductionSteps,
        onValueChange = {
            reproductionSteps = it.take(1_500)
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
                Text("Enter only the information needed to identify the reported player.")
            },
            singleLine = true
        )
    }

    Spacer(Modifier.height(12.dp))
    SupportReportConsentRow(
        title = "Include my Puppy Clicker identity",
        detail = "Adds username, public Player ID, Friend Code, and linked Discord identity if available.",
        checked = includeIdentity,
        onCheckedChange = {
            includeIdentity = it
            prepared = null
        }
    )
    SupportReportConsentRow(
        title = "Include sanitized session diagnostics",
        detail = "Adds up to the latest 30 in-app diagnostic entries. Secrets, emails, and common sensitive values are redacted.",
        checked = includeDiagnostics,
        onCheckedChange = {
            includeDiagnostics = it
            prepared = null
        }
    )

    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            prepared = PuppyUserReports.prepare(
                context = context,
                type = selectedType,
                subject = subject,
                description = description,
                reproductionSteps = reproductionSteps,
                reportedUser = reportedUser,
                includeIdentity = includeIdentity,
                includeDiagnostics = includeDiagnostics
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
                    "This is not marked Submitted because there is no support API acknowledgement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            PuppyUserReports.copy(context, report)
                            status = "Report copied to clipboard."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy")
                    }
                    Button(
                        onClick = {
                            PuppyUserReports.share(context, report)
                            status =
                                "Android Share opened. Puppy Clicker cannot confirm delivery without an API."
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
private fun SupportReportConsentRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
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
