package com.harleytg.puppyclicker

import android.content.Context
import android.content.Intent
import android.net.Uri

internal object PuppyLinks {
    const val DISCORD_INVITE = "https://discord.gg/HcZweHchbv"

    fun openDiscord(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_INVITE))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.applicationContext.startActivity(intent)
    }
}
