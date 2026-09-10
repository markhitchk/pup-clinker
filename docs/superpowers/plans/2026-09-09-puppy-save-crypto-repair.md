# Puppy Save Crypto Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair Android Keystore AES-GCM device-save encryption, prevent operational crypto failures from being counted as tampering, and stop repeated external-save warning floods while preserving the encrypted Android/data mirror.

**Architecture:** Keep the existing SharedPreferences runtime save and Android/data mirror architecture. Let Android Keystore generate the AES-GCM encryption IV, store that IV in the existing `PCE1 + IV + ciphertext/tag` envelope, classify integrity failures separately from provider/storage failures, and coalesce repeated identical operational logs without disabling retries.

**Tech Stack:** Kotlin, Android Keystore, AES-256-GCM, SharedPreferences, JUnit 4, AndroidX instrumentation tests, Gradle 8.9.

**Spec:** `docs/superpowers/specs/2026-09-09-puppy-clicker-save-onboarding-ui-repair-design.md`

## Global Constraints

- Internal SharedPreferences remain the runtime source of truth.
- Keep Android Keystore AES-256-GCM and `.setRandomizedEncryptionRequired(true)`.
- Device encryption must not supply its own encryption IV.
- The Android/data mirror remains non-fatal at `Android/data/com.harleytg.puppyclicker/files/PuppyClicker/puppy_clicker_save.pup`.
- Authentication/container-integrity failures may increment PupEye tamper state; provider/key/storage failures must not.
- Automatic mirror writes continue retrying after failure.
- Identical operational failures are coalesced instead of logged on every save callback.
- Password-protected transfer-save crypto remains unchanged unless a regression test fails.

## File Structure

- Create `App/app/src/main/java/com/harleytg/puppyclicker/SaveCryptoDiagnostics.kt` — pure failure classification and repeat-log gate.
- Create `App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt` — JVM tests for classification/coalescing.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt` — provider-generated device IV and PupEye error classification.
- Modify `App/app/src/main/java/com/harleytg/puppyclicker/ExternalGameSave.kt` — mirror error classification, quarantine rules, recovery logging.
- Create `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppySaveCryptoInstrumentedTest.kt` — real AndroidKeyStore AES-GCM regression tests.
- Modify `App/app/build.gradle.kts` — AndroidX JUnit instrumentation dependency.
- Modify `.github/workflows/android.yml` — run JVM tests and compile instrumentation tests before release assembly.

---

### Task 1: Define save-crypto failure classification and log coalescing

**Files:**
- Create: `App/app/src/main/java/com/harleytg/puppyclicker/SaveCryptoDiagnostics.kt`
- Create: `App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt`

**Interfaces:**
- Produces: `internal enum class SaveCryptoFailureKind { TAMPER, OPERATIONAL }`.
- Produces: `internal fun classifySaveCryptoFailure(error: Throwable): SaveCryptoFailureKind`.
- Produces: `internal class RepeatedFailureLogGate(private val cooldownMs: Long = 60_000L)` with `shouldLog(...)` and `markSuccess()`.

- [ ] **Step 1: Write the failing JVM test**

```kotlin
package com.harleytg.puppyclicker

import java.security.InvalidAlgorithmParameterException
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCryptoDiagnosticsTest {
    @Test
    fun authenticationAndContainerFailuresAreTamper() {
        assertEquals(
            SaveCryptoFailureKind.TAMPER,
            classifySaveCryptoFailure(AEADBadTagException("bad tag"))
        )
        assertEquals(
            SaveCryptoFailureKind.TAMPER,
            classifySaveCryptoFailure(IllegalArgumentException("bad header"))
        )
    }

    @Test
    fun providerParameterFailureIsOperational() {
        assertEquals(
            SaveCryptoFailureKind.OPERATIONAL,
            classifySaveCryptoFailure(
                InvalidAlgorithmParameterException("Caller-provided IV not permitted")
            )
        )
    }

    @Test
    fun identicalFailuresAreSuppressedUntilCooldownAndRecoveryResetsGate() {
        val gate = RepeatedFailureLogGate(cooldownMs = 60_000L)
        val failure = InvalidAlgorithmParameterException("Caller-provided IV not permitted")

        assertTrue(gate.shouldLog(failure, nowMs = 1_000L))
        assertFalse(gate.shouldLog(failure, nowMs = 10_000L))
        assertTrue(gate.shouldLog(failure, nowMs = 61_001L))
        assertTrue(gate.markSuccess())
        assertFalse(gate.markSuccess())
        assertTrue(gate.shouldLog(failure, nowMs = 62_000L))
    }

    @Test
    fun changedFailureSignatureLogsImmediately() {
        val gate = RepeatedFailureLogGate(cooldownMs = 60_000L)
        assertTrue(gate.shouldLog(InvalidAlgorithmParameterException("first"), 1_000L))
        assertTrue(gate.shouldLog(InvalidAlgorithmParameterException("second"), 2_000L))
    }
}
```

- [ ] **Step 2: Verify the new test is red**

Run from `App/`:

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.SaveCryptoDiagnosticsTest --stacktrace
```

