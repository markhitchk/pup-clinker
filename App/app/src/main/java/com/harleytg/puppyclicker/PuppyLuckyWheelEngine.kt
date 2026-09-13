package com.harleytg.puppyclicker

import java.security.SecureRandom
import org.json.JSONObject

internal enum class LuckyPupWheelPrize(
    val emoji: String,
    val label: String,
    val weightPercent: Int,
    val multiplierHundredths: Int
) {
    MISS("🐾", "No Treat Prize", 38, 0),
    HALF("🍪", "0.5× Treats", 25, 50),
    REFUND("🦴", "1× Treats", 20, 100),
    DOUBLE("⭐", "2× Treats", 10, 200),
    FIVE_X("🌟", "5× Treats", 4, 500),
    TEN_X("💎", "10× Treats", 1, 1_000),
    PUPPY_UNLOCK("🐶", "Pup Unlock", 2, 0)
}

internal data class LuckyPupWheelOutcome(
    val segmentIndex: Int,
    val prize: LuckyPupWheelPrize,
    val payoutTreats: Long,
    val puppyStyleId: String? = null
)

internal enum class LuckyPupWheelStartFailure {
    INVALID_WAGER,
    TRANSACTION_REJECTED,
    OUTCOME_GENERATION_FAILED,
    OUTCOME_COMMIT_FAILED
}

internal data class LuckyPupWheelStartResult(
    val success: Boolean,
    val roundId: String? = null,
    val outcome: LuckyPupWheelOutcome? = null,
    val failure: LuckyPupWheelStartFailure? = null,
    val transactionFailure: PuppyCasinoTransactionFailure? = null
)

internal object PuppyLuckyWheelEngine {
    const val MIN_WAGER_TREATS = 20L
    const val MAX_WAGER_TREATS = 100_000L
    const val WAGER_INCREMENT_TREATS = 20L
    const val PUBLISHED_TREAT_RTP_PERCENT = "82.5%"

    val wagerPresets: List<Long> = listOf(20L, 100L, 500L, 2_500L)

    init {
        require(LuckyPupWheelPrize.entries.sumOf { it.weightPercent } == 100)
    }

    fun isValidWager(wagerTreats: Long): Boolean =
        wagerTreats in MIN_WAGER_TREATS..MAX_WAGER_TREATS &&
            wagerTreats % WAGER_INCREMENT_TREATS == 0L

    fun randomSpin(
        wagerTreats: Long,
        availablePuppyStyleIds: List<String>,
        puppyUnlockAllowed: Boolean,
        random: SecureRandom = SecureRandom()
    ): LuckyPupWheelOutcome {
        require(isValidWager(wagerTreats))
        val roll = random.nextInt(100)
        var cursor = 0
        val rolledPrize = LuckyPupWheelPrize.entries.first { prize ->
            cursor += prize.weightPercent
            roll < cursor
        }

        val prize = if (
            rolledPrize == LuckyPupWheelPrize.PUPPY_UNLOCK &&
            (!puppyUnlockAllowed || availablePuppyStyleIds.isEmpty())
        ) {
            // Keep the puppy slice on the published wheel, but never burn a spin:
            // if the daily cap/pool blocks an unlock, return the wager instead.
            LuckyPupWheelPrize.REFUND
        } else {
            rolledPrize
        }

        val puppyStyleId = if (prize == LuckyPupWheelPrize.PUPPY_UNLOCK) {
            availablePuppyStyleIds[random.nextInt(availablePuppyStyleIds.size)]
        } else {
            null
        }
        return outcomeFor(wagerTreats, prize, puppyStyleId)
    }

    fun outcomeFor(
        wagerTreats: Long,
        prize: LuckyPupWheelPrize,
        puppyStyleId: String?
    ): LuckyPupWheelOutcome {
        require(isValidWager(wagerTreats))
        require(
            if (prize == LuckyPupWheelPrize.PUPPY_UNLOCK) {
                puppyStyleId in PuppyCasinoPuppyRewardEngine.eligibleStyleIds
            } else {
                puppyStyleId == null
            }
        )
        val payout = wagerTreats * prize.multiplierHundredths.toLong() / 100L
        return LuckyPupWheelOutcome(
            segmentIndex = LuckyPupWheelPrize.entries.indexOf(prize),
            prize = prize,
            payoutTreats = payout,
            puppyStyleId = puppyStyleId
        )
    }
}

internal object LuckyPupWheelOutcomeCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(outcome: LuckyPupWheelOutcome): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("segmentIndex", outcome.segmentIndex)
        put("prize", outcome.prize.name)
        put("payoutTreats", outcome.payoutTreats)
        outcome.puppyStyleId?.let { put("puppyStyleId", it) }
    }.toString()

    fun decodeAndValidate(raw: String?, wagerTreats: Long): LuckyPupWheelOutcome? {
        if (raw.isNullOrBlank() || !PuppyLuckyWheelEngine.isValidWager(wagerTreats)) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.getInt("schemaVersion") != SCHEMA_VERSION) return@runCatching null
            val prize = LuckyPupWheelPrize.valueOf(json.getString("prize"))
            val styleId = json.optString("puppyStyleId", "").trim().ifBlank { null }
            val expected = PuppyLuckyWheelEngine.outcomeFor(wagerTreats, prize, styleId)
            if (json.getInt("segmentIndex") != expected.segmentIndex) return@runCatching null
            if (json.getLong("payoutTreats") != expected.payoutTreats) return@runCatching null
            expected
        }.getOrNull()
    }
}
