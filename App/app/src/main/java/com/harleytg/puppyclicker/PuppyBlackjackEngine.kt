package com.harleytg.puppyclicker

import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

internal enum class PuppyBlackjackSuit(val symbol: String) {
    CLUBS("♣"),
    DIAMONDS("♦"),
    HEARTS("♥"),
    SPADES("♠")
}

internal data class PuppyBlackjackCard(val id: Int) {
    init {
        require(id in 0..51)
    }

    val rank: Int get() = id % 13 + 1
    val suit: PuppyBlackjackSuit get() = PuppyBlackjackSuit.entries[id / 13]
    val blackjackValue: Int get() = rank.coerceAtMost(10)

    val rankLabel: String
        get() = when (rank) {
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> rank.toString()
        }

    val label: String get() = rankLabel + suit.symbol
}

internal data class PuppyBlackjackHandValue(
    val total: Int,
    val soft: Boolean
)

internal data class PuppyBlackjackHand(
    val cards: List<Int>,
    val wagerTreats: Long,
    val stood: Boolean = false,
    val doubled: Boolean = false,
    val fromSplit: Boolean = false,
    val splitAces: Boolean = false
) {
    init {
        require(cards.size >= 2)
        require(cards.all { it in 0..51 })
        require(wagerTreats > 0L)
    }
}

internal data class PuppyBlackjackState(
    val remainingDeck: List<Int>,
    val dealerCards: List<Int>,
    val hands: List<PuppyBlackjackHand>,
    val activeHandIndex: Int,
    val complete: Boolean
) {
    init {
        require(dealerCards.size >= 2)
        require(hands.isNotEmpty())
        require(hands.size <= PuppyBlackjackEngine.MAX_PLAYER_HANDS)
        if (complete) {
            require(activeHandIndex == -1)
        } else {
            require(activeHandIndex in hands.indices)
        }
    }
}

internal enum class PuppyBlackjackActionError {
    ROUND_COMPLETE,
    INVALID_ACTIVE_HAND,
    CANNOT_HIT,
    CANNOT_DOUBLE,
    CANNOT_SPLIT,
    DECK_EXHAUSTED,
    PAYOUT_OVERFLOW
}

internal data class PuppyBlackjackActionResult(
    val success: Boolean,
    val state: PuppyBlackjackState,
    val additionalWagerTreats: Long = 0L,
    val error: PuppyBlackjackActionError? = null
)

internal enum class PuppyBlackjackHandResult {
    BLACKJACK,
    WIN,
    PUSH,
    LOSS,
    BUST
}

internal data class PuppyBlackjackHandOutcome(
    val result: PuppyBlackjackHandResult,
    val playerTotal: Int,
    val wagerTreats: Long,
    val payoutTreats: Long
)

internal data class PuppyBlackjackOutcome(
    val dealerTotal: Int,
    val dealerBlackjack: Boolean,
    val dealerBust: Boolean,
    val hands: List<PuppyBlackjackHandOutcome>,
    val totalPayoutTreats: Long
)

internal enum class PuppyBlackjackCommandFailure {
    INVALID_WAGER,
    TRANSACTION_REJECTED,
    INVALID_SAVED_STATE,
    ACTION_NOT_ALLOWED,
    OUTCOME_COMMIT_FAILED
}

internal data class PuppyBlackjackCommandResult(
    val success: Boolean,
    val roundId: String? = null,
    val state: PuppyBlackjackState? = null,
    val outcome: PuppyBlackjackOutcome? = null,
    val failure: PuppyBlackjackCommandFailure? = null,
    val actionError: PuppyBlackjackActionError? = null,
    val transactionFailure: PuppyCasinoTransactionFailure? = null
)

internal object PuppyBlackjackEngine {
    const val MIN_WAGER_TREATS = 20L
    const val MAX_WAGER_TREATS = 100_000L
    const val WAGER_INCREMENT_TREATS = 10L
    const val MAX_PLAYER_HANDS = 3

    val wagerPresets: List<Long> = listOf(20L, 100L, 500L, 2_500L)

    fun isValidInitialWager(wagerTreats: Long): Boolean =
        wagerTreats in MIN_WAGER_TREATS..MAX_WAGER_TREATS &&
            wagerTreats % WAGER_INCREMENT_TREATS == 0L

