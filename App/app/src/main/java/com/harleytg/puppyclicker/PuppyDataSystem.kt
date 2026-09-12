package com.harleytg.puppyclicker

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import java.io.File
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import org.json.JSONException
import org.json.JSONObject

/**
 * Save compatibility, external save handling, crypto diagnostics, and code anti-abuse data rules.
 *
 * Consolidated as part of the six-system Kotlin architecture.
 */

// ---- PuppySaveCompatibility ----
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

// ---- ExternalGameSave ----
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

// ---- SaveCryptoDiagnostics ----
internal enum class SaveCryptoFailureKind { TAMPER, OPERATIONAL }

internal fun classifySaveCryptoFailure(error: Throwable): SaveCryptoFailureKind = when (error) {
    is AEADBadTagException,
    is BadPaddingException,
    is IllegalArgumentException,
    is JSONException -> SaveCryptoFailureKind.TAMPER

    is InvalidAlgorithmParameterException,
    is InvalidKeyException,
    is KeyStoreException,
    is GeneralSecurityException -> SaveCryptoFailureKind.OPERATIONAL

    else -> SaveCryptoFailureKind.OPERATIONAL
}

internal class RepeatedFailureLogGate(
    private val cooldownMs: Long = 60_000L
) {
    private var lastSignature: String? = null
    private var lastLoggedAtMs: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldLog(error: Throwable, nowMs: Long = System.currentTimeMillis()): Boolean {
        val signature = "${error.javaClass.name}:${error.message.orEmpty()}"
        val changed = signature != lastSignature
        val cooldownElapsed = lastLoggedAtMs == Long.MIN_VALUE || nowMs - lastLoggedAtMs >= cooldownMs
        if (!changed && !cooldownElapsed) return false
        lastSignature = signature
        lastLoggedAtMs = nowMs
        return true
    }

    @Synchronized
    fun markSuccess(): Boolean {
        val recovering = lastSignature != null
        lastSignature = null
        lastLoggedAtMs = Long.MIN_VALUE
        return recovering
    }
}

// ---- PuppyCodeAntiAbuse ----
object PuppyCodeAntiAbuse {
    private const val INVALID_WINDOW_MS = 2L * 60L * 1_000L
    private const val ESCALATION_WINDOW_MS = 10L * 60L * 1_000L
    private const val RAPID_WINDOW_MS = 10_000L
    private const val FIRST_COOLDOWN_MS = 30_000L
    private const val REPEAT_COOLDOWN_MS = 60_000L
    private const val PUPEYE_COOLDOWN_MS = 60_000L
    private const val MAX_COOLDOWN_MS = 5L * 60L * 1_000L

    data class State(
        val invalidAttemptTimes: List<Long> = emptyList(),
        val submissionTimes: List<Long> = emptyList(),
        val lastPenaltyAtMs: Long = 0L,
        val cooldownUntilMs: Long = 0L,
        val pupEyeEscalated: Boolean = false
    )

    fun recordSubmission(state: State, nowMs: Long): State {
        val recent = (state.submissionTimes + nowMs).filter { it >= nowMs - RAPID_WINDOW_MS }
        if (recent.size < 5) return state.copy(submissionTimes = recent)

        val requestedUntil = safeAdd(nowMs, PUPEYE_COOLDOWN_MS)
        return state.copy(
            submissionTimes = recent,
            cooldownUntilMs = maxOf(state.cooldownUntilMs, requestedUntil).coerceAtMost(safeAdd(nowMs, MAX_COOLDOWN_MS)),
            lastPenaltyAtMs = nowMs,
            pupEyeEscalated = true
        )
    }

    fun recordInvalid(state: State, nowMs: Long): State {
        val recent = (state.invalidAttemptTimes + nowMs).filter { it >= nowMs - INVALID_WINDOW_MS }
        if (recent.size < 5) return state.copy(invalidAttemptTimes = recent)

        val isRepeatedBurst = state.lastPenaltyAtMs > 0L && nowMs - state.lastPenaltyAtMs in 0..ESCALATION_WINDOW_MS
        val penalty = if (isRepeatedBurst) REPEAT_COOLDOWN_MS else FIRST_COOLDOWN_MS
        return state.copy(
            invalidAttemptTimes = recent,
            lastPenaltyAtMs = nowMs,
            cooldownUntilMs = maxOf(state.cooldownUntilMs, safeAdd(nowMs, penalty))
                .coerceAtMost(safeAdd(nowMs, MAX_COOLDOWN_MS))
        )
    }

    fun recordSuccess(state: State): State = state.copy(
        invalidAttemptTimes = emptyList(),
        submissionTimes = emptyList(),
        pupEyeEscalated = false
    )

    fun remainingCooldownMs(state: State, nowMs: Long): Long =
        (state.cooldownUntilMs - nowMs).coerceAtLeast(0L)

    private fun safeAdd(a: Long, b: Long): Long =
        if (b > 0L && a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
}