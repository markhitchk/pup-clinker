package com.harleytg.puppyclicker

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class PuppyLogLevel(val shortName: String) {
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E")
}

internal data class PuppyLogEntry(
    val timestampMs: Long,
    val level: PuppyLogLevel,
    val tag: String,
    val message: String
)

/**
 * Puppy Clicker's app-scoped diagnostic facade.
 *
 * Diagnostics continue to Android Logcat while a sanitized, bounded in-memory copy is exposed to
 * the read-only Developer Console. Console history is intentionally never written to disk.
 */
internal object PuppyDebugLog {
    internal const val MAX_ENTRIES = 500
    private const val MAX_TAG_LENGTH = 48
    private const val MAX_MESSAGE_LENGTH = 4_096

    private val lock = Any()
    private val mutableEntries = MutableStateFlow<List<PuppyLogEntry>>(emptyList())

    private val sensitiveAssignment = Regex(
        """(?i)\b(token|authorization|password|passwd|secret|api[_-]?key|session(?:[_-]?id)?|keystore|encryption[_-]?key|payload|save[_-]?payload|birthday)\b\s*[:=]\s*(?:\"[^\"]*\"|'[^']*'|[^\s,;]+)"""
    )
    private val emailAddress = Regex(
        """(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"""
    )
    private val androidDataPath = Regex(
        """(?i)/(?:storage/emulated/\d+|sdcard)/Android/data/[^\s,;]+"""
    )

    fun observe(): StateFlow<List<PuppyLogEntry>> = mutableEntries.asStateFlow()

    fun snapshot(): List<PuppyLogEntry> = mutableEntries.value

    fun clear() {
        synchronized(lock) {
            mutableEntries.value = emptyList()
        }
    }

    fun v(tag: String, message: String): Int = publish(
        PuppyLogLevel.DEBUG,
        tag,
        message,
        null
    ) { Log.v(tag, message) }

    fun v(tag: String, message: String, throwable: Throwable?): Int = publish(
        PuppyLogLevel.DEBUG,
        tag,
        message,
        throwable
    ) { Log.v(tag, message, throwable) }

    fun d(tag: String, message: String): Int = publish(
        PuppyLogLevel.DEBUG,
        tag,
        message,
        null
    ) { Log.d(tag, message) }

    fun d(tag: String, message: String, throwable: Throwable?): Int = publish(
        PuppyLogLevel.DEBUG,
        tag,
        message,
        throwable
    ) { Log.d(tag, message, throwable) }

    fun i(tag: String, message: String): Int = publish(
        PuppyLogLevel.INFO,
        tag,
        message,
        null
    ) { Log.i(tag, message) }

    fun i(tag: String, message: String, throwable: Throwable?): Int = publish(
        PuppyLogLevel.INFO,
        tag,
        message,
        throwable
    ) { Log.i(tag, message, throwable) }

    fun w(tag: String, message: String): Int = publish(
        PuppyLogLevel.WARN,
        tag,
        message,
        null
    ) { Log.w(tag, message) }

    fun w(tag: String, message: String, throwable: Throwable?): Int = publish(
        PuppyLogLevel.WARN,
        tag,
        message,
        throwable
    ) { Log.w(tag, message, throwable) }

    fun w(tag: String, throwable: Throwable): Int = publish(
        PuppyLogLevel.WARN,
        tag,
        throwable.javaClass.simpleName,
        throwable
    ) { Log.w(tag, throwable) }

    fun e(tag: String, message: String): Int = publish(
        PuppyLogLevel.ERROR,
        tag,
        message,
        null
    ) { Log.e(tag, message) }

    fun e(tag: String, message: String, throwable: Throwable?): Int = publish(
        PuppyLogLevel.ERROR,
        tag,
        message,
        throwable
    ) { Log.e(tag, message, throwable) }

    fun e(tag: String, throwable: Throwable): Int = publish(
        PuppyLogLevel.ERROR,
        tag,
        throwable.javaClass.simpleName,
        throwable
    ) { Log.e(tag, throwable.javaClass.simpleName, throwable) }

    internal fun recordForTest(
        level: PuppyLogLevel,
        tag: String,
        message: String,
        timestampMs: Long
    ) {
        append(level, tag, message, timestampMs)
    }

    internal fun redactForConsole(input: String): String {
        var value = input
        value = sensitiveAssignment.replace(value) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }
        value = emailAddress.replace(value, "[REDACTED]")
        value = androidDataPath.replace(value, "[REDACTED]")
        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(MAX_MESSAGE_LENGTH)
    }

    private inline fun publish(
        level: PuppyLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
        logcat: () -> Int
    ): Int {
        val throwableText = throwable?.let {
            val detail = it.message?.takeIf(String::isNotBlank)
            if (detail == null) it.javaClass.simpleName else "${it.javaClass.simpleName}: $detail"
        }
        val consoleMessage = if (throwableText == null) message else "$message · $throwableText"
        append(level, tag, consoleMessage, System.currentTimeMillis())

        // Android's local JVM test stubs throw from Log.*; diagnostics must never throw into callers.
        return runCatching(logcat).getOrDefault(0)
    }

    private fun append(
        level: PuppyLogLevel,
        tag: String,
        message: String,
        timestampMs: Long
    ) {
        val safeTag = redactForConsole(tag).ifBlank { "PuppyClicker" }.take(MAX_TAG_LENGTH)
        val safeMessage = redactForConsole(message)
        val entry = PuppyLogEntry(
            timestampMs = timestampMs.coerceAtLeast(0L),
            level = level,
            tag = safeTag,
            message = safeMessage
        )

        synchronized(lock) {
            val current = mutableEntries.value
            mutableEntries.value = if (current.size >= MAX_ENTRIES) {
                current.drop(current.size - MAX_ENTRIES + 1) + entry
            } else {
                current + entry
            }
        }
    }
}
