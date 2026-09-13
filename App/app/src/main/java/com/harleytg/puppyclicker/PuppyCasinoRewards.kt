package com.harleytg.puppyclicker

import android.content.SharedPreferences
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

internal enum class PuppyCasinoRewardStatus {
    NOT_ELIGIBLE,
    NO_DROP,
    AWARDED,
    DAILY_CAP_REACHED,
    INVENTORY_CAP_REACHED,
    ALREADY_EVALUATED
}

internal data class PuppyCasinoTicketReward(
    val status: PuppyCasinoRewardStatus,
    val rarity: TicketRarity? = null,
    val dropChanceBasisPoints: Int = 0
)

internal data class PuppyCasinoRewardLedger(
    val evaluatedRoundIds: List<String> = emptyList(),
    val dailyEpochDay: Long = LocalDate.now().toEpochDay(),
    val dailyTicketAwards: Int = 0
)

internal data class PuppyCasinoRewardApplication(
    val state: V6GameState,
    val ledger: PuppyCasinoRewardLedger,
    val reward: PuppyCasinoTicketReward
)

internal object PuppyCasinoRewardEngine {
    const val POLICY_VERSION = 1
    const val MAX_DAILY_CASINO_TICKETS = 10
    const val MAX_TICKETS_PER_ROUND = 1
    const val MAX_EVALUATED_ROUND_IDS = 512
    const val MAX_TICKETS_PER_RARITY = 9_999

    fun apply(
        before: V6GameState,
        settledRound: PuppyCasinoRound,
        ledger: PuppyCasinoRewardLedger,
        todayEpochDay: Long = LocalDate.now().toEpochDay()
    ): PuppyCasinoRewardApplication {
        val normalizedLedger = normalizeDay(ledger, todayEpochDay)
        if (settledRound.roundId in normalizedLedger.evaluatedRoundIds) {
            return PuppyCasinoRewardApplication(
                state = before,
                ledger = normalizedLedger,
                reward = PuppyCasinoTicketReward(PuppyCasinoRewardStatus.ALREADY_EVALUATED)
            )
        }

        val updatedEvaluated = appendEvaluated(
            normalizedLedger.evaluatedRoundIds,
            settledRound.roundId
        )
        val profit = settledRound.payoutTreats - settledRound.wagerTreats
        if (profit <= 0L) {
            return PuppyCasinoRewardApplication(
                state = before,
                ledger = normalizedLedger.copy(evaluatedRoundIds = updatedEvaluated),
                reward = PuppyCasinoTicketReward(PuppyCasinoRewardStatus.NOT_ELIGIBLE)
            )
        }

        val chance = dropChanceBasisPoints(settledRound)
        val dropRoll = deterministicRoll(
            roundId = settledRound.roundId,
            purpose = "ticket-drop",
            bound = 10_000
        )
        if (dropRoll >= chance) {
            return PuppyCasinoRewardApplication(
                state = before,
                ledger = normalizedLedger.copy(evaluatedRoundIds = updatedEvaluated),
                reward = PuppyCasinoTicketReward(
                    status = PuppyCasinoRewardStatus.NO_DROP,
                    dropChanceBasisPoints = chance
                )
            )
        }

        if (normalizedLedger.dailyTicketAwards >= MAX_DAILY_CASINO_TICKETS) {
            return PuppyCasinoRewardApplication(
                state = before,
                ledger = normalizedLedger.copy(evaluatedRoundIds = updatedEvaluated),
                reward = PuppyCasinoTicketReward(
                    status = PuppyCasinoRewardStatus.DAILY_CAP_REACHED,
                    dropChanceBasisPoints = chance
                )
            )
        }

        val rarity = deterministicRarity(settledRound.roundId)
        val currentCount = before.ticketInventory[rarity] ?: 0
        if (currentCount >= MAX_TICKETS_PER_RARITY) {
            return PuppyCasinoRewardApplication(
                state = before,
                ledger = normalizedLedger.copy(evaluatedRoundIds = updatedEvaluated),
                reward = PuppyCasinoTicketReward(
                    status = PuppyCasinoRewardStatus.INVENTORY_CAP_REACHED,
                    rarity = rarity,
                    dropChanceBasisPoints = chance
                )
            )
        }

        val nextInventory = before.ticketInventory.toMutableMap().apply {
            this[rarity] = (currentCount + MAX_TICKETS_PER_ROUND)
                .coerceAtMost(MAX_TICKETS_PER_RARITY)
        }

        return PuppyCasinoRewardApplication(
            state = before.copy(
                ticketInventory = nextInventory,
                lastTicketDrop = rarity,
                ticketDropSerial = safeIncrement(before.ticketDropSerial),
                totalTicketsFound = safeIncrement(before.totalTicketsFound)
            ),
            ledger = normalizedLedger.copy(
                evaluatedRoundIds = updatedEvaluated,
                dailyTicketAwards = normalizedLedger.dailyTicketAwards + 1
            ),
            reward = PuppyCasinoTicketReward(
                status = PuppyCasinoRewardStatus.AWARDED,
                rarity = rarity,
                dropChanceBasisPoints = chance
            )
        )
    }

