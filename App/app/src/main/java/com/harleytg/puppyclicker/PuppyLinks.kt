package com.harleytg.puppyclicker

import android.content.Context
import android.content.Intent
import android.net.Uri

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
