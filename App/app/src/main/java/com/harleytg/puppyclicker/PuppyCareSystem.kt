package com.harleytg.puppyclicker

import android.content.SharedPreferences

/**
 * Per-puppy care state. Currency is account-wide, but needs/bond belong to the
 * individual puppy style so switching puppies never copies one puppy's care to another.
 */
internal data class PuppyCareProfile(
    val happiness: Int = 100,
    val fullness: Int = 100,
    val energy: Int = 100,
    val cleanliness: Int = 100,
    val bond: Int = 10
)

internal object PuppyCareSystem {
    const val TIRED_ENERGY_THRESHOLD = 10
    const val HEALTHY_CARE_SCORE = 70
    const val THRIVING_CARE_SCORE = 85
    const val THRIVING_BOND = 50

    private const val KEY_PREFIX = "care_profile_v1_"

    fun careScore(profile: PuppyCareProfile): Int =
        ((profile.happiness + profile.fullness + profile.energy + profile.cleanliness) / 4)
            .coerceIn(0, 100)

    fun isTired(energy: Int): Boolean =
        energy.coerceIn(0, 100) <= TIRED_ENERGY_THRESHOLD

    /**
     * A healthy active puppy helps normal tapping:
     * - 70%+ wellness: +1 Treat per accepted tap
     * - 85%+ wellness and 50%+ bond: +2 Treats per accepted tap
     */
    fun tapBonus(profile: PuppyCareProfile): Int {
        val score = careScore(profile)
        return when {
            score >= THRIVING_CARE_SCORE && profile.bond >= THRIVING_BOND -> 2
            score >= HEALTHY_CARE_SCORE -> 1
            else -> 0
        }
    }

    fun decay(profile: PuppyCareProfile, elapsedMinutes: Int): PuppyCareProfile {
        val minutes = elapsedMinutes.coerceIn(0, 240)
        if (minutes <= 0) return profile.normalized()
        return profile.copy(
            happiness = (profile.happiness - minutes / 3).coerceAtLeast(0),
            fullness = (profile.fullness - minutes / 2).coerceAtLeast(0),
            energy = (profile.energy - minutes / 4).coerceAtLeast(0),
            cleanliness = (profile.cleanliness - minutes / 4).coerceAtLeast(0)
        ).normalized()
    }

    /**
     * Loads one puppy's care. A legacy fallback is used only for the active puppy
     * during migration from the old shared-care save format. New/unvisited puppies
     * begin with their own fresh care profile.
     */
    fun load(
        prefs: SharedPreferences,
        styleId: String,
        nowMs: Long,
        legacyFallback: PuppyCareProfile? = null,
        legacyUpdatedAtMs: Long? = null
    ): PuppyCareProfile {
        val updatedKey = key(styleId, "updated_at")
        val hasProfile = prefs.contains(updatedKey)
        val base = if (hasProfile) {
            PuppyCareProfile(
                happiness = prefs.getInt(key(styleId, "happiness"), 100),
                fullness = prefs.getInt(key(styleId, "fullness"), 100),
                energy = prefs.getInt(key(styleId, "energy"), 100),
                cleanliness = prefs.getInt(key(styleId, "cleanliness"), 100),
                bond = prefs.getInt(key(styleId, "bond"), 10)
            ).normalized()
        } else {
            (legacyFallback ?: PuppyCareProfile()).normalized()
        }
        val updatedAt = if (hasProfile) {
            prefs.getLong(updatedKey, nowMs)
        } else {
            legacyUpdatedAtMs ?: nowMs
        }
        val elapsedMinutes =
            ((nowMs - updatedAt).coerceAtLeast(0L) / 60_000L).coerceAtMost(240L).toInt()
        return decay(base, elapsedMinutes)
    }

    fun write(
        editor: SharedPreferences.Editor,
        styleId: String,
        profile: PuppyCareProfile,
        updatedAtMs: Long
    ) {
        val normalized = profile.normalized()
        editor
            .putInt(key(styleId, "happiness"), normalized.happiness)
            .putInt(key(styleId, "fullness"), normalized.fullness)
            .putInt(key(styleId, "energy"), normalized.energy)
            .putInt(key(styleId, "cleanliness"), normalized.cleanliness)
            .putInt(key(styleId, "bond"), normalized.bond)
            .putLong(key(styleId, "updated_at"), updatedAtMs)
    }

    fun clearAll(prefs: SharedPreferences, editor: SharedPreferences.Editor) {
        prefs.all.keys
            .asSequence()
            .filter { it.startsWith(KEY_PREFIX) }
            .forEach { key -> editor.remove(key) }
    }

    private fun PuppyCareProfile.normalized(): PuppyCareProfile = copy(
        happiness = happiness.coerceIn(0, 100),
        fullness = fullness.coerceIn(0, 100),
        energy = energy.coerceIn(0, 100),
        cleanliness = cleanliness.coerceIn(0, 100),
        bond = bond.coerceIn(0, 100)
    )

    private fun key(styleId: String, field: String): String =
        KEY_PREFIX + styleId + "_" + field
}
