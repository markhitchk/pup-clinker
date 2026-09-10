package com.harleytg.puppyclicker

import android.Manifest
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harleytg.puppyclicker.ui.theme.LocalPuppyReducedMotion
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PuppyOnboardingFlow(vm: PuppyClickerV6ViewModel) {
    val context = LocalContext.current
    val ui by PuppyUiPreferences.observe(context).collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(ui.setupStep.coerceIn(0, 5)) }
    val reducedMotion = LocalPuppyReducedMotion.current

    LaunchedEffect(step) {
        PuppyUiPreferences.setSetupStep(context, step)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OnboardingProgress(step)
                Spacer(Modifier.height(16.dp))

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val duration = if (reducedMotion) 1 else 180
                        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                    },
                    label = "onboarding-step"
                ) { current ->
                    when (current) {
                        0 -> WelcomeStep(onNext = { step = 1 })
                        1 -> ProfileStep(onBack = { step = 0 }, onNext = { step = 2 })
                        2 -> BirthdayStep(vm, ui, onBack = { step = 1 }, onNext = { step = 3 })
                        3 -> SetupAppearanceStep(ui, onBack = { step = 2 }, onNext = { step = 4 })
                        4 -> SetupNotificationsStep(onBack = { step = 3 }, onNext = { step = 5 })
                        else -> FinishStep(vm, ui, onBack = { step = 4 })
                    }
                }
            }

            StreamedPupEyeBranding(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(38.dp),
                contentDescription = "PupEye fair-play protection"
            )
        }
    }
}