Expected: unresolved `SaveCryptoFailureKind`, `classifySaveCryptoFailure`, and `RepeatedFailureLogGate`.

- [ ] **Step 3: Implement the diagnostics helper**

```kotlin
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
        val cooldownElapsed =
            lastLoggedAtMs == Long.MIN_VALUE || nowMs - lastLoggedAtMs >= cooldownMs
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
```

- [ ] **Step 4: Verify the focused test is green**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.SaveCryptoDiagnosticsTest --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/SaveCryptoDiagnostics.kt App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt
git commit -m "test: classify save crypto failures"
```

---

### Task 2: Reproduce and fix Android Keystore encryption-IV initialization

**Files:**
- Modify: `App/app/build.gradle.kts` dependency block.
- Create: `App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppySaveCryptoInstrumentedTest.kt`.
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt` — `PuppySaveCrypto.encryptDevice()` only.

**Interfaces:**
- Consumes: existing `encryptDevice(ByteArray): ByteArray` and `decryptDevice(ByteArray): ByteArray`.
- Produces: same signatures and same `PCE1 + 12-byte IV + ciphertext/tag` device-save format.

- [ ] **Step 1: Add AndroidX JUnit instrumentation support**

```kotlin
androidTestImplementation("androidx.test.ext:junit:1.2.1")
```

Keep `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`.

- [ ] **Step 2: Write the real AndroidKeyStore regression test before changing crypto**

```kotlin
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
```

- [ ] **Step 3: Compile and run against the current implementation to reproduce the provider failure**

Compile:

```bash
gradle --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

Then on a connected device/emulator:

```bash
gradle --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

Expected before the fix on a provider enforcing randomized IV generation: the round-trip test fails with `InvalidAlgorithmParameterException: Caller-provided IV not permitted`.

- [ ] **Step 4: Change only device encryption initialization**

Replace `encryptDevice()` with:

```kotlin
fun encryptDevice(plain: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
    val iv = cipher.iv
    require(iv.size == IV_BYTES) {
        "Unexpected Android Keystore GCM IV length: ${iv.size}"
    }
    cipher.updateAAD(DEVICE_AAD.toByteArray(Charsets.UTF_8))
    val encrypted = cipher.doFinal(plain)
    return DEVICE_MAGIC.toByteArray(Charsets.US_ASCII) + iv + encrypted
}
```

Do not change `decryptDevice()`, `encryptTransfer()`, `decryptTransfer()`, the key alias, or `.setRandomizedEncryptionRequired(true)`.

- [ ] **Step 5: Re-run real Android crypto tests**

```bash
gradle --no-daemon :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --stacktrace
```

Expected: all `PuppySaveCryptoInstrumentedTest` tests PASS and the caller-provided encryption-IV exception is gone.

- [ ] **Step 6: Commit**

```bash
git add App/app/build.gradle.kts App/app/src/androidTest/java/com/harleytg/puppyclicker/PuppySaveCryptoInstrumentedTest.kt App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt
git commit -m "fix: let Android Keystore generate save IVs"
```

---

### Task 3: Prevent false tamper events and repeated mirror warnings

**Files:**
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/ExternalGameSave.kt` — `write()` and `verifyExisting()`.
- Modify: `App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt` — `PupEyeSaveGuard.verifyAndRecover()`.
- Test: `App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt`.

**Interfaces:**
- Consumes: `classifySaveCryptoFailure(Throwable)` and `RepeatedFailureLogGate`.
- Produces: quarantine only for integrity/tamper failures, operational failures left intact and log-coalesced, automatic retries preserved.

- [ ] **Step 1: Re-run diagnostics tests as the contract for integration**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.harleytg.puppyclicker.SaveCryptoDiagnosticsTest --stacktrace
```

