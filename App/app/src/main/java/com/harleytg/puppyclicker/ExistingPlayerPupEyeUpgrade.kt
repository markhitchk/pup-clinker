package com.harleytg.puppyclicker

import android.content.Context

/**
 * One-time bridge for players upgrading from a pre-Supabase Puppy Clicker build.
 *
 * New installs establish a baseline while setup is incomplete and never enter this migration.
 * Existing players arrive with setup already complete and no baseline marker, so their existing
 * Player ID, Friend Code and save are registered without rewriting progression.
 */
internal object ExistingPlayerPupEyeUpgrade {
    private const val PREFS = "pupeye_existing_player_upgrade_v1"
    private const val KEY_BASELINE_SEEN = "baseline_seen"
    private const val KEY_APPLIED_VERSION = "applied_version"
    private const val CURRENT_VERSION = 1
    private const val NOTICE_ID = "pupeye-existing-player-upgrade-v1"

    @Synchronized
    fun run(context: Context) {
        val app = context.applicationContext
        val migration = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val setupComplete = app.getSharedPreferences(
            PuppyUiPreferences.PREFS_NAME,
            Context.MODE_PRIVATE
        ).getBoolean("setup_complete", false)

        val baselineSeen = migration.getBoolean(KEY_BASELINE_SEEN, false)
        val appliedVersion = migration.getInt(KEY_APPLIED_VERSION, 0)

        if (!setupComplete) {
            // A fresh install sees the new security model before it has a player save.
            // Remember that baseline so completing onboarding later is not misclassified
            // as an old-player migration.
            if (!baselineSeen) {
                migration.edit().putBoolean(KEY_BASELINE_SEEN, true).apply()
            }
            return
        }

        if (!shouldRunExistingPlayerUpgrade(setupComplete, baselineSeen, appliedVersion)) {
            return
        }

        // Materialize the existing identity and the new device-bound Pupeye identity.
        // None of these calls modify game progression/currency/roster state.
        PuppyPlayerIdentity.playerId(app)
        PuppyPlayerIdentity.friendCode(app)
        PupEyeAuthority.installationId(app)
        val supportCode = PupEyeAuthority.supportInstallationCode(app)
        val discordLinked = DiscordSignupAuth.hasPersistedAccount(app)

        val body = if (discordLinked) {
            "Your existing Puppy Clicker progress is unchanged. Pupeye is registering this device with the new account security system. Re-verify Discord in Settings → Discord so your server role can be verified by Supabase. Support code: $supportCode"
        } else {
            "Your existing Puppy Clicker progress is unchanged. Pupeye is registering this device with the new account security system. Support code: $supportCode"
        }

        PuppyNotificationHistory.record(
            app,
            PuppyNotificationItem(
                id = NOTICE_ID,
                type = PuppyNotificationType.APP_UPDATE,
                title = "PupEye security upgraded",
                body = body,
                createdAtMs = System.currentTimeMillis(),
                read = false,
                route = PuppyNotificationRoute.NONE
            )
        )

        PuppyNotificationCenter.notifyPupEyeSecurityUpgrade(
            context = app,
            discordNeedsReverification = discordLinked
        )

        // Registration is independently idempotent and retries from application startup when
        // offline. Mark only the local migration/notice as applied so users are not spammed.
        migration.edit()
            .putBoolean(KEY_BASELINE_SEEN, true)
            .putInt(KEY_APPLIED_VERSION, CURRENT_VERSION)
            .apply()

        SupabasePupEyeClient.initialize(app)
    }

    internal fun shouldRunExistingPlayerUpgrade(
        setupComplete: Boolean,
        baselineSeen: Boolean,
        appliedVersion: Int
    ): Boolean =
        setupComplete &&
            !baselineSeen &&
            appliedVersion < CURRENT_VERSION
}
