package com.harleytg.puppyclicker

import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PuppySaveCryptoInstrumentedTest {
    @Test
    fun deviceEncryptionRoundTripsWithProviderGeneratedIv() {
        val plain = "puppy-save-regression".toByteArray()
        val encrypted = PuppySaveCrypto.encryptDevice(plain)
        val decrypted = PuppySaveCrypto.decryptDevice(encrypted)
        assertTrue(plain.contentEquals(decrypted))
    }

    @Test
    fun identicalPlaintextProducesDifferentContainers() {
        val plain = "same-input".toByteArray()
        val first = PuppySaveCrypto.encryptDevice(plain)
        val second = PuppySaveCrypto.encryptDevice(plain)
        assertFalse(first.contentEquals(second))
    }

    @Test(expected = AEADBadTagException::class)
    fun tamperingFailsAuthentication() {
        val encrypted = PuppySaveCrypto.encryptDevice("protected".toByteArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        PuppySaveCrypto.decryptDevice(encrypted)
    }
}