    /**
     * Chance is based on total return relative to the wager:
     * profitable <2x = 5%, 2x–<5x = 10%, 5x–<20x = 20%,
     * 20x–<100x = 35%, and 100x+ = guaranteed.
     */
    fun dropChanceBasisPoints(round: PuppyCasinoRound): Int {
        if (round.wagerTreats <= 0L || round.payoutTreats <= round.wagerTreats) return 0
        val payout = round.payoutTreats
        val wager = round.wagerTreats
        return when {
            ratioAtLeast(payout, wager, 100L) -> 10_000
            ratioAtLeast(payout, wager, 20L) -> 3_500
            ratioAtLeast(payout, wager, 5L) -> 2_000
            ratioAtLeast(payout, wager, 2L) -> 1_000
            else -> 500
        }
    }

    fun deterministicRarity(roundId: String): TicketRarity {
        val roll = deterministicRoll(roundId, "ticket-rarity", 100)
        var cursor = 0
        TicketRarity.entries.forEach { rarity ->
            cursor += rarity.rarityWeight
            if (roll < cursor) return rarity
        }
        return TicketRarity.COMMON
    }

    fun deterministicRoll(
        roundId: String,
        purpose: String,
        bound: Int
    ): Int {
        require(bound > 0)
        val input = (
            "puppy-casino-reward-v" + POLICY_VERSION +
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
        ledger: PuppyCasinoRewardLedger,
        todayEpochDay: Long
    ): PuppyCasinoRewardLedger =
        if (ledger.dailyEpochDay == todayEpochDay) {
            ledger
        } else {
            ledger.copy(
                dailyEpochDay = todayEpochDay,
                dailyTicketAwards = 0
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

    private fun safeIncrement(value: Long): Long =
        if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1L
}

internal object PuppyCasinoRewardPersistence {
    private const val SCHEMA_VERSION = 1
    private const val LEDGER_KEY = "casino_ticket_reward_ledger_v1"

    fun load(prefs: SharedPreferences): PuppyCasinoRewardLedger {
        val raw = prefs.getString(LEDGER_KEY, null) ?: return PuppyCasinoRewardLedger()
        return decode(raw) ?: PuppyCasinoRewardLedger()
    }

    fun write(
        editor: SharedPreferences.Editor,
        ledger: PuppyCasinoRewardLedger
    ) {
        editor.putString(LEDGER_KEY, encode(ledger))
    }

    private fun encode(ledger: PuppyCasinoRewardLedger): String =
        JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("dailyEpochDay", ledger.dailyEpochDay)
            put(
                "dailyTicketAwards",
                ledger.dailyTicketAwards.coerceIn(
                    0,
                    PuppyCasinoRewardEngine.MAX_DAILY_CASINO_TICKETS
                )
            )
            put("evaluatedRoundIds", JSONArray().apply {
                ledger.evaluatedRoundIds
                    .distinct()
                    .takeLast(PuppyCasinoRewardEngine.MAX_EVALUATED_ROUND_IDS)
                    .forEach { id ->
                        if (PuppyCasinoTransactionEngine.isValidRoundId(id)) put(id)
                    }
            })
        }.toString()

    private fun decode(raw: String): PuppyCasinoRewardLedger? =
        runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return@runCatching null
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
            }.takeLast(PuppyCasinoRewardEngine.MAX_EVALUATED_ROUND_IDS)

            PuppyCasinoRewardLedger(
                evaluatedRoundIds = ids,
                dailyEpochDay = root.optLong(
                    "dailyEpochDay",
                    LocalDate.now().toEpochDay()
                ),
                dailyTicketAwards = root.optInt("dailyTicketAwards", 0)
                    .coerceIn(0, PuppyCasinoRewardEngine.MAX_DAILY_CASINO_TICKETS)
            )
        }.getOrNull()
}
