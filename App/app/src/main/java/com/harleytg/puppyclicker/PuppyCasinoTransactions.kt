package com.harleytg.puppyclicker

import android.content.SharedPreferences
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal enum class PuppyCasinoGame {
    SLOTS,
    ROULETTE,
    BLACKJACK,
    PLINKO,
    SCRATCHERS,
    LUCKY_WHEEL
}

internal enum class PuppyCasinoRoundState {
    WAGER_ACCEPTED,
    OUTCOME_COMMITTED
}

/**
 * wagerTreats/payoutTreats keep their historical persisted names so pre-V7 committed rounds can
 * still be decoded. From Economy V7 onward these numeric values represent Casino Chips.
 */
internal data class PuppyCasinoRound(
    val roundId: String,
    val game: PuppyCasinoGame,
    val wagerTreats: Long,
    val state: PuppyCasinoRoundState,
    val acceptedAtMs: Long,
    val wagerPayload: String? = null,
    val outcomePayload: String? = null,
    val payoutTreats: Long = 0L,
    val outcomeCommittedAtMs: Long = 0L
) {
    val wagerChips: Long get() = wagerTreats
    val payoutChips: Long get() = payoutTreats
}

internal enum class PuppyCasinoTransactionFailure {
    FEATURE_DISABLED,
    INVALID_ROUND_ID,
    INVALID_WAGER,
    INSUFFICIENT_TREATS, // legacy enum value retained for source compatibility
    INSUFFICIENT_CHIPS,
    ROUND_ALREADY_ACTIVE,
    DUPLICATE_ROUND,
    ROUND_NOT_FOUND,
    ROUND_MISMATCH,
    INVALID_ROUND_STATE,
    INVALID_WAGER_PAYLOAD,
    INVALID_OUTCOME,
    BALANCE_OVERFLOW,
    CORRUPT_SAVE,
    PERSISTENCE_FAILED
}

internal data class PuppyCasinoTransactionResult(
    val success: Boolean,
    val state: V6GameState,
    val activeRound: PuppyCasinoRound?,
    val completedRoundIds: List<String>,
    val failure: PuppyCasinoTransactionFailure? = null
)

internal object PuppyCasinoRoundIds {
    fun newId(): String = UUID.randomUUID().toString()
}

internal object PuppyCasinoTransactionEngine {
    const val MAX_COMPLETED_ROUND_IDS = 256
    const val MAX_OUTCOME_PAYLOAD_CHARS = 4_096

    private val ROUND_ID_PATTERN = Regex("[A-Za-z0-9_-]{8,80}")

    fun isValidRoundId(value: String): Boolean = ROUND_ID_PATTERN.matches(value)

