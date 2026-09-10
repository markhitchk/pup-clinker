#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt")
source = path.read_text(encoding="utf-8")

source = source.replace("import java.time.LocalDate\n", "")
source = source.replace("import java.time.YearMonth\n", "")

old_preview = '''@Composable
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
            Text("Puppy Coins: 15,250", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(9.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("BUY") }
            Text(
                "${when (ui.themeMode) { PuppyThemeMode.LIGHT -> "Light"; PuppyThemeMode.DARK -> "Dark"; PuppyThemeMode.SYSTEM -> "System" }} · ${ui.accentHex}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
'''
new_preview = '''@Composable
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
'''
if source.count(old_preview) != 1:
    raise SystemExit(f"AppearancePreview anchor count={source.count(old_preview)}")
source = source.replace(old_preview, new_preview, 1)

start = source.index('    SettingsLabel("BIRTHDAY")', source.index("private fun AccountProfileSettings"))
end = source.index("\n}\n\n@Composable\ninternal fun BirthdayEditorDialog", start)
new_account_birthday = '''    SettingsLabel("BIRTHDAY")
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
'''
source = source[:start] + new_account_birthday + source[end:]

fn_start = source.index("@Composable\ninternal fun BirthdayEditorDialog")
fn_end = source.index("\n@Composable\nprivate fun BirthdayPicker", fn_start)
new_dialog = '''@Composable
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
'''
source = source[:fn_start] + new_dialog + source[fn_end:]

for forbidden in ("birthdayYear", "initialYear", "Puppy Coins: 15,250", 'Text("BUY")'):
    if forbidden in source:
        raise SystemExit(f"Forbidden Settings source remains: {forbidden}")

path.write_text(source, encoding="utf-8")
print("Patched", path)
