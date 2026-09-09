package com.harleytg.puppyclicker

import android.content.SharedPreferences
import android.util.Log

/**
 * Normalizes preference value types used by older Puppy Clicker releases and legacy JSON imports.
 * SharedPreferences throws ClassCastException when a stored Long is read with getInt (or vice
 * versa), so an otherwise valid old save must be normalized before the V6 ViewModel loads it.
 */
internal object PuppySaveCompatibility {
    private const val TAG = "PuppySaveCompat"

    private val explicitIntKeys = setOf(
        "happiness",
        "fullness",
        "energy",
        "cleanliness_v5",
        "bond_v5",
        "best_combo",
        "daily_streak_v5",
        "pup_eye_strikes",
        "prestige_count_v6",
        "prestige_skill_points_v6"
    )

    private val longKeys = setOf(
        "treats",
        "lifetime_treats",
        "total_shop_purchases_v5",
        "total_tickets_found",
        "care_actions_v5",
        "total_taps",
        "park_ready_at",
        "daily_claim_day",
        "daily_day_v5",
        "daily_tap_start_v5",
        "daily_care_start_v5",
        "daily_shop_start_v5",
        "fair_play_cooldown_until",
        "last_seen",
        "prestige_total_points_v6",
        "afk_background_at_v6",
        "afk_pending_treats_v6",
        "afk_away_ms_v6",
        "afk_claim_ready_v6"
    )

    private val booleanKeys = setOf(
        "park_active",
        "setting_haptics",
        "setting_animations",
        "setting_compact_numbers",
        "setting_afk_notifications"
    )

    private val stringKeys = setOf(
        "puppy_name",
        "puppy_style",
        "accessory"
    )

    private val stringSetKeys = setOf(
        "unlocked_puppies",
        "daily_tasks_v5",
        "redeemed_code_ids"
    )

    fun normalizeMainSave(prefs: SharedPreferences): Boolean {
        return runCatching {
            val snapshot = prefs.all
            val editor = prefs.edit()
            var changed = false

            snapshot.forEach { (key, raw) ->
                when {
                    isExpectedIntKey(key) && raw !is Int -> {
                        numberToLong(raw)?.let { value ->
                            editor.putInt(key, value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
                            changed = true
                        }
                    }

                    key in longKeys && raw !is Long -> {
                        numberToLong(raw)?.let { value ->
                            editor.putLong(key, value)
                            changed = true
                        }
                    }

                    key == "care_actions" && raw !is Int -> {
                        // Legacy pre-V5 key. V6 still uses it as a fallback if care_actions_v5
                        // does not exist, so keep it readable by its historical Int getter.
                        numberToLong(raw)?.let { value ->
                            editor.putInt(key, value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
                            changed = true
                        }
                    }

                    key in booleanKeys && raw !is Boolean -> {
                        toBoolean(raw)?.let { value ->
                            editor.putBoolean(key, value)
                            changed = true
                        }
                    }

                    key in stringKeys && raw !is String -> {
                        editor.putString(key, raw?.toString().orEmpty())
                        changed = true
                    }

                    key in stringSetKeys && raw !is Set<*> -> {
                        if (raw is String) {
                            val values = raw.split(',')
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .toSet()
                            editor.putStringSet(key, values)
                            changed = true
                        }
                    }
                }
            }

            if (changed) {
                check(editor.commit()) { "Unable to normalize legacy save types" }
                Log.i(TAG, "Normalized legacy save preference types")
            }
            changed
        }.getOrElse { error ->
            Log.w(TAG, "Legacy save normalization failed; leaving original values untouched", error)
            false
        }
    }

    private fun isExpectedIntKey(key: String): Boolean =
        key in explicitIntKeys ||
            key.startsWith("upgrade_") ||
            key.startsWith("prestige_skill_")

    private fun numberToLong(value: Any?): Long? = when (value) {
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        is Float -> value.toLong()
        is Double -> value.toLong()
        is String -> value.toLongOrNull() ?: value.toDoubleOrNull()?.toLong()
        else -> null
    }

    private fun toBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
        else -> null
    }
}
