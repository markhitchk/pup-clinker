package com.harleytg.puppyclicker

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class PuppyNotificationType {
    DAILY_REWARD,
    PARK_READY,
    APP_UPDATE,
    ROSTER_UPDATE,
    SYSTEM_REWARD
}

enum class PuppyNotificationRoute {
    NONE,
    REWARDS,
    ROSTER,
    RELEASE_HUB
}

data class PuppyNotificationItem(
    val id: String,
    val type: PuppyNotificationType,
    val title: String,
    val body: String,
    val createdAtMs: Long,
    val read: Boolean,
    val route: PuppyNotificationRoute,
    val externalUrl: String? = null,
    val rewardCurrency: PuppyRewardCurrency? = null,
    val rewardAmount: Long = 0L,
    val claimed: Boolean = false
) {
    val hasClaimableReward: Boolean
        get() =
            type == PuppyNotificationType.SYSTEM_REWARD &&
                rewardCurrency != null &&
                rewardAmount > 0L &&
                !claimed
}

internal object PuppyNotificationHistoryCodec {
    const val MAX_ITEMS = 100
    val NEWEST_FIRST = compareByDescending<PuppyNotificationItem> { it.createdAtMs }
        .thenByDescending { it.id }

    fun normalize(items: List<PuppyNotificationItem>): List<PuppyNotificationItem> {
        val deduped = items.asSequence()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .sortedWith(NEWEST_FIRST)
            .distinctBy { it.id }
            .toList()
        val protectedRewards = deduped.filter { it.hasClaimableReward }.take(MAX_ITEMS)
        val protectedIds = protectedRewards.mapTo(hashSetOf()) { it.id }
        val remaining = deduped
            .asSequence()
            .filterNot { it.id in protectedIds }
            .take((MAX_ITEMS - protectedRewards.size).coerceAtLeast(0))
            .toList()
        return (protectedRewards + remaining).sortedWith(NEWEST_FIRST)
    }

    fun insert(
        items: List<PuppyNotificationItem>,
        item: PuppyNotificationItem
    ): List<PuppyNotificationItem> {
        if (item.id.isBlank() || item.title.isBlank()) return normalize(items)
        // Never replace an existing settlement row. This preserves claimed/read state if the
        // producer retries after a process restart.
        val existing = items.firstOrNull { it.id == item.id }
        val candidate = existing ?: item
        return normalize(items.filterNot { it.id == item.id } + candidate)
    }

    fun findById(items: List<PuppyNotificationItem>, id: String): PuppyNotificationItem? =
        items.firstOrNull { it.id == id }

    fun markRead(items: List<PuppyNotificationItem>, id: String): List<PuppyNotificationItem> =
        normalize(items.map { if (it.id == id) it.copy(read = true) else it })

    fun markRewardClaimed(
        items: List<PuppyNotificationItem>,
        id: String
    ): List<PuppyNotificationItem> =
        normalize(
            items.map { item ->
                if (item.id == id && item.type == PuppyNotificationType.SYSTEM_REWARD) {
                    item.copy(read = true, claimed = true)
                } else {
                    item
                }
            }
        )

    fun markAllRead(items: List<PuppyNotificationItem>): List<PuppyNotificationItem> =
        normalize(items.map { if (it.read) it else it.copy(read = true) })

    fun clearRead(items: List<PuppyNotificationItem>): List<PuppyNotificationItem> =
        normalize(items.filterNot { it.read })

