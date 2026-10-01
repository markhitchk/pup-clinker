package com.harleytg.puppyclicker

import android.content.Context
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * T0 Pup Account cloud persistence.
 *
 * Supabase is authoritative once Discord has authenticated the Pup Account. The existing
 * SharedPreferences stores remain a fast local cache so gameplay does not depend on network
 * latency. Every cache mutation is debounced into an authenticated, device-bound cloud write.
 */
internal object PupAccountCloudSave {
    private const val STATE_PREFS = "pup_account_cloud_state_v1"
    private const val KEY_REVISION = "server_revision"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val MAIN_PREFS = PuppyClickerV6ViewModel.PREFS_NAME
    private const val SEASONAL_PREFS = "puppy_seasonal_v1"
    private const val FORMAT = "puppy-clicker-cloud-save"
    private const val VERSION = 1
    private const val SYNC_DEBOUNCE_MS = 500L
    private const val FOREGROUND_CLOUD_REFRESH_MS = 5_000L
    // Retry transient I/O failures with backoff. A slow or temporarily congested
    // connection should not force the player to restart onboarding.
    private const val NETWORK_ATTEMPTS = 4
    private val NETWORK_RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 7_000L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dirty = AtomicBoolean(false)
    private val syncLoopRunning = AtomicBoolean(false)
    private val continuousSyncRunning = AtomicBoolean(false)
    private val activationMutex = Mutex()
    private val _restoreEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restoreEvents: SharedFlow<Unit> = _restoreEvents.asSharedFlow()

    @Volatile
    private var restoringFromCloud = false

    fun initialize(context: Context) {
        val app = context.applicationContext
        if (!SupabasePupEyeClient.isConfigured()) return
        if (!DiscordSignupAuth.isConnected(app)) return
        activate(app)
    }

    fun activate(context: Context) {
        val app = context.applicationContext
        if (!SupabasePupEyeClient.isConfigured()) return
        if (!DiscordSignupAuth.isConnected(app)) return

        startContinuousSync(app)
        scope.launch {
            runCatching { activateAndAwait(app) }
                .onFailure { noteError(app, it.message ?: "Unable to initialize Pup Account cloud save") }
        }
    }

