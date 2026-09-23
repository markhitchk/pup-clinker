package com.harleytg.puppyclicker

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference

/**
 * Small runtime boundary for casino actions.
 *
 * UI-triggered casino commands should not be able to terminate the whole activity because of an
 * unexpected persistence/state exception. Coroutine cancellation is deliberately rethrown.
 */
internal object PuppyCasinoRuntimeGuard {
    private const val TAG = "PuppyCasinoRuntime"
    private val currentPage = AtomicReference("outside")

    fun markPage(page: String) {
        currentPage.set(
            page.trim()
                .replace(Regex("[^A-Za-z0-9_.-]"), "_")
                .take(64)
                .ifBlank { "unknown" }
        )
    }

    fun currentPage(): String = currentPage.get()

    fun <T> run(
        game: PuppyCasinoGame,
        action: String,
        block: () -> T
    ): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        PuppyDebugLog.e(
            TAG,
            "${game.name} runtime guard caught ${action.take(64)}",
            error
        )
        Result.failure(error)
    }
}
