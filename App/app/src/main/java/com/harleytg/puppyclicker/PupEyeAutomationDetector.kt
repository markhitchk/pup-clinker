package com.harleytg.puppyclicker

import android.os.SystemClock
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Behavioral detector for external clickers / scripts.
 *
 * Android accessibility gesture injection can look like normal touch input at the app layer,
 * so PupEye uses sustained timing signatures instead of blocking accessibility services.
 * Human tapping is intentionally given wide jitter/rate tolerance.
 */
internal class PupEyeAutomationDetector {
    private val taps = ArrayDeque<Long>()

    fun recordTapAndCheck(): Boolean {
        val now = SystemClock.elapsedRealtimeNanos() / 1_000_000L
        taps.addLast(now)
        while (taps.isNotEmpty() && taps.first < now - HISTORY_MS) taps.removeFirst()

        if (taps.size < MIN_TAPS) return false
        if (countSince(now - 1_000L) >= EXTREME_TAPS_PER_SECOND) return true
        if (countSince(now - 2_000L) >= EXTREME_TAPS_PER_TWO_SECONDS) return true

        val intervals = taps.zipWithNext { a, b -> (b - a).toDouble() }
        if (intervals.size < MIN_INTERVALS) return false

        // Injection loops frequently emit several events only a few milliseconds apart.
        val recent8 = intervals.takeLast(8)
        if (recent8.count { it <= IMPOSSIBLE_INTERVAL_MS } >= 5) return true

        // Exact/fixed-delay auto clickers: catches slow scripts up to one click per second.
        val short = intervals.takeLast(12)
        if (short.size >= 12) {
            val mean = short.average()
            val stdDev = stdDev(short, mean)
            val roundedModeRatio = modeRatio(short, 2.0)
            val identicalLike = short.zipWithNext().count { (a, b) -> abs(a - b) <= 2.0 }
            if (mean in 20.0..1_000.0 && stdDev <= 2.8 && roundedModeRatio >= 0.72) return true
            if (mean in 20.0..1_000.0 && identicalLike >= 9) return true
        }

        // Longer sustained periodicity allows a little scheduler jitter but requires a
        // machine-like concentration around the same delay bucket.
        val long = intervals.takeLast(30)
        if (long.size >= 24) {
            val mean = long.average()
            val stdDev = stdDev(long, mean)
            val coefficient = if (mean > 0.0) stdDev / mean else Double.MAX_VALUE
            val bucketRatio = modeRatio(long, 5.0)
            if (
                mean in 30.0..1_000.0 &&
                stdDev <= 7.0 &&
                coefficient <= 0.035 &&
                bucketRatio >= 0.62
            ) return true
        }

        return false
    }

    fun reset() = taps.clear()

    private fun countSince(since: Long): Int = taps.count { it >= since }

    private fun stdDev(values: List<Double>, mean: Double): Double {
        if (values.isEmpty()) return Double.MAX_VALUE
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    private fun modeRatio(values: List<Double>, bucketMs: Double): Double {
        if (values.isEmpty()) return 0.0
        val buckets = HashMap<Long, Int>()
        values.forEach { value ->
            val bucket = kotlin.math.round(value / bucketMs).toLong()
            buckets[bucket] = (buckets[bucket] ?: 0) + 1
        }
        return (buckets.values.maxOrNull() ?: 0).toDouble() / values.size
    }

    companion object {
        private const val HISTORY_MS = 35_000L
        private const val MIN_TAPS = 8
        private const val MIN_INTERVALS = 7
        private const val EXTREME_TAPS_PER_SECOND = 18
        private const val EXTREME_TAPS_PER_TWO_SECONDS = 32
        private const val IMPOSSIBLE_INTERVAL_MS = 12.0
    }
}
