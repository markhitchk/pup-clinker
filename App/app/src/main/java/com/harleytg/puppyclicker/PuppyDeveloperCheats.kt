package com.harleytg.puppyclicker

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

internal data class PuppyDeveloperCheatSessionState(
    val active: Boolean = false,
    val overlayExpanded: Boolean = false
)

/**
 * Developer-cheat authorization is session-only. It intentionally resets when
 * the process is recreated. Casino rounds carry their own devtest_ marker so a
 * round started while cheats are active stays void even after cheats are disabled.
 */
internal object PuppyDeveloperCheatsSession {
    private const val DEV_ROUND_PREFIX = "devtest_"
    private val mutableState =
        kotlinx.coroutines.flow.MutableStateFlow(PuppyDeveloperCheatSessionState())
    val state: kotlinx.coroutines.flow.StateFlow<PuppyDeveloperCheatSessionState> =
        mutableState

    fun isActive(): Boolean = mutableState.value.active

    fun activate() {
        mutableState.value = PuppyDeveloperCheatSessionState(
            active = true,
            overlayExpanded = false
        )
        PuppyDebugLog.w(
            "DeveloperCheats",
            "Developer Cheats activated; DEV casino winnings are void."
        )
    }

    fun disable() {
        mutableState.value = PuppyDeveloperCheatSessionState()
        PuppyDebugLog.i("DeveloperCheats", "Developer Cheats disabled.")
    }

    fun setOverlayExpanded(expanded: Boolean) {
        if (!mutableState.value.active) return
        mutableState.value = mutableState.value.copy(overlayExpanded = expanded)
    }

    fun newTestRoundId(): String =
        DEV_ROUND_PREFIX + PuppyCasinoRoundIds.newId()

    fun isTestRoundId(roundId: String?): Boolean =
        roundId?.startsWith(DEV_ROUND_PREFIX) == true
}

internal data class PuppyDeveloperPinResult(
    val success: Boolean,
    val message: String,
    val retryAfterMs: Long = 0L
)

/**
 * The PIN itself is never stored as plaintext.
 *
 * Verification layers:
 * 1) PBKDF2-HMAC-SHA256 derives a fixed verifier from the entered PIN.
 * 2) The expected verifier is wrapped with AES-256-GCM using Android Keystore key #1.
 * 3) That encrypted blob is wrapped again with AES-256-GCM using Android Keystore key #2.
 */
internal object PuppyDeveloperPinGate {
    private const val PREFS = "puppy_developer_cheat_security_v1"
    private const val OUTER_IV = "pin_outer_iv"
    private const val OUTER_BLOB = "pin_outer_blob"
    private const val FAILURES = "pin_failures"
    private const val LOCKOUT_UNTIL = "pin_lockout_until"
    private const val LOCKOUT_LEVEL = "pin_lockout_level"

    private const val KEY_ALIAS_INNER = "puppy_developer_cheat_pin_inner_v1"
    private const val KEY_ALIAS_OUTER = "puppy_developer_cheat_pin_outer_v1"
    private const val ITERATIONS = 210_000
    private const val MAX_ATTEMPTS = 5
    private const val BASE_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 15 * 60_000L

    // PBKDF2 verifier for the configured five-digit developer PIN.
    private const val SALT_B64 = "lBCmVLfkSBrN/9eXuNLH3w=="
    private const val EXPECTED_VERIFIER_B64 =
        "FKMSUiN+C7WLtL61PZYAZAWipGX97hKxYvhqbVj5+ZM="

    fun verify(context: Context, enteredPin: String): PuppyDeveloperPinResult {
        val app = context.applicationContext
        if (
            !PuppyDeveloperPreferences.current(app).unlocked &&
            !PuppyPlayerIdentity.isHarleyTgDeveloper(app)
        ) {
            return PuppyDeveloperPinResult(false, "Developer Mode is not authorized.")
        }

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lockoutUntil = prefs.getLong(LOCKOUT_UNTIL, 0L)
        if (lockoutUntil > now) {
            return PuppyDeveloperPinResult(
                success = false,
                message = "Developer PIN temporarily locked.",
                retryAfterMs = lockoutUntil - now
            )
        }

        if (enteredPin.length != 5 || enteredPin.any { !it.isDigit() }) {
            return recordFailure(prefs, now)
        }

        return try {
            ensureWrappedVerifier(prefs)
            val expected = decryptWrappedVerifier(prefs)
            val candidate = deriveVerifier(enteredPin)
            if (MessageDigest.isEqual(expected, candidate)) {
                prefs.edit()
                    .putInt(FAILURES, 0)
                    .putLong(LOCKOUT_UNTIL, 0L)
                    .putInt(LOCKOUT_LEVEL, 0)
                    .apply()
                PuppyDeveloperPinResult(true, "Developer PIN accepted.")
            } else {
                recordFailure(prefs, now)
            }
        } catch (error: Exception) {
            PuppyDebugLog.e(
                "DeveloperCheats",
                "Developer PIN security store unavailable: ${error.javaClass.simpleName}"
            )
            PuppyDeveloperPinResult(false, "Developer PIN security is unavailable.")
        }
    }

