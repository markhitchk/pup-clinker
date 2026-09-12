package com.harleytg.puppyclicker

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal enum class PuppyRewardMetric(val wireName: String) {
    TAPS("taps"),
    CARE("care"),
    SHOP("shop"),
    WELLNESS("wellness"),
    BOND("bond"),
    TICKETS("tickets");

    companion object {
        fun fromWireName(value: String): PuppyRewardMetric? =
            entries.firstOrNull { it.wireName == value.lowercase() }
    }
}

internal data class PuppyRewardGoal(
    val id: String,
    val metric: PuppyRewardMetric,
    val emoji: String,
    val title: String,
    val description: String,
    val target: Long,
    val rewardTreats: Long
) {
    fun progress(state: V6GameState): Long = when (metric) {
        PuppyRewardMetric.TAPS -> state.dailyTaps
        PuppyRewardMetric.CARE -> state.dailyCareActions
        PuppyRewardMetric.SHOP -> state.dailyShopPurchases
        PuppyRewardMetric.WELLNESS -> state.careScore.toLong()
        PuppyRewardMetric.BOND -> state.bond.toLong()
        PuppyRewardMetric.TICKETS -> state.ticketsOwned.toLong()
    }

    fun isComplete(state: V6GameState): Boolean = progress(state) >= target
}

internal data class PuppyMonthlyRewardSchedule(
    val month: String,
    val days: Map<Int, List<PuppyRewardGoal>>,
    val source: String
)

/**
 * Streams the current month's Today’s Goals from:
 * assets/rewards/YYYY-MM.json
 *
 * The document is data-only. It can change titles, icons, targets and treat
 * rewards for the supported metrics, but it cannot execute code or introduce
 * arbitrary reward types. The last valid month is cached for offline use.
 */
internal object PuppyMonthlyRewards {
    private const val BASE_URL =
        "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets/rewards"
    private const val PREFS = "puppy_monthly_rewards_v1"
    private const val MAX_BYTES = 512 * 1024
    private const val REFRESH_MS = 15L * 60L * 1_000L
    private const val MAX_GOALS_PER_DAY = 12
    private const val MAX_TARGET = 1_000_000L
    private const val MAX_REWARD = 100_000L

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initialMonth = YearMonth.now()
    private val _schedule = MutableStateFlow(fallbackSchedule(initialMonth, "fallback"))
    val schedule: StateFlow<PuppyMonthlyRewardSchedule> = _schedule.asStateFlow()

    fun initialize(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        loadCachedMonth(app, YearMonth.now())

        scope.launch {
            while (true) {
                val month = YearMonth.now()
                if (_schedule.value.month != month.toString()) {
                    loadCachedMonth(app, month)
                }
                refreshMonth(app, month)
                delay(REFRESH_MS)
            }
        }
    }

    fun currentGoals(today: LocalDate = LocalDate.now()): List<PuppyRewardGoal> {
        val month = YearMonth.from(today)
        val current = schedule.value
        if (current.month != month.toString()) return fallbackGoals(today.dayOfMonth)
        return current.days[today.dayOfMonth]
            ?.takeIf { it.isNotEmpty() }
            ?: fallbackGoals(today.dayOfMonth)
    }

    fun goalToday(id: String, today: LocalDate = LocalDate.now()): PuppyRewardGoal? =
        currentGoals(today).firstOrNull { it.id == id }