    fun newRound(
        wagerTreats: Long,
        random: SecureRandom = SecureRandom()
    ): PuppyBlackjackState {
        require(isValidInitialWager(wagerTreats))
        val deck = (0..51).toMutableList()
        for (index in deck.lastIndex downTo 1) {
            val swapWith = random.nextInt(index + 1)
            val value = deck[index]
            deck[index] = deck[swapWith]
            deck[swapWith] = value
        }
        return newRoundFromDeck(wagerTreats, deck)
    }

    fun newRoundFromDeck(
        wagerTreats: Long,
        shuffledDeck: List<Int>
    ): PuppyBlackjackState {
        require(isValidInitialWager(wagerTreats))
        require(shuffledDeck.size == 52)
        require(shuffledDeck.toSet().size == 52)
        require(shuffledDeck.all { it in 0..51 })

        val deck = shuffledDeck.toMutableList()
        val player = mutableListOf<Int>()
        val dealer = mutableListOf<Int>()
        player += draw(deck)
        dealer += draw(deck)
        player += draw(deck)
        dealer += draw(deck)

        val hand = PuppyBlackjackHand(
            cards = player,
            wagerTreats = wagerTreats
        )
        val initial = PuppyBlackjackState(
            remainingDeck = deck,
            dealerCards = dealer,
            hands = listOf(hand),
            activeHandIndex = 0,
            complete = false
        )

        return if (isNatural(hand) || isDealerNatural(dealer)) {
            initial.copy(activeHandIndex = -1, complete = true)
        } else {
            initial
        }
    }

    fun handValue(cards: List<Int>): PuppyBlackjackHandValue {
        require(cards.isNotEmpty())
        var total = cards.sumOf { PuppyBlackjackCard(it).blackjackValue }
        var aces = cards.count { PuppyBlackjackCard(it).rank == 1 }
        var soft = false

        while (aces > 0 && total + 10 <= 21) {
            total += 10
            aces--
            soft = true
        }
        return PuppyBlackjackHandValue(total = total, soft = soft)
    }

    fun isNatural(hand: PuppyBlackjackHand): Boolean =
        !hand.fromSplit &&
            hand.cards.size == 2 &&
            handValue(hand.cards).total == 21

    fun canHit(state: PuppyBlackjackState): Boolean {
        val hand = activeHand(state) ?: return false
        return !state.complete &&
            !hand.stood &&
            !hand.splitAces &&
            handValue(hand.cards).total < 21
    }

    fun canStand(state: PuppyBlackjackState): Boolean =
        activeHand(state)?.let { !state.complete && !it.stood } == true

    fun canDouble(state: PuppyBlackjackState): Boolean {
        val hand = activeHand(state) ?: return false
        return !state.complete &&
            !hand.stood &&
            !hand.splitAces &&
            hand.cards.size == 2 &&
            hand.wagerTreats <= Long.MAX_VALUE / 2L
    }

    fun canSplit(state: PuppyBlackjackState): Boolean {
        val hand = activeHand(state) ?: return false
        if (state.complete || hand.stood || hand.cards.size != 2) return false
        if (state.hands.size >= MAX_PLAYER_HANDS) return false
        val first = PuppyBlackjackCard(hand.cards[0])
        val second = PuppyBlackjackCard(hand.cards[1])
        if (first.rank != second.rank) return false
        if (first.rank == 1 && hand.fromSplit) return false
        return true
    }

    fun hit(state: PuppyBlackjackState): PuppyBlackjackActionResult {
        if (state.complete) return failure(state, PuppyBlackjackActionError.ROUND_COMPLETE)
        if (!canHit(state)) return failure(state, PuppyBlackjackActionError.CANNOT_HIT)
        if (state.remainingDeck.isEmpty()) {
            return failure(state, PuppyBlackjackActionError.DECK_EXHAUSTED)
        }

        val deck = state.remainingDeck.toMutableList()
        val hands = state.hands.toMutableList()
        val index = state.activeHandIndex
        val hand = hands[index]
        val nextCards = hand.cards + draw(deck)
        val total = handValue(nextCards).total
        hands[index] = hand.copy(
            cards = nextCards,
            stood = total >= 21
        )

        return success(advanceOrResolve(state.copy(
            remainingDeck = deck,
            hands = hands
        )))
    }

    fun stand(state: PuppyBlackjackState): PuppyBlackjackActionResult {
        if (state.complete) return failure(state, PuppyBlackjackActionError.ROUND_COMPLETE)
        if (!canStand(state)) return failure(state, PuppyBlackjackActionError.INVALID_ACTIVE_HAND)

        val hands = state.hands.toMutableList()
        val index = state.activeHandIndex
        hands[index] = hands[index].copy(stood = true)
        return success(advanceOrResolve(state.copy(hands = hands)))
    }

