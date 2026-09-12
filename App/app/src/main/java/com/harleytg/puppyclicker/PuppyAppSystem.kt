package com.harleytg.puppyclicker

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Application-level navigation and external-link contracts.
 *
 * Consolidated as part of the six-system Kotlin architecture.
 */

// ---- PuppyNavigationModel ----
enum class PuppyMainDestination(val label: String, val emoji: String) {
    PLAY("Play", "🐾"),
    CARE("Care", "💖"),
    ROSTER("Roster", "🐶"),
    SHOP("Shop", "🛍️"),
    REWARDS("Rewards", "🎁")
}

enum class PuppyInternalDestination(val label: String) {
    SETTINGS("Settings"),
    PRESTIGE("Prestige"),
    EXCHANGE("Puppy Exchange")
}

// ---- PuppyLinks ----
internal object PuppyLinks {
    const val DISCORD_INVITE = "https://discord.gg/HcZweHchbv"
    const val TRELLO_BOARD =
        "https://trello.com/b/qwwa6bq7"

    fun openDiscord(context: Context) {
        openExternal(context, DISCORD_INVITE)
    }

    fun openRoadmap(context: Context) {
        openExternal(context, TRELLO_BOARD)
    }

    private fun openExternal(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.applicationContext.startActivity(intent)
    }
}