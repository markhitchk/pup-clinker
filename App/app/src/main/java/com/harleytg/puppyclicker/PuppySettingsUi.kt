package com.harleytg.puppyclicker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harleytg.puppyclicker.ui.theme.LocalPuppyReducedMotion
import java.io.File
import java.text.DateFormat
import java.time.Month
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AccentPreset(val name: String, val hex: String)

private val ACCENT_PRESETS = listOf(
    AccentPreset("Puppy Cyan", "#00B8F0"),
    AccentPreset("Electric Blue", "#2979FF"),
    AccentPreset("Purple", "#8B5CF6"),
    AccentPreset("Pink", "#EC4899"),
    AccentPreset("Red", "#EF4444"),
    AccentPreset("Orange", "#F97316"),
    AccentPreset("Lime", "#84CC16"),
    AccentPreset("Green", "#22C55E")
)

private enum class SettingsDestination {
    HOME,
    PROFILE,
    DISCORD,
    TRANSFER,
    APPEARANCE,
    NOTIFICATIONS,
    GAMEPLAY,
    DATA,
    ROSTER,
    PUPEYE,
    ASSETS,
    DEVELOPER,
    ABOUT
}

@Composable
internal fun PuppySettingsScreen(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    val ui by PuppyUiPreferences.observe(context).collectAsStateWithLifecycle()
    var destinationName by rememberSaveable {
        mutableStateOf(SettingsDestination.HOME.name)
    }
    var developerConsoleOpen by rememberSaveable { mutableStateOf(false) }

    if (developerConsoleOpen) {
        PuppyDeveloperConsoleScreen(onBack = { developerConsoleOpen = false })
        return
    }

    val destination = runCatching {
        SettingsDestination.valueOf(destinationName)
    }.getOrDefault(SettingsDestination.HOME)

    fun open(next: SettingsDestination) {
        destinationName = next.name
    }

    when (destination) {
        SettingsDestination.HOME -> SettingsHome(
            state = state,
            onOpen = ::open
        )

        SettingsDestination.PROFILE -> SettingsSubpage(
            title = "Profile",
            subtitle = "Username, birthday, and Puppy Clicker identity.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            ProfileSettings(vm, ui)
        }

        SettingsDestination.DISCORD -> SettingsSubpage(
            title = "Discord",
            subtitle = "Connect or manage your Discord account.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            DiscordSettings()
        }

        SettingsDestination.TRANSFER -> SettingsSubpage(
            title = "Import / Export",
            subtitle = "Back up or restore your Puppy Clicker save.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            SaveTransferSettings(onImportSuccess = vm::reloadImportedSave)
        }

        SettingsDestination.APPEARANCE -> SettingsSubpage(
            title = "Appearance",
            subtitle = "Theme, accent color, scale, contrast, and motion.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            AppearanceSettings(ui)
        }

        SettingsDestination.NOTIFICATIONS -> SettingsSubpage(
            title = "Notifications",
            subtitle = "Choose what Puppy Clicker may alert you about.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            NotificationSettings()
        }

        SettingsDestination.GAMEPLAY -> SettingsSubpage(
            title = "Gameplay",
            subtitle = "Interaction, feedback, and gameplay preferences.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            GameplaySettings(state, vm)
        }

        SettingsDestination.DATA -> SettingsSubpage(
            title = "Data Management",
            subtitle = "Save status, backups, resets, and local data.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            SaveDataSettings(vm)
            Spacer(Modifier.height(16.dp))
            DangerZoneSettings(state, vm)
        }

        SettingsDestination.ROSTER -> SettingsSubpage(
            title = "Puppy Roster",
            subtitle = "Manage streamed puppy roster content.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            PuppyRosterSettings()
        }

        SettingsDestination.PUPEYE -> SettingsSubpage(
            title = "PupEye Protection",
            subtitle = "Fair-play, save integrity, and protection status.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            PupEyeSettings(state)
        }

        SettingsDestination.ASSETS -> SettingsSubpage(
            title = "Online Assets",
            subtitle = "Roster and streamed asset status.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            OnlineAssetSettings()
        }

        SettingsDestination.DEVELOPER -> SettingsSubpage(
            title = "Developer",
            subtitle = "Diagnostics, logs, and developer-only app tools.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            PuppyDeveloperOptions(
                onOpenConsole = { developerConsoleOpen = true }
            )
        }

        SettingsDestination.ABOUT -> SettingsSubpage(
            title = "About Puppy Clicker",
            subtitle = "App information, links, and development details.",
            onBack = { open(SettingsDestination.HOME) }
        ) {
            AboutSettings(
                onOpenDeveloperConsole = { developerConsoleOpen = true }
            )
        }
    }
}

