package com.harleytg.puppyclicker

/**
 * Compatibility helper for casino rounds created by older builds while the
 * removed developer casino inspector was active.
 *
 * New builds cannot activate developer casino rounds. The old round prefix is
 * still recognized so an interrupted legacy DEV TEST round cannot be converted
 * into a real-money-equivalent Treat payout after updating the app.
 */
internal object PuppyDeveloperCheatsSession {
    private const val DEV_ROUND_PREFIX = "devtest_"

    fun isActive(): Boolean = false

    fun newTestRoundId(): String =
        DEV_ROUND_PREFIX + PuppyCasinoRoundIds.newId()

    fun isTestRoundId(roundId: String?): Boolean =
        roundId?.startsWith(DEV_ROUND_PREFIX) == true
}
