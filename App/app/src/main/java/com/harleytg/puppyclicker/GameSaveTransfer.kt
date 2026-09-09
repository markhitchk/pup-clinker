package com.harleytg.puppyclicker

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

internal data class SaveTransferResult(val success: Boolean, val message: String)

/** Portable, typed save format used by Settings import/export. */
internal object GameSaveTransfer {
    private const val FORMAT = "puppy-clicker-save"
    private const val VERSION = 2
    private const val MAIN_PREFS = PuppyClickerV6ViewModel.PREFS_NAME
    private const val SEASONAL_PREFS = "puppy_seasonal_v1"
    private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

    fun export(context: Context, uri: Uri): SaveTransferResult = runCatching {
        val stores = JSONObject().apply {
            put(MAIN_PREFS, encodeStore(context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)))
            put(SEASONAL_PREFS, encodeStore(context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE)))
        }
        val document = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("package", context.packageName)
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("stores", stores)
        }

        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Unable to open the selected export file")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(document.toString(2)) }
        SaveTransferResult(true, "Save exported successfully.")
    }.getOrElse { error ->
        SaveTransferResult(false, "Export failed: ${error.message ?: "unknown error"}")
    }

    fun import(context: Context, uri: Uri): SaveTransferResult = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open the selected save file")
        val bytes = input.use { stream ->
            val buffer = ByteArray(MAX_IMPORT_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = stream.read(buffer, total, buffer.size - total)
                if (read < 0) break
                total += read
            }
            if (total > MAX_IMPORT_BYTES) error("Save file is larger than 4 MB")
            buffer.copyOf(total)
        }
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.optString("format") == FORMAT) { "Not a Puppy Clicker save" }
        val version = root.optInt("version", 1)
        require(version in 1..VERSION) { "Unsupported save version $version" }

        val mainPrefs = context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
        if (version >= 2 && root.has("stores")) {
            val stores = root.getJSONObject("stores")
            val mainStore = stores.optJSONObject(MAIN_PREFS)
                ?: error("Save does not contain the main game store")
            restoreStore(mainPrefs, mainStore)
            stores.optJSONObject(SEASONAL_PREFS)?.let { seasonalStore ->
                restoreStore(
                    context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE),
                    seasonalStore
                )
            }
        } else {
            // Compatibility with the readable v1 Android/data snapshot.
            val legacyValues = root.optJSONObject("values")
                ?: error("Legacy save is missing values")
            restoreLegacyStore(mainPrefs, legacyValues)
        }

        ExternalGameSave.write(context, mainPrefs)
        SaveTransferResult(true, "Save imported. Reloading Puppy Clicker…")
    }.getOrElse { error ->
        SaveTransferResult(false, "Import failed: ${error.message ?: "unknown error"}")
    }

    private fun encodeStore(prefs: SharedPreferences): JSONObject {
        val values = JSONObject()
        val types = JSONObject()
        prefs.all.toSortedMap().forEach { (key, value) ->
            when (value) {
                is String -> {
                    values.put(key, value)
                    types.put(key, "string")
                }
                is Boolean -> {
                    values.put(key, value)
                    types.put(key, "boolean")
                }
                is Int -> {
                    values.put(key, value)
                    types.put(key, "int")
                }
                is Long -> {
                    values.put(key, value)
                    types.put(key, "long")
                }
                is Float -> {
                    values.put(key, value.toDouble())
                    types.put(key, "float")
                }
                is Set<*> -> {
                    values.put(key, JSONArray(value.filterIsInstance<String>().sorted()))
                    types.put(key, "string_set")
                }
            }
        }
        return JSONObject().apply {
            put("values", values)
            put("types", types)
        }
    }

    private fun restoreStore(prefs: SharedPreferences, store: JSONObject) {
        val values = store.getJSONObject("values")
        val types = store.optJSONObject("types") ?: JSONObject()
        val editor = prefs.edit().clear()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = values.get(key)
            val type = types.optString(key).ifBlank { inferType(prefs, key, value) }
            putTyped(editor, key, type, value)
        }
        check(editor.commit()) { "Unable to write imported save" }
    }

    private fun restoreLegacyStore(prefs: SharedPreferences, values: JSONObject) {
        val editor = prefs.edit().clear()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = values.get(key)
            putTyped(editor, key, inferType(prefs, key, value), value)
        }
        check(editor.commit()) { "Unable to write imported legacy save" }
    }

    private fun inferType(prefs: SharedPreferences, key: String, value: Any): String {
        return when (prefs.all[key]) {
            is String -> "string"
            is Boolean -> "boolean"
            is Int -> "int"
            is Long -> "long"
            is Float -> "float"
            is Set<*> -> "string_set"
            else -> when (value) {
                is String -> "string"
                is Boolean -> "boolean"
                is JSONArray -> "string_set"
                is Number -> if (isLegacyIntKey(key)) "int" else "long"
                else -> "string"
            }
        }
    }

    private fun isLegacyIntKey(key: String): Boolean =
        key.startsWith("upgrade_") ||
            key.startsWith("prestige_skill_") ||
            key in setOf(
                "happiness",
                "fullness",
                "energy",
                "cleanliness_v5",
                "bond_v5",
                "best_combo",
                "daily_streak_v5",
                "pup_eye_strikes",
                "prestige_count_v6",
                "prestige_skill_points_v6",
                "birthday_month",
                "birthday_day"
            )

    private fun putTyped(editor: SharedPreferences.Editor, key: String, type: String, value: Any) {
        when (type) {
            "string" -> editor.putString(key, value.toString())
            "boolean" -> editor.putBoolean(key, value as? Boolean ?: value.toString().toBoolean())
            "int" -> editor.putInt(key, (value as Number).toInt())
            "long" -> editor.putLong(key, (value as Number).toLong())
            "float" -> editor.putFloat(key, (value as Number).toFloat())
            "string_set" -> {
                val array = value as JSONArray
                val set = buildSet {
                    for (index in 0 until array.length()) add(array.getString(index))
                }
                editor.putStringSet(key, set)
            }
            else -> error("Unsupported preference type '$type' for $key")
        }
    }
}

@Composable
internal fun SaveTransferSettings() {
    val context = LocalContext.current
    val activity = context as? Activity
    var status by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) status = GameSaveTransfer.export(context, uri).message
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = GameSaveTransfer.import(context, uri)
            status = result.message
            if (result.success) activity?.recreate()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text("💾 Save system", fontWeight = FontWeight.Black)
            Text(
                "Portable v2 backups include game progress plus seasonal settings. Legacy v1 Android/data saves can also be imported.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.size(9.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { exportLauncher.launch("puppy_clicker_save_v2.json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export save")
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import save")
                }
            }
            Spacer(Modifier.size(7.dp))
            Text(
                "Import replaces the current local game save and reloads the app. The automatic readable Android/data snapshot remains enabled.",
                style = MaterialTheme.typography.labelSmall
            )
            status?.let {
                Spacer(Modifier.size(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
