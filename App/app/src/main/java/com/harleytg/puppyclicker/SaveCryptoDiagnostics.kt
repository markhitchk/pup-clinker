package com.harleytg.puppyclicker

import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import org.json.JSONException

internal enum class SaveCryptoFailureKind { TAMPER, OPERATIONAL }

internal fun classifySaveCryptoFailure(error: Throwable): SaveCryptoFailureKind = when (error) {
    is AEADBadTagException,
    is BadPaddingException,
    is IllegalArgumentException,
    is JSONException -> SaveCryptoFailureKind.TAMPER

    is InvalidAlgorithmParameterException,
    is InvalidKeyException,
    is KeyStoreException,
    is GeneralSecurityException -> SaveCryptoFailureKind.OPERATIONAL

    else -> SaveCryptoFailureKind.OPERATIONAL
}

internal class RepeatedFailureLogGate(
    private val cooldownMs: Long = 60_000L
) {
    private var lastSignature: String? = null
    private var lastLoggedAtMs: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldLog(error: Throwable, nowMs: Long = System.currentTimeMillis()): Boolean {
        val signature = "${error.javaClass.name}:${error.message.orEmpty()}"
        val changed = signature != lastSignature
        val cooldownElapsed = lastLoggedAtMs == Long.MIN_VALUE || nowMs - lastLoggedAtMs >= cooldownMs
        if (!changed && !cooldownElapsed) return false
        lastSignature = signature
        lastLoggedAtMs = nowMs
        return true
    }

    @Synchronized
    fun markSuccess(): Boolean {
        val recovering = lastSignature != null
        lastSignature = null
        lastLoggedAtMs = Long.MIN_VALUE
        return recovering
    }
}