    fun encode(items: List<PuppyNotificationItem>): String {
        val array = JSONArray()
        normalize(items).forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("type", item.type.name)
                put("title", item.title)
                put("body", item.body)
                put("createdAtMs", item.createdAtMs.coerceAtLeast(0L))
                put("read", item.read)
                put("route", item.route.name)
                item.externalUrl?.takeIf { it.isNotBlank() }?.let { put("externalUrl", it) }
                item.rewardCurrency?.let { put("rewardCurrency", it.name) }
                if (item.rewardAmount > 0L) put("rewardAmount", item.rewardAmount)
                if (item.claimed) put("claimed", true)
            })
        }
        return array.toString()
    }

    fun decode(raw: String?): List<PuppyNotificationItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val root = array.optJSONObject(index) ?: continue
                    val id = root.optString("id").trim()
                    val title = root.optString("title").trim()
                    if (id.isBlank() || title.isBlank()) continue
                    val type = runCatching {
                        PuppyNotificationType.valueOf(root.optString("type"))
                    }.getOrNull() ?: continue
                    val route = runCatching {
                        PuppyNotificationRoute.valueOf(
                            root.optString("route", PuppyNotificationRoute.NONE.name)
                        )
                    }.getOrDefault(PuppyNotificationRoute.NONE)
                    val rewardCurrency = root.optString("rewardCurrency")
                        .takeIf { it.isNotBlank() }
                        ?.let { rawCurrency ->
                            runCatching { PuppyRewardCurrency.valueOf(rawCurrency) }.getOrNull()
                        }
                    add(
                        PuppyNotificationItem(
                            id = id,
                            type = type,
                            title = title,
                            body = root.optString("body").take(2_000),
                            createdAtMs = root.optLong("createdAtMs", 0L).coerceAtLeast(0L),
                            read = root.optBoolean("read", false),
                            route = route,
                            externalUrl = root.optString("externalUrl")
                                .trim()
                                .takeIf { it.isNotBlank() },
                            rewardCurrency = rewardCurrency,
                            rewardAmount = root.optLong("rewardAmount", 0L).coerceAtLeast(0L),
                            claimed = root.optBoolean("claimed", false)
                        )
                    )
                }
            }.let(::normalize)
        }.getOrDefault(emptyList())
    }
}

internal object PuppyNotificationHistory {
    const val PREFS_NAME = "puppy_notification_history_v1"
    private const val KEY_ITEMS = "items"

    private val mutableItems = MutableStateFlow<List<PuppyNotificationItem>>(emptyList())
    val items: StateFlow<List<PuppyNotificationItem>> = mutableItems.asStateFlow()

    private val mutableUnreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = mutableUnreadCount.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        publish(PuppyNotificationHistoryCodec.decode(store.getString(KEY_ITEMS, null)))
    }

    @Synchronized
    fun record(context: Context, item: PuppyNotificationItem) {
        val app = context.applicationContext
        val store = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = PuppyNotificationHistoryCodec.decode(store.getString(KEY_ITEMS, null))
        val next = PuppyNotificationHistoryCodec.insert(current, item)
        if (store.edit().putString(KEY_ITEMS, PuppyNotificationHistoryCodec.encode(next)).commit()) {
            publish(next)
        }
    }

    @Synchronized
    fun recordSystemReward(
        context: Context,
        id: String,
        title: String,
        body: String,
        currency: PuppyRewardCurrency,
        amount: Long,
        createdAtMs: Long = System.currentTimeMillis()
    ): PuppyNotificationItem? {
        if (id.isBlank() || title.isBlank() || amount <= 0L) return null
        val item = PuppyNotificationItem(
            id = id,
            type = PuppyNotificationType.SYSTEM_REWARD,
            title = title,
            body = body,
            createdAtMs = createdAtMs.coerceAtLeast(0L),
            read = false,
            route = PuppyNotificationRoute.NONE,
            rewardCurrency = currency,
            rewardAmount = amount,
            claimed = false
        )
        record(context, item)
        return findById(context, id)
    }

    @Synchronized
    fun findById(context: Context, id: String): PuppyNotificationItem? {
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PuppyNotificationHistoryCodec.findById(
            PuppyNotificationHistoryCodec.decode(store.getString(KEY_ITEMS, null)),
            id
        )
    }

    @Synchronized
    fun markRead(context: Context, id: String) {
        mutate(context) { PuppyNotificationHistoryCodec.markRead(it, id) }
    }

    @Synchronized
    fun markRewardClaimed(context: Context, id: String) {
        mutate(context) { PuppyNotificationHistoryCodec.markRewardClaimed(it, id) }
    }

    @Synchronized
    fun markAllRead(context: Context) {
        mutate(context, PuppyNotificationHistoryCodec::markAllRead)
    }

    @Synchronized
    fun clearRead(context: Context) {
        mutate(context, PuppyNotificationHistoryCodec::clearRead)
    }

    private fun mutate(
        context: Context,
        transform: (List<PuppyNotificationItem>) -> List<PuppyNotificationItem>
    ) {
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = transform(PuppyNotificationHistoryCodec.decode(store.getString(KEY_ITEMS, null)))
        if (store.edit().putString(KEY_ITEMS, PuppyNotificationHistoryCodec.encode(next)).commit()) {
            publish(next)
        }
    }

    private fun publish(items: List<PuppyNotificationItem>) {
        val normalized = PuppyNotificationHistoryCodec.normalize(items)
        mutableItems.value = normalized
        mutableUnreadCount.value = normalized.count { !it.read }
    }
}
