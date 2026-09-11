package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyOnboardingReadyContractTest {
    @Test
    fun readySummarizesIdentityAndDefersCompletionToStartPlaying() {
        val file = File(
            "src/main/java/com/harleytg/puppyclicker/PuppyOnboardingReady.kt"
        )
        assertTrue("PuppyOnboardingReady.kt must exist", file.exists())
        val source = file.readText()

        assertTrue(source.contains("publicPlayerId"))
        assertTrue(source.contains("publicFriendCode"))
        assertTrue(source.contains("DiscordSignupAuth"))
        assertTrue(source.contains("Save restored"))
        assertTrue(source.contains("Review Setup"))
        assertTrue(source.contains("Start Playing"))
    }
}
