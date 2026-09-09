package com.harleytg.puppyclicker

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mirrors Puppy Clicker's SharedPreferences save into the app-specific folder on
 * primary emulated/external storage.
 *
 * Typical path:
 * /storage/emulated/0/Android/data/com.harleytg.puppyclicker/files/PuppyClicker/puppy_clicker_save.json
 *
 * SharedPreferences remains the runtime source of truth. This file is a readable
 * snapshot of the current save for inspection/backup while the app is installed.
 */
object ExternalGameSave {
    const val DIRECTORY_NAME = "PuppyClicker"
    const val FILE_NAME = "puppy_clicker_save.json"

    fun write(context: Context, prefs: SharedPreferences): File? {
        val externalRoot = context.getExternalFilesDir(null) ?: return null
        if (Environment.getExternalStorageState(externalRoot) != Environment.MEDIA_MOUNTED) return null

        val directory = File(externalRoot, DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) return null

        val values = JSONObject()
        prefs.all.toSortedMap().forEach { (key, value) ->
            values.put(
                key,
                when (value) {
                    is Set<*> -> JSONArray(value.filterIsInstance<String>().sorted())
                    null -> JSONObject.NULL
                    else -> value
                }
            )
        }

        val document = JSONObject().apply {
            put("format", "puppy-clicker-save")
            put("version", 1)
            put("package", context.packageName)
            put("updatedAtEpochMs", System.currentTimeMillis())
            put("values", values)
        }

        val target = File(directory, FILE_NAME)
        val temporary = File(directory, "$FILE_NAME.tmp")

        return runCatching {
            temporary.writeText(document.toString(2), Charsets.UTF_8)
            if (target.exists() && !target.delete()) {
                error("Unable to replace existing external save")
            }
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText(Charsets.UTF_8), Charsets.UTF_8)
                temporary.delete()
            }
            target
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    fun path(context: Context): String? {
        val externalRoot = context.getExternalFilesDir(null) ?: return null
        return File(File(externalRoot, DIRECTORY_NAME), FILE_NAME).absolutePath
    }
}
