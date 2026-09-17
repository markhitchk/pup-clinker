package com.harleytg.puppyclicker

import org.json.JSONObject

internal object PuppyProgressionStore {
    const val KEY_BOND_BY_PUPPY = "bond_by_puppy_v1"
    const val KEY_PLAYER_XP = "player_xp_v1"
    const val KEY_ACHIEVEMENT_REWARDED = "achievement_rewarded_v1"
    const val KEY_XP_SETTLEMENTS = "xp_settlements_v1"

    fun encodeBondMap(values: Map<String, Int>): String {
        val normalized = values.entries
            .asSequence()
            .filter { it.key.isNotBlank() }
            .sortedBy { it.key }
            .associate { it.key to it.value.coerceIn(0, 100) }
        return buildString {
            append('{')
            normalized.entries.forEachIndexed { index, entry ->
                if (index > 0) append(',')
                append(JSONObject.quote(entry.key))
                append(':')
                append(entry.value)
            }
            append('}')
        }
    }

    fun decodeBondMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim()
                    if (key.isBlank()) continue
                    val value = when (val candidate = root.opt(key)) {
                        is Int -> candidate
                        is Long -> candidate.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                        is Number -> candidate.toInt()
                        else -> continue
                    }
                    put(key, value.coerceIn(0, 100))
                }
            }.toSortedMap()
        }.getOrDefault(emptyMap())
    }

    fun migrateBondMap(
        existingRaw: String?,
        activePuppyId: String,
        legacyBond: Int
    ): Map<String, Int> {
        if (!existingRaw.isNullOrBlank()) return decodeBondMap(existingRaw)
        val id = activePuppyId.trim()
        if (id.isBlank()) return emptyMap()
        return mapOf(id to legacyBond.coerceIn(0, 100))
    }

    fun migrateXp(existingXp: Long?, lifetimeTreats: Long): Long =
        existingXp?.coerceAtLeast(0L) ?: PuppyProgression.seedXpFromLifetimeTreats(lifetimeTreats)
}
