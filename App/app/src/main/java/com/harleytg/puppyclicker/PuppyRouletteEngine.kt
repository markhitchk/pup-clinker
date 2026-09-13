package com.harleytg.puppyclicker

import java.security.SecureRandom
import org.json.JSONObject

internal enum class PuppyRouletteColor {
    GREEN,
    RED,
    BLACK
}

internal enum class PuppyRouletteBetType(
    val label: String,
    val totalReturnMultiplier: Int
) {
    STRAIGHT("Straight number", 36),
    RED("Red", 2),
    BLACK("Black", 2),
    ODD("Odd", 2),
    EVEN("Even", 2),
    LOW("1–18", 2),
    HIGH("19–36", 2)
}

internal data class PuppyRouletteBet(
    val type: PuppyRouletteBetType,
    val number: Int? = null
) {
    init {
        when (type) {
            PuppyRouletteBetType.STRAIGHT -> require(number != null && number in 0..36)
            else -> require(number == null)
        }
    }

    val label: String
        get() = if (type == PuppyRouletteBetType.STRAIGHT) {
            "Number " + number
        } else {
            type.label
        }
}

internal data class PuppyRouletteOutcome(
    val winningNumber: Int,
    val color: PuppyRouletteColor,
    val bet: PuppyRouletteBet,
    val won: Boolean,
    val totalReturnMultiplier: Int,
    val payoutTreats: Long
)

internal enum class PuppyRouletteStartFailure {
    INVALID_WAGER,
    INVALID_BET,
    TRANSACTION_REJECTED,
    OUTCOME_GENERATION_FAILED,
    OUTCOME_COMMIT_FAILED
}

internal data class PuppyRouletteStartResult(
    val success: Boolean,
    val roundId: String? = null,
    val outcome: PuppyRouletteOutcome? = null,
    val failure: PuppyRouletteStartFailure? = null,
    val transactionFailure: PuppyCasinoTransactionFailure? = null
)

internal object PuppyRouletteEngine {
    const val MIN_WAGER_TREATS = 20L
    const val MAX_WAGER_TREATS = 100_000L
    const val WAGER_INCREMENT_TREATS = 10L
    const val PUBLISHED_RTP_PERCENT = "97.30%"

    val wagerPresets: List<Long> = listOf(20L, 100L, 500L, 2_500L)

    private val redNumbers = setOf(
        1, 3, 5, 7, 9,
        12, 14, 16, 18,
        19, 21, 23, 25, 27,
        30, 32, 34, 36
    )

    fun isValidWager(wagerTreats: Long): Boolean =
        wagerTreats in MIN_WAGER_TREATS..MAX_WAGER_TREATS &&
            wagerTreats % WAGER_INCREMENT_TREATS == 0L

    fun isValidBet(bet: PuppyRouletteBet): Boolean = when (bet.type) {
        PuppyRouletteBetType.STRAIGHT -> bet.number != null && bet.number in 0..36
        else -> bet.number == null
    }

    fun randomSpin(
        wagerTreats: Long,
        bet: PuppyRouletteBet,
        random: SecureRandom = SecureRandom()
    ): PuppyRouletteOutcome {
        require(isValidWager(wagerTreats))
        require(isValidBet(bet))
        return spinFromNumber(
            wagerTreats = wagerTreats,
            bet = bet,
            winningNumber = random.nextInt(37)
        )
    }

    fun spinFromNumber(
        wagerTreats: Long,
        bet: PuppyRouletteBet,
        winningNumber: Int
    ): PuppyRouletteOutcome {
        require(isValidWager(wagerTreats))
        require(isValidBet(bet))
        require(winningNumber in 0..36)

        val color = colorOf(winningNumber)
        val won = when (bet.type) {
            PuppyRouletteBetType.STRAIGHT -> winningNumber == bet.number
            PuppyRouletteBetType.RED -> color == PuppyRouletteColor.RED
            PuppyRouletteBetType.BLACK -> color == PuppyRouletteColor.BLACK
            PuppyRouletteBetType.ODD -> winningNumber != 0 && winningNumber % 2 == 1
            PuppyRouletteBetType.EVEN -> winningNumber != 0 && winningNumber % 2 == 0
            PuppyRouletteBetType.LOW -> winningNumber in 1..18
            PuppyRouletteBetType.HIGH -> winningNumber in 19..36
        }

        val multiplier = if (won) bet.type.totalReturnMultiplier else 0
        val payout = checkedMultiply(wagerTreats, multiplier)

        return PuppyRouletteOutcome(
            winningNumber = winningNumber,
            color = color,
            bet = bet,
            won = won,
            totalReturnMultiplier = multiplier,
            payoutTreats = payout
        )
    }

