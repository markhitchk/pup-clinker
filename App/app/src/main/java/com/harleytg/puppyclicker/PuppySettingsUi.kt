package com.harleytg.puppyclicker

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@Composable
internal fun PuppySettingsScreen(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    val ui by PuppyUiPreferences.observe(context).collectAsStateWithLifecycle()
    var expandedKeys by rememberSaveable { mutableStateOf("appearance") }

    fun isExpanded(key: String): Boolean = expandedKeys.split('|').filter { it.isNotBlank() }.contains(key)
    fun toggle(key: String) {
        val keys = expandedKeys.split('|').filter { it.isNotBlank() }.toMutableList()
        if (key in keys) keys.remove(key) else keys.add(key)
        expandedKeys = keys.distinct().joinToString("|")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "Customize Puppy Clicker without leaving the game.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        SettingsSectionCard("appearance", "🎨", "Appearance", isExpanded("appearance"), { toggle("appearance") }) {
            AppearanceSettings(ui)
        }
        SettingsSectionCard("gameplay", "🎮", "Gameplay", isExpanded("gameplay"), { toggle("gameplay") }) {
            GameplaySettings(state, vm)
        }
        SettingsSectionCard("notifications", "🔔", "Notifications", isExpanded("notifications"), { toggle("notifications") }) {
            NotificationSettings()
        }
        SettingsSectionCard("roster", "🐶", "Puppy Roster", isExpanded("roster"), { toggle("roster") }) {
            PuppyRosterSettings()
        }
        SettingsSectionCard("pupeye", "👁", "PupEye Protection", isExpanded("pupeye"), { toggle("pupeye") }) {
            PupEyeSettings(state)
        }
        SettingsSectionCard("save", "💾", "Save & Data", isExpanded("save"), { toggle("save") }) {
            SaveDataSettings(vm)
        }
        SettingsSectionCard("assets", "☁", "Online Assets", isExpanded("assets"), { toggle("assets") }) {
            OnlineAssetSettings()
        }
        SettingsSectionCard("account", "👤", "Account & Profile", isExpanded("account"), { toggle("account") }) {
            AccountProfileSettings(vm, ui)
        }
        SettingsSectionCard("about", "ℹ", "About Puppy Clicker", isExpanded("about"), { toggle("about") }) {
            AboutSettings()
        }

        Spacer(Modifier.height(12.dp))
        DangerZoneSettings(state, vm)
        Spacer(Modifier.height(26.dp))
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
    var afkEnabled by remember { mutableStateOf(PuppyAttentionNotifier.isEnabled(context)) }
    var permissionGranted by remember { mutableStateOf(PuppyNotificationCenter.canNotify(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (granted) {
            PuppyNotificationCenter.schedule(context)
            PuppyNotificationCenter.requestImmediate(context)
        }
    }

    Text("ANDROID PERMISSION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
    StatusLine(
        "Notification permission",
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || permissionGranted) "Allowed" else "Disabled by Android"
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted) {
        Text(
            "Allow Android notifications to receive AFK, daily reward, game event, and app update alerts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(7.dp))
        OutlinedButton(
            onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Allow Notifications") }
    }

    Spacer(Modifier.height(12.dp))
    SettingsLabel("NOTIFICATION TYPES")
    InlineSwitch("AFK reward / puppy attention", "Reminder while Puppy Clicker is away.", afkEnabled) { enabled ->
        afkEnabled = enabled
        PuppyAttentionNotifier.setEnabled(context, enabled)
    }
    InlineSwitch("Daily reward notifications", "One reminder when the current day's reward is available.", ui.dailyRewardNotifications) {
        PuppyUiPreferences.setDailyRewardNotifications(context, it)
        PuppyNotificationCenter.schedule(context)
        if (it) PuppyNotificationCenter.requestImmediate(context) else PuppyNotificationCenter.cancelDailyReward(context)
    }
    InlineSwitch("Game event notifications", "Alerts when timed game events such as Dog Park adventures are ready.", ui.gameEventNotifications) {
        PuppyUiPreferences.setGameEventNotifications(context, it)
        PuppyNotificationCenter.schedule(context)
        if (it) PuppyNotificationCenter.requestImmediate(context) else PuppyNotificationCenter.cancelParkReady(context)
    }
    InlineSwitch("App update notifications", "Checks the official Puppy Clicker repository for a newer Android build.", ui.updateNotifications) {
        PuppyUiPreferences.setUpdateNotifications(context, it)
        PuppyNotificationCenter.schedule(context)
        if (it) PuppyNotificationCenter.requestImmediate(context) else PuppyNotificationCenter.cancelAppUpdate(context)
    }

    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { PuppyNotificationCenter.requestImmediate(context) },
        enabled = permissionGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Check Notifications Now") }
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
    StatusLine("External click detection", "Enabled")
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
    var username by rememberSaveable { mutableStateOf(PuppyPlayerIdentity.username(context)) }
    var usernameStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var birthdayEditor by rememberSaveable { mutableStateOf(false) }
    val officialDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)

    SettingsLabel("PROFILE")
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
        label = { Text("Username") },
        supportingText = { Text("Saved using Puppy Clicker's existing lowercase username format.") },
        singleLine = true
    )
    Spacer(Modifier.height(7.dp))
    OutlinedButton(
        onClick = {
            val normalized = PuppyPlayerIdentity.normalizeUsername(username)
            if (normalized.isBlank()) {
                usernameStatus = "Enter a valid username."
            } else {
                username = PuppyPlayerIdentity.setUsername(context, username)
                usernameStatus = "Username saved as $username."
            }
        },
        enabled = PuppyPlayerIdentity.normalizeUsername(username).isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Save Username") }
    usernameStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

    Spacer(Modifier.height(12.dp))
    SettingsLabel("ACCOUNT CONNECTIONS")
    StatusLine("Website account", "Coming Soon")
    Spacer(Modifier.height(7.dp))
    OutlinedButton(
        onClick = { PuppyLinks.openDiscord(context) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open Discord Community")
    }
    Text(
        PuppyLinks.DISCORD_INVITE,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

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
private fun AboutSettings() {
    val context = LocalContext.current
    SettingsLabel("PUPPY CLICKER")
    StatusLine("Version", BuildConfig.VERSION_NAME)
    StatusLine("Build", BuildConfig.VERSION_CODE.toString())
    StatusLine("Developer", "Harley's Studios")
    StatusLine("Discord community", PuppyLinks.DISCORD_INVITE)
    Spacer(Modifier.height(7.dp))
    OutlinedButton(
        onClick = { PuppyLinks.openDiscord(context) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Join Puppy Clicker Discord") }
    Spacer(Modifier.height(8.dp))
    PuppyLegalLinks(
        modifier = Modifier.fillMaxWidth(),
        acknowledgementText = "Terms and privacy open inside Puppy Clicker using the repository-backed legal document viewer."
    )
}

@Composable
private fun DangerZoneSettings(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(15.dp)) {
            Text("DANGER ZONE", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Destructive actions always require confirmation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(9.dp))
            OutlinedButton(onClick = { confirmation = "settings" }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset Settings")
            }
            Spacer(Modifier.height(7.dp))
            OutlinedButton(onClick = { confirmation = "progress" }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset Game Progress")
            }
            Spacer(Modifier.height(7.dp))
            Button(onClick = { confirmation = "delete" }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete Local Save Data")
            }
        }
    }

    confirmation?.let { action ->
        val (title, description, button) = when (action) {
            "settings" -> Triple(
                "Reset settings?",
                "Theme, accent, motion, UI scale and gameplay preference switches return to defaults. Your profile, birthday and game progress remain.",
                "Reset Settings"
            )
            "progress" -> Triple(
                "Reset game progress?",
                "This resets the current run without awarding prestige points. Permanent prestige skills and existing special puppy unlocks remain, matching Puppy Clicker's current reset behavior.",
                "Reset Progress"
            )
            else -> Triple(
                "Delete local save data?",
                "This deletes local game progress, player identity, birthday/setup state, PupEye local integrity history and the device save mirror. Puppy Clicker will restart into first-run setup. Streamed asset caches are not treated as game save data.",
                "Delete Local Data"
            )
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(title, fontWeight = FontWeight.Black) },
            text = { Text(description) },
            confirmButton = {
                Button(onClick = {
                    when (action) {
                        "settings" -> {
                            PuppyUiPreferences.resetInterfaceSettings(context)
                            vm.setHapticsEnabled(true)
                            vm.setAnimationsEnabled(true)
                            vm.setCompactNumbers(true)
                        }
                        "progress" -> vm.resetRunWithoutPrestige()
                        else -> {
                            deletePuppyClickerLocalSave(context)
                            activity?.recreate()
                        }
                    }
                    confirmation = null
                }) { Text(button) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } }
        )
    }
}

private fun deletePuppyClickerLocalSave(context: Context) {
    context.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("puppy_seasonal_v1", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("puppy_player_identity_v1", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("pupeye_security_v1", Context.MODE_PRIVATE).edit().clear().commit()
    File(context.noBackupFilesDir, "pupeye/last_good_save.pup").delete()
    ExternalGameSave.path(context)?.let { File(it).delete() }
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
