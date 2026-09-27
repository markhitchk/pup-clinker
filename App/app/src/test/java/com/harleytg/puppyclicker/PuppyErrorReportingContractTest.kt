package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuppyErrorReportingContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun errorReportsUseDedicatedEncryptedDiscordDestination() {
        val reporting = source(
            "src/main/java/com/harleytg/puppyclicker/PuppySupportReporting.kt"
        )

        assertTrue(reporting.contains("ERROR_REPORT_WEBHOOK_CIPHER_B64"))
        assertTrue(reporting.contains("ERROR_REPORT_WEBHOOK_KEY_MASK_A"))
        assertTrue(reporting.contains("ERROR_REPORT_WEBHOOK_KEY_MASK_B"))
        assertTrue(reporting.contains("ERROR_REPORT_WEBHOOK_AAD"))
        assertTrue(reporting.contains("decryptErrorReportWebhook()"))
        assertTrue(reporting.contains("AES/GCM/NoPadding"))
        assertFalse(reporting.contains("https://discord.com/api/webhooks/"))
    }

    @Test
    fun usersMustDescribeAndExplicitlySubmitAnError() {
        val reporter = source(
            "src/main/java/com/harleytg/puppyclicker/PuppyErrorReportUi.kt"
        )
        val transfer = source(
            "src/main/java/com/harleytg/puppyclicker/GameSaveTransfer.kt"
        )

        assertTrue(reporter.contains("What happened?"))
        assertTrue(reporter.contains("Send Report"))
        assertTrue(reporter.contains("PuppySupportReporting.submitErrorReport"))
        assertTrue(reporter.contains("minimum 10 characters"))
        assertTrue(transfer.contains("Report Error"))
        assertTrue(transfer.contains("PuppyErrorReportDialog"))
    }

    @Test
    fun errorReportCarriesStructuredSupportContext() {
        val reporting = source(
            "src/main/java/com/harleytg/puppyclicker/PuppySupportReporting.kt"
        )

        assertTrue(reporting.contains(".put(\"name\", \"Error Code\")"))
        assertTrue(reporting.contains(".put(\"name\", \"Source\")"))
        assertTrue(reporting.contains(".put(\"name\", \"Severity\")"))
        assertTrue(reporting.contains(".put(\"name\", \"Detected Identity\")"))
        assertTrue(reporting.contains(".put(\"name\", \"Support Installation Code\")"))
        assertTrue(reporting.contains("PC-ERR-"))
        assertTrue(reporting.contains("ERROR_REPORT_MIN_INTERVAL_MS"))
    }
}