@Composable
private fun OnboardingProgress(step: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Step ${step + 1} of 6", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(6) { index ->
                Surface(
                    modifier = Modifier.size(if (index == step) 11.dp else 9.dp),
                    shape = CircleShape,
                    color = if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ) {}
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.source_logo),
            contentDescription = "Puppy Clicker logo",
            modifier = Modifier.size(176.dp),
            contentScale = ContentScale.Fit
        )
        Text("Welcome to Puppy Clicker", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(
            "Tap, care for puppies, build your collection, and make the game yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StreamedPupEyeBranding(Modifier.size(38.dp), "PupEye anti-cheat")
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Protected by PupEye", fontWeight = FontWeight.Black)
                    Text("Security branding loads independently and never blocks setup.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { PuppyLinks.openDiscord(context) },
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_discord), "Discord", Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Join Our Discord Server", fontWeight = FontWeight.Black)
                    Text(PuppyLinks.DISCORD_INVITE, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Get Started", fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(7.dp))
        PuppyLegalLinks(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        PuppyDevelopmentNotice()
    }
}

@Composable
private fun ProfileStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var username by rememberSaveable {
        mutableStateOf(PuppyPlayerIdentity.username(context).takeUnless { it == "localplayer" }.orEmpty())
    }
    val normalized = PuppyPlayerIdentity.normalizeUsername(username)

    SetupCard("YOUR PROFILE", "👤") {
        Text("Local Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            "Your local profile is stored on this device and linked to your protected Puppy Clicker save.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.take(24) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Player username") },
            supportingText = { Text("Stored on this device") },
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Text("Connections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { PuppyLinks.openDiscord(context) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.ic_discord), "Discord", Modifier.size(28.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Discord Community", fontWeight = FontWeight.Bold)
                    Text(PuppyLinks.DISCORD_INVITE, style = MaterialTheme.typography.labelSmall)
                }
                Text("Open", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        FutureAccountCard("Website account", "Coming Soon")
        Spacer(Modifier.height(14.dp))
        SetupNavigation(onBack, "Continue", normalized.isNotBlank()) {
            PuppyPlayerIdentity.setUsername(context, username)
            onNext()
        }
    }
}

@Composable
private fun FutureAccountCard(provider: String, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(provider, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BirthdayStep(vm: PuppyClickerV6ViewModel, ui: PuppyUiState, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var month by rememberSaveable { mutableIntStateOf(ui.birthdayMonth.coerceIn(0, 12)) }
    var day by rememberSaveable { mutableIntStateOf(ui.birthdayDay.coerceIn(0, 31)) }
    val maxDay = PuppyBirthday.maxDay(month).takeIf { it > 0 } ?: 31
    if (day > maxDay) day = 0
    val valid = PuppyBirthday.isValid(month, day)

    SetupCard("YOUR BIRTHDAY", "🎂") {
        Text("When's your birthday?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(
            "Your birthday helps Puppy Clicker provide birthday-related features and rewards.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        SetupPicker(
            if (month == 0) "Month" else setupMonthName(month),
            (1..12).map { it to setupMonthName(it) }
        ) { selected ->
            month = selected
            if (day > PuppyBirthday.maxDay(selected)) day = 0
        }
        Spacer(Modifier.height(7.dp))
        SetupPicker(
            if (day == 0) "Day" else day.toString(),
            (1..maxDay).map { it to it.toString() }
        ) { day = it }
        Spacer(Modifier.height(10.dp))
        Text(
            "Only your birthday month and day are stored locally. Your birthday is not displayed publicly to other players.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        SetupNavigation(
            onBack = onBack,
            nextLabel = "Continue",
            nextEnabled = valid,
            onNext = {
                if (PuppyUiPreferences.setBirthday(context, month, day)) {
                    vm.setSeasonalBirthday(month, day)
                    onNext()
                }
            }
        )
    }
}

@Composable
private fun SetupAppearanceStep(ui: PuppyUiState, onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    SetupCard("MAKE IT YOURS", "🎨") {
        Text("Theme", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PuppyThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = ui.themeMode == mode,
                    onClick = { PuppyUiPreferences.setThemeMode(context, mode) },
                    label = { Text(when (mode) { PuppyThemeMode.LIGHT -> "Light"; PuppyThemeMode.DARK -> "Dark"; PuppyThemeMode.SYSTEM -> "System" }) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Accent", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("#00B8F0", "#2979FF", "#8B5CF6", "#EC4899").forEach { hex ->
                val color = Color(AndroidColor.parseColor(hex))
                Surface(
                    modifier = Modifier.size(46.dp).clip(CircleShape).clickable { PuppyUiPreferences.setAccent(context, hex) },
                    shape = CircleShape,
                    color = color,
                    border = if (ui.accentHex.equals(hex, true)) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null
                ) {
                    if (ui.accentHex.equals(hex, true)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("✓", color = if (color.luminance() > 0.52f) Color.Black else Color.White) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Puppy Clicker", fontWeight = FontWeight.Black)
                Text("Theme preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(7.dp))
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Accent Preview") }
            }
        }
        Text("More colors and the full custom HEX/HSV picker are available in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        SetupNavigation(onBack, "Continue", true, onNext)
    }
}

@Composable
private fun SetupNotificationsStep(onBack: () -> Unit, onNext: () -> Unit) {
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

    SetupCard("STAY UPDATED", "🔔") {
        Text(
            "Choose which alerts Puppy Clicker may send. Android permission is requested only when needed.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        NotificationSetupSwitch("AFK Rewards & Puppy Attention", afkEnabled) {
            afkEnabled = it
            PuppyAttentionNotifier.setEnabled(context, it)
        }
        NotificationSetupSwitch("Daily Rewards", ui.dailyRewardNotifications) {
            PuppyUiPreferences.setDailyRewardNotifications(context, it)
            PuppyNotificationCenter.schedule(context)
        }
        NotificationSetupSwitch("Game Events", ui.gameEventNotifications) {
            PuppyUiPreferences.setGameEventNotifications(context, it)
            PuppyNotificationCenter.schedule(context)
        }
        NotificationSetupSwitch("App Updates", ui.updateNotifications) {
            PuppyUiPreferences.setUpdateNotifications(context, it)
            PuppyNotificationCenter.schedule(context)
        }

        val anyEnabled = afkEnabled ||
            ui.dailyRewardNotifications ||
            ui.gameEventNotifications ||
            ui.updateNotifications

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && anyEnabled && !permissionGranted) {
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Allow Android Notifications")
            }
        } else if (permissionGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Text(
                "Android notification permission is available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(14.dp))
        SetupNavigation(onBack, "Continue", true) {
            PuppyNotificationCenter.schedule(context)
            if (permissionGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                PuppyNotificationCenter.requestImmediate(context)
            }
            onNext()
        }
    }
}

@Composable
private fun NotificationSetupSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FinishStep(vm: PuppyClickerV6ViewModel, ui: PuppyUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    var pupEyeState by rememberSaveable { mutableStateOf("Checking") }

    LaunchedEffect(Unit) {
        val safe = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
            PupEyeSaveGuard.verifyAndRecover(context, prefs) && ExternalGameSave.verifyExisting(context)
        }
        pupEyeState = if (safe) "Protected" else "Warning"
    }

    SetupCard("YOU'RE READY!", "🐶") {
        Text("🎉 🐾 🎉", fontSize = 34.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        StatusSetupRow("Username", PuppyPlayerIdentity.username(context))
        StatusSetupRow(
            "Birthday",
            if (ui.birthdayMonth in 1..12 && ui.birthdayDay > 0) "${setupMonthName(ui.birthdayMonth)} ${ui.birthdayDay}" else "Not set"
        )
        StatusSetupRow("Theme", when (ui.themeMode) { PuppyThemeMode.LIGHT -> "Light"; PuppyThemeMode.DARK -> "Dark"; PuppyThemeMode.SYSTEM -> "Follow System" })
        StatusSetupRow("Accent", ui.accentHex)
        StatusSetupRow("PupEye", pupEyeState)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = {
                    vm.dismissSeasonalIntro()
                    PuppyUiPreferences.finishSetup(context)
                },
                enabled = pupEyeState != "Checking",
                modifier = Modifier.weight(1f)
            ) { Text("Start Playing", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun SetupCard(title: String, emoji: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(emoji, fontSize = 34.sp)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SetupNavigation(onBack: () -> Unit, nextLabel: String, nextEnabled: Boolean, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
        Button(onClick = onNext, enabled = nextEnabled, modifier = Modifier.weight(1f)) { Text(nextLabel) }
    }
}

@Composable
private fun SetupPicker(label: String, options: List<Pair<Int, String>>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun StatusSetupRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

private fun setupMonthName(month: Int): String = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
