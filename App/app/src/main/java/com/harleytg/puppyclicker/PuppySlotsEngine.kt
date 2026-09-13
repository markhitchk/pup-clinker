package com.harleytg.puppyclicker

import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

internal enum class PuppySlotSymbol(
    val label: String,
    val emoji: String,
    val weightPercent: Int,
    val tripleReturnMultiplier: Int
) {
    TREAT("Treat", "🍪", 30, 7),
    BALL("Ball", "🎾", 25, 12),
    PAW("Paw", "🐾", 20, 18),
    TICKET("Ticket", "🎟️", 13, 35),
    PUPPY("Puppy", "🐶", 8, 100),
    STAR("Star", "⭐", 4, 500)
}

internal enum class PuppySlotsWinKind {
    LOSS,
    PAIR,
    TRIPLE
}

internal data class PuppySlotsOutcome(
    val symbols: List<PuppySlotSymbol>,
    val winKind: PuppySlotsWinKind,
    val returnNumerator: Int,
    val returnDenominator: Int,
    val payoutTreats: Long
) {
    init {
        require(symbols.size == PuppySlotsEngine.REEL_COUNT)
        require(returnNumerator >= 0)
        require(returnDenominator > 0)
        require(payoutTreats >= 0L)
    }

    val multiplierLabel: String
        get() = when {
            returnNumerator == 0 -> "0×"
            returnDenominator == 1 -> returnNumerator.toString() + "×"
            returnNumerator == 1 && returnDenominator == 2 -> "0.5×"
            else -> returnNumerator.toString() + "/" + returnDenominator + "×"
        }
}

internal data class PuppySlotsPayoutRule(
    val label: String,
    val returnNumerator: Int,
    val returnDenominator: Int
) {
    val multiplierLabel: String
        get() = when {
            returnDenominator == 1 -> returnNumerator.toString() + "×"
            returnNumerator == 1 && returnDenominator == 2 -> "0.5×"
            else -> returnNumerator.toString() + "/" + returnDenominator + "×"
        }
}

internal enum class PuppySlotsStartFailure {
    INVALID_WAGER,
    TRANSACTION_REJECTED,
    OUTCOME_GENERATION_FAILED,
    OUTCOME_COMMIT_FAILED
}

internal data class PuppySlotsStartResult(
    val success: Boolean,
    val roundId: String? = null,
    val outcome: PuppySlotsOutcome? = null,
    val failure: PuppySlotsStartFailure? = null,
    val transactionFailure: PuppyCasinoTransactionFailure? = null
)

internal object PuppySlotsEngine {
    const val REEL_COUNT = 3
    const val MIN_WAGER_TREATS = 20L
    const val MAX_WAGER_TREATS = 100_000L
    const val WAGER_INCREMENT_TREATS = 10L
    const val PUBLISHED_RTP_PERCENT = "92.66%"

    val wagerPresets: List<Long> = listOf(20L, 100L, 500L, 2_500L)

    val payoutTable: List<PuppySlotsPayoutRule> =
        listOf(PuppySlotsPayoutRule("Any matching pair", 1, 2)) +
            PuppySlotSymbol.entries.map { symbol ->
                PuppySlotsPayoutRule(
                    label = "3× " + symbol.emoji + " " + symbol.label,
                    returnNumerator = symbol.tripleReturnMultiplier,
                    returnDenominator = 1
                )
            }

    private val totalWeight = PuppySlotSymbol.entries.sumOf { it.weightPercent }

    init {
        require(totalWeight == 100)
        require(PuppySlotSymbol.entries.all { it.weightPercent > 0 })
    }

    fun isValidWager(wagerTreats: Long): Boolean =
        wagerTreats in MIN_WAGER_TREATS..MAX_WAGER_TREATS &&
            wagerTreats % WAGER_INCREMENT_TREATS == 0L

    fun randomSpin(
        wagerTreats: Long,
        random: SecureRandom = SecureRandom()
    ): PuppySlotsOutcome {
        require(isValidWager(wagerTreats))
        val rolls = IntArray(REEL_COUNT) { random.nextInt(totalWeight) }
        return spinFromRolls(wagerTreats, rolls)
    }

