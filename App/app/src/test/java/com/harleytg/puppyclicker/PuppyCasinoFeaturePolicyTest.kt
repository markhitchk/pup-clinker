package com.harleytg.puppyclicker

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyCasinoFeaturePolicyTest {
    private val today = LocalDate.of(2026, 9, 12)

    private fun flag(
        key: String,
        visible: Boolean = true,
        enabled: Boolean = true,
        status: String = "released",
        releaseDate: String? = null
    ) = PuppyFeatureFlag(
        key = key,
        visible = visible,
        enabled = enabled,
        status = status,
        releaseDate = releaseDate,
        label = key,
        description = ""
    )

    private fun flagsFor(
        game: PuppyCasinoGame,
        casino: PuppyFeatureFlag = flag("puppy_casino"),
        gameFlag: PuppyFeatureFlag = flag(PuppyCasinoFeaturePolicy.gameFlagKey(game))
    ): Map<String, PuppyFeatureFlag> = mapOf(
        "puppy_casino" to casino,
        PuppyCasinoFeaturePolicy.gameFlagKey(game) to gameFlag
    )

    @Test
    fun newRoundRequiresUmbrellaAndExactGameFlagFromSameSnapshot() {
        PuppyCasinoGame.entries.forEach { game ->
            assertTrue(
                PuppyCasinoFeaturePolicy.canStartNewRound(
                    game = game,
                    flags = flagsFor(game),
                    today = today
                )
            )

            assertFalse(
                PuppyCasinoFeaturePolicy.canStartNewRound(
                    game = game,
                    flags = flagsFor(
                        game,
                        casino = flag("puppy_casino", enabled = false)
                    ),
                    today = today
                )
            )

            assertFalse(
                PuppyCasinoFeaturePolicy.canStartNewRound(
                    game = game,
                    flags = flagsFor(
                        game,
                        gameFlag = flag(
                            PuppyCasinoFeaturePolicy.gameFlagKey(game),
                            enabled = false
                        )
                    ),
                    today = today
                )
            )
        }
    }

    @Test
    fun comingSoonHiddenAndFutureReleaseAllBlockNewRounds() {
        val game = PuppyCasinoGame.SLOTS

        assertFalse(
            PuppyCasinoFeaturePolicy.canStartNewRound(
                game,
                flagsFor(
                    game,
                    gameFlag = flag("casino_slots", status = "coming_soon")
                ),
                today
            )
        )
        assertFalse(
            PuppyCasinoFeaturePolicy.canStartNewRound(
                game,
                flagsFor(
                    game,
                    gameFlag = flag("casino_slots", visible = false)
                ),
                today
            )
        )
        assertFalse(
            PuppyCasinoFeaturePolicy.canStartNewRound(
                game,
                flagsFor(
                    game,
                    gameFlag = flag(
                        "casino_slots",
                        releaseDate = "2026-09-13"
                    )
                ),
                today
            )
        )
    }

    @Test
    fun malformedReleaseDateFailsClosed() {
        val malformed = flag(
            key = "casino_slots",
            releaseDate = "not-a-date"
        )

        assertFalse(malformed.isAvailable(today))
        assertEquals("Disabled", malformed.statusLabel(today))
    }

    @Test
    fun missingRemoteFlagFailsClosed() {
        assertFalse(
            PuppyCasinoFeaturePolicy.canStartNewRound(
                PuppyCasinoGame.ROULETTE,
                mapOf("puppy_casino" to flag("puppy_casino")),
                today
            )
        )
    }

    @Test
    fun remoteHideCannotHideAnActiveRecoveryRound() {
        val hidden = mapOf(
            "puppy_casino" to flag(
                "puppy_casino",
                visible = false,
                enabled = false,
                status = "disabled"
            )
        )
        val active = PuppyCasinoRound(
            roundId = "round_recovery_01",
            game = PuppyCasinoGame.SLOTS,
            wagerTreats = 100L,
            state = PuppyCasinoRoundState.WAGER_ACCEPTED,
            acceptedAtMs = 1_000L
        )

        assertFalse(PuppyCasinoFeaturePolicy.shouldExposeCasinoEntry(hidden, null))
        assertTrue(PuppyCasinoFeaturePolicy.shouldExposeCasinoEntry(hidden, active))
    }

    @Test
    fun recoveryActionDependsOnlyOnPersistedRoundState() {
        val slotsAccepted = PuppyCasinoRound(
            "round_slots_rec1",
            PuppyCasinoGame.SLOTS,
            100L,
            PuppyCasinoRoundState.WAGER_ACCEPTED,
            1_000L
        )
        val blackjackAccepted = PuppyCasinoRound(
            "round_blackjack1",
            PuppyCasinoGame.BLACKJACK,
            100L,
            PuppyCasinoRoundState.WAGER_ACCEPTED,
            1_000L
        )
        val committed = PuppyCasinoRound(
            "round_committed1",
            PuppyCasinoGame.ROULETTE,
            100L,
            PuppyCasinoRoundState.OUTCOME_COMMITTED,
            1_000L,
            outcomePayload = "{\"ok\":true}",
            payoutTreats = 200L,
            outcomeCommittedAtMs = 1_500L
        )

        assertEquals(
            PuppyCasinoRecoveryAction.REFUND_ACCEPTED_WAGER,
            PuppyCasinoFeaturePolicy.recoveryAction(slotsAccepted)
        )
        assertEquals(
            PuppyCasinoRecoveryAction.RESUME_BLACKJACK,
            PuppyCasinoFeaturePolicy.recoveryAction(blackjackAccepted)
        )
        assertEquals(
            PuppyCasinoRecoveryAction.SETTLE_COMMITTED_OUTCOME,
            PuppyCasinoFeaturePolicy.recoveryAction(committed)
        )
    }

    @Test
    fun disabledSnapshotImmediatelyBlocksNextRound() {
        val game = PuppyCasinoGame.BLACKJACK
        val enabled = flagsFor(game)
        val disabled = flagsFor(
            game,
            casino = flag("puppy_casino", enabled = false)
        )

        assertTrue(PuppyCasinoFeaturePolicy.canStartNewRound(game, enabled, today))
        assertFalse(PuppyCasinoFeaturePolicy.canStartNewRound(game, disabled, today))
    }
}