@Composable
private fun SettingsHome(
    state: V6GameState,
    onOpen: (SettingsDestination) -> Unit
) {
    val context = LocalContext.current
    val security = PupEyeSaveGuard.state(context)
    val developer by PuppyDeveloperPreferences.observe(context).collectAsStateWithLifecycle()
    val showDeveloper = developer.unlocked || PuppyPlayerIdentity.isHarleyTgDeveloper(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            "Customize your experience, manage your account, and more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(SettingsDestination.PROFILE) },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreamedPuppyPortrait(
                    styleId = state.puppyStyle,
                    size = 66.dp,
                    accessory = state.accessory,
                    background = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        PuppyPlayerIdentity.username(context),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Local Profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (security.tamperEvents > 0) {
                            "PupEye needs attention"
                        } else {
                            "Protected by PupEye"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (security.tamperEvents > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "›",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSummaryValue(
                label = "Player ID",
                value = PuppyPlayerIdentity.publicPlayerId(context),
                modifier = Modifier.weight(1f)
            )
            SettingsSummaryValue(
                label = "Friend Code",
                value = PuppyPlayerIdentity.publicFriendCode(context),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsGroup("Account") {
            SettingsNavRow("👤", "Profile", "Username, birthday, and profile options") {
                onOpen(SettingsDestination.PROFILE)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("◉", "Discord", "Connect or manage your Discord account") {
                onOpen(SettingsDestination.DISCORD)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("↕", "Import / Export", "Back up or restore your .pupsave") {
                onOpen(SettingsDestination.TRANSFER)
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsGroup("Appearance") {
            SettingsNavRow("◐", "Theme", "Light, Dark, or System") {
                onOpen(SettingsDestination.APPEARANCE)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("●", "Accent Color", "Choose your accent color") {
                onOpen(SettingsDestination.APPEARANCE)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("↔", "UI Scale", "Compact, Default, or Large") {
                onOpen(SettingsDestination.APPEARANCE)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("◌", "Reduced Motion", "Minimize non-essential animations") {
                onOpen(SettingsDestination.APPEARANCE)
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsGroup("Notifications") {
            SettingsNavRow("🔔", "Notification Settings", "In-game alerts, updates, and more") {
                onOpen(SettingsDestination.NOTIFICATIONS)
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsGroup("Game") {
            SettingsNavRow("🎮", "Gameplay", "Preferences and gameplay options") {
                onOpen(SettingsDestination.GAMEPLAY)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("▤", "Data Management", "Back up, restore, reset, and delete data") {
                onOpen(SettingsDestination.DATA)
            }
        }

        Spacer(Modifier.height(14.dp))

        SettingsGroup("Advanced") {
            SettingsNavRow("🐶", "Puppy Roster", "Roster content and asset refresh") {
                onOpen(SettingsDestination.ROSTER)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("◉", "PupEye Protection", "Fair-play and save integrity status") {
                onOpen(SettingsDestination.PUPEYE)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("☁", "Online Assets", "Streamed asset and cache status") {
                onOpen(SettingsDestination.ASSETS)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("ⓘ", "About Puppy Clicker", "Version, links, development, and legal") {
                onOpen(SettingsDestination.ABOUT)
            }
        }

        if (showDeveloper) {
            Spacer(Modifier.height(14.dp))
            SettingsGroup("Developer") {
                SettingsNavRow("🛠", "Developer Options", "Diagnostics, app logs, and developer tools") {
                    onOpen(SettingsDestination.DEVELOPER)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSubpage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
        ) {
            Text("‹ Settings", fontWeight = FontWeight.Bold)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSummaryValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black
    )
    Spacer(Modifier.height(7.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(11.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(icon, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileSettings(
    vm: PuppyClickerV6ViewModel,
    ui: PuppyUiState
) {
    val context = LocalContext.current
    var username by rememberSaveable {
        mutableStateOf(PuppyPlayerIdentity.username(context))
    }
    var usernameModerationMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var savedMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var birthdayEditor by rememberSaveable { mutableStateOf(false) }
    val officialDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Local Profile", fontWeight = FontWeight.Black)
            Text(
                "Your Puppy Clicker identity stays device-bound.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            StatusLine("Player ID", PuppyPlayerIdentity.publicPlayerId(context))
            StatusLine("Friend Code", PuppyPlayerIdentity.publicFriendCode(context))
            if (officialDeveloper) {
                StatusLine("Account", "HarleyTG Developer / Owner")
                StatusLine("Studio", "Harley's Studios")
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = username,
        onValueChange = {
            username = it.take(24)
            savedMessage = null
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Puppy Clicker display name") },
        supportingText = {
            Text("Letters, numbers, _, - and . · up to 24 characters")
        },
        singleLine = true
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            val issue = PuppyPlayerIdentity.usernameModerationIssue(username)
            if (issue != null) {
                usernameModerationMessage = issue
            } else {
                username = PuppyPlayerIdentity.setUsername(context, username)
                savedMessage = "Display name saved."
            }
        },
        enabled = PuppyPlayerIdentity.normalizeUsername(username).isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Save Display Name")
    }
    savedMessage?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(Modifier.height(16.dp))
    SettingsLabel("BIRTHDAY")
    val birthdayText = if (PuppyBirthday.isValid(ui.birthdayMonth, ui.birthdayDay)) {
        monthName(ui.birthdayMonth) + " " + ui.birthdayDay
    } else {
        "Not set"
    }
    StatusLine("Birthday", birthdayText)
    Text(
        "Only the month and day are stored locally and they are not shown publicly.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { birthdayEditor = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            if (PuppyBirthday.isValid(ui.birthdayMonth, ui.birthdayDay)) {
                "Edit Birthday"
            } else {
                "Add Birthday"
            }
        )
    }

    if (birthdayEditor) {
        BirthdayEditorDialog(
            initialMonth = ui.birthdayMonth,
            initialDay = ui.birthdayDay,
            onDismiss = { birthdayEditor = false },
            onSave = { month, day ->
                if (PuppyUiPreferences.setBirthday(context, month, day)) {
                    vm.setSeasonalBirthday(month, day)
                    birthdayEditor = false
                }
            }
        )
    }

    if (usernameModerationMessage != null) {
        AlertDialog(
            onDismissRequest = { usernameModerationMessage = null },
            title = {
                Text("Username Not Allowed", fontWeight = FontWeight.Black)
            },
            text = {
                Text(
                    usernameModerationMessage
                        ?: "That username isn't allowed. Choose another username."
                )
            },
            confirmButton = {
                TextButton(onClick = { usernameModerationMessage = null }) {
                    Text("Choose Another")
                }
            }
        )
    }
}

@Composable
private fun DiscordSettings() {
    val context = LocalContext.current
    val discord by DiscordSignupAuth.observe(context).collectAsStateWithLifecycle()
    val account = discord.account
    val busy = discord.phase == DiscordSignupPhase.AUTHORIZING ||
        discord.phase == DiscordSignupPhase.EXCHANGING

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (account == null) "Discord not connected" else "Discord connected",
                fontWeight = FontWeight.Black
            )
            if (account != null) {
                StatusLine("Name", account.displayName)
                StatusLine("Username", "@" + account.username)
                StatusLine("User ID", account.id)
            } else {
                Text(
                    "Connect Discord for identity and community features. Only the identify permission is requested.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    Button(
        onClick = { DiscordSignupAuth.startSignup(context) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            when {
                busy -> "Waiting for Discord…"
                account != null -> "Reconnect Discord"
                else -> "Connect Discord"
            }
        )
    }

    if (account != null) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { DiscordSignupAuth.disconnect(context) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect Discord")
        }
    }

    discord.message?.let {
        Spacer(Modifier.height(8.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (discord.phase == DiscordSignupPhase.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    key: String,
    icon: String,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalPuppyReducedMotion.current
    val duration = if (reduceMotion) 1 else 190
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(duration),
        label = "settings-$key-chevron"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 15.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.width(11.dp))
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Black)
                Text(
                    "›",
                    modifier = Modifier.rotate(arrowRotation),
                    fontSize = 30.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 15.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettings(ui: PuppyUiState) {
    val context = LocalContext.current
    var customPickerOpen by rememberSaveable { mutableStateOf(false) }

    SettingsLabel("THEME")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        PuppyThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                PuppyThemeMode.LIGHT -> "Light"
                PuppyThemeMode.DARK -> "Dark"
                PuppyThemeMode.SYSTEM -> "Follow System"
            }
            FilterChip(
                selected = ui.themeMode == mode,
                onClick = { PuppyUiPreferences.setThemeMode(context, mode) },
                label = { Text(label) }
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SettingsLabel("ACCENT COLOR")
    Text(
        "Current: ${ACCENT_PRESETS.firstOrNull { it.hex.equals(ui.accentHex, true) }?.name ?: ui.accentHex}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ACCENT_PRESETS.forEach { preset ->
            AccentSwatch(
                preset = preset,
                selected = preset.hex.equals(ui.accentHex, true),
                onClick = { PuppyUiPreferences.setAccent(context, preset.hex) }
            )
        }
    }
    Spacer(Modifier.height(9.dp))
    OutlinedButton(onClick = { customPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Custom Color")
    }

    Spacer(Modifier.height(14.dp))
    AppearancePreview(ui)

    Spacer(Modifier.height(14.dp))
    SettingsLabel("INTERFACE")
    InlineSwitch("Animated UI", "Optional interface transitions and decorative movement.", ui.animatedUi) {
        PuppyUiPreferences.setAnimatedUi(context, it)
    }
    InlineSwitch("Button animations", "Small press and interaction motion.", ui.buttonAnimations) {
        PuppyUiPreferences.setButtonAnimations(context, it)
    }
    InlineSwitch("Reduced motion", "Minimizes non-essential movement and shortens expansion animations.", ui.reducedMotion) {
        PuppyUiPreferences.setReducedMotion(context, it)
    }
    InlineSwitch("High contrast", "Strengthens separation between text, cards and controls.", ui.highContrast) {
        PuppyUiPreferences.setHighContrast(context, it)
    }

    Spacer(Modifier.height(8.dp))
    Text("UI scale", fontWeight = FontWeight.Bold)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        PuppyUiScale.entries.forEach { scale ->
            FilterChip(
                selected = ui.uiScale == scale,
                onClick = { PuppyUiPreferences.setUiScale(context, scale) },
                label = {
                    Text(
                        when (scale) {
                            PuppyUiScale.COMPACT -> "Compact"
                            PuppyUiScale.DEFAULT -> "Default"
                            PuppyUiScale.LARGE -> "Large"
                        }
                    )
                }
            )
        }
    }

    if (customPickerOpen) {
        CustomAccentDialog(
            initial = ui.customAccentHex.ifBlank { ui.accentHex },
            onDismiss = { customPickerOpen = false },
            onApply = { value ->
                PuppyUiPreferences.setAccent(context, value, custom = true)
                customPickerOpen = false
            }
        )
    }
}

@Composable
private fun AccentSwatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit) {
    val color = parseComposeColor(preset.hex)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(42.dp).clickable(onClick = onClick),
            shape = CircleShape,
            color = color,
            border = BorderStroke(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
        ) {
            if (selected) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("✓", color = readableText(color), fontWeight = FontWeight.Black)
                }
            }
        }
        Text(preset.name.substringBefore(' '), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun AppearancePreview(ui: PuppyUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("LIVE PREVIEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(7.dp))
            Text("Puppy Clicker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Theme preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(9.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Accent Preview") }
            Text(
                "${when (ui.themeMode) { PuppyThemeMode.LIGHT -> "Light"; PuppyThemeMode.DARK -> "Dark"; PuppyThemeMode.SYSTEM -> "System" }} · ${ui.accentHex}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CustomAccentDialog(initial: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val normalizedInitial = PuppyUiPreferences.normalizeHex(initial) ?: PuppyUiPreferences.DEFAULT_ACCENT
    val initialInt = AndroidColor.parseColor(normalizedInitial)
    val hsvArray = remember(normalizedInitial) { FloatArray(3).also { AndroidColor.colorToHSV(initialInt, it) } }
    var hue by remember(normalizedInitial) { mutableFloatStateOf(hsvArray[0]) }
    var saturation by remember(normalizedInitial) { mutableFloatStateOf(hsvArray[1]) }
    var value by remember(normalizedInitial) { mutableFloatStateOf(hsvArray[2]) }
    var hex by rememberSaveable(normalizedInitial) { mutableStateOf(normalizedInitial) }

    fun syncHex() {
        hex = hsvToHex(hue, saturation, value)
    }

    val validHex = PuppyUiPreferences.normalizeHex(hex)
    val preview = parseComposeColor(validHex ?: hsvToHex(hue, saturation, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Accent", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = preview
                ) {}
                Spacer(Modifier.height(12.dp))
                Text("Hue · ${hue.toInt()}°", fontWeight = FontWeight.Bold)
                Slider(value = hue, onValueChange = { hue = it; syncHex() }, valueRange = 0f..360f)
                Text("Saturation · ${(saturation * 100).toInt()}%", fontWeight = FontWeight.Bold)
                Slider(value = saturation, onValueChange = { saturation = it; syncHex() }, valueRange = 0f..1f)
                Text("Brightness · ${(value * 100).toInt()}%", fontWeight = FontWeight.Bold)
                Slider(value = value, onValueChange = { value = it; syncHex() }, valueRange = 0f..1f)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { entered ->
                        hex = entered.take(7)
                        PuppyUiPreferences.normalizeHex(hex)?.let { accepted ->
                            val color = AndroidColor.parseColor(accepted)
                            val next = FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
                            hue = next[0]
                            saturation = next[1]
                            value = next[2]
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HEX") },
                    supportingText = {
                        Text(if (validHex != null) "Six-digit RGB HEX" else "Enter a value like #00B8F0")
                    },
                    isError = validHex == null,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { validHex?.let(onApply) }, enabled = validHex != null) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GameplaySettings(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    val ui by PuppyUiPreferences.observe(context).collectAsStateWithLifecycle()
    InlineSwitch("Haptic feedback", "Android vibration feedback for supported interactions.", state.hapticsEnabled, vm::setHapticsEnabled)
    if (state.hapticsEnabled) {
        OutlinedButton(onClick = { performV6Haptic(context, true) }, modifier = Modifier.fillMaxWidth()) { Text("Test Haptic") }
    }
    InlineSwitch("Tap animations", "Puppy idle and tap motion.", state.animationsEnabled, vm::setAnimationsEnabled)
    InlineSwitch("Compact numbers", "Use K/M/B abbreviations for large values.", state.compactNumbers, vm::setCompactNumbers)
    InlineSwitch("Reduced visual effects", "Uses the same reduced-motion preference as Appearance.", ui.reducedMotion) {
        PuppyUiPreferences.setReducedMotion(context, it)
    }
    StatusLine("Auto-save", "Active")
    Text(
        "Game progress continues using Puppy Clicker's existing save system.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun NotificationSettings() {
    val context = LocalContext.current
    val ui by PuppyUiPreferences.observe(context).collectAsStateWithLifecycle()
    var afkEnabled by remember {
        mutableStateOf(PuppyAttentionNotifier.isEnabled(context))
    }
    var permissionGranted by remember {
        mutableStateOf(PuppyNotificationCenter.canNotify(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        PuppyNotificationCenter.schedule(context)
        if (granted) {
            PuppyNotificationCenter.requestImmediate(context)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Notification Permission", fontWeight = FontWeight.Black)
            Text(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || permissionGranted) {
                    "Android notifications are allowed."
                } else {
                    "Android may ask for permission to send notifications. You can change this at any time."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !permissionGranted
            ) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow Notifications")
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    SettingsToggleCard(
        icon = "🎁",
        title = "Daily Rewards",
        description = "Get notified when daily rewards are available.",
        checked = ui.dailyRewardNotifications
    ) {
        PuppyUiPreferences.setDailyRewardNotifications(context, it)
        PuppyNotificationCenter.schedule(context)
        if (it) {
            PuppyNotificationCenter.requestImmediate(context)
        } else {
            PuppyNotificationCenter.cancelDailyReward(context)
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsToggleCard(
        icon = "🐾",
        title = "Game Events",
        description = "Seasonal and important in-game events.",
        checked = ui.gameEventNotifications
    ) {
        PuppyUiPreferences.setGameEventNotifications(context, it)
        PuppyNotificationCenter.schedule(context)
        if (it) {
            PuppyNotificationCenter.requestImmediate(context)
        } else {
            PuppyNotificationCenter.cancelParkReady(context)
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsToggleCard(
        icon = "📣",
        title = "App Updates",
        description = "Important Puppy Clicker update notices.",
        checked = ui.updateNotifications
    ) {
        PuppyUiPreferences.setUpdateNotifications(context, it)
        PuppyNotificationCenter.schedule(context)
        if (it) {
            PuppyNotificationCenter.requestImmediate(context)
        } else {
            PuppyNotificationCenter.cancelAppUpdate(context)
        }
    }

    Spacer(Modifier.height(8.dp))

    SettingsToggleCard(
        icon = "🐶",
        title = "Puppy Attention",
        description = "AFK puppy attention reminders while the app is away.",
        checked = afkEnabled
    ) { enabled ->
        afkEnabled = enabled
        PuppyAttentionNotifier.setEnabled(context, enabled)
    }

    Spacer(Modifier.height(12.dp))

    val anyEnabled = ui.dailyRewardNotifications ||
        ui.gameEventNotifications ||
        ui.updateNotifications ||
        afkEnabled
    val systemReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        permissionGranted

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (anyEnabled && systemReady) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (anyEnabled && systemReady) {
                    "✓ Notifications are ready!"
                } else if (!anyEnabled) {
                    "Notifications are off"
                } else {
                    "Notification permission is required"
                },
                fontWeight = FontWeight.Black
            )
            Text(
                "You can change these choices at any time in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(Modifier.height(10.dp))

    OutlinedButton(
        onClick = { PuppyNotificationCenter.requestImmediate(context) },
        enabled = systemReady,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Check Notifications Now")
    }
}

@Composable
private fun SettingsToggleCard(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    description,
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
}

@Composable
private fun PuppyRosterSettings() {
    val context = LocalContext.current
    val groups by DynamicPuppyRoster.groups.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsLabel("PUPPY ASSETS")
    if (groups.isEmpty()) {
        StatusLine("Roster", "Unavailable")
    } else {
        groups.forEach { group ->
            StatusLine(group.title, "${group.puppies.size} discovered")
        }
    }
    val checked = DynamicPuppyRoster.lastCheckedAt(context)
    StatusLine("Last roster verification", formatTimestamp(checked))
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            if (!refreshing) scope.launch {
                refreshing = true
                val online = DynamicPuppyRoster.refreshNow(context.applicationContext)
                result = if (online) "Roster service verified online." else "Online roster could not be verified; cached/bundled roster remains available."
                refreshing = false
            }
        },
        enabled = !refreshing,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (refreshing) "Refreshing…" else "Refresh Puppy Assets") }
    result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun PupEyeSettings(state: V6GameState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var integrityStatus by rememberSaveable { mutableStateOf("Not checked") }
    var externalStatus by rememberSaveable { mutableStateOf("Not checked") }
    val security = PupEyeSaveGuard.state(context)
    val branding = PupEyeAssetStream.status(context)
    val fairPlayEnforced = PuppyPlayerIdentity.shouldEnforcePupEyeFairPlay(context)

    Row(verticalAlignment = Alignment.CenterVertically) {
        StreamedPupEyeBranding(Modifier.size(54.dp), "PupEye Protection")
        Spacer(Modifier.width(10.dp))
        Column {
            Text("PupEye Protection", fontWeight = FontWeight.Black)
            Text(
                when {
                    checking -> "Checking"
                    integrityStatus == "Verified" && externalStatus == "Verified" -> "Protected"
                    security.tamperEvents > 0 -> "Warning"
                    else -> "Available"
                },
                color = if (security.tamperEvents > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    StatusLine("Save file integrity", integrityStatus)
    StatusLine("Android/data save integrity", externalStatus)
    StatusLine("Device-bound encryption", "Configured")
    StatusLine("Save integrity protection", "Always Active")
    StatusLine(
        "Fair-play enforcement",
        if (fairPlayEnforced) "Active · Player" else "Developer Exempt"
    )
    StatusLine(
        "External click detection",
        if (fairPlayEnforced) "Enabled" else "Developer Exempt"
    )
    StatusLine("PupEye branding", if (branding.ready) "Ready (cached/streamed)" else "Unavailable")
    StatusLine("Confirmed clicker cooldowns", state.pupEyeStrikes.toString())
    StatusLine("Save integrity events", security.tamperEvents.toString())
    security.lastReason?.takeIf { security.tamperEvents > 0 }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            if (!checking) scope.launch {
                checking = true
                val (privateOk, externalOk) = withContext(Dispatchers.IO) {
                    val prefs = context.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
                    PupEyeSaveGuard.verifyAndRecover(context, prefs) to ExternalGameSave.verifyExisting(context)
                }
                integrityStatus = if (privateOk) "Verified" else "Warning"
                externalStatus = if (externalOk) "Verified" else "Warning"
                checking = false
            }
        },
        enabled = !checking,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (checking) "Checking…" else "Run Security Check") }
}

@Composable
private fun SaveDataSettings(vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    val externalPath = ExternalGameSave.path(context)
    val externalFile = externalPath?.let(::File)
    val security = PupEyeSaveGuard.state(context)

    SettingsLabel("CURRENT SAVE")
    StatusLine("Player", PuppyPlayerIdentity.username(context))
    StatusLine("Encryption", "AES-256-GCM")
    StatusLine("PupEye integrity events", security.tamperEvents.toString())
    StatusLine(
        "Android/data mirror",
        if (externalFile?.isFile == true) "Saved" else "Not currently available"
    )
    if (externalFile?.isFile == true) {
        StatusLine("Last mirrored", formatTimestamp(externalFile.lastModified()))
    }
    externalPath?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(10.dp))
    SaveTransferSettings(onImportSuccess = vm::reloadImportedSave)
}

@Composable
private fun OnlineAssetSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups by DynamicPuppyRoster.groups.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    var onlineState by rememberSaveable { mutableStateOf("Not checked") }
    var lastResult by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshSerial by remember { mutableIntStateOf(0) }

    val pupEye = remember(refreshSerial) { PupEyeAssetStream.status(context) }
    val rosterChecked = remember(refreshSerial) { DynamicPuppyRoster.lastCheckedAt(context) }

    SettingsLabel("ONLINE CONTENT")
    StatusLine("Asset service", onlineState)
    StatusLine("Puppy roster", if (groups.isNotEmpty()) "${groups.size} roster groups available" else "Unavailable")
    StatusLine("PupEye", if (pupEye.ready) "Ready" else "Unavailable")
    StatusLine("PupEye last verified", formatTimestamp(pupEye.lastCheckedAtMs))
    StatusLine("Roster last verified", formatTimestamp(rosterChecked))
    StatusLine("Discord icon", "Local app resource")
    StatusLine("Puppy Clicker logo", "Local app resource")
    Spacer(Modifier.height(8.dp))

    Button(
        onClick = {
            if (!refreshing) scope.launch {
                refreshing = true
                val rosterOnline = DynamicPuppyRoster.refreshNow(context.applicationContext)
                val pupEyeOnline = PupEyeAssetStream.refreshNow(context.applicationContext)
                onlineState = if (rosterOnline || pupEyeOnline) "Online" else "Unavailable"
                lastResult = when {
                    rosterOnline && pupEyeOnline -> "Roster and PupEye streams verified."
                    rosterOnline -> "Roster stream verified; PupEye is using its last valid cache or placeholder."
                    pupEyeOnline -> "PupEye stream verified; roster is using its available fallback/cache."
                    else -> "Remote assets could not be verified. Puppy Clicker remains usable with available cached/fallback content."
                }
                refreshSerial++
                refreshing = false
            }
        },
        enabled = !refreshing,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (refreshing) "Refreshing…" else "Refresh Assets") }
    Spacer(Modifier.height(7.dp))
    OutlinedButton(
        onClick = {
            PupEyeAssetStream.clearCache(context.applicationContext)
            RemotePuppyAssets.clearCache(context.applicationContext)
            onlineState = "Not checked"
            lastResult = "Streamed image cache cleared. Assets will reload asynchronously when needed."
            refreshSerial++
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Clear Asset Cache") }
    lastResult?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountProfileSettings(vm: PuppyClickerV6ViewModel, ui: PuppyUiState) {
    val context = LocalContext.current
    val discord by DiscordSignupAuth.observe(context).collectAsStateWithLifecycle()
    val account = discord.account
    var username by rememberSaveable { mutableStateOf(PuppyPlayerIdentity.username(context)) }
    var usernameStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var birthdayEditor by rememberSaveable { mutableStateOf(false) }
    val officialDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)
    val busy = discord.phase == DiscordSignupPhase.AUTHORIZING ||
        discord.phase == DiscordSignupPhase.EXCHANGING

    SettingsLabel("DISCORD ACCOUNT")
    StatusLine("Status", if (account != null) "Connected" else "Not Connected")
    account?.let {
        StatusLine("Discord name", it.displayName)
        StatusLine("Discord username", "@${it.username}")
        StatusLine("Discord user ID", it.id)
    }
    Spacer(Modifier.height(7.dp))
    Button(
        onClick = { DiscordSignupAuth.startSignup(context) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            when {
                busy -> "Waiting for Discord…"
                account != null -> "Reconnect Discord"
                else -> "Connect Discord"
            }
        )
    }
    if (account != null) {
        Spacer(Modifier.height(7.dp))
        OutlinedButton(
            onClick = { DiscordSignupAuth.disconnect(context) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Disconnect Discord")
        }
    }
    discord.message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (discord.phase == DiscordSignupPhase.ERROR) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        "Discord is used for player signup. Only the identify permission is requested; Puppy Clicker does not persist the OAuth access token.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(14.dp))
    SettingsLabel("PUPPY CLICKER IDENTITY")
    if (officialDeveloper) {
        StatusLine("Account", "HarleyTG Developer / Owner")
        StatusLine("Studio", "Harley's Studios")
    }
    StatusLine("Player ID", PuppyPlayerIdentity.publicPlayerId(context))
    StatusLine("Friend Code", PuppyPlayerIdentity.publicFriendCode(context))
    StatusLine("Identity", if (officialDeveloper) "Official Developer · Device Bound" else "Device Bound")
    Spacer(Modifier.height(9.dp))
    OutlinedTextField(
        value = username,
        onValueChange = { username = it.take(24) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Puppy Clicker display name") },
        supportingText = { Text("Defaults to your verified Discord username and may be changed locally.") },
        singleLine = true
    )
    Spacer(Modifier.height(7.dp))
    OutlinedButton(
        onClick = {
            val issue = PuppyPlayerIdentity.usernameModerationIssue(username)
            if (issue != null) {
                usernameStatus = issue
            } else {
                username = PuppyPlayerIdentity.setUsername(context, username)
                usernameStatus = "Display name saved as $username."
            }
        },
        enabled = PuppyPlayerIdentity.normalizeUsername(username).isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Save Display Name") }
    usernameStatus?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (PuppyPlayerIdentity.usernameModerationIssue(username) != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(12.dp))
    SettingsLabel("BIRTHDAY")
    val birthdayText = if (PuppyBirthday.isValid(ui.birthdayMonth, ui.birthdayDay)) {
        "${monthName(ui.birthdayMonth)} ${ui.birthdayDay}"
    } else {
        "Not set"
    }
    StatusLine("Birthday", birthdayText)
    Text(
        "Your birthday month and day are used for birthday features. They are not publicly shown to other players and stay in the app's local preference storage.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(7.dp))
    OutlinedButton(onClick = { birthdayEditor = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (PuppyBirthday.isValid(ui.birthdayMonth, ui.birthdayDay)) "Edit Birthday" else "Add Birthday")
    }

    if (birthdayEditor) {
        BirthdayEditorDialog(
            initialMonth = ui.birthdayMonth,
            initialDay = ui.birthdayDay,
            onDismiss = { birthdayEditor = false },
            onSave = { month, day ->
                if (PuppyUiPreferences.setBirthday(context, month, day)) {
                    vm.setSeasonalBirthday(month, day)
                    birthdayEditor = false
                }
            }
        )
    }
}

@Composable
internal fun BirthdayEditorDialog(
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var month by rememberSaveable { mutableIntStateOf(initialMonth.coerceIn(0, 12)) }
    var day by rememberSaveable { mutableIntStateOf(initialDay.coerceIn(0, 31)) }
    val maxDay = PuppyBirthday.maxDay(month).takeIf { it > 0 } ?: 31
    if (day > maxDay) day = 0
    val valid = PuppyBirthday.isValid(month, day)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your Birthday 🎂", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("Choose your birthday month and day. No birth year is stored.")
                Spacer(Modifier.height(10.dp))
                BirthdayPicker(
                    label = if (month == 0) "Month" else monthName(month),
                    options = (1..12).map { it to monthName(it) },
                    onSelect = { selected ->
                        month = selected
                        if (day > PuppyBirthday.maxDay(selected)) day = 0
                    }
                )
                Spacer(Modifier.height(7.dp))
                BirthdayPicker(
                    label = if (day == 0) "Day" else day.toString(),
                    options = (1..maxDay).map { it to it.toString() },
                    onSelect = { day = it }
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your birthday month and day are not displayed publicly to other players.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(month, day) }, enabled = valid) { Text("Save Birthday") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BirthdayPicker(label: String, options: List<Pair<Int, String>>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun AboutSettings(
    onOpenDeveloperConsole: () -> Unit
) {
    val context = LocalContext.current
    val developer by PuppyDeveloperPreferences.observe(context)
        .collectAsStateWithLifecycle()
    var buildTapCount by rememberSaveable { mutableIntStateOf(0) }
    var developerStatus by rememberSaveable { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.source_logo),
                contentDescription = "Puppy Clicker logo",
                modifier = Modifier.size(112.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Puppy Clicker",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text("Version " + BuildConfig.VERSION_NAME)
            Text(
                "By Harley's Studios",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Tap, care for puppies, build your collection, and make the game yours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            SettingsInfoRow(
                title = "What is Puppy Clicker?",
                subtitle = "A local-first puppy clicker and collection game."
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsValueActionRow(
                title = "Build",
                value = BuildConfig.VERSION_CODE.toString()
            ) {
                val progress = nextDeveloperUnlockProgress(
                    currentTapCount = buildTapCount,
                    alreadyUnlocked = developer.unlocked
                )
                buildTapCount = progress.tapCount
                when {
                    progress.unlocked && !developer.unlocked -> {
                        PuppyDeveloperPreferences.setUnlocked(context, true)
                        PuppyDebugLog.i(
                            "DeveloperMode",
                            "Developer Mode unlocked from About"
                        )
                        developerStatus = "Developer Mode unlocked."
                    }
                    developer.unlocked -> {
                        developerStatus = "Developer Mode is already unlocked."
                    }
                    progress.remainingTaps <= 3 -> {
                        val suffix = if (progress.remainingTaps == 1) "" else "s"
                        developerStatus =
                            progress.remainingTaps.toString() +
                                " more tap" +
                                suffix +
                                " to unlock Developer Mode."
                    }
                }
            }
        }
    }

    developerStatus?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    Spacer(Modifier.height(14.dp))
    SettingsLabel("DEVELOPMENT")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Puppy Clicker Development",
                fontWeight = FontWeight.Black
            )
            Text(
                "Follow planned features, work in progress, known tasks, and upcoming changes on the official Puppy Clicker Trello board.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { PuppyLinks.openRoadmap(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Development Roadmap")
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SettingsLabel("COMMUNITY")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        SettingsInfoRow(
            title = "Discord Server",
            subtitle = "Join the Puppy Clicker community.",
            onClick = { PuppyLinks.openDiscord(context) }
        )
    }

    // Developer tools have their own top-level Settings category. Keep the callback
    // parameter here so the generated Developer Console compatibility patch can
    // recognize this routed Settings implementation without restoring the old card.
    @Suppress("UNUSED_VARIABLE")
    val developerConsoleRoute = onOpenDeveloperConsole

    Spacer(Modifier.height(14.dp))
    SettingsLabel("CREDITS")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.harleys_studios_icon),
                contentDescription = "Harley's Studios",
                modifier = Modifier.size(46.dp)
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Built by Harley's Studios", fontWeight = FontWeight.Black)
                Text(
                    "With help from the community. Thank you for playing!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SettingsLabel("LEGAL")
    PuppyLegalLinks(
        modifier = Modifier.fillMaxWidth(),
        acknowledgementText =
            "Terms and privacy open inside Puppy Clicker using the repository-backed legal document viewer."
    )
}

@Composable
private fun SettingsInfoRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsValueActionRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun DangerZoneSettings(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    var confirmationKey by rememberSaveable { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(
                "DANGER ZONE",
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "Every destructive action requires an uninterrupted 10-second hold.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = { confirmationKey = DangerZoneAction.RESET_SETTINGS.key },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Settings")
            }
            Spacer(Modifier.height(7.dp))
            OutlinedButton(
                onClick = { confirmationKey = DangerZoneAction.RESET_PROGRESS.key },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Game Progress")
            }
            Spacer(Modifier.height(7.dp))
            Button(
                onClick = { confirmationKey = DangerZoneAction.ERASE_ALL_DATA.key },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete Local Save Data")
            }
        }
    }

    val action = confirmationKey?.let { DangerZoneAction.fromKey(it) }
    if (action != null) {
        DangerHoldConfirmationDialog(
            action = action,
            onDismiss = { confirmationKey = null },
            onConfirmed = {
                when (action) {
                    DangerZoneAction.RESET_SETTINGS -> {
                        PuppyUiPreferences.resetInterfaceSettings(context)
                        vm.setHapticsEnabled(true)
                        vm.setAnimationsEnabled(true)
                        vm.setCompactNumbers(true)
                    }
                    DangerZoneAction.RESET_PROGRESS -> vm.resetRunWithoutPrestige()
                    DangerZoneAction.ERASE_ALL_DATA -> {
                        vm.prepareForFullLocalDataErase()
                        deletePuppyClickerLocalSave(context)

                        val restartIntent = Intent(context, PuppyClickerV6Activity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(restartIntent)
                        activity?.finish()
                    }
                }
                confirmationKey = null
            }
        )
    }
}

private fun deletePuppyClickerLocalSave(context: Context) {
    context.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("puppy_seasonal_v1", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("puppy_player_identity_v1", Context.MODE_PRIVATE).edit().clear().commit()
    DiscordSignupAuth.disconnect(context)
    PuppyBackupPasswordStore.clear(context)
    context.getSharedPreferences("pupeye_security_v1", Context.MODE_PRIVATE).edit().clear().commit()
    File(context.noBackupFilesDir, "pupeye/last_good_save.pup").delete()
    ExternalGameSave.path(context)?.let { File(it).delete() }
    PuppyDeveloperPreferences.clear(context)
    PuppyUiPreferences.prepareFreshSetupAfterDelete(context)
}

@Composable
private fun InlineSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ComingSoonRow(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Coming Soon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(5.dp))
}

private fun parseComposeColor(hex: String): Color = runCatching {
    Color(AndroidColor.parseColor(PuppyUiPreferences.normalizeHex(hex) ?: PuppyUiPreferences.DEFAULT_ACCENT))
}.getOrDefault(Color(0xFF00B8F0))

private fun readableText(color: Color): Color = if (color.luminance() > 0.52f) Color(0xFF071014) else Color.White

private fun hsvToHex(hue: Float, saturation: Float, value: Float): String {
    val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    return String.format(Locale.US, "#%06X", color and 0xFFFFFF)
}

private fun monthName(month: Int): String = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

private fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return "Not verified"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
}