    fun doubleDown(state: PuppyBlackjackState): PuppyBlackjackActionResult {
        if (state.complete) return failure(state, PuppyBlackjackActionError.ROUND_COMPLETE)
        if (!canDouble(state)) return failure(state, PuppyBlackjackActionError.CANNOT_DOUBLE)
        if (state.remainingDeck.isEmpty()) {
            return failure(state, PuppyBlackjackActionError.DECK_EXHAUSTED)
        }

        val deck = state.remainingDeck.toMutableList()
        val hands = state.hands.toMutableList()
        val index = state.activeHandIndex
        val hand = hands[index]
        val additional = hand.wagerTreats
        val doubledWager = checkedAdd(hand.wagerTreats, additional)
            ?: return failure(state, PuppyBlackjackActionError.PAYOUT_OVERFLOW)
        hands[index] = hand.copy(
            cards = hand.cards + draw(deck),
            wagerTreats = doubledWager,
            stood = true,
            doubled = true
        )

        return success(
            state = advanceOrResolve(state.copy(
                remainingDeck = deck,
                hands = hands
            )),
            additionalWagerTreats = additional
        )
    }

    fun split(state: PuppyBlackjackState): PuppyBlackjackActionResult {
        if (state.complete) return failure(state, PuppyBlackjackActionError.ROUND_COMPLETE)
        if (!canSplit(state)) return failure(state, PuppyBlackjackActionError.CANNOT_SPLIT)
        if (state.remainingDeck.size < 2) {
            return failure(state, PuppyBlackjackActionError.DECK_EXHAUSTED)
        }

        val deck = state.remainingDeck.toMutableList()
        val hands = state.hands.toMutableList()
        val index = state.activeHandIndex
        val source = hands[index]
        val splitAces = PuppyBlackjackCard(source.cards[0]).rank == 1
        val additional = source.wagerTreats

        val firstCards = listOf(source.cards[0], draw(deck))
        val secondCards = listOf(source.cards[1], draw(deck))
        val first = PuppyBlackjackHand(
            cards = firstCards,
            wagerTreats = source.wagerTreats,
            stood = splitAces || handValue(firstCards).total >= 21,
            fromSplit = true,
            splitAces = splitAces
        )
        val second = PuppyBlackjackHand(
            cards = secondCards,
            wagerTreats = source.wagerTreats,
            stood = splitAces || handValue(secondCards).total >= 21,
            fromSplit = true,
            splitAces = splitAces
        )

        hands.removeAt(index)
        hands.add(index, second)
        hands.add(index, first)

        val splitState = state.copy(
            remainingDeck = deck,
            hands = hands,
            activeHandIndex = index
        )

        return success(
            state = if (first.stood) advanceOrResolve(splitState) else splitState,
            additionalWagerTreats = additional
        )
    }

    fun outcome(state: PuppyBlackjackState): PuppyBlackjackOutcome {
        require(state.complete)
        val dealerValue = handValue(state.dealerCards)
        val dealerBlackjack = isDealerNatural(state.dealerCards)
        val dealerBust = dealerValue.total > 21
        var totalPayout = 0L

        val handOutcomes = state.hands.map { hand ->
            val playerValue = handValue(hand.cards)
            val natural = isNatural(hand)
            val result: PuppyBlackjackHandResult
            val payout: Long

            when {
                playerValue.total > 21 -> {
                    result = PuppyBlackjackHandResult.BUST
                    payout = 0L
                }
                dealerBlackjack && natural -> {
                    result = PuppyBlackjackHandResult.PUSH
                    payout = hand.wagerTreats
                }
                dealerBlackjack -> {
                    result = PuppyBlackjackHandResult.LOSS
                    payout = 0L
                }
                natural -> {
                    result = PuppyBlackjackHandResult.BLACKJACK
                    payout = multiplyRatioChecked(hand.wagerTreats, 5, 2)
                }
                dealerBust -> {
                    result = PuppyBlackjackHandResult.WIN
                    payout = multiplyRatioChecked(hand.wagerTreats, 2, 1)
                }
                playerValue.total > dealerValue.total -> {
                    result = PuppyBlackjackHandResult.WIN
                    payout = multiplyRatioChecked(hand.wagerTreats, 2, 1)
                }
                playerValue.total == dealerValue.total -> {
                    result = PuppyBlackjackHandResult.PUSH
                    payout = hand.wagerTreats
                }
                else -> {
                    result = PuppyBlackjackHandResult.LOSS
                    payout = 0L
                }
            }

            totalPayout = checkedAdd(totalPayout, payout)
                ?: error("Blackjack payout overflow")

            PuppyBlackjackHandOutcome(
                result = result,
                playerTotal = playerValue.total,
                wagerTreats = hand.wagerTreats,
                payoutTreats = payout
            )
        }

        return PuppyBlackjackOutcome(
            dealerTotal = dealerValue.total,
            dealerBlackjack = dealerBlackjack,
            dealerBust = dealerBust,
            hands = handOutcomes,
            totalPayoutTreats = totalPayout
        )
    }

