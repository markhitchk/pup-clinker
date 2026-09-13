package com.harleytg.puppyclicker

import android.content.SharedPreferences
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

internal enum class PuppyCasinoPuppyRewardStatus {
    NOT_ELIGIBLE,
    NO_DROP,
    UNLOCKED,
    DAILY_CAP_REACHED,
    ALL_ELIGIBLE_OWNED,
    ALREADY_EVALUATED
}

internal data class PuppyCasinoPuppyReward(
    val status: PuppyCasinoPuppyRewardStatus,
    val styleId: String? = null,
    val dropChanceBasisPoints: Int = 0
)

internal data class PuppyCasinoPuppyRewardLedger(
    val evaluatedRoundIds: List<String> = emptyList(),
    val dailyEpochDay: Long = LocalDate.now().toEpochDay(),
    val dailyPuppyUnlocks: Int = 0
)

internal data class PuppyCasinoPuppyRewardApplication(
    val state: V6GameState,
    val ledger: PuppyCasinoPuppyRewardLedger,
    val reward: PuppyCasinoPuppyReward
)

internal object PuppyCasinoPuppyRewardEngine {
    const val POLICY_VERSION = 1
    const val MAX_DAILY_CASINO_PUPPIES = 1
    const val MAX_EVALUATED_ROUND_IDS = 512

    /**
     * Exact Android save IDs from the current V1/V2 compatibility catalogue.
     *
     * Casino rewards intentionally exclude:
     * - default/free puppies: classic, golden, poodle, spotty
     * - seasonal/event puppies: halloween, santa, birthday
     * - developer/secret/tribute puppies: dev_pup, secret_snoot, classic_forever
     * - branded/special V2 puppies: v2_harleytg, v2_dev_pup
     *
     * This prevents casino rewards from taking over existing special unlock paths.
     */
    val eligibleStyleIds: List<String> = listOf(
        "midnight",
        "cloud",
        "aurora",
        "cocoa",
        "snowball",
        "galaxy",
        "neon_buddy",
        "golden_night",
        "v2_frost",
        "v2_honey",
        "v2_biscuit",
        "v2_onyx",
        "v2_domino",
        "v2_chestnut",
        "v2_prism",
        "v2_flurry"
    )

    val eligibleStyles: List<PuppyStyle> by lazy {
        val byId = V6_PUPPY_STYLES.associateBy { it.id }
        eligibleStyleIds.map { id ->
            requireNotNull(byId[id]) { "Casino puppy ID is missing from V6 roster: $id" }
        }.also { styles ->
            require(styles.all { it.redeemOnly }) {
                "Casino puppy pool must contain redeem-only roster entries"
            }
        }
    }

