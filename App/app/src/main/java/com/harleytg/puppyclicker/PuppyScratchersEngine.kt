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

internal data class PuppyScratcherCardType(
    val id: String,
    val emoji: String,
    val name: String,
    val shortName: String,
    val tagline: String,
    val costTreats: Long,
    val odds: Map<PuppyScratcherPrize, Int>
) {
    init {
        require(odds.keys.containsAll(PuppyScratcherPrize.entries))
        require(odds.values.sum() == 100)
        require(odds.values.all { it >= 0 })
    }

    fun weightFor(prize: PuppyScratcherPrize): Int = odds[prize] ?: 0
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
    const val REVEAL_THRESHOLD = 0.68f

    private val legacyOdds = mapOf(
        PuppyScratcherPrize.NO_PRIZE to 60,
        PuppyScratcherPrize.REFUND to 22,
        PuppyScratcherPrize.DOUBLE to 12,
        PuppyScratcherPrize.BIG_WIN to 5,
        PuppyScratcherPrize.JACKPOT to 1
    )

    val cardTypes: List<PuppyScratcherCardType> = listOf(
        PuppyScratcherCardType(
            id = "pup_scratch",
            emoji = "🐾",
            name = "Pup Scratch",
            shortName = "Pup",
            tagline = "The classic Puppy Casino scratcher.",
            costTreats = 20L,
            odds = legacyOdds
        ),
        PuppyScratcherCardType(
            id = "bone_bonanza",
            emoji = "🦴",
            name = "Bone Bonanza",
            shortName = "Bone",
            tagline = "More double-Treat hits with a lean prize table.",
            costTreats = 100L,
            odds = mapOf(
                PuppyScratcherPrize.NO_PRIZE to 58,
                PuppyScratcherPrize.REFUND to 14,
                PuppyScratcherPrize.DOUBLE to 26,
                PuppyScratcherPrize.BIG_WIN to 1,
                PuppyScratcherPrize.JACKPOT to 1
            )
        ),
        PuppyScratcherCardType(
            id = "lucky_fetch",
            emoji = "🎾",
            name = "Lucky Fetch",
            shortName = "Fetch",
            tagline = "A balanced mid-tier card for bigger Treat stacks.",
            costTreats = 500L,
            odds = mapOf(
                PuppyScratcherPrize.NO_PRIZE to 55,
                PuppyScratcherPrize.REFUND to 20,
                PuppyScratcherPrize.DOUBLE to 23,
                PuppyScratcherPrize.BIG_WIN to 1,
                PuppyScratcherPrize.JACKPOT to 1
            )
        ),
        PuppyScratcherCardType(
            id = "golden_paw",
            emoji = "🌟",
            name = "Golden Paw",
            shortName = "Gold",
            tagline = "Higher stakes with more refund protection.",
            costTreats = 2_500L,
            odds = mapOf(
                PuppyScratcherPrize.NO_PRIZE to 52,
                PuppyScratcherPrize.REFUND to 26,
                PuppyScratcherPrize.DOUBLE to 20,
                PuppyScratcherPrize.BIG_WIN to 1,
                PuppyScratcherPrize.JACKPOT to 1
            )
        ),
        PuppyScratcherCardType(
            id = "casino_scratch",
            emoji = "🎰",
            name = "Casino Scratch",
            shortName = "Casino",
            tagline = "A high-stakes card with a boosted jackpot chance.",
            costTreats = 10_000L,
            odds = mapOf(
                PuppyScratcherPrize.NO_PRIZE to 58,
                PuppyScratcherPrize.REFUND to 32,
                PuppyScratcherPrize.DOUBLE to 7,
                PuppyScratcherPrize.BIG_WIN to 1,
                PuppyScratcherPrize.JACKPOT to 2
            )
        ),
        PuppyScratcherCardType(
            id = "ultra_pup",
            emoji = "💎",
            name = "Ultra Pup",
            shortName = "Ultra",
            tagline = "The premium scratcher with more big-win and jackpot hits.",
            costTreats = 25_000L,
            odds = mapOf(
                PuppyScratcherPrize.NO_PRIZE to 60,
                PuppyScratcherPrize.REFUND to 31,
                PuppyScratcherPrize.DOUBLE to 5,
                PuppyScratcherPrize.BIG_WIN to 2,
                PuppyScratcherPrize.JACKPOT to 2
            )
        )
    )

    val wagerPresets: List<Long> = cardTypes.map { it.costTreats }

    fun isValidWager(wagerTreats: Long): Boolean =
        wagerTreats in MIN_WAGER_TREATS..MAX_WAGER_TREATS &&
            wagerTreats % WAGER_INCREMENT_TREATS == 0L

    fun cardForWager(wagerTreats: Long): PuppyScratcherCardType? =
        cardTypes.firstOrNull { it.costTreats == wagerTreats }

    fun oddsFor(wagerTreats: Long): Map<PuppyScratcherPrize, Int> =
        cardForWager(wagerTreats)?.odds ?: legacyOdds

    fun randomCard(
        wagerTreats: Long,
        random: SecureRandom = SecureRandom()
    ): PuppyScratcherOutcome {
        require(isValidWager(wagerTreats))
        val odds = oddsFor(wagerTreats)
        val roll = random.nextInt(100)
        var cursor = 0
        val prize = PuppyScratcherPrize.entries.first { candidate ->
            cursor += odds[candidate] ?: 0
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
