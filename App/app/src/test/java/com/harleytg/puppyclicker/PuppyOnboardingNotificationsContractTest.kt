package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingNotificationsContractTest {
    @Test
    fun notificationsUsePreferencesAndNeverRequirePermissionToContinue() {
        val file = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingNotifications.kt"
        )
        assertTrue("PuppyOnboardingNotifications.kt must exist", file.exists())
        val source = file.readText()

        assertTrue(source.contains("setDailyRewardNotifications"))
        assertTrue(source.contains("setGameEventNotifications"))
        assertTrue(source.contains("setUpdateNotifications"))
        assertTrue(source.contains("notificationPermissionDecision"))
        assertTrue(source.contains("Not Now"))
        assertTrue(source.contains("POST_NOTIFICATIONS"))
    }
}
