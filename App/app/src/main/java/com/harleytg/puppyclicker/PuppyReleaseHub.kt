package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

data class PuppyReleaseHubPresentation(
    val installedVersion: String,
    val installedBuild: Int,
    val latestVersion: String?,
    val latestBuild: Int?,
    val releaseName: String?,
    val releaseNotes: String,
    val updateAvailable: Boolean
)

internal object PuppyReleaseHubModel {
    const val WHATS_NEW_1_0 =
        "Puppy Clicker 1.0 adds per-puppy Bond progression, player XP, V6 Achievements, " +
            "a richer Puppy Viewer, notification history, large-screen improvements, " +
            "performance presets, accessibility hardening, a home-screen puppy widget, and release tools."

    fun build(
        installedVersion: String,
        installedBuild: Int,
        latest: PuppyReleaseUpdate?
    ): PuppyReleaseHubPresentation = PuppyReleaseHubPresentation(
        installedVersion = installedVersion,
        installedBuild = installedBuild,
        latestVersion = latest?.versionName?.takeIf { it.isNotBlank() },
        latestBuild = latest?.versionCode,
        releaseName = latest?.releaseName?.takeIf { it.isNotBlank() },
        releaseNotes = latest?.notes.orEmpty().take(4_000),
        updateAvailable = latest != null && latest.versionCode > installedBuild
    )
}

@Composable
internal fun PuppyReleaseHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by PuppyNotificationCenter.updateNotice.collectAsStateWithLifecycle()
    val model = PuppyReleaseHubModel.build(
        installedVersion = BuildConfig.VERSION_NAME,
        installedBuild = BuildConfig.VERSION_CODE,
        latest = update
    )

    LaunchedEffect(Unit) {
        PuppyNotificationCenter.loadCachedUpdate(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Release Hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "Updates and What's New for Puppy Clicker.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Installed", fontWeight = FontWeight.Black)
                Text("Version ${model.installedVersion} · Build ${model.installedBuild}")
                if (model.latestBuild != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Latest known", fontWeight = FontWeight.Black)
                    Text(
                        buildString {
                            append(model.releaseName ?: "Puppy Clicker release")
                            model.latestVersion?.let { append(" · $it") }
                            append(" · Build ${model.latestBuild}")
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("What's New in 1.0", fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(PuppyReleaseHubModel.WHATS_NEW_1_0)
            }
        }

        if (model.releaseNotes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Latest release notes", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text(model.releaseNotes)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { scope.launch { PuppyNotificationCenter.refreshUpdateStatus(context, force = true) } },
                modifier = Modifier.weight(1f)
            ) {
                Text("Refresh")
            }
            if (update != null) {
                Button(
                    onClick = {
                        val target = update?.apkUrl ?: update?.releaseUrl
                        if (!target.isNullOrBlank()) openPuppyUpdateUrl(context, target)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (model.updateAvailable) "Update" else "Open Release")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
