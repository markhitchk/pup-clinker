package com.harleytg.puppyclicker

import android.content.Context
import java.io.File

/**
 * Stores the local account's portable-backup password encrypted by the device-bound
 * Android Keystore save key. The plaintext password is never written to preferences,
 * logs, the feature-flag service, or the exported save container.
 */
internal object PuppyBackupPasswordStore {
    private const val FILE_NAME = "credentials/backup-password.pce"

    fun set(context: Context, password: String) {
        require(password.length >= 8) { "Backup password must be at least 8 characters" }
        val file = File(context.noBackupFilesDir, FILE_NAME)
        file.parentFile?.mkdirs()
        val encrypted = PuppySaveCrypto.encryptDevice(password.toByteArray(Charsets.UTF_8))
        val temp = File.createTempFile("backup-password", ".tmp", file.parentFile)
        try {
            temp.writeBytes(encrypted)
            if (file.exists() && !file.delete()) error("Unable to replace backup password")
            if (!temp.renameTo(file)) {
                file.writeBytes(encrypted)
            }
        } finally {
            temp.delete()
        }
    }

    fun get(context: Context): String? {
        val file = File(context.noBackupFilesDir, FILE_NAME)
        if (!file.isFile) return null
        return runCatching {
            PuppySaveCrypto.decryptDevice(file.readBytes()).toString(Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.length >= 8 }
    }

    fun clear(context: Context) {
        File(context.noBackupFilesDir, FILE_NAME).delete()
    }
}
