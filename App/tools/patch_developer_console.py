#!/usr/bin/env python3
"""Final generated-source integration for Puppy Clicker's hidden Developer Console."""
from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one {label} anchor, found {count}")
    return source.replace(old, new, 1)


def patch_settings(source: str) -> str:
    source = replace_once(
        source,
        "    val ui by PuppyUiPreferences.observe(context).collectAsStateWithLifecycle()\n    var expandedKeys by rememberSaveable { mutableStateOf(\"appearance\") }",
        """    val ui by PuppyUiPreferences.observe(context).collectAsStateWithLifecycle()
    val developer by PuppyDeveloperPreferences.observe(context).collectAsStateWithLifecycle()
    var developerConsoleOpen by rememberSaveable { mutableStateOf(false) }
    var expandedKeys by rememberSaveable { mutableStateOf(\"appearance\") }

    if (developerConsoleOpen) {
        PuppyDeveloperConsoleScreen(onBack = { developerConsoleOpen = false })
        return
    }""",
        "Settings developer state",
    )

    source = replace_once(
        source,
        '''        SettingsSectionCard("about", "ℹ", "About Puppy Clicker", isExpanded("about"), { toggle("about") }) {
            AboutSettings()
        }''',
        '''        if (developer.unlocked) {
            SettingsSectionCard("developer", "🛠", "Developer Options", isExpanded("developer"), { toggle("developer") }) {
                PuppyDeveloperOptions(onOpenConsole = { developerConsoleOpen = true })
            }
        }
        SettingsSectionCard("about", "ℹ", "About Puppy Clicker", isExpanded("about"), { toggle("about") }) {
            AboutSettings(developerUnlocked = developer.unlocked)
        }''',
        "Developer Options Settings card",
    )

    source = replace_once(
        source,
        '''@Composable
private fun AboutSettings() {
    SettingsLabel("PUPPY CLICKER")
    StatusLine("Version", BuildConfig.VERSION_NAME)
    StatusLine("Build", BuildConfig.VERSION_CODE.toString())
    StatusLine("Developer", "Harley's Studios")
    StatusLine("Discord community", "Coming Soon")
    Spacer(Modifier.height(8.dp))
    PuppyLegalLinks(
        modifier = Modifier.fillMaxWidth(),
        acknowledgementText = "Terms and privacy open inside Puppy Clicker using the repository-backed legal document viewer."
    )
}''',
        '''@Composable
private fun AboutSettings(developerUnlocked: Boolean) {
    val context = LocalContext.current
    var buildTapCount by rememberSaveable { mutableIntStateOf(0) }
    var buildTapMessage by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsLabel("PUPPY CLICKER")
    StatusLine("Version", BuildConfig.VERSION_NAME)
    ClickableStatusLine(
        label = "Build",
        value = BuildConfig.VERSION_CODE.toString(),
        onClick = {
            val progress = nextDeveloperUnlockProgress(buildTapCount, developerUnlocked)
            buildTapCount = progress.tapCount
            buildTapMessage = when {
                developerUnlocked -> "Developer Mode is already enabled."
                progress.unlocked -> {
                    PuppyDeveloperPreferences.setUnlocked(context, true)
                    PuppyDebugLog.i("DeveloperMode", "Developer Mode unlocked from About")
                    "Developer Mode enabled."
                }
                progress.remainingTaps == 1 -> "1 step away from Developer Mode."
                else -> "${progress.remainingTaps} steps away from Developer Mode."
            }
        }
    )
    buildTapMessage?.let {
        Text(
            it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp)
        )
    }
    StatusLine("Developer", "Harley's Studios")
    StatusLine("Discord community", "Coming Soon")
    Spacer(Modifier.height(8.dp))
    PuppyLegalLinks(
        modifier = Modifier.fillMaxWidth(),
        acknowledgementText = "Terms and privacy open inside Puppy Clicker using the repository-backed legal document viewer."
    )
}''',
        "About Build unlock",
    )

    source = replace_once(
        source,
        '''@Composable
private fun DangerZoneSettings(state: V6GameState, vm: PuppyClickerV6ViewModel) {''',
        '''@Composable
private fun ClickableStatusLine(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DangerZoneSettings(state: V6GameState, vm: PuppyClickerV6ViewModel) {''',
        "clickable Build status row",
    )

    source = replace_once(
        source,
        '''    context.getSharedPreferences("pupeye_security_v1", Context.MODE_PRIVATE).edit().clear().commit()
    File(context.noBackupFilesDir, "pupeye/last_good_save.pup").delete()''',
        '''    context.getSharedPreferences("pupeye_security_v1", Context.MODE_PRIVATE).edit().clear().commit()
    PuppyDeveloperPreferences.clear(context)
    File(context.noBackupFilesDir, "pupeye/last_good_save.pup").delete()''',
        "Developer Mode local-data reset",
    )
    return source


def route_android_logs(root: Path) -> int:
    routed_files = 0
    for path in root.rglob("*.kt"):
        if path.name == "PuppyDebugLog.kt":
            continue
        source = path.read_text()
        if "import android.util.Log" not in source:
            continue
        patched = source
        for method in ("v", "d", "i", "w", "e"):
            patched = patched.replace(f"Log.{method}(", f"PuppyDebugLog.{method}(")
        if patched != source:
            path.write_text(patched)
            routed_files += 1
    if routed_files == 0:
        raise RuntimeError("Developer Console integration found no Android Log call sites to route")
    return routed_files


def main(root: Path) -> None:
    settings = root / "com/harleytg/puppyclicker/PuppySettingsUi.kt"
    if not settings.is_file():
        raise RuntimeError(f"Missing generated Settings source: {settings}")

    settings.write_text(patch_settings(settings.read_text()))
    routed = route_android_logs(root / "com/harleytg/puppyclicker")
    print(f"Developer Console integrated; routed Android logs in {routed} Kotlin files")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_developer_console.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