    suspend fun activateAndAwait(context: Context): Boolean {
        val app = context.applicationContext
        if (!SupabasePupEyeClient.isConfigured()) return false
        if (!DiscordSignupAuth.isConnected(app)) return false

        return activationMutex.withLock {
            runCatching {
                retryTransientNetwork { restoreOrSeed(app) }
                app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_LAST_ERROR)
                    .apply()
                true
            }.getOrElse {
                noteError(app, it.message ?: "Unable to initialize Pup Account cloud save")
                false
            }
        }
    }

    fun isApplyingCloudRestore(): Boolean = restoringFromCloud

    fun refreshNow(context: Context) {
        val app = context.applicationContext
        if (!canSync(app)) return

        scope.launch {
            activationMutex.withLock {
                runCatching {
                    retryTransientNetwork { refreshFromCloudIfNewer(app) }
                }.onFailure {
                    noteError(app, it.message ?: "Unable to refresh Pup Account cloud save")
                }
            }
        }
    }

    private fun startContinuousSync(context: Context) {
        val app = context.applicationContext
        if (!continuousSyncRunning.compareAndSet(false, true)) return

        scope.launch {
            while (true) {
                if (PuppyAppRuntime.isForeground && canSync(app)) {
                    activationMutex.withLock {
                        runCatching {
                            retryTransientNetwork { refreshFromCloudIfNewer(app) }
                        }.onFailure {
                            noteError(app, it.message ?: "Unable to refresh Pup Account cloud save")
                        }
                    }
                }
                delay(FOREGROUND_CLOUD_REFRESH_MS)
            }
        }
    }

    private fun canSync(context: Context): Boolean =
        SupabasePupEyeClient.isConfigured() &&
            DiscordSignupAuth.isConnected(context) &&
            PuppyUiPreferences.current(context).setupComplete

    fun queueSync(context: Context, reason: String = "cache-change") {
        val app = context.applicationContext
        if (restoringFromCloud) return
        if (!canSync(app)) return

        dirty.set(true)
        if (!syncLoopRunning.compareAndSet(false, true)) return

        scope.launch {
            try {
                do {
                    delay(SYNC_DEBOUNCE_MS)
                    dirty.set(false)
                    runCatching {
                        retryTransientNetwork { syncOnce(app, reason.take(40)) }
                    }.onFailure {
                        noteError(app, it.message ?: "Unable to sync Pup Account save")
                    }
                } while (dirty.get())
            } finally {
                syncLoopRunning.set(false)
                if (dirty.get()) queueSync(app, reason)
            }
        }
    }

    private suspend fun refreshFromCloudIfNewer(context: Context) {
        val remote = SupabasePupEyeClient.readPupAccountCloudSave(context) ?: return
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val localRevision = state.getLong(KEY_REVISION, 0L).coerceAtLeast(0L)

        if (remote.revision <= localRevision) {
            state.edit()
                .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                .remove(KEY_LAST_ERROR)
                .apply()
            return
        }

        restore(context, remote.saveData)
        _restoreEvents.tryEmit(Unit)
        state.edit()
            .putLong(KEY_REVISION, remote.revision)
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private suspend fun restoreOrSeed(context: Context) {
        val remote = SupabasePupEyeClient.readPupAccountCloudSave(context)
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

        if (remote == null) {
            val seeded = SupabasePupEyeClient.writePupAccountCloudSave(
                context = context,
                expectedRevision = 0L,
                saveData = snapshot(context)
            )
            if (!seeded.success) {
                error(seeded.message ?: "Unable to seed Pup Account cloud save")
            }
            state.edit()
                .putLong(KEY_REVISION, seeded.revision)
                .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                .remove(KEY_LAST_ERROR)
                .apply()
            return
        }

        val localServerRevision = state.getLong(KEY_REVISION, 0L).coerceAtLeast(0L)
        if (localServerRevision != 0L && localServerRevision == remote.revision) {
            // Same authoritative revision. Local cache may contain newer offline mutations,
            // so preserve it and let the normal sync path attempt the next revision.
            queueSync(context, "resume")
            return
        }

        restore(context, remote.saveData)
        _restoreEvents.tryEmit(Unit)
        state.edit()
            .putLong(KEY_REVISION, remote.revision)
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    private suspend fun syncOnce(context: Context, reason: String) {
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val expectedRevision = state.getLong(KEY_REVISION, 0L).coerceAtLeast(0L)
        val result = SupabasePupEyeClient.writePupAccountCloudSave(
            context = context,
            expectedRevision = expectedRevision,
            saveData = snapshot(context)
        )

        if (result.success) {
            state.edit()
                .putLong(KEY_REVISION, result.revision)
                .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                .remove(KEY_LAST_ERROR)
                .apply()
            return
        }

        if (result.conflictRevision != null) {
            // Supabase wins a revision conflict. This should only happen after a restored
            // session or an interrupted sync because PupEye already limits the account to one device.
            val remote = SupabasePupEyeClient.readPupAccountCloudSave(context)
                ?: error("Cloud save conflict was reported but no cloud save exists")
            restore(context, remote.saveData)
            _restoreEvents.tryEmit(Unit)
            state.edit()
                .putLong(KEY_REVISION, remote.revision)
                .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                .putString(KEY_LAST_ERROR, result.message?.take(220))
                .apply()
            return
        }

        error(result.message ?: "Pup Account cloud save was rejected")
    }

    private fun snapshot(context: Context): JSONObject {
        val main = context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
        val seasonal = context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("savedAtEpochMs", System.currentTimeMillis())
            put("identity", PuppyPlayerIdentity.metadata(context))
            put(
                "stores",
                JSONObject().apply {
                    put(MAIN_PREFS, SecurePreferenceCodec.encode(main))
                    put(SEASONAL_PREFS, SecurePreferenceCodec.encode(seasonal))
                }
            )
        }
    }

    private fun restore(context: Context, saveData: JSONObject) {
        require(saveData.optString("format") == FORMAT) { "Unsupported Pup Account cloud save format" }
        require(saveData.optInt("version") == VERSION) { "Unsupported Pup Account cloud save version" }
        val stores = saveData.optJSONObject("stores")
            ?: error("Pup Account cloud save is missing stores")
        val mainStore = stores.optJSONObject(MAIN_PREFS)
            ?: error("Pup Account cloud save is missing main progression")
        val seasonalStore = stores.optJSONObject(SEASONAL_PREFS)

        restoringFromCloud = true
        try {
            SecurePreferenceCodec.restore(
                context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE),
                mainStore
            )
            seasonalStore?.let {
                SecurePreferenceCodec.restore(
                    context.getSharedPreferences(SEASONAL_PREFS, Context.MODE_PRIVATE),
                    it
                )
            }
            PuppySaveCompatibility.normalizeMainSave(
                context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
            )
            PupEyeSaveGuard.seal(
                context,
                context.getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
            )
        } finally {
            restoringFromCloud = false
        }
    }

    private suspend fun <T> retryTransientNetwork(block: suspend () -> T): T {
        var lastFailure: Throwable? = null
        for (attempt in 0 until NETWORK_ATTEMPTS) {
            try {
                return block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastFailure = error
                val canRetry = error is IOException && attempt < NETWORK_ATTEMPTS - 1
                if (!canRetry) throw error
                delay(NETWORK_RETRY_DELAYS_MS[attempt])
            }
        }
        throw lastFailure ?: IOException("Pup Account network request failed")
    }

    private fun noteError(context: Context, message: String) {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ERROR, message.take(220))
            .apply()
    }
}
