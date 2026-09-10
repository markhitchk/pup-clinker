package com.harleytg.puppyclicker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PuppyDeveloperOptions(onOpenConsole: () -> Unit) {
    val entries by PuppyDebugLog.observe().collectAsStateWithLifecycle()
    val warnings = entries.count { it.level == PuppyLogLevel.WARN }
    val errors = entries.count { it.level == PuppyLogLevel.ERROR }

    Text("Developer Mode", fontWeight = FontWeight.Black)
    Text(
        "Read-only Puppy Clicker diagnostics. Android-wide logcat and command execution are not exposed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    DeveloperMetric("Session logs", entries.size.toString())
    DeveloperMetric("Warnings", warnings.toString())
    DeveloperMetric("Errors", errors.toString())
    Spacer(Modifier.height(10.dp))
    Button(onClick = onOpenConsole, modifier = Modifier.fillMaxWidth()) {
        Text("Open Developer Console")
    }
}

@Composable
internal fun PuppyDeveloperConsoleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by PuppyDebugLog.observe().collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showDebug by rememberSaveable { mutableStateOf(true) }
    var showInfo by rememberSaveable { mutableStateOf(true) }
    var showWarn by rememberSaveable { mutableStateOf(true) }
    var showError by rememberSaveable { mutableStateOf(true) }
    var copyStatus by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        PuppyDebugLog.i("DeveloperConsole", "Developer Console opened")
    }

    val visibleEntries = remember(entries, query, showDebug, showInfo, showWarn, showError) {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        entries.asReversed().filter { entry ->
            val levelVisible = when (entry.level) {
                PuppyLogLevel.DEBUG -> showDebug
                PuppyLogLevel.INFO -> showInfo
                PuppyLogLevel.WARN -> showWarn
                PuppyLogLevel.ERROR -> showError
            }
            levelVisible && (
                normalizedQuery.isBlank() ||
                    entry.tag.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    entry.message.lowercase(Locale.ROOT).contains(normalizedQuery)
                )
        }
    }

    val warnings = entries.count { it.level == PuppyLogLevel.WARN }
    val errors = entries.count { it.level == PuppyLogLevel.ERROR }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Settings") }
        Text(
            "Developer Console",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "Puppy Clicker diagnostics only · sanitized · in memory",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConsoleCountCard("Logs", entries.size, Modifier.weight(1f))
            ConsoleCountCard("Warnings", warnings, Modifier.weight(1f))
            ConsoleCountCard("Errors", errors, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search tag or message") }
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FilterChip(selected = showDebug, onClick = { showDebug = !showDebug }, label = { Text("DEBUG") })
            FilterChip(selected = showInfo, onClick = { showInfo = !showInfo }, label = { Text("INFO") })
            FilterChip(selected = showWarn, onClick = { showWarn = !showWarn }, label = { Text("WARN") })
            FilterChip(selected = showError, onClick = { showError = !showError }, label = { Text("ERROR") })
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val text = visibleEntries.joinToString("\n") { it.toConsoleText() }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboard != null && text.isNotBlank()) {
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Puppy Clicker Developer Console", text)
                        )
                        copyStatus = "Copied ${visibleEntries.size} visible log entries."
                    } else {
                        copyStatus = if (text.isBlank()) "No visible logs to copy." else "Clipboard unavailable."
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Copy Visible Logs") }
            OutlinedButton(
                onClick = {
                    PuppyDebugLog.clear()
                    copyStatus = "Console cleared."
                },
                modifier = Modifier.weight(1f)
            ) { Text("Clear Console") }
        }
        copyStatus?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(6.dp))

        if (visibleEntries.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    if (entries.isEmpty()) "No Puppy Clicker diagnostics recorded this session." else "No logs match the current filters.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(visibleEntries) { _, entry ->
                    ConsoleLogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun DeveloperMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConsoleCountCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(9.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConsoleLogRow(entry: PuppyLogEntry) {
    val levelColor = when (entry.level) {
        PuppyLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        PuppyLogLevel.INFO -> MaterialTheme.colorScheme.primary
        PuppyLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        PuppyLogLevel.ERROR -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${entry.level.shortName}/${entry.tag}",
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = levelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatConsoleTime(entry.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                entry.message.ifBlank { "(no message)" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun PuppyLogEntry.toConsoleText(): String =
    "${formatConsoleTime(timestampMs)} ${level.shortName}/$tag: $message"

private fun formatConsoleTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(epochMs))
