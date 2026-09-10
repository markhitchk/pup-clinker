#!/usr/bin/env python3
"""Apply the 10-second hold-to-confirm Danger Zone UI to generated Settings sources.

This runs after the Settings/onboarding integration so it can replace the final Danger Zone
surface without destabilizing older source-patch anchors.
"""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def ensure_import(source: str, import_line: str) -> str:
    line = f"import {import_line}\n"
    if line in source:
        return source
    marker = "package com.harleytg.puppyclicker\n\n"
    if marker not in source:
        raise RuntimeError("Danger Zone patch: package marker not found")
    return source.replace(marker, marker + line, 1)


def replace_function(source: str, signature: str, next_signature: str, replacement: str, label: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"{label}: start signature not found")
    end = source.find(next_signature, start)
    if end < 0:
        raise RuntimeError(f"{label}: end signature not found")
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


def patch_settings(source: str) -> str:
    imports = (
        "android.os.SystemClock",
        "androidx.compose.animation.core.RepeatMode",
        "androidx.compose.animation.core.animateFloat",
        "androidx.compose.animation.core.infiniteRepeatable",
        "androidx.compose.animation.core.rememberInfiniteTransition",
        "androidx.compose.foundation.border",
        "androidx.compose.foundation.gestures.awaitEachGesture",
        "androidx.compose.foundation.gestures.awaitFirstDown",
        "androidx.compose.foundation.layout.fillMaxHeight",
        "androidx.compose.runtime.DisposableEffect",
        "androidx.compose.runtime.LaunchedEffect",
        "androidx.compose.runtime.mutableLongStateOf",
        "androidx.compose.ui.input.pointer.pointerInput",
        "androidx.compose.ui.window.Dialog",
        "androidx.compose.ui.window.DialogProperties",
        "androidx.lifecycle.Lifecycle",
        "androidx.lifecycle.LifecycleEventObserver",
        "androidx.lifecycle.compose.LocalLifecycleOwner",
        "kotlinx.coroutines.delay",
    )
    for item in imports:
        source = ensure_import(source, item)

    replacement = r'''@Composable
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
            Text("DANGER ZONE", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Every destructive action requires an uninterrupted 10-second hold.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = { confirmationKey = DangerZoneAction.RESET_SETTINGS.key },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reset Settings") }
            Spacer(Modifier.height(7.dp))
            OutlinedButton(
                onClick = { confirmationKey = DangerZoneAction.RESET_PROGRESS.key },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reset Game Progress") }
            Spacer(Modifier.height(7.dp))
            Button(
                onClick = { confirmationKey = DangerZoneAction.ERASE_ALL_DATA.key },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete Local Save Data") }
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
                        deletePuppyClickerLocalSave(context)
                        activity?.recreate()
                    }
                }
                confirmationKey = null
            }
        )
    }
}

@Composable
private fun DangerHoldConfirmationDialog(
    action: DangerZoneAction,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reducedMotion = LocalPuppyReducedMotion.current
    var holdStartedAtMs by remember(action.key) { mutableStateOf<Long?>(null) }
    var nowMs by remember(action.key) { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val progress = DangerHoldConfirmation.progress(holdStartedAtMs, nowMs, action.holdDurationMs)
    val remainingMs = DangerHoldConfirmation.remainingMs(holdStartedAtMs, nowMs, action.holdDurationMs)
    val countdown = String.format(Locale.US, "%.1f", remainingMs / 1000.0)

    val borderPulse = rememberInfiniteTransition(label = "danger-border")
    val animatedBorderAlpha by borderPulse.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "danger-border-alpha"
    )
    val borderAlpha = if (reducedMotion) 1f else (animatedBorderAlpha + progress * 0.35f).coerceIn(0.30f, 1f)

    fun cancelHold() {
        holdStartedAtMs = null
        nowMs = SystemClock.elapsedRealtime()
    }

    DisposableEffect(lifecycleOwner, action.key) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                cancelHold()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cancelHold()
        }
    }

    LaunchedEffect(holdStartedAtMs, action.key) {
        val startedAt = holdStartedAtMs ?: return@LaunchedEffect
        while (holdStartedAtMs == startedAt) {
            val current = SystemClock.elapsedRealtime()
            nowMs = current
            if (DangerHoldConfirmation.isComplete(startedAt, current, action.holdDurationMs)) {
                holdStartedAtMs = null
                performV6Haptic(context, true)
                onConfirmed()
                break
            }
            delay(16L)
        }
    }

    Dialog(
        onDismissRequest = {
            cancelHold()
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .border(6.dp, MaterialTheme.colorScheme.error.copy(alpha = borderAlpha)),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.58f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.94f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 10.dp,
                    shadowElevation = 14.dp
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "⚠️ DANGER ZONE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(action.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text(action.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Do you wish to continue? Press and hold the red button for 10 seconds. Releasing, moving off the button, or leaving the app cancels the hold.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .pointerInput(action.key) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val pointerId = down.id
                                        val startedAt = SystemClock.elapsedRealtime()
                                        holdStartedAtMs = startedAt
                                        nowMs = startedAt
                                        var active = true
                                        while (active) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == pointerId }
                                            if (change == null) {
                                                active = false
                                                if (holdStartedAtMs == startedAt) cancelHold()
                                                continue
                                            }
                                            val inside = change.position.x >= 0f &&
                                                change.position.y >= 0f &&
                                                change.position.x <= size.width.toFloat() &&
                                                change.position.y <= size.height.toFloat()
                                            active = change.pressed && inside
                                            if (!active && holdStartedAtMs == startedAt) cancelHold()
                                            change.consume()
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (progress > 0f) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progress.coerceIn(0f, 1f)),
                                        color = MaterialTheme.colorScheme.onError.copy(alpha = 0.24f)
                                    ) {}
                                }
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        if (holdStartedAtMs == null) action.buttonLabel else "KEEP HOLDING · ${countdown}s",
                                        color = MaterialTheme.colorScheme.onError,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        if (holdStartedAtMs == null) "Hold continuously for 10 seconds" else "${(progress * 100).toInt()}% complete",
                                        color = MaterialTheme.colorScheme.onError.copy(alpha = 0.86f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                cancelHold()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel") }
                    }
                }
            }
        }
    }
}'''

    return replace_function(
        source,
        "@Composable\nprivate fun DangerZoneSettings(state: V6GameState, vm: PuppyClickerV6ViewModel)",
        "private fun deletePuppyClickerLocalSave(context: Context)",
        replacement,
        "Danger Zone hold-confirmation UI",
    )


def main(root: Path) -> None:
    target = root / PACKAGE / "PuppySettingsUi.kt"
    source = target.read_text(encoding="utf-8")
    target.write_text(patch_settings(source), encoding="utf-8")
    print("Danger Zone hold confirmation integrated")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_danger_hold_confirmation.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
