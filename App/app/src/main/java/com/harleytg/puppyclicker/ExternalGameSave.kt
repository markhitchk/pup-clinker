package com.harleytg.puppyclicker

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import java.io.File
import org.json.JSONObject

/**
 * Mirrors Puppy Clicker's runtime save into an AES-GCM encrypted file in the app-specific
 * folder on primary emulated storage.
 *
 * Typical path:
 * /storage/emulated/0/Android/data/com.harleytg.puppyclicker/files/PuppyClicker/puppy_clicker_save.pup
 *
 * The Android/data copy is device-bound through Android Keystore. Any byte-level edit,
 * replacement, or ciphertext corruption fails GCM authentication and is recorded by PupEye.
 * The encrypted payload includes the normalized lowercase player username and a coarse
 * manufacturer/model label for source identification without storing hardware identifiers.
 *
 * This mirror is intentionally non-fatal. The private SharedPreferences save remains the
 * runtime source of truth, so a device-specific Keystore or external-storage failure must
 * never stop Puppy Clicker from launching.
 */
object ExternalGameSave {
    const val DIRECTORY_NAME = "PuppyClicker"
    const val FILE_NAME = "puppy_clicker_save.pup"
    private const val LEGACY_FILE_NAME = "puppy_clicker_save.json"
    private const val FORMAT = "puppy-clicker-device-save"
    private const val VERSION = 3
    private const val TAG = "PuppyExternalSave"
    private val failureLogGate = RepeatedFailureLogGate()

    fun write(context: Context, prefs: SharedPreferences): File? {
        return runCatching {
            val externalRoot = context.getExternalFilesDir(null) ?: return@runCatching null
            if (Environment.getExternalStorageState(externalRoot) != Environment.MEDIA_MOUNTED) {
                return@runCatching null
            }

            val directory = File(externalRoot, DIRECTORY_NAME)
            if (!directory.exists() && !directory.mkdirs()) return@runCatching null

            val target = File(directory, FILE_NAME)
            verifyExisting(context, target)

            val document = JSONObject().apply {
                put("format", FORMAT)
                put("version", VERSION)
                put("package", context.packageName)
                put("updatedAtEpochMs", System.currentTimeMillis())
                put("identity", PuppyPlayerIdentity.metadata(context))
                put("store", SecurePreferenceCodec.encode(prefs))
            }

            val encrypted = PuppySaveCrypto.encryptDevice(
                document.toString().toByteArray(Charsets.UTF_8)
            )
            val temporary = File(directory, "$FILE_NAME.tmp")

            try {
                temporary.writeBytes(encrypted)
                if (target.exists() && !target.delete()) {
                    error("Unable to replace encrypted external save")
                }
                if (!temporary.renameTo(target)) {
                    target.writeBytes(temporary.readBytes())
                    temporary.delete()
                }

                File(directory, LEGACY_FILE_NAME).takeIf { it.exists() }?.delete()
                if (failureLogGate.markSuccess()) {
                    Log.i(TAG, "Encrypted Android/data mirror recovered")
                }
                target
            } finally {
                temporary.takeIf { it.exists() }?.delete()
            }
        }.getOrElse { error ->
            if (failureLogGate.shouldLog(error)) {
                Log.w(TAG, "Encrypted Android/data mirror unavailable; continuing with internal save", error)
            }
            null
        }
    }

    fun verifyExisting(context: Context): Boolean {
        val externalRoot = context.getExternalFilesDir(null) ?: return true
        return verifyExisting(context, File(File(externalRoot, DIRECTORY_NAME), FILE_NAME))
    }

    private fun verifyExisting(context: Context, target: File): Boolean {
        if (!target.isFile) return true
        return runCatching {
            val plain = PuppySaveCrypto.decryptDevice(target.readBytes())
            val document = JSONObject(plain.toString(Charsets.UTF_8))
            require(document.optString("format") == FORMAT) { "Unexpected device-save format" }
            require(document.optInt("version") in 2..VERSION) { "Unexpected device-save version" }
            document.optJSONObject("identity")?.let { identity ->
                val username = PuppyPlayerIdentity.normalizeUsername(identity.optString("username"))
                require(username.isNotBlank()) { "Missing protected player username" }
                require(identity.optString("deviceModel").isNotBlank()) { "Missing protected device model" }
            }
            true
        }.getOrElse { error ->
            if (classifySaveCryptoFailure(error) == SaveCryptoFailureKind.TAMPER) {
                PupEyeSaveGuard.recordTamper(
                    context,
                    "Android/data save failed PupEye AES-GCM authentication"
                )
                quarantineTamperedFile(target)
            } else if (failureLogGate.shouldLog(error)) {
                Log.w(TAG, "Unable to verify encrypted Android/data mirror; leaving file intact", error)
            }
            false
        }
    }

    private fun quarantineTamperedFile(target: File) {
        runCatching {
            val quarantine = File(
                target.parentFile,
                "puppy_clicker_save.tampered.${System.currentTimeMillis()}.pup"
            )
            if (!target.renameTo(quarantine)) target.delete()
        }
    }

    fun path(context: Context): String? {
        val externalRoot = context.getExternalFilesDir(null) ?: return null
        return File(File(externalRoot, DIRECTORY_NAME), FILE_NAME).absolutePath
    }
}
