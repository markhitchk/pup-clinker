package com.harleytg.puppycompat

import android.content.Context
import android.util.Base64
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager
import java.io.File
import java.security.KeyStore
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Read-only legacy Kotlin install inspection.
 *
 * An in-place Google Play upgrade retains the same app UID, noBackupFilesDir,
 * SharedPreferences and Android Keystore aliases. This bridge only verifies
 * those records. It MUST NOT seed guest values, create/re-key PupEye devices,
 * extract a session token, update cloud saves or erase Kotlin app data.
 *
 * The production React app remains blocked until an authenticated, server-
 * reconciled account/save migration is implemented and validated.
 */
class PuppyCompatibilityModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {
    override fun getName(): String = "PuppyCompatibility"

    @ReactMethod
    fun inspect(promise: Promise) {
        try {
            val app = reactContext.applicationContext
            val productionPackage = app.packageName == "com.harleytg.puppyclicker"
            val authority = File(app.noBackupFilesDir, "pupeye/authority_state_v2.pup")
            val session = File(app.noBackupFilesDir, "pupeye/supabase_session_v1.pup")
            val enforcement = File(app.noBackupFilesDir, "pupeye/enforcement_v1.pup")
            val mainPrefs = app.getSharedPreferences("puppy_clicker_save", Context.MODE_PRIVATE)
            val legacyDiscord = app.getSharedPreferences("puppy_discord_signup_v1", Context.MODE_PRIVATE)
                .getString("discord_id", null)?.isNotBlank() == true
            val legacyData = authority.isFile || session.isFile || enforcement.isFile ||
                mainPrefs.all.isNotEmpty() || legacyDiscord

            val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val encryptionKey = keystore.getKey("puppy_clicker_save_aes_v1", null) as? SecretKey
            val signingKey = keystore.getKey("pupeye_install_signing_ec_v2", null)
                as? java.security.PrivateKey
            val signingPublic = keystore.getCertificate("pupeye_install_signing_ec_v2")?.publicKey
            val signingValid = runCatching {
                if (signingKey == null || signingPublic == null) return@runCatching false
                val challenge = "PuppyClicker/React-legacy-inspection/v1".toByteArray(Charsets.UTF_8)
                val signature = Signature.getInstance("SHA256withECDSA").run {
                    initSign(signingKey)
                    update(challenge)
                    sign()
                }
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(signingPublic)
                    update(challenge)
                    verify(signature)
                }
            }.getOrDefault(false)

            // Read the old authority state using exactly Kotlin's PCE1 AES-GCM
            // container/AAD. Never generate a replacement key for a failed read.
            val authorityState = runCatching {
                if (!authority.isFile || encryptionKey == null) return@runCatching null
                if (authority.length() !in 33L..262144L) return@runCatching null
                val data = authority.readBytes()
                if (!data.copyOfRange(0, 4).contentEquals("PCE1".toByteArray(Charsets.US_ASCII)))
                    return@runCatching null
                val iv = data.copyOfRange(4, 16)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(128, iv))
                cipher.updateAAD("PuppyClicker/device-save/v1".toByteArray(Charsets.UTF_8))
                val state = JSONObject(cipher.doFinal(data.copyOfRange(16, data.size))
                    .toString(Charsets.UTF_8))
                if (state.optInt("schema") != 2 || state.optString("installationId").isBlank() ||
                    state.optLong("generation", 0L) < 1L) return@runCatching null
                state
            }.getOrNull()

            val authorityValid = authorityState != null && signingValid
            val status = when {
                legacyData && !authorityValid -> "legacy-integrity-review-required"
                legacyData -> "legacy-requires-authoritative-migration"
                productionPackage -> "production-account-migration-required"
                else -> "preview-fresh-install"
            }
            val result = Arguments.createMap().apply {
                putString("status", status)
                putBoolean("requiresMigration", productionPackage || legacyData)
                putBoolean("legacyDataDetected", legacyData)
                putBoolean("authorityVerified", authorityValid)
                putBoolean("hasEncryptedCloudSession", session.isFile)
                putBoolean("discordPreviouslyConnected", legacyDiscord)
                putBoolean("isProductionPackage", productionPackage)
                putBoolean("inspectIsReadOnly", true)
                // Do not expose installation IDs, signing keys, save content,
                // session tokens or Discord OAuth secrets across the JS bridge.
            }
            promise.resolve(result)
        } catch (error: Exception) {
            promise.reject("PUPPY_COMPATIBILITY_INSPECTION_FAILED",
                "Could not verify existing Puppy Clicker installation data.", error)
        }
    }
}

class PuppyCompatibilityPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(PuppyCompatibilityModule(context))

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
