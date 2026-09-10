package com.harleytg.puppyclicker

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** Typed SharedPreferences codec shared by encrypted saves and PupEye integrity checks. */
internal object SecurePreferenceCodec {
    fun encode(prefs: SharedPreferences): JSONObject {
        val values = JSONObject()
        val types = JSONObject()
        prefs.all.toSortedMap().forEach { (key, value) ->
            when (value) {
                is String -> { values.put(key, value); types.put(key, "string") }
                is Boolean -> { values.put(key, value); types.put(key, "boolean") }
                is Int -> { values.put(key, value); types.put(key, "int") }
                is Long -> { values.put(key, value); types.put(key, "long") }
                is Float -> { values.put(key, value.toDouble()); types.put(key, "float") }
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

    fun restore(prefs: SharedPreferences, store: JSONObject) {
        val values = store.getJSONObject("values")
        val types = store.getJSONObject("types")
        val editor = prefs.edit().clear()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = values.get(key)
            when (types.getString(key)) {
                "string" -> editor.putString(key, value.toString())
                "boolean" -> editor.putBoolean(key, value as Boolean)
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
                else -> error("Unsupported save preference type")
            }
        }
        check(editor.commit()) { "Unable to restore protected save" }
    }

    fun canonicalBytes(prefs: SharedPreferences): ByteArray =
        encode(prefs).toString().toByteArray(Charsets.UTF_8)
}

/** AES-GCM encryption for device-bound automatic saves and password-protected transfer saves. */
internal object PuppySaveCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val DEVICE_KEY_ALIAS = "puppy_clicker_save_aes_v1"
    private const val DEVICE_MAGIC = "PCE1"
    private const val TRANSFER_FORMAT = "puppy-clicker-encrypted-save"
    private const val TRANSFER_VERSION = 3
    private const val PBKDF2_ITERATIONS = 210_000
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val SALT_BYTES = 16
    private const val DEVICE_AAD = "PuppyClicker/device-save/v1"
    private const val TRANSFER_AAD = "PuppyClicker/transfer-save/v3"
    private val random = SecureRandom()

    fun encryptDevice(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        val iv = cipher.iv
        require(iv.size == IV_BYTES) { "Unexpected Android Keystore GCM IV length: ${iv.size}" }
        cipher.updateAAD(DEVICE_AAD.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plain)
        return DEVICE_MAGIC.toByteArray(Charsets.US_ASCII) + iv + encrypted
    }

    fun decryptDevice(container: ByteArray): ByteArray {
        val magic = DEVICE_MAGIC.toByteArray(Charsets.US_ASCII)
        require(container.size > magic.size + IV_BYTES + 16) { "Encrypted save is too short" }
        require(container.copyOfRange(0, magic.size).contentEquals(magic)) { "Invalid encrypted save header" }
        val ivStart = magic.size
        val iv = container.copyOfRange(ivStart, ivStart + IV_BYTES)
        val encrypted = container.copyOfRange(ivStart + IV_BYTES, container.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(DEVICE_AAD.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted)
    }

    fun encryptTransfer(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 8) { "Backup password must be at least 8 characters" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = passwordKey(password, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(TRANSFER_AAD.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plain)
        val envelope = JSONObject().apply {
            put("format", TRANSFER_FORMAT)
            put("version", TRANSFER_VERSION)
            put("cipher", "AES-256-GCM")
            put("kdf", "PBKDF2-HMAC-SHA256")
            put("iterations", PBKDF2_ITERATIONS)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("ciphertext", b64(encrypted))
        }
        return envelope.toString().toByteArray(Charsets.UTF_8)
    }

    fun decryptTransfer(container: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 8) { "Backup password must be at least 8 characters" }
        val envelope = JSONObject(container.toString(Charsets.UTF_8))
        require(envelope.optString("format") == TRANSFER_FORMAT) { "Not an encrypted Puppy Clicker save" }
        require(envelope.optInt("version") == TRANSFER_VERSION) { "Unsupported encrypted save version" }
        val iterations = envelope.getInt("iterations")
        require(iterations in 100_000..1_000_000) { "Invalid key-derivation settings" }
        val salt = unb64(envelope.getString("salt"))
        val iv = unb64(envelope.getString("iv"))
        val encrypted = unb64(envelope.getString("ciphertext"))
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "Invalid encrypted save parameters" }
        val key = passwordKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(TRANSFER_AAD.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted)
    }

    private fun passwordKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun deviceKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(DEVICE_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                DEVICE_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}

internal data class PupEyeSecurityState(
    val tamperEvents: Int,
    val lastReason: String?,
    val lastDetectedAtMs: Long
)

/**
 * Keeps a Keystore-encrypted last-known-good copy of the runtime save. If the private
 * preference store changes outside an app-authorized save path, PupEye restores the
 * last sealed state and records the integrity failure.
 */
internal object PupEyeSaveGuard {
    private const val SECURITY_PREFS = "pupeye_security_v1"
    private const val BACKUP_FILE = "pupeye/last_good_save.pup"
    private const val KEY_TAMPER_COUNT = "tamper_count"
    private const val KEY_LAST_REASON = "last_reason"
    private const val KEY_LAST_TIME = "last_time"

    fun verifyAndRecover(context: Context, prefs: SharedPreferences): Boolean {
        val backup = File(context.noBackupFilesDir, BACKUP_FILE)
        if (!backup.isFile) {
            seal(context, prefs)
            return true
        }
        return runCatching {
            val protected = backup.readBytes()
            val expected = PuppySaveCrypto.decryptDevice(protected)
            val actual = SecurePreferenceCodec.canonicalBytes(prefs)
            if (!MessageDigest.isEqual(expected, actual)) {
                SecurePreferenceCodec.restore(prefs, JSONObject(expected.toString(Charsets.UTF_8)))
                recordTamper(context, "Runtime save integrity mismatch; restored last known good save")
                false
            } else true
        }.getOrElse {
            recordTamper(context, "Protected save seal failed authentication")
            false
        }
    }

    fun seal(context: Context, prefs: SharedPreferences) {
        runCatching {
            val target = File(context.noBackupFilesDir, BACKUP_FILE)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeBytes(PuppySaveCrypto.encryptDevice(SecurePreferenceCodec.canonicalBytes(prefs)))
            if (target.exists() && !target.delete()) error("Unable to replace PupEye save seal")
            if (!temp.renameTo(target)) {
                target.writeBytes(temp.readBytes())
                temp.delete()
            }
        }
    }

    fun recordTamper(context: Context, reason: String) {
        val prefs = context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_TAMPER_COUNT, 0).coerceAtLeast(0)
        prefs.edit()
            .putInt(KEY_TAMPER_COUNT, (count + 1).coerceAtMost(Int.MAX_VALUE))
            .putString(KEY_LAST_REASON, reason.take(180))
            .putLong(KEY_LAST_TIME, System.currentTimeMillis())
            .apply()
    }

    fun state(context: Context): PupEyeSecurityState {
        val prefs = context.getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE)
        return PupEyeSecurityState(
            tamperEvents = prefs.getInt(KEY_TAMPER_COUNT, 0).coerceAtLeast(0),
            lastReason = prefs.getString(KEY_LAST_REASON, null),
            lastDetectedAtMs = prefs.getLong(KEY_LAST_TIME, 0L).coerceAtLeast(0L)
        )
    }
}
