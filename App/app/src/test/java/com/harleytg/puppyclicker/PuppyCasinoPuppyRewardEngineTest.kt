package com.harleytg.puppyclicker

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoPuppyRewardEngineTest {
    private fun round(
        id: String,
        wager: Long = 100L,
        payout: Long
    ): PuppyCasinoRound = PuppyCasinoRound(
        roundId = id,
        game = PuppyCasinoGame.SLOTS,
        wagerTreats = wager,
        state = PuppyCasinoRoundState.OUTCOME_COMMITTED,
        acceptedAtMs = 1000L,
        outcomePayload = "{\"ok\":true}",
        payoutTreats = payout,
        outcomeCommittedAtMs = 1500L
    )

    @Test
    fun poolUsesOnlyVerifiedExistingRedeemOnlyRosterIds() {
        val byId = V6_PUPPY_STYLES.associateBy { it.id }

        assertEquals(16, PuppyCasinoPuppyRewardEngine.eligibleStyleIds.size)
        PuppyCasinoPuppyRewardEngine.eligibleStyleIds.forEach { id ->
            assertTrue(id in V6_PUPPY_IDS)
            assertTrue(byId[id]?.redeemOnly == true)
        }
    }

    @Test
    fun poolExcludesDefaultSeasonalDeveloperSecretTributeAndBrandedSpecials() {
        val excluded = setOf(
            "classic",
            "golden",
            "poodle",
            "spotty",
            "halloween",
            "santa",
            "birthday",
            "dev_pup",
            "secret_snoot",
            "classic_forever",
            "v2_harleytg",
            "v2_dev_pup"
        )

        assertTrue(
            excluded.none { it in PuppyCasinoPuppyRewardEngine.eligibleStyleIds }
        )
    }

    @Test
    fun exactVerifiedPoolRemainsStable() {
        assertEquals(
            listOf(
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
            ),
            PuppyCasinoPuppyRewardEngine.eligibleStyleIds
        )
    }

    @Test
    fun chanceBandsMatchPublishedPolicy() {
        assertEquals(
            0,
            PuppyCasinoPuppyRewardEngine.dropChanceBasisPoints(
                round("puppy_push_0001", payout = 100L)
            )
        )
        assertEquals(
            25,
            PuppyCasinoPuppyRewardEngine.dropChanceBasisPoints(
                round("puppy_profit_01", payout = 150L)
            )
        )
        assertEquals(
            50,
            PuppyCasinoPuppyRewardEngine.dropChanceBasisPoints(
                round("puppy_double_01", payout = 200L)
            )
        )
        assertEquals(
            150,
            PuppyCasinoPuppyRewardEngine.dropChanceBasisPoints(
                round("puppy_fivex_01", payout = 500L)
            )
        )
        assertEquals(
            500,
            PuppyCasinoPuppyRewardEngine.dropChanceBasisPoints(
                round("puppy_twenty_1", payout = 2_000L)
            )
        )
        assertEquals(
            10_000,
            PuppyCasinoPuppyRewardEngine.dropChanceBasisPoints(
                round("puppy_hundred1", payout = 10_000L)
            )
        )
    }

    @Test
    fun guaranteedJackpotUnlocksExactlyOneUnownedExistingPuppy() {
        val today = LocalDate.now().toEpochDay()
        val before = V6GameState()
        val application = PuppyCasinoPuppyRewardEngine.apply(
            before = before,
            settledRound = round("puppy_jackpot_1", payout = 10_000L),
            ledger = PuppyCasinoPuppyRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoPuppyRewardStatus.UNLOCKED, application.reward.status)
        val styleId = application.reward.styleId!!
        assertTrue(styleId in PuppyCasinoPuppyRewardEngine.eligibleStyleIds)
        assertTrue(styleId in application.state.unlockedPuppies)
        assertEquals(
            before.unlockedPuppies.size + 1,
            application.state.unlockedPuppies.size
        )
        assertEquals(1, application.ledger.dailyPuppyUnlocks)
    }

    @Test
    fun rewardNeverSelectsAnAlreadyOwnedEligiblePuppy() {
        val today = LocalDate.now().toEpochDay()
        val onlyRemaining = PuppyCasinoPuppyRewardEngine.eligibleStyleIds.last()
        val alreadyOwned =
            PuppyCasinoPuppyRewardEngine.eligibleStyleIds.dropLast(1).toSet()
        val before = V6GameState(
            unlockedPuppies = V6GameState().unlockedPuppies + alreadyOwned
        )

        val application = PuppyCasinoPuppyRewardEngine.apply(
            before = before,
            settledRound = round("puppy_last_one1", payout = 10_000L),
            ledger = PuppyCasinoPuppyRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoPuppyRewardStatus.UNLOCKED, application.reward.status)
        assertEquals(onlyRemaining, application.reward.styleId)
        assertTrue(onlyRemaining in application.state.unlockedPuppies)
    }

    @Test
    fun sameRoundCannotUnlockTwice() {
        val today = LocalDate.now().toEpochDay()
        val first = PuppyCasinoPuppyRewardEngine.apply(
            before = V6GameState(),
            settledRound = round("puppy_duplicate1", payout = 10_000L),
            ledger = PuppyCasinoPuppyRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )
        val second = PuppyCasinoPuppyRewardEngine.apply(
            before = first.state,
            settledRound = round("puppy_duplicate1", payout = 10_000L),
            ledger = first.ledger,
            todayEpochDay = today
        )

        assertEquals(
            PuppyCasinoPuppyRewardStatus.ALREADY_EVALUATED,
            second.reward.status
        )
        assertNull(second.reward.styleId)
        assertEquals(first.state.unlockedPuppies, second.state.unlockedPuppies)
        assertEquals(1, second.ledger.dailyPuppyUnlocks)
    }

    @Test
    fun dailyCapBlocksSecondPuppyButConsumesRound() {
        val today = LocalDate.now().toEpochDay()
        val application = PuppyCasinoPuppyRewardEngine.apply(
            before = V6GameState(),
            settledRound = round("puppy_dailycap1", payout = 10_000L),
            ledger = PuppyCasinoPuppyRewardLedger(
                dailyEpochDay = today,
                dailyPuppyUnlocks =
                    PuppyCasinoPuppyRewardEngine.MAX_DAILY_CASINO_PUPPIES
            ),
            todayEpochDay = today
        )

        assertEquals(
            PuppyCasinoPuppyRewardStatus.DAILY_CAP_REACHED,
            application.reward.status
        )
        assertTrue("puppy_dailycap1" in application.ledger.evaluatedRoundIds)
        assertEquals(
            PuppyCasinoPuppyRewardEngine.MAX_DAILY_CASINO_PUPPIES,
            application.ledger.dailyPuppyUnlocks
        )
    }

    @Test
    fun allEligibleOwnedReturnsNoDuplicateUnlock() {
        val today = LocalDate.now().toEpochDay()
        val before = V6GameState(
            unlockedPuppies =
                V6GameState().unlockedPuppies +
                    PuppyCasinoPuppyRewardEngine.eligibleStyleIds
        )
        val application = PuppyCasinoPuppyRewardEngine.apply(
            before = before,
            settledRound = round("puppy_allowned1", payout = 10_000L),
            ledger = PuppyCasinoPuppyRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )

        assertEquals(
            PuppyCasinoPuppyRewardStatus.ALL_ELIGIBLE_OWNED,
            application.reward.status
        )
        assertEquals(before.unlockedPuppies, application.state.unlockedPuppies)
        assertEquals(0, application.ledger.dailyPuppyUnlocks)
    }

    @Test
    fun lossOrPushNeverRunsPuppyDrop() {
        val today = LocalDate.now().toEpochDay()
        val application = PuppyCasinoPuppyRewardEngine.apply(
            before = V6GameState(),
            settledRound = round("puppy_noprofit1", payout = 100L),
            ledger = PuppyCasinoPuppyRewardLedger(dailyEpochDay = today),
            todayEpochDay = today
        )

        assertEquals(
            PuppyCasinoPuppyRewardStatus.NOT_ELIGIBLE,
            application.reward.status
        )
        assertFalse(
            application.state.unlockedPuppies.any {
                it in PuppyCasinoPuppyRewardEngine.eligibleStyleIds
            }
        )
        assertTrue("puppy_noprofit1" in application.ledger.evaluatedRoundIds)
    }

    @Test
    fun dayRolloverResetsDailyCapButKeepsEvaluationHistory() {
        val yesterday = LocalDate.now().minusDays(1).toEpochDay()
        val today = LocalDate.now().toEpochDay()
        val application = PuppyCasinoPuppyRewardEngine.apply(
            before = V6GameState(),
            settledRound = round("puppy_newday001", payout = 10_000L),
            ledger = PuppyCasinoPuppyRewardLedger(
                evaluatedRoundIds = listOf("puppy_oldround1"),
                dailyEpochDay = yesterday,
                dailyPuppyUnlocks =
                    PuppyCasinoPuppyRewardEngine.MAX_DAILY_CASINO_PUPPIES
            ),
            todayEpochDay = today
        )

        assertEquals(PuppyCasinoPuppyRewardStatus.UNLOCKED, application.reward.status)
        assertEquals(1, application.ledger.dailyPuppyUnlocks)
        assertTrue("puppy_oldround1" in application.ledger.evaluatedRoundIds)
        assertTrue("puppy_newday001" in application.ledger.evaluatedRoundIds)
    }

    @Test
    fun deterministicChoiceIsStableForSameRoundAndPool() {
        val id = "puppy_stable_01"
        assertEquals(
            PuppyCasinoPuppyRewardEngine.deterministicRoll(
                id,
                "puppy-drop",
                10_000
            ),
            PuppyCasinoPuppyRewardEngine.deterministicRoll(
                id,
                "puppy-drop",
                10_000
            )
        )
        assertEquals(
            PuppyCasinoPuppyRewardEngine.deterministicRoll(
                id,
                "puppy-choice",
                16
            ),
            PuppyCasinoPuppyRewardEngine.deterministicRoll(
                id,
                "puppy-choice",
                16
            )
        )
    }
}