    fun acceptWager(
        before: V6GameState,
        activeRound: PuppyCasinoRound?,
        completedRoundIds: List<String>,
        roundId: String,
        game: PuppyCasinoGame,
        wagerTreats: Long,
        featureAvailable: Boolean,
        acceptedAtMs: Long,
        wagerPayload: String? = null,
        chargeWager: Boolean = true
    ): PuppyCasinoTransactionResult {
        if (!featureAvailable) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.FEATURE_DISABLED)
        }
        if (!isValidRoundId(roundId)) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_ROUND_ID)
        }
        if (wagerTreats <= 0L) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_WAGER)
        }
        if (roundId in completedRoundIds) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.DUPLICATE_ROUND)
        }
        if (activeRound != null) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.ROUND_ALREADY_ACTIVE)
        }
        if (chargeWager && before.casinoChips < wagerTreats) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.INSUFFICIENT_CHIPS)
        }
        val persistedWagerPayload = wagerPayload?.trim()?.ifBlank { null }
        if (persistedWagerPayload != null && persistedWagerPayload.length > MAX_OUTCOME_PAYLOAD_CHARS) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_OUTCOME)
        }

        val round = PuppyCasinoRound(
            roundId = roundId,
            game = game,
            wagerTreats = wagerTreats,
            state = PuppyCasinoRoundState.WAGER_ACCEPTED,
            acceptedAtMs = acceptedAtMs.coerceAtLeast(0L),
            wagerPayload = persistedWagerPayload
        )
        return PuppyCasinoTransactionResult(
            success = true,
            state = if (chargeWager) {
                before.copy(casinoChips = before.casinoChips - wagerTreats)
            } else before,
            activeRound = round,
            completedRoundIds = completedRoundIds
        )
    }

    fun updateAcceptedRound(
        before: V6GameState,
        activeRound: PuppyCasinoRound?,
        completedRoundIds: List<String>,
        roundId: String,
        wagerPayload: String,
        additionalWagerTreats: Long = 0L,
        chargeAdditionalWager: Boolean = true
    ): PuppyCasinoTransactionResult {
        if (roundId in completedRoundIds) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.DUPLICATE_ROUND)
        }
        val round = activeRound
            ?: return failure(before, null, completedRoundIds, PuppyCasinoTransactionFailure.ROUND_NOT_FOUND)
        if (round.roundId != roundId) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.ROUND_MISMATCH)
        }
        if (round.state != PuppyCasinoRoundState.WAGER_ACCEPTED) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_ROUND_STATE)
        }
        val payload = wagerPayload.trim()
        if (payload.isBlank() || payload.length > MAX_OUTCOME_PAYLOAD_CHARS) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_WAGER_PAYLOAD)
        }
        if (additionalWagerTreats < 0L) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_WAGER)
        }
        if (chargeAdditionalWager && before.casinoChips < additionalWagerTreats) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.INSUFFICIENT_CHIPS)
        }
        val totalWager = checkedAdd(round.wagerTreats, additionalWagerTreats)
            ?: return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.BALANCE_OVERFLOW)

        return PuppyCasinoTransactionResult(
            success = true,
            state = if (chargeAdditionalWager) {
                before.copy(casinoChips = before.casinoChips - additionalWagerTreats)
            } else {
                before
            },
            activeRound = round.copy(
                wagerTreats = totalWager,
                wagerPayload = payload
            ),
            completedRoundIds = completedRoundIds
        )
    }

    fun commitOutcome(
        before: V6GameState,
        activeRound: PuppyCasinoRound?,
        completedRoundIds: List<String>,
        roundId: String,
        outcomePayload: String,
        payoutTreats: Long,
        committedAtMs: Long
    ): PuppyCasinoTransactionResult {
        if (roundId in completedRoundIds) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.DUPLICATE_ROUND)
        }
        val round = activeRound
            ?: return failure(before, null, completedRoundIds, PuppyCasinoTransactionFailure.ROUND_NOT_FOUND)
        if (round.roundId != roundId) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.ROUND_MISMATCH)
        }
        if (round.state != PuppyCasinoRoundState.WAGER_ACCEPTED) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_ROUND_STATE)
        }
        val payload = outcomePayload.trim()
        if (payload.isBlank() || payload.length > MAX_OUTCOME_PAYLOAD_CHARS || payoutTreats < 0L) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_OUTCOME)
        }

        return PuppyCasinoTransactionResult(
            success = true,
            state = before,
            activeRound = round.copy(
                state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
                outcomePayload = payload,
                payoutTreats = payoutTreats,
                outcomeCommittedAtMs = committedAtMs.coerceAtLeast(round.acceptedAtMs)
            ),
            completedRoundIds = completedRoundIds
        )
    }

    fun settle(
        before: V6GameState,
        activeRound: PuppyCasinoRound?,
        completedRoundIds: List<String>,
        roundId: String,
        creditPayout: Boolean = true
    ): PuppyCasinoTransactionResult {
        if (roundId in completedRoundIds) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.DUPLICATE_ROUND)
        }
        val round = activeRound
            ?: return failure(before, null, completedRoundIds, PuppyCasinoTransactionFailure.ROUND_NOT_FOUND)
        if (round.roundId != roundId) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.ROUND_MISMATCH)
        }
        if (round.state != PuppyCasinoRoundState.OUTCOME_COMMITTED || round.outcomePayload.isNullOrBlank()) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_ROUND_STATE)
        }

        val nextChips = if (creditPayout) {
            checkedAdd(before.casinoChips, round.payoutTreats)
        } else {
            before.casinoChips
        }
            ?: return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.BALANCE_OVERFLOW)

        return PuppyCasinoTransactionResult(
            success = true,
            // Casino settlements are isolated from progression: lifetimeTreats never changes.
            state = before.copy(casinoChips = nextChips),
            activeRound = null,
            completedRoundIds = appendCompleted(completedRoundIds, roundId)
        )
    }

    fun refund(
        before: V6GameState,
        activeRound: PuppyCasinoRound?,
        completedRoundIds: List<String>,
        roundId: String,
        refundWager: Boolean = true
    ): PuppyCasinoTransactionResult {
        if (roundId in completedRoundIds) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.DUPLICATE_ROUND)
        }
        val round = activeRound
            ?: return failure(before, null, completedRoundIds, PuppyCasinoTransactionFailure.ROUND_NOT_FOUND)
        if (round.roundId != roundId) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.ROUND_MISMATCH)
        }
        if (round.state != PuppyCasinoRoundState.WAGER_ACCEPTED) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.INVALID_ROUND_STATE)
        }

        val nextChips = if (refundWager) {
            checkedAdd(before.casinoChips, round.wagerTreats)
                ?: return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.BALANCE_OVERFLOW)
        } else {
            before.casinoChips
        }

        return PuppyCasinoTransactionResult(
            success = true,
            state = before.copy(casinoChips = nextChips),
            activeRound = null,
            completedRoundIds = appendCompleted(completedRoundIds, roundId)
        )
    }

    private fun appendCompleted(existing: List<String>, roundId: String): List<String> =
        (existing.filterNot { it == roundId } + roundId).takeLast(MAX_COMPLETED_ROUND_IDS)

    private fun checkedAdd(a: Long, b: Long): Long? {
        if (b < 0L) return null
        if (a > Long.MAX_VALUE - b) return null
        return a + b
    }

    private fun failure(
        before: V6GameState,
        activeRound: PuppyCasinoRound?,
        completedRoundIds: List<String>,
        reason: PuppyCasinoTransactionFailure
    ): PuppyCasinoTransactionResult = PuppyCasinoTransactionResult(
        success = false,
        state = before,
        activeRound = activeRound,
        completedRoundIds = completedRoundIds,
        failure = reason
    )
}

