from pathlib import Path

path = Path('app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt')
s = path.read_text()
old = '@Composable\nprivate fun V6Shop(state: V6GameState, vm: PuppyClickerV6ViewModel) {'
new = '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun V6Shop(state: V6GameState, vm: PuppyClickerV6ViewModel) {'
if new in s:
    raise SystemExit('Material3 opt-in already present')
if old not in s:
    raise SystemExit('V6Shop marker not found')
path.write_text(s.replace(old, new, 1))
