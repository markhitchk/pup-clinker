from pathlib import Path
import sys
import tempfile
import unittest

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

from patch_android_1_0_runner import pre_normalize


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


if __name__ == "__main__":
    unittest.main()
