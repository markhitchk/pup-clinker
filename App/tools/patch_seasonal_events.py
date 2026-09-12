#!/usr/bin/env python3
"""Apply Android-only seasonal and dynamic-roster integration to generated V6 sources.

The Android project compiles from a generated source copy. Keep the original sources
stable and fail the build if an integration anchor changes upstream.
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
    source = replace_once(source,
        '''    init {
        rollDailyDayIfNeeded()''',
        '''    init {
        syncDynamicFreePuppies()
        viewModelScope.launch {
            DynamicPuppyRoster.groups.collect { syncDynamicFreePuppies() }
        }
        rollDailyDayIfNeeded()''',
        'dynamic free roster observer')
    source = replace_once(source, '                seconds++\n                consumeClaimedAfkReward()',
        '                seconds++\n                if (seconds % 30 == 0) refreshSeasonalEvents()\n                consumeClaimedAfkReward()', 'event clock')
    source = replace_once(source, '    fun redeemCode(rawCode: String): V6RedeemOutcome {', '''    private fun syncDynamicFreePuppies() {
        val free = DynamicPuppyRoster.freeIds()
        if (free.isEmpty()) return
        val current = _state.value
        val next = current.unlockedPuppies + free
        if (next != current.unlockedPuppies) {
            _state.value = current.copy(unlockedPuppies = next)
            saveState()
        }
    }

    fun refreshSeasonalEvents() = seasonalStore.refreshClock()

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

    fun redeemCode(rawCode: String): V6RedeemOutcome {''', 'seasonal and dynamic ViewModel methods')
    source = replace_once(source,
        '        val reward = LocalRedeemCodes.find(rawCode)\n',
        '        val reward = StreamedRedeemCodes.find(rawCode)\n',
        'streamed redeem lookup')
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
    source = replace_once(source,
        '        val puppy = reward.puppyId?.let { id -> V6_PUPPY_STYLES.firstOrNull { it.id == id } }',
        '        val puppy = reward.puppyId?.let(DynamicPuppyRoster::style)',
        'dynamic redeem puppy lookup')
    source = replace_once(source,
        '''    fun setPuppyStyle(id: String) {
        val s = _state.value
        if (id !in s.unlockedPuppies || id !in V6_PUPPY_IDS) return''',
        '''    fun setPuppyStyle(id: String) {
        val s = _state.value
        if (id !in s.unlockedPuppies || !DynamicPuppyRoster.isKnown(id)) return''',
        'dynamic puppy selection')
    source = replace_once(source,
        '''        val unlocked = (prefs.getStringSet(KEY_UNLOCKED_PUPPIES, DEFAULT_V6_PUPPIES)?.toSet() ?: DEFAULT_V6_PUPPIES) + DEFAULT_V6_PUPPIES
        val style = prefs.getString(KEY_PUPPY_STYLE, "classic")
            ?.takeIf { it in unlocked && it in V6_PUPPY_IDS }
            ?: "classic"''',
        '''        val unlocked = (prefs.getStringSet(KEY_UNLOCKED_PUPPIES, DEFAULT_V6_PUPPIES)?.toSet() ?: DEFAULT_V6_PUPPIES) +
            DEFAULT_V6_PUPPIES + DynamicPuppyRoster.freeIds()
        val style = prefs.getString(KEY_PUPPY_STYLE, "classic")
            ?.takeIf { it in unlocked && DynamicPuppyRoster.isKnown(it) }
            ?: "classic"''',
        'dynamic save restore')
    return source


def patch_activity(source: str) -> str:
    source = replace_once(source,
        '    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }',
        '    var tab by rememberSaveable { mutableStateOf(V6Tab.PLAY) }\n    SeasonalWelcomeGate(vm)', 'welcome overlay')
    source = replace_once(source,
        '    var renameOpen by rememberSaveable { mutableStateOf(false) }\n    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {',
        '''    var renameOpen by rememberSaveable { mutableStateOf(false) }
    val dynamicGroups by DynamicPuppyRoster.groups.collectAsStateWithLifecycle()
    val dynamicIds = remember(dynamicGroups) {
        dynamicGroups.flatMap { group -> group.puppies.map { it.id } }.toSet()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {''',
        'dynamic roster state')
    source = replace_once(source,
        '        V6Header("Puppy Collection", "V1 classics and V2 puppies in one collection.")',
        '        V6Header("Puppy Collection", "V1, V2, test and future streamed puppy rosters in one collection.")',
        'dynamic roster header')
    source = replace_once(source,
        '        Text("Unlocked ${state.unlockedPuppies.count { it in V6_PUPPY_IDS }} / ${V6_PUPPY_STYLES.size}", fontWeight = FontWeight.Bold)',
        '        Text("Unlocked ${state.unlockedPuppies.count { it in dynamicIds }} / ${dynamicIds.size}", fontWeight = FontWeight.Bold)',
        'dynamic roster count')
    source = replace_once(source,
        '        V6PuppyRosterSection("V1 Puppies", V1_PUPPY_STYLES, state, vm)',
        '''        SeasonalCollectionPanel(state, vm)
        V6PuppyRosterSection("V1 Puppies", V1_PUPPY_STYLES.filterNot { SeasonalPuppyEvents.isSeasonal(it.id) }, state, vm)''', 'seasonal collection')
    source = replace_once(source,
        '        V6PuppyRosterSection("V2 Puppies", V2_PUPPY_STYLES, state, vm)',
        '''        V6PuppyRosterSection("V2 Puppies", V2_PUPPY_STYLES, state, vm)
        dynamicGroups.filterNot { it.id == "v1" || it.id == "v2" }.forEach { group ->
            Spacer(Modifier.height(8.dp))
            V6PuppyRosterSection(group.title, group.puppies, state, vm)
        }''',
        'dynamic roster groups')
    source = replace_once(source,
        '''                    if (puppy.redeemOnly) {
                        val generation = if (puppy.id in V2_PUPPY_IDS) "V2" else "V1"
                        Text(
                            if (unlocked) "$generation Puppy Code unlocked" else "$generation Puppy Code unlock",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }''',
        '''                    val rosterAsset = DynamicPuppyRoster.asset(puppy.id)
                    if (rosterAsset?.free == true) {
                        Text("Free puppy · automatically owned", style = MaterialTheme.typography.labelSmall)
                    } else if (puppy.redeemOnly) {
                        val rosterName = rosterAsset?.groupTitle?.removeSuffix(" Puppies") ?: "Special"
                        Text(
                            if (unlocked) "$rosterName special puppy unlocked" else "$rosterName special puppy unlock",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }''',
        'dynamic roster labels')
    # Older V6 builds hosted birthday controls directly in the Activity Settings panel.
    # Current builds use PuppySettingsScreen/ProfileSettings, which already persists the
    # month/day and calls vm.setSeasonalBirthday(). Only inject the legacy control when
    # that legacy Settings anchor is actually present.
    birthday_settings_anchor = '        V6Switch("🔢", "Compact numbers", "Use K/M/B abbreviations.", state.compactNumbers, vm::setCompactNumbers)'
    if birthday_settings_anchor in source:
        source = replace_once(source,
            birthday_settings_anchor,
            '''        V6Switch("🔢", "Compact numbers", "Use K/M/B abbreviations.", state.compactNumbers, vm::setCompactNumbers)
        Spacer(Modifier.height(14.dp))
        SeasonalBirthdaySettings(vm)''', 'birthday settings')
    source = replace_once(source,
        '                Text("Puppy Codes work locally. They can grant treats or special puppies, but never Upgrade Tickets or prestige points.")',
        '                Text("Puppy Codes sync from the live catalog when available and stay cached for offline use. They can grant treats or special puppies, but never seasonal puppies, Upgrade Tickets or prestige points.")',
        'redeem dialog copy')
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
