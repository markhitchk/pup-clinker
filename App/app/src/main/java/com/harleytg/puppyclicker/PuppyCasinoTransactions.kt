package com.harleytg.puppyclicker

import android.content.SharedPreferences
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal enum class PuppyCasinoGame {
    SLOTS,
    ROULETTE,
    BLACKJACK
}

internal enum class PuppyCasinoRoundState {
    WAGER_ACCEPTED,
    OUTCOME_COMMITTED
}

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
)

internal enum class PuppyCasinoTransactionFailure {
    FEATURE_DISABLED,
    INVALID_ROUND_ID,
    INVALID_WAGER,
    INSUFFICIENT_TREATS,
    ROUND_ALREADY_ACTIVE,
    DUPLICATE_ROUND,
    ROUND_NOT_FOUND,
    ROUND_MISMATCH,
    INVALID_ROUND_STATE,
    INVALID_WAGER_PAYLOAD,
    INVALID_OUTCOME,
    BALANCE_OVERFLOW,
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
        wagerPayload: String? = null
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
        if (before.treats < wagerTreats) {
            return failure(before, activeRound, completedRoundIds, PuppyCasinoTransactionFailure.INSUFFICIENT_TREATS)
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
            state = before.copy(treats = before.treats - wagerTreats),
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
        additionalWagerTreats: Long = 0L
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
        if (before.treats < additionalWagerTreats) {
            return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.INSUFFICIENT_TREATS)
        }
        val totalWager = checkedAdd(round.wagerTreats, additionalWagerTreats)
            ?: return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.BALANCE_OVERFLOW)

        return PuppyCasinoTransactionResult(
            success = true,
            state = before.copy(treats = before.treats - additionalWagerTreats),
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
        roundId: String
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

        val nextTreats = checkedAdd(before.treats, round.payoutTreats)
            ?: return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.BALANCE_OVERFLOW)
        val profit = (round.payoutTreats - round.wagerTreats).coerceAtLeast(0L)
        val nextLifetime = checkedAdd(before.lifetimeTreats, profit)
            ?: return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.BALANCE_OVERFLOW)

        return PuppyCasinoTransactionResult(
            success = true,
            state = before.copy(
                treats = nextTreats,
                lifetimeTreats = nextLifetime
            ),
            activeRound = null,
            completedRoundIds = appendCompleted(completedRoundIds, roundId)
        )
    }

    fun refund(
        before: V6GameState,
        activeRound: PuppyCasinoRound?,
        completedRoundIds: List<String>,
        roundId: String
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

        val nextTreats = checkedAdd(before.treats, round.wagerTreats)
            ?: return failure(before, round, completedRoundIds, PuppyCasinoTransactionFailure.BALANCE_OVERFLOW)

        return PuppyCasinoTransactionResult(
            success = true,
            state = before.copy(treats = nextTreats),
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

internal object PuppyCasinoPersistence {
    private const val SCHEMA_VERSION = 1
    internal const val ACTIVE_ROUND_KEY = "casino_active_round_v1"
    internal const val COMPLETED_ROUND_IDS_KEY = "casino_completed_round_ids_v1"

    fun loadActiveRound(prefs: SharedPreferences): PuppyCasinoRound? =
        decodeRound(prefs.getString(ACTIVE_ROUND_KEY, null))

    internal fun decodeRoundForValidation(raw: String?): PuppyCasinoRound? =
        decodeRound(raw)

    fun loadCompletedRoundIds(prefs: SharedPreferences): List<String> =
        decodeCompletedIds(prefs.getString(COMPLETED_ROUND_IDS_KEY, null))

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

    private fun decodeCompletedIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val id = array.optString(index, "")
                    if (
                        PuppyCasinoTransactionEngine.isValidRoundId(id) &&
                        id !in this
                    ) {
                        add(id)
                    }
                }
            }.takeLast(PuppyCasinoTransactionEngine.MAX_COMPLETED_ROUND_IDS)
        }.getOrDefault(emptyList())
    }
}