    /**
     * Deterministic entry point used by tests and recovery validation.
     * Each roll is 0..99 and maps through the published symbol weights.
     */
    fun spinFromRolls(
        wagerTreats: Long,
        rolls: IntArray
    ): PuppySlotsOutcome {
        require(isValidWager(wagerTreats))
        require(rolls.size == REEL_COUNT)
        val symbols = rolls.map(::symbolForRoll)
        return evaluate(wagerTreats, symbols)
    }

    fun evaluate(
        wagerTreats: Long,
        symbols: List<PuppySlotSymbol>
    ): PuppySlotsOutcome {
        require(isValidWager(wagerTreats))
        require(symbols.size == REEL_COUNT)

        val distinct = symbols.distinct()
        val rule = when (distinct.size) {
            1 -> {
                val symbol = symbols.first()
                TripleRule(symbol.tripleReturnMultiplier, 1)
            }
            2 -> TripleRule(1, 2)
            else -> TripleRule(0, 1)
        }

        val kind = when (distinct.size) {
            1 -> PuppySlotsWinKind.TRIPLE
            2 -> PuppySlotsWinKind.PAIR
            else -> PuppySlotsWinKind.LOSS
        }

        val payout = multiplyRatioChecked(
            value = wagerTreats,
            numerator = rule.numerator,
            denominator = rule.denominator
        )

        return PuppySlotsOutcome(
            symbols = symbols,
            winKind = kind,
            returnNumerator = rule.numerator,
            returnDenominator = rule.denominator,
            payoutTreats = payout
        )
    }

    fun symbolForRoll(roll: Int): PuppySlotSymbol {
        require(roll in 0 until totalWeight)
        var cursor = 0
        PuppySlotSymbol.entries.forEach { symbol ->
            cursor += symbol.weightPercent
            if (roll < cursor) return symbol
        }
        error("Slot roll was not mapped")
    }

    private fun multiplyRatioChecked(
        value: Long,
        numerator: Int,
        denominator: Int
    ): Long {
        require(value >= 0L)
        require(numerator >= 0)
        require(denominator > 0)
        if (numerator == 0) return 0L
        val maxBeforeMultiply = Long.MAX_VALUE / numerator.toLong()
        require(value <= maxBeforeMultiply) { "Slot payout overflow" }
        return (value * numerator.toLong()) / denominator.toLong()
    }

    private data class TripleRule(val numerator: Int, val denominator: Int)
}

internal object PuppySlotsOutcomeCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(outcome: PuppySlotsOutcome): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("game", PuppyCasinoGame.SLOTS.name)
        put("symbols", JSONArray().apply {
            outcome.symbols.forEach { put(it.name) }
        })
        put("winKind", outcome.winKind.name)
        put("returnNumerator", outcome.returnNumerator)
        put("returnDenominator", outcome.returnDenominator)
        put("payoutTreats", outcome.payoutTreats)
    }.toString()

    /**
     * Decoding always recomputes the payout from the saved symbols and original wager.
     * A payload whose stored payout/multiplier does not match the published table is rejected.
     */
    fun decodeAndValidate(
        raw: String?,
        wagerTreats: Long
    ): PuppySlotsOutcome? {
        if (raw.isNullOrBlank() || !PuppySlotsEngine.isValidWager(wagerTreats)) return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return@runCatching null
            if (root.optString("game") != PuppyCasinoGame.SLOTS.name) return@runCatching null

            val symbolsJson = root.getJSONArray("symbols")
            if (symbolsJson.length() != PuppySlotsEngine.REEL_COUNT) return@runCatching null
            val symbols = buildList {
                for (index in 0 until symbolsJson.length()) {
                    add(PuppySlotSymbol.valueOf(symbolsJson.getString(index)))
                }
            }

            val recomputed = PuppySlotsEngine.evaluate(wagerTreats, symbols)
            if (root.optString("winKind") != recomputed.winKind.name) return@runCatching null
            if (root.optInt("returnNumerator", -1) != recomputed.returnNumerator) return@runCatching null
            if (root.optInt("returnDenominator", -1) != recomputed.returnDenominator) return@runCatching null
            if (root.optLong("payoutTreats", -1L) != recomputed.payoutTreats) return@runCatching null

            recomputed
        }.getOrNull()
    }
}
