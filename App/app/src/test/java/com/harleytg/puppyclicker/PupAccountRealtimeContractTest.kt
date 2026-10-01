package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PupAccountRealtimeContractTest {
    private fun source(name: String): String {
        val file = File("src/main/java/com/harleytg/puppyclicker/$name")
        return if (file.isFile) file.readText() else
            File("../src/main/java/com/harleytg/puppyclicker/$name").readText()
    }

    @Test
    fun realtimeIsInvalidationOnlyAndFallsBackToAuthenticatedRead() {
        val realtime = source("PupAccountRealtimeSync.kt")
        val cloud = source("PupAccountCloudSave.kt")
        val backend = source("SupabasePupEyeClient.kt")

        assertTrue(realtime.contains("/realtime/v1/websocket"))
        assertTrue(realtime.contains("event\") != \"broadcast"))
        assertTrue(realtime.contains("PupAccountCloudSave.refreshNow(context)"))
        assertTrue(realtime.contains("private\", false"))
        assertFalse(realtime.contains("save_data"))
        assertFalse(realtime.contains("service_role"))
        assertFalse(realtime.contains("sb_secret_"))

        assertTrue(cloud.contains("PupAccountRealtimeSync.start(app)"))
        assertTrue(backend.contains("action = \"account-status\""))
        assertTrue(backend.contains("realtimeTopic"))
    }

    @Test
    fun realtimeLifecycleStopsInBackground() {
        val app = source("PuppyClickerApplication.kt")
        val discord = source("DiscordSignupAuth.kt")

        assertTrue(app.contains("PupAccountRealtimeSync.start(this)"))
        assertTrue(app.contains("PupAccountRealtimeSync.stop()"))
        assertTrue(discord.contains("PupAccountRealtimeSync.stop()"))
    }
}
