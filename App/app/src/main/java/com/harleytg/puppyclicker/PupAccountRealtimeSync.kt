package com.harleytg.puppyclicker

import android.content.Context
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Low-latency invalidation channel for the authoritative Pup Account cloud save.
 *
 * No save payload or account PII travels over Realtime. Supabase broadcasts only an opaque
 * per-account topic plus revision metadata. On an event the app re-reads the full save through
 * the signed Pup Account Edge Function, preserving PupEye authorization and conflict checks.
 */
internal object PupAccountRealtimeSync {
    private const val HEARTBEAT_MS = 20_000L
    private const val MAX_RECONNECT_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = AtomicLong(1L)
    private val lock = Any()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var topic: String? = null
    @Volatile private var connecting = false
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    fun start(context: Context) {
        val app = context.applicationContext
        if (!SupabasePupEyeClient.isConfigured()) return
        if (!DiscordSignupAuth.isConnected(app)) return

        val ticket: Long
        synchronized(lock) {
            if (socket != null || connecting) return
            connecting = true
            reconnectJob?.cancel()
            reconnectJob = null
            ticket = lifecycle.get()
        }

        scope.launch {
            val info = runCatching {
                SupabasePupEyeClient.readPupAccountRealtimeInfo(app)
            }.getOrElse {
                synchronized(lock) { connecting = false }
                scheduleReconnect(app, ticket)
                return@launch
            }

            if (ticket != lifecycle.get()) {
                synchronized(lock) { connecting = false }
                return@launch
            }

            topic = info.topic
            openSocket(app, info.topic, ticket)
        }
    }

    fun stop() {
        lifecycle.incrementAndGet()
        synchronized(lock) {
            connecting = false
            reconnectAttempt = 0
            topic = null
            heartbeatJob?.cancel()
            heartbeatJob = null
            reconnectJob?.cancel()
            reconnectJob = null
            socket?.close(1000, "Puppy Clicker backgrounded")
            socket = null
        }
    }

    private fun openSocket(context: Context, channelTopic: String, ticket: Long) {
        val request = Request.Builder()
            .url(realtimeWebSocketUrl())
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (ticket != lifecycle.get()) {
                    webSocket.close(1000, "Superseded")
                    return
                }
                synchronized(lock) {
                    socket = webSocket
                    connecting = false
                    reconnectAttempt = 0
                }
                webSocket.send(joinMessage(channelTopic))
                startHeartbeat(webSocket, ticket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (ticket != lifecycle.get()) return
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (message.optString("event") != "broadcast") return
                if (message.optString("topic") != "realtime:$channelTopic") return

                val broadcast = message.optJSONObject("payload") ?: return
                if (broadcast.optString("event") != "changed") return
                val payload = broadcast.optJSONObject("payload") ?: JSONObject()
                val kind = payload.optString("kind")
                if (kind !in setOf("save", "account", "identity", "device")) return

                // Supabase is authoritative. Re-read through the signed Edge Function instead
                // of applying unauthenticated broadcast payload data directly.
                PupAccountCloudSave.refreshNow(context)
                if (kind != "save") {
                    scope.launch {
                        runCatching {
                            val status = SupabasePupEyeClient.readPupAccountStatus(context)
                            DiscordSignupAuth.applyAuthoritativeAccountStatus(context, status)
                        }
                    }
                    SupabasePupEyeClient.refreshEnforcementAsync(context)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                clearSocket(webSocket)
                scheduleReconnect(context, ticket)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                clearSocket(webSocket)
                scheduleReconnect(context, ticket)
            }
        }

        val newSocket = client.newWebSocket(request, listener)
        synchronized(lock) {
            if (ticket != lifecycle.get()) {
                newSocket.cancel()
                connecting = false
            } else {
                socket = newSocket
            }
        }
    }

    private fun startHeartbeat(webSocket: WebSocket, ticket: Long) {
        synchronized(lock) {
            heartbeatJob?.cancel()
            heartbeatJob = scope.launch {
                var ref = 2L
                while (isActive && ticket == lifecycle.get()) {
                    delay(HEARTBEAT_MS)
                    if (ticket != lifecycle.get()) break
                    val heartbeat = JSONObject()
                        .put("topic", "phoenix")
                        .put("event", "heartbeat")
                        .put("payload", JSONObject())
                        .put("ref", (ref++).toString())
                        .put("join_ref", JSONObject.NULL)
                    if (!webSocket.send(heartbeat.toString())) break
                }
            }
        }
    }

    private fun clearSocket(webSocket: WebSocket) {
        synchronized(lock) {
            if (socket === webSocket) socket = null
            connecting = false
            heartbeatJob?.cancel()
            heartbeatJob = null
        }
    }

    private fun scheduleReconnect(context: Context, ticket: Long) {
        if (ticket != lifecycle.get()) return
        if (!PuppyAppRuntime.isForeground) return
        if (!DiscordSignupAuth.isConnected(context)) return

        synchronized(lock) {
            if (reconnectJob?.isActive == true) return
            val exponent = reconnectAttempt.coerceIn(0, 5)
            val waitMs = (1_000L shl exponent).coerceAtMost(MAX_RECONNECT_MS)
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
            reconnectJob = scope.launch {
                delay(waitMs)
                if (ticket != lifecycle.get() || !PuppyAppRuntime.isForeground) return@launch
                synchronized(lock) {
                    reconnectJob = null
                    connecting = false
                }
                start(context)
            }
        }
    }

    private fun joinMessage(channelTopic: String): String {
        val config = JSONObject()
            .put(
                "broadcast",
                JSONObject()
                    .put("ack", false)
                    .put("self", false)
            )
            .put("presence", JSONObject().put("enabled", false))
            .put("postgres_changes", JSONArray())
            .put("private", false)

        return JSONObject()
            .put("topic", "realtime:$channelTopic")
            .put("event", "phx_join")
            .put("payload", JSONObject().put("config", config))
            .put("ref", "1")
            .put("join_ref", "1")
            .toString()
    }

    private fun realtimeWebSocketUrl(): String {
        val base = when {
            SupabasePupEyeClient.baseUrl.startsWith("https://") ->
                "wss://" + SupabasePupEyeClient.baseUrl.removePrefix("https://")
            SupabasePupEyeClient.baseUrl.startsWith("http://") ->
                "ws://" + SupabasePupEyeClient.baseUrl.removePrefix("http://")
            else -> error("Invalid Supabase URL")
        }
        val key = URLEncoder.encode(
            SupabasePupEyeClient.publishableKey,
            StandardCharsets.UTF_8.name()
        )
        return "$base/realtime/v1/websocket?apikey=$key&vsn=1.0.0"
    }
}
