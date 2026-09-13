package com.harleytg.puppyclicker

import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

internal enum class PuppyScratcherPrize(
    val emoji: String,
    val label: String,
    val weightPercent: Int,
    val multiplierHundredths: Int
) {
    NO_PRIZE("🐾", "No prize", 60, 0),
    REFUND("🍪", "Treat Refund", 22, 100),
    DOUBLE("🦴", "Double Treats", 12, 200),
    BIG_WIN("⭐", "Big Win", 5, 500),
    JACKPOT("💎", "Jackpot", 1, 2_000)
}

internal data class PuppyScratcherOutcome(
    val prize: PuppyScratcherPrize,
    val symbols: List<String>,
    val payoutTreats: Long
)

internal enum class PuppyScratcherStartFailure {
    INVALID_WAGER,
    TRANSACTION_REJECTED,
    OUTCOME_GENERATION_FAILED,
    OUTCOME_COMMIT_FAILED
}

internal data class PuppyScratcherStartResult(
    val success: Boolean,
    val roundId: String? = null,
    val outcome: PuppyScratcherOutcome? = null,
    val failure: PuppyScratcherStartFailure? = null,
    val transactionFailure: PuppyCasinoTransactionFailure? = null
)

internal object PuppyScratchersEngine {
    const val MIN_WAGER_TREATS = 20L
    const val MAX_WAGER_TREATS = 100_000L
    const val WAGER_INCREMENT_TREATS = 20L
    const val PUBLISHED_RTP_PERCENT = "91%"
    const val REVEAL_THRESHOLD = 0.55f

    val wagerPresets: List<Long> = listOf(20L, 100L, 500L, 2_500L)

    fun isValidWager(wagerTreats: Long): Boolean =
        wagerTreats in MIN_WAGER_TREATS..MAX_WAGER_TREATS &&
            wagerTreats % WAGER_INCREMENT_TREATS == 0L

    fun randomCard(
        wagerTreats: Long,
        random: SecureRandom = SecureRandom()
    ): PuppyScratcherOutcome {
        require(isValidWager(wagerTreats))
        val roll = random.nextInt(100)
        var cursor = 0
        val prize = PuppyScratcherPrize.entries.first { candidate ->
            cursor += candidate.weightPercent
            roll < cursor
        }
        val symbols = if (prize == PuppyScratcherPrize.NO_PRIZE) {
            listOf("🐾", "🍪", "🦴")
        } else {
            List(3) { prize.emoji }
        }
        return outcomeFor(wagerTreats, prize, symbols)
    }

    fun outcomeFor(
        wagerTreats: Long,
        prize: PuppyScratcherPrize,
        symbols: List<String>
    ): PuppyScratcherOutcome {
        require(isValidWager(wagerTreats))
        require(symbols.size == 3)
        val payout = wagerTreats * prize.multiplierHundredths.toLong() / 100L
        return PuppyScratcherOutcome(prize, symbols, payout)
    }
}

internal object PuppyScratcherOutcomeCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(outcome: PuppyScratcherOutcome): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("prize", outcome.prize.name)
        put("symbols", JSONArray().apply { outcome.symbols.forEach(::put) })
        put("payoutTreats", outcome.payoutTreats)
    }.toString()

    fun decodeAndValidate(raw: String?, wagerTreats: Long): PuppyScratcherOutcome? {
        if (raw.isNullOrBlank() || !PuppyScratchersEngine.isValidWager(wagerTreats)) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.getInt("schemaVersion") != SCHEMA_VERSION) return@runCatching null
            val prize = PuppyScratcherPrize.valueOf(json.getString("prize"))
            val array = json.getJSONArray("symbols")
            if (array.length() != 3) return@runCatching null
            val symbols = List(3) { index -> array.getString(index) }
            val expectedSymbols = if (prize == PuppyScratcherPrize.NO_PRIZE) {
                listOf("🐾", "🍪", "🦴")
            } else {
                List(3) { prize.emoji }
            }
            if (symbols != expectedSymbols) return@runCatching null
            val expected = PuppyScratchersEngine.outcomeFor(wagerTreats, prize, symbols)
            if (json.getLong("payoutTreats") != expected.payoutTreats) return@runCatching null
            expected
        }.getOrNull()
    }
}