    fun totalWager(state: PuppyBlackjackState): Long =
        state.hands.fold(0L) { total, hand ->
            checkedAdd(total, hand.wagerTreats)
                ?: error("Blackjack wager overflow")
        }

    private fun activeHand(state: PuppyBlackjackState): PuppyBlackjackHand? =
        if (!state.complete && state.activeHandIndex in state.hands.indices) {
            state.hands[state.activeHandIndex]
        } else {
            null
        }

    private fun advanceOrResolve(state: PuppyBlackjackState): PuppyBlackjackState {
        val start = state.activeHandIndex.coerceAtLeast(0)
        for (index in (start + 1) until state.hands.size) {
            val hand = state.hands[index]
            val value = handValue(hand.cards).total
            if (!hand.stood && value < 21 && !hand.splitAces) {
                return state.copy(activeHandIndex = index)
            }
        }

        val normalizedHands = state.hands.map { hand ->
            val value = handValue(hand.cards).total
            if (hand.stood || value >= 21 || hand.splitAces) {
                hand.copy(stood = true)
            } else {
                hand
            }
        }

        if (normalizedHands.any { !it.stood && handValue(it.cards).total < 21 }) {
            val index = normalizedHands.indexOfFirst {
                !it.stood && handValue(it.cards).total < 21
            }
            return state.copy(hands = normalizedHands, activeHandIndex = index)
        }

        return resolveDealer(state.copy(hands = normalizedHands))
    }

    private fun resolveDealer(state: PuppyBlackjackState): PuppyBlackjackState {
        if (state.hands.all { handValue(it.cards).total > 21 }) {
            return state.copy(activeHandIndex = -1, complete = true)
        }
        if (isDealerNatural(state.dealerCards)) {
            return state.copy(activeHandIndex = -1, complete = true)
        }

        val deck = state.remainingDeck.toMutableList()
        val dealer = state.dealerCards.toMutableList()

        while (handValue(dealer).total < 17) {
            if (deck.isEmpty()) error("Blackjack deck exhausted")
            dealer += draw(deck)
        }

        return state.copy(
            remainingDeck = deck,
            dealerCards = dealer,
            activeHandIndex = -1,
            complete = true
        )
    }

    private fun isDealerNatural(cards: List<Int>): Boolean =
        cards.size == 2 && handValue(cards).total == 21

    private fun draw(deck: MutableList<Int>): Int {
        require(deck.isNotEmpty())
        return deck.removeAt(0)
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
        require(value <= Long.MAX_VALUE / numerator.toLong()) {
            "Blackjack payout overflow"
        }
        return value * numerator.toLong() / denominator.toLong()
    }

    private fun checkedAdd(a: Long, b: Long): Long? {
        if (b < 0L || a > Long.MAX_VALUE - b) return null
        return a + b
    }

    private fun success(
        state: PuppyBlackjackState,
        additionalWagerTreats: Long = 0L
    ): PuppyBlackjackActionResult = PuppyBlackjackActionResult(
        success = true,
        state = state,
        additionalWagerTreats = additionalWagerTreats
    )

    private fun failure(
        state: PuppyBlackjackState,
        error: PuppyBlackjackActionError
    ): PuppyBlackjackActionResult = PuppyBlackjackActionResult(
        success = false,
        state = state,
        error = error
    )
}

