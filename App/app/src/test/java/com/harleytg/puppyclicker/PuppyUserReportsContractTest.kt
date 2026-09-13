package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyUserReportsContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun supportReportsWorkWithoutBackendApi() {
        val reports = source(
            "src/main/java/com/harleytg/puppyclicker/PuppyUserReports.kt"
        )

        assertTrue(reports.contains("Intent.ACTION_SEND"))
        assertTrue(reports.contains("Share Puppy Clicker support report"))
        assertTrue(reports.contains("does not have a user-report API"))
        assertFalse(reports.contains("HttpURLConnection"))
        assertFalse(reports.contains("discord.com/api/webhooks/"))
    }

    @Test
    fun supportReportsUseLocalIdsAndNeverClaimServerSubmission() {
        val reports = source(
            "src/main/java/com/harleytg/puppyclicker/PuppyUserReports.kt"
        )

        assertTrue(reports.contains("PC-RPT-"))
        assertTrue(reports.contains("Prepared locally"))
        assertTrue(reports.contains("It has not been submitted"))
        assertTrue(reports.contains("cannot confirm delivery"))
    }

    @Test
    fun optionalDiagnosticsAreSanitizedAndBounded() {
        val reports = source(
            "src/main/java/com/harleytg/puppyclicker/PuppyUserReports.kt"
        )

        assertTrue(reports.contains("includeDiagnostics"))
        assertTrue(reports.contains("PuppyDebugLog.snapshot().takeLast(MAX_DIAGNOSTIC_ENTRIES)"))
        assertTrue(reports.contains("PuppyDebugLog.redactForConsole"))
        assertTrue(reports.contains("MAX_DIAGNOSTIC_ENTRIES = 30"))
        assertTrue(reports.contains("includeIdentity"))
    }

    @Test
    fun playerReportsAreAvailableAndRequireTargetIdentifier() {
        val reports = source(
            "src/main/java/com/harleytg/puppyclicker/PuppyUserReports.kt"
        )

        assertTrue(reports.contains("PLAYER("🚩", "Player / User Report")"))
        assertTrue(reports.contains("Reported username / Friend Code"))
        assertTrue(reports.contains("selectedType != PuppySupportReportType.PLAYER || reportedUser.isNotBlank()"))
    }

    @Test
    fun settingsExposeTierOneSupportDestination() {
        val settings = source(
            "src/main/java/com/harleytg/puppyclicker/PuppySettingsUi.kt"
        )

        assertTrue(settings.contains("SettingsDestination.SUPPORT"))
        assertTrue(settings.contains("Support & Reports"))
        assertTrue(settings.contains("PuppyUserReportSettings()"))
        assertTrue(settings.contains("Tier 1 support intake"))
    }
}
