package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Month
import java.time.MonthDay
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** One welcome per installation, then one greeting per active event occurrence. */
@Composable
internal fun SeasonalWelcomeGate(vm: PuppyClickerV6ViewModel) {
    val settings by vm.seasonalSettings.collectAsStateWithLifecycle()
    val clock by vm.seasonalClock.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshSeasonalEvents() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            vm.refreshSeasonalEvents()
        }
    }

    if (!settings.introSeen) {
        SeasonalBirthdayDialog(
            initial = settings.birthday,
            welcome = true,
            onSave = { vm.setSeasonalBirthday(it.monthValue, it.dayOfMonth) },
            onClose = vm::dismissSeasonalIntro
        )
        return
    }

    val pending = SeasonalPuppyEvents.events.firstNotNullOfOrNull { event ->
        val window = SeasonalPuppyEvents.activeWindow(event, clock.now, clock.zone, settings.birthday)
        if (window != null && window.cycle !in settings.seenCycles) event to window else null
    }
    if (pending != null) {
        val (event, window) = pending
        SeasonalEventWelcomeDialog(
            event = event,
            window = window,
            vm = vm,
            onClose = { vm.markSeasonalSeen(window.cycle) }
        )
    }
}

@Composable
private fun SeasonalBirthdayDialog(
    initial: MonthDay?,
    welcome: Boolean,
    onSave: (MonthDay) -> Unit,
    onClose: () -> Unit
) {
    var month by rememberSaveable { mutableIntStateOf(initial?.monthValue ?: 0) }
    var day by rememberSaveable { mutableIntStateOf(initial?.dayOfMonth ?: 0) }
    val valid = SeasonalPuppyEvents.birthday(month, day)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (welcome) "Welcome to Puppy Clicker! 🐾" else "Your birthday") },
        text = {
            Column {
                Text("When is your birthday? Enter only the month and day to activate Birthday Buddy on your local birthday.")
                Spacer(Modifier.height(8.dp))
                Text("Optional. No birth year is requested. You can edit or remove the date in Settings. The app keeps it on this device and does not upload it.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeasonalPicker(
                        label = if (month == 0) "Month" else Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        options = (1..12).map { it to Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                        modifier = Modifier.weight(1f),
                        onSelect = { month = it; if (SeasonalPuppyEvents.birthday(month, day) == null) day = 0 }
                    )
                    val maxDay = if (month == 0) 31 else Month.of(month).maxLength()
                    SeasonalPicker(
                        label = if (day == 0) "Day" else day.toString(),
                        options = (1..maxDay).map { it to it.toString() },
                        modifier = Modifier.weight(1f),
                        onSelect = { day = it }
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text("February 29 birthdays celebrate on February 28 in non-leap years. Birthday rewards use your local calendar day, not a fixed UTC day.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { valid?.let(onSave) }, enabled = valid != null) { Text("Save birthday") } },
        dismissButton = { TextButton(onClick = onClose) { Text(if (welcome) "Not now" else "Cancel") } }
    )
}

@Composable
private fun SeasonalPicker(label: String, options: List<Pair<Int, String>>, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, fontSize = 12.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun SeasonalEventWelcomeDialog(
    event: SeasonalPuppyEvent,
    window: SeasonalWindow,
    vm: PuppyClickerV6ViewModel,
    onClose: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clock by vm.seasonalClock.collectAsStateWithLifecycle()
    val owned = event.puppyId in state.unlockedPuppies
    var message by remember(event.puppyId, window.cycle) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (event.isBirthday) "Happy Birthday! 🎂" else "${event.emoji} ${event.title} is here!") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                StreamedPuppyPortrait(
                    styleId = event.puppyId, size = 128.dp,
                    unlocked = true, background = MaterialTheme.colorScheme.primaryContainer
                )
                Spacer(Modifier.height(10.dp))
                Text(if (event.isBirthday) "A special birthday puppy is ready to celebrate with you!" else "The ${event.description.lowercase()} has started. Your limited-time puppy is ready to claim.")
                Spacer(Modifier.height(8.dp))
                Text("Available until ${SeasonalPuppyEvents.formatLocal(window.end, clock.zone)}", style = MaterialTheme.typography.bodySmall)
                Text("Once claimed, this puppy stays in your collection permanently.", style = MaterialTheme.typography.bodySmall)
                if (owned) Text("Already in your collection ✓", fontWeight = FontWeight.Bold)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            if (!owned) {
                Button(onClick = {
                    val result = vm.claimSeasonalPuppy(event.puppyId)
                    message = result.message
                    if (result.success) onClose()
                }) { Text("Claim puppy") }
            } else TextButton(onClick = onClose) { Text("Continue") }
        },
        dismissButton = { if (!owned) TextButton(onClick = onClose) { Text("Later") } }
    )
}

/** The collection keeps regular and event characters together without duplicating IDs. */
@Composable
internal fun SeasonalCollectionPanel(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    val settings by vm.seasonalSettings.collectAsStateWithLifecycle()
    val clock by vm.seasonalClock.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    Text("Seasonal & birthday puppies", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(6.dp))
    Text("Claim during the event to keep a puppy permanently. Holiday schedules are fixed in UTC and shown in your local time.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    SeasonalPuppyEvents.events.forEach { event ->
        val owned = event.puppyId in state.unlockedPuppies
        val active = SeasonalPuppyEvents.activeWindow(event, clock.now, clock.zone, settings.birthday) != null
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreamedPuppyPortrait(event.puppyId, 72.dp, unlocked = owned, background = MaterialTheme.colorScheme.primaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${event.emoji} ${event.title}", fontWeight = FontWeight.Bold)
                        Text(SeasonalPuppyEvents.availability(event, clock.now, clock.zone, settings.birthday), style = MaterialTheme.typography.bodySmall)
                        Text(if (owned) "Permanently owned" else if (active) "Limited-time claim available" else "Not currently available", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (owned) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.setPuppyStyle(event.puppyId) },
                        enabled = state.puppyStyle != event.puppyId,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (state.puppyStyle == event.puppyId) "Selected" else "Choose ${event.title}") }
                } else if (active) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { message = vm.claimSeasonalPuppy(event.puppyId).message }, modifier = Modifier.fillMaxWidth()) { Text("Claim ${event.title}") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    Spacer(Modifier.height(14.dp))
}

