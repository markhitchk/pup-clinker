package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyProgressionStoreTest {
    @Test
    fun bondJsonRoundTripsDeterministically() {
        val encoded = PuppyProgressionStore.encodeBondMap(
            linkedMapOf("v2_frost" to 73, "classic" to 22)
        )
        assertEquals("{\"classic\":22,\"v2_frost\":73}", encoded)
        assertEquals(
            mapOf("classic" to 22, "v2_frost" to 73),
            PuppyProgressionStore.decodeBondMap(encoded)
        )
    }

    @Test
    fun malformedBondEntriesAreIgnoredAndValuesClamp() {
        val decoded = PuppyProgressionStore.decodeBondMap(
            "{\"classic\":500,\"low\":-4,\"bad\":\"abc\",\"\":40}"
        )
        assertEquals(mapOf("classic" to 100, "low" to 0), decoded)
    }

    @Test
    fun legacyBondMigratesOnlyWhenNewMapMissing() {
        assertEquals(
            mapOf("v2_frost" to 73),
            PuppyProgressionStore.migrateBondMap(
                existingRaw = null,
                activePuppyId = "v2_frost",
                legacyBond = 73
            )
        )
        assertEquals(
            mapOf("classic" to 55),
            PuppyProgressionStore.migrateBondMap(
                existingRaw = "{\"classic\":55}",
                activePuppyId = "v2_frost",
                legacyBond = 73
            )
        )
    }

    @Test
    fun xpMigrationUsesExistingValueOtherwiseLifetimeTreats() {
        assertEquals(500L, PuppyProgressionStore.migrateXp(existingXp = 500L, lifetimeTreats = 12L))
        assertEquals(12L, PuppyProgressionStore.migrateXp(existingXp = null, lifetimeTreats = 12L))
        assertEquals(0L, PuppyProgressionStore.migrateXp(existingXp = null, lifetimeTreats = -12L))
    }

    @Test
    fun xpSettlementRejectsDuplicateStableId() {
        val first = PuppyProgressionSettlement.apply(
            currentXp = 10L,
            settlements = emptySet(),
            event = PuppyXpEvent.DAILY_TASK,
            settlementId = "daily:20000:tap"
        )
        assertTrue(first.applied)
        assertEquals(35L, first.xp)
        assertTrue("daily:20000:tap" in first.settlements)

        val second = PuppyProgressionSettlement.apply(
            currentXp = first.xp,
            settlements = first.settlements,
            event = PuppyXpEvent.DAILY_TASK,
            settlementId = "daily:20000:tap"
        )
        assertFalse(second.applied)
        assertEquals(first.xp, second.xp)
    }
}