    private fun recordFailure(
        prefs: android.content.SharedPreferences,
        now: Long
    ): PuppyDeveloperPinResult {
        val failures = prefs.getInt(FAILURES, 0) + 1
        if (failures < MAX_ATTEMPTS) {
            prefs.edit().putInt(FAILURES, failures).apply()
            return PuppyDeveloperPinResult(
                false,
                "Invalid developer PIN. ${MAX_ATTEMPTS - failures} attempts remaining."
            )
        }

        val level = prefs.getInt(LOCKOUT_LEVEL, 0).coerceIn(0, 5)
        val duration = (BASE_LOCKOUT_MS * (1L shl level))
            .coerceAtMost(MAX_LOCKOUT_MS)
        prefs.edit()
            .putInt(FAILURES, 0)
            .putInt(LOCKOUT_LEVEL, (level + 1).coerceAtMost(5))
            .putLong(LOCKOUT_UNTIL, now + duration)
            .apply()
        return PuppyDeveloperPinResult(
            success = false,
            message = "Too many invalid attempts. Developer PIN temporarily locked.",
            retryAfterMs = duration
        )
    }

    private fun ensureWrappedVerifier(prefs: android.content.SharedPreferences) {
        if (
            prefs.contains(OUTER_IV) &&
            prefs.contains(OUTER_BLOB)
        ) return

        val verifier = Base64.decode(EXPECTED_VERIFIER_B64, Base64.NO_WRAP)
        val inner = encrypt(getOrCreateKey(KEY_ALIAS_INNER), verifier)
        val packedInner = ByteBuffer.allocate(4 + inner.first.size + inner.second.size)
            .putInt(inner.first.size)
            .put(inner.first)
            .put(inner.second)
            .array()
        val outer = encrypt(getOrCreateKey(KEY_ALIAS_OUTER), packedInner)

        prefs.edit()
            .putString(OUTER_IV, Base64.encodeToString(outer.first, Base64.NO_WRAP))
            .putString(OUTER_BLOB, Base64.encodeToString(outer.second, Base64.NO_WRAP))
            .commit()
    }

    private fun decryptWrappedVerifier(
        prefs: android.content.SharedPreferences
    ): ByteArray {
        val outerIv = Base64.decode(
            requireNotNull(prefs.getString(OUTER_IV, null)),
            Base64.NO_WRAP
        )
        val outerBlob = Base64.decode(
            requireNotNull(prefs.getString(OUTER_BLOB, null)),
            Base64.NO_WRAP
        )
        val packed = decrypt(getOrCreateKey(KEY_ALIAS_OUTER), outerIv, outerBlob)
        val buffer = ByteBuffer.wrap(packed)
        val innerIvLength = buffer.int
        require(innerIvLength in 12..32)
        require(buffer.remaining() > innerIvLength)
        val innerIv = ByteArray(innerIvLength)
        buffer.get(innerIv)
        val innerBlob = ByteArray(buffer.remaining())
        buffer.get(innerBlob)
        return decrypt(getOrCreateKey(KEY_ALIAS_INNER), innerIv, innerBlob)
    }

