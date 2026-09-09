#!/usr/bin/env python3
"""Apply Android-only seasonal integration to the existing generated V6 sources.

The Android project already compiles from a generated source copy. Keep the original
sources untouched and fail the build if an integration anchor changes upstream.
"""
from pathlib import Path
import sys

PACKAGE = Path('com/harleytg/puppyclicker')


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected one integration anchor, found {count}')
    return source.replace(old, new, 1)


def patch_view_model(source: str) -> str:
    source = replace_once(source, 'import java.time.LocalDate\n',
        'import java.time.LocalDate\nimport java.time.Instant\nimport java.time.ZoneId\n', 'time imports')
    source = replace_once(source,
        '    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)\n',
        '''    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val seasonalStore = SeasonalPuppyStore(application)
    val seasonalSettings: StateFlow<SeasonalPuppySettings> = seasonalStore.settings
    val seasonalClock: StateFlow<SeasonalPuppyClock> = seasonalStore.clock
''', 'seasonal store')
    source = replace_once(source, '                seconds++\n                consumeClaimedAfkReward()',
        '                seconds++\n                if (seconds % 30 == 0) refreshSeasonalEvents()\n                consumeClaimedAfkReward()', 'event clock')
    source = replace_once(source, '    fun redeemCode(rawCode: String): V6RedeemOutcome {', '''    fun refreshSeasonalEvents() = seasonalStore.refreshClock()

    fun setSeasonalBirthday(month: Int, day: Int): Boolean = seasonalStore.saveBirthday(month, day)
    fun clearSeasonalBirthday() = seasonalStore.clearBirthday()
    fun dismissSeasonalIntro() = seasonalStore.dismissIntro()
    fun markSeasonalSeen(cycle: String) = seasonalStore.markSeen(cycle)

    private fun activeSeasonalWindow(id: String): SeasonalWindow? {
        val event = SeasonalPuppyEvents.find(id) ?: return null
        return SeasonalPuppyEvents.activeWindow(
            event, Instant.now(), ZoneId.systemDefault(), seasonalSettings.value.birthday
        )
    }

    fun claimSeasonalPuppy(id: String): V6RedeemOutcome {
        val event = SeasonalPuppyEvents.find(id)
            ?: return V6RedeemOutcome(false, "Unknown seasonal puppy.")
        val window = activeSeasonalWindow(id)
            ?: return V6RedeemOutcome(false, SeasonalPuppyEvents.availability(
                event, Instant.now(), ZoneId.systemDefault(), seasonalSettings.value.birthday
            ))
        val current = _state.value
        if (id in current.unlockedPuppies) {
            return V6RedeemOutcome(false, "${event.title} is already in your collection.")
        }
        _state.value = current.copy(
            unlockedPuppies = current.unlockedPuppies + id,
            puppyStyle = id
        )
        saveState()
        seasonalStore.markSeen(window.cycle)
        return V6RedeemOutcome(true, "${event.title} unlocked permanently!")
    }

    fun redeemCode(rawCode: String): V6RedeemOutcome {''', 'seasonal ViewModel methods')
    source = replace_once(source, '''        if (reward.id in s.redeemedCodeIds) {
            return V6RedeemOutcome(false, "That Puppy Code was already redeemed on this device.")
        }

        val puppy =''', '''        if (reward.id in s.redeemedCodeIds) {
            return V6RedeemOutcome(false, "That Puppy Code was already redeemed on this device.")
        }
        val seasonalEvent = reward.puppyId?.let(SeasonalPuppyEvents::find)
        if (seasonalEvent != null && activeSeasonalWindow(seasonalEvent.puppyId) == null) {
            return V6RedeemOutcome(false, SeasonalPuppyEvents.availability(
                seasonalEvent, Instant.now(), ZoneId.systemDefault(), seasonalSettings.value.birthday
            ))
        }

        val puppy =''', 'seasonal code gate')
    return source


def patch_activity(source: str) -> str:
    source = replace_once(source,
        '    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }',
        '    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }\n    SeasonalWelcomeGate(vm)', 'welcome overlay')
    source = replace_once(source,
        '        V6PuppyRosterSection("V1 Puppies", V1_PUPPY_STYLES, state, vm)',
        '''        SeasonalCollectionPanel(state, vm)
        V6PuppyRosterSection("V1 Puppies", V1_PUPPY_STYLES.filterNot { SeasonalPuppyEvents.isSeasonal(it.id) }, state, vm)''', 'seasonal collection')
    source = replace_once(source,
        '        V6Switch("🔢", "Compact numbers", "Use K/M/B abbreviations.", state.compactNumbers, vm::setCompactNumbers)',
        '''        V6Switch("🔢", "Compact numbers", "Use K/M/B abbreviations.", state.compactNumbers, vm::setCompactNumbers)
        Spacer(Modifier.height(14.dp))
        SeasonalBirthdaySettings(vm)''', 'birthday settings')
    return source


def main(root: Path) -> None:
    for filename, patch in (
        ('PuppyClickerV6ViewModel.kt', patch_view_model),
        ('PuppyClickerV6Activity.kt', patch_activity),
    ):
        target = root / PACKAGE / filename
        original = target.read_text(encoding='utf-8')
        updated = patch(original)
        target.write_text(updated, encoding='utf-8')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: patch_seasonal_events.py GENERATED_SOURCE_ROOT')
    main(Path(sys.argv[1]))
