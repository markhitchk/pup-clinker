package com.harleytg.puppyclicker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PrestigeSkill(
    val title: String,
    val emoji: String,
    val description: String,
    val maxLevel: Int
) {
    TAP_TRAINING(
        "Tap Training", "👆",
        "Each CLICK upgrade bought in the Shop gains +1 extra tap power per skill level.", 5
    ),
    AUTO_TRAINING(
        "Active Training", "⚡",
        "Each ACTIVE upgrade bought in the Shop gains +1 extra Treat in the 10-tap active bonus per skill level.", 5
    ),
    SMART_SHOPPER(
        "Smart Shopper", "🛍️",
        "Shop treat prices are 5% cheaper per skill level.", 5
    ),
    TICKET_SENSE(
        "Ticket Sense", "🎟️",
        "At levels 2 and 4, matching-ticket upgrade requirements drop by 1 (minimum 1).", 5
    )
}

data class V6GameState(
    val puppyName: String = "Buddy",
    val puppyStyle: String = "classic",
    val unlockedPuppies: Set<String> = DEFAULT_V6_PUPPIES,
    val accessory: String = "None",
    val ownedAccessories: Set<String> = setOf("None"),

    val treats: Long = 0,
    val lifetimeTreats: Long = 0,
    val bones: Long = 0,
    val pupCoins: Long = 0,
    val casinoChips: Long = 0,
    val clickPower: Int = 1,
    val activeBonus: Int = 0,
    // Retained for save/source compatibility only. Passive Treat production is disabled.
    val autoPerSecond: Int = 0,
    val legitimateTaps: Long = 0,
    val upgrades: Map<String, Int> = V5_UPGRADES.associate { it.id to 0 },
    val totalShopPurchases: Long = 0,

    val ticketInventory: Map<TicketRarity, Int> = TicketRarity.entries.associateWith { 0 },
    val ticketShopPurchases: Map<TicketRarity, Int> = TicketRarity.entries.associateWith { 0 },
    val lastTicketDrop: TicketRarity? = null,
    val ticketDropSerial: Long = 0,
    val totalTicketsFound: Long = 0,

    val happiness: Int = 100,
    val fullness: Int = 100,
    val energy: Int = 100,
    val cleanliness: Int = 100,
    val bond: Int = 10,
    val careActions: Long = 0,

    val totalTaps: Long = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val lastTapMs: Long = 0,

    val parkActive: Boolean = false,
    val parkReadyAtMs: Long = 0,

    val lastDailyClaimDay: Long = Long.MIN_VALUE,
    val dailyStreak: Int = 0,
    val dailyDay: Long = LocalDate.now().toEpochDay(),
    val dailyTapStart: Long = 0,
    val dailyCareStart: Long = 0,
    val dailyShopStart: Long = 0,
    val claimedDailyTasks: Set<String> = emptySet(),

    val redeemedCodeIds: Set<String> = emptySet(),
    val hapticsEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val compactNumbers: Boolean = true,

    val pupEyeStrikes: Int = 0,
    val cooldownUntilMs: Long = 0,

    val prestigeCount: Int = 0,
    val skillPoints: Int = 0,
    val prestigeSkills: Map<PrestigeSkill, Int> = PrestigeSkill.entries.associateWith { 0 },
    val totalPrestigePointsEarned: Long = 0,

    val afkLastClaimed: Long = 0
) {
    val level: Int
        get() = 1 + sqrt(lifetimeTreats.coerceAtLeast(0).toDouble() / 100.0).toInt()

    val careScore: Int
        get() = ((happiness + fullness + energy + cleanliness) / 4).coerceIn(0, 100)

    val mood: String
        get() = when {
            careScore >= 90 && bond >= 70 -> "Best friend"
            careScore >= 85 -> "Thrilled"
            careScore >= 65 -> "Happy"
            careScore >= 40 -> "Okay"
            careScore >= 20 -> "Needs care"
            else -> "Very tired"
        }

    val ticketsOwned: Int
        get() = ticketInventory.values.sum()

    val dailyTaps: Long
        get() = (totalTaps - dailyTapStart).coerceAtLeast(0)
    val dailyCareActions: Long
        get() = (careActions - dailyCareStart).coerceAtLeast(0)
    val dailyShopPurchases: Long
        get() = (totalShopPurchases - dailyShopStart).coerceAtLeast(0)

    val prestigePointsAvailable: Int
        get() = when {
            lifetimeTreats < PRESTIGE_MIN_TREATS -> 0
            else -> (1 + ((lifetimeTreats - PRESTIGE_MIN_TREATS) / PRESTIGE_BONUS_STEP).toInt())
                .coerceIn(1, MAX_POINTS_PER_PRESTIGE)
        }

    val canPrestige: Boolean
        get() = prestigePointsAvailable > 0
}

data class V6RedeemOutcome(val success: Boolean, val message: String)

fun v6SkillCost(state: V6GameState, skill: PrestigeSkill): Int {
    val level = state.prestigeSkills[skill] ?: 0
    return 1 + level / 2
}

fun v6UpgradeTreatCost(state: V6GameState, upgrade: V5Upgrade): Long =
    PuppyEconomyV7.upgradeTreatCost(state, upgrade)

fun v6UpgradeBoneCost(upgrade: V5Upgrade): Long =
    PuppyEconomyV7.upgradeBoneCost(upgrade)

fun v6UpgradeTicketCost(state: V6GameState, upgrade: V5Upgrade): Int {
    val owned = state.upgrades[upgrade.id] ?: 0
    val base = v5TicketCost(upgrade, owned)
    val sense = (state.prestigeSkills[PrestigeSkill.TICKET_SENSE] ?: 0).coerceIn(0, 5)
    val reduction = (if (sense >= 2) 1 else 0) + (if (sense >= 4) 1 else 0)
    return (base - reduction).coerceAtLeast(1)
}

