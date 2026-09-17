package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnePointZeroSaveCompatibilityContractTest {
    @Test
    fun onePointZeroPreferenceTypesAreCoveredByCompatibilityLayer() {
        val source = File("src/main/java/com/harleytg/puppyclicker/PuppySaveCompatibility.kt").readText()
        listOf(
            "player_xp_v1",
            "bond_by_puppy_v1",
            "achievement_rewarded_v1",
            "xp_settlements_v1",
            "release_claim_ids_v1",
            "profile_badge_ids_v1",
            "performance_preset_v1",
            "afk_pending_start_v1",
            "afk_pending_end_v1"
        ).forEach { key -> assertTrue("missing compatibility key $key", source.contains(key)) }
    }

    @Test
    fun encryptedTransferRemainsV3AndIncludesWholeMainStore() {
        val source = File("src/main/java/com/harleytg/puppyclicker/GameSaveTransfer.kt").readText()
        assertTrue(source.contains("private const val PAYLOAD_VERSION = 3"))
        assertTrue(source.contains("SecurePreferenceCodec.encode(mainPrefs)"))
        assertTrue(source.contains("put(MAIN_PREFS, mainStore)"))
    }
}
