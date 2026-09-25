package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PupEyeEnforcementUiContractTest {
    private fun source(name: String): String {
        val file = File("src/main/java/com/harleytg/puppyclicker/$name")
        return if (file.isFile) file.readText() else ""
    }

    @Test
    fun rootRoutesEnforcementBeforeOnboardingOrGameplay() {
        val source = source("PuppyClickerV6Activity.kt")
        val gate = source.indexOf("PupEyeEnforcementGate")
        val onboarding = source.indexOf("PuppyOnboardingFlow")

        assertTrue(gate >= 0)
        assertTrue(onboarding >= 0)
        assertTrue(gate < onboarding)
        assertTrue(source.contains("SupabasePupEyeClient.enforcementState"))
    }

    @Test
    fun banUiContainsOnlySupportLegalAndDiagnosticRecoveryActions() {
        val source = source("PupEyeEnforcementUi.kt")

        assertTrue(source.contains("PupEye Global Ban"))
        assertTrue(source.contains("PupEye Review Required"))
        assertTrue(source.contains("Copy Ban ID"))
        assertTrue(source.contains("Contact Support"))
        assertTrue(source.contains("PuppyLegalLinks"))
        assertTrue(source.contains("PupEyeAuthority.supportInstallationCode"))
        assertFalse(source.contains("V6Play("))
        assertFalse(source.contains("SaveTransferSettings("))
        assertFalse(source.contains("authenticateDiscord("))
    }

    @Test
    fun foregroundReturnRefreshesAuthoritativeEnforcement() {
        val source = source("PuppyClickerApplication.kt")
        val foreground = source.indexOf("if (returningFromBackground)")
        val refresh = source.indexOf(
            "SupabasePupEyeClient.refreshEnforcementAsync(this)",
            foreground
        )

        assertTrue(foreground >= 0)
        assertTrue(refresh > foreground)
    }
}