class PuppyClickerV6ViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val recentTapTimes = ArrayDeque<Long>()
    private var suspicionHits = 0
    private var suspicionWindowStartedMs = 0L
    private var suppressPersistence = false

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<V6GameState> = _state.asStateFlow()

    private val initialCasinoInspection = PuppyCasinoPersistence.inspectActiveRound(prefs)
    private val _casinoRound = MutableStateFlow(initialCasinoInspection.round)
    internal val casinoRound: StateFlow<PuppyCasinoRound?> = _casinoRound.asStateFlow()

    private val _casinoRecoveryIssue = MutableStateFlow(initialCasinoInspection.issue)
    internal val casinoRecoveryIssue: StateFlow<String?> =
        _casinoRecoveryIssue.asStateFlow()

    private val _casinoRewardLedger = MutableStateFlow(PuppyCasinoRewardPersistence.load(prefs))
    internal val casinoRewardLedger: StateFlow<PuppyCasinoRewardLedger> =
        _casinoRewardLedger.asStateFlow()

    private val _lastCasinoTicketReward = MutableStateFlow<PuppyCasinoTicketReward?>(null)
    internal val lastCasinoTicketReward: StateFlow<PuppyCasinoTicketReward?> =
        _lastCasinoTicketReward.asStateFlow()

    private val _casinoPuppyRewardLedger =
        MutableStateFlow(PuppyCasinoPuppyRewardPersistence.load(prefs))
    internal val casinoPuppyRewardLedger: StateFlow<PuppyCasinoPuppyRewardLedger> =
        _casinoPuppyRewardLedger.asStateFlow()

    private val _lastCasinoPuppyReward =
        MutableStateFlow<PuppyCasinoPuppyReward?>(null)
    internal val lastCasinoPuppyReward: StateFlow<PuppyCasinoPuppyReward?> =
        _lastCasinoPuppyReward.asStateFlow()

    init {
        rollDailyDayIfNeeded()
        consumeClaimedAfkReward()
        viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1_000)
                seconds++
                consumeClaimedAfkReward()

                val now = System.currentTimeMillis()
                // No passive Treat generation. AUTO upgrades are active 10-tap bonuses.
                if (_state.value.combo > 0 && now - _state.value.lastTapMs > COMBO_TIMEOUT_MS) {
                    _state.update { it.copy(combo = 0) }
                }
                if (PuppyAppRuntime.isForeground && seconds % 120 == 0) decayNeeds()
                if (seconds % 5 == 0) saveState()
            }
        }
    }

    @Synchronized
    internal fun beginCasinoRound(
        game: PuppyCasinoGame,
        wagerTreats: Long,
        wagerPayload: String? = null
    ): PuppyCasinoTransactionResult {
        val current = _state.value
        val completed = PuppyCasinoPersistence.loadCompletedRoundIds(prefs)
        if (_casinoRecoveryIssue.value != null) {
            return PuppyCasinoTransactionResult(
                success = false,
                state = current,
                activeRound = _casinoRound.value,
                completedRoundIds = completed,
                failure = PuppyCasinoTransactionFailure.CORRUPT_SAVE
            )
        }
        val roundId = PuppyCasinoRoundIds.newId()
        val flagSnapshot = PuppyFeatureFlags.flags.value
        val result = PuppyCasinoTransactionEngine.acceptWager(
            before = current,
            activeRound = _casinoRound.value,
            completedRoundIds = completed,
            roundId = roundId,
            game = game,
            wagerTreats = wagerTreats,
            featureAvailable = PuppyCasinoFeaturePolicy.canStartNewRound(
                game = game,
                flags = flagSnapshot
            ),
            acceptedAtMs = System.currentTimeMillis(),
            wagerPayload = wagerPayload
        )
        return persistCasinoMutation(current, completed, result)
    }

    @Synchronized
    internal fun startSlotsSpin(wagerTreats: Long): PuppySlotsStartResult {
        if (!PuppySlotsEngine.isValidWager(wagerTreats)) {
            return PuppySlotsStartResult(
                success = false,
                failure = PuppySlotsStartFailure.INVALID_WAGER
            )
        }

        val accepted = beginCasinoRound(
            game = PuppyCasinoGame.SLOTS,
            wagerTreats = wagerTreats
        )
        if (!accepted.success) {
            return PuppySlotsStartResult(
                success = false,
                failure = PuppySlotsStartFailure.TRANSACTION_REJECTED,
                transactionFailure = accepted.failure
            )
        }

        val round = accepted.activeRound
            ?: return PuppySlotsStartResult(
                success = false,
                failure = PuppySlotsStartFailure.TRANSACTION_REJECTED
            )

        val outcome = runCatching {
            PuppySlotsEngine.randomSpin(wagerTreats)
        }.getOrElse {
            refundCasinoRound(round.roundId)
            return PuppySlotsStartResult(
                success = false,
                failure = PuppySlotsStartFailure.OUTCOME_GENERATION_FAILED
            )
        }

        val committed = commitCasinoOutcome(
            roundId = round.roundId,
            outcomePayload = PuppySlotsOutcomeCodec.encode(outcome),
            payoutTreats = outcome.payoutTreats
        )
        if (!committed.success) {
            return PuppySlotsStartResult(
                success = false,
                roundId = round.roundId,
                outcome = outcome,
                failure = PuppySlotsStartFailure.OUTCOME_COMMIT_FAILED,
                transactionFailure = committed.failure
            )
        }

        return PuppySlotsStartResult(
            success = true,
            roundId = round.roundId,
            outcome = outcome
        )
    }

    @Synchronized
    internal fun startRouletteSpin(
        bet: PuppyRouletteBet,
        wagerTreats: Long
    ): PuppyRouletteStartResult {
        if (!PuppyRouletteEngine.isValidWager(wagerTreats)) {
            return PuppyRouletteStartResult(
                success = false,
                failure = PuppyRouletteStartFailure.INVALID_WAGER
            )
        }
        if (!PuppyRouletteEngine.isValidBet(bet)) {
            return PuppyRouletteStartResult(
                success = false,
                failure = PuppyRouletteStartFailure.INVALID_BET
            )
        }

        val wagerPayload = PuppyRouletteBetCodec.encode(bet)
        val accepted = beginCasinoRound(
            game = PuppyCasinoGame.ROULETTE,
            wagerTreats = wagerTreats,
            wagerPayload = wagerPayload
        )
        if (!accepted.success) {
            return PuppyRouletteStartResult(
                success = false,
                failure = PuppyRouletteStartFailure.TRANSACTION_REJECTED,
                transactionFailure = accepted.failure
            )
        }

        val round = accepted.activeRound
            ?: return PuppyRouletteStartResult(
                success = false,
                failure = PuppyRouletteStartFailure.TRANSACTION_REJECTED
            )

        val outcome = runCatching {
            PuppyRouletteEngine.randomSpin(
                wagerTreats = wagerTreats,
                bet = bet
            )
        }.getOrElse {
            refundCasinoRound(round.roundId)
            return PuppyRouletteStartResult(
                success = false,
                failure = PuppyRouletteStartFailure.OUTCOME_GENERATION_FAILED
            )
        }

        val committed = commitCasinoOutcome(
            roundId = round.roundId,
            outcomePayload = PuppyRouletteOutcomeCodec.encode(outcome),
            payoutTreats = outcome.payoutTreats
        )
        if (!committed.success) {
            return PuppyRouletteStartResult(
                success = false,
                roundId = round.roundId,
                outcome = outcome,
                failure = PuppyRouletteStartFailure.OUTCOME_COMMIT_FAILED,
                transactionFailure = committed.failure
            )
        }

        return PuppyRouletteStartResult(
            success = true,
            roundId = round.roundId,
            outcome = outcome
        )
    }

    @Synchronized
    internal fun startPlinkoDrop(wagerTreats: Long): PuppyPlinkoStartResult {
        if (!PuppyPlinkoEngine.isValidWager(wagerTreats)) {
            return PuppyPlinkoStartResult(
                success = false,
                failure = PuppyPlinkoStartFailure.INVALID_WAGER
            )
        }

        val accepted = beginCasinoRound(
            game = PuppyCasinoGame.PLINKO,
            wagerTreats = wagerTreats
        )
        if (!accepted.success) {
            return PuppyPlinkoStartResult(
                success = false,
                failure = PuppyPlinkoStartFailure.TRANSACTION_REJECTED,
                transactionFailure = accepted.failure
            )
        }
        val round = accepted.activeRound
            ?: return PuppyPlinkoStartResult(
                success = false,
                failure = PuppyPlinkoStartFailure.TRANSACTION_REJECTED
            )

        val outcome = runCatching {
            PuppyPlinkoEngine.randomDrop(wagerTreats)
        }.getOrElse {
            refundCasinoRound(round.roundId)
            return PuppyPlinkoStartResult(
                success = false,
                failure = PuppyPlinkoStartFailure.OUTCOME_GENERATION_FAILED
            )
        }

        val committed = commitCasinoOutcome(
            roundId = round.roundId,
            outcomePayload = PuppyPlinkoOutcomeCodec.encode(outcome),
            payoutTreats = outcome.payoutTreats
        )
        if (!committed.success) {
            return PuppyPlinkoStartResult(
                success = false,
                roundId = round.roundId,
                outcome = outcome,
                failure = PuppyPlinkoStartFailure.OUTCOME_COMMIT_FAILED,
                transactionFailure = committed.failure
            )
        }
        return PuppyPlinkoStartResult(
            success = true,
            roundId = round.roundId,
            outcome = outcome
        )
    }

    @Synchronized
    internal fun startScratcher(wagerTreats: Long): PuppyScratcherStartResult {
        if (!PuppyScratchersEngine.isValidWager(wagerTreats)) {
            return PuppyScratcherStartResult(
                success = false,
                failure = PuppyScratcherStartFailure.INVALID_WAGER
            )
        }

        val accepted = beginCasinoRound(
            game = PuppyCasinoGame.SCRATCHERS,
            wagerTreats = wagerTreats
        )
        if (!accepted.success) {
            return PuppyScratcherStartResult(
                success = false,
                failure = PuppyScratcherStartFailure.TRANSACTION_REJECTED,
                transactionFailure = accepted.failure
            )
        }
        val round = accepted.activeRound
            ?: return PuppyScratcherStartResult(
                success = false,
                failure = PuppyScratcherStartFailure.TRANSACTION_REJECTED
            )

        val outcome = runCatching {
            PuppyScratchersEngine.randomCard(wagerTreats)
        }.getOrElse {
            refundCasinoRound(round.roundId)
            return PuppyScratcherStartResult(
                success = false,
                failure = PuppyScratcherStartFailure.OUTCOME_GENERATION_FAILED
            )
        }

        val committed = commitCasinoOutcome(
            roundId = round.roundId,
            outcomePayload = PuppyScratcherOutcomeCodec.encode(outcome),
            payoutTreats = outcome.payoutTreats
        )
        if (!committed.success) {
            return PuppyScratcherStartResult(
                success = false,
                roundId = round.roundId,
                outcome = outcome,
                failure = PuppyScratcherStartFailure.OUTCOME_COMMIT_FAILED,
                transactionFailure = committed.failure
            )
        }
        return PuppyScratcherStartResult(
            success = true,
            roundId = round.roundId,
            outcome = outcome
        )
    }

    @Synchronized
    internal fun startLuckyPupWheel(wagerTreats: Long): LuckyPupWheelStartResult {
        if (!PuppyLuckyWheelEngine.isValidWager(wagerTreats)) {
            return LuckyPupWheelStartResult(
                success = false,
                failure = LuckyPupWheelStartFailure.INVALID_WAGER
            )
        }

        val accepted = beginCasinoRound(
            game = PuppyCasinoGame.LUCKY_WHEEL,
            wagerTreats = wagerTreats
        )
        if (!accepted.success) {
            return LuckyPupWheelStartResult(
                success = false,
                failure = LuckyPupWheelStartFailure.TRANSACTION_REJECTED,
                transactionFailure = accepted.failure
            )
        }
        val round = accepted.activeRound
            ?: return LuckyPupWheelStartResult(
                success = false,
                failure = LuckyPupWheelStartFailure.TRANSACTION_REJECTED
            )

        val today = LocalDate.now().toEpochDay()
        val puppyLedger = _casinoPuppyRewardLedger.value
        val puppyUnlockAllowed =
            puppyLedger.dailyEpochDay != today ||
                puppyLedger.dailyPuppyUnlocks < PuppyCasinoPuppyRewardEngine.MAX_DAILY_CASINO_PUPPIES
        val availablePuppies = PuppyCasinoPuppyRewardEngine.eligibleStyleIds
            .filterNot { it in _state.value.unlockedPuppies }

        val outcome = runCatching {
            PuppyLuckyWheelEngine.randomSpin(
                wagerTreats = wagerTreats,
                availablePuppyStyleIds = availablePuppies,
                puppyUnlockAllowed = puppyUnlockAllowed
            )
        }.getOrElse {
            refundCasinoRound(round.roundId)
            return LuckyPupWheelStartResult(
                success = false,
                failure = LuckyPupWheelStartFailure.OUTCOME_GENERATION_FAILED
            )
        }

        val committed = commitCasinoOutcome(
            roundId = round.roundId,
            outcomePayload = LuckyPupWheelOutcomeCodec.encode(outcome),
            payoutTreats = outcome.payoutTreats
        )
        if (!committed.success) {
            return LuckyPupWheelStartResult(
                success = false,
                roundId = round.roundId,
                outcome = outcome,
                failure = LuckyPupWheelStartFailure.OUTCOME_COMMIT_FAILED,
                transactionFailure = committed.failure
            )
        }
        return LuckyPupWheelStartResult(
            success = true,
            roundId = round.roundId,
            outcome = outcome
        )
    }

    @Synchronized
    internal fun startBlackjackRound(wagerTreats: Long): PuppyBlackjackCommandResult {
        if (!PuppyBlackjackEngine.isValidInitialWager(wagerTreats)) {
            return PuppyBlackjackCommandResult(
                success = false,
                failure = PuppyBlackjackCommandFailure.INVALID_WAGER
            )
        }

        val blackjackState = runCatching {
            PuppyBlackjackEngine.newRound(wagerTreats)
        }.getOrElse {
            return PuppyBlackjackCommandResult(
                success = false,
                failure = PuppyBlackjackCommandFailure.INVALID_SAVED_STATE
            )
        }

        val accepted = beginCasinoRound(
            game = PuppyCasinoGame.BLACKJACK,
            wagerTreats = wagerTreats,
            wagerPayload = PuppyBlackjackStateCodec.encode(blackjackState)
        )
        if (!accepted.success) {
            return PuppyBlackjackCommandResult(
                success = false,
                failure = PuppyBlackjackCommandFailure.TRANSACTION_REJECTED,
                transactionFailure = accepted.failure
            )
        }

        val roundId = accepted.activeRound?.roundId
            ?: return PuppyBlackjackCommandResult(
                success = false,
                failure = PuppyBlackjackCommandFailure.TRANSACTION_REJECTED
            )

        return if (blackjackState.complete) {
            commitCompletedBlackjack(roundId, blackjackState)
        } else {
            PuppyBlackjackCommandResult(
                success = true,
                roundId = roundId,
                state = blackjackState
            )
        }
    }

    @Synchronized
    internal fun blackjackHit(): PuppyBlackjackCommandResult =
        runBlackjackAction(PuppyBlackjackEngine::hit)

    @Synchronized
    internal fun blackjackStand(): PuppyBlackjackCommandResult =
        runBlackjackAction(PuppyBlackjackEngine::stand)

    @Synchronized
    internal fun blackjackDouble(): PuppyBlackjackCommandResult =
        runBlackjackAction(PuppyBlackjackEngine::doubleDown)

    @Synchronized
    internal fun blackjackSplit(): PuppyBlackjackCommandResult =
        runBlackjackAction(PuppyBlackjackEngine::split)

    @Synchronized
    internal fun finalizePendingBlackjackRound(): PuppyBlackjackCommandResult {
        val round = _casinoRound.value
            ?.takeIf {
                it.game == PuppyCasinoGame.BLACKJACK &&
                    it.state == PuppyCasinoRoundState.WAGER_ACCEPTED
            }
            ?: return PuppyBlackjackCommandResult(
                success = false,
                failure = PuppyBlackjackCommandFailure.INVALID_SAVED_STATE
            )

        val blackjackState = PuppyBlackjackStateCodec.decodeAndValidate(round.wagerPayload)
            ?: return PuppyBlackjackCommandResult(
                success = false,
                roundId = round.roundId,
                failure = PuppyBlackjackCommandFailure.INVALID_SAVED_STATE
            )

        if (!blackjackState.complete) {
            return PuppyBlackjackCommandResult(
                success = false,
                roundId = round.roundId,
                state = blackjackState,
                failure = PuppyBlackjackCommandFailure.ACTION_NOT_ALLOWED
            )
        }

        return commitCompletedBlackjack(round.roundId, blackjackState)
    }

    private fun runBlackjackAction(
        action: (PuppyBlackjackState) -> PuppyBlackjackActionResult
    ): PuppyBlackjackCommandResult {
        val round = _casinoRound.value
            ?.takeIf {
                it.game == PuppyCasinoGame.BLACKJACK &&
                    it.state == PuppyCasinoRoundState.WAGER_ACCEPTED
            }
            ?: return PuppyBlackjackCommandResult(
                success = false,
                failure = PuppyBlackjackCommandFailure.INVALID_SAVED_STATE
            )

        val blackjackState = PuppyBlackjackStateCodec.decodeAndValidate(round.wagerPayload)
            ?: return PuppyBlackjackCommandResult(
                success = false,
                roundId = round.roundId,
                failure = PuppyBlackjackCommandFailure.INVALID_SAVED_STATE
            )

        val actionResult = action(blackjackState)
        if (!actionResult.success) {
            return PuppyBlackjackCommandResult(
                success = false,
                roundId = round.roundId,
                state = blackjackState,
                failure = PuppyBlackjackCommandFailure.ACTION_NOT_ALLOWED,
                actionError = actionResult.error
            )
        }

        val current = _state.value
        val completed = PuppyCasinoPersistence.loadCompletedRoundIds(prefs)
        val persisted = PuppyCasinoTransactionEngine.updateAcceptedRound(
            before = current,
            activeRound = round,
            completedRoundIds = completed,
            roundId = round.roundId,
            wagerPayload = PuppyBlackjackStateCodec.encode(actionResult.state),
            additionalWagerTreats = actionResult.additionalWagerTreats
        )
        val saved = persistCasinoMutation(current, completed, persisted)
        if (!saved.success) {
            return PuppyBlackjackCommandResult(
                success = false,
                roundId = round.roundId,
                state = blackjackState,
                failure = PuppyBlackjackCommandFailure.TRANSACTION_REJECTED,
                transactionFailure = saved.failure
            )
        }

        return if (actionResult.state.complete) {
            commitCompletedBlackjack(round.roundId, actionResult.state)
        } else {
            PuppyBlackjackCommandResult(
                success = true,
                roundId = round.roundId,
                state = actionResult.state
            )
        }
    }

    private fun commitCompletedBlackjack(
        roundId: String,
        blackjackState: PuppyBlackjackState
    ): PuppyBlackjackCommandResult {
        val round = _casinoRound.value
            ?.takeIf { it.roundId == roundId && it.game == PuppyCasinoGame.BLACKJACK }
            ?: return PuppyBlackjackCommandResult(
                success = false,
                roundId = roundId,
                state = blackjackState,
                failure = PuppyBlackjackCommandFailure.INVALID_SAVED_STATE
            )

        if (
            !blackjackState.complete ||
            PuppyBlackjackEngine.totalWager(blackjackState) != round.wagerTreats
        ) {
            return PuppyBlackjackCommandResult(
                success = false,
                roundId = roundId,
                state = blackjackState,
                failure = PuppyBlackjackCommandFailure.INVALID_SAVED_STATE
            )
        }

        val outcome = runCatching {
            PuppyBlackjackEngine.outcome(blackjackState)
        }.getOrElse {
            return PuppyBlackjackCommandResult(
                success = false,
                roundId = roundId,
                state = blackjackState,
                failure = PuppyBlackjackCommandFailure.INVALID_SAVED_STATE
            )
        }

        val committed = commitCasinoOutcome(
            roundId = roundId,
            outcomePayload = PuppyBlackjackOutcomeCodec.encode(outcome),
            payoutTreats = outcome.totalPayoutTreats
        )
        if (!committed.success) {
            return PuppyBlackjackCommandResult(
                success = false,
                roundId = roundId,
                state = blackjackState,
                outcome = outcome,
                failure = PuppyBlackjackCommandFailure.OUTCOME_COMMIT_FAILED,
                transactionFailure = committed.failure
            )
        }

        return PuppyBlackjackCommandResult(
            success = true,
            roundId = roundId,
            state = blackjackState,
            outcome = outcome
        )
    }

    @Synchronized
    internal fun commitCasinoOutcome(
        roundId: String,
        outcomePayload: String,
        payoutTreats: Long
    ): PuppyCasinoTransactionResult {
        val current = _state.value
        val completed = PuppyCasinoPersistence.loadCompletedRoundIds(prefs)
        val result = PuppyCasinoTransactionEngine.commitOutcome(
            before = current,
            activeRound = _casinoRound.value,
            completedRoundIds = completed,
            roundId = roundId,
            outcomePayload = outcomePayload,
            payoutTreats = payoutTreats,
            committedAtMs = System.currentTimeMillis()
        )
        return persistCasinoMutation(current, completed, result)
    }

    @Synchronized
    internal fun settleCasinoRound(roundId: String): PuppyCasinoTransactionResult {
        val current = _state.value
        val completed = PuppyCasinoPersistence.loadCompletedRoundIds(prefs)
        val active = _casinoRound.value

        if (active?.roundId == roundId && active.state == PuppyCasinoRoundState.OUTCOME_COMMITTED) {
            val validOutcome = when (active.game) {
                PuppyCasinoGame.SLOTS -> PuppySlotsOutcomeCodec.decodeAndValidate(
                    raw = active.outcomePayload,
                    wagerTreats = active.wagerTreats
                )?.takeIf { it.payoutTreats == active.payoutTreats } != null

                PuppyCasinoGame.ROULETTE -> {
                    val bet = PuppyRouletteBetCodec.decodeAndValidate(active.wagerPayload)
                    bet != null &&
                        PuppyRouletteOutcomeCodec.decodeAndValidate(
                            raw = active.outcomePayload,
                            wagerTreats = active.wagerTreats,
                            expectedBet = bet
                        )?.takeIf { it.payoutTreats == active.payoutTreats } != null
                }

                PuppyCasinoGame.BLACKJACK -> {
                    val blackjackState = PuppyBlackjackStateCodec.decodeAndValidate(active.wagerPayload)
                    blackjackState != null &&
                        blackjackState.complete &&
                        PuppyBlackjackEngine.totalWager(blackjackState) == active.wagerTreats &&
                        PuppyBlackjackOutcomeCodec.decodeAndValidate(
                            raw = active.outcomePayload,
                            state = blackjackState
                        )?.takeIf { it.totalPayoutTreats == active.payoutTreats } != null
                }

                PuppyCasinoGame.PLINKO ->
                    PuppyPlinkoOutcomeCodec.decodeAndValidate(
                        raw = active.outcomePayload,
                        wagerTreats = active.wagerTreats
                    )?.takeIf { it.payoutTreats == active.payoutTreats } != null

                PuppyCasinoGame.SCRATCHERS ->
                    PuppyScratcherOutcomeCodec.decodeAndValidate(
                        raw = active.outcomePayload,
                        wagerTreats = active.wagerTreats
                    )?.takeIf { it.payoutTreats == active.payoutTreats } != null

                PuppyCasinoGame.LUCKY_WHEEL ->
                    LuckyPupWheelOutcomeCodec.decodeAndValidate(
                        raw = active.outcomePayload,
                        wagerTreats = active.wagerTreats
                    )?.takeIf { it.payoutTreats == active.payoutTreats } != null
            }
            if (!validOutcome) {
                return PuppyCasinoTransactionResult(
                    success = false,
                    state = current,
                    activeRound = active,
                    completedRoundIds = completed,
                    failure = PuppyCasinoTransactionFailure.INVALID_OUTCOME
                )
            }
        }

        val result = PuppyCasinoTransactionEngine.settle(
            before = current,
            activeRound = active,
            completedRoundIds = completed,
            roundId = roundId
        )
        if (!result.success || active == null) {
            return persistCasinoMutation(current, completed, result)
        }

        val rewardApplication = PuppyCasinoRewardEngine.apply(
            before = result.state,
            settledRound = active,
            ledger = _casinoRewardLedger.value
        )
        val wheelOutcome = if (active.game == PuppyCasinoGame.LUCKY_WHEEL) {
            LuckyPupWheelOutcomeCodec.decodeAndValidate(
                raw = active.outcomePayload,
                wagerTreats = active.wagerTreats
            )
        } else {
            null
        }
        val puppyApplication =
            if (
                wheelOutcome?.prize == LuckyPupWheelPrize.PUPPY_UNLOCK &&
                wheelOutcome.puppyStyleId != null
            ) {
                PuppyCasinoPuppyRewardEngine.applyGuaranteedUnlock(
                    before = rewardApplication.state,
                    settledRound = active,
                    styleId = wheelOutcome.puppyStyleId,
                    ledger = _casinoPuppyRewardLedger.value
                )
            } else {
                PuppyCasinoPuppyRewardEngine.apply(
                    before = rewardApplication.state,
                    settledRound = active,
                    ledger = _casinoPuppyRewardLedger.value
                )
            }
        val rewardedResult = result.copy(state = puppyApplication.state)
        val persisted = persistCasinoMutation(
            before = current,
            completedBefore = completed,
            result = rewardedResult,
            rewardLedger = rewardApplication.ledger,
            puppyRewardLedger = puppyApplication.ledger
        )
        if (persisted.success) {
            _lastCasinoTicketReward.value = rewardApplication.reward
            _lastCasinoPuppyReward.value = puppyApplication.reward
        }
        return persisted
    }

    @Synchronized
    internal fun refundCasinoRound(roundId: String): PuppyCasinoTransactionResult {
        val current = _state.value
        val completed = PuppyCasinoPersistence.loadCompletedRoundIds(prefs)
        val active = _casinoRound.value
        if (
            active?.roundId == roundId &&
            active.game == PuppyCasinoGame.BLACKJACK &&
            PuppyBlackjackStateCodec.decodeAndValidate(active.wagerPayload) != null
        ) {
            return PuppyCasinoTransactionResult(
                success = false,
                state = current,
                activeRound = active,
                completedRoundIds = completed,
                failure = PuppyCasinoTransactionFailure.INVALID_ROUND_STATE
            )
        }
        val result = PuppyCasinoTransactionEngine.refund(
            before = current,
            activeRound = active,
            completedRoundIds = completed,
            roundId = roundId
        )
        return persistCasinoMutation(current, completed, result)
    }

    fun tapPuppy() {
        val now = System.currentTimeMillis()
        val current = _state.value
        val enforcePupEyeFairPlay =
            PuppyPlayerIdentity.shouldEnforcePupEyeFairPlay(getApplication<Application>())

        if (enforcePupEyeFairPlay && now < current.cooldownUntilMs) return

        if (enforcePupEyeFairPlay) {
            recentTapTimes.addLast(now)
            while (recentTapTimes.isNotEmpty() && recentTapTimes.first < now - FAIR_PLAY_HISTORY_MS) {
                recentTapTimes.removeFirst()
            }
        } else if (recentTapTimes.isNotEmpty() || suspicionHits != 0 || suspicionWindowStartedMs != 0L) {
            // Developer exemption applies only to behavioral fair-play detection.
            // Reset transient detector state so an old player-mode sample cannot leak
            // into a verified developer session.
            recentTapTimes.clear()
            suspicionHits = 0
            suspicionWindowStartedMs = 0L
        }

        val suspiciousThisTap = enforcePupEyeFairPlay && looksAutomated(now)
        if (suspiciousThisTap) {
            if (suspicionWindowStartedMs == 0L || now - suspicionWindowStartedMs > SUSPICION_CONFIRM_WINDOW_MS) {
                suspicionWindowStartedMs = now
                suspicionHits = 1
            } else {
                suspicionHits++
            }

            if (suspicionHits >= REQUIRED_SUSPICION_HITS) {
                recentTapTimes.clear()
                suspicionHits = 0
                suspicionWindowStartedMs = 0L
                val strike = current.pupEyeStrikes + 1
                val cooldown = (BASE_FAIR_PLAY_COOLDOWN_MS + (strike - 1) * 2_000L)
                    .coerceAtMost(MAX_FAIR_PLAY_COOLDOWN_MS)
                _state.value = current.copy(
                    pupEyeStrikes = strike,
                    cooldownUntilMs = now + cooldown,
                    combo = 0
                )
                saveState()
                return
            }
        } else if (suspicionWindowStartedMs != 0L && now - suspicionWindowStartedMs > SUSPICION_CONFIRM_WINDOW_MS) {
            suspicionHits = 0
            suspicionWindowStartedMs = 0L
        }

        val combo = if (now - current.lastTapMs <= COMBO_CHAIN_MS) {
            (current.combo + 1).coerceAtMost(50)
        } else 1

        val nextTaps = safeAdd(current.totalTaps, 1)
        val legitimateTap = !suspiciousThisTap
        val nextLegitimateTaps =
            if (legitimateTap) safeAdd(current.legitimateTaps, 1L) else current.legitimateTaps
        val activeBonusPayout =
            if (
                legitimateTap &&
                nextLegitimateTaps % PuppyEconomyV7.ACTIVE_BONUS_TAP_INTERVAL == 0L
            ) {
                current.activeBonus.toLong()
            } else {
                0L
            }
        val tapPayout = safeAdd(current.clickPower.toLong(), activeBonusPayout)
        val boneDrop =
            if (
                legitimateTap &&
                nextLegitimateTaps % PuppyEconomyV7.BONE_TAP_INTERVAL == 0L
            ) PuppyEconomyV7.CARE_ACTION_BONES else 0L
        val pupCoinDrop =
            if (
                legitimateTap &&
                nextLegitimateTaps % PuppyEconomyV7.PUP_COIN_TAP_INTERVAL == 0L
            ) 1L else 0L

        // Upgrade Tickets are rare secondary drops. Suspicious/machine-like taps never roll.
        val ticketDrop =
            if (
                legitimateTap &&
                PuppyEconomyV7.shouldDropTicket(
                    Random.nextInt(PuppyEconomyV7.TICKET_DROP_DENOMINATOR)
                )
            ) {
                rollTicketRarityV6()
            } else {
                null
            }
        val inventory = if (ticketDrop == null) current.ticketInventory else {
            current.ticketInventory.toMutableMap().apply {
                this[ticketDrop] = ((this[ticketDrop] ?: 0) + 1).coerceAtMost(MAX_TICKETS_PER_RARITY)
            }
        }

        _state.value = current.copy(
            treats = safeAdd(current.treats, tapPayout),
            lifetimeTreats = safeAdd(current.lifetimeTreats, tapPayout),
            bones = safeAdd(current.bones, boneDrop),
            pupCoins = safeAdd(current.pupCoins, pupCoinDrop),
            totalTaps = nextTaps,
            legitimateTaps = nextLegitimateTaps,
            combo = combo,
            bestCombo = maxOf(current.bestCombo, combo),
            lastTapMs = now,
            energy = if (nextTaps % 12L == 0L) (current.energy - 1).coerceAtLeast(0) else current.energy,
            happiness = if (nextTaps % 15L == 0L) (current.happiness + 1).coerceAtMost(100) else current.happiness,
            ticketInventory = inventory,
            lastTicketDrop = ticketDrop ?: current.lastTicketDrop,
            ticketDropSerial = if (ticketDrop != null) current.ticketDropSerial + 1 else current.ticketDropSerial,
            totalTicketsFound = if (ticketDrop != null) safeAdd(current.totalTicketsFound, 1) else current.totalTicketsFound
        )
        if (ticketDrop != null || boneDrop > 0L || pupCoinDrop > 0L) saveState()
    }

    fun buyCookieUpgrade(upgrade: V5Upgrade) {
        if (upgrade.type != V5UpgradeType.COOKIE) return
        val current = _state.value
        val owned = current.upgrades[upgrade.id] ?: 0
        val cost = v6UpgradeTreatCost(current, upgrade)
        val boneCost = v6UpgradeBoneCost(upgrade)
        if (current.treats < cost || current.bones < boneCost) return
        applyUpgradePurchase(
            current = current,
            upgrade = upgrade,
            owned = owned,
            nextTreats = current.treats - cost,
            nextBones = current.bones - boneCost,
            inventory = current.ticketInventory
        )
    }

    fun buyTicketUpgrade(upgrade: V5Upgrade) {
        if (upgrade.type != V5UpgradeType.TICKET || upgrade.rarity == null) return
        val current = _state.value
        val owned = current.upgrades[upgrade.id] ?: 0
        val ticketCost = v6UpgradeTicketCost(current, upgrade)
        val ownedTickets = current.ticketInventory[upgrade.rarity] ?: 0
        val treatFee = v6UpgradeTreatCost(current, upgrade)
        val boneCost = v6UpgradeBoneCost(upgrade)
        if (
            ownedTickets < ticketCost ||
            current.treats < treatFee ||
            current.bones < boneCost
        ) return

        val inventory = current.ticketInventory.toMutableMap().apply {
            this[upgrade.rarity] = (ownedTickets - ticketCost).coerceAtLeast(0)
        }
        applyUpgradePurchase(
            current = current,
            upgrade = upgrade,
            owned = owned,
            nextTreats = current.treats - treatFee,
            nextBones = current.bones - boneCost,
            inventory = inventory
        )
    }

    private fun applyUpgradePurchase(
        current: V6GameState,
        upgrade: V5Upgrade,
        owned: Int,
        nextTreats: Long,
        nextBones: Long,
        inventory: Map<TicketRarity, Int>
    ) {
        val nextUpgrades = current.upgrades.toMutableMap().apply { this[upgrade.id] = owned + 1 }
        val tapSkill = current.prestigeSkills[PrestigeSkill.TAP_TRAINING] ?: 0
        val autoSkill = current.prestigeSkills[PrestigeSkill.AUTO_TRAINING] ?: 0
        val actualAmount = when (upgrade.effect) {
            V5UpgradeEffect.CLICK -> upgrade.amount + tapSkill
            V5UpgradeEffect.AUTO -> upgrade.amount + autoSkill
        }
        val nextActiveBonus = PuppyEconomyV7.activeBonus(nextUpgrades, autoSkill)

        _state.value = current.copy(
            treats = nextTreats,
            bones = nextBones,
            upgrades = nextUpgrades,
            ticketInventory = inventory,
            clickPower = current.clickPower + if (upgrade.effect == V5UpgradeEffect.CLICK) actualAmount else 0,
            activeBonus = nextActiveBonus,
            autoPerSecond = 0,
            totalShopPurchases = safeAdd(current.totalShopPurchases, 1),
            happiness = (current.happiness + 2).coerceAtMost(100)
        )
        saveState()
    }

    fun feedPuppy() {
        rollDailyDayIfNeeded()
        val s = _state.value
        if (s.treats < FEED_COST || s.fullness >= 100) return
        _state.value = s.copy(
            treats = s.treats - FEED_COST,
            fullness = (s.fullness + 25).coerceAtMost(100),
            happiness = (s.happiness + 5).coerceAtMost(100),
            bond = (s.bond + 1).coerceAtMost(100),
            cleanliness = (s.cleanliness - 1).coerceAtLeast(0),
            bones = safeAdd(s.bones, PuppyEconomyV7.CARE_ACTION_BONES),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun playWithPuppy() {
        rollDailyDayIfNeeded()
        val s = _state.value
        if (s.energy < 12 || (s.happiness >= 100 && s.bond >= 100)) return
        _state.value = s.copy(
            happiness = (s.happiness + 20).coerceAtMost(100),
            fullness = (s.fullness - 4).coerceAtLeast(0),
            energy = (s.energy - 12).coerceAtLeast(0),
            cleanliness = (s.cleanliness - 3).coerceAtLeast(0),
            bond = (s.bond + 3).coerceAtMost(100),
            bones = safeAdd(s.bones, PuppyEconomyV7.CARE_ACTION_BONES),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun restPuppy() {
        rollDailyDayIfNeeded()
        val s = _state.value
        if (s.energy >= 100) return
        _state.value = s.copy(
            energy = (s.energy + 30).coerceAtMost(100),
            happiness = (s.happiness + 3).coerceAtMost(100),
            bones = safeAdd(s.bones, PuppyEconomyV7.CARE_ACTION_BONES),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun groomPuppy() {
        rollDailyDayIfNeeded()
        val s = _state.value
        if (s.cleanliness >= 100) return
        _state.value = s.copy(
            cleanliness = (s.cleanliness + 35).coerceAtMost(100),
            happiness = (s.happiness + 4).coerceAtMost(100),
            bond = (s.bond + 2).coerceAtMost(100),
            bones = safeAdd(s.bones, PuppyEconomyV7.CARE_ACTION_BONES),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun cuddlePuppy() {
        rollDailyDayIfNeeded()
        val s = _state.value
        if (s.happiness >= 100 && s.bond >= 100) return
        _state.value = s.copy(
            happiness = (s.happiness + 8).coerceAtMost(100),
            bond = (s.bond + 2).coerceAtMost(100),
            bones = safeAdd(s.bones, PuppyEconomyV7.CARE_ACTION_BONES),
            careActions = safeAdd(s.careActions, 1)
        )
        saveState()
    }

    fun startParkAdventure() {
        val s = _state.value
        if (s.parkActive || s.energy < 20) return
        val readyAt = System.currentTimeMillis() + PARK_ADVENTURE_MS
        _state.value = s.copy(
            parkActive = true,
            parkReadyAtMs = readyAt,
            energy = (s.energy - 20).coerceAtLeast(0)
        )
        saveState()
        PuppyNotificationCenter.scheduleParkReady(getApplication(), readyAt)
    }

    fun claimParkAdventure() {
        val s = _state.value
        if (!s.parkActive || System.currentTimeMillis() < s.parkReadyAtMs) return
        val reward = 250L + s.level * 50L
        _state.value = s.copy(
            treats = safeAdd(s.treats, reward),
            lifetimeTreats = safeAdd(s.lifetimeTreats, reward),
            happiness = (s.happiness + 12).coerceAtMost(100),
            fullness = (s.fullness - 5).coerceAtLeast(0),
            cleanliness = (s.cleanliness - 5).coerceAtLeast(0),
            bond = (s.bond + 3).coerceAtMost(100),
            parkActive = false,
            parkReadyAtMs = 0
        )
        saveState()
        PuppyNotificationCenter.cancelParkReady(getApplication())
    }

    fun claimDailyReward() {
        rollDailyDayIfNeeded()
        val s = _state.value
        val today = LocalDate.now().toEpochDay()
        if (s.lastDailyClaimDay == today) return
        val streak = if (s.lastDailyClaimDay == today - 1) (s.dailyStreak + 1).coerceAtMost(365) else 1
        val reward = 150L + streak * 25L + s.level * 10L
        _state.value = s.copy(
            treats = safeAdd(s.treats, reward),
            lifetimeTreats = safeAdd(s.lifetimeTreats, reward),
            bones = safeAdd(s.bones, PuppyEconomyV7.DAILY_BONES),
            pupCoins = safeAdd(s.pupCoins, PuppyEconomyV7.DAILY_PUP_COINS),
            lastDailyClaimDay = today,
            dailyStreak = streak,
            happiness = (s.happiness + 8).coerceAtMost(100),
            bond = (s.bond + 1).coerceAtMost(100)
        )
        saveState()
        PuppyNotificationCenter.cancelDailyReward(getApplication())
    }

    fun claimDailyTask(id: String) {
        rollDailyDayIfNeeded()
        val s = _state.value
        if (id in s.claimedDailyTasks) return

        // Reward values are resolved from the validated repository schedule inside
        // the ViewModel rather than trusted from UI arguments.
        val goal = PuppyMonthlyRewards.goalToday(id) ?: return
        if (!goal.isComplete(s)) return

        _state.value = s.copy(
            treats = safeAdd(s.treats, goal.rewardTreats),
            lifetimeTreats = safeAdd(s.lifetimeTreats, goal.rewardTreats),
            bones = safeAdd(s.bones, PuppyEconomyV7.DAILY_TASK_BONES),
            pupCoins = safeAdd(s.pupCoins, PuppyEconomyV7.DAILY_TASK_PUP_COINS),
            claimedDailyTasks = s.claimedDailyTasks + id
        )
        saveState()
    }

    fun redeemCode(rawCode: String): V6RedeemOutcome {
        val reward = LocalRedeemCodes.find(rawCode)
            ?: return V6RedeemOutcome(false, "That Puppy Code is invalid or unavailable in this version.")
        val s = _state.value
        if (reward.id in s.redeemedCodeIds) {
            return V6RedeemOutcome(false, "That Puppy Code was already redeemed on this device.")
        }

        val puppy = reward.puppyId?.let { id -> V6_PUPPY_STYLES.firstOrNull { it.id == id } }
        _state.value = s.copy(
            treats = safeAdd(s.treats, reward.treats),
            lifetimeTreats = safeAdd(s.lifetimeTreats, reward.treats),
            unlockedPuppies = if (puppy != null) s.unlockedPuppies + puppy.id else s.unlockedPuppies,
            puppyStyle = puppy?.id ?: s.puppyStyle,
            redeemedCodeIds = s.redeemedCodeIds + reward.id
        )
        saveState()
        return V6RedeemOutcome(true, reward.message)
    }

    fun prestige() {
        if (_casinoRound.value != null) return
        val current = _state.value
        val gained = current.prestigePointsAvailable
        if (gained <= 0) return
        val today = LocalDate.now().toEpochDay()

        // Current run economy resets. Collection, code history, settings and skills remain.
        _state.value = current.copy(
            treats = 0,
            lifetimeTreats = 0,
            bones = 0,
            clickPower = 1,
            activeBonus = 0,
            autoPerSecond = 0,
            legitimateTaps = 0,
            upgrades = V5_UPGRADES.associate { it.id to 0 },
            totalShopPurchases = 0,
            ticketInventory = TicketRarity.entries.associateWith { 0 },
            lastTicketDrop = null,
            ticketDropSerial = current.ticketDropSerial,
            happiness = 100,
            fullness = 100,
            energy = 100,
            cleanliness = 100,
            careActions = 0,
            totalTaps = 0,
            combo = 0,
            bestCombo = 0,
            lastTapMs = 0,
            parkActive = false,
            parkReadyAtMs = 0,
            lastDailyClaimDay = Long.MIN_VALUE,
            dailyStreak = 0,
            dailyDay = today,
            dailyTapStart = 0,
            dailyCareStart = 0,
            dailyShopStart = 0,
            claimedDailyTasks = emptySet(),
            prestigeCount = current.prestigeCount + 1,
            skillPoints = current.skillPoints + gained,
            totalPrestigePointsEarned = safeAdd(current.totalPrestigePointsEarned, gained.toLong()),
            cooldownUntilMs = 0
        )
        recentTapTimes.clear()
        suspicionHits = 0
        suspicionWindowStartedMs = 0L
        saveState(clearUpgradeKeys = true)
    }

    fun buyPrestigeSkill(skill: PrestigeSkill) {
        val s = _state.value
        val level = s.prestigeSkills[skill] ?: 0
        if (level >= skill.maxLevel) return
        val cost = v6SkillCost(s, skill)
        if (s.skillPoints < cost) return
        val nextSkills = s.prestigeSkills.toMutableMap().apply { this[skill] = level + 1 }
        _state.value = s.copy(skillPoints = s.skillPoints - cost, prestigeSkills = nextSkills)
        saveState()
    }

    fun renamePuppy(name: String) {
        val clean = name.trim().replace("\n", " ").take(18)
        if (clean.isBlank()) return
        _state.update { it.copy(puppyName = clean) }
        saveState()
    }

    @Synchronized
    internal fun pullPuppyGacha(payment: PuppyGachaPayment): PuppyGachaPullResult {
        val current = _state.value
        val styles = DynamicPuppyRoster.groups.value.flatMap { it.puppies }
        val candidates = PuppyGachaEngine.pullPool(
            styles = styles,
            unlocked = current.unlockedPuppies
        )
        if (candidates.isEmpty()) {
            return PuppyGachaPullResult(
                success = false,
                payment = payment,
                failure = PuppyGachaFailure.NO_ELIGIBLE_PUPPIES
            )
        }

        val commonTickets = current.ticketInventory[TicketRarity.COMMON] ?: 0
        when (payment) {
            PuppyGachaPayment.TREATS -> {
                if (current.treats < PuppyGachaEngine.COST_TREATS) {
                    return PuppyGachaPullResult(
                        success = false,
                        costTreats = PuppyGachaEngine.COST_TREATS,
                        payment = payment,
                        failure = PuppyGachaFailure.NOT_ENOUGH_TREATS
                    )
                }
            }
            PuppyGachaPayment.COMMON_TICKET -> {
                if (commonTickets < PuppyGachaEngine.COST_COMMON_TICKETS) {
                    return PuppyGachaPullResult(
                        success = false,
                        costTickets = PuppyGachaEngine.COST_COMMON_TICKETS,
                        payment = payment,
                        failure = PuppyGachaFailure.NOT_ENOUGH_TICKETS
                    )
                }
            }
        }

        val selected = PuppyGachaEngine.select(
            candidates = candidates,
            roll = Random.nextInt(candidates.size)
        ) ?: return PuppyGachaPullResult(
            success = false,
            payment = payment,
            failure = PuppyGachaFailure.NO_ELIGIBLE_PUPPIES
        )
        val isNewUnlock = selected.id !in current.unlockedPuppies

        val nextInventory = if (payment == PuppyGachaPayment.COMMON_TICKET) {
            current.ticketInventory + (
                TicketRarity.COMMON to
                    (commonTickets - PuppyGachaEngine.COST_COMMON_TICKETS).coerceAtLeast(0)
            )
        } else {
            current.ticketInventory
        }

        _state.value = current.copy(
            treats = if (payment == PuppyGachaPayment.TREATS) {
                current.treats - PuppyGachaEngine.COST_TREATS
            } else {
                current.treats
            },
            ticketInventory = nextInventory,
            unlockedPuppies = current.unlockedPuppies + selected.id
        )
        saveState()

        return PuppyGachaPullResult(
            success = true,
            puppyId = selected.id,
            puppyName = selected.name,
            puppyEmoji = selected.emoji,
            costTreats = if (payment == PuppyGachaPayment.TREATS) {
                PuppyGachaEngine.COST_TREATS
            } else {
                0L
            },
            costTickets = if (payment == PuppyGachaPayment.COMMON_TICKET) {
                PuppyGachaEngine.COST_COMMON_TICKETS
            } else {
                0
            },
            payment = payment,
            isNewUnlock = isNewUnlock
        )
    }

    fun setPuppyStyle(id: String) {
        val s = _state.value
        if (id !in s.unlockedPuppies || id !in V6_PUPPY_IDS) return
        _state.value = s.copy(puppyStyle = id)
        saveState()
    }

    fun receiveExchangePuppy(puppyId: String): Boolean {
        val asset = DynamicPuppyRoster.asset(puppyId) ?: return false
        val policy = asset.transferPolicy
        if (!policy.giftable || policy.bound) return false
        val current = _state.value
        if (asset.style.id in current.unlockedPuppies) return true
        _state.value = current.copy(unlockedPuppies = current.unlockedPuppies + asset.style.id)
        saveState()
        return true
    }

    fun applyExchangeTrade(
        sentPuppyIds: Set<String>,
        receivedPuppyIds: Set<String>,
        recoveryTransactionId: String? = null
    ): Boolean {
        if (sentPuppyIds.isEmpty() || receivedPuppyIds.isEmpty()) return false
        if (sentPuppyIds.size > PuppyExchangeLedger.MAX_TRADE_ITEMS ||
            receivedPuppyIds.size > PuppyExchangeLedger.MAX_TRADE_ITEMS
        ) return false
        val current = _state.value
        if (!current.unlockedPuppies.containsAll(sentPuppyIds)) return false

        val localPlayerId = PuppyPlayerIdentity.playerId(getApplication())
        val lockedPuppies = PuppyExchangeLedger(getApplication()).lockedPuppyIdsFor(
            playerId = localPlayerId,
            excludingTransactionId = recoveryTransactionId
        )
        if (sentPuppyIds.any { it in lockedPuppies }) return false

        val sentAssets = sentPuppyIds.map { DynamicPuppyRoster.asset(it) ?: return false }
        val receivedAssets = receivedPuppyIds.map { DynamicPuppyRoster.asset(it) ?: return false }
        if ((sentAssets + receivedAssets).any { asset ->
                val policy = asset.transferPolicy
                !policy.tradeable || policy.bound || policy.sourceCopy
            }
        ) return false

        val nextUnlocked = (current.unlockedPuppies - sentPuppyIds) + receivedPuppyIds
        if (nextUnlocked.isEmpty()) return false
        val nextStyle = if (current.puppyStyle in sentPuppyIds) {
            nextUnlocked.firstOrNull { it in V6_PUPPY_IDS } ?: nextUnlocked.first()
        } else current.puppyStyle

        _state.value = current.copy(
            unlockedPuppies = nextUnlocked,
            puppyStyle = nextStyle
        )
        saveState()
        return true
    }

    fun cancelRecoveredExchangeTrade(
        sentPuppyIds: Set<String>,
        receivedPuppyIds: Set<String>,
        receivedAlreadyOwnedIds: Set<String>,
        selectedPuppyBeforeCommit: String?
    ): Boolean {
        if (sentPuppyIds.isEmpty() || receivedPuppyIds.isEmpty()) return false
        val current = _state.value
        val newlyReceived = receivedPuppyIds - receivedAlreadyOwnedIds
        val nextUnlocked = (current.unlockedPuppies - newlyReceived) + sentPuppyIds
        if (nextUnlocked.isEmpty()) return false

        val nextStyle = when {
            current.puppyStyle in nextUnlocked -> current.puppyStyle
            selectedPuppyBeforeCommit != null && selectedPuppyBeforeCommit in nextUnlocked ->
                selectedPuppyBeforeCommit
            else -> nextUnlocked.firstOrNull { it in V6_PUPPY_IDS } ?: nextUnlocked.first()
        }

        _state.value = current.copy(
            unlockedPuppies = nextUnlocked,
            puppyStyle = nextStyle
        )
        saveState()
        return true
    }

    fun setAccessory(value: String) {
        if (value !in ACCESSORIES) return
        val current = _state.value
        if (value != "None" && value !in current.ownedAccessories) return
        _state.value = current.copy(accessory = value)
        saveState()
    }

    fun buyAccessoryWithPupCoins(accessory: String): Boolean {
        val price = PuppyEconomyV7.accessoryPrice(accessory) ?: return false
        val current = _state.value
        if (accessory in current.ownedAccessories) return true
        if (current.pupCoins < price) return false
        _state.value = current.copy(
            pupCoins = current.pupCoins - price,
            ownedAccessories = current.ownedAccessories + accessory
        )
        saveState()
        return true
    }

    fun buyUpgradeTicketWithPupCoins(rarity: TicketRarity): Boolean {
        val current = _state.value
        val purchased = current.ticketShopPurchases[rarity] ?: 0
        val cost = PuppyEconomyV7.ticketShopCost(rarity, purchased)
        val ownedTickets = current.ticketInventory[rarity] ?: 0
        if (current.pupCoins < cost || ownedTickets >= MAX_TICKETS_PER_RARITY) return false
        _state.value = current.copy(
            pupCoins = current.pupCoins - cost,
            ticketInventory = current.ticketInventory + (
                rarity to (ownedTickets + 1).coerceAtMost(MAX_TICKETS_PER_RARITY)
            ),
            ticketShopPurchases = current.ticketShopPurchases + (
                rarity to (purchased + 1).coerceAtLeast(0)
            )
        )
        saveState()
        return true
    }

    fun convertTreatsToCasinoChips(spendTreats: Long): Boolean {
        val current = _state.value
        val chips = PuppyEconomyV7.chipsForTreats(spendTreats) ?: return false
        if (current.treats < spendTreats) return false
        _state.value = current.copy(
            treats = current.treats - spendTreats,
            casinoChips = safeAdd(current.casinoChips, chips)
        )
        saveState()
        return true
    }

    fun setHapticsEnabled(value: Boolean) { _state.update { it.copy(hapticsEnabled = value) }; saveState() }
    fun setAnimationsEnabled(value: Boolean) { _state.update { it.copy(animationsEnabled = value) }; saveState() }
    fun setCompactNumbers(value: Boolean) { _state.update { it.copy(compactNumbers = value) }; saveState() }

    /** Reload state after an authenticated portable-save import without recreating the Activity. */
    fun reloadImportedSave() {
        recentTapTimes.clear()
        suspicionHits = 0
        suspicionWindowStartedMs = 0L
        _state.value = loadState()
        val casinoInspection = PuppyCasinoPersistence.inspectActiveRound(prefs)
        _casinoRound.value = casinoInspection.round
        _casinoRecoveryIssue.value = casinoInspection.issue
        _casinoRewardLedger.value = PuppyCasinoRewardPersistence.load(prefs)
        _lastCasinoTicketReward.value = null
        _casinoPuppyRewardLedger.value = PuppyCasinoPuppyRewardPersistence.load(prefs)
        _lastCasinoPuppyReward.value = null
    }

    /**
     * Stops this ViewModel from writing its old in-memory state after "Delete Local Save Data".
     * Activity/task teardown normally calls onCleared(), which otherwise saves the state again.
     */
    fun prepareForFullLocalDataErase() {
        suppressPersistence = true
        recentTapTimes.clear()
        suspicionHits = 0
        suspicionWindowStartedMs = 0L
        _casinoRound.value = null
        _casinoRecoveryIssue.value = null
        _casinoRewardLedger.value = PuppyCasinoRewardLedger()
        _lastCasinoTicketReward.value = null
        _casinoPuppyRewardLedger.value = PuppyCasinoPuppyRewardLedger()
        _lastCasinoPuppyReward.value = null
        _state.value = V6GameState()
    }

    /** Full non-prestige reset. Special code collection and settings stay protected. */
    fun resetRunWithoutPrestige() {
        if (_casinoRound.value != null) return
        val keep = _state.value
        prefs.edit().apply {
            V5_UPGRADES.forEach { remove("upgrade_${it.id}") }
            TicketRarity.entries.forEach { remove(ticketKey(it)) }
        }.apply()
        _state.value = V6GameState(
            puppyName = keep.puppyName,
            puppyStyle = keep.puppyStyle,
            unlockedPuppies = keep.unlockedPuppies,
            accessory = keep.accessory,
            redeemedCodeIds = keep.redeemedCodeIds,
            hapticsEnabled = keep.hapticsEnabled,
            animationsEnabled = keep.animationsEnabled,
            compactNumbers = keep.compactNumbers,
            prestigeCount = keep.prestigeCount,
            skillPoints = keep.skillPoints,
            prestigeSkills = keep.prestigeSkills,
            totalPrestigePointsEarned = keep.totalPrestigePointsEarned,
            totalTicketsFound = keep.totalTicketsFound
        )
        saveState(clearUpgradeKeys = true)
    }

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

    private fun rollTicketRarityV6(): TicketRarity =
        PuppyEconomyV7.ticketRarityForRoll(Random.nextInt(10_000))

    private fun rollDailyDayIfNeeded() {
        val today = LocalDate.now().toEpochDay()
        val s = _state.value
        if (s.dailyDay == today) return
        _state.value = s.copy(
            dailyDay = today,
            dailyTapStart = s.totalTaps,
            dailyCareStart = s.careActions,
            dailyShopStart = s.totalShopPurchases,
            claimedDailyTasks = emptySet()
        )
        saveState()
    }

    private fun decayNeeds() {
        _state.update {
            it.copy(
                happiness = (it.happiness - 1).coerceAtLeast(0),
                fullness = (it.fullness - 2).coerceAtLeast(0),
                energy = (it.energy - 1).coerceAtLeast(0),
                cleanliness = (it.cleanliness - 1).coerceAtLeast(0)
            )
        }
    }

    private fun looksAutomated(now: Long): Boolean {
        val taps = recentTapTimes.filter { it >= now - FAIR_PLAY_SAMPLE_MS }
        if (taps.size < MIN_FAIR_PLAY_SAMPLE_TAPS) return false
        if (taps.size >= EXTREME_TAPS_IN_SAMPLE) return true
        val intervals = taps.zipWithNext { a, b -> (b - a).toDouble() }
        if (intervals.size < MIN_INTERVAL_SAMPLE) return false
        if (intervals.takeLast(8).count { it <= IMPOSSIBLE_INTERVAL_MS } >= 6) return true
        val mean = intervals.average()
        if (mean <= 0.0 || mean > MACHINE_MEAN_MAX_MS) return false
        val variance = intervals.sumOf { (it - mean) * (it - mean) } / intervals.size
        val stdDev = sqrt(variance)
        val nearMeanRatio = intervals.count { abs(it - mean) <= MACHINE_NEAR_MEAN_MS }.toDouble() / intervals.size
        return stdDev <= MACHINE_STDDEV_MAX_MS && nearMeanRatio >= MACHINE_REGULARITY_RATIO
    }

    private fun addTreats(amount: Long) {
        if (amount <= 0) return
        _state.update {
            it.copy(
                treats = safeAdd(it.treats, amount),
                lifetimeTreats = safeAdd(it.lifetimeTreats, amount)
            )
        }
    }

    private fun persistCasinoMutation(
        before: V6GameState,
        completedBefore: List<String>,
        result: PuppyCasinoTransactionResult,
        rewardLedger: PuppyCasinoRewardLedger? = null,
        puppyRewardLedger: PuppyCasinoPuppyRewardLedger? = null
    ): PuppyCasinoTransactionResult {
        if (!result.success) return result

        val editor = prefs.edit()
            .putLong(KEY_TREATS, result.state.treats)
            .putLong(KEY_LIFETIME, result.state.lifetimeTreats)
            .putLong(KEY_TOTAL_TICKETS_FOUND, result.state.totalTicketsFound)
            .putStringSet(KEY_UNLOCKED_PUPPIES, result.state.unlockedPuppies)
        TicketRarity.entries.forEach { rarity ->
            editor.putInt(
                ticketKey(rarity),
                (result.state.ticketInventory[rarity] ?: 0)
                    .coerceIn(0, MAX_TICKETS_PER_RARITY)
            )
        }
        PuppyCasinoPersistence.write(
            editor = editor,
            activeRound = result.activeRound,
            completedRoundIds = result.completedRoundIds
        )
        rewardLedger?.let { PuppyCasinoRewardPersistence.write(editor, it) }
        puppyRewardLedger?.let {
            PuppyCasinoPuppyRewardPersistence.write(editor, it)
        }
        if (!editor.commit()) {
            return PuppyCasinoTransactionResult(
                success = false,
                state = before,
                activeRound = _casinoRound.value,
                completedRoundIds = completedBefore,
                failure = PuppyCasinoTransactionFailure.PERSISTENCE_FAILED
            )
        }

        _state.value = result.state
        _casinoRound.value = result.activeRound
        rewardLedger?.let { _casinoRewardLedger.value = it }
        puppyRewardLedger?.let { _casinoPuppyRewardLedger.value = it }
        return result
    }

    private fun loadState(): V6GameState {
        val skills = PrestigeSkill.entries.associateWith { skill ->
            prefs.getInt(skillKey(skill), 0).coerceIn(0, skill.maxLevel)
        }
        val owned = V5_UPGRADES.associate { upgrade ->
            upgrade.id to prefs.getInt("upgrade_${upgrade.id}", 0).coerceAtLeast(0)
        }
        val tapSkill = skills[PrestigeSkill.TAP_TRAINING] ?: 0
        val autoSkill = skills[PrestigeSkill.AUTO_TRAINING] ?: 0
        val clickPower = 1 + V5_UPGRADES
            .filter { it.effect == V5UpgradeEffect.CLICK }
            .sumOf { (it.amount + tapSkill) * (owned[it.id] ?: 0) }
        val activeBonus = PuppyEconomyV7.activeBonus(owned, autoSkill)

        val now = System.currentTimeMillis()
        val lastSeen = prefs.getLong(KEY_LAST_SEEN, now)
        val elapsedMinutes = ((now - lastSeen).coerceAtLeast(0) / 60_000L).coerceAtMost(240L).toInt()
        val totalTaps = prefs.getLong(KEY_TOTAL_TAPS, 0L).coerceAtLeast(0L)
        val careActions = prefs.getLong(KEY_CARE_ACTIONS, prefs.getInt("care_actions", 0).toLong()).coerceAtLeast(0L)
        val totalShop = prefs.getLong(KEY_TOTAL_SHOP, owned.values.sum().toLong()).coerceAtLeast(0L)
        val today = LocalDate.now().toEpochDay()
        val savedDailyDay = prefs.getLong(KEY_DAILY_DAY, today)
        val isToday = savedDailyDay == today

        val unlocked = (prefs.getStringSet(KEY_UNLOCKED_PUPPIES, DEFAULT_V6_PUPPIES)?.toSet() ?: DEFAULT_V6_PUPPIES) + DEFAULT_V6_PUPPIES
        val style = prefs.getString(KEY_PUPPY_STYLE, "classic")
            ?.takeIf { it in unlocked && it in V6_PUPPY_IDS }
            ?: "classic"
        val inventory = TicketRarity.entries.associateWith { rarity ->
            prefs.getInt(ticketKey(rarity), 0).coerceIn(0, MAX_TICKETS_PER_RARITY)
        }

        return V6GameState(
            puppyName = prefs.getString(KEY_NAME, "Buddy") ?: "Buddy",
            puppyStyle = style,
            unlockedPuppies = unlocked,
            accessory = prefs.getString(KEY_ACCESSORY, "None")?.takeIf { it in ACCESSORIES } ?: "None",
            ownedAccessories = (
                prefs.getStringSet(KEY_OWNED_ACCESSORIES, emptySet())?.toSet().orEmpty() +
                    setOf("None") +
                    listOfNotNull(prefs.getString(KEY_ACCESSORY, null)?.takeIf { it in ACCESSORIES })
            ),
            treats = prefs.getLong(KEY_TREATS, 0L).coerceAtLeast(0L),
            lifetimeTreats = prefs.getLong(KEY_LIFETIME, 0L).coerceAtLeast(0L),
            bones = prefs.getLong(KEY_BONES, 0L).coerceAtLeast(0L),
            pupCoins = prefs.getLong(KEY_PUP_COINS, 0L).coerceAtLeast(0L),
            casinoChips = prefs.getLong(KEY_CASINO_CHIPS, 0L).coerceAtLeast(0L),
            clickPower = clickPower,
            activeBonus = activeBonus,
            autoPerSecond = 0,
            legitimateTaps = prefs.getLong(KEY_LEGITIMATE_TAPS, totalTaps).coerceAtLeast(0L),
            upgrades = owned,
            totalShopPurchases = totalShop,
            ticketInventory = inventory,
            ticketShopPurchases = TicketRarity.entries.associateWith { rarity ->
                prefs.getInt(ticketShopPurchaseKey(rarity), 0).coerceAtLeast(0)
            },
            totalTicketsFound = prefs.getLong(KEY_TOTAL_TICKETS_FOUND, 0L).coerceAtLeast(0L),
            happiness = (prefs.getInt(KEY_HAPPINESS, 100).coerceIn(0, 100) - elapsedMinutes / 3).coerceAtLeast(0),
            fullness = (prefs.getInt(KEY_FULLNESS, 100).coerceIn(0, 100) - elapsedMinutes / 2).coerceAtLeast(0),
            energy = (prefs.getInt(KEY_ENERGY, 100).coerceIn(0, 100) - elapsedMinutes / 4).coerceAtLeast(0),
            cleanliness = (prefs.getInt(KEY_CLEANLINESS, 100).coerceIn(0, 100) - elapsedMinutes / 4).coerceAtLeast(0),
            bond = prefs.getInt(KEY_BOND, 10).coerceIn(0, 100),
            careActions = careActions,
            totalTaps = totalTaps,
            bestCombo = prefs.getInt(KEY_BEST_COMBO, 0).coerceAtLeast(0),
            parkActive = prefs.getBoolean(KEY_PARK_ACTIVE, false),
            parkReadyAtMs = prefs.getLong(KEY_PARK_READY_AT, 0L),
            lastDailyClaimDay = prefs.getLong(KEY_DAILY_CLAIM, Long.MIN_VALUE),
            dailyStreak = prefs.getInt(KEY_DAILY_STREAK, 0).coerceAtLeast(0),
            dailyDay = today,
            dailyTapStart = if (isToday) prefs.getLong(KEY_DAILY_TAP_START, totalTaps) else totalTaps,
            dailyCareStart = if (isToday) prefs.getLong(KEY_DAILY_CARE_START, careActions) else careActions,
            dailyShopStart = if (isToday) prefs.getLong(KEY_DAILY_SHOP_START, totalShop) else totalShop,
            claimedDailyTasks = if (isToday) prefs.getStringSet(KEY_DAILY_TASKS, emptySet())?.toSet() ?: emptySet() else emptySet(),
            redeemedCodeIds = prefs.getStringSet(KEY_REDEEMED_CODES, emptySet())?.toSet() ?: emptySet(),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
            animationsEnabled = prefs.getBoolean(KEY_ANIMATIONS, true),
            compactNumbers = prefs.getBoolean(KEY_COMPACT_NUMBERS, true),
            pupEyeStrikes = prefs.getInt(KEY_STRIKES, 0).coerceAtLeast(0),
            cooldownUntilMs = prefs.getLong(KEY_COOLDOWN, 0L).coerceAtLeast(0L),
            prestigeCount = prefs.getInt(KEY_PRESTIGE_COUNT, 0).coerceAtLeast(0),
            skillPoints = prefs.getInt(KEY_SKILL_POINTS, 0).coerceAtLeast(0),
            prestigeSkills = skills,
            totalPrestigePointsEarned = prefs.getLong(KEY_TOTAL_PRESTIGE_POINTS, 0L).coerceAtLeast(0L)
        )
    }

    private fun saveState(clearUpgradeKeys: Boolean = false) {
        if (suppressPersistence) return
        val s = _state.value
        prefs.edit().apply {
            putString(KEY_NAME, s.puppyName)
            putString(KEY_PUPPY_STYLE, s.puppyStyle)
            putStringSet(KEY_UNLOCKED_PUPPIES, s.unlockedPuppies)
            putString(KEY_ACCESSORY, s.accessory)
            putStringSet(KEY_OWNED_ACCESSORIES, s.ownedAccessories)
            putLong(KEY_TREATS, s.treats)
            putLong(KEY_LIFETIME, s.lifetimeTreats)
            putLong(KEY_BONES, s.bones)
            putLong(KEY_PUP_COINS, s.pupCoins)
            putLong(KEY_CASINO_CHIPS, s.casinoChips)
            putLong(KEY_LEGITIMATE_TAPS, s.legitimateTaps)
            putLong(KEY_TOTAL_SHOP, s.totalShopPurchases)
            putLong(KEY_TOTAL_TICKETS_FOUND, s.totalTicketsFound)
            TicketRarity.entries.forEach { rarity ->
                putInt(ticketKey(rarity), s.ticketInventory[rarity] ?: 0)
                putInt(ticketShopPurchaseKey(rarity), s.ticketShopPurchases[rarity] ?: 0)
            }
            if (clearUpgradeKeys) V5_UPGRADES.forEach { remove("upgrade_${it.id}") }
            s.upgrades.forEach { (id, count) -> putInt("upgrade_$id", count) }
            putInt(KEY_HAPPINESS, s.happiness)
            putInt(KEY_FULLNESS, s.fullness)
            putInt(KEY_ENERGY, s.energy)
            putInt(KEY_CLEANLINESS, s.cleanliness)
            putInt(KEY_BOND, s.bond)
            putLong(KEY_CARE_ACTIONS, s.careActions)
            putLong(KEY_TOTAL_TAPS, s.totalTaps)
            putInt(KEY_BEST_COMBO, s.bestCombo)
            putBoolean(KEY_PARK_ACTIVE, s.parkActive)
            putLong(KEY_PARK_READY_AT, s.parkReadyAtMs)
            putLong(KEY_DAILY_CLAIM, s.lastDailyClaimDay)
            putInt(KEY_DAILY_STREAK, s.dailyStreak)
            putLong(KEY_DAILY_DAY, s.dailyDay)
            putLong(KEY_DAILY_TAP_START, s.dailyTapStart)
            putLong(KEY_DAILY_CARE_START, s.dailyCareStart)
            putLong(KEY_DAILY_SHOP_START, s.dailyShopStart)
            putStringSet(KEY_DAILY_TASKS, s.claimedDailyTasks)
            putStringSet(KEY_REDEEMED_CODES, s.redeemedCodeIds)
            putBoolean(KEY_HAPTICS, s.hapticsEnabled)
            putBoolean(KEY_ANIMATIONS, s.animationsEnabled)
            putBoolean(KEY_COMPACT_NUMBERS, s.compactNumbers)
            putInt(KEY_STRIKES, s.pupEyeStrikes)
            putLong(KEY_COOLDOWN, s.cooldownUntilMs)
            putInt(KEY_PRESTIGE_COUNT, s.prestigeCount)
            putInt(KEY_SKILL_POINTS, s.skillPoints)
            putLong(KEY_TOTAL_PRESTIGE_POINTS, s.totalPrestigePointsEarned)
            PrestigeSkill.entries.forEach { skill -> putInt(skillKey(skill), s.prestigeSkills[skill] ?: 0) }
            putLong(KEY_LAST_SEEN, System.currentTimeMillis())
        }.apply()
    }

    override fun onCleared() {
        saveState()
        super.onCleared()
    }

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    companion object {
        const val PREFS_NAME = "puppy_clicker_save"
        val ACCESSORIES = listOf("None", "Bandana", "Bow", "Crown")
        const val FEED_COST = 20L
        const val PARK_ADVENTURE_MS = 60_000L

        private const val PRESTIGE_MIN_TREATS = 50_000L
        private const val PRESTIGE_BONUS_STEP = 100_000L
        private const val MAX_POINTS_PER_PRESTIGE = 8

        private const val KEY_NAME = "puppy_name"
        private const val KEY_PUPPY_STYLE = "puppy_style"
        private const val KEY_UNLOCKED_PUPPIES = "unlocked_puppies"
        private const val KEY_ACCESSORY = "accessory"
        private const val KEY_OWNED_ACCESSORIES = "owned_accessories_v7"
        private const val KEY_TREATS = "treats"
        private const val KEY_LIFETIME = "lifetime_treats"
        private const val KEY_BONES = "bones_v7"
        private const val KEY_PUP_COINS = "pup_coins_v7"
        private const val KEY_CASINO_CHIPS = "casino_chips_v7"
        private const val KEY_LEGITIMATE_TAPS = "legitimate_taps_v7"
        private const val KEY_TOTAL_SHOP = "total_shop_purchases_v5"
        private const val KEY_TOTAL_TICKETS_FOUND = "total_tickets_found"
        private const val KEY_HAPPINESS = "happiness"
        private const val KEY_FULLNESS = "fullness"
        private const val KEY_ENERGY = "energy"
        private const val KEY_CLEANLINESS = "cleanliness_v5"
        private const val KEY_BOND = "bond_v5"
        private const val KEY_CARE_ACTIONS = "care_actions_v5"
        private const val KEY_TOTAL_TAPS = "total_taps"
        private const val KEY_BEST_COMBO = "best_combo"
        private const val KEY_PARK_ACTIVE = "park_active"
        private const val KEY_PARK_READY_AT = "park_ready_at"
        private const val KEY_DAILY_CLAIM = "daily_claim_day"
        private const val KEY_DAILY_STREAK = "daily_streak_v5"
        private const val KEY_DAILY_DAY = "daily_day_v5"
        private const val KEY_DAILY_TAP_START = "daily_tap_start_v5"
        private const val KEY_DAILY_CARE_START = "daily_care_start_v5"
        private const val KEY_DAILY_SHOP_START = "daily_shop_start_v5"
        private const val KEY_DAILY_TASKS = "daily_tasks_v5"
        private const val KEY_REDEEMED_CODES = "redeemed_code_ids"
        private const val KEY_HAPTICS = "setting_haptics"
        private const val KEY_ANIMATIONS = "setting_animations"
        private const val KEY_COMPACT_NUMBERS = "setting_compact_numbers"
        private const val KEY_STRIKES = "pup_eye_strikes"
        private const val KEY_COOLDOWN = "fair_play_cooldown_until"
        private const val KEY_LAST_SEEN = "last_seen"

        private const val KEY_PRESTIGE_COUNT = "prestige_count_v6"
        private const val KEY_SKILL_POINTS = "prestige_skill_points_v6"
        private const val KEY_TOTAL_PRESTIGE_POINTS = "prestige_total_points_v6"

        private const val KEY_AFK_CLAIM_READY = "afk_claim_ready_v6"

        private const val MAX_TICKETS_PER_RARITY =
            PuppyCasinoRewardEngine.MAX_TICKETS_PER_RARITY
        private const val COMBO_CHAIN_MS = 900L
        private const val COMBO_TIMEOUT_MS = 1_600L
        private const val FAIR_PLAY_HISTORY_MS = 3_000L
        private const val FAIR_PLAY_SAMPLE_MS = 2_000L
        private const val MIN_FAIR_PLAY_SAMPLE_TAPS = 12
        private const val MIN_INTERVAL_SAMPLE = 11
        private const val EXTREME_TAPS_IN_SAMPLE = 48
        private const val IMPOSSIBLE_INTERVAL_MS = 22.0
        private const val MACHINE_MEAN_MAX_MS = 115.0
        private const val MACHINE_STDDEV_MAX_MS = 5.5
        private const val MACHINE_NEAR_MEAN_MS = 7.0
        private const val MACHINE_REGULARITY_RATIO = 0.82
        private const val REQUIRED_SUSPICION_HITS = 2
        private const val SUSPICION_CONFIRM_WINDOW_MS = 5_000L
        private const val BASE_FAIR_PLAY_COOLDOWN_MS = 4_000L
        private const val MAX_FAIR_PLAY_COOLDOWN_MS = 20_000L

        private fun ticketKey(rarity: TicketRarity) = "upgrade_ticket_${rarity.name.lowercase()}"
        private fun ticketShopPurchaseKey(rarity: TicketRarity) =
            "ticket_shop_purchase_${rarity.name.lowercase()}_v7"
        private fun skillKey(skill: PrestigeSkill) = "prestige_skill_${skill.name.lowercase()}_v6"
    }
}

private val DEFAULT_V6_PUPPIES = setOf("classic", "golden", "poodle", "spotty")
private const val PRESTIGE_MIN_TREATS = 50_000L
private const val PRESTIGE_BONUS_STEP = 100_000L
private const val MAX_POINTS_PER_PRESTIGE = 8