    fun colorOf(number: Int): PuppyRouletteColor {
        require(number in 0..36)
        return when {
            number == 0 -> PuppyRouletteColor.GREEN
            number in redNumbers -> PuppyRouletteColor.RED
            else -> PuppyRouletteColor.BLACK
        }
    }

    private fun checkedMultiply(value: Long, multiplier: Int): Long {
        require(value >= 0L)
        require(multiplier >= 0)
        if (multiplier == 0) return 0L
        require(value <= Long.MAX_VALUE / multiplier.toLong()) {
            "Roulette payout overflow"
        }
        return value * multiplier.toLong()
    }
}

internal object PuppyRouletteBetCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(bet: PuppyRouletteBet): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("game", PuppyCasinoGame.ROULETTE.name)
        put("type", bet.type.name)
        bet.number?.let { put("number", it) }
    }.toString()

    fun decodeAndValidate(raw: String?): PuppyRouletteBet? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return@runCatching null
            if (root.optString("game") != PuppyCasinoGame.ROULETTE.name) return@runCatching null
            val type = PuppyRouletteBetType.valueOf(root.getString("type"))
            val number = if (root.has("number")) root.getInt("number") else null
            PuppyRouletteBet(type = type, number = number)
        }.getOrNull()
    }
}

internal object PuppyRouletteOutcomeCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(outcome: PuppyRouletteOutcome): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("game", PuppyCasinoGame.ROULETTE.name)
        put("winningNumber", outcome.winningNumber)
        put("color", outcome.color.name)
        put("betType", outcome.bet.type.name)
        outcome.bet.number?.let { put("betNumber", it) }
        put("won", outcome.won)
        put("totalReturnMultiplier", outcome.totalReturnMultiplier)
        put("payoutTreats", outcome.payoutTreats)
    }.toString()

    fun decodeAndValidate(
        raw: String?,
        wagerTreats: Long,
        expectedBet: PuppyRouletteBet
    ): PuppyRouletteOutcome? {
        if (
            raw.isNullOrBlank() ||
            !PuppyRouletteEngine.isValidWager(wagerTreats) ||
            !PuppyRouletteEngine.isValidBet(expectedBet)
        ) {
            return null
        }

        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return@runCatching null
            if (root.optString("game") != PuppyCasinoGame.ROULETTE.name) return@runCatching null

            val savedBetType = PuppyRouletteBetType.valueOf(root.getString("betType"))
            val savedBetNumber = if (root.has("betNumber")) root.getInt("betNumber") else null
            val savedBet = PuppyRouletteBet(savedBetType, savedBetNumber)
            if (savedBet != expectedBet) return@runCatching null

            val winningNumber = root.getInt("winningNumber")
            val recomputed = PuppyRouletteEngine.spinFromNumber(
                wagerTreats = wagerTreats,
                bet = expectedBet,
                winningNumber = winningNumber
            )

            if (root.optString("color") != recomputed.color.name) return@runCatching null
            if (root.optBoolean("won", !recomputed.won) != recomputed.won) return@runCatching null
            if (
                root.optInt("totalReturnMultiplier", -1) !=
                    recomputed.totalReturnMultiplier
            ) {
                return@runCatching null
            }
            if (root.optLong("payoutTreats", -1L) != recomputed.payoutTreats) {
                return@runCatching null
            }

            recomputed
        }.getOrNull()
    }
}
