package com.harleytg.puppyclicker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
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
internal fun PuppyDeveloperOptions(
    onOpenConsole: () -> Unit,
    onMaxAccount: () -> String
) {
    val context = LocalContext.current
    val entries by PuppyDebugLog.observe().collectAsStateWithLifecycle()
    val discord by DiscordSignupAuth.observe(context).collectAsStateWithLifecycle()
    val developerAuthorized =
        PuppyPlayerIdentity.isHarleyTgDeveloper(context) ||
            discord.guildAccess?.role == DiscordGuildRole.DEVELOPER
    var confirmMax by rememberSaveable { mutableStateOf(false) }
    var maxStatus by rememberSaveable { mutableStateOf<String?>(null) }
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

    if (developerAuthorized) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { confirmMax = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Max Developer Account")
        }
        Text(
            "Developer-only: maxes local progression, then PupEye reseals it on this installation.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    maxStatus?.let { message ->
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }

    if (confirmMax) {
        AlertDialog(
            onDismissRequest = { confirmMax = false },
            title = { Text("Max Developer Account?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "This will max currencies, upgrades, tickets, prestige, care, achievements, bonds, puppies, and accessories on this device. Player identity and protected transaction ledgers are preserved."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        maxStatus = onMaxAccount()
                        confirmMax = false
                    }
                ) {
                    Text("Max Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMax = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun PuppyDeveloperConsoleScreen(
    state: V6GameState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val entries by PuppyDebugLog.observe().collectAsStateWithLifecycle()
    val rosterGroups by DynamicPuppyRoster.groups.collectAsStateWithLifecycle()
    val discord by DiscordSignupAuth.observe(context).collectAsStateWithLifecycle()
    val discordAsset = DynamicPuppyRoster.asset("v2_discord_pup")
    val devAsset = DynamicPuppyRoster.asset("v2_dev_pup")
    val activeAsset = DynamicPuppyRoster.asset(state.puppyStyle)
    val rosterCount = rosterGroups.sumOf { it.puppies.size }
    val discordRole = discord.guildAccess?.role?.label ?: "Not verified"
    val mainPrefs = context.getSharedPreferences(
        PuppyClickerV6ViewModel.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val security = PupEyeSaveGuard.state(context)
    val hardFlags = PupEyeAuthority.hardFlags(context)
    val pupEyeActor = PupEyeAuthority.actorLabel(context)
    val supportCode = runCatching {
        PupEyeAuthority.supportInstallationCode(context)
    }.getOrDefault("Unavailable")
    val saveGeneration = runCatching {
        PupEyeAuthority.currentGeneration(context)
    }.getOrDefault(0L)
    val playerXp = mainPrefs.getLong(PuppyProgressionStore.KEY_PLAYER_XP, 0L)
        .coerceAtLeast(0L)
    val rewardedAchievements = mainPrefs
        .getStringSet(PuppyProgressionStore.KEY_ACHIEVEMENT_REWARDED, emptySet())
        ?.size ?: 0
    val casinoInspection = PuppyCasinoPersistence.inspectActiveRound(mainPrefs)
    val completedCasinoRounds = PuppyCasinoPersistence.loadCompletedRoundIds(mainPrefs).size
    val exchangeSnapshot = PuppyExchangeLedger(context).snapshot()
    var query by rememberSaveable { mutableStateOf("") }
    var showDebug by rememberSaveable { mutableStateOf(true) }
    var showInfo by rememberSaveable { mutableStateOf(true) }
    var showWarn by rememberSaveable { mutableStateOf(true) }
    var showError by rememberSaveable { mutableStateOf(true) }
    var copyStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var userAdminOpen by rememberSaveable { mutableStateOf(false) }
    val userAdminAuthorized =
        PuppyPlayerIdentity.isHarleyTgDeveloper(context) ||
            discord.guildAccess?.role == DiscordGuildRole.DEVELOPER

    if (!userAdminOpen) {
        BackHandler(onBack = onBack)
    }

    if (userAdminOpen) {
        PuppyDeveloperUserAdminScreen(onBack = { userAdminOpen = false })
        return
    }

    LaunchedEffect(Unit) {
        PuppyDebugLog.i(
            "DeveloperConsole",
            "Opened build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
        PuppyDebugLog.i(
            "Roster",
            "Loaded $rosterCount puppies; active=${state.puppyStyle} source=" +
                "${activeAsset?.folder ?: "missing"}/${activeAsset?.fileName ?: "missing"}; " +
                "Dev Pup=${devAsset?.folder ?: "missing"}/${devAsset?.fileName ?: "missing"}; " +
                "Discord Pup=${discordAsset?.folder ?: "missing"}/${discordAsset?.fileName ?: "missing"}"
        )
        PuppyDebugLog.i(
            "DiscordAuth",
            "Account=${discord.account?.displayName ?: "not connected"}; " +
                "guild role=$discordRole; verifiedAt=${discord.guildAccess?.verifiedAtMs ?: 0L}"
        )
        PuppyDebugLog.i(
            "PupEye",
            "Actor=$pupEyeActor; generation=$saveGeneration; " +
                "privateIntegrity=${security.privateIntegrityOk}; " +
                "externalIntegrity=${security.externalIntegrityOk}; " +
                "tamperEvents=${security.tamperEvents}; hardFlags=${hardFlags.joinToString(",").ifBlank { "none" }}"
        )
        PuppyDebugLog.i(
            "Player",
            "Username=${PuppyPlayerIdentity.username(context)}; " +
                "PlayerID=${PuppyPlayerIdentity.publicPlayerId(context)}; " +
                "FriendCode=${PuppyPlayerIdentity.publicFriendCode(context)}; " +
                "level=${state.level}; xp=$playerXp; achievements=$rewardedAchievements"
        )
        PuppyDebugLog.i(
            "Economy",
            "Treats=${state.treats}; lifetime=${state.lifetimeTreats}; bones=${state.bones}; " +
                "pupCoins=${state.pupCoins}; casinoChips=${state.casinoChips}; " +
                "tickets=${state.ticketsOwned}; upgrades=${state.upgrades.values.sum()}; " +
                "prestige=${state.prestigeCount}; skillPoints=${state.skillPoints}"
        )
        PuppyDebugLog.i(
            "Casino",
            "activeRound=${casinoInspection.round?.roundId ?: "none"}; " +
                "recoveryIssue=${casinoInspection.issue ?: "none"}; completed=$completedCasinoRounds"
        )
        PuppyDebugLog.i(
            "Exchange",
            "ownership=${exchangeSnapshot.ownership.size}; " +
                "transactions=${exchangeSnapshot.transactions.size}; friends=${exchangeSnapshot.friends.size}"
        )
        PuppyDebugLog.i(
            "Support",
            "PupEye support installation code=$supportCode"
        )
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
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 0.dp)
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

        if (userAdminAuthorized) {
            Button(
                onClick = { userAdminOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Pup Accounts")
            }
            Text(
                "Server-authorized user data, inventory and system-message controls.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        ) {
            Column(Modifier.padding(11.dp)) {
                Text("Runtime snapshot", fontWeight = FontWeight.Black)
                DeveloperMetric("Build", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                DeveloperMetric("Android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                DeveloperMetric(
                    "SDK support",
                    "min ${BuildConfig.MIN_ANDROID_SDK} · target ${BuildConfig.TARGET_ANDROID_SDK} · compile ${BuildConfig.COMPILE_ANDROID_SDK}"
                )
                DeveloperMetric("Device", PuppyPlayerIdentity.deviceModel())
                DeveloperMetric("PupEye identity", pupEyeActor)
                DeveloperMetric("Save generation", saveGeneration.toString())
                DeveloperMetric(
                    "PupEye integrity",
                    "private=${if (security.privateIntegrityOk) "OK" else "FAIL"} · " +
                        "external=${if (security.externalIntegrityOk) "OK" else "FAIL"}"
                )
                DeveloperMetric("Tamper events", security.tamperEvents.toString())
                DeveloperMetric(
                    "Hard flags",
                    hardFlags.joinToString(", ").ifBlank { "None" }
                )
                DeveloperMetric("Support code", supportCode)
                DeveloperMetric("Username", PuppyPlayerIdentity.username(context))
                DeveloperMetric("Player ID", PuppyPlayerIdentity.publicPlayerId(context))
                DeveloperMetric("Friend Code", PuppyPlayerIdentity.publicFriendCode(context))
                DeveloperMetric("Discord account", discord.account?.displayName ?: "Not connected")
                DeveloperMetric("Discord role", discordRole)
                DeveloperMetric("Level / XP", "${state.level} / $playerXp")
                DeveloperMetric(
                    "Currencies",
                    "T=${state.treats} · B=${state.bones} · P=${state.pupCoins} · C=${state.casinoChips}"
                )
                DeveloperMetric("Tickets", state.ticketsOwned.toString())
                DeveloperMetric("Upgrade levels", state.upgrades.values.sum().toString())
                DeveloperMetric(
                    "Prestige",
                    "${state.prestigeCount} · skill points ${state.skillPoints}"
                )
                DeveloperMetric("Achievements", rewardedAchievements.toString())
                DeveloperMetric("Roster puppies", rosterCount.toString())
                DeveloperMetric("Unlocked puppies", state.unlockedPuppies.size.toString())
                DeveloperMetric("Active puppy", state.puppyStyle)
                DeveloperMetric(
                    "Active asset",
                    activeAsset?.let { "${it.folder}/${it.fileName}" } ?: "Missing"
                )
                DeveloperMetric(
                    "Dev Pup source",
                    devAsset?.let { "${it.folder}/${it.fileName}" } ?: "Missing"
                )
                DeveloperMetric(
                    "Discord Pup source",
                    discordAsset?.let { "${it.folder}/${it.fileName}" } ?: "Missing"
                )
                DeveloperMetric(
                    "Casino",
                    casinoInspection.round?.let { "Active ${it.game.name} · ${it.roundId}" }
                        ?: casinoInspection.issue?.let { "Recovery: $it" }
                        ?: "Idle"
                )
                DeveloperMetric("Casino completed", completedCasinoRounds.toString())
                DeveloperMetric(
                    "Exchange",
                    "${exchangeSnapshot.ownership.size} owned · " +
                        "${exchangeSnapshot.transactions.size} tx · " +
                        "${exchangeSnapshot.friends.size} friends"
                )
            }
        }

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
                    val diagnostics = buildString {
                        appendLine("Puppy Clicker Developer Diagnostics")
                        appendLine("Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("SDK support: min=${BuildConfig.MIN_ANDROID_SDK}, target=${BuildConfig.TARGET_ANDROID_SDK}, compile=${BuildConfig.COMPILE_ANDROID_SDK}")
                        appendLine("Device: ${PuppyPlayerIdentity.deviceModel()}")
                        appendLine("PupEye identity: $pupEyeActor")
                        appendLine("PupEye generation: $saveGeneration")
                        appendLine("PupEye integrity: private=${security.privateIntegrityOk}, external=${security.externalIntegrityOk}")
                        appendLine("PupEye tamper events: ${security.tamperEvents}")
                        appendLine("PupEye hard flags: ${hardFlags.joinToString(",").ifBlank { "none" }}")
                        appendLine("Support code: $supportCode")
                        appendLine("Player ID: ${PuppyPlayerIdentity.publicPlayerId(context)}")
                        appendLine("Friend Code: ${PuppyPlayerIdentity.publicFriendCode(context)}")
                        appendLine("Discord: ${discord.account?.displayName ?: "not connected"} / $discordRole")
                        appendLine("Roster: $rosterCount total, ${state.unlockedPuppies.size} unlocked")
                        appendLine("Active puppy: ${state.puppyStyle} -> ${activeAsset?.folder ?: "missing"}/${activeAsset?.fileName ?: "missing"}")
                        appendLine("Dev Pup: ${devAsset?.folder ?: "missing"}/${devAsset?.fileName ?: "missing"}")
                        appendLine("Discord Pup: ${discordAsset?.folder ?: "missing"}/${discordAsset?.fileName ?: "missing"}")
                        appendLine("Level / XP: ${state.level} / $playerXp")
                        appendLine("Economy: treats=${state.treats}, lifetime=${state.lifetimeTreats}, bones=${state.bones}, pupCoins=${state.pupCoins}, casinoChips=${state.casinoChips}")
                        appendLine("Tickets: ${state.ticketsOwned}; upgrades=${state.upgrades.values.sum()}")
                        appendLine("Prestige: ${state.prestigeCount}; skillPoints=${state.skillPoints}")
                        appendLine("Achievements rewarded: $rewardedAchievements")
                        appendLine("Casino: active=${casinoInspection.round?.roundId ?: "none"}, recovery=${casinoInspection.issue ?: "none"}, completed=$completedCasinoRounds")
                        appendLine("Exchange: ownership=${exchangeSnapshot.ownership.size}, transactions=${exchangeSnapshot.transactions.size}, friends=${exchangeSnapshot.friends.size}")
                        appendLine()
                        appendLine("Visible logs:")
                        visibleEntries.forEach { appendLine(it.toConsoleText()) }
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Puppy Clicker Developer Diagnostics", diagnostics)
                        )
                        copyStatus = "Copied runtime diagnostics and ${visibleEntries.size} visible logs."
                    } else {
                        copyStatus = "Clipboard unavailable."
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Copy Diagnostics") }
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
