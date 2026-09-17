package com.harleytg.puppyclicker

import android.content.Context
import org.json.JSONObject

data class PuppyWidgetSnapshot(
    val puppyName: String,
    val puppyStyle: String,
    val treats: Long,
    val careScore: Int,
    val bond: Int,
    val updatedAtMs: Long
)

internal object PuppyWidgetSnapshotStore {
    const val PREFS_NAME = "puppy_widget_snapshot_v1"
    private const val KEY_SNAPSHOT = "snapshot"

    fun encode(snapshot: PuppyWidgetSnapshot): String = JSONObject().apply {
        put("puppyName", snapshot.puppyName)
        put("puppyStyle", snapshot.puppyStyle)
        put("treats", snapshot.treats.coerceAtLeast(0L))
        put("careScore", snapshot.careScore.coerceIn(0, 100))
        put("bond", snapshot.bond.coerceIn(0, 100))
        put("updatedAtMs", snapshot.updatedAtMs.coerceAtLeast(0L))
    }.toString()

    fun decode(raw: String?): PuppyWidgetSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val puppyName = root.optString("puppyName").trim()
            val puppyStyle = root.optString("puppyStyle").trim()
            if (puppyName.isBlank() || puppyStyle.isBlank()) return null
            PuppyWidgetSnapshot(
                puppyName = puppyName,
                puppyStyle = puppyStyle,
                treats = root.optLong("treats", 0L).coerceAtLeast(0L),
                careScore = root.optInt("careScore", 0).coerceIn(0, 100),
                bond = root.optInt("bond", 0).coerceIn(0, 100),
                updatedAtMs = root.optLong("updatedAtMs", 0L).coerceAtLeast(0L)
            )
        }.getOrNull()
    }

    fun write(context: Context, snapshot: PuppyWidgetSnapshot) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNAPSHOT, encode(snapshot))
            .apply()
    }

    fun read(context: Context): PuppyWidgetSnapshot? =
        decode(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SNAPSHOT, null)
        )
}
