package com.harleytg.puppyclicker

/**
 * Destructive Settings actions all share the same uninterrupted hold requirement.
 * UI presentation is intentionally separate so the timing contract stays testable.
 */
enum class DangerZoneAction(
    val key: String,
    val title: String,
    val description: String,
    val buttonLabel: String,
    val holdDurationMs: Long = 10_000L
) {
    RESET_SETTINGS(
        key = "settings",
        title = "Reset settings?",
        description = "Theme, accent, motion, UI scale and gameplay preference switches return to defaults. Your profile, birthday and game progress remain.",
        buttonLabel = "Hold to Reset Settings"
    ),
    RESET_PROGRESS(
        key = "progress",
        title = "Reset game progress?",
        description = "This resets the current run without awarding prestige points. Permanent prestige skills and existing special puppy unlocks remain.",
        buttonLabel = "Hold to Reset Progress"
    ),
    ERASE_ALL_DATA(
        key = "delete",
        title = "Delete local save data?",
        description = "This deletes local game progress, player identity, birthday/setup state, PupEye local integrity history and the device save mirror. Puppy Clicker will restart into first-run setup.",
        buttonLabel = "Hold to Delete Local Data"
    );

    companion object {
        fun fromKey(key: String): DangerZoneAction? = entries.firstOrNull { it.key == key }
    }
}

object DangerHoldConfirmation {
    const val HOLD_DURATION_MS = 10_000L

    fun progress(startedAtMs: Long?, nowMs: Long, durationMs: Long = HOLD_DURATION_MS): Float {
        if (startedAtMs == null || durationMs <= 0L || nowMs <= startedAtMs) return 0f
        return ((nowMs - startedAtMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun remainingMs(startedAtMs: Long?, nowMs: Long, durationMs: Long = HOLD_DURATION_MS): Long {
        if (startedAtMs == null) return durationMs.coerceAtLeast(0L)
        return (durationMs - (nowMs - startedAtMs)).coerceAtLeast(0L)
    }

    fun isComplete(startedAtMs: Long?, nowMs: Long, durationMs: Long = HOLD_DURATION_MS): Boolean =
        startedAtMs != null && durationMs > 0L && nowMs - startedAtMs >= durationMs
}
