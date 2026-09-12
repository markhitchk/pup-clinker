#!/usr/bin/env python3
"""Apply the final Puppy Roster navigation shell after established compatibility patches."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Roster navigation patch {label}: expected 1 match, found {count}")
    return source.replace(old, new, 1)


def replace_function(source: str, signature: str, next_signature: str, replacement: str, label: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise RuntimeError(f"Roster navigation patch {label}: start signature not found")
    end = source.find(next_signature, start)
    if end < 0:
        raise RuntimeError(f"Roster navigation patch {label}: end signature not found")
    return source[:start] + replacement.rstrip() + "\n\n" + source[end:]


def patch_activity(source: str) -> str:
    source = replace_once(
        source,
        "import androidx.activity.compose.setContent\n",
        "import androidx.activity.compose.BackHandler\nimport androidx.activity.compose.setContent\n",
        "BackHandler import",
    )
    source = replace_once(
        source,
        "import androidx.compose.ui.res.painterResource\n",
        "import androidx.compose.ui.res.painterResource\nimport androidx.compose.ui.semantics.contentDescription\nimport androidx.compose.ui.semantics.semantics\n",
        "semantics imports",
    )

    enum_start = source.find("private enum class V6Tab")
    enum_end_marker = "\n}\n\n@Composable\nprivate fun PuppyClickerV6App"
    enum_end = source.find(enum_end_marker, enum_start)
    if enum_start < 0 or enum_end < 0:
        raise RuntimeError("Roster navigation patch V6Tab: enum block not found")
    enum_replacement = '''private enum class V6Tab(val destination: PuppyMainDestination) {
    PLAY(PuppyMainDestination.PLAY),
    CARE(PuppyMainDestination.CARE),
    ROSTER(PuppyMainDestination.ROSTER),
    SHOP(PuppyMainDestination.SHOP),
    REWARDS(PuppyMainDestination.REWARDS);

    val label: String get() = destination.label
    val emoji: String get() = destination.emoji
}'''
    source = source[:enum_start] + enum_replacement + source[enum_end + 2:]

    source = replace_function(
        source,
        "@Composable\nprivate fun PuppyClickerV6App(vm: PuppyClickerV6ViewModel)",
        "@Composable\nprivate fun V6Play",
        '''@Composable
private fun PuppyClickerV6App(vm: PuppyClickerV6ViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var internalDestination by rememberSaveable { mutableStateOf<PuppyInternalDestination?>(null) }
    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }
    PuppyAttentionLifecycle()
    val uiPreferences by PuppyUiPreferences.observe(LocalContext.current).collectAsStateWithLifecycle()
    if (!uiPreferences.setupComplete) {
        PuppyOnboardingFlow(vm)
        return
    }
    SeasonalWelcomeGate(vm)

    BackHandler(enabled = internalDestination != null) {
        internalDestination = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PuppyFixedAppBar(
                onOpenSettings = { internalDestination = PuppyInternalDestination.SETTINGS }
            )
        },
        bottomBar = {
            if (internalDestination == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
                    V6Tab.entries.forEach { item ->
                        val roster = item == V6Tab.ROSTER
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Box(
                                    modifier = if (roster) Modifier.offset(y = (-5).dp) else Modifier
                                ) {
                                    Text(item.emoji, fontSize = if (roster) 26.sp else 19.sp)
                                }
                            },
                            label = { Text(item.label, fontSize = 10.sp, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val internal = internalDestination
            if (internal != null) {
                when (internal) {
                    PuppyInternalDestination.SETTINGS -> V6Settings(state, vm)
                    PuppyInternalDestination.PRESTIGE -> V6Prestige(state, vm)
                }
            } else {
                when (tab) {
                    V6Tab.PLAY -> V6Play(state, vm)
                    V6Tab.CARE -> V6CareAndDaily(state, vm)
                    V6Tab.ROSTER -> PuppyRosterScreen(
                        state = state,
                        vm = vm,
                        onUseConfirmed = { id ->
                            vm.setPuppyStyle(id)
                            if (vm.state.value.puppyStyle == id) tab = V6Tab.PLAY
                        },
                        onOpenSettings = { internalDestination = PuppyInternalDestination.SETTINGS }
                    )
                    V6Tab.SHOP -> V6Shop(state, vm)
                    V6Tab.REWARDS -> PuppyRewardsHub(
                        state = state,
                        vm = vm,
                        onOpenPrestige = { internalDestination = PuppyInternalDestination.PRESTIGE }
                    )
                }

            }
        }
    }
}

@Composable
private fun PuppyFixedAppBar(onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 3.dp,
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = streamedRepoLogoPainter(RepoLogoAsset.PUPPY_CLICKER, R.drawable.source_logo),
                    contentDescription = "Puppy Clicker logo",
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "Puppy Clicker",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Open settings" }
                ) {
                    Text("⚙️", fontSize = 22.sp)
                }
            }
        }
    }
}''',
        "main app shell",
    )

    source = replace_function(
        source,
        "@Composable\nprivate fun V6CareAndDaily(state: V6GameState, vm: PuppyClickerV6ViewModel)",
        "@Composable\nprivate fun V6CarePanel",
        '''@Composable
private fun V6CareAndDaily(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V6Header("Pup Care", "Care for and bond with your active puppy.")
        Spacer(Modifier.height(14.dp))
        V6CarePanel(state, vm)
        Spacer(Modifier.height(18.dp))
    }
}''',
        "Care destination",
    )

    source = replace_once(
        source,
        "@Composable\nprivate fun V6DailyPanel(state: V6GameState, vm: PuppyClickerV6ViewModel)",
        "@Composable\ninternal fun V6DailyPanel(state: V6GameState, vm: PuppyClickerV6ViewModel)",
        "Rewards daily panel visibility",
    )

    source = replace_function(
        source,
        "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun V6Shop(state: V6GameState, vm: PuppyClickerV6ViewModel)",
        "@Composable\nprivate fun V6CookieUpgradeCard",
        '''@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V6Shop(state: V6GameState, vm: PuppyClickerV6ViewModel) {
    var shopTab by rememberSaveable { mutableIntStateOf(0) }
    var redeemOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        V6Header("Puppy Shop", "Cookie upgrades, rarity-ticket upgrades and Puppy Codes.")
        Spacer(Modifier.height(12.dp))
        V6Wallet(state)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { redeemOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("🎫 Redeem Code")
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = shopTab == 0, onClick = { shopTab = 0 }, label = { Text("🍪 Cookie Upgrades") }, modifier = Modifier.weight(1f))
            FilterChip(selected = shopTab == 1, onClick = { shopTab = 1 }, label = { Text("🎟️ Ticket Upgrades") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        if (shopTab == 0) {
            if ((state.prestigeSkills[PrestigeSkill.SMART_SHOPPER] ?: 0) > 0) {
                Text("⭐ Smart Shopper discount active", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
            }
            V5_UPGRADES.filter { it.type == V5UpgradeType.COOKIE }.forEach { upgrade ->
                V6CookieUpgradeCard(state, upgrade, vm)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            V6TicketInventory(state)
            Spacer(Modifier.height(10.dp))
            V5_UPGRADES.filter { it.type == V5UpgradeType.TICKET }.forEach { upgrade ->
                V6TicketUpgradeCard(state, upgrade, vm)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (redeemOpen) V6RedeemDialog(vm) { redeemOpen = false }
}''',
        "Shop roster removal",
    )

    return source


def main(root: Path) -> None:
    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    activity.write_text(patch_activity(activity.read_text(encoding="utf-8")), encoding="utf-8")
    print("Puppy roster navigation and Rewards hub integrated")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_roster_navigation.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
