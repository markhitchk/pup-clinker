package com.harleytg.puppyproduction

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
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only handoff to the ORIGINAL Kotlin/PupEye installation identity.
 *
 * Never generates replacement keys, clears legacy saves, imports guest progress,
 * uploads progression, or exposes a Pupeye session token to JavaScript.
 * The original Google Play signing identity is necessary to access this sandbox.
 */
class PuppyProductionAuthorityModule(private val reactCtx: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactCtx) {
  override fun getName() = "PuppyProductionAuthority"

  private val authorityFile get() = File(reactCtx.noBackupFilesDir, "pupeye/authority_state_v2.pup")
  private val sessionFile get() = File(reactCtx.noBackupFilesDir, "pupeye/supabase_session_v1.pup")
  private val sealedFile get() = File(reactCtx.noBackupFilesDir, "pupeye/last_good_save.pup")
  private val mainSaveFile get() = File(reactCtx.applicationInfo.dataDir, "shared_prefs/puppy_clicker_save.xml")
  private val keyStore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

  @ReactMethod fun inspect(promise: Promise) {
    try {
      val store = keyStore
      val info = Arguments.createMap().apply {
        putString("packageName", reactCtx.packageName)
        putBoolean("hasOriginalSave", mainSaveFile.isFile)
        putBoolean("hasSealedSave", sealedFile.isFile)
        putBoolean("hasAuthorityState", authorityFile.isFile)
        putBoolean("hasOriginalSession", sessionFile.isFile)
        putBoolean("hasOriginalSigningKey", store.containsAlias("pupeye_install_signing_ec_v2"))
        putBoolean("hasOriginalEncryptionKey", store.containsAlias("puppy_clicker_save_aes_v1"))
      }
      promise.resolve(info)
    } catch (error: Exception) {
      promise.reject("PUPEYE_INSPECTION_FAILED", "Unable to inspect the original device identity", error)
    }
  }

  private fun decryptOriginal(file: File): JSONObject {
    require(file.isFile) { "Original PupEye record is missing" }
    val content = file.readBytes()
    require(content.size > 32 && content.copyOfRange(0,4).contentEquals("PCE1".toByteArray(Charsets.US_ASCII))) {
      "Original encrypted PupEye record has an invalid header"
    }
    val secret = keyStore.getKey("puppy_clicker_save_aes_v1", null) as? SecretKey
      ?: error("Original Android encryption key is not available")
    val iv = content.copyOfRange(4,16)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
    cipher.updateAAD("PuppyClicker/device-save/v1".toByteArray(Charsets.UTF_8))
    return JSONObject(String(cipher.doFinal(content.copyOfRange(16,content.size)), Charsets.UTF_8))
  }

  private fun canonical(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> "{" + value.keys().asSequence().toList().sorted()
      .joinToString(",") { JSONObject.quote(it) + ":" + canonical(value.get(it)) } + "}"
    is JSONArray -> "[" + (0 until value.length()).joinToString(",") { canonical(value.get(it)) } + "]"
    is String -> JSONObject.quote(value)
    is Boolean, is Number -> value.toString()
    else -> error("Unsupported signed payload value")
  }

  private fun digestHex(value: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(value)
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }

  /** The native module makes this request itself: the session never enters JS. */
  @ReactMethod fun accountStatus(publishableKey: String, promise: Promise) {
    if (!publishableKey.startsWith("sb_publishable_") || publishableKey.length > 250) {
      promise.reject("PUBLISHABLE_KEY_REQUIRED", "The production publishable key is not configured")
      return
    }
    Thread {
      try {
        val authority = decryptOriginal(authorityFile)
        require(authority.getInt("schema") == 2) { "Unsupported original PupEye authority schema" }
        val session = decryptOriginal(sessionFile)
        val now = System.currentTimeMillis()
        require(session.getLong("expiresAtEpochMs") > now + 5_000) { "Original Pupeye session expired; Discord reauthorization is required" }
        val token = session.getString("token")
        require(token.isNotBlank()) { "Original Pupeye session token is missing" }
        val installationId = authority.getString("installationId")
        val generation = authority.getLong("generation")
        require(generation > 0) { "Original authority generation is invalid" }
        val store = keyStore
        val privateKey = store.getKey("pupeye_install_signing_ec_v2", null) as? java.security.PrivateKey
          ?: error("Original Pupeye signing key is missing")
        val publicKey = store.getCertificate("pupeye_install_signing_ec_v2")?.publicKey?.encoded
          ?: error("Original Pupeye public key is missing")
        val keyId = digestHex(publicKey)
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val payload = JSONObject()
        val material = listOf(
          "PuppyClicker/PupEye/request/v1", "account-status", installationId,
          keyId, generation.toString(), timestamp.toString(), nonce, canonical(payload)
        ).joinToString("\n")
        val signature = Signature.getInstance("SHA256withECDSA").run {
          initSign(privateKey)
          update(material.toByteArray(Charsets.UTF_8))
          sign()
        }
        val base64Flags = Base64.NO_WRAP
        val envelope = JSONObject().apply {
          put("schema", 1)
          put("action", "account-status")
          put("installationId", installationId)
          put("deviceKeyId", keyId)
          put("generation", generation)
          put("timestampEpochMs", timestamp)
          put("nonce", nonce)
          put("publicKey", Base64.encodeToString(publicKey, base64Flags))
          put("payload", payload)
          put("signature", Base64.encodeToString(signature, base64Flags))
        }
        // Pinned production project/function: the bearer token cannot be sent
        // to a caller-selected endpoint, even if the JavaScript is modified.
        val target = URL("https://damcmyqusdxucwozkoeh.supabase.co/functions/v1/pup-account")
        val connection = target.openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "POST"
          connection.connectTimeout = 30_000
          connection.readTimeout = 45_000
          connection.doOutput = true
          connection.setRequestProperty("Content-Type", "application/json")
          connection.setRequestProperty("apikey", publishableKey)
          connection.setRequestProperty("X-Pupeye-Session", token)
          connection.outputStream.use { it.write(envelope.toString().toByteArray(Charsets.UTF_8)) }
          val code = connection.responseCode
          if (code != 200) {
            // Never echo server details or raw response bodies containing player info.
            error("Pup Account verification was rejected (HTTP $code)")
          }
          val bytes = connection.inputStream.use { input ->
            input.readNBytes(262_145)
          }
          require(bytes.size <= 262_144) { "Account status response exceeded limit" }
          val result = JSONObject(String(bytes, Charsets.UTF_8))
          val account = result.getJSONObject("account")
          require(account.getString("status") == "active") { "Account is not active" }
          val save = result.getJSONObject("cloudSave")
          val response = Arguments.createMap().apply {
            putBoolean("verified", true)
            putString("username", account.getString("username"))
            putString("accountId", account.getString("id"))
            putBoolean("cloudSaveExists", save.getBoolean("exists"))
            putDouble("cloudRevision", save.getLong("revision").toDouble())
            putDouble("cloudGeneration", save.getLong("generation").toDouble())
            putString("guildRole", result.optString("guildRole", ""))
          }
          promise.resolve(response)
        } finally {
          connection.disconnect()
        }
      } catch (error: Exception) {
        promise.reject("PUP_ACCOUNT_VERIFY_FAILED",
          error.message?.take(150) ?: "Production Pup Account verification failed", error)
      }
    }.start()
  }
}

class PuppyProductionAuthorityPackage : ReactPackage {
  override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
    listOf(PuppyProductionAuthorityModule(context))
  override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
    emptyList()
}