    private fun deriveVerifier(pin: String): ByteArray {
        val salt = Base64.decode(SALT_B64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(key: SecretKey, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv to cipher.doFinal(plain)
    }

    private fun decrypt(
        key: SecretKey,
        iv: ByteArray,
        encrypted: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }
}

@Composable
internal fun PuppyDeveloperCheatsSettings(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val session by PuppyDeveloperCheatsSession.state.collectAsStateWithLifecycle()
    var pin by remember { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmActivation by rememberSaveable { mutableStateOf(false) }

    Spacer(Modifier.width(1.dp))
    Text("Developer Cheats", fontWeight = FontWeight.Black)
    Text(
        "PIN-gated testing controls. Cheat authorization lasts only for this app session.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (session.active) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "🛠 DEV CHEATS ACTIVE · WINNINGS VOID",
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Casino rounds started in this mode do not charge a real wager, pay Treat winnings, grant Upgrade Tickets, or unlock casino puppies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Current Treats: ${state.treats} · Tickets: ${state.ticketsOwned}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Button(
                    onClick = {
                        PuppyDeveloperCheatsSession.disable()
                        vm.clearDeveloperCheatOverrides()
                        status = "Developer Cheats disabled. New casino rounds are normal."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Disable Developer Cheats")
                }
            }
        }
    } else {
        OutlinedTextField(
            value = pin,
            onValueChange = { value ->
                pin = value.filter(Char::isDigit).take(5)
                status = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            label = { Text("Developer access PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation()
        )
        Button(
            onClick = {
                val result = PuppyDeveloperPinGate.verify(context, pin)
                status = if (result.retryAfterMs > 0L) {
                    result.message + " Try again in " +
                        ((result.retryAfterMs + 999L) / 1000L) + "s."
                } else {
                    result.message
                }
                if (result.success) confirmActivation = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = pin.length == 5
        ) {
            Text("Unlock Developer Cheats")
        }
    }

    status?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (confirmActivation) {
        AlertDialog(
            onDismissRequest = { confirmActivation = false },
            title = { Text("⚠️ Activate Developer Cheats?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Developer Cheats are intended for Puppy Clicker testing only.")
                    Text("Activating this mode voids all casino winnings from DEV TEST rounds.")
                    Text("Treat payouts, Upgrade Ticket rewards, and casino puppy unlock rewards are not granted.")
                    Text("A DEV TEST round remains void even if cheats are disabled before that round finishes.")
                    Text("Developer Cheats can be disabled at any time from Developer Tools or the overlay.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        PuppyDeveloperCheatsSession.activate()
                        pin = ""
                        status = "Developer Cheats activated. Casino winnings are void."
                        confirmActivation = false
                    }
                ) {
                    Text("Activate Developer Cheats")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmActivation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun PuppyDeveloperCheatOverlay(
    state: V6GameState,
    vm: PuppyClickerV6ViewModel,
    modifier: Modifier = Modifier
) {
    val session by PuppyDeveloperCheatsSession.state.collectAsStateWithLifecycle()
    if (!session.active) return

    val casinoRound by vm.casinoRound.collectAsStateWithLifecycle()
    val nextCasinoPuppy = PuppyCasinoPuppyRewardEngine.eligibleStyleIds
        .firstOrNull { it !in state.unlockedPuppies }

    if (!session.overlayExpanded) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
        ) {
            TextButton(
                onClick = { PuppyDeveloperCheatsSession.setOverlayExpanded(true) },
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    "🛠 DEV · VOID",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        tonalElevation = 1.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🛠 DEV · VOID",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium
                )
                TextButton(
                    onClick = { PuppyDeveloperCheatsSession.setOverlayExpanded(false) }
                ) {
                    Text("−")
                }
            }

            Text(
                "T ${state.treats} · 🎟 ${state.ticketsOwned} · " +
                    (casinoRound?.let { it.game.name } ?: "Casino idle"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TextButton(
                    onClick = { vm.developerAddTreats(1_000L) },
                    modifier = Modifier.weight(1f)
                ) { Text("+1K") }
                TextButton(
                    onClick = { vm.developerAddTreats(10_000L) },
                    modifier = Modifier.weight(1f)
                ) { Text("+10K") }
                TextButton(
                    onClick = { vm.developerFillCare() },
                    modifier = Modifier.weight(1f)
                ) { Text("Care") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                TicketRarity.entries.forEach { rarity ->
                    TextButton(
                        onClick = { vm.developerAddTicket(rarity) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(rarity.emoji)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TextButton(
                    onClick = { vm.developerAddSkillPoints(10) },
                    modifier = Modifier.weight(1f)
                ) { Text("+10 SP") }
                TextButton(
                    onClick = { vm.developerClearPupEyeCooldown() },
                    modifier = Modifier.weight(1f)
                ) { Text("PupEye") }
            }

            if (nextCasinoPuppy != null) {
                TextButton(
                    onClick = { vm.developerUnlockPuppy(nextCasinoPuppy) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "🐶 Unlock " +
                            (DynamicPuppyRoster.style(nextCasinoPuppy)?.name ?: nextCasinoPuppy),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { PuppyDeveloperCheatsSession.setOverlayExpanded(false) }
                ) {
                    Text("Hide")
                }
                TextButton(
                    onClick = {
                        PuppyDeveloperCheatsSession.disable()
                        vm.clearDeveloperCheatOverrides()
                    }
                ) {
                    Text(
                        "Disable",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
