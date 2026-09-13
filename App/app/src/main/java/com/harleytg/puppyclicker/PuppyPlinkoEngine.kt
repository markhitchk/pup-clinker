package com.harleytg.puppyclicker

import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

internal data class PuppyPlinkoOutcome(
    val pathRight: List<Boolean>,
    val binIndex: Int,
    val multiplierHundredths: Int,
    val payoutTreats: Long
) {
    val multiplierLabel: String
        get() = if (multiplierHundredths % 100 == 0) {
            (multiplierHundredths / 100).toString() + "×"
        } else {
            (multiplierHundredths / 100.0).toString().trimEnd('0').trimEnd('.') + "×"
        }
}

internal enum class PuppyPlinkoStartFailure {
    INVALID_WAGER,
    TRANSACTION_REJECTED,
    OUTCOME_GENERATION_FAILED,
    OUTCOME_COMMIT_FAILED
}

internal data class PuppyPlinkoStartResult(
    val success: Boolean,
    val roundId: String? = null,
    val outcome: PuppyPlinkoOutcome? = null,
    val failure: PuppyPlinkoStartFailure? = null,
    val transactionFailure: PuppyCasinoTransactionFailure? = null
)

internal object PuppyPlinkoEngine {
    const val ROWS = 8
    const val MIN_WAGER_TREATS = 20L
    const val MAX_WAGER_TREATS = 100_000L
    const val WAGER_INCREMENT_TREATS = 20L
    const val PUBLISHED_RTP_PERCENT = "97.3%"

    val wagerPresets: List<Long> = listOf(20L, 100L, 500L, 2_500L)

    // Binomial 8-row board. The visual board and the math use this exact table.
    val binMultiplierHundredths: List<Int> =
        listOf(5, 25, 50, 100, 150, 100, 50, 25, 5)

    fun isValidWager(wagerTreats: Long): Boolean =
        wagerTreats in MIN_WAGER_TREATS..MAX_WAGER_TREATS &&
            wagerTreats % WAGER_INCREMENT_TREATS == 0L

    fun randomDrop(
        wagerTreats: Long,
        random: SecureRandom = SecureRandom()
    ): PuppyPlinkoOutcome {
        require(isValidWager(wagerTreats))
        val path = List(ROWS) { random.nextBoolean() }
        return outcomeForPath(wagerTreats, path)
    }

    fun outcomeForPath(
        wagerTreats: Long,
        pathRight: List<Boolean>
    ): PuppyPlinkoOutcome {
        require(isValidWager(wagerTreats))
        require(pathRight.size == ROWS)
        val binIndex = pathRight.count { it }
        val multiplier = binMultiplierHundredths[binIndex]
        val payout = wagerTreats * multiplier.toLong() / 100L
        return PuppyPlinkoOutcome(pathRight, binIndex, multiplier, payout)
    }
}

internal object PuppyPlinkoOutcomeCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(outcome: PuppyPlinkoOutcome): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("pathRight", JSONArray().apply { outcome.pathRight.forEach(::put) })
        put("binIndex", outcome.binIndex)
        put("multiplierHundredths", outcome.multiplierHundredths)
        put("payoutTreats", outcome.payoutTreats)
    }.toString()

    fun decodeAndValidate(raw: String?, wagerTreats: Long): PuppyPlinkoOutcome? {
        if (raw.isNullOrBlank() || !PuppyPlinkoEngine.isValidWager(wagerTreats)) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.getInt("schemaVersion") != SCHEMA_VERSION) return@runCatching null
            val array = json.getJSONArray("pathRight")
            if (array.length() != PuppyPlinkoEngine.ROWS) return@runCatching null
            val path = List(array.length()) { index -> array.getBoolean(index) }
            val expected = PuppyPlinkoEngine.outcomeForPath(wagerTreats, path)
            if (json.getInt("binIndex") != expected.binIndex) return@runCatching null
            if (json.getInt("multiplierHundredths") != expected.multiplierHundredths) return@runCatching null
            if (json.getLong("payoutTreats") != expected.payoutTreats) return@runCatching null
            expected
        }.getOrNull()
    }
}
