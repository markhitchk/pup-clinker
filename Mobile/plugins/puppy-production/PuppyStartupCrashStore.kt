package com.harleytg.puppyproduction

import android.content.Context
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal local crash marker installed before React Native starts.
 * Only an allowlisted error code and timestamp are persisted.
 * Never persist a stack trace, exception message, identifier or credential.
 * A fatal native exception must still go to Android's previous handler.
 */
object PuppyStartupCrashStore {
 private const val PREFS = "puppy_startup_errors_v1"
 private val installed = AtomicBoolean(false)
 private val allowed = setOf("PUPPY-CRASH-901", "PUPPY-CRASH-902", "PUPPY-CRASH-903", "PUPPY-CRASH-904")
 private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
 fun install(ctx:Context) {
  if(ctx.packageName!="com.harleytg.puppyclicker.beta" || !installed.compareAndSet(false,true))return
  val app=ctx.applicationContext
  val prior=Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { thread, error ->
   val code=when(error){
    is OutOfMemoryError -> "PUPPY-CRASH-903"
    is LinkageError -> "PUPPY-CRASH-904"
    else -> "PUPPY-CRASH-901"
   }
   runCatching { save(app,code) }
   if(prior!=null)prior.uncaughtException(thread,error)
   else Process.killProcess(Process.myPid())
  }
 }
 fun save(ctx:Context,code:String):Boolean {
  if(!allowed.contains(code))return false
  return prefs(ctx).edit().putString("code",code)
   .putLong("at",System.currentTimeMillis()).commit()
 }
 fun read(ctx:Context):String? {
  val age=System.currentTimeMillis()-prefs(ctx).getLong("at",0L)
  if(age<0||age>7L*24*60*60*1000L){clear(ctx);return null}
  val code=prefs(ctx).getString("code",null)
  if(!allowed.contains(code)){clear(ctx);return null}
  return code
 }
 fun clear(ctx:Context):Boolean=prefs(ctx).edit().remove("code").remove("at").commit()
}
