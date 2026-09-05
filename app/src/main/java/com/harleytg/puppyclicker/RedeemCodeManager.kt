package com.harleytg.puppyclicker

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class RedeemPayload(
    val id: String,
    val rewardType: String,
    val amount: Long,
    val value: String,
    val expiresAtEpochSeconds: Long
)

sealed class RedeemVerification {
    data class Valid(val payload: RedeemPayload) : RedeemVerification()
    data class Invalid(val reason: String) : RedeemVerification()
}

object RedeemCodeManager {
    private const val PREFIX = "PC1"

    // Public verification key only. The private signing key must never ship in the APK or repo.
    private const val PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtexyFsiwvI4CpbzslMQN
AisEdHWD5MFZkjzq02EGYzEOViF0kVjRuTiZmN9N8NNUWGSuYSA7XFRn9ADjYh/x
k/oTyNSiYkmcTVAE/g1VevDOnOcKfRkL8lTtApmMs5ww4VHr1Ptpn8uaSi8znZtg
cgEKW0DQ/CYTl4l6vI86x5dXpjKynU6iYyePE0QeyeDbKjdF2lwipAzpGg2EH4ox
RyMHKa7tejwS6JYKznQ+Ij3Ig4Y50hTFTfrZZQt8ihO6nx+tSCE+kzQIE0VB9D2U
90ahNfktXiBX6zwQgRQiw0G0955r6IT6zEJawdjLuFxWgNU+PuM0SsS6kq0gMnfz
SwIDAQAB
-----END PUBLIC KEY-----"""

    fun verify(rawCode: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): RedeemVerification {
        val code = rawCode.trim().replace("\n", "").replace(" ", "")
        if (code.length !in 40..4096) return RedeemVerification.Invalid("Code format is invalid.")

        val parts = code.split('.')
        if (parts.size != 3 || parts[0] != PREFIX) {
            return RedeemVerification.Invalid("This is not a Puppy Clicker redeem code.")
        }

        return try {
            val payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]))
            val signatureBytes = Base64.getUrlDecoder().decode(padBase64(parts[2]))
            val publicKeyBytes = Base64.getDecoder().decode(
                PUBLIC_KEY_PEM
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
            )
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(payloadBytes)
            if (!verifier.verify(signatureBytes)) {
                return RedeemVerification.Invalid("Code signature is invalid.")
            }

            val payloadText = payloadBytes.toString(Charsets.UTF_8)
            val fields = payloadText.split('|')
            if (fields.size != 5) return RedeemVerification.Invalid("Code payload is invalid.")

            val id = fields[0]
            val rewardType = fields[1].uppercase()
            val amount = fields[2].toLongOrNull() ?: return RedeemVerification.Invalid("Reward amount is invalid.")
            val value = fields[3]
            val expires = fields[4].toLongOrNull() ?: return RedeemVerification.Invalid("Expiry is invalid.")

            if (!id.matches(Regex("[A-Za-z0-9_-]{4,64}"))) {
                return RedeemVerification.Invalid("Code ID is invalid.")
            }
            if (expires > 0 && nowEpochSeconds > expires) {
                return RedeemVerification.Invalid("This redeem code has expired.")
            }

            RedeemVerification.Valid(RedeemPayload(id, rewardType, amount, value, expires))
        } catch (_: IllegalArgumentException) {
            RedeemVerification.Invalid("Code encoding is invalid.")
        } catch (_: Exception) {
            RedeemVerification.Invalid("Code could not be verified.")
        }
    }

    private fun padBase64(value: String): String {
        val missing = (4 - value.length % 4) % 4
        return value + "=".repeat(missing)
    }
}