internal object PuppyBlackjackStateCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(state: PuppyBlackjackState): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("game", PuppyCasinoGame.BLACKJACK.name)
        put("remainingDeck", JSONArray().apply {
            state.remainingDeck.forEach(::put)
        })
        put("dealerCards", JSONArray().apply {
            state.dealerCards.forEach(::put)
        })
        put("hands", JSONArray().apply {
            state.hands.forEach { hand ->
                put(JSONObject().apply {
                    put("cards", JSONArray().apply { hand.cards.forEach(::put) })
                    put("wagerTreats", hand.wagerTreats)
                    put("stood", hand.stood)
                    put("doubled", hand.doubled)
                    put("fromSplit", hand.fromSplit)
                    put("splitAces", hand.splitAces)
                })
            }
        })
        put("activeHandIndex", state.activeHandIndex)
        put("complete", state.complete)
    }.toString()

    fun decodeAndValidate(raw: String?): PuppyBlackjackState? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return@runCatching null
            if (root.optString("game") != PuppyCasinoGame.BLACKJACK.name) return@runCatching null

            val remaining = root.getJSONArray("remainingDeck").toIntList()
            val dealer = root.getJSONArray("dealerCards").toIntList()
            val handsJson = root.getJSONArray("hands")
            if (handsJson.length() !in 1..PuppyBlackjackEngine.MAX_PLAYER_HANDS) {
                return@runCatching null
            }
            val hands = buildList {
                for (index in 0 until handsJson.length()) {
                    val item = handsJson.getJSONObject(index)
                    add(
                        PuppyBlackjackHand(
                            cards = item.getJSONArray("cards").toIntList(),
                            wagerTreats = item.getLong("wagerTreats"),
                            stood = item.optBoolean("stood", false),
                            doubled = item.optBoolean("doubled", false),
                            fromSplit = item.optBoolean("fromSplit", false),
                            splitAces = item.optBoolean("splitAces", false)
                        )
                    )
                }
            }

            val complete = root.getBoolean("complete")
            val active = root.getInt("activeHandIndex")
            val allCards = remaining + dealer + hands.flatMap { it.cards }
            if (allCards.size != 52) return@runCatching null
            if (allCards.toSet().size != 52) return@runCatching null
            if (allCards.any { it !in 0..51 }) return@runCatching null

            val state = PuppyBlackjackState(
                remainingDeck = remaining,
                dealerCards = dealer,
                hands = hands,
                activeHandIndex = active,
                complete = complete
            )

            if (!complete) {
                val hand = hands.getOrNull(active) ?: return@runCatching null
                if (hand.stood || PuppyBlackjackEngine.handValue(hand.cards).total >= 21) {
                    return@runCatching null
                }
            }

            state
        }.getOrNull()
    }

    private fun JSONArray.toIntList(): List<Int> = buildList {
        for (index in 0 until length()) add(getInt(index))
    }
}

internal object PuppyBlackjackOutcomeCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(outcome: PuppyBlackjackOutcome): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("game", PuppyCasinoGame.BLACKJACK.name)
        put("dealerTotal", outcome.dealerTotal)
        put("dealerBlackjack", outcome.dealerBlackjack)
        put("dealerBust", outcome.dealerBust)
        put("totalPayoutTreats", outcome.totalPayoutTreats)
        put("hands", JSONArray().apply {
            outcome.hands.forEach { hand ->
                put(JSONObject().apply {
                    put("result", hand.result.name)
                    put("playerTotal", hand.playerTotal)
                    put("wagerTreats", hand.wagerTreats)
                    put("payoutTreats", hand.payoutTreats)
                })
            }
        })
    }.toString()

    fun decodeAndValidate(
        raw: String?,
        state: PuppyBlackjackState
    ): PuppyBlackjackOutcome? {
        if (raw.isNullOrBlank() || !state.complete) return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return@runCatching null
            if (root.optString("game") != PuppyCasinoGame.BLACKJACK.name) return@runCatching null

            val recomputed = PuppyBlackjackEngine.outcome(state)
            if (root.optInt("dealerTotal", -1) != recomputed.dealerTotal) return@runCatching null
            if (root.optBoolean("dealerBlackjack", !recomputed.dealerBlackjack) != recomputed.dealerBlackjack) {
                return@runCatching null
            }
            if (root.optBoolean("dealerBust", !recomputed.dealerBust) != recomputed.dealerBust) {
                return@runCatching null
            }
            if (root.optLong("totalPayoutTreats", -1L) != recomputed.totalPayoutTreats) {
                return@runCatching null
            }

            val handsJson = root.getJSONArray("hands")
            if (handsJson.length() != recomputed.hands.size) return@runCatching null
            recomputed.hands.forEachIndexed { index, hand ->
                val saved = handsJson.getJSONObject(index)
                if (saved.optString("result") != hand.result.name) return@runCatching null
                if (saved.optInt("playerTotal", -1) != hand.playerTotal) return@runCatching null
                if (saved.optLong("wagerTreats", -1L) != hand.wagerTreats) return@runCatching null
                if (saved.optLong("payoutTreats", -1L) != hand.payoutTreats) return@runCatching null
            }

            recomputed
        }.getOrNull()
    }
}
