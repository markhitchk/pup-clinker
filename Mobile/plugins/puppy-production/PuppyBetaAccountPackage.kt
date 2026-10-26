package com.harleytg.puppyproduction

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import org.json.*

/** Separate .beta Keystore identity; never accesses the production app sandbox. */
class PuppyBetaAccountModule(private val ctx:ReactApplicationContext):ReactContextBaseJavaModule(ctx) {
 override fun getName()="PuppyBetaAccount"
 override fun getConstants():Map<String,Any> = mapOf("isBeta" to (ctx.packageName=="com.harleytg.puppyclicker.beta"))
 private val prefs get()=ctx.getSharedPreferences("puppy_beta_discord_v1",0)
 private val player get()=ctx.getSharedPreferences("puppy_player_identity_v1",0)
 private val setup get()=ctx.getSharedPreferences("puppy_ui_preferences_v2",0)
 private val stateFile get()=File(ctx.noBackupFilesDir,"pupeye/authority_state_v2.pup")
 private val sessionFile get()=File(ctx.noBackupFilesDir,"pupeye/supabase_session_v1.pup")
 // Encrypted and device-bound; never an authority for economy writes.
 private val accountPreviewFile get()=File(ctx.noBackupFilesDir,"pupeye/account_preview_v1.pup")
 private val store get()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
 private val random=SecureRandom()
 private val root="https://damcmyqusdxucwozkoeh.supabase.co/functions/v1/"
 private val redirect="discord-1547982688898777118:/authorize/callback"
 private val keyAlias="pupeye_install_signing_ec_v2"
 private val aesAlias="puppy_clicker_save_aes_v1"
 private fun beta()=require(ctx.packageName=="com.harleytg.puppyclicker.beta"){"Only beta can create a new identity"}
 private fun publishable(key:String)=require(key.startsWith("sb_publishable_")&&key.length<250){"Public API key required"}
 private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
 private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it.toInt() and 255)}
 private fun b64(bytes:ByteArray)=Base64.encodeToString(bytes,Base64.NO_WRAP)
 private fun canon(v:Any?):String=when(v){
  null,JSONObject.NULL->"null"
  is JSONObject->"{"+v.keys().asSequence().toList().sorted().joinToString(","){JSONObject.quote(it)+":"+canon(v.get(it))}+"}"
  is JSONArray->"["+(0 until v.length()).joinToString(","){canon(v.get(it))}+"]"
  is String->JSONObject.quote(v)
  is Boolean,is Number->v.toString()
  else->error("Invalid signed data")
 }
 private fun seal(json:JSONObject):ByteArray {
  val secret=store.getKey(aesAlias,null) as SecretKey
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE,secret)
  cipher.updateAAD("PuppyClicker/device-save/v1".toByteArray())
  return "PCE1".toByteArray()+cipher.iv+cipher.doFinal(json.toString().toByteArray())
 }
 private fun write(file:File,json:JSONObject){
  file.parentFile?.mkdirs()
  val tmp=File(file.parentFile,file.name+".tmp")
  tmp.writeBytes(seal(json))
  check(tmp.renameTo(file)||run{file.writeBytes(tmp.readBytes());tmp.delete();true})
 }
 private fun read(file:File):JSONObject {
  require(file.isFile){"No secured account session"}
  val bytes=file.readBytes()
  require(bytes.size>32&&bytes.copyOfRange(0,4).contentEquals("PCE1".toByteArray())){"Bad secured data"}
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE,store.getKey(aesAlias,null) as SecretKey,GCMParameterSpec(128,bytes.copyOfRange(4,16)))
  cipher.updateAAD("PuppyClicker/device-save/v1".toByteArray())
  return JSONObject(String(cipher.doFinal(bytes.copyOfRange(16,bytes.size))))
 }
 private fun initializeBetaIdentity() {
  beta()
  if(!store.containsAlias(keyAlias)){
   val gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore")
   gen.initialize(KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
   gen.generateKeyPair()
  }
  if(!store.containsAlias(aesAlias)){
   val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
   gen.init(KeyGenParameterSpec.Builder(aesAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256).build())
   gen.generateKey()
  }
  if(!stateFile.isFile)write(stateFile,JSONObject().put("schema",2)
   .put("installationId",UUID.randomUUID().toString()).put("generation",1)
   .put("hardFlags",JSONArray()).put("events",JSONArray()))
  if(!player.contains("player_id")){
   val bytes=ByteArray(16).also(random::nextBytes)
   val id="PC-"+bytes.joinToString(""){"%02X".format(it.toInt() and 255)}
   val alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
   val raw=(0 until 12).map{alphabet[random.nextInt(alphabet.length)]}.joinToString("")
   val friend="PUP-"+raw.substring(0,4)+"-"+raw.substring(4,8)+"-"+raw.substring(8,12)
   check(player.edit().putString("player_id",id).putString("friend_code",friend)
    .putString("username","localplayer").commit())
  }
 }
 private fun envelope(action:String,payload:JSONObject):JSONObject {
  val state=read(stateFile)
  require(state.getInt("schema")==2)
  val pub=store.getCertificate(keyAlias).publicKey.encoded
  val id=state.getString("installationId")
  val gen=state.getLong("generation")
  val keyId=hex(sha(pub))
  val time=System.currentTimeMillis()
  val nonce=UUID.randomUUID().toString()
  val msg=listOf("PuppyClicker/PupEye/request/v1",action,id,keyId,gen.toString(),time.toString(),nonce,canon(payload)).joinToString("\n")
  val sig=Signature.getInstance("SHA256withECDSA").run {
   initSign(store.getKey(keyAlias,null) as PrivateKey);update(msg.toByteArray());sign()
  }
  return JSONObject().put("schema",1).put("action",action).put("installationId",id)
   .put("deviceKeyId",keyId).put("generation",gen).put("timestampEpochMs",time)
   .put("nonce",nonce).put("publicKey",b64(pub)).put("payload",payload).put("signature",b64(sig))
 }
 private class ApiError(val code:String):Exception(code)
 private fun post(function:String,body:JSONObject,key:String,token:String?=null):JSONObject {
  publishable(key)
  require(function in setOf("pupeye-register","pupeye-auth-discord","pup-account","pup-beta-link","pup-credentials"))
  val c=URL(root+function).openConnection() as HttpURLConnection
  try {
   c.requestMethod="POST";c.connectTimeout=30000;c.readTimeout=90000;c.doOutput=true
   c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("apikey",key)
   if(token!=null)c.setRequestProperty("X-Pupeye-Session",token)
   val bytes=body.toString().toByteArray()
   c.setFixedLengthStreamingMode(bytes.size)
   c.outputStream.use{it.write(bytes)}
   val ok=c.responseCode in 200..299
   val data=(if(ok)c.inputStream else c.errorStream)?.use{it.readNBytes(2097153)}?:ByteArray(0)
   require(data.size<=2097152){"Response too large"}
   val obj=runCatching{JSONObject(String(data))}.getOrDefault(JSONObject())
   if(!ok)throw ApiError(obj.optString("code","HTTP_"+c.responseCode))
   return obj
  }finally{c.disconnect()}
 }
 private fun register(key:String):String {
  initializeBetaIdentity()
  val s=read(stateFile)
  val pub=store.getCertificate(keyAlias).publicKey.encoded
  val support=hex(sha((s.getString("installationId")+"|"+hex(sha(pub))).toByteArray())).take(12).uppercase()
  val payload=JSONObject().put("playerId",player.getString("player_id",""))
   .put("friendCode",player.getString("friend_code","")).put("username","localplayer")
   .put("supportCode",support).put("deviceModel",(Build.MANUFACTURER+"-"+Build.MODEL).take(48))
   .put("platform","android").put("appVersion","1.8.0-beta")
  val result=post("pupeye-register",envelope("register",payload),key)
  val token=result.getString("sessionToken")
  require(token.isNotBlank()&&result.getLong("expiresAtEpochMs")>System.currentTimeMillis())
  write(sessionFile,JSONObject().put("token",token).put("expiresAtEpochMs",result.getLong("expiresAtEpochMs")))
  return token
 }
 private fun session(key:String):String {
  val s=runCatching{read(sessionFile)}.getOrNull()
  return if(s!=null&&s.optLong("expiresAtEpochMs")>System.currentTimeMillis()+5000)
   s.getString("token") else register(key)
 }
 private fun betaRead(key:String,action:String):JSONObject =
  post("pup-beta-link",envelope(action,JSONObject()),key,session(key))
 private fun account(key:String):WritableMap {
  // A Play account may authorize a read-only beta companion using Discord
  // without revoking or transferring its original PupEye installation.
  val obj=try{betaRead(key,"beta-account-status")}
   catch(e:ApiError){
    if(e.code!="BETA_LINK_REQUIRED")throw e
    post("pup-account",envelope("account-status",JSONObject()),key,session(key))
   }
  val a=obj.getJSONObject("account");require(a.getString("status")=="active")
  val save=obj.getJSONObject("cloudSave")
  return Arguments.createMap().apply{
   putString("status","VERIFIED");putString("username",a.getString("username"))
   putBoolean("readOnly",obj.optBoolean("readOnly",false))
   putBoolean("accountWritable",true)
   putBoolean("linkedFromPlay",obj.optBoolean("linkedFromPlay",false))
   putString("accountId",a.getString("id"));putBoolean("cloudSaveExists",save.optBoolean("exists"))
   putString("displayName",a.optString("displayName",a.getString("username")))
   putString("playerId",obj.optString("playerId",player.getString("player_id","")))
   putString("friendCode",obj.optString("friendCode",player.getString("friend_code","")))
   putDouble("cloudRevision",save.optDouble("revision"));putString("guildRole",obj.optString("guildRole",""))
  }
 }
 private fun runAsync(p:Promise, fn:()->Any) {Thread{
  try{beta();p.resolve(fn())}
  catch(e:ApiError){
   val message=when(e.code){
    "PUP_ACCOUNT_ON_OTHER_DEVICE","DISCORD_ALREADY_LINKED" ->
     "Account conflict. The Play app remains intact; contact Support for account recovery."
    "BETA_LINK_REQUIRED" -> "Sign in using your Pup Account or Discord."
    "PUP_USERNAME_TAKEN" -> "This Pup username is already taken. Choose another."
    "PUP_EMAIL_UNVERIFIED" -> "Verify your email from the confirmation message, then sign in."
    "PUP_SIGNIN_INVALID" -> "Incorrect username/email or password."
    "PUP_CREDENTIAL_ALREADY_LINKED" -> "This Pup Account already has a password login."
    "PUP_ACCOUNT_RECOVERY_REQUIRED" -> "Account recovery is required. Contact Support."
    "PUP_SIGNUP_UNAVAILABLE" -> "Could not register. Check your email or sign in."
    "BETA_OWNER_DEVICE_UNAVAILABLE" -> "Original Play app must have an active verified PupEye installation."
    else -> "PupEye server rejected login ("+e.code.take(64)+")."
   }
   p.reject("BETA_"+e.code.take(64),message)
  }catch(e:Exception){p.reject("BETA_LOGIN_FAILED","Could not securely verify this beta login. Original progress was not changed.")}
 }.start()}
 @ReactMethod fun status(key:String,p:Promise)=runAsync(p){
  if(!sessionFile.isFile)Arguments.createMap().apply{putString("status","NEEDS_ACCOUNT")}
  else account(key)
 }
 @ReactMethod fun createPupAccount(username:String,email:String,password:String,key:String,p:Promise)=runAsync(p){
  require(username.matches(Regex("[A-Za-z0-9_-]{3,20}"))){"Invalid Pup username"}
  require(email.length<=254 && email.contains("@") && password.length in 8..128){"Invalid Pup account input"}
  val body=JSONObject().put("username",username).put("email",email.trim()).put("password",password)
  val result=post("pup-credentials",envelope("native-sign-up",body),key,session(key))
  if(result.optString("status")=="VERIFY_EMAIL")Arguments.createMap().apply{
   putString("status","VERIFY_EMAIL")
   putString("message","Check your email to verify your Pup Account, then sign in.")
  } else account(key)
 }
 @ReactMethod fun signInPupAccount(login:String,password:String,key:String,p:Promise)=runAsync(p){
  require(login.isNotBlank()&&login.length<=254&&password.length in 1..128){"Invalid Pup login"}
  post("pup-credentials",envelope("native-sign-in",
   JSONObject().put("login",login.trim()).put("password",password)),key,session(key))
  account(key)
 }
 @ReactMethod fun beginDiscord(p:Promise) {
  try{
   beta()
   val bytes=ByteArray(48).also(random::nextBytes)
   val verifier=Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
   val challenge=Base64.encodeToString(sha(verifier.toByteArray()),Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
   val state=UUID.randomUUID().toString()
   check(prefs.edit().putString("state",state).putString("verifier",verifier)
    .putLong("issued",System.currentTimeMillis()).commit())
   val uri=Uri.parse("https://discord.com/oauth2/authorize").buildUpon()
    .appendQueryParameter("client_id","1547982688898777118").appendQueryParameter("response_type","code")
    .appendQueryParameter("redirect_uri",redirect)
    .appendQueryParameter("scope","identify email guilds guilds.join guilds.members.read")
    .appendQueryParameter("state",state).appendQueryParameter("code_challenge",challenge)
    .appendQueryParameter("code_challenge_method","S256").build()
   ctx.startActivity(Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   p.resolve(true)
  }catch(e:Exception){p.reject("DISCORD_OPEN_FAILED","Could not open Discord login")}
 }
 private fun exchange(code:String,verifier:String):String {
  val fields=mapOf("client_id" to "1547982688898777118","grant_type" to "authorization_code",
   "redirect_uri" to redirect,"code" to code,"code_verifier" to verifier)
  val body=fields.entries.joinToString("&"){Uri.encode(it.key)+"="+Uri.encode(it.value)}
  val c=URL("https://discord.com/api/oauth2/token").openConnection() as HttpURLConnection
  try{
   c.requestMethod="POST";c.connectTimeout=30000;c.readTimeout=60000;c.doOutput=true
   c.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
   c.outputStream.use{it.write(body.toByteArray())}
   require(c.responseCode==200){"Discord token exchange failed"}
   return c.inputStream.use{JSONObject(String(it.readNBytes(32769))).getString("access_token")}
  }finally{c.disconnect()}
 }
 @ReactMethod fun completeDiscord(url:String,key:String,p:Promise)=runAsync(p){
  val u=Uri.parse(url)
  require(u.scheme=="discord-1547982688898777118"&&u.path=="/authorize/callback")
  val state=prefs.getString("state",null)
  val verifier=prefs.getString("verifier",null)
  require(!state.isNullOrBlank()&&state==u.getQueryParameter("state")&&
   !verifier.isNullOrBlank()&&System.currentTimeMillis()-prefs.getLong("issued",0)<600000)
  check(prefs.edit().remove("state").remove("verifier").remove("issued").commit())
  val code=u.getQueryParameter("code")
  require(!code.isNullOrBlank()&&u.getQueryParameter("error")==null)
  val discordToken=exchange(code,verifier)
  val pupeyeToken=session(key)
  // Existing Discord/Pup Account: companion access only, never an
  // automatic device transfer. New accounts retain normal registration.
  try {
   post("pup-beta-link",envelope("beta-link-discord",JSONObject().put("discordAccessToken",discordToken)),
    key,pupeyeToken)
  } catch(e:ApiError) {
   if(e.code!="BETA_ACCOUNT_NOT_FOUND")throw e
   post("pupeye-auth-discord",envelope("auth-discord",JSONObject().put("discordAccessToken",discordToken)),
    key,pupeyeToken)
  }
  account(key)
 }
 @ReactMethod fun updateDisplayName(displayName:String,key:String,p:Promise)=runAsync(p){
  val name=displayName.trim()
  require(name.length in 2..24 && Regex("""[\p{L}\p{N} ._'’-]+""").matches(name)){"Invalid display name"}
  val payload=JSONObject().put("displayName",name)
  try{
   post("pup-beta-link",envelope("beta-account-update",payload),key,session(key))
  }catch(e:ApiError){
   if(e.code!="BETA_LINK_REQUIRED")throw e
   post("pup-account",envelope("account-update",payload),key,session(key))
  }
  account(key)
 }
 // Cache is display-only until a fresh signed server response verifies the
 // account. This prevents blank startup on weak mobile data connections.
 private fun bootstrapMap(obj:JSONObject,cached:Boolean):WritableMap {
  val a=obj.getJSONObject("account")
  val summary=obj.optJSONObject("cloudSave")
  val save=obj.optJSONObject("save")
  val account=Arguments.createMap().apply{
   putString("status",if(cached)"CACHED" else "VERIFIED")
   putString("username",a.getString("username"))
   putString("displayName",a.optString("displayName",a.getString("username")))
   putString("accountId",a.getString("id"))
   putBoolean("readOnly",obj.optBoolean("readOnly",true))
   putBoolean("accountWritable",obj.optBoolean("accountWritable",true))
   putBoolean("linkedFromPlay",obj.optBoolean("linkedFromPlay",false))
   putBoolean("cloudSaveExists",summary?.optBoolean("exists",false)?:false)
   putDouble("cloudRevision",summary?.optDouble("revision",0.0)?:0.0)
   putString("playerId",obj.optString("playerId",player.getString("player_id","")))
   putString("friendCode",obj.optString("friendCode",player.getString("friend_code","")))
   putString("guildRole",obj.optString("guildRole",""))
  }
  val cloud=Arguments.createMap().apply{
   putBoolean("exists",save!=null)
   if(save!=null){
    putDouble("revision",save.optDouble("revision",0.0))
    putString("saveJson",save.getJSONObject("saveData").toString())
   }
  }
  return Arguments.createMap().apply{
   putBoolean("cached",cached)
   putMap("account",account)
   putMap("cloud",cloud)
  }
 }
 @ReactMethod fun cachedBootstrap(p:Promise)=runAsync(p){
  if(!sessionFile.isFile||!accountPreviewFile.isFile)Arguments.createMap().apply{
   putBoolean("cached",false)
  } else {
   val cache=read(accountPreviewFile)
   // The cached identity can be displayed, but is never treated as verified.
   val age=System.currentTimeMillis()-cache.optLong("storedAt",0)
   if(age<0||age>7L*24*60*60*1000)Arguments.createMap().apply{putBoolean("cached",false)}
   else bootstrapMap(cache.getJSONObject("response"),true)
  }
 }
 @ReactMethod fun bootstrap(key:String,p:Promise)=runAsync(p){
  if(!sessionFile.isFile)Arguments.createMap().apply{
   putMap("account",Arguments.createMap().apply{putString("status","NEEDS_ACCOUNT")})
   putMap("cloud",Arguments.createMap().apply{putBoolean("exists",false)})
  } else {
   val obj=try{betaRead(key,"beta-bootstrap")}
    catch(e:ApiError){
     if(e.code!="BETA_LINK_REQUIRED")throw e
     post("pup-account",envelope("account-bootstrap",JSONObject()),key,session(key))
    }
   require(obj.getJSONObject("account").getString("status")=="active")
   // Persist successful authenticated snapshots only, never an unverified response.
   val json=obj.toString()
   if(json.length<=1100000)write(accountPreviewFile,
    JSONObject().put("storedAt",System.currentTimeMillis()).put("response",obj))
   bootstrapMap(obj,false)
  }
 }
 @ReactMethod fun clearCachedBootstrap(p:Promise)=runAsync(p){
  accountPreviewFile.delete()
  true
 }
 @ReactMethod fun cloudRead(key:String,p:Promise)=runAsync(p){
  val obj=try{betaRead(key,"beta-cloud-read")}
   catch(e:ApiError){
    if(e.code!="BETA_LINK_REQUIRED")throw e
    post("pup-account",envelope("cloud-save-read",JSONObject()),key,session(key))
   }
  val save=obj.optJSONObject("save")
  Arguments.createMap().apply {
   putBoolean("exists",save!=null)
   if(save!=null){putDouble("revision",save.optDouble("revision"))
    putString("saveJson",save.opt("saveData")?.toString()?:"")}
  }
 }
 // Only gameplay intent crosses into native code; balances, XP and
 // saves are recalculated inside the signed PupEye server endpoint.
 @ReactMethod fun gameplayAction(intentJson:String,key:String,p:Promise)=runAsync(p){
  require(intentJson.length in 2..4096){"Invalid gameplay intent size"}
  val payload=JSONObject(intentJson)
  val action=payload.optString("type")
  require(action in setOf(
   "tap","care","purchase","ticket","accessory","equip","exchange","skill",
   "daily","claimDailyGoal","parkStart","parkClaim",
   "slotsStart","slotsReveal","rouletteStart","rouletteReveal",
   "plinkoStart","plinkoReveal","scratchStart","scratchReveal",
   "wheelStart","wheelReveal",
   "blackjackStart","blackjackHit","blackjackStand","blackjackDouble","blackjackSplit"
  )){"Unsupported live gameplay action"}
  val answer=post("pup-beta-link",envelope("beta-gameplay",payload),key,session(key))
  Arguments.createMap().apply{
   putDouble("revision",answer.getDouble("revision"))
   putString("saveJson",answer.getJSONObject("saveData").toString())
  }
 }
 private val setupBooleans=mapOf(
  "reducedMotion" to "reduced_motion", "anonymousDiagnosticsEnabled" to "anonymous_diagnostics_enabled",
  "crashReportsEnabled" to "crash_reports_enabled", "supportIdentityEnabled" to "support_identity_enabled",
  "dailyRewardNotifications" to "notify_daily_rewards", "gameEventNotifications" to "notify_game_events",
  "updateNotifications" to "notify_app_updates")
 private val setupEnums=mapOf("themeMode" to ("theme_mode" to setOf("system","light","dark")),
  "uiScale" to ("ui_scale" to setOf("compact","default","large")))
 private fun validBirthday(month:Int,day:Int):Boolean =
  (month==0&&day==0)||(month in 1..12&&day in 1..listOf(31,29,31,30,31,30,31,31,30,31,30,31)[month-1])
 @ReactMethod fun readSetup(p:Promise)=runAsync(p){
  Arguments.createMap().apply{
   val version=setup.getInt("setup_flow_version",0)
   val reviewed=setup.getInt("privacy_consent_version",0)
   val complete=setup.getBoolean("setup_complete",false)&&version>=5&&reviewed>=5
   val step=if(setup.getBoolean("setup_complete",false)&&!complete)3 else setup.getInt("setup_step",0).coerceIn(0,5)
   putBoolean("setupComplete",complete);putInt("setupStep",step)
   putInt("birthdayMonth",setup.getInt("birthday_month",0));putInt("birthdayDay",setup.getInt("birthday_day",0))
   putInt("privacyConsentVersion",reviewed)
   putString("themeMode",setup.getString("theme_mode","SYSTEM")!!.lowercase())
   putString("uiScale",setup.getString("ui_scale","COMPACT")!!.lowercase())
   putString("accentHex",setup.getString("accent_hex","#00B8F0"))
   for((name,stored) in setupBooleans)putBoolean(name,setup.getBoolean(stored,name.endsWith("Notifications")))
  }
 }
 @ReactMethod fun saveSetup(json:String,p:Promise)=runAsync(p){
  require(json.length<=8192)
  val input=JSONObject(json)
  val step=input.getInt("setupStep");require(step in 0..5)
  val month=input.getInt("birthdayMonth");val day=input.getInt("birthdayDay")
  // Incomplete picker choices may be persisted while editing, but cannot finish.
  require(month in 0..12&&day in 0..31)
  val reviewed=input.getInt("privacyConsentVersion");require(reviewed==0||reviewed==5)
  val edit=setup.edit().putInt("setup_step",step).putInt("setup_flow_version",5)
   .putInt("birthday_month",month).putInt("birthday_day",day).remove("birthday_year")
   .putInt("privacy_consent_version",reviewed)
  for((name,stored) in setupBooleans)if(input.has(name))edit.putBoolean(stored,input.getBoolean(name))
  for((name,pair) in setupEnums)if(input.has(name)){
   val value=input.getString(name);require(value in pair.second);edit.putString(pair.first,value.uppercase())
  }
  if(input.has("accentHex")){
   val accent=input.getString("accentHex");require(Regex("#[0-9A-Fa-f]{6}").matches(accent))
   edit.putString("accent_hex",accent.uppercase())
  }
  // setupComplete from JavaScript is deliberately ignored; only server-verified finish can set it.
  check(edit.commit());true
 }
 @ReactMethod fun finishSetup(key:String,p:Promise)=runAsync(p){
  require(setup.getInt("setup_step",0)==5&&setup.getInt("privacy_consent_version",0)>=5)
  require(validBirthday(setup.getInt("birthday_month",0),setup.getInt("birthday_day",0)))
  val verified=account(key)
  check(setup.edit().putBoolean("setup_complete",true).putInt("setup_step",5).putInt("setup_flow_version",5).commit())
  verified
 }
}
class PuppyBetaAccountPackage:ReactPackage {
 override fun createNativeModules(context:ReactApplicationContext):List<NativeModule> =
  listOf(PuppyBetaAccountModule(context))
 override fun createViewManagers(context:ReactApplicationContext):List<ViewManager<*,*>> = emptyList()
}