internal data class PuppyCasinoStoredRoundInspection(
    val round: PuppyCasinoRound?,
    val issue: String? = null
) {
    val hasIssue: Boolean get() = issue != null
}

internal object PuppyCasinoPersistence {
    private const val SCHEMA_VERSION = 1
    internal const val ACTIVE_ROUND_KEY = "casino_active_round_v1"
    internal const val COMPLETED_ROUND_IDS_KEY = "casino_completed_round_ids_v1"

    fun loadActiveRound(prefs: SharedPreferences): PuppyCasinoRound? =
        inspectActiveRound(prefs).round

    fun inspectActiveRound(prefs: SharedPreferences): PuppyCasinoStoredRoundInspection {
        val raw = prefs.getString(ACTIVE_ROUND_KEY, null)
        if (raw.isNullOrBlank()) return PuppyCasinoStoredRoundInspection(round = null)

        val round = decodeRound(raw)
            ?: return PuppyCasinoStoredRoundInspection(
                round = null,
                issue = "Stored Casino round is malformed"
            )
        val validation = PuppyCasinoSaveValidator.validateActiveRound(round)
        if (!validation.valid) {
            return PuppyCasinoStoredRoundInspection(
                round = null,
                issue = validation.message ?: "Stored Casino round failed validation"
            )
        }
        return PuppyCasinoStoredRoundInspection(round = round)
    }

    internal fun decodeRoundForValidation(raw: String?): PuppyCasinoRound? =
        decodeRound(raw)

    fun loadCompletedRoundIds(prefs: SharedPreferences): List<String> =
        decodeCompletedIds(prefs.getString(COMPLETED_ROUND_IDS_KEY, null))