Expected: PASS.

- [ ] **Step 2: Add one process-local log gate to `ExternalGameSave`**

```kotlin
private val failureLogGate = RepeatedFailureLogGate()
```

After a successful atomic target replacement and legacy-file cleanup:

```kotlin
if (failureLogGate.markSuccess()) {
    Log.i(TAG, "Encrypted Android/data mirror recovered")
}
```

Replace the unconditional write catch log with:

```kotlin
}.getOrElse { error ->
    if (failureLogGate.shouldLog(error)) {
        Log.w(
            TAG,
            "Encrypted Android/data mirror unavailable; continuing with internal save",
            error
        )
    }
    null
}
```

- [ ] **Step 3: Classify verification failures before recording tamper/quarantining**

Use:

```kotlin
}.getOrElse { error ->
    if (classifySaveCryptoFailure(error) == SaveCryptoFailureKind.TAMPER) {
        PupEyeSaveGuard.recordTamper(
            context,
            "Android/data save failed PupEye AES-GCM authentication"
        )
        quarantineTamperedFile(target)
    } else if (failureLogGate.shouldLog(error)) {
        Log.w(
            TAG,
            "Unable to verify encrypted Android/data mirror; leaving file intact",
            error
        )
    }
    false
}
```

Operational provider/key failures leave the existing encrypted mirror intact.

- [ ] **Step 4: Apply the same operational-vs-tamper distinction to the private PupEye seal**

Replace the blanket catch in `PupEyeSaveGuard.verifyAndRecover()` with:

```kotlin
}.getOrElse { error ->
    if (classifySaveCryptoFailure(error) == SaveCryptoFailureKind.TAMPER) {
        recordTamper(context, "Protected save seal failed authentication")
    }
    false
}
```

- [ ] **Step 5: Run unit tests and generated-source/debug compilation**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:generateProtectedPuppySources :app:assembleDebug --stacktrace
```

Expected: PASS with no generated-source patch-anchor failures.

- [ ] **Step 6: Perform device-level regression checks**

On an installed debug build, confirm:

```text
- normal gameplay saves no longer emit Caller-provided IV not permitted;
- Android/data/PuppyClicker/puppy_clicker_save.pup is created/updated;
- relaunch verifies the mirror successfully;
- an operational provider/storage failure does not increment PupEye tamper count;
- an intentionally corrupted ciphertext still fails authentication and is quarantined;
- repeated identical operational warnings are suppressed during the cooldown;
- a later successful mirror write emits one recovery log entry.
```

- [ ] **Step 7: Commit**

```bash
git add App/app/src/main/java/com/harleytg/puppyclicker/ExternalGameSave.kt App/app/src/main/java/com/harleytg/puppyclicker/SecureSaveCrypto.kt App/app/src/test/java/com/harleytg/puppyclicker/SaveCryptoDiagnosticsTest.kt
git commit -m "fix: distinguish save tamper from crypto failures"
```

---

### Task 4: Compile regression coverage in CI

**Files:**
- Modify: `.github/workflows/android.yml`.

**Interfaces:**
- Consumes: JVM diagnostics tests and Android instrumentation source from Tasks 1-3.
- Produces: CI that rejects unit-test or androidTest compilation regressions before release assembly.

- [ ] **Step 1: Add an explicit test/compile step after Gradle setup**

```yaml
      - name: Run JVM tests and compile Android instrumentation tests
        run: gradle --no-daemon :app:testDebugUnitTest :app:assembleDebugAndroidTest --stacktrace
```

- [ ] **Step 2: Run the same local Gradle coverage plus release assembly**

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleRelease --stacktrace
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/android.yml
git commit -m "ci: compile save crypto regression tests"
```

---

## Plan Self-Review

- Spec coverage: Keystore IV root cause, preserved external mirror, false-tamper prevention, retry behavior, log coalescing, device regression testing, and CI compilation are covered.
- Placeholder scan: no implementation placeholders remain.
- Type consistency: `SaveCryptoFailureKind`, `classifySaveCryptoFailure`, and `RepeatedFailureLogGate` are `internal` everywhere and used with the same signatures across tasks.
- Test realism: the provider-specific IV regression is exercised on Android instrumentation rather than mocked in a plain JVM test.
- Non-goal check: transfer-save PBKDF2/AES-GCM behavior remains untouched.
