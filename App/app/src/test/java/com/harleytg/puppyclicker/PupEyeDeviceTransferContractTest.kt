package com.harleytg.puppyclicker

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PupEyeDeviceTransferContractTest {
    private fun source(name: String): String =
        File("src/main/java/com/harleytg/puppyclicker/$name").readText()

    @Test
    fun portableSaveCarriesSignedMigrationClaim() {
        val authority = source("PupEyeAuthority.kt")

        assertTrue(authority.contains("PuppyClicker/PupEye/migration-claim/v1"))
        assertTrue(authority.contains("migrationPayloadHashSha256"))
        assertTrue(authority.contains("migrationSignature"))
        assertTrue(authority.contains("fun transferMigrationClaim("))
    }

    @Test
    fun crossDeviceImportRequiresBackendApprovalBeforeIdentityAdoption() {
        val transfer = source("GameSaveTransfer.kt")
        val request = transfer.indexOf("SupabasePupEyeClient.requestDeviceTransfer")
        val approval = transfer.indexOf("if (!transfer.approved)", request)
        val adopt = transfer.indexOf("PuppyPlayerIdentity.adoptTransferredIdentity", request)

        assertTrue(transfer.contains("PupEyeErrorCode.DEVICE_TRANSFER_REQUIRED"))
        assertTrue(request >= 0)
        assertTrue(approval > request)
        assertTrue(adopt > approval)
    }

    @Test
    fun freshInstallDoesNotRegisterPlaceholderPlayerBeforeOnboarding() {
        val client = source("SupabasePupEyeClient.kt")
        val init = client.indexOf("fun initialize(context: Context)")
        val setupGate = client.indexOf("PuppyUiPreferences.current(app).setupComplete", init)
        val register = client.indexOf("ensureRegistered(app)", setupGate)

        assertTrue(setupGate > init)
        assertTrue(register > setupGate)
        assertTrue(client.contains("functionName = \"pupeye-device-transfer\""))
        assertTrue(client.contains("action = \"device-transfer\""))
    }

    @Test
    fun normalNewPlayerRegistersAfterOnboardingCompletes() {
        val onboarding = source("PuppyOnboardingUi.kt")
        val finish = onboarding.indexOf("PuppyUiPreferences.finishSetup(context)")
        val checkpoint = onboarding.indexOf(
            "SupabasePupEyeClient.queueSaveCheckpoint",
            finish
        )

        assertTrue(finish >= 0)
        assertTrue(checkpoint > finish)
    }
}
