package com.harleytg.puppyclicker

import android.content.Context
import android.content.Intent
import android.net.Uri

internal object PuppyLinks {
    const val DISCORD_INVITE = "https://discord.gg/HcZweHchbv"
    const val TRELLO_BOARD =
        "https://trello.com/invite/b/6aa4b77f278c2676155b6d9b/ATTI33a5ace18684382af934f0544d2026a4AF70F7AB/puppy-clicker"

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
