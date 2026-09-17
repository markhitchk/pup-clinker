from pathlib import Path
import sys
import tempfile
import unittest

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from patch_android_1_0_runner import pre_normalize
from patch_android_1_0_completion import patch_main_screens, patch_settings


VM_RELATIVE = Path("com/harleytg/puppyclicker/PuppyClickerV6ViewModel.kt")


class AndroidOnePointZeroRunnerTest(unittest.TestCase):
    def test_afk_normalization_renames_lambda_and_all_state_references(self) -> None:
        source = '''class PuppyClickerV6ViewModel {
    fun tapPuppy() {
        val nextTaps = 1L
    }

    fun buyCookieUpgrade() = Unit

    private fun consumeClaimedAfkReward() {
        val amount = prefs.getLong(KEY_AFK_CLAIM_READY, 0L).coerceAtLeast(0L)
        if (amount <= 0L) return
        prefs.edit().putLong(KEY_AFK_CLAIM_READY, 0L).apply()
        _state.update {
            it.copy(
                treats = safeAdd(it.treats, amount),
                lifetimeTreats = safeAdd(it.lifetimeTreats, amount),
                afkLastClaimed = amount
            )
        }
        saveState()
    }

    private fun rollDailyDayIfNeeded() = Unit
}
'''
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            vm = root / VM_RELATIVE
            vm.parent.mkdir(parents=True)
            vm.write_text(source, encoding="utf-8")

            pre_normalize(root)

            normalized = vm.read_text(encoding="utf-8")
            self.assertIn("_state.update { s ->", normalized)
            self.assertIn("treats = safeAdd(s.treats, amount)", normalized)
            self.assertIn("lifetimeTreats = safeAdd(s.lifetimeTreats, amount)", normalized)
            self.assertNotIn("safeAdd(it.treats, amount)", normalized)
            self.assertNotIn("safeAdd(it.lifetimeTreats, amount)", normalized)

    def test_settings_badge_and_progression_are_scoped_to_profile_settings(self) -> None:
        source = '''enum class SettingsDestination {
    DEVELOPER,
    ABOUT
}

fun router() {
        SettingsDestination.ABOUT -> SettingsSubpage(
            title = "About Puppy Clicker",
}

fun advanced() {
            SettingsNavRow("ⓘ", "About Puppy Clicker", "Version, links, development, and legal") {
                onOpen(SettingsDestination.ABOUT)
            }
}

fun appearance() {
    SettingsLabel("INTERFACE")
    InlineSwitch("Animated UI",
}

@Composable
private fun SettingsHome() {
    val context = LocalContext.current
    val showDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)
}

@Composable
private fun ProfileSettings(
    vm: PuppyClickerV6ViewModel,
    ui: PuppyUiState
) {
    val context = LocalContext.current
    val officialDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)
            if (officialDeveloper) {
                StatusLine("Account", "HarleyTG Developer / Owner")
                StatusLine("Studio", "Harley's Studios")
            }
}

@Composable
private fun DiscordSettings() {
    val context = LocalContext.current
    val officialDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)
    if (officialDeveloper) {
        Unit
    }
}
'''
        try:
            patched = patch_settings(source)
        except RuntimeError as exc:
            self.fail(f"profile settings patch should tolerate another developer identity state: {exc}")

        settings_home = patched.index("private fun SettingsHome()")
        profile = patched.index("private fun ProfileSettings(")
        discord = patched.index("private fun DiscordSettings()")
        self.assertNotIn("val gameState by vm.state.collectAsStateWithLifecycle()", patched[settings_home:profile])
        self.assertIn("val gameState by vm.state.collectAsStateWithLifecycle()", patched[profile:discord])
        self.assertIn('StatusLine("Badge", PuppyReleaseMilestones.RELEASE_1_0_BADGE_NAME)', patched[profile:discord])
        self.assertIn("PuppyPlayerProgressCard(gameState)", patched[profile:discord])
        self.assertIn("PuppyAchievementsSection(gameState)", patched[profile:discord])
        self.assertNotIn("val gameState by vm.state.collectAsStateWithLifecycle()", patched[discord:])

    def test_rewards_patch_does_not_add_player_progression_sections(self) -> None:
        source = '''@Composable
internal fun PuppyRevampedRewardsScreen(state: V6GameState) {
    Column {
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
            ),
        ) {}
    }
}
'''
        patched = patch_main_screens(source)
        self.assertNotIn("PuppyPlayerProgressCard(state)", patched)
        self.assertNotIn("PuppyAchievementsSection(state)", patched)
        self.assertNotIn("PuppyAchievementsV6.statuses", patched)


if __name__ == "__main__":
    unittest.main()