    fun apply(
        before: V6GameState,
        settledRound: PuppyCasinoRound,
        ledger: PuppyCasinoPuppyRewardLedger,
        todayEpochDay: Long = LocalDate.now().toEpochDay()
    ): PuppyCasinoPuppyRewardApplication {
        val normalized = normalizeDay(ledger, todayEpochDay)
        if (settledRound.roundId in normalized.evaluatedRoundIds) {
            return PuppyCasinoPuppyRewardApplication(
                state = before,
                ledger = normalized,
                reward = PuppyCasinoPuppyReward(
                    PuppyCasinoPuppyRewardStatus.ALREADY_EVALUATED
                )
            )
        }

        val evaluated = appendEvaluated(
            normalized.evaluatedRoundIds,
            settledRound.roundId
        )
        val chance = dropChanceBasisPoints(settledRound)
        if (chance <= 0) {
            return PuppyCasinoPuppyRewardApplication(
                state = before,
                ledger = normalized.copy(evaluatedRoundIds = evaluated),
                reward = PuppyCasinoPuppyReward(
                    status = PuppyCasinoPuppyRewardStatus.NOT_ELIGIBLE,
                    dropChanceBasisPoints = 0
                )
            )
        }

        if (normalized.dailyPuppyUnlocks >= MAX_DAILY_CASINO_PUPPIES) {
            return PuppyCasinoPuppyRewardApplication(
                state = before,
                ledger = normalized.copy(evaluatedRoundIds = evaluated),
                reward = PuppyCasinoPuppyReward(
                    status = PuppyCasinoPuppyRewardStatus.DAILY_CAP_REACHED,
                    dropChanceBasisPoints = chance
                )
            )
        }

        val available = eligibleStyleIds.filterNot { it in before.unlockedPuppies }
        if (available.isEmpty()) {
            return PuppyCasinoPuppyRewardApplication(
                state = before,
                ledger = normalized.copy(evaluatedRoundIds = evaluated),
                reward = PuppyCasinoPuppyReward(
                    status = PuppyCasinoPuppyRewardStatus.ALL_ELIGIBLE_OWNED,
                    dropChanceBasisPoints = chance
                )
            )
        }

        val roll = deterministicRoll(
            settledRound.roundId,
            "puppy-drop",
            10_000
        )
        if (roll >= chance) {
            return PuppyCasinoPuppyRewardApplication(
                state = before,
                ledger = normalized.copy(evaluatedRoundIds = evaluated),
                reward = PuppyCasinoPuppyReward(
                    status = PuppyCasinoPuppyRewardStatus.NO_DROP,
                    dropChanceBasisPoints = chance
                )
            )
        }

        val styleId = available[
            deterministicRoll(
                settledRound.roundId,
                "puppy-choice",
                available.size
            )
        ]

        return PuppyCasinoPuppyRewardApplication(
            state = before.copy(
                unlockedPuppies = before.unlockedPuppies + styleId
            ),
            ledger = normalized.copy(
                evaluatedRoundIds = evaluated,
                dailyPuppyUnlocks = normalized.dailyPuppyUnlocks + 1
            ),
            reward = PuppyCasinoPuppyReward(
                status = PuppyCasinoPuppyRewardStatus.UNLOCKED,
                styleId = styleId,
                dropChanceBasisPoints = chance
            )
        )
    }

    /**
     * Casino puppy drops are intentionally much rarer than Ticket drops.
     *
     * Profitable total return:
     * <2x = 0.25%
     * 2x–<5x = 0.50%
     * 5x–<20x = 1.50%
     * 20x–<100x = 5%
     * 100x+ = guaranteed
     */
    fun dropChanceBasisPoints(round: PuppyCasinoRound): Int {
        if (round.wagerTreats <= 0L || round.payoutTreats <= round.wagerTreats) return 0
        val payout = round.payoutTreats
        val wager = round.wagerTreats
        return when {
            ratioAtLeast(payout, wager, 100L) -> 10_000
            ratioAtLeast(payout, wager, 20L) -> 500
            ratioAtLeast(payout, wager, 5L) -> 150
            ratioAtLeast(payout, wager, 2L) -> 50
            else -> 25
        }
    }

    fun deterministicRoll(
        roundId: String,
        purpose: String,
        bound: Int
    ): Int {
        require(bound > 0)
        val input = (
            "puppy-casino-puppy-v" + POLICY_VERSION +
                ":" + purpose + ":" + roundId
            ).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        var value = 0L
        repeat(8) { index ->
            value = (value shl 8) or (digest[index].toLong() and 0xFFL)
        }
        return (value and Long.MAX_VALUE).rem(bound.toLong()).toInt()
    }

    private fun normalizeDay(
        ledger: PuppyCasinoPuppyRewardLedger,
        todayEpochDay: Long
    ): PuppyCasinoPuppyRewardLedger =
        if (ledger.dailyEpochDay == todayEpochDay) {
            ledger
        } else {
            ledger.copy(
                dailyEpochDay = todayEpochDay,
                dailyPuppyUnlocks = 0
            )
        }

    private fun appendEvaluated(existing: List<String>, roundId: String): List<String> =
        (existing.filterNot { it == roundId } + roundId)
            .takeLast(MAX_EVALUATED_ROUND_IDS)

