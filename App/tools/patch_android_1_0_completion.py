#!/usr/bin/env python3
"""Final generated-source integration for the approved Puppy Clicker Android 1.0 program."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Android 1.0 patch {label}: expected 1 match, found {count}")
    return source.replace(old, new, 1)


def replace_all(source: str, old: str, new: str, label: str, minimum: int = 1) -> str:
    count = source.count(old)
    if count < minimum:
        raise RuntimeError(f"Android 1.0 patch {label}: expected at least {minimum} matches, found {count}")
    return source.replace(old, new)


def section(source: str, start_marker: str, end_marker: str, label: str) -> tuple[int, int, str]:
    start = source.find(start_marker)
    if start < 0:
        raise RuntimeError(f"Android 1.0 patch {label}: start marker not found")
    end = source.find(end_marker, start)
    if end < 0:
        raise RuntimeError(f"Android 1.0 patch {label}: end marker not found")
    return start, end, source[start:end]


def replace_section(source: str, start_marker: str, end_marker: str, transform, label: str) -> str:
    start, end, body = section(source, start_marker, end_marker, label)
    return source[:start] + transform(body) + source[end:]


def patch_view_model(source: str) -> str:
    if "val playerXp: Long = 0" in source:
        return source

    source = replace_once(
        source,
        '    val bond: Int = 10,\n    val careActions: Long = 0,',
        '''    val bond: Int = 10,
    val bondByPuppyId: Map<String, Int> = emptyMap(),
    val playerXp: Long = 0,
    val achievementRewardedIds: Set<String> = emptySet(),
    val xpSettlementIds: Set<String> = emptySet(),
    val releaseClaimIds: Set<String> = emptySet(),
    val profileBadgeIds: Set<String> = emptySet(),
    val careActions: Long = 0,''',
        "V6 progression fields",
    )
    source = replace_once(
        source,
        '''    val level: Int
        get() = 1 + sqrt(lifetimeTreats.coerceAtLeast(0).toDouble() / 100.0).toInt()''',
        '''    val level: Int
        get() = PuppyProgression.levelForXp(playerXp)''',
        "XP level curve",
    )

    # Manual taps earn XP, independent of Treat multiplier / active-bonus size.
    source = replace_once(
        source,
        '''            totalTaps = nextTotalTaps,''',
        '''            playerXp = PuppyProgression.addXp(current.playerXp, PuppyXpEvent.MANUAL_TAP),
            totalTaps = nextTotalTaps,''',
        "manual tap XP",
    )

    # Every successful Care action settles +5 XP. Existing action guards remain authoritative.
    source = replace_all(
        source,
        "            careActions = safeAdd(s.careActions, 1)",
        "            playerXp = PuppyProgression.addXp(s.playerXp, PuppyXpEvent.CARE_ACTION),\n            careActions = safeAdd(s.careActions, 1)",
        "Care XP",
        minimum=5,
    )

    bond_deltas = {
        "            bond = (s.bond + 1).coerceAtMost(100),": 1,
        "            bond = (s.bond + 3).coerceAtMost(100),": 3,
        "            bond = (s.bond + 2).coerceAtMost(100),": 2,
    }
    for old, delta in bond_deltas.items():
        if old in source:
            source = source.replace(
                old,
                f'''            bondByPuppyId = PuppyProgression.withBondDelta(
                s.bondByPuppyId,
                s.puppyStyle,
                {delta}
            ),
{old}'''
            )

    # Park and daily reward use the same compatibility mirror but target the active puppy map.
    source = source.replace(
        "            bond = (s.bond + 3).coerceAtMost(100),\n            parkActive = false,",
        '''            bondByPuppyId = PuppyProgression.withBondDelta(s.bondByPuppyId, s.puppyStyle, 3),
            bond = (s.bond + 3).coerceAtMost(100),
            parkActive = false,'''
    ) if "            parkActive = false," in source and "PuppyProgression.withBondDelta(s.bondByPuppyId, s.puppyStyle, 3),\n            bond = (s.bond + 3)" not in source else source
    source = source.replace(
        "            bond = (s.bond + 1).coerceAtMost(100)\n        )\n        saveState()\n        PuppyNotificationCenter.cancelDailyReward",
        '''            bondByPuppyId = PuppyProgression.withBondDelta(s.bondByPuppyId, s.puppyStyle, 1),
            bond = (s.bond + 1).coerceAtMost(100)
        )
        saveState()
        PuppyNotificationCenter.cancelDailyReward'''
    ) if "PuppyNotificationCenter.cancelDailyReward" in source else source

    # Daily tasks use a stable settlement ID in addition to the existing claimed-task gate.
    source = replace_once(
        source,
        '''        val goal = PuppyMonthlyRewards.goalToday(id) ?: return
        if (!goal.isComplete(s)) return

        _state.value = s.copy(''',
        '''        val goal = PuppyMonthlyRewards.goalToday(id) ?: return
        if (!goal.isComplete(s)) return
        val xpSettlement = PuppyProgressionSettlement.apply(
            currentXp = s.playerXp,
            settlements = s.xpSettlementIds,
            event = PuppyXpEvent.DAILY_TASK,
            settlementId = "daily:${s.dailyDay}:$id"
        )

        _state.value = s.copy(''',
        "daily task XP settlement",
    )
    source = replace_once(
        source,
        '''            claimedDailyTasks = s.claimedDailyTasks + id''',
        '''            playerXp = xpSettlement.xp,
            xpSettlementIds = xpSettlement.settlements,
            claimedDailyTasks = s.claimedDailyTasks + id''',
        "daily task XP state",
    )

    # New puppy ownership XP is a stable once-per-puppy settlement and is shared by every acquisition path.
    helper_anchor = "    private fun persistCasinoMutation("
    helper = '''    private fun awardNewPuppyXp(before: V6GameState, after: V6GameState): V6GameState {
        var next = after
        (after.unlockedPuppies - before.unlockedPuppies).sorted().forEach { puppyId ->
            val settlement = PuppyProgressionSettlement.apply(
                currentXp = next.playerXp,
                settlements = next.xpSettlementIds,
                event = PuppyXpEvent.NEW_PUPPY,
                settlementId = "puppy:$puppyId"
            )
            next = next.copy(
                playerXp = settlement.xp,
                xpSettlementIds = settlement.settlements
            )
        }
        return next
    }

    private fun settleNewAchievements(state: V6GameState): V6GameState {
        val completed = PuppyAchievementsV6.newlyCompleted(state, state.achievementRewardedIds)
        if (completed.isEmpty()) return state
        var xp = state.playerXp
        completed.forEach { xp = PuppyProgression.addXp(xp, PuppyXpEvent.ACHIEVEMENT) }
        return state.copy(
            playerXp = xp,
            achievementRewardedIds = state.achievementRewardedIds + completed.map { it.id }
        )
    }

    fun claimOnePointZeroLaunch() {
        val current = _state.value
        val result = PuppyReleaseMilestones.claimOnePointZero(
            claims = current.releaseClaimIds,
            badges = current.profileBadgeIds
        )
        if (!result.applied) return
        _state.value = current.copy(
            releaseClaimIds = result.claims,
            profileBadgeIds = result.badges
        )
        saveState()
    }

'''
    source = replace_once(source, helper_anchor, helper + helper_anchor, "progression helpers")

    # Redeem, Gacha and exchange paths all pass newly acquired ownership through the shared helper.
    source = replace_once(
        source,
        '''            redeemedCodeIds = s.redeemedCodeIds + reward.id
        )
        saveState()
        return V6RedeemOutcome(true, reward.message)''',
        '''            redeemedCodeIds = s.redeemedCodeIds + reward.id
        )
        _state.value = awardNewPuppyXp(s, _state.value)
        saveState()
        return V6RedeemOutcome(true, reward.message)''',
        "redeem ownership XP",
    )
    source = replace_once(
        source,
        '''            ticketInventory = nextInventory,
            unlockedPuppies = current.unlockedPuppies + selected.id
        )
        saveState()''',
        '''            ticketInventory = nextInventory,
            unlockedPuppies = current.unlockedPuppies + selected.id
        )
        _state.value = awardNewPuppyXp(current, _state.value)
        saveState()''',
        "Gacha ownership XP",
    )
    source = replace_once(
        source,
        '''        _state.value = current.copy(unlockedPuppies = current.unlockedPuppies + puppyId)
        saveState()''',
        '''        _state.value = current.copy(unlockedPuppies = current.unlockedPuppies + puppyId)
        _state.value = awardNewPuppyXp(current, _state.value)
        saveState()''',
        "gift ownership XP",
    )
    source = replace_all(
        source,
        '''            puppyStyle = nextStyle
        )
        saveState()''',
        '''            puppyStyle = nextStyle,
            bond = PuppyProgression.bondFor(current.bondByPuppyId, nextStyle)
        )
        _state.value = awardNewPuppyXp(current, _state.value)
        saveState()''',
        "trade ownership XP and Bond mirror",
        minimum=2,
    )

    source = replace_once(
        source,
        '''        _state.value = s.copy(puppyStyle = id)
        saveState()''',
        '''        _state.value = s.copy(
            puppyStyle = id,
            bond = PuppyProgression.bondFor(s.bondByPuppyId, id)
        )
        saveState()''',
        "selected puppy Bond mirror",
    )

    # Casino settlements may grant puppies. Apply ownership XP before any transaction data is persisted.
    def transform_casino(body: str) -> str:
        body = replace_once(body, "        if (!result.success) return result\n", '''        if (!result.success) return result
        val progressedResult = result.copy(state = awardNewPuppyXp(before, result.state))
''', "casino progression result")
        for field in ("state", "activeRound", "completedRoundIds"):
            body = body.replace(f"result.{field}", f"progressedResult.{field}")
        # Restore the original reference inside the copy expression altered above.
        body = body.replace("progressedResult.copy(state = awardNewPuppyXp(before, progressedResult.state))", "result.copy(state = awardNewPuppyXp(before, result.state))")
        if body.rstrip().endswith("return result"):
            body = body.rstrip()[:-len("return result")] + "return progressedResult\n"
        else:
            body = body.replace("        return result\n", "        return progressedResult\n")
        return body

    source = replace_section(
        source,
        "    private fun persistCasinoMutation(",
        "    private fun loadState(): V6GameState",
        transform_casino,
        "casino mutation",
    )

    # Load progression with deterministic legacy migration. Existing qualifying achievements become
    # migration-settled so established saves do not receive a retroactive XP spike.
    def transform_load(body: str) -> str:
        body = replace_once(body, "        return V6GameState(\n", "        val loaded = V6GameState(\n", "load temp state")
        body = replace_once(
            body,
            '''            lifetimeTreats = prefs.getLong(KEY_LIFETIME, 0L).coerceAtLeast(0L),''',
            '''            lifetimeTreats = prefs.getLong(KEY_LIFETIME, 0L).coerceAtLeast(0L),
            playerXp = PuppyProgressionStore.migrateXp(
                existingXp = if (prefs.contains(PuppyProgressionStore.KEY_PLAYER_XP)) {
                    prefs.getLong(PuppyProgressionStore.KEY_PLAYER_XP, 0L)
                } else null,
                lifetimeTreats = prefs.getLong(KEY_LIFETIME, 0L)
            ),''',
            "load XP",
        )
        body = replace_once(
            body,
            '''            bond = prefs.getInt(KEY_BOND, 10).coerceIn(0, 100),''',
            '''            bondByPuppyId = PuppyProgressionStore.migrateBondMap(
                existingRaw = prefs.getString(PuppyProgressionStore.KEY_BOND_BY_PUPPY, null),
                activePuppyId = style,
                legacyBond = prefs.getInt(KEY_BOND, 10)
            ),
            bond = PuppyProgression.bondFor(
                PuppyProgressionStore.migrateBondMap(
                    existingRaw = prefs.getString(PuppyProgressionStore.KEY_BOND_BY_PUPPY, null),
                    activePuppyId = style,
                    legacyBond = prefs.getInt(KEY_BOND, 10)
                ),
                style
            ),''',
            "load per-puppy Bond",
        )
        body = replace_once(
            body,
            '''            redeemedCodeIds = prefs.getStringSet(KEY_REDEEMED_CODES, emptySet())?.toSet() ?: emptySet(),''',
            '''            redeemedCodeIds = prefs.getStringSet(KEY_REDEEMED_CODES, emptySet())?.toSet() ?: emptySet(),
            achievementRewardedIds = prefs.getStringSet(PuppyProgressionStore.KEY_ACHIEVEMENT_REWARDED, emptySet())?.toSet() ?: emptySet(),
            xpSettlementIds = prefs.getStringSet(PuppyProgressionStore.KEY_XP_SETTLEMENTS, emptySet())?.toSet() ?: emptySet(),
            releaseClaimIds = prefs.getStringSet("release_claim_ids_v1", emptySet())?.toSet() ?: emptySet(),
            profileBadgeIds = prefs.getStringSet("profile_badge_ids_v1", emptySet())?.toSet() ?: emptySet(),''',
            "load progression sets",
        )
        closing = "        )\n"
        pos = body.rfind(closing)
        if pos < 0:
            raise RuntimeError("Android 1.0 patch load migration: constructor close not found")
        suffix = '''        )
        val migratedAchievementIds = if (prefs.contains(PuppyProgressionStore.KEY_ACHIEVEMENT_REWARDED)) {
            loaded.achievementRewardedIds
        } else {
            PuppyAchievementsV6.definitions
                .filter { PuppyAchievementsV6.status(it, loaded, emptySet()).completed }
                .mapTo(linkedSetOf()) { it.id }
        }
        return loaded.copy(achievementRewardedIds = migratedAchievementIds)
'''
        return body[:pos] + suffix + body[pos + len(closing):]

    source = replace_section(
        source,
        "    private fun loadState(): V6GameState",
        "    private fun saveState(clearUpgradeKeys: Boolean = false)",
        transform_load,
        "load state",
    )

    def transform_save(body: str) -> str:
        body = replace_once(
            body,
            "        val s = _state.value\n",
            '''        var s = settleNewAchievements(_state.value)
        if (s != _state.value) _state.value = s
''',
            "settle achievements on save",
        )
        body = replace_once(
            body,
            '''            putLong(KEY_LIFETIME, s.lifetimeTreats)
            putLong(KEY_TOTAL_SHOP, s.totalShopPurchases)''',
            '''            putLong(KEY_LIFETIME, s.lifetimeTreats)
            putLong(PuppyProgressionStore.KEY_PLAYER_XP, s.playerXp)
            putString(PuppyProgressionStore.KEY_BOND_BY_PUPPY, PuppyProgressionStore.encodeBondMap(s.bondByPuppyId))
            putStringSet(PuppyProgressionStore.KEY_ACHIEVEMENT_REWARDED, s.achievementRewardedIds)
            putStringSet(PuppyProgressionStore.KEY_XP_SETTLEMENTS, s.xpSettlementIds)
            putStringSet("release_claim_ids_v1", s.releaseClaimIds)
            putStringSet("profile_badge_ids_v1", s.profileBadgeIds)
            putLong(KEY_TOTAL_SHOP, s.totalShopPurchases)''',
            "save progression",
        )
        end = "        }.apply()\n"
        if end not in body:
            raise RuntimeError("Android 1.0 patch save widget: SharedPreferences apply not found")
        body = body.replace(
            end,
            '''        }.apply()
        runCatching {
            PuppyWidgetSnapshotStore.write(
                getApplication(),
                PuppyWidgetSnapshot(
                    puppyName = s.puppyName,
                    puppyStyle = s.puppyStyle,
                    treats = s.treats,
                    careScore = s.careScore,
                    bond = s.bond,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            PuppyHomeWidgetProvider.refreshAll(getApplication())
        }
''',
            1,
        )
        return body

    source = replace_section(
        source,
        "    private fun saveState(clearUpgradeKeys: Boolean = false)",
        "    override fun onCleared()",
        transform_save,
        "save state",
    )

    # Preserve progression across the non-prestige reset helper introduced by Exchange.
    reset_anchor = '''            redeemedCodeIds = current.redeemedCodeIds,
            hapticsEnabled = current.hapticsEnabled,'''
    if reset_anchor in source:
        source = source.replace(
            reset_anchor,
            '''            redeemedCodeIds = current.redeemedCodeIds,
            bond = current.bond,
            bondByPuppyId = current.bondByPuppyId,
            playerXp = current.playerXp,
            achievementRewardedIds = current.achievementRewardedIds,
            xpSettlementIds = current.xpSettlementIds,
            releaseClaimIds = current.releaseClaimIds,
            profileBadgeIds = current.profileBadgeIds,
            hapticsEnabled = current.hapticsEnabled,''',
            1,
        )

    # Harden claim consumption: synchronously clear the staged claim and persist the settlement ID
    # before mutating gameplay state, so duplicate lifecycle callbacks cannot settle it twice.
    def transform_afk_claim(body: str) -> str:
        if "PuppyNotificationHistory.recordSystemReward(" in body:
            return body
        old = '''        val amount = prefs.getLong(KEY_AFK_CLAIM_READY, 0L).coerceAtLeast(0L)
        if (amount <= 0L) return
        prefs.edit().putLong(KEY_AFK_CLAIM_READY, 0L).apply()
        _state.update { s ->
            s.copy(
                treats = safeAdd(s.treats, amount),
                lifetimeTreats = safeAdd(s.lifetimeTreats, amount),
                afkLastClaimed = amount
            )
        }
        saveState()
'''
        new = '''        val amount = prefs.getLong(KEY_AFK_CLAIM_READY, 0L).coerceIn(0L, 7_000L)
        if (amount <= 0L) return
        val claimId = prefs.getString(PuppyAfkPolicy.KEY_CLAIM_SETTLEMENT_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: "afk-legacy-claim:$amount"
        val lastSettled = prefs.getString(PuppyAfkPolicy.KEY_LAST_SETTLED_ID, null)
        if (claimId == lastSettled) {
            prefs.edit()
                .putLong(KEY_AFK_CLAIM_READY, 0L)
                .remove(PuppyAfkPolicy.KEY_CLAIM_SETTLEMENT_ID)
                .commit()
            return
        }
        val consumed = prefs.edit()
            .putLong(KEY_AFK_CLAIM_READY, 0L)
            .remove(PuppyAfkPolicy.KEY_CLAIM_SETTLEMENT_ID)
            .putString(PuppyAfkPolicy.KEY_LAST_SETTLED_ID, claimId)
            .commit()
        if (!consumed) return
        _state.update { s ->
            s.copy(
                treats = safeAdd(s.treats, amount),
                lifetimeTreats = safeAdd(s.lifetimeTreats, amount),
                afkLastClaimed = amount
            )
        }
        saveState()
'''
        return replace_once(body, old, new, "AFK claim body")

    source = replace_section(
        source,
        "    private fun consumeClaimedAfkReward()",
        "    private fun rollDailyDayIfNeeded()",
        transform_afk_claim,
        "AFK claim",
    )
    return source


def patch_notifications(source: str) -> str:
    if "PuppyNotificationHistory.record(" in source and "historyItemFor(" in source:
        return source

    source = source.replace(
        "        if (PuppyAppRuntime.isForeground || !canNotify(app)) return\n",
        "        if (PuppyAppRuntime.isForeground) return\n",
        1,
    )
    source = source.replace(
        "        if (PuppyAppRuntime.isForeground || !canNotify(context)) return\n",
        "",
    )
    # Roster history must still be generated when Android notification permission is denied.
    source = source.replace("        if (!canNotify(app)) return\n", "", 1)

    helper_anchor = "    private fun post(\n"
    helper = '''    private fun historyItemFor(
        context: Context,
        id: Int,
        title: String,
        text: String
    ): PuppyNotificationItem? {
        val game = context.getSharedPreferences(PuppyClickerV6ViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        return when (id) {
            NOTIFY_DAILY -> PuppyNotificationItem(
                id = "daily:${LocalDate.now().toEpochDay()}",
                type = PuppyNotificationType.DAILY_REWARD,
                title = title,
                body = text,
                createdAtMs = System.currentTimeMillis(),
                read = false,
                route = PuppyNotificationRoute.REWARDS
            )
            NOTIFY_EVENT -> {
                val readyAt = game.getLong("park_ready_at", 0L)
                PuppyNotificationItem(
                    id = "park:$readyAt",
                    type = PuppyNotificationType.PARK_READY,
                    title = title,
                    body = text,
                    createdAtMs = System.currentTimeMillis(),
                    read = false,
                    route = PuppyNotificationRoute.REWARDS
                )
            }
            NOTIFY_UPDATE -> {
                val update = _updateNotice.value ?: return null
                PuppyNotificationItem(
                    id = "update:${update.versionCode}",
                    type = PuppyNotificationType.APP_UPDATE,
                    title = title,
                    body = text,
                    createdAtMs = System.currentTimeMillis(),
                    read = false,
                    route = PuppyNotificationRoute.RELEASE_HUB,
                    externalUrl = update.releaseUrl
                )
            }
            NOTIFY_ROSTER -> PuppyNotificationItem(
                id = "roster:${title.hashCode()}:${text.hashCode()}",
                type = PuppyNotificationType.ROSTER_UPDATE,
                title = title,
                body = text,
                createdAtMs = System.currentTimeMillis(),
                read = false,
                route = PuppyNotificationRoute.ROSTER
            )
            else -> null
        }
    }

'''
    source = replace_once(source, helper_anchor, helper + helper_anchor, "notification history helper")
    source = replace_once(
        source,
        '''    ) {
        if (!canNotify(context)) return
        val openApp = PendingIntent.getActivity(''',
        '''    ) {
        historyItemFor(context, id, title, text)?.let { item ->
            PuppyNotificationHistory.record(context, item)
        }
        if (PuppyAppRuntime.isForeground || !canNotify(context)) return
        val openApp = PendingIntent.getActivity(''',
        "notification post history",
    )
    return source


def patch_activity(source: str) -> str:
    if "PuppyNotificationHistory.unreadCount" in source:
        return source

    source = replace_once(
        source,
        "    val updateNotice by PuppyNotificationCenter.updateNotice.collectAsStateWithLifecycle()\n",
        '''    val updateNotice by PuppyNotificationCenter.updateNotice.collectAsStateWithLifecycle()
    val notificationItems by PuppyNotificationHistory.items.collectAsStateWithLifecycle()
    val notificationUnreadCount by PuppyNotificationHistory.unreadCount.collectAsStateWithLifecycle()
''',
        "notification history flows",
    )
    source = replace_once(
        source,
        "    var notificationsOpen by rememberSaveable { mutableStateOf(false) }\n",
        '''    var notificationsOpen by rememberSaveable { mutableStateOf(false) }
    var releaseHubOpen by rememberSaveable { mutableStateOf(false) }
''',
        "release hub state",
    )
    source = replace_once(
        source,
        "                hasUnreadNotification = updateNotice?.unread == true,",
        "                hasUnreadNotification = notificationUnreadCount > 0,",
        "bell unread source",
    )
    old_open = '''                onOpenNotifications = {
                    notificationsOpen = true
                    PuppyNotificationCenter.markUpdateRead(context)
                    scope.launch {
                        PuppyNotificationCenter.refreshUpdateStatus(context, force = true)
                    }
                },'''
    new_open = '''                onOpenNotifications = {
                    notificationsOpen = true
                    scope.launch {
                        PuppyNotificationCenter.refreshUpdateStatus(context, force = true)
                    }
                },'''
    source = replace_once(source, old_open, new_open, "bell open behavior")
    source = replace_once(
        source,
        "            if (internal != null) {",
        '''            if (releaseHubOpen) {
                PuppyReleaseHubScreen(onBack = { releaseHubOpen = false })
            } else if (internal != null) {''',
        "release hub shell",
    )

    # Add descriptive semantics to the bottom navigation without changing visual labels.
    nav_anchor = '''                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },'''
    nav_replacement = '''                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            modifier = Modifier.semantics {
                                contentDescription = item.label + " tab" + if (tab == item) ", selected" else ""
                            },'''
    source = replace_once(source, nav_anchor, nav_replacement, "bottom navigation semantics")

    start = source.find("    if (notificationsOpen) {\n")
    end = source.find("\n}\n\n@Composable\nprivate fun PuppyFixedAppBar", start)
    if start < 0 or end < 0:
        raise RuntimeError("Android 1.0 patch notifications dialog block not found")
    replacement = '''    if (notificationsOpen) {
        PuppyNotificationInboxDialog(
            items = notificationItems,
            onDismiss = { notificationsOpen = false },
            onOpenItem = { item ->
                PuppyNotificationHistory.markRead(context, item.id)
                notificationsOpen = false
                when (item.route) {
                    PuppyNotificationRoute.REWARDS -> {
                        releaseHubOpen = false
                        internalDestination = null
                        tab = V6Tab.REWARDS
                    }
                    PuppyNotificationRoute.ROSTER -> {
                        releaseHubOpen = false
                        internalDestination = null
                        tab = V6Tab.ROSTER
                    }
                    PuppyNotificationRoute.RELEASE_HUB -> {
                        internalDestination = null
                        releaseHubOpen = true
                    }
                    PuppyNotificationRoute.NONE -> {
                        item.externalUrl?.takeIf { it.isNotBlank() }?.let { openPuppyUpdateUrl(context, it) }
                    }
                }
            },
            onClaimReward = { item -> vm.claimSystemReward(item.id) },
            onMarkAllRead = { PuppyNotificationHistory.markAllRead(context) }
        )
    }

    val showOnePointZeroCelebration =
        (BuildConfig.VERSION_NAME == "1.0" || BuildConfig.VERSION_NAME == "1.0.0") &&
            PuppyReleaseMilestones.RELEASE_1_0_CLAIM_ID !in state.releaseClaimIds
    if (showOnePointZeroCelebration) {
        PuppyOnePointZeroCelebrationDialog(
            onContinue = vm::claimOnePointZeroLaunch,
            onOpenReleaseHub = {
                vm.claimOnePointZeroLaunch()
                internalDestination = null
                releaseHubOpen = true
            }
        )
    }
'''
    source = source[:start] + replacement + source[end:]
    return source


def patch_roster(source: str) -> str:
    if "PuppyViewerDialog(" in source:
        return source
    old = '''            PuppyPreviewDialog(
                asset = asset,
                unlocked = id in state.unlockedPuppies,
                onDismiss = { previewId = null }
            )'''
    new = '''            PuppyViewerDialog(
                asset = asset,
                state = state,
                retryToken = retryTokens[id] ?: 0,
                onDismiss = { previewId = null },
                onUse = {
                    previewId = null
                    confirmUseId = id
                },
                onToggleFavorite = { vm.toggleFavoritePuppy(id) },
                onRetryArtwork = {
                    retryTokens[id] = (retryTokens[id] ?: 0) + 1
                },
                onRename = if (id == state.puppyStyle) {
                    {
                        previewId = null
                        renameOpen = true
                    }
                } else null
            )'''
    source = replace_once(source, old, new, "Puppy Viewer")
    source = replace_once(
        source,
        "                columns = GridCells.Adaptive(minSize = 100.dp),",
        '''                columns = GridCells.Adaptive(
                    minSize = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840) {
                        148.dp
                    } else {
                        100.dp
                    }
                ),''',
        "expanded roster grid",
    )
    return source


def patch_main_screens(source: str) -> str:
    if "PuppyAchievementsV6.statuses" in source:
        return source
    anchor = '''        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
            ),'''
    insertion = '''        Spacer(Modifier.height(14.dp))
        PuppyPlayerProgressCard(state)
        Spacer(Modifier.height(10.dp))
        PuppyAchievementsSection(state)

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
            ),'''
    source = replace_once(source, anchor, insertion, "Rewards progression section")
    source += '''

@Composable
private fun PuppyPlayerProgressCard(state: V6GameState) {
    val nextLevelXp = runCatching {
        val level = state.level.toLong().coerceAtLeast(1L)
        Math.multiplyExact(Math.multiplyExact(level, level), 100L)
    }.getOrDefault(Long.MAX_VALUE)
    val progressBase = if (state.level <= 1) 0L else {
        val previous = (state.level - 1).toLong()
        previous * previous * 100L
    }
    val span = (nextLevelXp - progressBase).coerceAtLeast(1L)
    val current = (state.playerXp - progressBase).coerceIn(0L, span)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Player Level ${state.level}", fontWeight = FontWeight.Black)
                Text("${state.playerXp} XP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { current.toFloat() / span.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
            )
            Text(
                if (nextLevelXp == Long.MAX_VALUE) "Maximum tracked level" else "$current / $span XP toward next level",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PuppyAchievementsSection(state: V6GameState) {
    val statuses = PuppyAchievementsV6.statuses(state, state.achievementRewardedIds)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Achievements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(
                    statuses.count { it.completed }.toString() + "/" + statuses.size,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            statuses.forEachIndexed { index, status ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status.definition.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(status.definition.title, fontWeight = FontWeight.Black)
                        Text(status.definition.description, style = MaterialTheme.typography.bodySmall)
                        Text(
                            status.progress.coerceAtMost(status.definition.target).toString() + "/" + status.definition.target,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (status.completed) "✓ Complete" else status.definition.rewardDescription,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (status.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
'''
    return source


def patch_settings(source: str) -> str:
    if "SettingsDestination.RELEASE_HUB" in source:
        return source
    source = replace_once(
        source,
        "    DEVELOPER,\n    ABOUT\n}",
        "    DEVELOPER,\n    RELEASE_HUB,\n    ABOUT\n}",
        "Release Hub destination enum",
    )
    about_case = '''        SettingsDestination.ABOUT -> SettingsSubpage(
            title = "About Puppy Clicker",'''
    release_case = '''        SettingsDestination.RELEASE_HUB -> PuppyReleaseHubScreen(
            onBack = { open(SettingsDestination.HOME) }
        )

        SettingsDestination.ABOUT -> SettingsSubpage(
            title = "About Puppy Clicker",'''
    source = replace_once(source, about_case, release_case, "Release Hub destination")
    advanced_about = '''            SettingsNavRow("ⓘ", "About Puppy Clicker", "Version, links, development, and legal") {
                onOpen(SettingsDestination.ABOUT)
            }'''
    advanced_release = '''            SettingsNavRow("↻", "Release Hub", "What's New, installed build, and updates") {
                onOpen(SettingsDestination.RELEASE_HUB)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsNavRow("ⓘ", "About Puppy Clicker", "Version, links, development, and legal") {
                onOpen(SettingsDestination.ABOUT)
            }'''
    source = replace_once(source, advanced_about, advanced_release, "Release Hub Settings row")
    source = replace_once(
        source,
        '''    SettingsLabel("INTERFACE")
    InlineSwitch("Animated UI",''',
        '''    SettingsLabel("INTERFACE")
    PuppyPerformancePresetSelector()
    Spacer(Modifier.height(10.dp))
    InlineSwitch("Animated UI",''',
        "performance preset selector",
    )

    # Surface the 1.0 profile entitlement without changing device-bound identity behavior.
    source = replace_once(
        source,
        '''    val officialDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)
''',
        '''    val officialDeveloper = PuppyPlayerIdentity.isHarleyTgDeveloper(context)
    val gameState by vm.state.collectAsStateWithLifecycle()
''',
        "profile badge state",
    )
    profile_anchor = '''            if (officialDeveloper) {
                StatusLine("Account", "HarleyTG Developer / Owner")
                StatusLine("Studio", "Harley's Studios")
            }
'''
    profile_badge = profile_anchor + '''            if (PuppyReleaseMilestones.RELEASE_1_0_BADGE_ID in gameState.profileBadgeIds) {
                StatusLine("Badge", PuppyReleaseMilestones.RELEASE_1_0_BADGE_NAME)
            }
'''
    source = replace_once(source, profile_anchor, profile_badge, "profile release badge")
    return source


def main(root: Path) -> None:
    files = {
        "PuppyClickerV6ViewModel.kt": patch_view_model,
        "PuppyNotificationCenter.kt": patch_notifications,
        "PuppyClickerV6Activity.kt": patch_activity,
        "PuppyRosterScreen.kt": patch_roster,
        "PuppyMainScreensRevamp.kt": patch_main_screens,
        "PuppySettingsUi.kt": patch_settings,
    }
    for name, patcher in files.items():
        path = root / PACKAGE / name
        if not path.is_file():
            raise RuntimeError(f"Android 1.0 patch missing generated source: {path}")
        source = path.read_text(encoding="utf-8")
        path.write_text(patcher(source), encoding="utf-8")
    print("Puppy Clicker Android 1.0 generated-source integration complete")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_android_1_0_completion.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