    suspend fun refreshNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        refreshMonth(context.applicationContext, YearMonth.now())
    }

    private fun loadCachedMonth(context: Context, month: YearMonth) {
        val fallback = fallbackSchedule(month, "fallback")
        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(cacheKey(month), null)
        _schedule.value = cached
            ?.let { text ->
                runCatching { parse(text, month, "cache") }.getOrNull()
            }
            ?: fallback
    }

    private fun refreshMonth(context: Context, month: YearMonth): Boolean {
        return try {
            val json = fetch(month)
            val parsed = parse(json, month, "github")
            _schedule.value = parsed
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(cacheKey(month), json)
                .apply()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            PuppyDebugLog.w("MonthlyRewards", "Unable to refresh monthly rewards for $month", error)
            false
        }
    }

    private fun parse(text: String, expectedMonth: YearMonth, source: String): PuppyMonthlyRewardSchedule {
        val root = JSONObject(text)
        if (root.optInt("schemaVersion", -1) != 1) {
            throw IOException("Unsupported monthly rewards schema")
        }
        val monthText = root.optString("month").trim()
        if (monthText != expectedMonth.toString()) {
            throw IOException("Monthly rewards file month does not match $expectedMonth")
        }

        val daysJson = root.getJSONObject("days")
        val parsedDays = linkedMapOf<Int, List<PuppyRewardGoal>>()
        val maxDay = expectedMonth.lengthOfMonth()
        val dayKeys = daysJson.keys()
        while (dayKeys.hasNext()) {
            val key = dayKeys.next()
            val day = key.toIntOrNull() ?: continue
            if (day !in 1..maxDay) continue

            val array = daysJson.optJSONArray(key) ?: continue
            val goals = mutableListOf<PuppyRewardGoal>()
            val seen = mutableSetOf<String>()
            for (index in 0 until minOf(array.length(), MAX_GOALS_PER_DAY)) {
                val item = array.optJSONObject(index) ?: continue
                if (!item.optBoolean("enabled", true)) continue

                val id = item.optString("id").trim()
                if (!id.matches(Regex("[a-z0-9_-]{1,48}")) || !seen.add(id)) continue
                val metric = PuppyRewardMetric.fromWireName(item.optString("type")) ?: continue
                val target = item.optLong("target", 0L)
                val reward = item.optLong("rewardTreats", 0L)
                if (target !in 1L..MAX_TARGET || reward !in 1L..MAX_REWARD) continue
                if ((metric == PuppyRewardMetric.WELLNESS || metric == PuppyRewardMetric.BOND) && target > 100L) {
                    continue
                }

                goals += PuppyRewardGoal(
                    id = id,
                    metric = metric,
                    emoji = item.optString("emoji", "🎯").take(8),
                    title = item.optString("title", id).take(64),
                    description = item.optString("description").take(140),
                    target = target,
                    rewardTreats = reward
                )
            }
            if (goals.isNotEmpty()) parsedDays[day] = goals
        }

        if (parsedDays.isEmpty()) throw IOException("Monthly rewards file contains no valid days")
        return PuppyMonthlyRewardSchedule(monthText, parsedDays, source)
    }

    private fun fetch(month: YearMonth): String {
        val connection = URL("$BASE_URL/$month.json").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "PuppyClicker-Android")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Monthly rewards endpoint returned HTTP " + connection.responseCode)
            }
            if (connection.contentLengthLong > MAX_BYTES) {
                throw IOException("Monthly rewards response is too large")
            }

            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_BYTES) {
                        throw IOException("Monthly rewards response is too large")
                    }
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun fallbackSchedule(month: YearMonth, source: String): PuppyMonthlyRewardSchedule =
        PuppyMonthlyRewardSchedule(
            month = month.toString(),
            days = (1..month.lengthOfMonth()).associateWith(::fallbackGoals),
            source = source
        )

    private fun fallbackGoals(day: Int): List<PuppyRewardGoal> {
        val tapTarget = listOf(50L, 75L, 90L, 100L)[(day - 1).coerceAtLeast(0) % 4]
        val careTarget = listOf(3L, 4L, 5L)[(day - 1).coerceAtLeast(0) % 3]
        return listOf(
            PuppyRewardGoal("tap_time", PuppyRewardMetric.TAPS, "🐾", "Tap Time", "Tap your puppy $tapTarget times", tapTarget, tapTarget * 4L),
            PuppyRewardGoal("good_care", PuppyRewardMetric.CARE, "💖", "Good Care", "Complete $careTarget care actions", careTarget, 150L + careTarget * 40L),
            PuppyRewardGoal("shop_visit", PuppyRewardMetric.SHOP, "🛍️", "Shop Visit", "Buy 1 upgrade", 1L, 400L),
            PuppyRewardGoal("wellness", PuppyRewardMetric.WELLNESS, "✨", "Healthy Pup", "Reach 85% wellness", 85L, 420L)
        )
    }

    private fun cacheKey(month: YearMonth): String = "month_" + month.toString()
}
