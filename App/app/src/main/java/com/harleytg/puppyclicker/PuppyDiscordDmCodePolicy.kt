package com.harleytg.puppyclicker

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal object PuppyDiscordDmCodePolicy {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 8
    const val TTL_MS = 10L * 60L * 1000L
    const val MAX_ATTEMPTS = 5
    const val RESEND_COOLDOWN_MS = 60L * 1000L
    const val MAX_SENDS_PER_HOUR = 5
    const val HOUR_MS = 60L * 60L * 1000L

    private val CODE_CHARS = Regex("^[A-Z0-9]{6,8}$")

    fun normalize(raw: String): String =
        raw.trim().uppercase(Locale.US).replace(" ", "").replace("-", "")

    fun isWellFormed(raw: String): Boolean = CODE_CHARS.matches(normalize(raw))

    fun hash(code: String, playerUuid: String, installationUuid: String, discordUserId: String): String {
        val normalized = normalize(code)
        val material = "$normalized:$playerUuid:$installationUuid:$discordUserId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun canResend(
        lastSentAtMs: Long?,
        sendsInWindow: Int,
        windowStartedAtMs: Long?,
        nowMs: Long
    ): ResendDecision {
        if (lastSentAtMs != null && nowMs - lastSentAtMs < RESEND_COOLDOWN_MS) {
            return ResendDecision(false, "WAIT_COOLDOWN", lastSentAtMs + RESEND_COOLDOWN_MS)
        }
        val windowStart = windowStartedAtMs ?: nowMs
        val inSameWindow = nowMs - windowStart < HOUR_MS
        val used = if (inSameWindow) sendsInWindow else 0
        if (used >= MAX_SENDS_PER_HOUR) {
            return ResendDecision(false, "HOUR_CAP", windowStart + HOUR_MS)
        }
        return ResendDecision(true, null, nowMs)
    }

    fun nextAttemptState(attemptCount: Int, maxAttempts: Int = MAX_ATTEMPTS): AttemptState {
        val next = attemptCount + 1
        return AttemptState(next, (maxAttempts - next).coerceAtLeast(0), next >= maxAttempts)
    }

    data class ResendDecision(val allowed: Boolean, val reason: String?, val availableAtMs: Long)
    data class AttemptState(val attemptCount: Int, val remaining: Int, val locked: Boolean)
}