    private fun ratioAtLeast(payout: Long, wager: Long, multiple: Long): Boolean {
        if (wager <= 0L || multiple <= 0L) return false
        if (wager > Long.MAX_VALUE / multiple) return false
        return payout >= wager * multiple
    }
}

internal object PuppyCasinoPuppyRewardPersistence {
    private const val SCHEMA_VERSION = 1
    internal const val LEDGER_KEY = "casino_puppy_reward_ledger_v1"

    fun load(prefs: SharedPreferences): PuppyCasinoPuppyRewardLedger {
        val raw = prefs.getString(LEDGER_KEY, null)
            ?: return PuppyCasinoPuppyRewardLedger()
        return decode(raw) ?: PuppyCasinoPuppyRewardLedger()
    }

    internal fun decodeForValidation(raw: String?): PuppyCasinoPuppyRewardLedger? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.getInt("schemaVersion") != SCHEMA_VERSION) return@runCatching null
            val idsJson = root.getJSONArray("evaluatedRoundIds")
            if (idsJson.length() > PuppyCasinoPuppyRewardEngine.MAX_EVALUATED_ROUND_IDS) {
                return@runCatching null
            }
            val ids = buildList {
                for (index in 0 until idsJson.length()) {
                    val id = idsJson.getString(index)
                    if (!PuppyCasinoTransactionEngine.isValidRoundId(id) || id in this) {
                        return@runCatching null
                    }
                    add(id)
                }
            }
            val dailyUnlocks = root.getInt("dailyPuppyUnlocks")
            if (dailyUnlocks !in 0..PuppyCasinoPuppyRewardEngine.MAX_DAILY_CASINO_PUPPIES) {
                return@runCatching null
            }
            PuppyCasinoPuppyRewardLedger(
                evaluatedRoundIds = ids,
                dailyEpochDay = root.getLong("dailyEpochDay"),
                dailyPuppyUnlocks = dailyUnlocks
            )
        }.getOrNull()
    }

    fun write(
        editor: SharedPreferences.Editor,
        ledger: PuppyCasinoPuppyRewardLedger
    ) {
        editor.putString(LEDGER_KEY, encode(ledger))
    }

    private fun encode(ledger: PuppyCasinoPuppyRewardLedger): String =
        JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("dailyEpochDay", ledger.dailyEpochDay)
            put(
                "dailyPuppyUnlocks",
                ledger.dailyPuppyUnlocks.coerceIn(
                    0,
                    PuppyCasinoPuppyRewardEngine.MAX_DAILY_CASINO_PUPPIES
                )
            )
            put("evaluatedRoundIds", JSONArray().apply {
                ledger.evaluatedRoundIds
                    .distinct()
                    .takeLast(PuppyCasinoPuppyRewardEngine.MAX_EVALUATED_ROUND_IDS)
                    .forEach { id ->
                        if (PuppyCasinoTransactionEngine.isValidRoundId(id)) put(id)
                    }
            })
        }.toString()

    private fun decode(raw: String): PuppyCasinoPuppyRewardLedger? =
        runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
                return@runCatching null
            }

            val idsJson = root.optJSONArray("evaluatedRoundIds") ?: JSONArray()
            val ids = buildList {
                for (index in 0 until idsJson.length()) {
                    val id = idsJson.optString(index, "")
                    if (
                        PuppyCasinoTransactionEngine.isValidRoundId(id) &&
                        id !in this
                    ) {
                        add(id)
                    }
                }
            }.takeLast(PuppyCasinoPuppyRewardEngine.MAX_EVALUATED_ROUND_IDS)

            PuppyCasinoPuppyRewardLedger(
                evaluatedRoundIds = ids,
                dailyEpochDay = root.optLong(
                    "dailyEpochDay",
                    LocalDate.now().toEpochDay()
                ),
                dailyPuppyUnlocks = root.optInt("dailyPuppyUnlocks", 0)
                    .coerceIn(
                        0,
                        PuppyCasinoPuppyRewardEngine.MAX_DAILY_CASINO_PUPPIES
                    )
            )
        }.getOrNull()
}
