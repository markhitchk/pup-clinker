package com.harleytg.puppyclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PuppyReleaseHubTest {
    @Test
    fun noLatestReleaseShowsInstalledBuildWithoutUpdate() {
        val model = PuppyReleaseHubModel.build("1.7.14", 27, null)
        assertEquals("1.7.14", model.installedVersion)
        assertEquals(27, model.installedBuild)
        assertFalse(model.updateAvailable)
    }

    @Test
    fun newerReleaseShowsUpdateAndNotes() {
        val update = PuppyReleaseUpdate(
            versionCode = 28,
            versionName = "1.8.0",
            releaseName = "Puppy Clicker 1.8.0",
            notes = "New things",
            releaseUrl = "https://github.com/markhitchk/pup-clinker/releases/tag/v1.8.0",
            apkUrl = null,
            unread = true
        )
        val model = PuppyReleaseHubModel.build("1.7.14", 27, update)
        assertTrue(model.updateAvailable)
        assertEquals("New things", model.releaseNotes)
        assertEquals(28, model.latestBuild)
    }

    @Test
    fun whatsNewSummaryAlwaysDescribesOnePointZeroProgram() {
        assertTrue(PuppyReleaseHubModel.WHATS_NEW_1_0.isNotBlank())
        assertTrue(PuppyReleaseHubModel.WHATS_NEW_1_0.contains("Bond"))
        assertTrue(PuppyReleaseHubModel.WHATS_NEW_1_0.contains("Achievements"))
    }
}