    internal fun decodeCompletedIdsForValidation(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        return decodeCompletedIdsStrict(raw)
    }

    fun write(
        editor: SharedPreferences.Editor,
        activeRound: PuppyCasinoRound?,
        completedRoundIds: List<String>
    ) {
        if (activeRound == null) {
            editor.remove(ACTIVE_ROUND_KEY)
        } else {
            editor.putString(ACTIVE_ROUND_KEY, encodeRound(activeRound))
        }
        editor.putString(
            COMPLETED_ROUND_IDS_KEY,
            encodeCompletedIds(completedRoundIds.takeLast(PuppyCasinoTransactionEngine.MAX_COMPLETED_ROUND_IDS))
        )
    }

    private fun encodeRound(round: PuppyCasinoRound): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("roundId", round.roundId)
        put("game", round.game.name)
        // Historical field names intentionally remain for backward-compatible round recovery.
        put("wagerTreats", round.wagerTreats)
        put("state", round.state.name)
        put("acceptedAtMs", round.acceptedAtMs)
        round.wagerPayload?.let { put("wagerPayload", it) }
        round.outcomePayload?.let { put("outcomePayload", it) }
        put("payoutTreats", round.payoutTreats)
        put("outcomeCommittedAtMs", round.outcomeCommittedAtMs)
    }.toString()

    private fun decodeRound(raw: String?): PuppyCasinoRound? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schemaVersion", -1) != SCHEMA_VERSION) return@runCatching null
            val roundId = json.getString("roundId")
            if (!PuppyCasinoTransactionEngine.isValidRoundId(roundId)) return@runCatching null
            val game = PuppyCasinoGame.valueOf(json.getString("game"))
            val state = PuppyCasinoRoundState.valueOf(json.getString("state"))
            val wager = json.getLong("wagerTreats")
            if (wager <= 0L) return@runCatching null
            val payout = json.optLong("payoutTreats", 0L)
            if (payout < 0L) return@runCatching null
            val wagerPayload = json.optString("wagerPayload", "").trim().ifBlank { null }
            if (wagerPayload != null && wagerPayload.length > PuppyCasinoTransactionEngine.MAX_OUTCOME_PAYLOAD_CHARS) {
                return@runCatching null
            }
            val outcome = json.optString("outcomePayload", "").trim().ifBlank { null }
            if (state == PuppyCasinoRoundState.OUTCOME_COMMITTED && outcome == null) {
                return@runCatching null
            }
            PuppyCasinoRound(
                roundId = roundId,
                game = game,
                wagerTreats = wager,
                state = state,
                acceptedAtMs = json.optLong("acceptedAtMs", 0L).coerceAtLeast(0L),
                wagerPayload = wagerPayload,
                outcomePayload = outcome,
                payoutTreats = payout,
                outcomeCommittedAtMs = json.optLong("outcomeCommittedAtMs", 0L).coerceAtLeast(0L)
            )
        }.getOrNull()
    }

    private fun encodeCompletedIds(ids: List<String>): String = JSONArray().apply {
        ids.distinct().takeLast(PuppyCasinoTransactionEngine.MAX_COMPLETED_ROUND_IDS).forEach { id ->
            if (PuppyCasinoTransactionEngine.isValidRoundId(id)) put(id)
        }
    }.toString()

    private fun decodeCompletedIds(raw: String?): List<String> =
        decodeCompletedIdsStrict(raw) ?: emptyList()

    private fun decodeCompletedIdsStrict(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val result = buildList {
                for (index in 0 until array.length()) {
                    val id = array.getString(index)
                    if (!PuppyCasinoTransactionEngine.isValidRoundId(id)) {
                        return@runCatching null
                    }
                    if (id in this) return@runCatching null
                    add(id)
                }
            }
            if (result.size > PuppyCasinoTransactionEngine.MAX_COMPLETED_ROUND_IDS) {
                return@runCatching null
            }
            result
        }.getOrNull()
    }
}