@Composable
internal fun SeasonalBirthdaySettings(vm: PuppyClickerV6ViewModel) {
    val settings by vm.seasonalSettings.collectAsStateWithLifecycle()
    val clock by vm.seasonalClock.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text("🎂 Birthday & seasonal events", fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text(settings.birthday?.let { "Birthday: ${Month.of(it.monthValue).getDisplayName(TextStyle.FULL, Locale.getDefault())} ${it.dayOfMonth}" } ?: "Birthday not set", style = MaterialTheme.typography.bodyMedium)
            Text("Only month and day are stored. No birth year or age is needed.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(7.dp))
            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text(if (settings.birthday == null) "Add birthday" else "Change birthday") }
            if (settings.birthday != null) TextButton(onClick = { deleting = true }) { Text("Remove birthday") }
            Spacer(Modifier.height(4.dp))
            Text("Time zone: ${clock.zone.id}", style = MaterialTheme.typography.bodySmall)
            Text("Holiday times convert from UTC. Birthday events use local midnight. Previously claimed puppies remain owned.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (editing) SeasonalBirthdayDialog(
        initial = settings.birthday, welcome = false,
        onSave = { vm.setSeasonalBirthday(it.monthValue, it.dayOfMonth); editing = false },
        onClose = { editing = false }
    )
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Remove birthday?") },
        text = { Text("The saved month and day will be removed. Your previously unlocked puppies will not be deleted.") },
        confirmButton = { TextButton(onClick = { vm.clearSeasonalBirthday(); deleting = false }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
    )
}
