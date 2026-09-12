package com.harleytg.puppyclicker

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun PuppyOnboardingPersonalize(
    vm: PuppyClickerV6ViewModel,
    ui: PuppyUiState,
    session: PuppyOnboardingSessionState,
    onSessionChange: (PuppyOnboardingSessionState) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var month by rememberSaveable { mutableIntStateOf(ui.birthdayMonth.coerceIn(0, 12)) }
    var day by rememberSaveable { mutableIntStateOf(ui.birthdayDay.coerceIn(0, 31)) }

    val maxDay = PuppyBirthday.maxDay(month).takeIf { it > 0 } ?: 31
    if (day > maxDay) day = 0

    val birthdayBlank = month == 0 && day == 0
    val birthdayValid = PuppyBirthday.isValid(month, day)
    val birthdayIncomplete = (month == 0) xor (day == 0)
    val canContinue = !birthdayIncomplete &&
        (birthdayBlank || birthdayValid || session.birthdaySkipped)

    PuppyOnboardingShell(
        step = PuppyOnboardingStep.PERSONALIZE,
        title = "Personalize",
        canGoBack = true,
        primaryLabel = "Continue",
        primaryEnabled = canContinue,
        onBack = onBack,
        onPrimary = {
            if (session.birthdaySkipped || birthdayBlank) {
                PuppyUiPreferences.clearBirthday(context)
                vm.clearSeasonalBirthday()
                onComplete()
            } else if (PuppyUiPreferences.setBirthday(context, month, day)) {
                vm.setSeasonalBirthday(month, day)
                onComplete()
            }
        }
    ) {
        Text(
            "Set up your appearance. Everything here can be changed later in Settings.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Birthday", fontWeight = FontWeight.Black)
                        Text(
                            "Optional · only the month and day are stored locally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (session.birthdaySkipped || birthdayBlank) {
                        Text(
                            "Optional",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (!session.birthdaySkipped) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OnboardingPicker(
                            modifier = Modifier.weight(1f),
                            label = if (month == 0) "Month" else onboardingMonthName(month),
                            options = (1..12).map { it to onboardingMonthName(it) },
                            enabled = true
                        ) { selected ->
                            month = selected
                            if (day > PuppyBirthday.maxDay(selected)) day = 0
                            onSessionChange(session.copy(birthdaySkipped = false))
                        }
                        OnboardingPicker(
                            modifier = Modifier.weight(1f),
                            label = if (day == 0) "Day" else day.toString(),
                            options = (1..maxDay).map { it to it.toString() },
                            enabled = month != 0
                        ) { selected ->
                            day = selected
                            onSessionChange(session.copy(birthdaySkipped = false))
                        }
                    }

                    if (birthdayIncomplete) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Choose both a month and day, or leave birthday blank.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    TextButton(
                        onClick = {
                            month = 0
                            day = 0
                            PuppyUiPreferences.clearBirthday(context)
                            vm.clearSeasonalBirthday()
                            onSessionChange(session.copy(birthdaySkipped = true))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skip birthday")
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Birthday not set",
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    onSessionChange(session.copy(birthdaySkipped = false))
                                }
                            ) {
                                Text("Add birthday instead")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Appearance", fontWeight = FontWeight.Black)
                Text(
                    "Changes preview immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Text("Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    PuppyThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = ui.themeMode == mode,
                            onClick = { PuppyUiPreferences.setThemeMode(context, mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        PuppyThemeMode.SYSTEM -> "System"
                                        PuppyThemeMode.LIGHT -> "Light"
                                        PuppyThemeMode.DARK -> "Dark"
                                    }
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text("Accent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("#00B8F0", "#2979FF", "#8B5CF6", "#EC4899").forEach { hex ->
                        val color = Color(AndroidColor.parseColor(hex))
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { PuppyUiPreferences.setAccent(context, hex) },
                            shape = CircleShape,
                            color = color,
                            border = if (ui.accentHex.equals(hex, ignoreCase = true)) {
                                BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                            } else {
                                null
                            }
                        ) {
                            if (ui.accentHex.equals(hex, ignoreCase = true)) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "✓",
                                        color = if (color.luminance() > 0.52f) {
                                            Color.Black
                                        } else {
                                            Color.White
                                        },
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("UI Scale", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
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

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reduced Motion", fontWeight = FontWeight.Bold)
                        Text(
                            "Minimize onboarding and app animations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ui.reducedMotion,
                        onCheckedChange = {
                            PuppyUiPreferences.setReducedMotion(context, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPicker(
    modifier: Modifier = Modifier,
    label: String,
    options: List<Pair<Int, String>>,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun onboardingMonthName(month: Int): String =
    Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
